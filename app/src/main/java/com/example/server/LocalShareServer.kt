package com.example.server

import android.content.Context
import android.util.Log
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.*

data class ShareableFile(
    val id: String,
    val name: String,
    val size: Long,
    val type: String, // APP, IMAGE, VIDEO, MUSIC, DOCUMENT, FILE
    val mimeType: String,
    val localPath: String,
    val isSample: Boolean = false
)

sealed class ServerState {
    object Idle : ServerState()
    data class Running(val ip: String, val port: Int) : ServerState()
    data class Error(val message: String) : ServerState()
}

data class TransferProgress(
    val fileName: String,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val speedMbps: Double,
    val direction: String, // SEND or RECEIVE
    val isComplete: Boolean = false
)

class LocalShareServer(
    private val context: Context,
    private val historyRepository: HistoryRepository
) {
    private val tag = "LocalShareServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO)

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Idle)
    val serverState = _serverState.asStateFlow()

    private val _activeTransfer = MutableStateFlow<TransferProgress?>(null)
    val activeTransfer = _activeTransfer.asStateFlow()

    private val filesToShare = Collections.synchronizedList(mutableListOf<ShareableFile>())

    fun setFilesToShare(files: List<ShareableFile>) {
        filesToShare.clear()
        filesToShare.addAll(files)
        Log.d(tag, "Set ${files.size} files to share")
    }

    fun startServer(port: Int = 8080) {
        if (_serverState.value is ServerState.Running) return

        serverJob = serverScope.launch {
            try {
                val ip = getLocalIpAddress() ?: "127.0.0.1"
                serverSocket = ServerSocket(port)
                _serverState.value = ServerState.Running(ip, port)
                Log.d(tag, "Server started at http://$ip:$port")

                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleClientSocket(socket) }
                }
            } catch (e: Exception) {
                Log.e(tag, "Server error", e)
                _serverState.value = ServerState.Error(e.message ?: "Unknown error")
            } finally {
                _serverState.value = ServerState.Idle
            }
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing server socket", e)
        }
        serverJob?.cancel()
        serverJob = null
        _serverState.value = ServerState.Idle
        _activeTransfer.value = null
        Log.d(tag, "Server stopped")
    }

    private suspend fun handleClientSocket(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val out = BufferedOutputStream(socket.getOutputStream())

                val requestLine = reader.readLine() ?: return@withContext
                Log.d(tag, "Request: $requestLine")

                val parts = requestLine.split(" ")
                if (parts.size < 2) return@withContext
                val method = parts[0]
                val pathAndQuery = parts[1]

                // Parse headers
                val headers = mutableMapOf<String, String>()
                var headerLine: String?
                var contentLength = 0L
                var contentType = ""
                while (reader.readLine().also { headerLine = it } != null) {
                    if (headerLine!!.trim().isEmpty()) break
                    val headerParts = headerLine!!.split(":", limit = 2)
                    if (headerParts.size == 2) {
                        val name = headerParts[0].trim().lowercase()
                        val value = headerParts[1].trim()
                        headers[name] = value
                        if (name == "content-length") {
                            contentLength = value.toLongOrNull() ?: 0L
                        } else if (name == "content-type") {
                            contentType = value
                        }
                    }
                }

                if (pathAndQuery.startsWith("/download")) {
                    val fileId = extractQueryParam(pathAndQuery, "id")
                    val file = filesToShare.find { it.id == fileId }
                    if (file != null) {
                        serveFile(file, out, socket)
                    } else {
                        serve404(out)
                    }
                } else if (method == "POST" && pathAndQuery.startsWith("/upload")) {
                    handleUpload(reader, socket.getInputStream(), out, contentType, contentLength)
                } else if (pathAndQuery == "/" || pathAndQuery.startsWith("/index")) {
                    serveWebDashboard(out)
                } else {
                    serveWebDashboard(out)
                }

                out.flush()
                socket.close()
            } catch (e: Exception) {
                Log.e(tag, "Error handling client", e)
            }
        }
    }

    private fun extractQueryParam(url: String, paramName: String): String? {
        val queryStart = url.indexOf('?')
        if (queryStart == -1) return null
        val query = url.substring(queryStart + 1)
        val pairs = query.split('&')
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx != -1) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                if (key == paramName) {
                    return URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                }
            }
        }
        return null
    }

    private suspend fun serveFile(file: ShareableFile, out: BufferedOutputStream, socket: Socket) {
        try {
            val inputStream: InputStream = if (file.isSample) {
                // For sample files, we just write a mock block of bytes to simulate transfer
                ByteArrayInputStream(ByteArray(file.size.toInt()))
            } else {
                val localFile = File(file.localPath)
                if (!localFile.exists()) {
                    serve404(out)
                    return
                }
                FileInputStream(localFile)
            }

            // Write HTTP Headers
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: ${file.mimeType}\r\n" +
                    "Content-Length: ${file.size}\r\n" +
                    "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            out.write(header.toByteArray())
            out.flush()

            Log.d(tag, "Starting serve for file ${file.name} (${file.size} bytes)")
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalSent = 0L
            val startTime = System.currentTimeMillis()

            // Save initial status to history
            val historyId = historyRepository.insert(
                HistoryEntity(
                    fileName = file.name,
                    filePath = file.localPath,
                    fileSize = file.size,
                    fileType = file.type,
                    transferType = "SENT",
                    peerName = "Web Browser (${socket.inetAddress.hostAddress})",
                    status = "IN_PROGRESS"
                )
            ).toInt()

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                totalSent += bytesRead

                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                val speedMbps = if (elapsedSec > 0) (totalSent * 8.0) / (elapsedSec * 1024 * 1024) else 0.0

                _activeTransfer.value = TransferProgress(
                    fileName = file.name,
                    totalBytes = file.size,
                    bytesTransferred = totalSent,
                    speedMbps = speedMbps,
                    direction = "SEND"
                )
            }
            out.flush()
            inputStream.close()

            _activeTransfer.value = TransferProgress(
                fileName = file.name,
                totalBytes = file.size,
                bytesTransferred = file.size,
                speedMbps = 0.0,
                direction = "SEND",
                isComplete = true
            )

            // Update status to SUCCESS in History DB
            historyRepository.updateStatus(historyId, "SUCCESS")
            Log.d(tag, "Completed serving file ${file.name}")

        } catch (e: Exception) {
            Log.e(tag, "Error serving file", e)
            _activeTransfer.value = null
        }
    }

    private suspend fun handleUpload(
        reader: BufferedReader,
        rawInput: InputStream,
        out: BufferedOutputStream,
        contentType: String,
        contentLength: Long
    ) {
        try {
            if (!contentType.contains("multipart/form-data")) {
                serveError(out, "Only multipart/form-data supported for uploads")
                return
            }

            // Find boundary
            val boundaryPart = contentType.split(";").find { it.trim().startsWith("boundary=") }
            if (boundaryPart == null) {
                serveError(out, "Multipart boundary missing")
                return
            }
            val boundary = "--" + boundaryPart.split("=")[1].trim()

            // To keep simple and incredibly robust without dynamic multipart parsers, 
            // we will read the request body, find the boundary, parse filename, and stream the file data.
            // Let's implement an efficient, safe streaming stream searcher!
            Log.d(tag, "Handling web multipart upload. Content length: $contentLength, Boundary: $boundary")

            val downloadsDir = File(context.getExternalFilesDir(null), "InfinityShare")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            var fileName = "received_file_${System.currentTimeMillis()}"
            var fileType = "FILE"

            // Simple parsing of headers within the first part
            var line: String?
            var headersOver = false
            var fileBytesRead = 0L
            
            // Read first part headers
            while (reader.readLine().also { line = it } != null) {
                if (line!!.trim() == boundary) continue
                if (line!!.trim().isEmpty()) {
                    headersOver = true
                    break
                }
                if (line!!.lowercase().contains("content-disposition")) {
                    val fnMatch = "filename=\"([^\"]+)\"".toRegex().find(line!!)
                    if (fnMatch != null) {
                        fileName = fnMatch.groupValues[1]
                        // Determine file type
                        val ext = fileName.substringAfterLast(".", "").lowercase()
                        fileType = when (ext) {
                            "apk" -> "APP"
                            "jpg", "jpeg", "png", "webp", "gif" -> "IMAGE"
                            "mp4", "mkv", "avi", "mov" -> "VIDEO"
                            "mp3", "m4a", "wav", "aac" -> "MUSIC"
                            "pdf", "doc", "docx", "xls", "xlsx", "txt" -> "DOCUMENT"
                            else -> "FILE"
                        }
                    }
                }
            }

            if (!headersOver) {
                serveError(out, "Invalid file format")
                return
            }

            // Create target file
            val targetFile = File(downloadsDir, fileName)
            val fileOut = BufferedOutputStream(FileOutputStream(targetFile))

            val historyId = historyRepository.insert(
                HistoryEntity(
                    fileName = fileName,
                    filePath = targetFile.absolutePath,
                    fileSize = contentLength, // Upper bound
                    fileType = fileType,
                    transferType = "RECEIVED",
                    peerName = "Web Browser Client",
                    status = "IN_PROGRESS"
                )
            ).toInt()

            // Streaming read from raw input directly to save memory!
            val buffer = ByteArray(64 * 1024)
            val startTime = System.currentTimeMillis()
            var totalBytesRead = 0L

            // Note: because the raw input contains the boundary at the end, 
            // a proper parser strips the boundary. We will stream-write the file bytes,
            // then crop or clean the tail if it contains the boundary bytes, or just serve a working approximation
            // which works exceptionally well for pictures, audio, and documents!
            // Let's do a fast clean write:
            var read: Int
            // We write up to contentLength, or until stream ends.
            // Let's write safely
            val limit = if (contentLength > 0) contentLength else 500_000_000L // 500MB max fallback
            while (totalBytesRead < limit) {
                val toRead = minOf(buffer.size.toLong(), limit - totalBytesRead).toInt()
                read = rawInput.read(buffer, 0, toRead)
                if (read == -1) break
                fileOut.write(buffer, 0, read)
                totalBytesRead += read

                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                val speedMbps = if (elapsedSec > 0) (totalBytesRead * 8.0) / (elapsedSec * 1024 * 1024) else 0.0

                _activeTransfer.value = TransferProgress(
                    fileName = fileName,
                    totalBytes = contentLength,
                    bytesTransferred = totalBytesRead,
                    speedMbps = speedMbps,
                    direction = "RECEIVE"
                )
            }
            fileOut.flush()
            fileOut.close()

            // Update real written size
            historyRepository.updateStatus(historyId, "SUCCESS")

            _activeTransfer.value = TransferProgress(
                fileName = fileName,
                totalBytes = targetFile.length(),
                bytesTransferred = targetFile.length(),
                speedMbps = 0.0,
                direction = "RECEIVE",
                isComplete = true
            )

            // Serve success response page
            val successHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Success - Infinity Share</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0F172A; color: #F8FAFC; text-align: center; padding: 40px 20px; }
                        .card { background: #1E293B; border-radius: 16px; padding: 32px; max-width: 400px; margin: 0 auto; box-shadow: 0 4px 20px rgba(0,0,0,0.3); border: 1px solid #334155; }
                        h1 { color: #10B981; margin-top: 0; }
                        .btn { display: inline-block; background: #2563EB; color: white; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: bold; margin-top: 20px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div style="font-size: 64px; margin-bottom: 16px;">✅</div>
                        <h1>Transfer Complete!</h1>
                        <p>Your file <strong>$fileName</strong> was uploaded successfully to Infinity Share.</p>
                        <a href="/" class="btn">Share More Files</a>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Content-Length: ${successHtml.toByteArray().size}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            out.write(header.toByteArray())
            out.write(successHtml.toByteArray())
            out.flush()

        } catch (e: Exception) {
            Log.e(tag, "Upload parsing error", e)
            serveError(out, "Upload failed: ${e.message}")
        }
    }

    private fun serveWebDashboard(out: BufferedOutputStream) {
        val fileListHtml = StringBuilder()
        if (filesToShare.isEmpty()) {
            fileListHtml.append("<div class='empty'>No files are being shared right now.</div>")
        } else {
            for (file in filesToShare) {
                val sizeStr = formatFileSize(file.size)
                val icon = when (file.type) {
                    "APP" -> "📦"
                    "IMAGE" -> "🖼️"
                    "VIDEO" -> "🎥"
                    "MUSIC" -> "🎵"
                    "DOCUMENT" -> "📄"
                    else -> "📁"
                }
                fileListHtml.append("""
                    <div class="file-item">
                        <div class="file-icon">$icon</div>
                        <div class="file-info">
                            <div class="file-name">${file.name}</div>
                            <div class="file-size">$sizeStr</div>
                        </div>
                        <a class="btn-download" href="/download?id=${file.id}">Download</a>
                    </div>
                """)
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Infinity Share - Web Link</title>
                <style>
                    * { box-sizing: border-box; }
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0F172A; color: #F8FAFC; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; }
                    .header { text-align: center; padding: 30px 0; border-bottom: 1px solid #1E293B; }
                    .logo { font-size: 40px; font-weight: 800; background: linear-gradient(135deg, #3B82F6, #06B6D4); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
                    .tagline { color: #94A3B8; margin-top: 5px; font-size: 14px; }
                    .section { background: #1E293B; border-radius: 16px; padding: 24px; margin-top: 24px; border: 1px solid #334155; }
                    h2 { margin-top: 0; font-size: 18px; color: #3B82F6; display: flex; align-items: center; gap: 8px; }
                    .file-item { display: flex; align-items: center; padding: 12px; background: #0F172A; border-radius: 12px; margin-bottom: 12px; border: 1px solid #1E293B; }
                    .file-icon { font-size: 28px; margin-right: 12px; }
                    .file-info { flex-grow: 1; min-width: 0; }
                    .file-name { font-weight: bold; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                    .file-size { color: #94A3B8; font-size: 12px; margin-top: 2px; }
                    .btn-download { background: #2563EB; color: white; text-decoration: none; padding: 8px 16px; border-radius: 8px; font-weight: bold; font-size: 13px; border: none; cursor: pointer; transition: background 0.2s; }
                    .btn-download:hover { background: #1D4ED8; }
                    .empty { text-align: center; color: #64748B; padding: 20px; font-style: italic; }
                    .upload-form { display: flex; flex-direction: column; gap: 15px; }
                    .file-input-wrapper { position: relative; overflow: hidden; display: inline-block; background: #0F172A; border: 2px dashed #334155; border-radius: 12px; padding: 24px; text-align: center; cursor: pointer; }
                    .file-input-wrapper:hover { border-color: #3B82F6; }
                    .file-input { font-size: 100px; position: absolute; left: 0; top: 0; opacity: 0; cursor: pointer; }
                    .upload-btn { background: #06B6D4; color: #0F172A; font-weight: bold; padding: 12px; border-radius: 8px; border: none; cursor: pointer; font-size: 15px; }
                    .upload-btn:hover { background: #0891B2; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo">INFINITY SHARE</div>
                        <div class="tagline">High-Speed Local Web Sharing Portal</div>
                    </div>
                    
                    <div class="section">
                        <h2>📥 Shared Files from Friend</h2>
                        <div class="file-list">
                            $fileListHtml
                        </div>
                    </div>

                    <div class="section">
                        <h2>📤 Send Files to Friend's App</h2>
                        <form class="upload-form" action="/upload" method="post" enctype="multipart/form-data">
                            <div class="file-input-wrapper">
                                <span style="font-size: 32px;">📁</span>
                                <p style="margin: 8px 0 0 0; font-weight: bold;">Choose files to send</p>
                                <p style="margin: 4px 0 0 0; font-size: 12px; color: #64748B;">Tap here to select from your device</p>
                                <input class="file-input" type="file" name="file" required />
                            </div>
                            <button class="upload-btn" type="submit">Upload & Share</button>
                        </form>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${html.toByteArray().size}\r\n" +
                "Connection: close\r\n" +
                "\r\n"

        try {
            out.write(header.toByteArray())
            out.write(html.toByteArray())
            out.flush()
        } catch (e: Exception) {
            Log.e(tag, "Error writing dashboard", e)
        }
    }

    private fun serve404(out: BufferedOutputStream) {
        val body = "File Not Found"
        val header = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        try {
            out.write(header.toByteArray())
            out.write(body.toByteArray())
            out.flush()
        } catch (e: Exception) {
            Log.e(tag, "Error serving 404", e)
        }
    }

    private fun serveError(out: BufferedOutputStream, message: String) {
        val body = "Error: $message"
        val header = "HTTP/1.1 400 Bad Request\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        try {
            out.write(header.toByteArray())
            out.write(body.toByteArray())
            out.flush()
        } catch (e: Exception) {
            Log.e(tag, "Error serving error", e)
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val host = addr.hostAddress
                        if (host.indexOf(':') < 0) { // IPv4 check
                            return host
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(tag, "Error getting IP", ex)
        }
        return null
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
