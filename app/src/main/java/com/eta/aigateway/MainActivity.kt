package com.eta.aigateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.engine.UsageRecorder
import com.eta.aigateway.ui.chat.ChatScreen
import com.eta.aigateway.ui.chat.ChatViewModel
import com.eta.aigateway.ui.dashboard.DashboardScreen
import com.eta.aigateway.ui.dashboard.DashboardViewModel
import com.eta.aigateway.ui.settings.SettingsScreen
import com.eta.aigateway.ui.settings.SettingsViewModel
import com.eta.aigateway.ui.theme.AIGatewayTheme

class MainActivity : ComponentActivity() {

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        UsageRecorder.init(AppDatabase.getInstance(this))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            AIGatewayTheme {
                AppRoot()
            }
        }
    }
}

private enum class Screen { CHAT, SETTINGS, DASHBOARD }

@Composable
fun AppRoot() {
    val chatVm: ChatViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val dashVm: DashboardViewModel = viewModel()
    var screen by remember { mutableStateOf(Screen.CHAT) }
    val st by settingsVm.state.collectAsState()

    LaunchedEffect(st.providers, st.modelCache, st.customModels) {
        chatVm.refreshModels(st.modelCache, st.providers, st.customModels)
    }

    when (screen) {
        Screen.CHAT -> ChatScreen(
            vm = chatVm,
            availableModels = chatVm.allAvailableModels(),
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenDashboard = { screen = Screen.DASHBOARD }
        )
        Screen.SETTINGS -> SettingsScreen(settingsVm) { screen = Screen.CHAT }
        Screen.DASHBOARD -> DashboardScreen(dashVm) { screen = Screen.CHAT }
    }
}
