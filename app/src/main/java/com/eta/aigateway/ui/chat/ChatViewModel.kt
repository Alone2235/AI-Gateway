package com.eta.aigateway.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.data.model.*
import com.eta.aigateway.data.repository.SettingsRepository
import com.eta.aigateway.engine.GatewayEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val streaming: Boolean = false,
    val streamingText: String = "",
    val currentModel: ModelEntry? = null,
    val statusHint: String? = null,
    val error: String? = null,
    val sessionId: String = ""
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val repo = SettingsRepository(app)

    private val _state = MutableStateFlow(ChatUiState(sessionId = UUID.randomUUID().toString()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val providers = repo.providers
    val modelPreferences = repo.modelPreferences

    private var streamJob: Job? = null
    private var allModels: List<ModelEntry> = emptyList()

    fun refreshModels(
        cache: Map<String, List<String>>,
        providerList: List<ProviderConfig>,
        customModels: List<CustomModel> = emptyList()
    ) {
        val pulled = providerList.filter { it.enabled && it.baseUrl.isNotBlank() }
            .flatMap { p ->
                (cache[p.id] ?: emptyList()).map { mid ->
                    ModelEntry(
                        id = mid,
                        displayName = mid,
                        providerId = p.id,
                        providerName = p.name,
                        protocol = p.protocol
                    )
                }
            }
        val custom = customModels.mapNotNull { cm ->
            val p = providerList.firstOrNull { it.id == cm.providerId } ?: return@mapNotNull null
            ModelEntry(
                id = cm.modelId,
                displayName = cm.displayName,
                providerId = cm.providerId,
                providerName = p.name,
                protocol = cm.protocol,
                custom = true
            )
        }
        // 自定义优先显示，并去重
        allModels = (custom + pulled).distinctBy { "${it.providerId}|${it.id}" }
        _state.value.currentModel?.let { cur ->
            val updated = allModels.firstOrNull { it.id == cur.id && it.providerId == cur.providerId }
            if (updated != null) _state.update { it.copy(currentModel = updated) }
        }
    }

    fun allAvailableModels(): List<ModelEntry> = allModels

    fun selectModel(m: ModelEntry) {
        _state.update { it.copy(currentModel = m) }
        viewModelScope.launch { repo.saveSelectedModel(m.id) }
    }

    fun updateInput(t: String) = _state.update { it.copy(input = t) }
    fun clearError() = _state.update { it.copy(error = null, statusHint = null) }

    fun newSession() {
        streamJob?.cancel()
        _state.value = ChatUiState(
            sessionId = UUID.randomUUID().toString(),
            currentModel = _state.value.currentModel
        )
    }

    fun send() {
        val st = _state.value
        val text = st.input.trim()
        if (text.isEmpty() || st.streaming) return
        val model = st.currentModel ?: run {
            _state.update { it.copy(error = "请先选择模型") }
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
                it.copy(messages = it.messages + userMsg, input = "", streaming = true, streamingText = "", error = null)
            }
            db.messageDao().insert(userMsg)

            val history = _state.value.messages.filter { !it.isError }.map { it.role to it.content }
            val providerList = providers.first()
            val prefs = modelPreferences.first()
            val engine = GatewayEngine(providerList, prefs, allModels)

            val sb = StringBuilder()
            var finalModel = model.id
            var finalProvider = model.providerName

            engine.chat(model, history).collect { ev ->
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
                    it.copy(messages = it.messages + aiMsg, streaming = false, streamingText = "", statusHint = null)
                }
            } else {
                _state.update { it.copy(streaming = false, streamingText = "") }
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        _state.update { it.copy(streaming = false, streamingText = "") }
    }
}
