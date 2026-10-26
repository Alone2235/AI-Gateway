package com.eta.aigateway.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.data.model.ChatMessage
import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    groups: List<ModelGroup>,
    onOpenSettings: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenModels: () -> Unit
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    var showModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.streamingText) {
        val count = state.messages.size + if (state.streamingText.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { TopTitle(state, onPick = { showModelPicker = true }) },
                actions = {
                    IconButton(onClick = onOpenModels) {
                        Icon(Icons.Default.Tune, "模型", Modifier.size(22.dp))
                    }
                    IconButton(onClick = onOpenDashboard) {
                        Icon(Icons.Default.Insights, "仪表盘", Modifier.size(22.dp))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "设置", Modifier.size(22.dp))
                    }
                }
            )
        },
        bottomBar = {
            InputBar(
                value = state.input,
                streaming = state.streaming,
                onValueChange = vm::updateInput,
                onSend = vm::send,
                onStop = vm::stop
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (state.messages.isEmpty() && state.streamingText.isEmpty()) {
                EmptyState(Modifier.align(Alignment.Center))
            } else {
                ChatList(state, listState)
            }
            HintBanner(state.statusHint, Modifier.align(Alignment.TopCenter))
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            groups = groups,
            current = state.currentGroup,
            currentTier = state.currentTier,
            onSelect = { vm.selectGroup(it); showModelPicker = false },
            onSelectTier = { vm.selectTier(it); showModelPicker = false },
            onDismiss = { showModelPicker = false }
        )
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("请求失败", fontSize = Fonts.title2) },
            text = { Text(err, fontSize = Fonts.body) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("知道了") } }
        )
    }
}

@Composable
private fun TopTitle(state: ChatUiState, onPick: () -> Unit) {
    Column(Modifier.clickable(onClick = onPick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.currentTier?.label ?: state.currentGroup?.displayName ?: "选择模型",
                fontWeight = FontWeight.SemiBold,
                fontSize = Fonts.title2
            )
            Icon(
                Icons.Default.ExpandMore, null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            state.currentTier?.let { t -> t.desc }
                ?: state.currentGroup?.let { g ->
                    if (g.sources.size > 1) "${g.sources.size} 个来源可切换" else "单个来源"
                } ?: "未启用任何模型",
            fontSize = Fonts.micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
        )
    }
}

@Composable
private fun ChatList(state: ChatUiState, listState: androidx.compose.foundation.lazy.LazyListState) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.pagePadding, end = Dimens.pagePadding,
            top = Dimens.gap8, bottom = Dimens.gap16
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap12)
    ) {
        items(state.messages, key = { it.id }) { MessageBubble(it) }
        if (state.streamingText.isNotEmpty()) {
            item {
                MessageBubble(ChatMessage(
                    id = "streaming", sessionId = "tmp",
                    role = "assistant", content = state.streamingText
                ))
            }
        }
    }
}

@Composable
private fun HintBanner(hint: String?, modifier: Modifier = Modifier) {
    if (hint == null) return
    Surface(
        modifier = modifier.padding(top = Dimens.gap8),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
    ) {
        Text(
            hint, fontSize = Fonts.micro,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Waves, null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(Dimens.gap12))
        Text(
            "多来源聚合 · 自动切换",
            fontWeight = FontWeight.Medium,
            fontSize = Fonts.bodyStrong
        )
        Spacer(Modifier.height(Dimens.gap4))
        Text(
            "点标题栏选模型，或直接开始对话",
            fontSize = Fonts.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TextAlpha.secondary)
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    streaming: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(Dimens.panelRadius, GlassLevel.Strong)
            .padding(horizontal = Dimens.pagePadding, vertical = Dimens.gap12),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("说点什么…", fontSize = Fonts.body) },
            maxLines = 5,
            shape = RoundedCornerShape(22.dp)
        )
        Spacer(Modifier.width(Dimens.gap8))
        FilledIconButton(
            onClick = if (streaming) onStop else onSend,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                if (streaming) Icons.Default.Stop else Icons.Default.ArrowUpward,
                if (streaming) "停止" else "发送"
            )
        }
    }
}
