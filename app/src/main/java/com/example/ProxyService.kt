package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.proxy.ProxyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ProxyService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "ProxyServiceChannel"

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                ProxyManager.startServers(applicationContext)
                startForegroundNotification()
                monitorStatsForNotification()
            }
            ACTION_STOP -> {
                ProxyManager.stopServers()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        updateJob?.cancel()
        ProxyManager.stopServers()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ProxyShare Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time speed and status of the VPN proxy servers."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = createNotification("Proxy Servers Starting...", "HTTP: ${ProxyManager.httpPort.value} | SOCKS5: ${ProxyManager.socksPort.value}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Add a Stop Action directly on the notification for a top-tier user experience!
        val stopIntent = Intent(this, ProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Servers", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun monitorStatsForNotification() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            // Combine flows to update notification periodically with actual speed and connection metrics!
            combine(
                ProxyManager.uploadSpeed,
                ProxyManager.downloadSpeed,
                ProxyManager.activeConnections
            ) { upSpeed, downSpeed, connections ->
                Triple(upSpeed, downSpeed, connections.size)
            }.collect { (upSpeed, downSpeed, connCount) ->
                val upStr = formatSpeed(upSpeed)
                val downStr = formatSpeed(downSpeed)
                val notification = createNotification(
                    "Proxy Active | $connCount Clients",
                    "↑ Up: $upStr  ↓ Down: $downStr"
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val kb = bytesPerSec / 1024.0
        return if (kb >= 1024) {
            String.format("%.1f MB/s", kb / 1024)
        } else {
            String.format("%.1f KB/s", kb)
        }
    }
}
