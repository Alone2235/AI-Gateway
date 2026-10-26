package com.eta.aigateway.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eta.aigateway.data.model.FallbackPolicy
import com.eta.aigateway.data.model.ModelPreference
import com.eta.aigateway.data.model.ProviderConfig
import com.eta.aigateway.data.provider.OpenAIClient
import com.eta.aigateway.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val modelCache: Map<String, List<String>> = emptyMap(),
    val prefs: Map<String, ModelPreference> = emptyMap(),
    val loading: String? = null,
    val toast: String? = null,
    val gatewayPort: Int = 8080,
    val gatewayKeys: List<String> = emptyList(),
    val gatewayAllowLan: Boolean = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.providers.collect { list -> _state.update { it.copy(providers = list) } }
        }
        viewModelScope.launch {
            repo.modelCache.collect { c -> _state.update { it.copy(modelCache = c) } }
        }
        viewModelScope.launch {
            repo.modelPreferences.collect { p -> _state.update { it.copy(prefs = p) } }
        }
        viewModelScope.launch {
            repo.gatewayPort.collect { v -> _state.update { it.copy(gatewayPort = v) } }
        }
        viewModelScope.launch {
            repo.gatewayAccessKeys.collect { v -> _state.update { it.copy(gatewayKeys = v) } }
        }
        viewModelScope.launch {
            repo.gatewayAllowLan.collect { v -> _state.update { it.copy(gatewayAllowLan = v) } }
        }
    }

    fun setGatewayPort(port: Int) {
        viewModelScope.launch { repo.saveGatewayPort(port) }
    }

    fun setGatewayKeys(keys: List<String>) {
        viewModelScope.launch { repo.saveGatewayAccessKeys(keys) }
    }

    fun setGatewayAllowLan(v: Boolean) {
        viewModelScope.launch { repo.saveGatewayAllowLan(v) }
    }

    fun setKeys(providerId: String, keysText: String) {
        val keys = keysText.lines().map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            val updated = _state.value.providers.map { if (it.id == providerId) it.copy(apiKeys = keys) else it }
            repo.saveProviders(updated)
        }
    }

    fun setBaseUrl(providerId: String, url: String) {
        viewModelScope.launch {
            val updated = _state.value.providers.map {
                if (it.id == providerId) it.copy(baseUrl = url.trim()) else it
            }
            repo.saveProviders(updated)
        }
    }

    fun toggleEnabled(providerId: String, enabled: Boolean) {
        viewModelScope.launch {
            val updated = _state.value.providers.map {
                if (it.id == providerId) it.copy(enabled = enabled) else it
            }
            repo.saveProviders(updated)
        }
    }

    fun test(providerId: String) {
        val p = _state.value.providers.firstOrNull { it.id == providerId } ?: return
        val key = p.apiKeys.firstOrNull()
        if (key.isNullOrBlank()) {
            _state.update { it.copy(toast = "先填一个 API Key") }
            return
        }
        _state.update { it.copy(loading = providerId) }
        viewModelScope.launch {
            val (_, msg) = OpenAIClient.ping(p.baseUrl, key)
            _state.update { it.copy(loading = null, toast = "${p.name}: $msg") }
        }
    }

    fun fetchModels(providerId: String) {
        val p = _state.value.providers.firstOrNull { it.id == providerId } ?: return
        val key = p.apiKeys.firstOrNull()
        if (key.isNullOrBlank()) {
            _state.update { it.copy(toast = "先填一个 API Key") }
            return
        }
        _state.update { it.copy(loading = providerId) }
        viewModelScope.launch {
            val models = OpenAIClient.listModels(p.baseUrl, key)
            if (models.isEmpty()) {
                _state.update { it.copy(loading = null, toast = "${p.name}: 拉取失败或没有模型") }
            } else {
                val cache = _state.value.modelCache.toMutableMap()
                cache[providerId] = models
                repo.saveModelCache(cache)
                _state.update {
                    it.copy(loading = null, modelCache = cache, toast = "${p.name}: 拉到 ${models.size} 个模型")
                }
            }
        }
    }

    fun setPolicy(modelId: String, policy: FallbackPolicy) {
        viewModelScope.launch {
            val map = _state.value.prefs.toMutableMap()
            val old = map[modelId] ?: ModelPreference(modelId)
            map[modelId] = old.copy(policy = policy)
            repo.saveModelPreferences(map)
        }
    }

    fun clearToast() = _state.update { it.copy(toast = null) }
}
