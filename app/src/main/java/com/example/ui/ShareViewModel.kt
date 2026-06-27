package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HistoryDatabase
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import com.example.server.LocalShareServer
import com.example.server.ServerState
import com.example.server.ShareableFile
import com.example.server.TransferProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

// Representation of nearby devices discovered by Radar
data class DiscoveredDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val deviceType: String, // PHONE, TABLET, LAPTOP
    val signalStrength: Float, // 0.0 to 1.0
    val avatarColorIndex: Int
)

class ShareViewModel(application: Application) : AndroidViewModel(application) {

    private val historyDao = HistoryDatabase.getDatabase(application).historyDao()
    private val repository = HistoryRepository(historyDao)

    private val localServer = LocalShareServer(application, repository)

    // UI States
    val history = repository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val serverState = localServer.serverState
    val realActiveTransfer = localServer.activeTransfer

    // Combined active transfer (supports real or simulated)
    private val _simulatedActiveTransfer = MutableStateFlow<TransferProgress?>(null)
    
    val activeTransfer = combine(realActiveTransfer, _simulatedActiveTransfer) { real, sim ->
        real ?: sim
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Mode toggles
    private val _isSimulationMode = MutableStateFlow(true)
    val isSimulationMode = _isSimulationMode.asStateFlow()

    // File Selection
    private val _selectedFiles = MutableStateFlow<Set<ShareableFile>>(emptySet())
    val selectedFiles = _selectedFiles.asStateFlow()

    // Device Discovery
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<DiscoveredDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    // Static available list of preset sample files
    val presetFiles = listOf(
        ShareableFile("preset_1", "WhatsApp.apk", 44582912, "APP", "application/vnd.android.package-archive", "", isSample = true),
        ShareableFile("preset_2", "SHAREit_Clone.apk", 18239482, "APP", "application/vnd.android.package-archive", "", isSample = true),
        ShareableFile("preset_3", "Infinity_Share_v1.apk", 15400293, "APP", "application/vnd.android.package-archive", "", isSample = true),
        ShareableFile("preset_4", "summer_beach_photo.jpg", 3450293, "IMAGE", "image/jpeg", "", isSample = true),
        ShareableFile("preset_5", "family_portrait.png", 5293048, "IMAGE", "image/png", "", isSample = true),
        ShareableFile("preset_6", "neon_cyber_city.jpg", 1450293, "IMAGE", "image/jpeg", "", isSample = true),
        ShareableFile("preset_7", "matrix_rain_wallpaper.png", 2293048, "IMAGE", "image/png", "", isSample = true),
        ShareableFile("preset_8", "cinematic_action_scene.mp4", 450293048, "VIDEO", "video/mp4", "", isSample = true),
        ShareableFile("preset_9", "lofi_beats_session.mp3", 12493048, "MUSIC", "audio/mpeg", "", isSample = true),
        ShareableFile("preset_10", "gaming_highlights_60fps.mp4", 185029304, "VIDEO", "video/mp4", "", isSample = true),
        ShareableFile("preset_11", "electro_swing_remix.mp3", 8493048, "MUSIC", "audio/mpeg", "", isSample = true),
        ShareableFile("preset_12", "annual_project_report.pdf", 1893048, "DOCUMENT", "application/pdf", "", isSample = true),
        ShareableFile("preset_13", "resume_cv_templates.zip", 4893048, "DOCUMENT", "application/zip", "", isSample = true),
        ShareableFile("preset_14", "database_backup.sql", 3289304, "DOCUMENT", "application/sql", "", isSample = true)
    )

    private var simulationJob: Job? = null

    init {
        // Start discovering devices initially or on request
        startDiscovery()
    }

    fun setSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
    }

    fun toggleFileSelection(file: ShareableFile) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.any { it.id == file.id }) {
            current.removeAll { it.id == file.id }
        } else {
            current.add(file)
        }
        _selectedFiles.value = current
        
        // Sync to local server shareable list
        localServer.setFilesToShare(current.toList())
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
        localServer.setFilesToShare(emptyList())
    }

    fun startDiscovery() {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        viewModelScope.launch {
            // Simulate device discovery radar finding devices over 1-5 seconds
            delay(1000)
            _discoveredDevices.value = _discoveredDevices.value + DiscoveredDevice(name = "OnePlus 12", deviceType = "PHONE", signalStrength = 0.9f, avatarColorIndex = 0)
            delay(1500)
            _discoveredDevices.value = _discoveredDevices.value + DiscoveredDevice(name = "Lenovo Legion Tab", deviceType = "TABLET", signalStrength = 0.75f, avatarColorIndex = 1)
            delay(1200)
            _discoveredDevices.value = _discoveredDevices.value + DiscoveredDevice(name = "My Windows PC", deviceType = "LAPTOP", signalStrength = 0.85f, avatarColorIndex = 2)
            delay(1800)
            _discoveredDevices.value = _discoveredDevices.value + DiscoveredDevice(name = "Galaxy S24 Ultra", deviceType = "PHONE", signalStrength = 0.6f, avatarColorIndex = 3)
        }
    }

    fun stopDiscovery() {
        _isScanning.value = false
    }

    // Connect to a device
    fun connectToDevice(device: DiscoveredDevice, onConnected: () -> Unit) {
        _connectedDevice.value = device
        viewModelScope.launch {
            // Simple fake handshaking connection animation
            delay(1500)
            onConnected()
        }
    }

    // Start local server (Real share mode)
    fun startHostingServer() {
        localServer.startServer()
    }

    fun stopHostingServer() {
        localServer.stopServer()
    }

    // Run Simulated File Transfer (For perfect emulation visualization)
    fun runSimulatedTransfer(onComplete: () -> Unit) {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            val device = _connectedDevice.value ?: DiscoveredDevice(name = "Receiver PC", deviceType = "LAPTOP", signalStrength = 0.9f, avatarColorIndex = 0)
            val filesList = _selectedFiles.value.toList()
            if (filesList.isEmpty()) return@launch

            for (file in filesList) {
                // Insert IN_PROGRESS into history Room DB
                val historyId = repository.insert(
                    HistoryEntity(
                        fileName = file.name,
                        filePath = file.localPath.ifEmpty { "Simulation_Cache/${file.name}" },
                        fileSize = file.size,
                        fileType = file.type,
                        transferType = "SENT",
                        peerName = device.name,
                        status = "IN_PROGRESS"
                    )
                ).toInt()

                val fileSize = file.size
                var transferred = 0L
                val steps = 20
                val sizePerStep = fileSize / steps
                val startTime = System.currentTimeMillis()

                for (i in 1..steps) {
                    delay(120) // Fast transfer delay
                    transferred += sizePerStep
                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                    val speedMbps = if (elapsedSec > 0) (transferred * 8.0) / (elapsedSec * 1024 * 1024) else 15.6

                    _simulatedActiveTransfer.value = TransferProgress(
                        fileName = file.name,
                        totalBytes = fileSize,
                        bytesTransferred = transferred,
                        speedMbps = speedMbps,
                        direction = "SEND"
                    )
                }

                // Final step complete
                _simulatedActiveTransfer.value = TransferProgress(
                    fileName = file.name,
                    totalBytes = fileSize,
                    bytesTransferred = fileSize,
                    speedMbps = 0.0,
                    direction = "SEND",
                    isComplete = true
                )

                // Update Room db
                repository.updateStatus(historyId, "SUCCESS")
                delay(300)
            }

            _simulatedActiveTransfer.value = null
            clearSelection()
            onComplete()
        }
    }

    // Real trigger (If using web share QR, can start server and host)
    fun initiateWebShare() {
        startHostingServer()
    }

    // Clear history
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        localServer.stopServer()
        simulationJob?.cancel()
    }
}

// ViewModel factory to satisfy modern Android lifecycle injection
class ShareViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShareViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShareViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
