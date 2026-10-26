package com.eta.aigateway.gateway

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.eta.aigateway.MainActivity
import com.eta.aigateway.data.db.AppDatabase
import com.eta.aigateway.data.model.ModelGroup
import com.eta.aigateway.data.repository.ModelGroupRepository
import com.eta.aigateway.data.repository.SettingsRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class GatewayService : Service() {

    companion object {
        private const val TAG = "GatewayService"
        private const val CHANNEL_ID = "gateway_channel"
        private const val NOTIF_ID = 1001

        @Volatile var isRunning = false
            private set
        @Volatile var runningPort = 0
            private set
        @Volatile var lastError: String? = null

        const val ACTION_START = "com.eta.aigateway.GATEWAY_START"
        const val ACTION_STOP = "com.eta.aigateway.GATEWAY_STOP"
    }

    private var server: GatewayServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repo: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
        AppDatabase.getInstance(applicationContext)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification("正在启动…", "准备监听端口"))
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        if (isRunning) return
        scope.launch {
            try {
                val port = repo.gatewayPort.first()
                val accessKeys = repo.gatewayAccessKeys.first()
                val allowLan = repo.gatewayAllowLan.first()
                val providers = repo.providers.first()
                val cache = repo.modelCache.first()
                val prefs = repo.modelPreferences.first()
                val groups = ModelGroupRepository(
                    com.eta.aigateway.data.db.AppDatabase.getInstance(applicationContext)
                ).loadGroups(providers, cache)

                val bindAddr = if (allowLan) null else "127.0.0.1"
                val srv = GatewayServer(port, {
                    GatewayServer.GatewayState(
                        accessKeys = accessKeys,
                        providers = providers,
                        groups = groups,
                        prefs = prefs,
                        enabled = true,
                        allowLan = allowLan
                    )
                })

                srv.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = srv
                isRunning = true
                runningPort = port
                lastError = null

                val url = "http://127.0.0.1:$port/v1"
                updateNotification("网关运行中", url)
                Log.i(TAG, "Gateway started on port $port (lan=$allowLan)")
            } catch (e: Exception) {
                lastError = e.message
                Log.e(TAG, "Gateway start failed", e)
                isRunning = false
                updateNotification("启动失败", e.message ?: "未知错误")
            }
        }
    }

    override fun onDestroy() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
        isRunning = false
        runningPort = 0
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "网关服务", NotificationManager.IMPORTANCE_LOW
            )
            ch.description = "AI Gateway 内嵌服务运行状态"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Gateway · $title")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title, text))
    }
}
