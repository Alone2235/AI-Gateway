package com.eta.aigateway.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.data.model.ModelEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<ModelEntry>,
    current: ModelEntry?,
    onSelect: (ModelEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, models) {
        if (query.isBlank()) models
        else models.filter { it.id.contains(query, true) || it.providerName.contains(query, true) }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.providerName } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("选择模型", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索模型 / provider", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            ModelList(models, grouped, current, onSelect)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModelList(
    all: List<ModelEntry>,
    grouped: Map<String, List<ModelEntry>>,
    current: ModelEntry?,
    onSelect: (ModelEntry) -> Unit
) {
    if (all.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("还没有可用模型", fontWeight = FontWeight.Medium)
            Text(
                "去设置里填 API Key 然后拉取模型列表",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(Modifier.heightIn(max = 480.dp)) {
        grouped.forEach { (providerName, list) ->
            item(key = "h_$providerName") {
                Text(
                    providerName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }
            items(list, key = { "${it.providerId}_${it.id}" }) { m ->
                val isSel = current?.id == m.id && current.providerId == m.providerId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(m) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(m.id, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (isSel) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        }
    }
}
