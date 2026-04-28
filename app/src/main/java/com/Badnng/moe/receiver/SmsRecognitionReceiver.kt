package com.Badnng.moe.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import com.Badnng.moe.service.SmsRecognitionService

class SmsRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: "未知"
        val fullText = messages.joinToString("") { it.messageBody ?: "" }
        if (fullText.isBlank()) return

        Log.d("SmsRecognition", "收到短信：$sender, 长度：${fullText.length}")

        try {
            val serviceIntent = Intent(context, SmsRecognitionService::class.java).apply {
                putExtra("smsText", fullText)
                putExtra("sender", sender)
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
        } catch (e: Throwable) {
            Log.e("SmsRecognition", "启动服务失败: ${e.message}")
        }
    }
}
