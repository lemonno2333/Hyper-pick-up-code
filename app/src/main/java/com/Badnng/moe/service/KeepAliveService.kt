package com.Badnng.moe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.Badnng.moe.R
import com.Badnng.moe.activity.MainActivity

class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 任务被清除后重新拉起
        val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
        startService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 系统杀死时不调用 stopForeground，让系统保留通知避免频繁重建
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "后台保活", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val customView = RemoteViews(packageName, R.layout.notification_keep_alive).apply {
            setTextViewText(R.id.notification_title, "澎湃记正在后台运行")
            setTextViewText(R.id.notification_text, "保持短信读取、磁贴识别等功能可用")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView)
            .build()
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
            start(context)
        }

        fun hideNotification(context: Context) {
            stop(context)
        }
    }
}
