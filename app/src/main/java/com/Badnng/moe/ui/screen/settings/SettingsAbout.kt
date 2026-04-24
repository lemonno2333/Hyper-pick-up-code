package com.Badnng.moe.ui.screen.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.helper.BackupHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.helper.UpdateHelper
import com.Badnng.moe.helper.UpdateInfo
import com.Badnng.moe.ui.component.UpdateDialog
import com.Badnng.moe.ui.component.UpdateProgressDialog
import com.Badnng.moe.ui.component.PreferenceSection
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun AboutSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val versionName = remember { getVersionName(context) }
    val versionCode = remember { getVersionCode(context) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    val pausedFlag = remember { AtomicBoolean(false) }
    var isChecking by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val notificationHelper = remember { NotificationHelper(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Surface(
            modifier = Modifier.size(86.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.abouttopicon),
                    contentDescription = "Logo",
                    modifier = Modifier.size(66.dp)
                )
            }
        }


        Spacer(Modifier.height(16.dp))
        Text(text = "澎湃记", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = "版本 $versionName($versionCode)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

        Spacer(Modifier.height(24.dp))

        // 检查更新按钮
        val networkUpdateEnabled = prefs.getBoolean("network_update_enabled", false)
        val updateChannel = prefs.getString("update_channel", "stable") ?: "stable"

        if (networkUpdateEnabled) {
            Button(
                onClick = {
                    performHaptic()
                    isChecking = true
                    coroutineScope.launch {
                        val info = UpdateHelper.checkUpdate(updateChannel == "dev")
                        isChecking = false
                        if (info != null) {
                            val localVersion = UpdateHelper.getCurrentVersionCode(context)
                            android.util.Log.d("UpdateCheck", "本地版本号: $localVersion")
                            android.util.Log.d("UpdateCheck", "远程版本号: ${info.versionCode}")
                            android.util.Log.d("UpdateCheck", "版本比较: ${info.versionCode} > $localVersion = ${info.versionCode > localVersion}")

                            if (info.versionCode > localVersion) {
                                android.util.Log.d("UpdateCheck", "发现新版本")

                                // 检查是否正在下载
                                if (UpdateHelper.isDownloading) {
                                    android.util.Log.d("UpdateCheck", "正在下载中，显示下载进度弹窗")
                                    updateInfo = info
                                    showProgressDialog = true
                                }
                                // 检查是否已下载
                                else if (UpdateHelper.downloadedFile != null && UpdateHelper.downloadedFile!!.exists()) {
                                    android.util.Log.d("UpdateCheck", "已下载完成，直接安装")
                                    UpdateHelper.installUpdate(context, UpdateHelper.downloadedFile!!)
                                }
                                // 显示更新弹窗
                                else {
                                    android.util.Log.d("UpdateCheck", "显示更新弹窗")
                                    updateInfo = info
                                    showUpdateDialog = true
                                }
                            } else {
                                android.util.Log.d("UpdateCheck", "当前已是最新版本")
                                UpdateHelper.showNoUpdateToast(context)
                            }
                        } else {
                            android.util.Log.e("UpdateCheck", "获取更新信息失败")
                        }
                    }
                },
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.SystemUpdate, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isChecking) "检查中..." else "检查更新")
            }
        }

        Spacer(Modifier.height(48.dp))

        PreferenceSection(title = "致谢") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OssItem("Jetpack Compose", "现代化声明式 UI 框架", "https://developer.android.com/jetpack/compose", performHaptic)
                OssItem("Material Design 3", "Google 现代设计语言规范", "https://m3.material.io", performHaptic)
                OssItem("ML Kit", "Google 强大的设备端机器学习 SDK", "https://developers.google.com/ml-kit", performHaptic)
                OssItem("Shizuku", "利用系统 API 实现高级权限调用", "https://shizuku.rikka.app", performHaptic)
                OssItem("ZXing", "高效的二维码生成与处理库", "https://github.com/zxing/zxing", performHaptic)
                OssItem("Room", "官方高性能 SQLite 数据库封装", "https://developer.android.com/training/data-storage/room", performHaptic)
                OssItem("Coil", "现代化的 Android 图片加载库", "https://coil-kt.github.io/coil/", performHaptic)
                OssItem("Kyant Backdrop", "优雅的毛玻璃与层级模糊效果实现", "https://github.com/Kyant0/AndroidLiquidGlass", performHaptic)
                OssItem("Paddle Lite" , "使用深度识别算法在本地进行OCR识别" , "https://www.paddlepaddle.org.cn/paddle/paddlelite", performHaptic)
                OssItem("Paddle4Android" , "不需要学习原理即可一键在Android上引入OCR识别" , "https://github.com/equationl/paddleocr4android", performHaptic)
            }
        }

        Spacer(Modifier.height(48.dp))

        // 备份与恢复
        PreferenceSection(title = "备份与恢复") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 备份相关状态
                var isBackingUp by remember { mutableStateOf(false) }
                var isRestoring by remember { mutableStateOf(false) }
                var pendingBackupData by remember { mutableStateOf<ByteArray?>(null) }

                // 创建备份文件的launcher
                val createBackupLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    uri?.let {
                        coroutineScope.launch {
                            try {
                                pendingBackupData?.let { data ->
                                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                        outputStream.write(data)
                                    }
                                    android.widget.Toast.makeText(context, "备份成功！", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("Backup", "保存备份失败", e)
                                android.widget.Toast.makeText(context, "保存备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            } finally {
                                pendingBackupData = null
                            }
                        }
                    }
                }

                // 恢复备份的launcher
                val restoreBackupLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        isRestoring = true
                        coroutineScope.launch {
                            try {
                                val backupData = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    inputStream.readBytes()
                                } ?: throw Exception("无法读取备份文件")

                                val restoredData = BackupHelper.restoreBackup(context, backupData)

                                // 恢复设置
                                val editor = prefs.edit()
                                restoredData.settings.forEach { (key, value) ->
                                    when (value) {
                                        is Boolean -> editor.putBoolean(key, value)
                                        is String -> editor.putString(key, value)
                                        is Int -> editor.putInt(key, value)
                                        is Long -> editor.putLong(key, value)
                                        is Float -> editor.putFloat(key, value)
                                    }
                                }
                                editor.apply()

                                // 恢复订单数据
                                val database = OrderDatabase.getDatabase(context)
                                val orderDao = database.orderDao()
                                restoredData.orders.forEach { order ->
                                    val existingOrder = orderDao.getOrderById(order.id)
                                    if (existingOrder == null) {
                                        orderDao.insert(order)
                                    }
                                }

                                android.widget.Toast.makeText(context, "恢复成功！共恢复 ${restoredData.orders.size} 条取餐码", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.util.Log.e("Backup", "恢复备份失败", e)
                                android.widget.Toast.makeText(context, "恢复备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            } finally {
                                isRestoring = false
                            }
                        }
                    }
                }

                // 备份卡片
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "备份数据", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "备份取餐码和设置到压缩包", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                performHaptic()
                                isBackingUp = true
                                coroutineScope.launch {
                                    try {
                                        // 获取订单数据
                                        val database = OrderDatabase.getDatabase(context)
                                        val orders = database.orderDao().getAllOrdersList()

                                        // 获取设置数据
                                        val settingsMap = mutableMapOf<String, Any?>()
                                        val allPrefs = prefs.all
                                        allPrefs.forEach { (key, value) ->
                                            settingsMap[key] = value
                                        }

                                        // 创建备份
                                        val backupData = BackupHelper.createBackup(context, orders, settingsMap)
                                        pendingBackupData = backupData

                                        // 使用ActivityResultLauncher保存文件
                                        val fileName = BackupHelper.generateBackupFileName()
                                        createBackupLauncher.launch(fileName)

                                    } catch (e: Exception) {
                                        android.util.Log.e("Backup", "备份失败", e)
                                        android.widget.Toast.makeText(context, "备份失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        isBackingUp = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isBackingUp
                        ) {
                            if (isBackingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("备份")
                            }
                        }
                    }
                }

                // 恢复卡片
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "恢复数据", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "从备份文件恢复取餐码和设置", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                performHaptic()
                                restoreBackupLauncher.launch(arrayOf("*/*"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isRestoring
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("恢复")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(64.dp))
        val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
        Text(
            text = "Made with ❤️ by Badnng and Vibe Codding\n© $currentYear 澎湃记",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(32.dp))
    }

    // 更新弹窗
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateDialog = false },
            onInstall = {
                showUpdateDialog = false
                showProgressDialog = true
                isPaused = false
                pausedFlag.set(false)
                coroutineScope.launch {
                    val file = UpdateHelper.downloadUpdate(
                        context = context,
                        updateInfo = updateInfo!!,
                        onProgress = {
                            downloadProgress = it
                            if (!showProgressDialog) {
                                notificationHelper.showUpdateDownloadNotification(
                                    versionName = updateInfo!!.versionName,
                                    progress = it,
                                    isPaused = pausedFlag.get()
                                )
                            }
                        },
                        isPaused = { pausedFlag.get() }
                    )
                    showProgressDialog = false
                    notificationHelper.cancelUpdateDownloadNotification()
                    if (file != null) {
                        UpdateHelper.installUpdate(context, file)
                    }
                }
            }
        )
    }

    // 下载进度弹窗
    if (showProgressDialog && updateInfo != null) {
        UpdateProgressDialog(
            progress = downloadProgress,
            isPaused = isPaused,
            onPause = {
                isPaused = true
                pausedFlag.set(true)
                updateInfo?.let {
                    notificationHelper.showUpdateDownloadNotification(it.versionName, downloadProgress, true)
                }
            },
            onResume = {
                isPaused = false
                pausedFlag.set(false)
                updateInfo?.let {
                    notificationHelper.showUpdateDownloadNotification(it.versionName, downloadProgress, false)
                }
            },
            onCancel = {
                showProgressDialog = false
                updateInfo?.let {
                    notificationHelper.showUpdateDownloadNotification(it.versionName, downloadProgress, isPaused)
                }
            }
        )
    }
}

@Composable
fun OssItem(name: String, desc: String, url: String, performHaptic: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Surface(
        onClick = {
            performHaptic()
            uriHandler.openUri(url)
        },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(2.dp))
                Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}
