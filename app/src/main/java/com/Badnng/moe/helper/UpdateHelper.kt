package com.Badnng.moe.helper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String
)

object UpdateHelper {
    private const val TAG = "UpdateHelper"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val STABLE_URL = "https://badnng.dpdns.org/https://raw.githubusercontent.com/badnng/Hyper-pick-up-code/refs/heads/master/Stable.json"
    private const val DEV_URL = "https://badnng.dpdns.org/https://raw.githubusercontent.com/badnng/Hyper-pick-up-code/refs/heads/master/DevUI.json"
    private const val DOWNLOAD_BASE_URL = "https://badnng.dpdns.org/"

    // 下载状态跟踪
    @Volatile
    var isDownloading = false
        private set

    // 当前下载的版本信息
    @Volatile
    var currentDownloadingVersion: UpdateInfo? = null
        private set

    // 已下载的文件
    @Volatile
    var downloadedFile: File? = null
        private set

    // 当前下载进度
    @Volatile
    var currentProgress: Float = 0f
        private set

    // 本次下载是否由用户暂停结束（用于避免提示“已损坏/失效”）
    @Volatile
    private var pausedStop = false

    fun consumePausedStop(): Boolean {
        val value = pausedStop
        pausedStop = false
        return value
    }

    // 更新当前下载状态
    fun setDownloadingState(downloading: Boolean, version: UpdateInfo? = null, file: File? = null) {
        isDownloading = downloading
        currentDownloadingVersion = if (downloading) version else null
        downloadedFile = if (downloading) null else file
        if (downloading) currentProgress = 0f
        Log.d(TAG, "下载状态更新: isDownloading=$downloading, version=${version?.versionName}, file=${file?.name}")
    }

    fun updateProgress(progress: Float) {
        currentProgress = progress
    }

    suspend fun checkUpdate(isDev: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = if (isDev) DEV_URL else STABLE_URL
            AppLogger.update("UpdateHelper checkUpdate: channel=${if (isDev) "dev" else "stable"}, url=$url")
            Log.d(TAG, "开始检查更新 - 通道: ${if (isDev) "测试版" else "正式版"}")
            Log.d(TAG, "请求URL: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            Log.d(TAG, "HTTP响应码: ${response.code}")
            Log.d(TAG, "HTTP响应消息: ${response.message}")
            AppLogger.update("UpdateHelper HTTP ${response.code} ${response.message}")

            val body = response.body?.string()
            if (body == null) {
                Log.e(TAG, "响应体为空")
                AppLogger.update("UpdateHelper response body is null")
                return@withContext null
            }

            Log.d(TAG, "响应内容: $body")

            val json = JSONObject(body)
            val versionCode = json.getLong("versionCode")
            val versionName = json.getString("versionName")
            val releaseNotes = json.getString("releaseNotes")
            val downloadUrl = json.getString("downloadUrl")

            Log.d(TAG, "解析结果:")
            Log.d(TAG, "  - versionCode: $versionCode")
            Log.d(TAG, "  - versionName: $versionName")
            Log.d(TAG, "  - releaseNotes: $releaseNotes")
            Log.d(TAG, "  - downloadUrl: $downloadUrl")
            AppLogger.update("UpdateHelper parsed: version=$versionName($versionCode), notes=$releaseNotes")

            return@withContext UpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            AppLogger.update("UpdateHelper checkUpdate failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun downloadUpdate(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit,
        isPaused: () -> Boolean
    ): File? = withContext(Dispatchers.IO) {
        try {
            // 降低线程优先级，减少对 UI 线程的影响
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

            pausedStop = false

            // 设置下载状态
            setDownloadingState(true, updateInfo)

            val downloadUrl = DOWNLOAD_BASE_URL + updateInfo.downloadUrl
            AppLogger.update("UpdateHelper download start: ${updateInfo.versionName}, url=$downloadUrl")
            Log.d(TAG, "开始下载: $downloadUrl")

            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, "update_${updateInfo.versionName}.apk")
            var downloadedBytes = if (file.exists()) file.length() else 0L
            var totalBytes = -1L
            var lastReportedBytes = 0L
            val reportInterval = 64 * 1024L // 每 64KB 才回调一次进度，减少内存抖动

            downloadLoop@ while (true) {
                while (isPaused()) {
                    pausedStop = true
                    delay(150)
                }

                val requestBuilder = Request.Builder().url(downloadUrl)
                if (downloadedBytes > 0L) {
                    requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
                }
                val request = requestBuilder.build()
                val call = client.newCall(request)
                val response = call.execute()
                if (!response.isSuccessful) {
                    response.close()
                    Log.e(TAG, "下载失败: http=${response.code}")
                    AppLogger.update("UpdateHelper download failed: HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body ?: return@withContext null

                val code = response.code
                val isPartialResponse = code == 206
                val appendMode = downloadedBytes > 0L && isPartialResponse

                if (downloadedBytes > 0L && !isPartialResponse) {
                    // 服务端不支持断点续传，回退到完整重下
                    downloadedBytes = 0L
                    if (file.exists()) file.delete()
                }

                val responseLength = body.contentLength()
                if (totalBytes <= 0L && responseLength > 0L) {
                    totalBytes = if (appendMode) downloadedBytes + responseLength else responseLength
                }

                var pausedDuringStream = false
                body.byteStream().use { input ->
                    FileOutputStream(file, appendMode).use { output ->
                        val buffer = ByteArray(32768) // 32KB 缓冲区，减少系统调用次数
                        while (true) {
                            if (isPaused()) {
                                pausedStop = true
                                pausedDuringStream = true
                                output.flush() // 暂停前确保数据写入磁盘
                                call.cancel()
                                break
                            }
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            // 每 64KB 回调一次进度，避免频繁回调导致内存抖动
                            if (totalBytes > 0L && downloadedBytes - lastReportedBytes >= reportInterval) {
                                lastReportedBytes = downloadedBytes
                                val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                currentProgress = progress
                                onProgress(progress)
                            }
                        }
                        output.flush() // 确保最后的数据写入磁盘
                    }
                }

                response.close()
                // 最终进度回调
                if (totalBytes > 0L) {
                    val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    currentProgress = progress
                    onProgress(progress)
                }
                if (pausedDuringStream) {
                    continue@downloadLoop
                }
                break@downloadLoop
            }

            // 下载完成，设置下载状态
            Log.d(TAG, "下载完成: ${file.name}")
            AppLogger.update("UpdateHelper download complete: ${file.name} (${file.length()} bytes)")
            setDownloadingState(false, null, file)
            file
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            AppLogger.update("UpdateHelper download exception: ${e.message}")
            setDownloadingState(false)
            e.printStackTrace()
            null
        }
    }

    fun installUpdate(context: Context, file: File) {
        AppLogger.update("UpdateHelper installUpdate: ${file.name}")
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            StorageCleanupHelper.markUpdateApkForCleanup(context, file.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(context, "无法启动安装器", Toast.LENGTH_SHORT).show()
        }
    }

    fun showNoUpdateToast(context: Context) {
        Toast.makeText(context, "暂无新版本更新", Toast.LENGTH_SHORT).show()
    }
}
