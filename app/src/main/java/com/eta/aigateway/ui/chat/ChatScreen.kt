package com.eta.aigateway.ui.chat

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eta.aigateway.data.model.ChatMessage
import com.eta.aigateway.data.model.ModelEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    availableModels: List<ModelEntry>,
    onOpenSettings: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    var showModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.streamingText) {
        val count = state.messages.size + if (state.streamingText.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Gateway", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            state.currentModel?.id ?: "未选择模型",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showModelPicker = true }) {
                        Icon(Icons.Default.SwapHoriz, "切换模型")
                    }
                    IconButton(onClick = { vm.newSession() }) {
                        Icon(Icons.Default.AddComment, "新对话")
                    }
                    IconButton(onClick = onOpenDashboard) {
                        Icon(Icons.Default.Insights, "仪表盘")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "设置")
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
        ChatBody(state, listState, Modifier.fillMaxSize().padding(pad))
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = availableModels,
            current = state.currentModel,
            onSelect = { vm.selectModel(it); showModelPicker = false },
            onDismiss = { showModelPicker = false }
        )
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("请求失败") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("知道了") } }
        )
    }
}

@Composable
private fun ChatBody(
    state: ChatUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (state.messages.isEmpty() && state.streamingText.isEmpty()) {
            EmptyState(Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages, key = { it.id }) { msg -> MessageBubble(msg) }
                if (state.streamingText.isNotEmpty()) {
                    item {
                        MessageBubble(
                            ChatMessage(
                                id = "streaming",
                                sessionId = "tmp",
                                role = "assistant",
                                content = state.streamingText
                            )
                        )
                    }
                }
            }
        }
        state.statusHint?.let { hint ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    hint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Waves,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(12.dp))
        Text("多接口聚合 · 自动降级", fontWeight = FontWeight.Medium)
        Text(
            "点右上角切换模型开始对话",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么…", fontSize = 14.sp) },
                maxLines = 5,
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = if (streaming) onStop else onSend,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (streaming) Icons.Default.Stop else Icons.Default.Send,
                    contentDescription = if (streaming) "停止" else "发送"
                )
            }
        }
    }
}
