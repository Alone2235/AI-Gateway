package com.eta.aigateway.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        private val KEY_GW_PORT = intPreferencesKey("gateway_port")
        private val KEY_GW_KEYS = stringPreferencesKey("gateway_keys")
        private val KEY_GW_LAN = booleanPreferencesKey("gateway_lan")
        private val KEY_COMPRESS_ON = booleanPreferencesKey("compress_on")
        private val KEY_COMPRESS_MODEL = stringPreferencesKey("compress_model")
        private val KEY_COMPRESS_THRESHOLD = intPreferencesKey("compress_threshold")
    }

    /** 所有 provider（含模板合并） */
    val providers: Flow<List<ProviderConfig>> = context.dataStore.data.map { p ->
        val savedJson = p[KEY_PROVIDERS] ?: ""
        val saved: List<ProviderConfig> = if (savedJson.isBlank()) emptyList() else try {
            gson.fromJson(savedJson, object : TypeToken<List<ProviderConfig>>() {}.type)
        } catch (_: Exception) { emptyList() }

        // 以模板为骨架；用户层只保存差异，避免模板值反向覆盖用户修改
        ProviderTemplates.all.map { tpl ->
            val s = saved.firstOrNull { it.id == tpl.id }
            if (s == null) tpl
            else tpl.copy(
                apiKeys = s.apiKeys,
                enabled = s.enabled,
                // 空串表示用户没改过，用模板默认
                baseUrl = s.baseUrl.ifBlank { tpl.baseUrl }
            )
        } + saved.filter { s -> ProviderTemplates.all.none { it.id == s.id } }
    }

    /**
     * 只持久化用户真正改动过的字段。
     * 未改动的 baseUrl 存空串，读取时回落到模板默认值，
     * 这样以后模板升级地址也能自动生效，且不会覆盖用户修改。
     */
    suspend fun saveProviders(list: List<ProviderConfig>) {
        val slim = list.map { p ->
            val tpl = ProviderTemplates.all.firstOrNull { it.id == p.id }
            p.copy(
                name = tpl?.name ?: p.name,
                builtin = tpl?.builtin ?: p.builtin,
                protocol = tpl?.protocol ?: p.protocol,
                baseUrl = if (tpl != null && p.baseUrl == tpl.baseUrl) "" else p.baseUrl
            )
        }
        context.dataStore.edit { it[KEY_PROVIDERS] = gson.toJson(slim) }
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

    /** 网关端口，默认 8080 */
    val gatewayPort: Flow<Int> = context.dataStore.data.map { it[KEY_GW_PORT] ?: 8080 }
    suspend fun saveGatewayPort(port: Int) {
        context.dataStore.edit { it[KEY_GW_PORT] = port }
    }

    /** 网关访问密钥（外部客户端用） */
    val gatewayAccessKeys: Flow<List<String>> = context.dataStore.data.map { p ->
        (p[KEY_GW_KEYS] ?: "").lines().map { it.trim() }.filter { it.isNotBlank() }
    }
    suspend fun saveGatewayAccessKeys(keys: List<String>) {
        context.dataStore.edit { it[KEY_GW_KEYS] = keys.joinToString("\n") }
    }

    /** 是否允许局域网访问 */
    val gatewayAllowLan: Flow<Boolean> = context.dataStore.data.map { it[KEY_GW_LAN] ?: false }
    suspend fun saveGatewayAllowLan(v: Boolean) {
        context.dataStore.edit { it[KEY_GW_LAN] = v }
    }

    /** 上下文压缩开关 */
    val compressEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_COMPRESS_ON] ?: true }
    suspend fun saveCompressEnabled(v: Boolean) {
        context.dataStore.edit { it[KEY_COMPRESS_ON] = v }
    }

    /** 压缩用的摘要模型 */
    val compressModel: Flow<String> = context.dataStore.data.map { it[KEY_COMPRESS_MODEL] ?: "" }
    suspend fun saveCompressModel(id: String) {
        context.dataStore.edit { it[KEY_COMPRESS_MODEL] = id }
    }

    /** 触发阈值（token 估算） */
    val compressThreshold: Flow<Int> = context.dataStore.data.map { it[KEY_COMPRESS_THRESHOLD] ?: 8000 }
    suspend fun saveCompressThreshold(v: Int) {
        context.dataStore.edit { it[KEY_COMPRESS_THRESHOLD] = v }
    }

    val theme: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "dark" }
    suspend fun saveTheme(v: String) {
        context.dataStore.edit { it[KEY_THEME] = v }
    }
}
