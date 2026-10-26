package com.eta.aigateway.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eta.aigateway.data.model.FallbackPolicy
import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.data.model.CustomModel
import com.eta.aigateway.data.model.ModelPreference
import com.eta.aigateway.data.model.ProviderConfig
import com.eta.aigateway.data.provider.ChatProtocol
import com.eta.aigateway.data.provider.ProtocolType
import com.eta.aigateway.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class SettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val modelCache: Map<String, List<String>> = emptyMap(),
    val prefs: Map<String, ModelPreference> = emptyMap(),
    val loading: String? = null,
    val toast: String? = null,
    val gatewayPort: Int = 8080,
    val gatewayKeys: List<String> = emptyList(),
    val gatewayAllowLan: Boolean = false,
    val customModels: List<CustomModel> = emptyList(),
    val compressEnabled: Boolean = true,
    val compressModel: String = "",
    val compressThreshold: Int = 8000,
    val enabledModels: Set<String> = emptySet(),
    val block5xx: Boolean = true
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    private val db = AppDatabase.getInstance(app)

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
        viewModelScope.launch {
            repo.compressEnabled.collect { v -> _state.update { it.copy(compressEnabled = v) } }
        }
        viewModelScope.launch {
            repo.compressModel.collect { v -> _state.update { it.copy(compressModel = v) } }
        }
        viewModelScope.launch {
            repo.compressThreshold.collect { v -> _state.update { it.copy(compressThreshold = v) } }
        }
        viewModelScope.launch {
            repo.block5xx.collect { v ->
                com.eta.aigateway.engine.SourceHealth.enabled = v
                _state.update { it.copy(block5xx = v) }
            }
        }
        reloadCustom()
        viewModelScope.launch {
            val set = db.modelStateDao().allGroups().filter { it.enabled }.map { it.modelId }.toSet()
            _state.update { it.copy(enabledModels = set) }
        }
    }

    fun setCompressEnabled(v: Boolean) {
        viewModelScope.launch { repo.saveCompressEnabled(v) }
    }

    fun setCompressModel(id: String) {
        viewModelScope.launch { repo.saveCompressModel(id) }
    }

    fun setCompressThreshold(v: Int) {
        viewModelScope.launch { repo.saveCompressThreshold(v) }
    }

    private fun reloadCustom() {
        viewModelScope.launch {
            _state.update { it.copy(customModels = db.customModelDao().all()) }
        }
    }

    fun addCustomModel(m: CustomModel) {
        viewModelScope.launch {
            db.customModelDao().upsert(m)
            viewModelScope.launch {
            repo.block5xx.collect { v ->
                com.eta.aigateway.engine.SourceHealth.enabled = v
                _state.update { it.copy(block5xx = v) }
            }
        }
        reloadCustom()
        }
    }

    fun deleteCustomModel(id: String) {
        viewModelScope.launch {
            db.customModelDao().delete(id)
            viewModelScope.launch {
            repo.block5xx.collect { v ->
                com.eta.aigateway.engine.SourceHealth.enabled = v
                _state.update { it.copy(block5xx = v) }
            }
        }
        reloadCustom()
        }
    }

    /** 新建一个自定义 provider */
    fun addCustomProvider(name: String, baseUrl: String, protocol: String) {
        viewModelScope.launch {
            val id = "custom_" + UUID.randomUUID().toString().take(8)
            val cur = _state.value.providers
            val np = ProviderConfig(
                id = id,
                name = name.ifBlank { "自定义接口" },
                baseUrl = baseUrl.trim(),
                enabled = true,
                builtin = false,
                protocol = protocol
            )
            repo.saveProviders(cur + np)
        }
    }

    /** 删除自定义 provider */
    fun deleteCustomProvider(id: String) {
        viewModelScope.launch {
            val cur = _state.value.providers.filter { it.id != id }
            repo.saveProviders(cur)
            // 同时清理它的模型缓存
            val cache = _state.value.modelCache.toMutableMap()
            cache.remove(id)
            repo.saveModelCache(cache)
        }
    }

    /** 该 provider 下已启用的模型集合（读 ModelState） */
    val enabledModelIds: Flow<Set<String>> = kotlinx.coroutines.flow.flow {
        emit(db.modelStateDao().allGroups().filter { it.enabled }.map { it.modelId }.toSet())
    }

    fun loadEnabledModels(onResult: (Set<String>) -> Unit) {
        viewModelScope.launch {
            onResult(db.modelStateDao().allGroups().filter { it.enabled }.map { it.modelId }.toSet())
        }
    }

    fun toggleModelEnabled(modelId: String, enabled: Boolean) {
        viewModelScope.launch {
            val cur = db.modelStateDao().allGroups().firstOrNull { it.modelId == modelId }
            db.modelStateDao().upsert(
                (cur ?: com.eta.aigateway.data.model.ModelState(modelId)).copy(enabled = enabled)
            )
            val set = db.modelStateDao().allGroups().filter { it.enabled }.map { it.modelId }.toSet()
            _state.update { it.copy(enabledModels = set) }
        }
    }

    fun enableVendorsFor(providerId: String) {
        viewModelScope.launch {
            val models = _state.value.modelCache[providerId] ?: return@launch
            val known = models.filter { com.eta.aigateway.data.model.ModelClassifier.isKnownVendor(it) }
            known.forEach { mid ->
                val cur = db.modelStateDao().allGroups().firstOrNull { it.modelId == mid }
                db.modelStateDao().upsert(
                    (cur ?: com.eta.aigateway.data.model.ModelState(mid)).copy(enabled = true)
                )
            }
            val set = db.modelStateDao().allGroups().filter { it.enabled }.map { it.modelId }.toSet()
            _state.update {
                it.copy(
                    enabledModels = set,
                    toast = "已启用 ${known.size} 个大厂模型"
                )
            }
        }
    }

    fun setBlock5xx(v: Boolean) {
        viewModelScope.launch { repo.saveBlock5xx(v) }
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
            val proto = ChatProtocol.of(ProtocolType.from(p.protocol))
            val (_, msg) = proto.ping(p.baseUrl, key)
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
            val proto = ChatProtocol.of(ProtocolType.from(p.protocol))
            val models = proto.listModels(p.baseUrl, key)
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
