package com.eta.aigateway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eta.aigateway.data.repository.SettingsRepository
import com.eta.aigateway.ui.chat.ChatScreen
import com.eta.aigateway.ui.chat.ChatViewModel
import com.eta.aigateway.ui.settings.SettingsScreen
import com.eta.aigateway.ui.settings.SettingsViewModel
import com.eta.aigateway.ui.theme.AIGatewayTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIGatewayTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val chatVm: ChatViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    var showSettings by remember { mutableStateOf(false) }

    val providers by settingsVm.state.collectAsState()
    val repo = remember { SettingsRepository(ctx) }

    LaunchedEffect(providers.providers, providers.modelCache) {
        chatVm.refreshModels(providers.modelCache, providers.providers)
    }

    if (showSettings) {
        SettingsScreen(settingsVm) { showSettings = false }
    } else {
        ChatScreen(
            vm = chatVm,
            availableModels = chatVm.allAvailableModels(),
            onOpenSettings = { showSettings = true }
        )
    }
}
