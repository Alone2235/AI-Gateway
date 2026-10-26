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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenAbout: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showAddProvider by remember { mutableStateOf(false) }

    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); vm.clearToast() }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                title = { Text("设置", fontWeight = FontWeight.SemiBold, fontSize = Fonts.title1) },
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
            contentPadding = PaddingValues(
                start = Dimens.pagePadding,
                end = Dimens.pagePadding,
                top = Dimens.gap4,
                bottom = Dimens.gap24 * 2
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap8)
        ) {
            item { SectionHeader("接口", "填地址和 Key，拉取模型") }

            items(state.providers, key = { it.id }) { p ->
                ProviderCard(
                    p = p,
                    modelCount = state.modelCache[p.id]?.size ?: 0,
                    models = state.modelCache[p.id] ?: emptyList(),
                    enabledModels = state.enabledModels,
                    loading = state.loading == p.id,
                    onKeysChange = { vm.setKeys(p.id, it) },
                    onBaseUrlChange = { vm.setBaseUrl(p.id, it) },
                    onToggle = { vm.toggleEnabled(p.id, it) },
                    onTest = { vm.test(p.id) },
                    onFetch = { vm.fetchModels(p.id) },
                    onToggleModel = { mid, en -> vm.toggleModelEnabled(mid, en) },
                    onEnableVendors = { vm.enableVendorsFor(p.id) },
                    onDelete = if (!p.builtin) ({ vm.deleteCustomProvider(p.id) }) else null
                )
            }

            item {
                Button(
                    onClick = { showAddProvider = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.controlRadius)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.gap8))
                    Text("新建自定义接口", fontSize = Fonts.bodyStrong)
                }
            }

            item { SectionHeader("对话", "长对话的处理方式") }
            item { CompressSection(vm) }

            item { SectionHeader("对外网关", "让其它客户端连这台手机") }
            item { GatewaySettingsSection(vm) }

            item {
                OutlinedButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("关于与声明", fontSize = Fonts.caption) }
            }
            item { Spacer(Modifier.height(Dimens.gap16)) }
            item { VersionFooter() }
        }
    }

    if (showAddProvider) {
        AddProviderDialog(
            onDismiss = { showAddProvider = false },
            onConfirm = { name, url, proto ->
                vm.addCustomProvider(name, url, proto)
                showAddProvider = false
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = Dimens.gap12, bottom = Dimens.gap4)) {
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = Fonts.title2,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            subtitle,
            fontSize = Fonts.micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
        )
    }
}

@Composable
private fun VersionFooter() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ver = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    } catch (e: Exception) { "?" }
    Text(
        "AI Gateway v$ver",
        fontSize = Fonts.micro,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.tertiary),
        modifier = Modifier.padding(top = Dimens.gap8)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, protocol: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var proto by remember { mutableStateOf("openai") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建自定义接口", fontSize = Fonts.title2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap8)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称", fontSize = Fonts.micro) },
                    placeholder = { Text("我的中转站") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL", fontSize = Fonts.micro) },
                    placeholder = { Text("https://api.example.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap8)) {
                    FilterChip(
                        selected = proto == "openai",
                        onClick = { proto = "openai" },
                        label = { Text("OpenAI 兼容", fontSize = Fonts.caption) }
                    )
                    FilterChip(
                        selected = proto == "anthropic",
                        onClick = { proto = "anthropic" },
                        label = { Text("Anthropic", fontSize = Fonts.caption) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url, proto) },
                enabled = url.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
