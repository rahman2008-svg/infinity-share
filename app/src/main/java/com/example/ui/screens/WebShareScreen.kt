package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.ServerState
import com.example.ui.ShareViewModel
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebShareScreen(
    viewModel: ShareViewModel,
    onBackClick: () -> Unit
) {
    val serverState by viewModel.serverState.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val activeTransfer by viewModel.activeTransfer.collectAsState()

    val fileList = remember { selectedFiles.toList() }

    // Start server when opening screen
    LaunchedEffect(Unit) {
        viewModel.startHostingServer()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Web Share",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopHostingServer()
                            onBackClick()
                        },
                        modifier = Modifier.testTag("webshare_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Connection Guide Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Connect PC, iOS, or Android",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // High fidelity matrix QR code drawn directly on canvas
                        MockQrCodeCanvas()

                        Spacer(modifier = Modifier.height(16.dp))

                        when (val state = serverState) {
                            is ServerState.Running -> {
                                Text(
                                    text = "Scan QR Code or visit URL in Browser:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        text = "http://${state.ip}:${state.port}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = CyanAccent,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }
                            is ServerState.Idle -> {
                                Text("Starting web share engine...", color = ProgressYellow, fontSize = 13.sp)
                            }
                            is ServerState.Error -> {
                                Text("Server Error: ${state.message}", color = ErrorRed, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Connection Steps
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "How to connect:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        StepRow(1, "Connect your receiver device to the same Wi-Fi network.")
                        StepRow(2, "Open Safari, Chrome, or any web browser.")
                        StepRow(3, "Navigate to the address above or scan the QR Code.")
                        StepRow(4, "Download shared files, or upload files back to this phone!")
                    }
                }
            }

            // active transfer details if somebody is downloading right now!
            activeTransfer?.let { transfer ->
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, SuccessGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = SuccessGreen,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Client downloading...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = SuccessGreen
                                )
                                Text(
                                    text = transfer.fileName,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.1f MB/s", transfer.speedMbps),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }

            // Hosted items
            if (fileList.isNotEmpty()) {
                item {
                    Text(
                        text = "Currently Hosting (${fileList.size} Files)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(fileList) { file ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(BlueSecondary.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (file.type == "APP") Icons.Default.Android else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = BlueSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatSize(file.size),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MockQrCodeCanvas() {
    Canvas(
        modifier = Modifier
            .size(150.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("qr_canvas")
    ) {
        val size = size.width
        val cols = 15
        val cellSize = size / cols

        // Draw standard QR Finder squares (top-left, top-right, bottom-left)
        drawFinderPattern(0f, 0f, cellSize)
        drawFinderPattern((cols - 5) * cellSize, 0f, cellSize)
        drawFinderPattern(0f, (cols - 5) * cellSize, cellSize)

        // Generate pseudo-random matrix blocks
        val matrix = Array(cols) { BooleanArray(cols) }
        // Set finders to true to avoid double draw
        for (r in 0..4) {
            for (c in 0..4) { matrix[r][c] = true }
            for (c in (cols - 5) until cols) { matrix[r][c] = true }
        }
        for (r in (cols - 5) until cols) {
            for (c in 0..4) { matrix[r][c] = true }
        }

        // Draw random fill elements
        for (r in 0 until cols) {
            for (c in 0 until cols) {
                if (matrix[r][c]) continue
                // Pseudo random based on simple hash
                val filled = (r * 3 + c * 7) % 5 < 2
                if (filled) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(c * cellSize, r * cellSize),
                        size = Size(cellSize + 0.5f, cellSize + 0.5f) // overlapping offset prevents grid lines
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFinderPattern(
    x: Float,
    y: Float,
    cellSize: Float
) {
    // Large outer ring
    drawRect(
        color = Color.Black,
        topLeft = Offset(x, y),
        size = Size(cellSize * 5, cellSize * 5)
    )
    // Inner white ring
    drawRect(
        color = Color.White,
        topLeft = Offset(x + cellSize, y + cellSize),
        size = Size(cellSize * 3, cellSize * 3)
    )
    // Center filled block
    drawRect(
        color = Color.Black,
        topLeft = Offset(x + cellSize * 1.5f, y + cellSize * 1.5f),
        size = Size(cellSize * 2, cellSize * 2)
    )
}

@Composable
fun StepRow(stepNumber: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
