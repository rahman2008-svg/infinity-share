package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.ShareViewModel
import com.example.ui.ShareViewModelFactory
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val shareViewModel: ShareViewModel by viewModels {
        ShareViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavigationHost(
                        viewModel = shareViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationHost(
    viewModel: ShareViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = modifier
    ) {
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onSendClick = {
                    navController.navigate("send")
                },
                onReceiveClick = {
                    // Receiver can look for nearby sending devices
                    navController.navigate("discovery")
                },
                onHistoryClick = {
                    navController.navigate("history")
                },
                onWebShareClick = {
                    navController.navigate("web_share")
                }
            )
        }

        composable("send") {
            SendScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onProceedToSend = {
                    navController.navigate("discovery")
                }
            )
        }

        composable("discovery") {
            DiscoveryScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onDeviceConnected = {
                    navController.navigate("transfer")
                }
            )
        }

        composable("transfer") {
            TransferScreen(
                viewModel = viewModel,
                onFinish = {
                    // Reset to dashboard when done
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                }
            )
        }

        composable("web_share") {
            WebShareScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
