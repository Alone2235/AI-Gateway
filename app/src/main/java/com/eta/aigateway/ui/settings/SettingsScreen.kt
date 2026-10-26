package com.eta.aigateway.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.data.model.ProviderConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "接口配置",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            items(state.providers) { p ->
                ProviderCard(
                    p = p,
                    modelCount = state.modelCache[p.id]?.size ?: 0,
                    loading = state.loading == p.id,
                    onKeysChange = { vm.setKeys(p.id, it) },
                    onBaseUrlChange = { vm.setBaseUrl(p.id, it) },
                    onToggle = { vm.toggleEnabled(p.id, it) },
                    onTest = { vm.test(p.id) },
                    onFetch = { vm.fetchModels(p.id) }
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                GatewaySettingsSection(vm)
            }
            item {
                Spacer(Modifier.height(8.dp))
                CustomModelSection(vm, state.providers, state.customModels)
            }
            item {
                Text(
                    "降级策略",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            item {
                Text(
                    "每个模型可单独设置。默认「换 Key」：失败时先试同 provider 的其他 key。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    p: ProviderConfig,
    modelCount: Int,
    loading: Boolean,
    onKeysChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onFetch: () -> Unit
) {
    var expanded by remember(p.id) { mutableStateOf(false) }
    var keysText by remember(p.id) { mutableStateOf(p.apiKeys.joinToString("\n")) }
    var baseUrl by remember(p.id) { mutableStateOf(p.baseUrl) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        if (modelCount > 0) "$modelCount 个模型" else "未拉取模型",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Switch(checked = p.enabled, onCheckedChange = onToggle)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "展开"
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (!it.isFocused && baseUrl != p.baseUrl) onBaseUrlChange(baseUrl)
                    },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keysText,
                    onValueChange = { keysText = it; onKeysChange(it) },
                    label = { Text("API Key（每行一个，可填多个轮换）", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onTest, enabled = !loading, modifier = Modifier.weight(1f)) {
                        Text("测试连接", fontSize = 13.sp)
                    }
                    Button(onClick = onFetch, enabled = !loading, modifier = Modifier.weight(1f)) {
                        Text("拉取模型", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
