package com.eta.aigateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.eta.aigateway.ui.theme.DeepSeaBackground
import com.eta.aigateway.ui.theme.LocalBackdrop
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.engine.UsageRecorder
import com.eta.aigateway.ui.chat.ChatScreen
import com.eta.aigateway.ui.chat.ChatViewModel
import com.eta.aigateway.ui.dashboard.DashboardScreen
import com.eta.aigateway.ui.dashboard.DashboardViewModel
import com.eta.aigateway.ui.models.ModelsScreen
import com.eta.aigateway.ui.models.ModelsViewModel
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

private enum class Screen { CHAT, SETTINGS, DASHBOARD, MODELS, ABOUT }

@Composable
fun AppRoot() {
    val chatVm: ChatViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val dashVm: DashboardViewModel = viewModel()
    val modelsVm: ModelsViewModel = viewModel()
    var screen by remember { mutableStateOf(Screen.CHAT) }

    val backdrop = rememberLayerBackdrop()

    LaunchedEffect(screen) {
        if (screen == Screen.CHAT) chatVm.refreshGroups()
    }

    // 导出层：背景 + 内容都进离屏画布，供玻璃折射
    Box(Modifier.fillMaxSize()) {
        // 背景画在导出层内
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            DeepSeaBackground {
                Box(Modifier.fillMaxSize()) { }
            }
        }
        // 内容层叠在背景之上，玻璃从这里引用 backdrop
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                when (screen) {
                    Screen.CHAT -> {
                        val groups by chatVm.groups.collectAsState()
                        ChatScreen(
                            vm = chatVm,
                            groups = groups,
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onOpenDashboard = { screen = Screen.DASHBOARD },
                            onOpenModels = { screen = Screen.SETTINGS }
                        )
                    }
                    Screen.SETTINGS -> SettingsScreen(
                        settingsVm,
                        onBack = { screen = Screen.CHAT },
                        onOpenAbout = { screen = Screen.ABOUT }
                    )
                    Screen.DASHBOARD -> DashboardScreen(dashVm) { screen = Screen.CHAT }
                    Screen.MODELS -> ModelsScreen(modelsVm) { screen = Screen.CHAT }
                    Screen.ABOUT -> com.eta.aigateway.ui.about.AboutScreen { screen = Screen.SETTINGS }
                }
            }
        }
    }
}
