package com.example.wirelesskeyboardsender

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class KeyboardService : Service() {

    private val binder = LocalBinder()
    lateinit var networkManager: NetworkManager
        private set

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): KeyboardService = this@KeyboardService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        networkManager = NetworkManager(this)
        createNotificationChannel()

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "KeyboardApp:WifiLock")

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeyboardApp:WakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "KEYBOARD_CHANNEL")
            .setContentTitle("Wireless Keyboard Active")
            .setContentText("Connection maintained in background")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
        return START_STICKY
    }

    fun startBackgroundLocks() {
        if (wifiLock?.isHeld == false) wifiLock?.acquire()
        if (wakeLock?.isHeld == false) wakeLock?.acquire(10 * 60 * 1000L)
        startHeartbeat()
    }

    fun stopBackgroundLocks() {
        heartbeatJob?.cancel()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(15000) // 15 seconds
                if (networkManager.getConnectionStatus()) {
                    networkManager.send("PING")
                } else {
                    break
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundLocks()
        serviceScope.cancel()
        networkManager.disconnect()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "KEYBOARD_CHANNEL",
                "Keyboard Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
