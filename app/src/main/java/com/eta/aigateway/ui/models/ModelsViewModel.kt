package com.eta.aigateway.ui.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.data.repository.ModelGroupRepository
import com.eta.aigateway.data.repository.SettingsRepository
import com.eta.aigateway.engine.AutoRouter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ModelsUiState(
    val groups: List<ModelGroup> = emptyList(),
    val toast: String? = null,
    val loading: Boolean = false
) {
    val enabledCount: Int get() = groups.count { it.enabled }
}

class ModelsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = SettingsRepository(app)
    private val groupRepo = ModelGroupRepository(db)

    private val _state = MutableStateFlow(ModelsUiState())
    val state: StateFlow<ModelsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val ps = repo.providers.first()
            val cache = repo.modelCache.first()
            val raw = groupRepo.loadGroups(ps, cache)
            // 附上实时健康
            val withHealth = raw.map { g ->
                g.copy(sources = AutoRouter.allWithHealth(g))
            }
            _state.update { it.copy(groups = withHealth, loading = false) }
        }
    }

    fun toggleGroup(modelId: String, enabled: Boolean) {
        viewModelScope.launch {
            groupRepo.setGroupEnabled(modelId, enabled)
            refresh()
        }
    }

    fun toggleSource(key: String, enabled: Boolean) {
        viewModelScope.launch {
            groupRepo.setSourceEnabled(key, enabled)
            refresh()
        }
    }

    fun setTags(modelId: String, tags: List<String>) {
        viewModelScope.launch {
            groupRepo.setGroupTags(modelId, tags)
            refresh()
        }
    }

    fun enableKnownVendors() {
        viewModelScope.launch {
            val n = groupRepo.enableKnownVendors(_state.value.groups)
            refresh()
            _state.update { it.copy(toast = "已启用 $n 个大厂模型") }
        }
    }

    fun disableAll() {
        viewModelScope.launch {
            groupRepo.disableAll(_state.value.groups)
            refresh()
            _state.update { it.copy(toast = "已全部取消启用") }
        }
    }

    fun clearToast() = _state.update { it.copy(toast = null) }
}
