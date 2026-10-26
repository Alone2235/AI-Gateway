package com.eta.aigateway.ui.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.eta.aigateway.data.model.HealthStatus
import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.data.model.ModelSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(vm: ModelsViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(st.toast) {
        st.toast?.let { snackbar.showSnackbar(it); vm.clearToast() }
    }

    val filtered = remember(query, st.groups) {
        if (query.isBlank()) st.groups
        else st.groups.filter {
            it.modelId.contains(query, true) ||
                it.sources.any { s -> s.providerName.contains(query, true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("模型", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            "已启用 ${st.enabledCount} / ${st.groups.size}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ModelToolbar(vm, query) { query = it }
            ModelList(filtered, vm)
        }
    }
}

@Composable
private fun ColumnScope.ModelToolbar(vm: ModelsViewModel, query: String, onQuery: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.enableKnownVendors() },
                modifier = Modifier.weight(1f)
            ) { Text("识别大厂", fontSize = 13.sp) }
            OutlinedButton(
                onClick = { vm.disableAll() },
                modifier = Modifier.weight(1f)
            ) { Text("全部取消", fontSize = 13.sp) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("搜索模型 / 来源", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ColumnScope.ModelList(list: List<ModelGroup>, vm: ModelsViewModel) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("没有模型", fontWeight = FontWeight.Medium)
                Text(
                    "先在设置里填 Key 并拉取模型",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.modelId }) { g ->
                GroupCard(g, vm)
            }
        }
    }
}

@Composable
private fun GroupCard(g: ModelGroup, vm: ModelsViewModel) {
    var expanded by remember(g.modelId) { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).clickable { expanded = !expanded }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        if (g.sources.size > 1) {
                            Spacer(Modifier.width(6.dp))
                            SourceBadge(g.sources.size)
                        }
                    }
                    Text(
                        "${g.modelId}${if (g.custom) " · 自定义" else ""}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Switch(
                    checked = g.enabled,
                    onCheckedChange = { vm.toggleGroup(g.modelId, it) }
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(6.dp))
                g.sources.forEach { s -> SourceRow(s, vm) }
            }
        }
    }
}

@Composable
private fun SourceRow(s: ModelSource, vm: ModelsViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(s.health.emoji, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(s.providerName, fontSize = 12.sp)
            Text(
                buildString {
                    append(s.health.label)
                    if (s.lastLatencyMs > 0) append(" · ${s.lastLatencyMs}ms")
                    if (s.protocol == "anthropic") append(" · Anthropic")
                },
                fontSize = 10.sp,
                color = when (s.health) {
                    HealthStatus.COOLING -> MaterialTheme.colorScheme.error
                    HealthStatus.SLOW -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Switch(
            checked = s.enabled,
            onCheckedChange = { vm.toggleSource(s.key, it) },
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
private fun SourceBadge(count: Int) {
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
