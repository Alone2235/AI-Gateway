package com.eta.aigateway.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.data.db.ProviderAgg
import com.eta.aigateway.data.model.BalanceSnapshot
import com.eta.aigateway.data.model.UsageRecord
import com.eta.aigateway.data.provider.BalanceClient
import com.eta.aigateway.data.repository.SettingsRepository
import com.eta.aigateway.engine.CooldownTracker
import com.eta.aigateway.gateway.GatewayService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardState(
    val todayCalls: Int = 0,
    val todayOkCalls: Int = 0,
    val todayTokens: Int = 0,
    val appCalls: Int = 0,
    val apiCalls: Int = 0,
    val byProvider: List<ProviderAgg> = emptyList(),
    val recent: List<UsageRecord> = emptyList(),
    val balances: List<BalanceSnapshot> = emptyList(),
    val refreshing: Boolean = false,
    val gatewayRunning: Boolean = false,
    val gatewayPort: Int = 0,
    val gatewayError: String? = null,
    val cooldowns: Map<String, Long> = emptyMap()
) {
    val successRate: Float
        get() = if (todayCalls == 0) 0f else todayOkCalls.toFloat() / todayCalls
}

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = SettingsRepository(app)

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            repo.gatewayPort.collect { p -> _state.update { it.copy(gatewayPort = p) } }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val dayStart = todayStart()
            val dao = db.usageDao()
            _state.update {
                it.copy(
                    todayCalls = dao.countSince(dayStart),
                    todayOkCalls = dao.okCountSince(dayStart),
                    todayTokens = dao.tokensSince(dayStart),
                    appCalls = dao.countBySource(dayStart, "app"),
                    apiCalls = dao.countBySource(dayStart, "api"),
                    byProvider = dao.byProvider(dayStart),
                    recent = dao.recent(dayStart, 30),
                    balances = db.balanceDao().all(),
                    gatewayRunning = GatewayService.isRunning,
                    gatewayPort = GatewayService.runningPort,
                    gatewayError = GatewayService.lastError
                )
            }
        }
    }

    fun refreshBalances() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val providers = repo.providers.first().filter { it.apiKeys.isNotEmpty() }
            val results = providers.map { BalanceClient.query(it) }
            results.forEach { db.balanceDao().upsert(it) }
            _state.update {
                it.copy(balances = db.balanceDao().all(), refreshing = false)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            db.usageDao().clearAll()
            refresh()
        }
    }

    fun isCooling(key: String): Boolean = CooldownTracker.isCooling(key)

    private fun todayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
