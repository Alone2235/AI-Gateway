package com.eta.aigateway.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.data.model.ProviderConfig
import com.eta.aigateway.ui.theme.*

@Composable
fun ProviderCard(
    p: ProviderConfig,
    modelCount: Int,
    loading: Boolean,
    onKeysChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onFetch: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var expanded by remember(p.id) { mutableStateOf(false) }
    var keysText by remember(p.id) { mutableStateOf(p.apiKeys.joinToString("\n")) }
    var baseUrl by remember(p.id) { mutableStateOf(p.baseUrl) }
    var showKeys by remember(p.id) { mutableStateOf(false) }
    var confirmDelete by remember(p.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .glassStatic(Dimens.cardRadius, if (expanded) GlassLevel.Strong else GlassLevel.Weak)
            .padding(Dimens.cardPadding)
    ) {
        ProviderHeader(p, modelCount, loading, expanded, onToggle) { expanded = !expanded }

        if (expanded) {
            Spacer(Modifier.height(Dimens.gap12))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(Modifier.height(Dimens.gap12))
            ProviderFields(
                p = p, baseUrl = baseUrl, keysText = keysText, showKeys = showKeys,
                loading = loading,
                onBaseUrl = { baseUrl = it },
                onBaseUrlCommit = { if (it != p.baseUrl) onBaseUrlChange(it) },
                onKeys = { keysText = it; onKeysChange(it) },
                onShowKeys = { showKeys = it },
                onTest = onTest, onFetch = onFetch,
                onDelete = onDelete?.let { { confirmDelete = true } }
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除接口") },
            text = { Text("确定删除「${p.name}」？已拉取的模型列表也会一并清除。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ProviderHeader(
    p: ProviderConfig, modelCount: Int, loading: Boolean,
    expanded: Boolean, onToggle: (Boolean) -> Unit, onExpand: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable { onExpand() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, fontWeight = FontWeight.Medium, fontSize = Fonts.bodyStrong)
                if (p.protocol == "anthropic") {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text("A", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
            }
            Text(
                when {
                    modelCount > 0 -> "$modelCount 个模型"
                    p.apiKeys.isEmpty() -> "未配置 Key"
                    else -> "未拉取模型"
                },
                fontSize = Fonts.micro,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
            )
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Switch(checked = p.enabled, onCheckedChange = onToggle, modifier = Modifier.height(28.dp))
        }
        IconButton(onClick = onExpand, modifier = Modifier.size(36.dp)) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                "展开", modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ProviderFields(
    p: ProviderConfig,
    baseUrl: String,
    keysText: String,
    showKeys: Boolean,
    loading: Boolean,
    onBaseUrl: (String) -> Unit,
    onBaseUrlCommit: (String) -> Unit,
    onKeys: (String) -> Unit,
    onShowKeys: (Boolean) -> Unit,
    onTest: () -> Unit,
    onFetch: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Column {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrl,
            label = { Text("Base URL", fontSize = Fonts.micro) },
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (!it.isFocused) onBaseUrlCommit(baseUrl)
            },
            singleLine = true,
            shape = RoundedCornerShape(Dimens.controlRadius)
        )
        Spacer(Modifier.height(Dimens.gap8))
        OutlinedTextField(
            value = keysText,
            onValueChange = onKeys,
            label = { Text("API Key（每行一个）", fontSize = Fonts.micro) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(Dimens.controlRadius),
            visualTransformation = if (showKeys) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { onShowKeys(!showKeys) }) {
                    Icon(
                        if (showKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "显示", modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
        Spacer(Modifier.height(Dimens.gap12))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap8)) {
            OutlinedButton(
                onClick = onTest, enabled = !loading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Dimens.controlRadius)
            ) { Text("测试连接", fontSize = Fonts.caption) }
            Button(
                onClick = onFetch, enabled = !loading,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Dimens.controlRadius)
            ) { Text("拉取模型", fontSize = Fonts.caption) }
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text("删除此接口", fontSize = Fonts.caption, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
