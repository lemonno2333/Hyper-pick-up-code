package com.Badnng.moe.rules

import android.util.Log

object RuleValidator {

    const val CURRENT_SCHEMA_VERSION = 1

    data class ValidationResult(val errors: List<String>, val warnings: List<String>) {
        val isValid get() = errors.isEmpty()
    }

    fun validate(rules: RecognitionRules): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (rules.schemaVersion > CURRENT_SCHEMA_VERSION) {
            warnings.add("Schema 版本 ${rules.schemaVersion} 高于当前支持的 $CURRENT_SCHEMA_VERSION，部分字段可能无法识别")
        }

        validateBrands(rules, errors)
        validateExpressPatterns(rules, errors)
        validateFoodPatterns(rules, errors)
        validateThresholds(rules, warnings)

        return ValidationResult(errors, warnings)
    }

    fun validateJson(json: String): ValidationResult {
        return try {
            val rules = RecognitionRules.fromJson(org.json.JSONObject(json))
            validate(rules)
        } catch (e: Exception) {
            ValidationResult(listOf("JSON 解析失败: ${e.message}"), emptyList())
        }
    }

    private fun validateBrands(rules: RecognitionRules, errors: MutableList<String>) {
        val allBrands = rules.brands.drink + rules.brands.food + rules.brands.express
        allBrands.forEach { brand ->
            if (brand.name.isBlank()) {
                errors.add("品牌名称不能为空")
            }
            if (brand.packageName != null && !brand.packageName.contains(".")) {
                errors.add("品牌 '${brand.name}' 的包名格式无效: ${brand.packageName}")
            }
        }

        val names = allBrands.map { it.name }
        val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            errors.add("品牌名称重复: ${duplicates.joinToString(", ")}")
        }
    }

    private fun validateExpressPatterns(rules: RecognitionRules, errors: MutableList<String>) {
        rules.codeExtraction.express.patterns.forEach { pattern ->
            validateRegex(pattern.id, pattern.regex, errors)
        }
        rules.codeExtraction.express.fallbackPattern?.let {
            validateRegex("express_fallback", it.regex, errors)
        }
    }

    private fun validateFoodPatterns(rules: RecognitionRules, errors: MutableList<String>) {
        val foodPatterns = rules.codeExtraction.food.patterns
        foodPatterns.queuePatterns.forEach { pattern ->
            validateRegex(pattern.id, pattern.regex, errors)
        }
        foodPatterns.sloganPattern?.let {
            validateRegex(it.id, it.regex, errors)
        }
        foodPatterns.keywordPattern?.let {
            if (it.forwardRegex.isNotBlank()) validateRegex("${it.id}_forward", it.forwardRegex, errors)
            if (it.reverseRegex.isNotBlank()) validateRegex("${it.id}_reverse", it.reverseRegex, errors)
        }
        foodPatterns.fallbackPattern?.let {
            validateRegex(it.id, it.regex, errors)
        }
        rules.codeExtraction.food.starbucksPattern?.let {
            validateRegex("starbucks", it.regex, errors)
        }
    }

    private fun validateRegex(id: String, regex: String, errors: MutableList<String>) {
        try {
            Regex(regex)
        } catch (e: Exception) {
            errors.add("正则表达式 '$id' 编译失败: ${e.message}")
        }
    }

    private fun validateThresholds(rules: RecognitionRules, warnings: MutableList<String>) {
        val threshold = rules.homepageDetection.threshold
        if (threshold < 1 || threshold > 10) {
            warnings.add("首页检测阈值 $threshold 不在合理范围 (1-10)")
        }
        val queueThreshold = rules.codeExtraction.food.queueThreshold
        if (queueThreshold < 1 || queueThreshold > 5) {
            warnings.add("排队叫号阈值 $queueThreshold 不在合理范围 (1-5)")
        }
    }
}
