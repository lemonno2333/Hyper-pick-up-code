package com.Badnng.moe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.ocr.TextRecognitionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsRecognitionService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        val smsText = intent?.getStringExtra("smsText")
        val sender = intent?.getStringExtra("sender") ?: "未知"

        if (!smsText.isNullOrBlank()) {
            Log.d("SmsRecognition", "Service 开始处理短信：sender=$sender, length=${smsText.length}")
            scope.launch {
                try {
                    processSms(smsText, sender)
                } catch (e: Exception) {
                    Log.e("SmsRecognition", "处理短信失败", e)
                }
                stopSelf()
            }
        } else {
            Log.d("SmsRecognition", "Service 无短信内容，停止")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun processSms(smsText: String, sender: String) {
        val helper = TextRecognitionHelper(applicationContext)
        val result = helper.recognizeFromText(smsText)
        helper.close()

        Log.d("SmsRecognition", "识别结果：code=${result.code}, type=${result.type}, brand=${result.brand}")

        if (result.code != null) {
            val order = OrderEntity(
                takeoutCode = result.code,
                qrCodeData = result.qr,
                screenshotPath = "",
                recognizedText = smsText,
                orderType = result.type,
                brandName = result.brand,
                fullText = result.fullText,
                pickupLocation = result.pickupLocation,
                sourceApp = "短信识别",
                sourcePackage = sender
            )

            val db = OrderDatabase.getDatabase(applicationContext)
            val orderDao = db.orderDao()
            val groupDao = db.orderGroupDao()
            orderDao.insert(order)

            DailyExpressGroupingHelper.regroupPendingExpressByDay(orderDao, groupDao, this)

            val notificationHelper = NotificationHelper(applicationContext)
            val refreshedOrder = orderDao.getOrderById(order.id)
            val groupId = refreshedOrder?.groupId
            if (groupId != null) {
                val group = groupDao.getGroupById(groupId)
                val groupOrders = orderDao.getAllOrdersList()
                    .filter { it.groupId == groupId && !it.isCompleted }
                    .sortedByDescending { it.createdAt }
                if (group != null && groupOrders.size >= 2) {
                    groupOrders.forEach { notificationHelper.cancelNotification(it.id) }
                    groupDao.updateOrderCount(groupId, groupOrders.size)
                    notificationHelper.showGroupNotification(group.copy(orderCount = groupOrders.size), groupOrders)
                } else {
                    notificationHelper.showPromotedLiveUpdate(order, result.brand)
                }
            } else {
                notificationHelper.showPromotedLiveUpdate(order, result.brand)
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "sms_recognition"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "短信识别", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("正在识别短信")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1004
    }
}
