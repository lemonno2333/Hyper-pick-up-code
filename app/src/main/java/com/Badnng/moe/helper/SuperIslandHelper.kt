package com.Badnng.moe.helper

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import java.lang.reflect.Method

object SuperIslandHelper {

    // ==================== 设备检测 ====================
    private var cachedSupported: Boolean? = null
    private var cachedProtocol: Int? = null

    fun isSupportIsland(): Boolean = try {
        val method = Class.forName("android.os.SystemProperties")
            .getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        method.invoke(null, "persist.sys.feature.island", false) as? Boolean ?: false
    } catch (_: Exception) { false }

    fun getFocusProtocol(context: Context): Int = try {
        Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
    } catch (_: Exception) { 0 }

    fun hasFocusPermission(context: Context): Boolean = try {
        val uri = android.net.Uri.parse("content://miui.statusbar.notification.public")
        val args = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(uri, "canShowFocus", null, args)
            ?.getBoolean("canShowFocus", false) ?: false
    } catch (_: Exception) { false }

    fun isDeviceSupported(context: Context): Boolean {
        if (cachedSupported == null) {
            cachedProtocol = getFocusProtocol(context)
            cachedSupported = isSupportIsland() && (cachedProtocol!! >= 3) && hasFocusPermission(context)
        }
        return cachedSupported!!
    }

    fun clearCache() {
        cachedSupported = null
        cachedProtocol = null
    }

    // ==================== 图片 Key 常量 ====================
    private const val PIC_MAIN = "miui.focus.pic_icon_main"
    private const val PIC_LAND = "miui.focus.pic_icon_main"
    private const val PIC_TICKER = "miui.focus.pic_ticker"
    private const val PIC_AOD = "miui.focus.pic_aod"

    // ==================== 动作 Key 常量 ====================
    private const val ACT_COMPLETE = "miui.focus.action_complete"
    private const val ACT_IDENTITY = "miui.focus.action_identity"

    // ==================== 图标映射 ====================
    private fun brandIconRes(brandName: String?, orderType: String?): Int = when (brandName) {
        "麦当劳" -> R.drawable.ic_mcdonalds
        "肯德基", "KFC" -> R.drawable.ic_kfc
        "瑞幸" -> R.drawable.ic_luckin
        "喜茶" -> R.drawable.ic_heytea
        "星巴克" -> R.drawable.ic_starbucks
        "霸王茶姬" -> R.drawable.ic_chagee
        "古茗" -> R.drawable.ic_goodme
        "蜜雪冰城" -> R.drawable.ic_mixue
        else -> when (orderType) {
            "饮品" -> R.drawable.ic_drink
            "快递" -> R.drawable.ic_package
            else -> R.drawable.ic_restaurant
        }
    }

    // ==================== miui.focus.pics Bundle ====================
    private fun buildPicsBundle(context: Context, iconRes: Int): Bundle {
        val picsBundle = Bundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putParcelable(PIC_MAIN, Icon.createWithResource(context, iconRes))
                putParcelable(PIC_LAND, Icon.createWithResource(context, iconRes))
                putParcelable(PIC_TICKER, Icon.createWithResource(context, R.mipmap.ic_launcher))
                putParcelable(PIC_AOD, Icon.createWithResource(context, R.mipmap.ic_launcher))
            }
        }
        return Bundle().apply { putBundle("miui.focus.pics", picsBundle) }
    }

    // ==================== miui.focus.actions Bundle ====================
    private fun buildActionsBundle(
        context: Context,
        iconRes: Int,
        completePI: PendingIntent,
        identityPI: PendingIntent?,
        hasIdentity: Boolean
    ): Bundle {
        val actionsBundle = Bundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putParcelable(ACT_COMPLETE, Notification.Action.Builder(null, "已完成", completePI).build())
                if (hasIdentity && identityPI != null) {
                    putParcelable(ACT_IDENTITY, Notification.Action.Builder(null, "身份码", identityPI).build())
                }
            }
        }
        return Bundle().apply { putBundle("miui.focus.actions", actionsBundle) }
    }

    private const val PICKUP_TEMPLATE = """
{
    "param_v2": {
        "protocol": 3,
        "business": "pickup",
        "updatable": true,
        "ticker": "{{ticker}}",
        "enableFloat": true,
        "isShowNotification": true,
        "islandFirstFloat": true,
        "aodTitle": "{{contentText}}",
        "picInfo": {
            "type": 2,
            "pic": "miui.focus.pic_icon_main",
            "loop": false,
            "autoplay": false,
            "number": 0
        },
        "smallWindowInfo": {
            "targetPage": "{{targetPage}}"
        },
        "param_island": {
            "islandProperty": 2,
            "islandOrder": false,
            "dismissIsland": false,
            "needCloseAnimation": true,
            "bigIslandArea": {
                "imageTextInfoLeft": {
                    "type": 1,
                    "picInfo": {
                        "type": 1,
                        "pic": "miui.focus.pic_icon_main",
                        "loop": false,
                        "autoplay": false,
                        "number": 0
                    }
                },
                "textInfo": {
                    "frontTitle": "",
                    "title": "{{smallCode}}",
                    "content": "",
                    "showHighlightColor": false,
                    "narrowFont": false
                }
            },
            "smallIslandArea": {
                "imageTextInfoRight": {
                    "type": 6,
                    "textInfo": {
                        "title": "{{smallCode}}",
                        "showHighlightColor": false
                    },
                    "picInfo": {
                        "type": 1,
                        "pic": "miui.focus.pic_icon_main"
                    }
                }
            }
        },
        "chatInfo": {
            "picProfile": "miui.focus.pic_icon_main",
            "title": "{{smallCode}}",
            "content": "{{brand}}",
            "colorTitle": "#000000",
            "colorTitleDark": "#FFFFFF",
            "colorContent": "#666666",
            "colorContentDark": "#AAAAAA"
        },
        "hintInfo": {
            "type": 1,
            "title": "{{hintTitle}}",
            "colorTitleDark": "#FFFFFF",
            "colorContentBg": "{{hintActionBgColor}}",
            "actionInfo": {
                "action": "{{hintAction}}",
                "actionTitle": "{{hintActionTitle}}",
                "actionTitleColor": "#FFFFFF",
                "actionBgColor": "{{hintActionBgColor}}",
                "actionIntentType": 1
            }
        }
    }
}
"""

    private fun buildPickupJson(
        context: Context,
        brand: String,
        contentText: String,
        smallCode: String,
        isExpress: Boolean,
        hasIdentity: Boolean,
        pickupLocation: String? = null,
        orderType: String? = null
    ): String {
        val hintAction = if (hasIdentity) ACT_IDENTITY else ACT_COMPLETE
        val hintTitle = when {
            !pickupLocation.isNullOrBlank() -> pickupLocation
            isExpress -> "请仔细核对您的取件码"
            orderType == "饮品" -> "请注意大堂/荧幕叫号"
            else -> "请注意及时取餐"
        }
        val hintActionTitle = if (hasIdentity) "身份码" else "已完成"
        val hintActionBgColor = if (hasIdentity) "#007AFF" else "#34C759"
        val hintContent = if (isExpress) "快递" else "外卖"
        val targetPage = "${context.packageName}.MainActivity"

        return PICKUP_TEMPLATE
            .replace("{{ticker}}", contentText)
            .replace("{{brand}}", brand)
            .replace("{{contentText}}", contentText)
            .replace("{{hintTitle}}", hintTitle)
            .replace("{{hintContent}}", hintContent)
            .replace("{{hintAction}}", hintAction)
            .replace("{{hintActionTitle}}", hintActionTitle)
            .replace("{{hintActionBgColor}}", hintActionBgColor)
            .replace("{{targetPage}}", targetPage)
            .replace("{{smallCode}}", smallCode)
    }

    // ==================== 发送测试通知 ====================
    fun sendTestNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "promoted_live_update_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(channelId, "订单实况更新", NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = "用于在状态栏和锁屏显示订单实时进度" }
                )
            }

            val testContent = "测试取件码: TEST123456"
            val dismissPI = PendingIntent.getBroadcast(
                context, 999999,
                Intent(context, com.Badnng.moe.receiver.TestNotificationDismissReceiver::class.java).apply {
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = if (isDeviceSupported(context)) {
                val picsBundle = buildPicsBundle(context, R.drawable.ic_package)
                val actionsBundle = buildActionsBundle(context, R.drawable.ic_package, dismissPI, null, false)
                val paramsJson = buildPickupJson(
                    context = context,
                    brand = "测试",
                    contentText = testContent,
                    smallCode = "TEST123456",
                    isExpress = true,
                    hasIdentity = false
                )

                Notification.Builder(context, channelId)
                    .setContentTitle("快递待取 - 测试")
                    .setContentText(testContent)
                    .setSmallIcon(R.drawable.ic_package)
                    .setContentIntent(dismissPI)
                    .setOngoing(true)
                    .addExtras(picsBundle)
                    .addExtras(actionsBundle)
                    .build()
                    .apply { extras.putString("miui.focus.param", paramsJson) }
            } else {
                Notification.Builder(context, channelId)
                    .setContentTitle("快递待取 - 测试")
                    .setContentText(testContent)
                    .setSmallIcon(R.drawable.ic_package)
                    .setContentIntent(dismissPI)
                    .setOngoing(true)
                    .addAction(Notification.Action.Builder(null, "已完成", dismissPI).build())
                    .build()
            }

            if (Build.VERSION.SDK_INT >= 35) {
                notification.extras.putBoolean("android.requestPromotedOngoing", true)
            }

            nm.notify(999999, notification)
            android.widget.Toast.makeText(
                context,
                if (isDeviceSupported(context)) "已发送岛通知" else "降级原生通知",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            android.util.Log.e("SuperIsland", "sendTest error", e)
            android.widget.Toast.makeText(context, "发送失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // ==================== 构建单条订单通知 ====================
    fun buildPromotedNotification(
        context: Context,
        channelId: String,
        order: OrderEntity,
        brandName: String?,
        isExpress: Boolean,
        completePendingIntent: PendingIntent,
        viewPendingIntent: PendingIntent,
        identityChooserPendingIntent: PendingIntent?
    ): Notification {
        val label = if (isExpress) "取件码" else "取餐码"
        val contentText = "$label: ${order.takeoutCode}"
        val iconRes = brandIconRes(brandName, order.orderType)

        val picsBundle = buildPicsBundle(context, iconRes)
        val actionsBundle = buildActionsBundle(
            context, iconRes,
            completePendingIntent, identityChooserPendingIntent, isExpress
        )
        val paramsJson = buildPickupJson(
            context = context,
            brand = brandName ?: if (isExpress) "新包裹" else "新订单",
            contentText = contentText,
            smallCode = order.takeoutCode,
            isExpress = isExpress,
            hasIdentity = isExpress && identityChooserPendingIntent != null,
            pickupLocation = order.pickupLocation,
            orderType = order.orderType
        )

        val notification = Notification.Builder(context, channelId)
            .setContentTitle(if (isExpress) "快递待取 - ${brandName ?: "新包裹"}" else "取餐提醒 - ${brandName ?: "新订单"}")
            .setContentText(contentText)
            .setSmallIcon(iconRes)
            .setContentIntent(viewPendingIntent)
            .setOngoing(true)
            .addExtras(picsBundle)
            .addExtras(actionsBundle)
            .build()

        notification.extras.putString("miui.focus.param", paramsJson)

        if (Build.VERSION.SDK_INT >= 35) {
            notification.extras.putBoolean("android.requestPromotedOngoing", true)
            try {
                if (Build.VERSION.SDK_INT >= 36)
                    notification.extras.putString("android.shortCriticalText", " ${order.takeoutCode}")
            } catch (_: Exception) {}
        }

        return notification
    }

    // ==================== 构建组通知 ====================
    fun buildGroupNotification(
        context: Context,
        channelId: String,
        group: OrderGroup,
        orders: List<OrderEntity>,
        isExpress: Boolean,
        completeAllPendingIntent: PendingIntent,
        groupDetailPendingIntent: PendingIntent,
        identityChooserPendingIntent: PendingIntent?
    ): Notification {
        val codes = orders.take(3).joinToString(", ") { it.takeoutCode }
        val more = if (orders.size > 3) " 等${orders.size}件" else ""
        val label = if (isExpress) "取件码" else "取餐码"
        val contentText = "$label: $codes$more"
        val iconRes = brandIconRes(group.brandName, group.orderType)

        val picsBundle = buildPicsBundle(context, iconRes)
        val actionsBundle = buildActionsBundle(
            context, iconRes,
            completeAllPendingIntent, identityChooserPendingIntent, isExpress
        )
        val paramsJson = buildPickupJson(
            context = context,
            brand = group.brandName ?: if (isExpress) "新包裹" else "新订单",
            contentText = contentText,
            smallCode = codes,
            isExpress = isExpress,
            hasIdentity = isExpress && identityChooserPendingIntent != null,
            pickupLocation = orders.firstNotNullOfOrNull { it.pickupLocation },
            orderType = group.orderType
        )

        val notification = Notification.Builder(context, channelId)
            .setContentTitle(if (isExpress) "快递待取 - ${group.brandName ?: "新包裹"}" else "取餐提醒 - ${group.brandName ?: "新订单"}")
            .setContentText(contentText)
            .setSmallIcon(iconRes)
            .setContentIntent(groupDetailPendingIntent)
            .setOngoing(true)
            .addExtras(picsBundle)
            .addExtras(actionsBundle)
            .build()

        notification.extras.putString("miui.focus.param", paramsJson)

        if (Build.VERSION.SDK_INT >= 35) {
            notification.extras.putBoolean("android.requestPromotedOngoing", true)
            try {
                if (Build.VERSION.SDK_INT >= 36)
                    notification.extras.putString("android.shortCriticalText", " ${orders.size}件")
            } catch (_: Exception) {}
        }

        return notification
    }
}