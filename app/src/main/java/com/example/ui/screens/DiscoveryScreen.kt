package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.ServerState
import com.example.ui.DiscoveredDevice
import com.example.ui.ShareViewModel
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: ShareViewModel,
    onBackClick: () -> Unit,
    onDeviceConnected: () -> Unit
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val isSimulationMode by viewModel.isSimulationMode.collectAsState()
    val serverState by viewModel.serverState.collectAsState()

    var showConnectionModal by remember { mutableStateOf(false) }
    var selectedConnectingDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

    // Start discovery when entering screen
    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
        if (!isSimulationMode) {
            viewModel.startHostingServer()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Device Discovery",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopDiscovery()
                            viewModel.stopHostingServer()
                            onBackClick()
                        },
                        modifier = Modifier.testTag("discovery_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Quick QR/Web Share guide toggle
                    IconButton(onClick = { /* Help link */ }) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "QR Code",
                            tint = MaterialTheme.colorScheme.primary
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
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Instructions
                Text(
                    text = if (isSimulationMode) "Scanning in Emulator Simulation Mode" else "Real Web Sharing Active",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Text(
                    text = if (isSimulationMode) "Tap a device on the radar to simulate transfer" else "Open browser or scan the QR Code",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                // The Radar scanning system
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    RadarCanvas(isScanning = isScanning)

                    // Central glowing node representing our device
                    CentralNode()

                    // Render Discovered devices as floating nodes
                    discoveredDevices.forEachIndexed { index, device ->
                        // Calculate orbital distribution offsets
                        val angle = (index * 75 + 45) * (Math.PI / 180.0)
                        val radius = 110.dp + (index * 25).dp
                        val offsetDx = (radius.value * cos(angle)).dp
                        val offsetDy = (radius.value * sin(angle)).dp

                        FloatingDeviceNode(
                            device = device,
                            modifier = Modifier
                                .offset(x = offsetDx, y = offsetDy),
                            onClick = {
                                selectedConnectingDevice = device
                                showConnectionModal = true
                                viewModel.connectToDevice(device) {
                                    showConnectionModal = false
                                    viewModel.stopDiscovery()
                                    onDeviceConnected()
                                }
                            }
                        )
                    }
                }

                // Simulation mode selector card at the bottom
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Mode",
                                tint = CyanAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Simulation Fallback Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Generates responsive peer nodes for sandbox testing",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isSimulationMode,
                                onCheckedChange = { checked ->
                                    viewModel.setSimulationMode(checked)
                                    if (checked) {
                                        viewModel.stopHostingServer()
                                        viewModel.startDiscovery()
                                    } else {
                                        viewModel.startHostingServer()
                                    }
                                },
                                modifier = Modifier.testTag("toggle_simulation_mode")
                            )
                        }

                        // Display real server parameters if Web Share is active
                        if (!isSimulationMode) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            when (val state = serverState) {
                                is ServerState.Running -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "HTTP Web Sharing active at:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen
                                        )
                                        Text(
                                            text = "http://${state.ip}:${state.port}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        Text(
                                            text = "Connect devices to the same Wi-Fi network.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                                is ServerState.Idle -> {
                                    Text("Starting HTTP sharing server...", fontSize = 12.sp, color = ProgressYellow)
                                }
                                is ServerState.Error -> {
                                    Text("Server setup failed: ${state.message}", fontSize = 12.sp, color = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }

            // High-fidelity Connecting Handshake dialog
            if (showConnectionModal && selectedConnectingDevice != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .width(300.dp)
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Initiating Connection",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Establishing secure peer-to-peer handshaking with ${selectedConnectingDevice!!.name}...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RadarCanvas(isScanning: Boolean) {
    val transition = rememberInfiniteTransition(label = "radar")
    
    // Rotating sweep line angle
    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulsing circle radius scale
    val pulseScale by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val themePrimaryColor = MaterialTheme.colorScheme.primary
    val gridColor = themePrimaryColor.copy(alpha = 0.15f)
    val radarBrush = Brush.sweepGradient(
        colors = listOf(
            Color.Transparent,
            themePrimaryColor.copy(alpha = 0.25f),
            Color.Transparent
        )
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("radar_drawing_canvas")
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = minOf(size.width, size.height) / 2.2f

        // Draw static grid rings
        for (i in 1..4) {
            drawCircle(
                color = gridColor,
                radius = maxRadius * (i / 4f),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Draw static grid cross-lines
        drawLine(
            color = gridColor,
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridColor,
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = 1.dp.toPx()
        )

        // Draw pulsing expanding radar ring
        if (isScanning) {
            drawCircle(
                color = themePrimaryColor.copy(alpha = (1f - pulseScale) * 0.4f),
                radius = maxRadius * pulseScale,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw rotating scanning beam
            rotate(degrees = rotationAngle, pivot = center) {
                drawCircle(
                    brush = radarBrush,
                    radius = maxRadius,
                    center = center
                )
                // Draw leading glowing edge
                drawLine(
                    color = themePrimaryColor.copy(alpha = 0.6f),
                    start = center,
                    end = Offset(center.x, center.y - maxRadius),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun CentralNode() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(
                Brush.radialGradient(listOf(BluePrimary, VioletAccent)),
                CircleShape
            )
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Phonelink,
            contentDescription = "Me",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun FloatingDeviceNode(
    device: DiscoveredDevice,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val avatarColors = listOf(BlueSecondary, CyanAccent, VioletAccent, ProgressYellow)
    val themeColor = avatarColors[device.avatarColorIndex % avatarColors.size]

    val icon = when (device.deviceType) {
        "PHONE" -> Icons.Default.Smartphone
        "TABLET" -> Icons.Default.TabletMac
        "LAPTOP" -> Icons.Default.Computer
        else -> Icons.Default.Phonelink
    }

    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingOffset"
    )

    Column(
        modifier = modifier
            .offset(y = floatOffset.dp)
            .clickable(onClick = onClick)
            .testTag("discovered_node_${device.name}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Slate800, CircleShape)
                .border(2.dp, themeColor, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = device.name,
                    tint = themeColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Slate800),
            modifier = Modifier.widthIn(max = 110.dp)
        ) {
            Text(
                text = device.name,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
