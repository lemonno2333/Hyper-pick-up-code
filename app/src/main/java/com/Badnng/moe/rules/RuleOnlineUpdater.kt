package com.Badnng.moe.rules

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RuleOnlineUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val repository = RuleRepository(context)

    data class FetchResult(
        val rules: RecognitionRules?,
        val etag: String?,
        val lastModified: String?,
        val notModified: Boolean
    )

    suspend fun fetch(url: String): Result<FetchResult> = withContext(Dispatchers.IO) {
        try {
            val meta = repository.loadOnlineCacheMeta()
            val builder = Request.Builder().url(url)
            meta?.etag?.let { builder.header("If-None-Match", it) }
            meta?.lastModified?.let { builder.header("If-Modified-Since", it) }

            val response = client.newCall(builder.build()).execute()

            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: throw Exception("响应体为空")
                    val validationResult = RuleValidator.validateJson(body)
                    if (!validationResult.isValid) {
                        throw Exception("规则验证失败: ${validationResult.errors.joinToString("; ")}")
                    }
                    val rules = RecognitionRules.fromJson(org.json.JSONObject(body))
                    val etag = response.header("ETag")
                    val lastModified = response.header("Last-Modified")
                    Result.success(FetchResult(rules, etag, lastModified, false))
                }
                304 -> {
                    Log.d(TAG, "规则未修改 (304)")
                    Result.success(FetchResult(null, null, null, true))
                }
                else -> {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取在线规则失败: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchAndSave(url: String): Result<Boolean> {
        val result = fetch(url)
        return result.fold(
            onSuccess = { fetchResult ->
                if (fetchResult.notModified) {
                    Result.success(false)
                } else {
                    fetchResult.rules?.let { rules ->
                        repository.saveOnlineCache(rules, fetchResult.etag, fetchResult.lastModified)
                        RecognitionRuleEngine.saveOnlineCache(rules, fetchResult.etag, fetchResult.lastModified)
                        Result.success(true)
                    } ?: Result.success(false)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun fetchAndSaveSource(source: OnlineRuleSource): Result<Pair<Boolean, OnlineRuleSource>> {
        val result = fetch(source.url)
        return result.fold(
            onSuccess = { fetchResult ->
                if (fetchResult.notModified) {
                    val updated = source.copy(lastUpdated = System.currentTimeMillis())
                    Result.success(false to updated)
                } else {
                    fetchResult.rules?.let { rules ->
                        repository.saveOnlineCache(rules, fetchResult.etag, fetchResult.lastModified)
                        RecognitionRuleEngine.saveOnlineCache(rules, fetchResult.etag, fetchResult.lastModified)
                        val updated = source.copy(
                            lastUpdated = System.currentTimeMillis(),
                            lastEtag = fetchResult.etag,
                            lastModified = fetchResult.lastModified
                        )
                        Result.success(true to updated)
                    } ?: Result.success(false to source)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    companion object {
        private const val TAG = "RuleOnlineUpdater"
    }
}
