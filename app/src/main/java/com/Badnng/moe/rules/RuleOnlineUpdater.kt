package com.Badnng.moe.rules

import android.content.Context
import android.util.Log
import com.Badnng.moe.helper.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import java.security.cert.X509Certificate

class RuleOnlineUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1)) // 强制 HTTP/1.1，避免 HTTP/2 兼容性问题
        .eventListener(object : okhttp3.EventListener() {
            override fun connectStart(call: okhttp3.Call, inetAddr: java.net.InetSocketAddress, proxy: java.net.Proxy) {
                Log.d(TAG, "[事件] 连接开始 -> ${inetAddr.address?.hostAddress ?: "未知IP"}:${inetAddr.port} (代理: ${proxy.type()})")
                Log.d(TAG, "[事件] 主机名: ${inetAddr.hostName}")
            }
            override fun connectEnd(call: okhttp3.Call, inetAddr: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: okhttp3.Protocol?) {
                Log.d(TAG, "[事件] 连接成功 <- 协议: $protocol, 地址: ${inetAddr.address?.hostAddress}:${inetAddr.port}")
            }
            override fun connectFailed(call: okhttp3.Call, inetAddr: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: okhttp3.Protocol?, e: java.io.IOException) {
                Log.e(TAG, "[事件] 连接失败 <- ${e.javaClass.simpleName}: ${e.message}")
                Log.e(TAG, "[事件] 目标: ${inetAddr.address?.hostAddress}:${inetAddr.port}, 代理: ${proxy.type()}")
            }
            override fun secureConnectStart(call: okhttp3.Call) {
                Log.d(TAG, "[事件] TLS 握手开始...")
            }
            override fun secureConnectEnd(call: okhttp3.Call, handshake: okhttp3.Handshake?) {
                if (handshake != null) {
                    Log.d(TAG, "[事件] TLS 握手成功: 版本=${handshake.tlsVersion}, 密码套件=${handshake.cipherSuite}")
                    handshake.peerCertificates.forEachIndexed { i, cert ->
                        if (cert is X509Certificate) {
                            Log.d(TAG, "[事件] 证书[$i]: ${cert.subjectDN}, 签发者=${cert.issuerDN}, 有效期=${cert.notBefore}~${cert.notAfter}")
                        }
                    }
                } else {
                    Log.w(TAG, "[事件] TLS 握手完成但无握手信息 (可能非 TLS 连接)")
                }
            }
            override fun callStart(call: okhttp3.Call) {
                Log.d(TAG, "[事件] 请求开始: ${call.request().method} ${call.request().url}")
            }
            override fun callEnd(call: okhttp3.Call) {
                Log.d(TAG, "[事件] 请求结束")
            }
            override fun callFailed(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "[事件] 请求失败: ${e.javaClass.simpleName}: ${e.message}")
            }
        })
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val t1 = System.nanoTime()
            Log.d(TAG, "网络拦截器 -> 发送请求: ${request.method} ${request.url}")
            Log.d(TAG, "  协议: ${chain.connection()?.protocol() ?: "未知"}")
            Log.d(TAG, "  TLS 版本: ${chain.connection()?.handshake()?.tlsVersion ?: "未知"}")
            Log.d(TAG, "  密码套件: ${chain.connection()?.handshake()?.cipherSuite ?: "未知"}")
            val response = chain.proceed(request)
            val t2 = System.nanoTime()
            Log.d(TAG, "网络拦截器 <- 收到响应: ${response.code} (${(t2 - t1) / 1_000_000}ms)")
            Log.d(TAG, "  协议: ${response.protocol}")
            Log.d(TAG, "  TLS: ${response.handshake?.tlsVersion ?: "无"}")
            response
        }
        .build()

    private val repository = RuleRepository(context)

    data class FetchResult(
        val rules: RecognitionRules?,
        val rawJson: String? = null,
        val etag: String?,
        val lastModified: String?,
        val notModified: Boolean
    )

    suspend fun fetch(url: String, forceNoCache: Boolean = false): Result<FetchResult> = withContext(Dispatchers.IO) {
        try {
            AppLogger.update("Fetch start: url=$url, forceNoCache=$forceNoCache")
            Log.d(TAG, "========== 开始请求在线规则 ==========")
            Log.d(TAG, "URL: $url, forceNoCache=$forceNoCache")
            // 系统 TLS 信息
            val defaultSslContext = SSLContext.getDefault()
            Log.d(TAG, "系统默认 SSL 协议: ${defaultSslContext.protocol}")
            // 列出系统支持的 TLS 协议版本
            val defaultSslSocketFactory = defaultSslContext.socketFactory
            val defaultSocket = defaultSslSocketFactory.createSocket()
            if (defaultSocket is javax.net.ssl.SSLSocket) {
                val supportedProtocols = defaultSocket.supportedProtocols
                val enabledProtocols = defaultSocket.enabledProtocols
                Log.d(TAG, "系统支持的 TLS 协议: ${supportedProtocols.joinToString()}")
                Log.d(TAG, "系统启用的 TLS 协议: ${enabledProtocols.joinToString()}")
                val supportedCipherSuites = defaultSocket.supportedCipherSuites
                val enabledCipherSuites = defaultSocket.enabledCipherSuites
                Log.d(TAG, "系统支持的密码套件数: ${supportedCipherSuites.size}, 启用数: ${enabledCipherSuites.size}")
                Log.d(TAG, "启用的密码套件: ${enabledCipherSuites.joinToString()}")
                defaultSocket.close()
            }
            // DNS 解析测试
            try {
                val host = java.net.URI(url).host
                val addresses = java.net.InetAddress.getAllByName(host)
                addresses.forEach { addr ->
                    Log.d(TAG, "DNS 解析: $host -> ${addr.hostAddress} (${addr.hostName})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "DNS 解析测试失败: ${e.message}")
            }

            val meta = repository.loadOnlineCacheMeta()
            Log.d(TAG, "缓存状态: etag=${meta?.etag ?: "无"}, lastModified=${meta?.lastModified ?: "无"}, fetchTime=${meta?.fetchTime ?: 0}")

            val builder = Request.Builder().url(url)
                .header("User-Agent", "HyperNote/1.0 (Android)")
            if (!forceNoCache) {
                meta?.etag?.let { builder.header("If-None-Match", it) }
                meta?.lastModified?.let { builder.header("If-Modified-Since", it) }
            } else {
                Log.d(TAG, "无条件获取：跳过缓存头")
            }

            val request = builder.build()
            Log.d(TAG, "请求头: ${request.headers}")

            val response = client.newCall(request).execute()
            Log.d(TAG, "响应状态: ${response.code} ${response.message}")
            Log.d(TAG, "响应头: ${response.headers}")

            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: throw Exception("响应体为空")
                    Log.d(TAG, "响应体大小: ${body.length} 字符")
                    AppLogger.update("Fetch 200: body=${body.length} chars")
                    val validationResult = RuleValidator.validateJson(body)
                    if (!validationResult.isValid) {
                        Log.w(TAG, "规则验证失败: ${validationResult.errors}")
                        AppLogger.update("Fetch validation failed: ${validationResult.errors}")
                        throw Exception("规则验证失败: ${validationResult.errors.joinToString("; ")}")
                    }
                    val rules = RecognitionRules.fromJson(org.json.JSONObject(body), rawJson = body)
                    val etag = response.header("ETag")
                    val lastModified = response.header("Last-Modified")
                    Log.d(TAG, "规则解析成功: schemaVersion=${rules.schemaVersion}, etag=$etag, lastModified=$lastModified")
                    AppLogger.update("Fetch parsed: schema=${rules.schemaVersion}, etag=$etag")
                    Result.success(FetchResult(rules, rawJson = body, etag = etag, lastModified = lastModified, notModified = false))
                }
                304 -> {
                    Log.d(TAG, "规则未修改 (304)")
                    AppLogger.update("Fetch 304: not modified")
                    Result.success(FetchResult(rules = null, rawJson = null, etag = null, lastModified = null, notModified = true))
                }
                else -> {
                    val errorBody = try { response.body?.string()?.take(500) } catch (_: Exception) { null }
                    Log.w(TAG, "HTTP 错误: ${response.code} ${response.message}, 响应体: $errorBody")
                    AppLogger.update("Fetch HTTP ${response.code}: ${response.message}")
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "连接失败 [${e.javaClass.simpleName}]: ${e.message}")
            Log.e(TAG, "URL: $url")
            Log.e(TAG, "可能原因: 服务器拒绝连接、DNS 解析失败、防火墙拦截")
            AppLogger.update("Fetch ConnectException: ${e.message}")
            Result.failure(e)
        } catch (e: javax.net.ssl.SSLException) {
            Log.e(TAG, "SSL/TLS 错误 [${e.javaClass.simpleName}]: ${e.message}")
            Log.e(TAG, "URL: $url")
            Log.e(TAG, "可能原因: 证书不受信任、TLS 版本不兼容、中间人攻击")
            AppLogger.update("Fetch SSLException: ${e.message}")
            Result.failure(e)
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "请求超时 [${e.javaClass.simpleName}]: ${e.message}")
            Log.e(TAG, "URL: $url")
            Log.e(TAG, "connectTimeout=${client.connectTimeoutMillis}ms, readTimeout=${client.readTimeoutMillis}ms")
            AppLogger.update("Fetch SocketTimeout: ${e.message}")
            Result.failure(e)
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "DNS 解析失败 [${e.javaClass.simpleName}]: ${e.message}")
            Log.e(TAG, "URL: $url")
            Log.e(TAG, "可能原因: 无网络连接、DNS 服务器不可用、域名不存在")
            AppLogger.update("Fetch UnknownHost: ${e.message}")
            Result.failure(e)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "网络 IO 错误 [${e.javaClass.simpleName}]: ${e.message}")
            Log.e(TAG, "URL: $url")
            AppLogger.update("Fetch IOException: ${e.message}")
            // 打印完整异常链
            var cause: Throwable? = e.cause
            var depth = 0
            while (cause != null && depth < 5) {
                Log.e(TAG, "  原因链[$depth]: [${cause.javaClass.simpleName}] ${cause.message}")
                cause = cause.cause
                depth++
            }
            if (e.message?.contains("reset", ignoreCase = true) == true) {
                Log.e(TAG, "Connection reset 详细排查:")
                Log.e(TAG, "  - 服务器可能不支持 HTTP/2 或强制使用了特定 TLS 版本")
                Log.e(TAG, "  - 尝试在浏览器中确认 URL 是否需要特殊请求头")
                Log.e(TAG, "  - 可能是 Android Network Security Config 限制")
                Log.e(TAG, "  - 请检查是否添加了 network_security_config.xml 信任用户证书")
            }
            Log.e(TAG, "异常堆栈:", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "获取在线规则失败 [${e.javaClass.simpleName}]: ${e.message}")
            Log.e(TAG, "URL: $url")
            Log.e(TAG, "异常堆栈:", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAndSave(url: String): Result<Boolean> {
        AppLogger.update("fetchAndSave start: $url")
        val result = fetch(url)
        return result.fold(
            onSuccess = { fetchResult ->
                if (fetchResult.notModified) {
                    AppLogger.update("fetchAndSave: not modified")
                    Result.success(false)
                } else {
                    fetchResult.rules?.let { rules ->
                        repository.saveOnlineCache(rules, fetchResult.etag, fetchResult.lastModified)
                        RecognitionRuleEngine.saveOnlineCache(rules, fetchResult.etag, fetchResult.lastModified)
                        AppLogger.update("fetchAndSave: saved successfully")
                        Result.success(true)
                    } ?: Result.success(false)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun fetchAndSaveSource(source: OnlineRuleSource): Result<Pair<Boolean, OnlineRuleSource>> {
        AppLogger.update("fetchAndSaveSource start: id=${source.id}, name=${source.name}, url=${source.url}")
        // 如果按源文件不存在，做无条件获取（不发缓存头）
        val perSourceFile = java.io.File(
            context.filesDir, "rules/online_sources/${source.id}.json"
        )
        val forceFetch = !perSourceFile.exists()
        val result = fetch(source.url, forceNoCache = forceFetch)
        return result.fold(
            onSuccess = { fetchResult ->
                if (fetchResult.notModified) {
                    // 304: 规则没变
                    AppLogger.update("fetchAndSaveSource: ${source.id} not modified")
                    val updated = source.copy(lastUpdated = System.currentTimeMillis())
                    Result.success(false to updated)
                } else {
                    fetchResult.rules?.let { rules ->
                        // 直接保存原始 JSON 文本，不做任何处理
                        repository.saveRawOnlineRulesById(source.id, fetchResult.rawJson!!, fetchResult.etag, fetchResult.lastModified)
                        // 同时更新全局缓存
                        repository.saveRawOnlineCache(fetchResult.rawJson!!, fetchResult.etag, fetchResult.lastModified)
                        RecognitionRuleEngine.saveOnlineCache(rules, fetchResult.etag, fetchResult.lastModified)
                        val updated = source.copy(
                            lastUpdated = System.currentTimeMillis(),
                            lastEtag = fetchResult.etag,
                            lastModified = fetchResult.lastModified
                        )
                        AppLogger.update("fetchAndSaveSource: ${source.id} saved successfully")
                        Result.success(true to updated)
                    } ?: Result.success(false to source)
                }
            },
            onFailure = { e ->
                AppLogger.update("fetchAndSaveSource failed: ${source.id} - ${e.message}")
                Result.failure(e)
            }
        )
    }

    companion object {
        private const val TAG = "RuleOnlineUpdater"
    }
}
