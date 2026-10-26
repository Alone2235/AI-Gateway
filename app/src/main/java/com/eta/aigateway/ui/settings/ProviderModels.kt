package com.eta.aigateway.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.data.model.ModelClassifier
import com.eta.aigateway.ui.theme.*

@Composable
fun ProviderModelsInline(
    providerId: String,
    models: List<String>,
    enabledModels: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onEnableVendors: () -> Unit
) {
    var query by remember(providerId) { mutableStateOf("") }
    var expanded by remember(providerId) { mutableStateOf(models.size <= 30) }

    val filtered = remember(query, models) {
        if (query.isBlank()) models else models.filter { it.contains(query, true) }
    }
    val enabledCount = models.count { it in enabledModels }

    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Spacer(Modifier.height(Dimens.gap8))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                Text("模型列表", fontSize = Fonts.caption, fontWeight = FontWeight.Medium)
                Text(
                    "已启用 $enabledCount / ${models.size}",
                    fontSize = Fonts.micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
                )
            }
            TextButton(onClick = onEnableVendors) {
                Text("识别大厂", fontSize = Fonts.micro)
            }
        }
        if (expanded && models.isNotEmpty()) {
            Spacer(Modifier.height(Dimens.gap8))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索", fontSize = Fonts.micro) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(Dimens.controlRadius)
            )
            Spacer(Modifier.height(Dimens.gap8))
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(filtered, key = { it }) { mid ->
                    ModelRow(mid, mid in enabledModels) { onToggle(mid, it) }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(modelId: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    val vendor = ModelClassifier.vendorOf(modelId)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheck(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheck, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Dimens.gap8))
        Column(Modifier.weight(1f)) {
            Text(
                ModelClassifier.prettyName(modelId),
                fontSize = Fonts.caption,
                maxLines = 1
            )
            Text(
                modelId,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.tertiary),
                maxLines = 1
            )
        }
        vendor?.let {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Text(
                    it,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
