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
        validateExpressPatterns(rules, errors, warnings)
        validateFoodPatterns(rules, errors)
        validateValidationConfig(rules, errors, warnings)
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

    private fun validateExpressPatterns(rules: RecognitionRules, errors: MutableList<String>, warnings: MutableList<String>) {
        val patterns = rules.codeExtraction.express.patterns
        patterns.forEach { pattern ->
            validateRegex(pattern.id, pattern.regex, errors)
        }
        rules.codeExtraction.express.fallbackPattern?.let {
            validateRegex("express_fallback", it.regex, errors)
        }

        // 检测重叠：用多个测试样本，检查低优先级 pattern 是否匹配高优先级 pattern 的子串
        val testSamples = listOf("取件码158-3-0685、140-3-0441", "A123-4-5678B", "取件码ZT-20001")
        val compiled = patterns.mapNotNull { p ->
            try { p to Regex(p.regex) } catch (e: Exception) { null }
        }
        for (i in compiled.indices) {
            for (j in i + 1 until compiled.size) {
                val (pA, rA) = compiled[i]
                val (pB, rB) = compiled[j]
                if (pA.priority <= pB.priority) continue
                for (sample in testSamples) {
                    val matchA = rA.find(sample) ?: continue
                    val codeA = matchA.groupValues.getOrElse(1) { matchA.value }
                    val subMatch = rB.find(codeA)
                    if (subMatch != null && subMatch.value != codeA) {
                        warnings.add("pattern '${pB.id}' (priority=${pB.priority}) 可能匹配 '${pA.id}' (priority=${pA.priority}) 的子串 '${subMatch.value}'，建议提高 '${pB.id}' 的 priority 或收紧正则")
                        break
                    }
                }
            }
        }

        // 检查 priority 重复
        val priorityGroups = patterns.groupBy { it.priority }
        priorityGroups.forEach { (pri, pats) ->
            if (pats.size > 1) {
                warnings.add("priority=$pri 有 ${pats.size} 个 pattern (${pats.joinToString { it.id }})，建议用不同优先级区分")
            }
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
        foodPatterns.hashCodePattern?.let {
            validateRegex(it.id, it.regex, errors)
        }
    }

    private fun validateValidationConfig(rules: RecognitionRules, errors: MutableList<String>, warnings: MutableList<String>) {
        val vc = rules.validation.expressCode
        if (vc.maxLength < 3) {
            warnings.add("express maxLength=${vc.maxLength} 过小，可能无法匹配有效取件码")
        }
        if (vc.maxReverseDistance < 10) {
            warnings.add("express maxReverseDistance=${vc.maxReverseDistance} 过小，反向匹配可能找不到 code")
        }
        if (vc.maxCodeKeywordGap < 5) {
            warnings.add("express maxCodeKeywordGap=${vc.maxCodeKeywordGap} 过小，code 和关键词之间距离受限")
        }
        if (vc.codeSeparators.isEmpty()) {
            errors.add("express codeSeparators 不能为空，多码分隔将失效")
        }
        vc.threeSegment?.let { ts ->
            validateRegex("three_segment", ts.pattern, errors)
            if (ts.lastSegmentLength < 1) {
                warnings.add("threeSegment lastSegmentLength=${ts.lastSegmentLength} 无效")
            }
            if (ts.lastSegmentPattern.isNotBlank()) {
                validateRegex("three_segment_last", ts.lastSegmentPattern, errors)
            }
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
