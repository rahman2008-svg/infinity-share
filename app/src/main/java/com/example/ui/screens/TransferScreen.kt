package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.ShareableFile
import com.example.ui.ShareViewModel
import com.example.ui.theme.*
import kotlin.math.sin
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    viewModel: ShareViewModel,
    onFinish: () -> Unit
) {
    val activeTransfer by viewModel.activeTransfer.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()

    val fileList = remember { selectedFiles.toList() }
    var currentFileIndex by remember { mutableStateOf(0) }
    var totalFilesSentSize by remember { mutableStateOf(0L) }

    // Aggregate progress
    val totalSizeToTransfer = remember { fileList.sumOf { it.size } }

    val currentFileProgress = activeTransfer
    val currentFileTransferred = currentFileProgress?.bytesTransferred ?: 0L
    val aggregateTransferred = totalFilesSentSize + currentFileTransferred
    
    val aggregateProgress = if (totalSizeToTransfer > 0) {
        aggregateTransferred.toFloat() / totalSizeToTransfer.toFloat()
    } else {
        0f
    }

    // Keep track of index transitions
    LaunchedEffect(currentFileProgress) {
        if (currentFileProgress != null) {
            val idx = fileList.indexOfFirst { it.name == currentFileProgress.fileName }
            if (idx != -1 && idx != currentFileIndex) {
                // We moved to the next file! Accumulate prior sizes.
                var accum = 0L
                for (i in 0 until idx) {
                    accum += fileList[i].size
                }
                totalFilesSentSize = accum
                currentFileIndex = idx
            }
        }
    }

    val isFinished = aggregateProgress >= 0.999f || currentFileProgress?.isComplete == true && currentFileIndex == fileList.size - 1

    // Run simulated transfer if simulation mode is active
    val isSimulationMode by viewModel.isSimulationMode.collectAsState()
    LaunchedEffect(Unit) {
        if (isSimulationMode) {
            viewModel.runSimulatedTransfer {
                // Completed!
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isFinished) "Transfer Complete" else "Transferring Files",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Speed indicator and overview
            Spacer(modifier = Modifier.height(8.dp))
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
                        text = if (isFinished) "Finished sharing" else "Transfer speed",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isFinished) "0.0 MB/s" else String.format(Locale.US, "%.1f MB/s", currentFileProgress?.speedMbps ?: 14.8),
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Floating particle visual bridge animation
                    TransferBridgeAnimation(isTransferring = !isFinished)

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "To: ${connectedDevice?.name ?: "Web Client"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${currentFileIndex + 1}/${fileList.size} Files",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Visual Progress Indicators
                    LinearProgressIndicator(
                        progress = { if (isFinished) 1f else aggregateProgress },
                        color = BlueSecondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .testTag("transfer_progress_bar")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isFinished) "All files sent" else "Transferred ${formatSize(aggregateTransferred)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Total: ${formatSize(totalSizeToTransfer)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable list of files in queue
            Text(
                text = "Transfer Queue",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(fileList.indices.toList()) { index ->
                    val file = fileList[index]
                    val isPending = index > currentFileIndex
                    val isCurrent = index == currentFileIndex && !isFinished
                    val isDone = index < currentFileIndex || isFinished

                    QueueItemRow(
                        file = file,
                        isPending = isPending,
                        isCurrent = isCurrent,
                        isDone = isDone,
                        currentProgress = if (isCurrent) (currentFileTransferred.toFloat() / file.size.toFloat()) else 0f
                    )
                }
            }

            // Bottom action bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = onFinish,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFinished) SuccessGreen else BluePrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_transfer_finish")
                ) {
                    Text(
                        text = if (isFinished) "DONE" else "CANCEL TRANSFER",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TransferBridgeAnimation(isTransferring: Boolean) {
    val transition = rememberInfiniteTransition(label = "particles")
    val p1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "p1"
    )
    val p2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Restart
        ),
        label = "p2"
    )
    val p3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "p3"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        val yCenter = size.height / 2f
        val startX = 40.dp.toPx()
        val endX = size.width - 40.dp.toPx()
        val length = endX - startX

        // Draw Sender & Receiver Nodes on Canvas
        drawCircle(
            color = BluePrimary,
            radius = 16.dp.toPx(),
            center = Offset(startX, yCenter)
        )
        drawCircle(
            color = CyanAccent,
            radius = 16.dp.toPx(),
            center = Offset(endX, yCenter)
        )

        // Draw visual stream particles
        if (isTransferring) {
            drawCircle(
                color = VioletAccent,
                radius = 5.dp.toPx(),
                center = Offset(startX + length * p1, yCenter + sin(p1 * Math.PI).toFloat() * 10.dp.toPx())
            )
            drawCircle(
                color = CyanAccent,
                radius = 4.dp.toPx(),
                center = Offset(startX + length * p2, yCenter - sin(p2 * Math.PI).toFloat() * 12.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(startX + length * p3, yCenter)
            )
        }
    }
}

@Composable
fun QueueItemRow(
    file: ShareableFile,
    isPending: Boolean,
    isCurrent: Boolean,
    isDone: Boolean,
    currentProgress: Float
) {
    val rowIcon = when (file.type) {
        "APP" -> Icons.Default.Android
        "IMAGE" -> Icons.Default.Image
        "VIDEO" -> Icons.Default.PlayCircle
        "MUSIC" -> Icons.Default.MusicNote
        "DOCUMENT" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }

    val themeColor = when (file.type) {
        "APP" -> SuccessGreen
        "IMAGE" -> CyanAccent
        "VIDEO" -> VioletAccent
        "MUSIC" -> BlueSecondary
        "DOCUMENT" -> ProgressYellow
        else -> BluePrimary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) themeColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            1.dp,
            if (isCurrent) themeColor else Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(themeColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = rowIcon,
                    contentDescription = file.type,
                    tint = themeColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatSize(file.size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                    )
                    Text(
                        text = when {
                            isDone -> "Sent"
                            isCurrent -> "Transferring... ${(currentProgress * 100).toInt()}%"
                            else -> "Pending"
                        },
                        fontSize = 11.sp,
                        color = if (isCurrent) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (isCurrent) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { currentProgress },
                        color = themeColor,
                        trackColor = themeColor.copy(alpha = 0.15f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isDone -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Done",
                        tint = SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    isCurrent -> CircularProgressIndicator(
                        color = themeColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    else -> Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Pending",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
