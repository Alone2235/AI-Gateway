package com.eta.aigateway.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.data.model.ModelGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    groups: List<ModelGroup>,
    current: ModelGroup?,
    currentTier: com.eta.aigateway.data.model.VirtualTier?,
    onSelect: (ModelGroup) -> Unit,
    onSelectTier: (com.eta.aigateway.data.model.VirtualTier) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val enabled = remember(groups) { groups.filter { it.enabled } }
    val filtered = remember(query, enabled) {
        if (query.isBlank()) enabled
        else enabled.filter {
            it.modelId.contains(query, true) ||
                it.sources.any { s -> s.providerName.contains(query, true) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("选择模型", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "只显示已启用的模型 · 共 ${enabled.size} 个",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            TierPickerSection(groups, currentTier, onSelectTier)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索模型 / 来源", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            ModelGroupList(filtered, current, onSelect)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModelGroupList(
    list: List<ModelGroup>,
    current: ModelGroup?,
    onSelect: (ModelGroup) -> Unit
) {
    if (list.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("没有已启用的模型", fontWeight = FontWeight.Medium)
            Text(
                "去 设置 → 模型 里勾选要用的模型",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(Modifier.heightIn(max = 460.dp)) {
        items(list, key = { it.modelId }) { g ->
            val sel = current?.modelId == g.modelId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(g) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.displayName, fontSize = 14.sp, maxLines = 1)
                        if (g.sources.size > 1) {
                            Spacer(Modifier.width(6.dp))
                            Badge(g.sources.size)
                        }
                    }
                    Text(
                        g.sources.joinToString(" / ") { it.providerName },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (sel) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }
    }
}

@Composable
private fun Badge(count: Int) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            "$count 源",
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}
