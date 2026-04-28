package com.Badnng.moe.helper

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AppLogger {

    private const val TAG = "AppLogger"
    private const val LOG_DIR = "logs"
    private const val RETENTION_DAYS = 3

    private var recognitionWriter: BufferedWriter? = null
    private var appWriter: BufferedWriter? = null
    private var updateWriter: BufferedWriter? = null
    private var crashWriter: BufferedWriter? = null

    private var currentDate: String = ""
    private var logsDir: File? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())

    // ==================== 初始化 ====================

    fun init(context: Context) {
        logsDir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        currentDate = dateFormat.format(Date())
        openWriters()

        // 延迟 5 秒后执行清理和压缩，避免启动时 IO 阻塞
        scope.launch {
            delay(5000)
            cleanupOldLogs(context)
            compressPreviousDayLogs(context)
        }

        // 设置崩溃处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crash(throwable, thread.name)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        app("AppLogger initialized, date=$currentDate")
    }

    // ==================== 日志写入 ====================

    fun recognition(message: String) {
        checkDateSwitch()
        writeLog(recognitionWriter, "RECOGNITION", message)
    }

    fun app(message: String) {
        checkDateSwitch()
        writeLog(appWriter, "APP", message)
    }

    fun update(message: String) {
        checkDateSwitch()
        writeLog(updateWriter, "UPDATE", message)
    }

    fun service(message: String) {
        checkDateSwitch()
        writeLog(appWriter, "SERVICE", message)
    }

    fun crash(throwable: Throwable, tag: String = "") {
        checkDateSwitch()
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val trace = sw.toString()
        val header = if (tag.isNotEmpty()) "[$tag] ${throwable.message}" else throwable.message ?: "Unknown crash"
        writeLog(crashWriter, "CRASH", "$header\n$trace")
    }

    // ==================== 内部写入 ====================

    private fun writeLog(writer: BufferedWriter?, level: String, message: String) {
        try {
            val time = timeFormat.format(Date())
            writer?.apply {
                write("[$time] [$level] $message\n")
                flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeLog failed: $level", e)
        }
    }

    private fun checkDateSwitch() {
        val today = dateFormat.format(Date())
        if (today != currentDate) {
            currentDate = today
            closeWriters()
            openWriters()
        }
    }

    private fun openWriters() {
        val dir = File(logsDir, currentDate).apply { mkdirs() }
        recognitionWriter = getWriter(File(dir, "recognition.log"))
        appWriter = getWriter(File(dir, "app.log"))
        updateWriter = getWriter(File(dir, "update.log"))
        crashWriter = getWriter(File(dir, "crash.log"))
    }

    private fun getWriter(file: File): BufferedWriter {
        return BufferedWriter(FileWriter(file, true), 8192)
    }

    private fun closeWriters() {
        fun closeQuietly(w: BufferedWriter?) {
            try { w?.close() } catch (_: Exception) {}
        }
        closeQuietly(recognitionWriter)
        closeQuietly(appWriter)
        closeQuietly(updateWriter)
        closeQuietly(crashWriter)
        recognitionWriter = null
        appWriter = null
        updateWriter = null
        crashWriter = null
    }

    // ==================== 压缩 ====================

    fun flush() {
        try {
            recognitionWriter?.flush()
            appWriter?.flush()
            updateWriter?.flush()
            crashWriter?.flush()
        } catch (_: Exception) {}
    }

    fun compressTodayLogs(context: Context): File? {
        val todayDir = File(logsDir, dateFormat.format(Date()))
        if (!todayDir.exists() || !todayDir.isDirectory) return null

        val logFiles = todayDir.listFiles { f -> f.extension == "log" && f.length() > 0 }
        if (logFiles.isNullOrEmpty()) return null

        val zipName = "${fileNameFormat.format(Date())}-com.Badnng.moe-Log.zip"
        val zipFile = File(logsDir, zipName)

        return try {
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                zos.setLevel(Deflater.BEST_COMPRESSION)
                for (file in logFiles) {
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            // 压缩成功后删除原目录
            todayDir.deleteRecursively()
            zipFile
        } catch (e: Exception) {
            Log.e(TAG, "compressTodayLogs failed", e)
            zipFile.delete()
            null
        }
    }

    private fun compressPreviousDayLogs(context: Context) {
        val today = dateFormat.format(Date())
        val dirs = logsDir?.listFiles { f -> f.isDirectory && f.name != today } ?: return

        for (dir in dirs) {
            val logFiles = dir.listFiles { f -> f.extension == "log" && f.length() > 0 } ?: continue
            if (logFiles.isEmpty()) continue

            val zipName = "${dir.name}-${fileNameFormat.format(Date())}-com.Badnng.moe-Log.zip"
            val zipFile = File(logsDir, zipName)

            try {
                ZipOutputStream(zipFile.outputStream()).use { zos ->
                    zos.setLevel(Deflater.BEST_COMPRESSION)
                    for (file in logFiles) {
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                dir.deleteRecursively()
            } catch (e: Exception) {
                Log.e(TAG, "compressPreviousDayLogs failed for ${dir.name}", e)
                zipFile.delete()
            }
        }
    }

    // ==================== 清理 ====================

    fun cleanupOldLogs(context: Context) {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60 * 60 * 1000
        val files = logsDir?.listFiles() ?: return

        for (file in files) {
            if (file.lastModified() < cutoff) {
                file.deleteRecursively()
            }
        }
    }

    // ==================== 导出 ====================

    fun getTodayLogFiles(context: Context): List<File> {
        val today = dateFormat.format(Date())
        val dir = File(logsDir, today)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension == "log" && f.length() > 0 }?.toList() ?: emptyList()
    }

    fun getExportableFiles(context: Context): List<File> {
        val files = mutableListOf<File>()

        // 当天日志目录
        val today = dateFormat.format(Date())
        val todayDir = File(logsDir, today)
        if (todayDir.exists()) {
            todayDir.listFiles { f -> f.extension == "log" && f.length() > 0 }?.let { files.addAll(it) }
        }

        // 之前的压缩包
        logsDir?.listFiles { f -> f.extension == "zip" }?.let { files.addAll(it) }

        return files
    }
}
