package com.Badnng.moe.rules

import android.content.Context
import android.util.Log
import com.Badnng.moe.helper.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RuleAutoUpdateManager {

    private const val TAG = "RuleAutoUpdate"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 每个源的倒计时（分钟）
    private val _countdowns = MutableStateFlow<Map<String, Int>>(emptyMap())
    val countdowns: StateFlow<Map<String, Int>> = _countdowns

    @Volatile
    private var running = false

    fun start(context: Context) {
        if (running) return
        running = true
        Log.d(TAG, "自动更新管理器启动")
        AppLogger.update("RuleAutoUpdateManager started")
        scope.launch { loop(context.applicationContext) }
    }

    private suspend fun loop(context: Context) {
        val repo = RuleRepository(context)
        // 初始化倒计时（分钟）
        val config = repo.loadSystemConfig()
        val now = System.currentTimeMillis()
        val map = mutableMapOf<String, Int>()
        config.onlineSources.filter { it.enabled }.forEach { source ->
            val intervalMin = source.updateIntervalMinutes
            val elapsedMin = ((now - source.lastUpdated) / 60000).toInt()
            val remaining = (intervalMin - elapsedMin).coerceAtLeast(0)
            map[source.id] = if (remaining == 0) intervalMin else remaining
            Log.d(TAG, "初始化倒计时: ${source.name} [${source.id}] 间隔${intervalMin}分钟 已过${elapsedMin}分钟 剩余${map[source.id]}分钟")
        }
        _countdowns.value = map
        Log.d(TAG, "倒计时循环开始，共${map.size}个启用源")

        while (true) {
            delay(60_000L) // 每分钟检测一次
            val currentMap = _countdowns.value.toMutableMap()

            val sources = repo.loadSystemConfig().onlineSources.filter { it.enabled }

            // 移除已不存在的源
            val activeIds = sources.map { it.id }.toSet()
            currentMap.keys.filter { it !in activeIds }.forEach {
                currentMap.remove(it)
                Log.d(TAG, "移除已删除源的倒计时: $it")
            }

            // 添加新启用的源
            val currentTime = System.currentTimeMillis()
            sources.forEach { source ->
                if (source.id !in currentMap) {
                    val intervalMin = source.updateIntervalMinutes
                    val elapsedMin = ((currentTime - source.lastUpdated) / 60000).toInt()
                    val remaining = (intervalMin - elapsedMin).coerceAtLeast(0)
                    currentMap[source.id] = if (remaining == 0) intervalMin else remaining
                    Log.d(TAG, "新增启用源倒计时: ${source.name} [${source.id}] 剩余${currentMap[source.id]}分钟")
                }
            }

            // 递减
            val iterator = currentMap.entries.iterator()
            while (iterator.hasNext()) {
                val (id, min) = iterator.next()
                val newMin = min - 1
                if (newMin <= 0) {
                    val source = sources.firstOrNull { it.id == id }
                    if (source != null) {
                        Log.d(TAG, "倒计时到期，触发更新: ${source.name} [${source.id}]")
                        scope.launch { silentUpdate(context, source) }
                        currentMap[id] = source.updateIntervalMinutes
                        Log.d(TAG, "重置倒计时: ${source.name} [${source.id}] → ${currentMap[id]}分钟")
                    } else {
                        iterator.remove()
                    }
                } else {
                    currentMap[id] = newMin
                }
            }

            _countdowns.value = currentMap
        }
    }

    private suspend fun silentUpdate(context: Context, source: OnlineRuleSource) {
        try {
            val updater = RuleOnlineUpdater(context)
            val result = updater.fetchAndSaveSource(source)
            result.fold(
                onSuccess = { (updated, newSource) ->
                    val repo = RuleRepository(context)
                    val config = repo.loadSystemConfig()
                    val newSources = config.onlineSources.map {
                        if (it.id == source.id) newSource else it
                    }
                    repo.saveOnlineSources(newSources)
                    if (updated) {
                        // 如果当前激活的是这个源，重新加载引擎
                        if (RecognitionRuleEngine.currentSourceId == source.id) {
                            RecognitionRuleEngine.reload(context)
                        }
                        Log.d(TAG, "后台自动更新成功: ${source.name}")
                        AppLogger.update("Rule auto-update success: ${source.name}")
                    }
                },
                onFailure = { e ->
                    Log.w(TAG, "后台自动更新失败: ${source.name} - ${e.message}")
                    AppLogger.update("Rule auto-update failed: ${source.name} - ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "后台自动更新异常: ${source.name} - ${e.message}")
        }
    }

    // 手动重置某个源的倒计时（比如手动更新后）
    fun resetCountdown(sourceId: String, intervalMinutes: Int) {
        _countdowns.value = _countdowns.value.toMutableMap().apply {
            put(sourceId, intervalMinutes)
        }
        Log.d(TAG, "手动重置倒计时: [$sourceId] → ${intervalMinutes}分钟")
    }
}
