package com.Badnng.moe.rules

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RecognitionRuleEngine {

    private const val TAG = "RuleEngine"

    @Volatile
    private var _rules: RecognitionRules = RecognitionRules()
    val rules: RecognitionRules get() = _rules

    private val compiledPatterns = mutableMapOf<String, Regex>()
    private var repository: RuleRepository? = null

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        repository = RuleRepository(context)
        val builtIn = repository!!.loadBuiltIn()
        val local = repository!!.loadLocal()
        val online = repository!!.loadOnlineCache()
        _rules = RuleMerger.merge(builtIn, local, online)
        precompilePatterns()
        Log.d(TAG, "规则引擎初始化完成: ${_rules.description}")
    }

    suspend fun reload(context: Context) = withContext(Dispatchers.IO) {
        repository = RuleRepository(context)
        val builtIn = repository!!.loadBuiltIn()
        val local = repository!!.loadLocal()
        val online = repository!!.loadOnlineCache()
        _rules = RuleMerger.merge(builtIn, local, online)
        precompilePatterns()
        Log.d(TAG, "规则引擎重新加载完成")
    }

    private fun precompilePatterns() {
        compiledPatterns.clear()

        _rules.codeExtraction.express.patterns.filter { it.enabled }.forEach { pattern ->
            try {
                compiledPatterns[pattern.id] = Regex(pattern.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译正则失败 '${pattern.id}': ${e.message}")
            }
        }

        _rules.codeExtraction.express.fallbackPattern?.let {
            try {
                compiledPatterns["express_fallback"] = Regex(it.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译兜底正则失败: ${e.message}")
            }
        }

        _rules.codeExtraction.food.patterns.queuePatterns.filter { it.enabled }.forEach { pattern ->
            try {
                compiledPatterns[pattern.id] = Regex(pattern.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译排队正则失败 '${pattern.id}': ${e.message}")
            }
        }

        _rules.codeExtraction.food.patterns.sloganPattern?.let {
            try {
                compiledPatterns[it.id] = Regex(it.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译口令正则失败: ${e.message}")
            }
        }

        _rules.codeExtraction.food.patterns.keywordPattern?.let { kp ->
            if (kp.forwardRegex.isNotBlank()) {
                try {
                    compiledPatterns["${kp.id}_forward"] = Regex(kp.forwardRegex)
                } catch (e: Exception) {
                    Log.e(TAG, "编译关键词正向正则失败: ${e.message}")
                }
            }
            if (kp.reverseRegex.isNotBlank()) {
                try {
                    compiledPatterns["${kp.id}_reverse"] = Regex(kp.reverseRegex)
                } catch (e: Exception) {
                    Log.e(TAG, "编译关键词反向正则失败: ${e.message}")
                }
            }
        }

        _rules.codeExtraction.food.patterns.fallbackPattern?.let {
            try {
                compiledPatterns[it.id] = Regex(it.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译食物兜底正则失败: ${e.message}")
            }
        }

        _rules.codeExtraction.food.starbucksPattern?.let {
            try {
                compiledPatterns["starbucks"] = Regex(it.regex)
            } catch (e: Exception) {
                Log.e(TAG, "编译星巴克正则失败: ${e.message}")
            }
        }

        Log.d(TAG, "预编译完成，共 ${compiledPatterns.size} 个正则")
    }

    fun getCompiledPattern(id: String): Regex? = compiledPatterns[id]

    fun getAllDrinkNames(): List<String> = _rules.brands.drink.filter { it.enabled }.map { it.name }

    fun getAllFoodNames(): List<String> = _rules.brands.food.filter { it.enabled }.map { it.name }

    fun getAllExpressKeywords(): List<String> = _rules.brands.express.filter { it.enabled }
        .flatMap { listOf(it.name) + it.aliases }

    fun getDrinkBrands(): List<BrandDefinition> = _rules.brands.drink.filter { it.enabled }

    fun getFoodBrands(): List<BrandDefinition> = _rules.brands.food.filter { it.enabled }

    fun getExpressBrands(): List<BrandDefinition> = _rules.brands.express.filter { it.enabled }

    fun getAllBrands(): List<BrandDefinition> = getDrinkBrands() + getFoodBrands() + getExpressBrands()

    fun getBrandByPackage(packageName: String): String? {
        return getAllBrands().firstOrNull { it.packageName == packageName }?.name
    }

    fun getBrandByName(name: String): BrandDefinition? {
        return getAllBrands().firstOrNull { it.name == name || it.aliases.contains(name) }
    }

    fun getTextCorrections(): List<Pair<String, String>> {
        return _rules.textCleaning.corrections.map { it.from to it.to }
    }

    fun getHomepageKeywords(): List<String> = _rules.homepageDetection.keywords

    fun getHomepageThreshold(): Int = _rules.homepageDetection.threshold

    fun getExpressTriggerKeywords(): List<String> = _rules.codeExtraction.express.triggerKeywords

    fun getFoodTriggerKeywords(): List<String> = _rules.codeExtraction.food.triggerKeywords

    fun getFoodHintKeywords(): List<String> = _rules.codeExtraction.food.hintKeywords

    fun getQueueKeywords(): List<String> = _rules.codeExtraction.food.queueKeywords

    fun getQueueThreshold(): Int = _rules.codeExtraction.food.queueThreshold

    fun getExpressPatterns(): List<ExtractionPattern> {
        return _rules.codeExtraction.express.patterns.filter { it.enabled }.sortedBy { it.priority }
    }

    fun getQueuePatterns(): List<QueuePattern> {
        return _rules.codeExtraction.food.patterns.queuePatterns.filter { it.enabled }
    }

    suspend fun saveLocalRules(rules: RecognitionRules) {
        repository?.saveLocal(rules)
        _rules = rules
        precompilePatterns()
    }

    suspend fun deleteLocalRules(context: Context) {
        repository?.deleteLocal()
        reload(context)
    }

    suspend fun saveOnlineCache(rules: RecognitionRules, etag: String?, lastModified: String?) {
        repository?.saveOnlineCache(rules, etag, lastModified)
    }

    fun exportToJson(rules: RecognitionRules): String {
        return repository?.exportToJson(rules) ?: rules.toJson().toString(2)
    }

    // ─────────── Online Rule Sources ───────────

    suspend fun loadOnlineSources(): List<OnlineRuleSource> {
        return repository?.loadOnlineSources() ?: emptyList()
    }

    suspend fun saveOnlineSources(sources: List<OnlineRuleSource>) {
        repository?.saveOnlineSources(sources)
    }

    fun importFromJson(json: String): Result<RecognitionRules> {
        return repository?.importFromJson(json) ?: run {
            try {
                val rules = RecognitionRules.fromJson(org.json.JSONObject(json))
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
    }
}
