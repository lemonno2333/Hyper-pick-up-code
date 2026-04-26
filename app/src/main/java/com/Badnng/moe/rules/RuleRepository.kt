package com.Badnng.moe.rules

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class RuleRepository(private val context: Context) {

    private val rulesDir = File(context.filesDir, "rules")

    init {
        rulesDir.mkdirs()
    }

    suspend fun loadBuiltIn(): RecognitionRules = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("default_rules.json").bufferedReader().use { it.readText() }
            RecognitionRules.fromJson(JSONObject(json), rawJson = json)
        } catch (e: Exception) {
            Log.e(TAG, "加载内置规则失败", e)
            RecognitionRules()
        }
    }

    suspend fun loadLocal(): RecognitionRules? = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "rules.json")
            if (!file.exists()) return@withContext null
            val json = file.readText()
            RecognitionRules.fromJson(JSONObject(json), rawJson = json)
        } catch (e: Exception) {
            Log.e(TAG, "加载本地自定义规则失败", e)
            null
        }
    }

    suspend fun saveLocal(rules: RecognitionRules) = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "rules.json")
            file.writeText(rules.rawJson ?: rules.toJson().toString(2))
            Log.d(TAG, "保存本地自定义规则成功")
        } catch (e: Exception) {
            Log.e(TAG, "保存本地自定义规则失败", e)
        }
    }

    suspend fun deleteLocal() = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "rules.json")
            if (file.exists()) file.delete()
            Log.d(TAG, "删除本地自定义规则成功")
        } catch (e: Exception) {
            Log.e(TAG, "删除本地自定义规则失败", e)
        }
    }

    suspend fun loadOnlineCache(): RecognitionRules? = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "online_cache.json")
            if (!file.exists()) return@withContext null
            val json = file.readText()
            RecognitionRules.fromJson(JSONObject(json), rawJson = json)
        } catch (e: Exception) {
            Log.e(TAG, "加载在线缓存规则失败", e)
            null
        }
    }

    suspend fun saveOnlineCache(rules: RecognitionRules, etag: String?, lastModified: String?) = withContext(Dispatchers.IO) {
        try {
            val rulesFile = File(rulesDir, "online_cache.json")
            rulesFile.writeText(rules.rawJson ?: rules.toJson().toString(2))

            val metaFile = File(rulesDir, "online_cache_meta.json")
            val meta = JSONObject().apply {
                put("etag", etag ?: "")
                put("last_modified", lastModified ?: "")
                put("fetch_time", System.currentTimeMillis())
            }
            metaFile.writeText(meta.toString(2))
            Log.d(TAG, "保存在线缓存规则成功")
        } catch (e: Exception) {
            Log.e(TAG, "保存在线缓存规则失败", e)
        }
    }

    suspend fun loadOnlineCacheMeta(): OnlineCacheMeta? = withContext(Dispatchers.IO) {
        try {
            val metaFile = File(rulesDir, "online_cache_meta.json")
            if (!metaFile.exists()) return@withContext null
            val json = JSONObject(metaFile.readText())
            OnlineCacheMeta(
                etag = json.optString("etag", "").ifBlank { null },
                lastModified = json.optString("last_modified", "").ifBlank { null },
                fetchTime = json.optLong("fetch_time", 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun exportToJson(rules: RecognitionRules): String {
        return rules.rawJson ?: rules.toJson().toString(2)
    }

    fun importFromJson(json: String): Result<RecognitionRules> {
        return try {
            val rules = RecognitionRules.fromJson(JSONObject(json), rawJson = json)
            val validation = RuleValidator.validate(rules)
            if (!validation.isValid) {
                Result.failure(Exception("规则验证失败: ${validation.errors.joinToString("; ")}"))
            } else {
                Result.success(rules)
            }
        } catch (e: Exception) {
            Result.failure(Exception("JSON 解析失败: ${e.message}"))
        }
    }

    // ─────────── Online Rule Sources ───────────

    suspend fun loadOnlineSources(): List<OnlineRuleSource> = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "online_sources.json")
            if (!file.exists()) return@withContext emptyList()
            val json = org.json.JSONArray(file.readText())
            (0 until json.length()).map { OnlineRuleSource.fromJson(json.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "加载在线规则源失败", e)
            emptyList()
        }
    }

    suspend fun saveOnlineSources(sources: List<OnlineRuleSource>) = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "online_sources.json")
            val arr = org.json.JSONArray()
            sources.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString(2))

            // 同步更新 config.json 中的 online_sources
            val config = loadSystemConfig()
            val newConfig = config.copy(onlineSources = sources)
            saveSystemConfig(newConfig)

            Log.d(TAG, "保存在线规则源成功")
        } catch (e: Exception) {
            Log.e(TAG, "保存在线规则源失败", e)
        }
    }

    // ─────────── System Config Management ───────────

    suspend fun loadSystemConfig(): RuleSystemConfig = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "config.json")
            if (!file.exists()) {
                val defaultConfig = RuleSystemConfig()
                file.writeText(defaultConfig.toJson().toString(2))
                return@withContext defaultConfig
            }
            val json = JSONObject(file.readText())
            RuleSystemConfig.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "加载系统配置失败", e)
            RuleSystemConfig()
        }
    }

    suspend fun saveSystemConfig(config: RuleSystemConfig) = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "config.json")
            file.writeText(config.toJson().toString(2))
            Log.d(TAG, "保存系统配置成功")
        } catch (e: Exception) {
            Log.e(TAG, "保存系统配置失败", e)
        }
    }

    // ─────────── Local Rule Management ───────────

    suspend fun loadLocalCustomRules(): RecognitionRules? = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "local_custom_rules.json")
            if (!file.exists()) return@withContext null
            val json = file.readText()
            RecognitionRules.fromJson(JSONObject(json), rawJson = json)
        } catch (e: Exception) {
            Log.e(TAG, "加载本地自定义规则失败", e)
            null
        }
    }

    suspend fun saveLocalCustomRules(rules: RecognitionRules) = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "local_custom_rules.json")
            file.writeText(rules.rawJson ?: rules.toJson().toString(2))
            Log.d(TAG, "保存本地自定义规则成功")
        } catch (e: Exception) {
            Log.e(TAG, "保存本地自定义规则失败", e)
        }
    }

    suspend fun deleteLocalCustomRules() = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "local_custom_rules.json")
            if (file.exists()) file.delete()
            Log.d(TAG, "删除本地自定义规则成功")
        } catch (e: Exception) {
            Log.e(TAG, "删除本地自定义规则失败", e)
        }
    }

    suspend fun hasLocalCustomRules(): Boolean = withContext(Dispatchers.IO) {
        File(rulesDir, "local_custom_rules.json").exists()
    }

    // ─────────── Multiple Local Custom Sources ───────────

    private val localCustomDir get() = File(rulesDir, "local_custom")

    suspend fun saveLocalCustomRulesById(id: String, rules: RecognitionRules) = withContext(Dispatchers.IO) {
        try {
            localCustomDir.mkdirs()
            val file = File(localCustomDir, "$id.json")
            file.writeText(rules.rawJson ?: rules.toJson().toString(2))
            Log.d(TAG, "保存本地自定义规则成功 [$id]")
        } catch (e: Exception) {
            Log.e(TAG, "保存本地自定义规则失败 [$id]", e)
        }
    }

    suspend fun loadLocalCustomRulesById(id: String): RecognitionRules? = withContext(Dispatchers.IO) {
        try {
            val file = File(localCustomDir, "$id.json")
            if (!file.exists()) return@withContext null
            val json = file.readText()
            RecognitionRules.fromJson(JSONObject(json), rawJson = json)
        } catch (e: Exception) {
            Log.e(TAG, "加载本地自定义规则失败 [$id]", e)
            null
        }
    }

    suspend fun deleteLocalCustomRulesById(id: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(localCustomDir, "$id.json")
            if (file.exists()) file.delete()
            Log.d(TAG, "删除本地自定义规则成功 [$id]")
        } catch (e: Exception) {
            Log.e(TAG, "删除本地自定义规则失败 [$id]", e)
        }
    }

    // ─────────── Rule Loading (Single Select Mode) ───────────

    suspend fun loadBuiltInRules(): RecognitionRules = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("default_rules.json").bufferedReader().use { it.readText() }
            val rules = RecognitionRules.fromJson(JSONObject(json), rawJson = json)
            Log.d(TAG, "loadBuiltInRules: patterns=${rules.codeExtraction.express.patterns.size}")
            rules
        } catch (e: Exception) {
            Log.e(TAG, "加载内置规则失败", e)
            RecognitionRules()
        }
    }

    suspend fun loadLocalRules(): RecognitionRules = withContext(Dispatchers.IO) {
        val builtIn = loadBuiltInRules()
        val custom = loadLocalCustomRules()
        Log.d(TAG, "loadLocalRules: builtIn patterns=${builtIn.codeExtraction.express.patterns.size}, custom=${custom != null}, custom patterns=${custom?.codeExtraction?.express?.patterns?.size}")
        custom ?: builtIn
    }

    suspend fun loadActiveRules(sourceId: String): Result<RecognitionRules> = withContext(Dispatchers.IO) {
        try {
            val builtIn = loadBuiltInRules()
            Log.d(TAG, "loadActiveRules: sourceId=$sourceId, builtIn patterns=${builtIn.codeExtraction.express.patterns.size}")
            val rules = when (sourceId) {
                "builtin" -> builtIn
                "local" -> loadLocalRules()
                else -> {
                    if (sourceId.startsWith("local_custom_")) {
                        val id = sourceId.removePrefix("local_custom_")
                        loadLocalCustomRulesById(id) ?: builtIn
                    } else {
                        Log.d(TAG, "loadActiveRules: 加载在线源 $sourceId")
                        loadOnlineRulesById(sourceId) ?: builtIn
                    }
                }
            }
            Log.d(TAG, "loadActiveRules: source=$sourceId, loaded patterns=${rules.codeExtraction.express.patterns.size}")
            // 非内置源：与内置规则合并，缺失部分自动补全
            val merged = if (sourceId != "builtin") {
                Log.d(TAG, "loadActiveRules: 执行合并 sourceId=$sourceId")
                rules.mergeWithBuiltIn(builtIn)
            } else {
                rules
            }
            Log.d(TAG, "loadActiveRules: 最终 patterns=${merged.codeExtraction.express.patterns.size}")
            Result.success(merged)
        } catch (e: Exception) {
            Log.e(TAG, "加载激活规则源失败: $sourceId", e)
            Result.failure(e)
        }
    }

    suspend fun loadOnlineRulesById(sourceId: String): RecognitionRules? = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "online_sources/$sourceId.json")
            Log.d(TAG, "加载在线规则文件: ${file.absolutePath}, 存在: ${file.exists()}")
            if (!file.exists()) return@withContext null
            val json = file.readText()
            Log.d(TAG, "在线规则文件内容大小: ${json.length} 字符 [$sourceId]")
            // 打印前200字符帮助调试
            Log.d(TAG, "在线规则文件开头: ${json.take(200)}")
            val rules = RecognitionRules.fromJson(JSONObject(json), rawJson = json)
            Log.d(TAG, "在线规则解析结果: express patterns=${rules.codeExtraction.express.patterns.size}, brands=${rules.brands.drink.size + rules.brands.food.size + rules.brands.express.size}")
            rules
        } catch (e: Exception) {
            Log.e(TAG, "加载在线规则缓存失败 [$sourceId]", e)
            null
        }
    }

    suspend fun saveOnlineRulesById(sourceId: String, rules: RecognitionRules, etag: String?, lastModified: String?) = withContext(Dispatchers.IO) {
        try {
            val onlineDir = File(rulesDir, "online_sources")
            onlineDir.mkdirs()

            val rulesFile = File(onlineDir, "$sourceId.json")
            val jsonStr = rules.rawJson ?: rules.toJson().toString(2)
            rulesFile.writeText(jsonStr)
            Log.d(TAG, "保存在线规则文件: ${rulesFile.absolutePath}, 大小: ${jsonStr.length} 字符, 存在: ${rulesFile.exists()}")

            val metaFile = File(onlineDir, "${sourceId}_meta.json")
            val meta = JSONObject().apply {
                put("etag", etag ?: "")
                put("last_modified", lastModified ?: "")
                put("fetch_time", System.currentTimeMillis())
            }
            metaFile.writeText(meta.toString(2))
            Log.d(TAG, "保存在线规则缓存成功 [$sourceId]")
        } catch (e: Exception) {
            Log.e(TAG, "保存在线规则缓存失败 [$sourceId]", e)
        }
    }

    /** 直接保存原始 JSON 文本，不做任何处理 */
    suspend fun saveRawOnlineRulesById(sourceId: String, rawJson: String, etag: String?, lastModified: String?) = withContext(Dispatchers.IO) {
        try {
            val onlineDir = File(rulesDir, "online_sources")
            onlineDir.mkdirs()

            val rulesFile = File(onlineDir, "$sourceId.json")
            rulesFile.writeText(rawJson)
            Log.d(TAG, "保存原始在线规则文件: ${rulesFile.absolutePath}, 大小: ${rawJson.length} 字符")

            val metaFile = File(onlineDir, "${sourceId}_meta.json")
            val meta = JSONObject().apply {
                put("etag", etag ?: "")
                put("last_modified", lastModified ?: "")
                put("fetch_time", System.currentTimeMillis())
            }
            metaFile.writeText(meta.toString(2))
            Log.d(TAG, "保存在线规则缓存成功 [$sourceId]")
        } catch (e: Exception) {
            Log.e(TAG, "保存在线规则缓存失败 [$sourceId]", e)
        }
    }

    /** 直接保存原始 JSON 到全局缓存 */
    suspend fun saveRawOnlineCache(rawJson: String, etag: String?, lastModified: String?) = withContext(Dispatchers.IO) {
        try {
            val rulesFile = File(rulesDir, "online_cache.json")
            rulesFile.writeText(rawJson)

            val metaFile = File(rulesDir, "online_cache_meta.json")
            val meta = JSONObject().apply {
                put("etag", etag ?: "")
                put("last_modified", lastModified ?: "")
                put("fetch_time", System.currentTimeMillis())
            }
            metaFile.writeText(meta.toString(2))
            Log.d(TAG, "保存原始在线缓存规则成功")
        } catch (e: Exception) {
            Log.e(TAG, "保存在线缓存规则失败", e)
        }
    }

    data class OnlineCacheMeta(
        val etag: String?,
        val lastModified: String?,
        val fetchTime: Long
    )

    companion object {
        private const val TAG = "RuleRepository"
    }
}
