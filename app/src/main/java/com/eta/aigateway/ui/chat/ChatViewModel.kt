package com.eta.aigateway.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.data.model.*
import com.eta.aigateway.data.model.VirtualTier
import com.eta.aigateway.data.repository.ModelGroupRepository
import com.eta.aigateway.data.repository.SettingsRepository
import com.eta.aigateway.engine.ContextCompressor
import com.eta.aigateway.engine.GatewayEngine
import com.eta.aigateway.engine.RetryPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val streaming: Boolean = false,
    val streamingText: String = "",
    val currentGroup: ModelGroup? = null,
    val currentTier: VirtualTier? = null,
    val statusHint: String? = null,
    val error: String? = null,
    val sessionId: String = ""
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val repo = SettingsRepository(app)
    private val groupRepo = ModelGroupRepository(db)

    private val _state = MutableStateFlow(ChatUiState(sessionId = UUID.randomUUID().toString()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _groups = MutableStateFlow<List<ModelGroup>>(emptyList())
    val groups: StateFlow<List<ModelGroup>> = _groups.asStateFlow()

    val providers = repo.providers

    private var streamJob: Job? = null

    suspend fun refreshGroups() {
        val ps = providers.first()
        val cache = repo.modelCache.first()
        val list = groupRepo.loadGroups(ps, cache)
        _groups.value = list

        // 当前选择失效则自动切到第一个启用的
        val cur = _state.value.currentGroup
        val updated = cur?.let { c -> list.firstOrNull { it.modelId == c.modelId } }
            ?: list.firstOrNull { it.enabled }
        if (updated != null) _state.update { it.copy(currentGroup = updated) }
    }

    fun selectedGroup(): ModelGroup? = _state.value.currentGroup ?: _groups.value.firstOrNull { it.enabled }

    fun selectGroup(g: ModelGroup) {
        _state.update { it.copy(currentGroup = g, currentTier = null) }
    }

    fun selectTier(t: VirtualTier) {
        _state.update { it.copy(currentTier = t) }
    }

    fun updateInput(t: String) = _state.update { it.copy(input = t) }
    fun clearError() = _state.update { it.copy(error = null, statusHint = null) }

    fun newSession() {
        streamJob?.cancel()
        _state.value = ChatUiState(
            sessionId = UUID.randomUUID().toString(),
            currentGroup = _state.value.currentGroup
        )
    }

    fun send() {
        val st = _state.value
        val text = st.input.trim()
        if (text.isEmpty() || st.streaming) return

        val tier = st.currentTier
        val group = selectedGroup()
        if (tier == null && group == null) {
            _state.update { it.copy(error = "没有启用的模型。请到设置里拉取并勾选模型") }
            return
        }

        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = st.sessionId,
                role = "user",
                content = text
            )
            _state.update {
                it.copy(
                    messages = it.messages + userMsg,
                    input = "", streaming = true, streamingText = "", error = null
                )
            }
            db.messageDao().insert(userMsg)

            val rawHistory = _state.value.messages.filter { !it.isError }.map { it.role to it.content }
            val ps = providers.first()
            val engine = GatewayEngine(ps, _groups.value, RetryPolicy())

            // 上下文压缩
            val compressOn = repo.compressEnabled.first()
            val threshold = repo.compressThreshold.first()
            val summaryModelId = repo.compressModel.first()
            val summarizer = if (summaryModelId.isNotBlank()) {
                _groups.value.firstOrNull { it.modelId == summaryModelId && it.enabled }
            } else {
                _groups.value.filter { it.enabled }.minByOrNull { g ->
                    // 优先挑便宜的：名字含 mini/flash/lite 的
                    val n = g.modelId.lowercase()
                    when {
                        n.contains("mini") || n.contains("flash") || n.contains("lite") -> 0
                        else -> 1
                    }
                }
            }

            val history = ContextCompressor.compress(
                history = rawHistory,
                summarizer = summarizer,
                config = ContextCompressor.Config(enabled = compressOn, triggerTokens = threshold)
            ) { model, msgs ->
                val sb = StringBuilder()
                GatewayEngine(ps, _groups.value, RetryPolicy())
                    .chat(model, msgs, 0.3, source = "compress")
                    .collect { ev -> if (ev is StreamEvent.Chunk) sb.append(ev.text) }
                sb.toString()
            }

            val sb = StringBuilder()
            var finalModel = group?.modelId ?: ""
            var finalProvider = ""

            val flow = if (tier != null) engine.autoChat(history, source = "app", tier = tier)
                       else engine.chat(group!!, history, source = "app")
            flow.collect { ev ->
                when (ev) {
                    is StreamEvent.Chunk -> {
                        sb.append(ev.text)
                        _state.update { it.copy(streamingText = sb.toString()) }
                    }
                    is StreamEvent.Switched -> _state.update { it.copy(statusHint = ev.reason) }
                    is StreamEvent.Done -> {
                        finalModel = ev.model
                        finalProvider = ev.provider
                    }
                    is StreamEvent.Failed -> _state.update { it.copy(error = ev.reason, statusHint = null) }
                }
            }

            val finalText = sb.toString()
            if (finalText.isNotEmpty()) {
                val aiMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = _state.value.sessionId,
                    role = "assistant",
                    content = finalText,
                    model = finalModel,
                    providerName = finalProvider
                )
                db.messageDao().insert(aiMsg)
                _state.update {
                    it.copy(
                        messages = it.messages + aiMsg,
                        streaming = false, streamingText = "", statusHint = null
                    )
                }
            } else {
                _state.update { it.copy(streaming = false, streamingText = "") }
            }
            // 更新健康状态展示
            refreshGroups()
        }
    }

    fun stop() {
        streamJob?.cancel()
        _state.update { it.copy(streaming = false, streamingText = "") }
    }
}
