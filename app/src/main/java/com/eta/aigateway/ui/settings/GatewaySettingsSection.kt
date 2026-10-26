package com.eta.aigateway.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eta.aigateway.gateway.GatewayService
import java.util.UUID

@Composable
fun GatewaySettingsSection(vm: SettingsViewModel) {
    val ctx = LocalContext.current
    val st by vm.state.collectAsState()

    var portText by remember(st.gatewayPort) { mutableStateOf(st.gatewayPort.toString()) }
    var keysText by remember(st.gatewayKeys) { mutableStateOf(st.gatewayKeys.joinToString("\n")) }
    var showKeys by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(GatewayService.isRunning) }

    LaunchedEffect(Unit) {
        while (true) {
            running = GatewayService.isRunning
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "对外网关",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp
        )
        GatewayCard(vm, ctx, running, portText, keysText, showKeys, st,
            onPort = { portText = it },
            onKeys = { keysText = it },
            onShowKeys = { showKeys = it },
            onRunning = { running = it })
    }
}

@Composable
private fun GatewayCard(
    vm: SettingsViewModel,
    ctx: Context,
    running: Boolean,
    portText: String,
    keysText: String,
    showKeys: Boolean,
    st: SettingsUiState,
    onPort: (String) -> Unit,
    onKeys: (String) -> Unit,
    onShowKeys: (Boolean) -> Unit,
    onRunning: (Boolean) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (running) "运行中" else "未运行",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (running) "http://127.0.0.1:${GatewayService.runningPort}/v1"
                        else "启动后其他客户端可连",
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = running,
                    onCheckedChange = { on ->
                        if (on) startGateway(ctx) else stopGateway(ctx)
                        onRunning(on)
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            OutlinedTextField(
                value = portText,
                onValueChange = { t ->
                    val f = t.filter { it.isDigit() }.take(5)
                    onPort(f)
                    f.toIntOrNull()?.let { if (it in 1024..65535) vm.setGatewayPort(it) }
                },
                label = { Text("端口", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许局域网访问", fontSize = 13.sp)
                    Text(
                        "关闭则只有本机能连",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = st.gatewayAllowLan,
                    onCheckedChange = { vm.setGatewayAllowLan(it) }
                )
            }

            OutlinedTextField(
                value = keysText,
                onValueChange = {
                    onKeys(it)
                    vm.setGatewayKeys(it.lines().map { k -> k.trim() }.filter { k -> k.isNotBlank() })
                },
                label = { Text("访问密钥（每行一个，留空则无需鉴权）", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                visualTransformation = if (showKeys) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { onShowKeys(!showKeys) }) {
                        Icon(
                            if (showKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            "显示"
                        )
                    }
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val k = "sk-gw-" + UUID.randomUUID().toString().replace("-", "").take(24)
                        onKeys(k)
                        vm.setGatewayKeys(listOf(k))
                        onShowKeys(true)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("生成密钥", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        onKeys("")
                        vm.setGatewayKeys(emptyList())
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("清空", fontSize = 13.sp) }
            }

            Text(
                "外部客户端填：\nBase URL: http://<手机IP>:${st.gatewayPort}/v1\nAPI Key: 上面生成的密钥",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun startGateway(ctx: Context) {
    val i = Intent(ctx, GatewayService::class.java).apply {
        action = GatewayService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(ctx, i)
    } else {
        ctx.startService(i)
    }
}

fun stopGateway(ctx: Context) {
    ctx.stopService(Intent(ctx, GatewayService::class.java))
}
