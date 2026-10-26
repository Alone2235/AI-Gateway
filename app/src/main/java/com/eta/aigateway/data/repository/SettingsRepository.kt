package com.eta.aigateway.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.eta.aigateway.data.model.FallbackPolicy
import com.eta.aigateway.data.model.ModelPreference
import com.eta.aigateway.data.model.ProviderConfig
import com.eta.aigateway.data.model.ProviderTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val KEY_PROVIDERS = stringPreferencesKey("providers")
        private val KEY_PREFS = stringPreferencesKey("model_preferences")
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val KEY_MODEL_CACHE = stringPreferencesKey("model_cache")
        private val KEY_THEME = stringPreferencesKey("theme")
    }

    /** 所有 provider（含模板合并） */
    val providers: Flow<List<ProviderConfig>> = context.dataStore.data.map { p ->
        val savedJson = p[KEY_PROVIDERS] ?: ""
        val saved: List<ProviderConfig> = if (savedJson.isBlank()) emptyList() else try {
            gson.fromJson(savedJson, object : TypeToken<List<ProviderConfig>>() {}.type)
        } catch (_: Exception) { emptyList() }

        // 以模板为骨架，把保存的 key 合并进去
        ProviderTemplates.all.map { tpl ->
            val s = saved.firstOrNull { it.id == tpl.id }
            if (s != null) tpl.copy(apiKeys = s.apiKeys, enabled = s.enabled, baseUrl = s.baseUrl.ifBlank { tpl.baseUrl })
            else tpl
        } + saved.filter { s -> ProviderTemplates.all.none { it.id == s.id } }
    }

    suspend fun saveProviders(list: List<ProviderConfig>) {
        context.dataStore.edit { it[KEY_PROVIDERS] = gson.toJson(list) }
    }

    val modelPreferences: Flow<Map<String, ModelPreference>> = context.dataStore.data.map { p ->
        val json = p[KEY_PREFS] ?: ""
        if (json.isBlank()) emptyMap() else try {
            gson.fromJson<Map<String, ModelPreference>>(json, object : TypeToken<Map<String, ModelPreference>>() {}.type) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    suspend fun saveModelPreferences(map: Map<String, ModelPreference>) {
        context.dataStore.edit { it[KEY_PREFS] = gson.toJson(map) }
    }

    val selectedModel: Flow<String> = context.dataStore.data.map { it[KEY_SELECTED_MODEL] ?: "" }
    suspend fun saveSelectedModel(id: String) {
        context.dataStore.edit { it[KEY_SELECTED_MODEL] = id }
    }
    suspend fun currentSelectedModel(): String = selectedModel.first()

    /** 模型列表缓存 providerId -> List<modelId> */
    val modelCache: Flow<Map<String, List<String>>> = context.dataStore.data.map { p ->
        val json = p[KEY_MODEL_CACHE] ?: ""
        if (json.isBlank()) emptyMap() else try {
            gson.fromJson<Map<String, List<String>>>(json, object : TypeToken<Map<String, List<String>>>() {}.type) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    suspend fun saveModelCache(map: Map<String, List<String>>) {
        context.dataStore.edit { it[KEY_MODEL_CACHE] = gson.toJson(map) }
    }

    val theme: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "dark" }
    suspend fun saveTheme(v: String) {
        context.dataStore.edit { it[KEY_THEME] = v }
    }
}
