package com.Badnng.moe.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.Badnng.moe.R

class KeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        when (action) {
            "show" -> showNotification()
            "hide" -> hideNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "后台保活", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("澎湃记正在后台运行")
            .setContentText("保持短信读取、磁贴识别等功能可用")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun hideNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "keep_alive"

        fun start(context: Context) {
            context.startService(Intent(context, KeepAliveService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }

        fun showNotification(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                putExtra("action", "show")
            }
            context.startService(intent)
        }

        fun hideNotification(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                putExtra("action", "hide")
            }
            context.startService(intent)
        }
    }
}
