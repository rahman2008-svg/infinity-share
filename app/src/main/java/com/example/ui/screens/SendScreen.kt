package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    viewModel: ShareViewModel,
    onBackClick: () -> Unit,
    onProceedToSend: () -> Unit
) {
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val presetFiles = viewModel.presetFiles

    var selectedTab by remember { mutableStateOf(0) }
    val categories = listOf("APPS", "PHOTOS", "VIDEOS", "MUSIC", "DOCS")
    val categoryKeys = listOf("APP", "IMAGE", "VIDEO", "MUSIC", "DOCUMENT")

    val filteredFiles = remember(selectedTab) {
        presetFiles.filter { it.type == categoryKeys[selectedTab] }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Select Files",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("send_back_button")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Beautiful scrolling category tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("tab_$title")
                        )
                    }
                }

                // Grid of files for selected category
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (selectedTab == 1 || selectedTab == 2) 3 else 2),
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredFiles) { file ->
                        val isChecked = selectedFiles.any { it.id == file.id }
                        FileItemCard(
                            file = file,
                            isChecked = isChecked,
                            onClick = { viewModel.toggleFileSelection(file) }
                        )
                    }
                }

                // Bottom Glow Send bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                            )
                        )
                        .padding(16.dp)
                        .navigationBarsPadding()
                ) {
                    val totalSize = selectedFiles.sumOf { it.size }
                    val filesCount = selectedFiles.size

                    Button(
                        onClick = onProceedToSend,
                        enabled = filesCount > 0,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BluePrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("btn_proceed_send")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (filesCount > 0) "SEND ($filesCount files)" else "Select items",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (filesCount > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (filesCount > 0) {
                                Text(
                                    text = formatSize(totalSize),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = CyanAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileItemCard(
    file: ShareableFile,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    val fileIcon = when (file.type) {
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
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.5.dp,
            if (isChecked) themeColor else MaterialTheme.colorScheme.surfaceVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) themeColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("file_card_${file.name}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                // Custom check indicator
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            if (isChecked) themeColor else Color.Transparent,
                            CircleShape
                        )
                        .border(
                            1.5.dp,
                            if (isChecked) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (file.type == "APP") Slate900 else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Central Icon
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(themeColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = file.name,
                    tint = themeColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // File Details
            Text(
                text = file.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = formatSize(file.size),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
