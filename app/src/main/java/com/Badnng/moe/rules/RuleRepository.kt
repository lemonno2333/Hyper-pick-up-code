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
            RecognitionRules.fromJson(JSONObject(json))
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
            RecognitionRules.fromJson(JSONObject(json))
        } catch (e: Exception) {
            Log.e(TAG, "加载本地自定义规则失败", e)
            null
        }
    }

    suspend fun saveLocal(rules: RecognitionRules) = withContext(Dispatchers.IO) {
        try {
            val file = File(rulesDir, "rules.json")
            file.writeText(rules.toJson().toString(2))
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
            RecognitionRules.fromJson(JSONObject(json))
        } catch (e: Exception) {
            Log.e(TAG, "加载在线缓存规则失败", e)
            null
        }
    }

    suspend fun saveOnlineCache(rules: RecognitionRules, etag: String?, lastModified: String?) = withContext(Dispatchers.IO) {
        try {
            val rulesFile = File(rulesDir, "online_cache.json")
            rulesFile.writeText(rules.toJson().toString(2))

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
        return rules.toJson().toString(2)
    }

    fun importFromJson(json: String): Result<RecognitionRules> {
        return try {
            val rules = RecognitionRules.fromJson(JSONObject(json))
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
            Log.d(TAG, "保存在线规则源成功")
        } catch (e: Exception) {
            Log.e(TAG, "保存在线规则源失败", e)
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
