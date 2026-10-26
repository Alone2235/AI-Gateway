package com.eta.aigateway.ui.dashboard

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
import com.eta.aigateway.data.db.ProviderAgg
import com.eta.aigateway.data.model.BalanceSnapshot
import com.eta.aigateway.data.model.UsageRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel, onBack: () -> Unit) {
    val st by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("仪表盘", fontWeight = FontWeight.Bold) },
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
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { StatCards(st) }
            item { GatewayCard(st) }
            item { BalanceSection(st, vm) }
            item { ProviderSection(st.byProvider) }
            item { RecentSection(st.recent) }
        }
    }
}

@Composable
private fun StatCards(st: DashboardState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("今日调用", "${st.todayCalls}", Modifier.weight(1f))
            StatCard("成功率", "${(st.successRate * 100).toInt()}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Token", fmtNum(st.todayTokens), Modifier.weight(1f))
            StatCard("App / 外部", "${st.appCalls} / ${st.apiCalls}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GatewayCard(st: DashboardState) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (st.gatewayRunning) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    null,
                    tint = if (st.gatewayRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (st.gatewayRunning) "网关运行中" else "网关未启动",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    Text(
                        if (st.gatewayRunning) "http://127.0.0.1:${st.gatewayPort}/v1"
                        else "去设置里开启对外服务",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            st.gatewayError?.let {
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BalanceSection(st: DashboardState, vm: DashboardViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("余额", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (st.refreshing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { vm.refreshBalances() }) { Text("刷新", fontSize = 12.sp) }
            }
        }
        if (st.balances.isEmpty()) {
            Text("还没有余额数据，点刷新查询", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            st.balances.forEach { b -> BalanceRow(b) }
        }
    }
}

@Composable
private fun BalanceRow(b: BalanceSnapshot) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.providerName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        !b.supported -> "不支持查询余额"
                        b.error.isNotBlank() -> b.error
                        else -> "剩余 ${fmtMoney(b.remaining)} ${b.currency}"
                    },
                    fontSize = 11.sp,
                    color = if (b.error.isNotBlank()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (b.supported && b.error.isBlank()) {
                Text(
                    fmtMoney(b.remaining),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ProviderSection(list: List<ProviderAgg>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("各接口今日表现", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        if (list.isEmpty()) {
            Text("今日还没有调用记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            list.forEach { p ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.providerName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${p.okCnt}/${p.cnt} 成功 · ${fmtNum(p.tokens)} tokens",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val rate = if (p.cnt == 0) 0 else (p.okCnt * 100 / p.cnt)
                        Text(
                            "$rate%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                rate >= 90 -> MaterialTheme.colorScheme.primary
                                rate >= 60 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSection(list: List<UsageRecord>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("最近请求", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        if (list.isEmpty()) {
            Text("暂无记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            list.take(15).forEach { r ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (r.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = if (r.success) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.modelId.take(34),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                            Text(
                                "${r.providerName} · ${r.source} · ${r.latencyMs}ms" +
                                    if (!r.success) " · ${r.errorMsg.take(28)}" else "",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun fmtNum(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1000 -> String.format("%.1fK", n / 1000.0)
    else -> n.toString()
}

private fun fmtMoney(v: Double): String = String.format("%.2f", v)
