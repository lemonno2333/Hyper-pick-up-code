package com.Badnng.moe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.AppLogger
import com.Badnng.moe.helper.DailyExpressGroupingHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.ocr.MultiRecognitionResult
import com.Badnng.moe.ocr.TextRecognitionHelper
import java.io.File
import java.io.FileOutputStream

class ShareRecognitionService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.service("ShareRecognitionService onStartCommand")
        startForeground(NOTIFICATION_ID, createNotification())

        val imageUri = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("imageUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("imageUri")
        }

        if (imageUri != null) {
            AppLogger.service("ShareRecognitionService processing image: $imageUri")
            scope.launch {
                try {
                    processImage(imageUri)
                } catch (e: Exception) {
                    Log.e("ShareRecognition", "Error processing image", e)
                    AppLogger.service("ShareRecognitionService error: ${e.message}")
                }
                AppLogger.service("ShareRecognitionService stopping")
                stopSelf()
            }
        } else {
            AppLogger.service("ShareRecognitionService no imageUri, stopping")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun processImage(imageUri: Uri) {
        val inputStream = contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (bitmap == null) {
            return
        }

        val croppedBitmap = cropStatusBar(bitmap)
        val helper = TextRecognitionHelper(applicationContext)

        try {
            helper.initOcr()

            val singleResult = helper.recognizeAll(croppedBitmap)
            val hasExpressKeyword = singleResult.fullText.contains("\u53d6\u4ef6") ||
                singleResult.fullText.contains("\u53d6\u8d27") ||
                singleResult.fullText.contains("\u5feb\u9012") ||
                singleResult.fullText.contains("\u9a7f\u7ad9") ||
                singleResult.fullText.contains("\u83dc\u9e1f")
            val bitmapToUse = if (hasExpressKeyword) bitmap else croppedBitmap
            val multiResult = if (hasExpressKeyword || singleResult.type == "\u5feb\u9012") {
                helper.recognizeMultipleCodes(bitmapToUse)
            } else {
                MultiRecognitionResult(emptyList(), false)
            }
            val recognizedOrders = when {
                multiResult.hasMultipleCodes && multiResult.orders.size > 1 -> multiResult.orders
                singleResult.code != null -> listOf(singleResult)
                multiResult.orders.isNotEmpty() -> multiResult.orders
                else -> emptyList()
            }

            if (recognizedOrders.isEmpty()) {
                AppLogger.recognition("ShareRecognition: no codes found")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "\u672a\u8bc6\u522b\u5230\u53d6\u4ef6\u7801",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }

            val screenshotFile = File(filesDir, "screenshots/shared_${System.currentTimeMillis()}.png")
            screenshotFile.parentFile?.mkdirs()
            FileOutputStream(screenshotFile).use { outputStream ->
                bitmapToUse.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val database = OrderDatabase.getDatabase(applicationContext)
            val orderDao = database.orderDao()
            val orderGroupDao = database.orderGroupDao()
            val insertedOrders = mutableListOf<OrderEntity>()
            for (result in recognizedOrders) {
                val code = result.code ?: continue
                AppLogger.recognition("ShareRecognition code=$code, type=${result.type}, brand=${result.brand}, pickup=${result.pickupLocation}")
                val order = OrderEntity(
                    takeoutCode = code,
                    qrCodeData = result.qr,
                    screenshotPath = screenshotFile.absolutePath,
                    recognizedText = "\u5206\u4eab\u8bc6\u522b",
                    orderType = result.type,
                    brandName = result.brand,
                    fullText = result.fullText,
                    pickupLocation = result.pickupLocation,
                    sourceApp = "\u5206\u4eab\u8bc6\u522b"
                )
                orderDao.insert(order)
                insertedOrders.add(order)
            }
            if (insertedOrders.isEmpty()) return

            // 每次新识别后立即重整分组：不依赖打开 App。
            DailyExpressGroupingHelper.regroupPendingExpressByDay(orderDao, orderGroupDao, this)
            val notificationHelper = NotificationHelper(applicationContext)
            val refreshedInsertedOrders = insertedOrders.mapNotNull { orderDao.getOrderById(it.id) }
            val groupedIds = refreshedInsertedOrders.mapNotNull { it.groupId }.toSet()
            val allOrders = orderDao.getAllOrdersList()

            if (groupedIds.isNotEmpty()) {
                groupedIds.forEach { groupId ->
                    val group = orderGroupDao.getGroupById(groupId) ?: return@forEach
                    val groupOrders = allOrders
                        .filter { it.groupId == groupId && !it.isCompleted }
                        .sortedByDescending { it.createdAt }
                    if (groupOrders.size >= 2) {
                        groupOrders.forEach { notificationHelper.cancelNotification(it.id) }
                        orderGroupDao.updateOrderCount(groupId, groupOrders.size)
                        notificationHelper.showGroupNotification(
                            group.copy(orderCount = groupOrders.size),
                            groupOrders
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "\u65b0\u8bc6\u522b\u53d6\u4ef6\u7801\u5df2\u81ea\u52a8\u6574\u7406",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            refreshedInsertedOrders
                .filter { it.groupId == null }
                .forEach { order ->
                    notificationHelper.showPromotedLiveUpdate(order, order.brandName)
                }

            if (groupedIds.isEmpty()) {
                val firstCode = refreshedInsertedOrders.firstOrNull()?.takeoutCode
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        if (firstCode != null) "\u8bc6\u522b\u6210\u529f: $firstCode" else "\u8bc6\u522b\u6210\u529f",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            val refreshIntent = Intent("com.Badnng.moe.REFRESH_ORDERS")
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(refreshIntent)
        } finally {
            helper.close()
            bitmap.recycle()
            if (croppedBitmap != bitmap) {
                croppedBitmap.recycle()
            }
        }
    }

    private fun cropStatusBar(src: Bitmap): Bitmap {
        val statusBarHeight = 150
        val sideMargin = (src.width * 0.02).toInt()
        val targetWidth = (src.width * 0.92).toInt()
        val targetHeight = (src.height * 0.81).toInt()
        return if (src.height > statusBarHeight + targetHeight && src.width > sideMargin + targetWidth) {
            Bitmap.createBitmap(src, sideMargin, statusBarHeight, targetWidth, targetHeight)
        } else {
            src
        }
    }

    private fun createNotification(): Notification {
        val channelId = "share_recognition"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "\u5206\u4eab\u8bc6\u522b",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("\u6b63\u5728\u8bc6\u522b\u622a\u56fe")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1002
    }
}
