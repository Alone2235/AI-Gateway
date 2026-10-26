package com.eta.aigateway.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.data.model.CustomModel
import com.eta.aigateway.data.model.ProviderConfig
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModelSection(
    vm: SettingsViewModel,
    providers: List<ProviderConfig>,
    customs: List<CustomModel>
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "自定义模型",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Text("添加", fontSize = 12.sp)
            }
        }

        if (customs.isEmpty()) {
            Text(
                "拉不到模型的接口可以手动添加，并给模型取个好认的名字",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            customs.forEach { m ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${m.modelId} · ${protocolLabel(m.protocol)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { vm.deleteCustomModel(m.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                "删除",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddCustomModelDialog(
            providers = providers,
            onDismiss = { showDialog = false },
            onConfirm = { modelId, display, pid, proto ->
                vm.addCustomModel(
                    CustomModel(
                        id = UUID.randomUUID().toString(),
                        modelId = modelId,
                        displayName = display,
                        providerId = pid,
                        protocol = proto
                    )
                )
                showDialog = false
            }
        )
    }
}

private fun protocolLabel(p: String) = if (p == "anthropic") "Anthropic" else "OpenAI"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomModelDialog(
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onConfirm: (modelId: String, display: String, providerId: String, protocol: String) -> Unit
) {
    val usable = providers.filter { it.enabled }
    var modelId by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(usable.firstOrNull()) }
    var protoExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("模型 ID（发给 API 的真实值）", fontSize = 11.sp) },
                    placeholder = { Text("claude-3-5-sonnet-20241022") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = display,
                    onValueChange = { display = it },
                    label = { Text("显示名（你自己取）", fontSize = 11.sp) },
                    placeholder = { Text("Claude 主力") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("所属接口", fontSize = 11.sp)
                Box {
                    OutlinedButton(
                        onClick = { protoExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selected?.name ?: "选择接口", fontSize = 13.sp)
                    }
                    DropdownMenu(
                        expanded = protoExpanded,
                        onDismissRequest = { protoExpanded = false }
                    ) {
                        usable.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Text("${p.name} · ${protocolLabel(p.protocol)}", fontSize = 13.sp)
                                },
                                onClick = { selected = p; protoExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (modelId.isNotBlank() && selected != null) {
                        onConfirm(
                            modelId.trim(),
                            display.ifBlank { modelId.trim() },
                            selected!!.id,
                            selected!!.protocol
                        )
                    }
                },
                enabled = modelId.isNotBlank() && selected != null
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
