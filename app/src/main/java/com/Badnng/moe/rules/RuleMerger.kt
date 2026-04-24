package com.Badnng.moe.rules

import android.util.Log

object RuleMerger {

    private const val TAG = "RuleMerger"

    fun merge(
        builtIn: RecognitionRules,
        local: RecognitionRules?,
        online: RecognitionRules?
    ): RecognitionRules {
        var result = builtIn
        local?.let {
            Log.d(TAG, "合并本地自定义规则")
            result = mergeRules(result, it)
        }
        online?.let {
            Log.d(TAG, "合并在线规则")
            result = mergeRules(result, it)
        }
        return result
    }

    private fun mergeRules(base: RecognitionRules, override: RecognitionRules): RecognitionRules {
        return RecognitionRules(
            schemaVersion = maxOf(base.schemaVersion, override.schemaVersion),
            appVersion = override.appVersion.ifBlank { base.appVersion },
            updatedAt = override.updatedAt.ifBlank { base.updatedAt },
            description = override.description.ifBlank { base.description },
            brands = mergeBrands(base.brands, override.brands),
            codeExtraction = mergeCodeExtraction(base.codeExtraction, override.codeExtraction),
            categoryDetection = mergeCategoryDetection(base.categoryDetection, override.categoryDetection),
            homepageDetection = mergeHomepageDetection(base.homepageDetection, override.homepageDetection),
            textCleaning = mergeTextCleaning(base.textCleaning, override.textCleaning),
            validation = mergeValidation(base.validation, override.validation),
            scoring = mergeScoring(base.scoring, override.scoring),
            pickupLocation = mergePickupLocation(base.pickupLocation, override.pickupLocation)
        )
    }

    private fun mergeBrands(base: BrandConfig, override: BrandConfig): BrandConfig {
        return BrandConfig(
            drink = mergeBrandList(base.drink, override.drink),
            food = mergeBrandList(base.food, override.food),
            express = mergeBrandList(base.express, override.express)
        )
    }

    private fun mergeBrandList(base: List<BrandDefinition>, override: List<BrandDefinition>): List<BrandDefinition> {
        val result = base.toMutableList()
        override.forEach { overrideBrand ->
            val existingIdx = result.indexOfFirst { it.name == overrideBrand.name }
            if (existingIdx >= 0) {
                result[existingIdx] = overrideBrand
            } else {
                result.add(overrideBrand)
            }
        }
        return result
    }

    private fun mergeCodeExtraction(base: CodeExtractionConfig, override: CodeExtractionConfig): CodeExtractionConfig {
        return CodeExtractionConfig(
            express = mergeExpressExtraction(base.express, override.express),
            food = mergeFoodExtraction(base.food, override.food)
        )
    }

    private fun mergeExpressExtraction(base: ExpressExtraction, override: ExpressExtraction): ExpressExtraction {
        return ExpressExtraction(
            triggerKeywords = mergeListWithDedup(override.triggerKeywords, base.triggerKeywords),
            patterns = mergePatterns(base.patterns, override.patterns),
            fallbackPattern = override.fallbackPattern ?: base.fallbackPattern
        )
    }

    private fun mergeFoodExtraction(base: FoodExtraction, override: FoodExtraction): FoodExtraction {
        return FoodExtraction(
            triggerKeywords = mergeListWithDedup(override.triggerKeywords, base.triggerKeywords),
            hintKeywords = mergeListWithDedup(override.hintKeywords, base.hintKeywords),
            queueKeywords = mergeListWithDedup(override.queueKeywords, base.queueKeywords),
            queueThreshold = if (override.queueThreshold != 2) override.queueThreshold else base.queueThreshold,
            patterns = mergeFoodPatterns(base.patterns, override.patterns),
            starbucksPattern = override.starbucksPattern ?: base.starbucksPattern
        )
    }

    private fun mergeFoodPatterns(base: FoodPatterns, override: FoodPatterns): FoodPatterns {
        return FoodPatterns(
            queuePatterns = mergeQueuePatterns(base.queuePatterns, override.queuePatterns),
            sloganPattern = override.sloganPattern ?: base.sloganPattern,
            keywordPattern = override.keywordPattern ?: base.keywordPattern,
            fallbackPattern = override.fallbackPattern ?: base.fallbackPattern
        )
    }

    private fun mergePatterns(base: List<ExtractionPattern>, override: List<ExtractionPattern>): List<ExtractionPattern> {
        val result = base.toMutableList()
        override.forEach { overridePattern ->
            val existingIdx = result.indexOfFirst { it.id == overridePattern.id }
            if (existingIdx >= 0) {
                result[existingIdx] = overridePattern
            } else {
                result.add(overridePattern)
            }
        }
        return result.sortedBy { it.priority }
    }

    private fun mergeQueuePatterns(base: List<QueuePattern>, override: List<QueuePattern>): List<QueuePattern> {
        val result = base.toMutableList()
        override.forEach { overridePattern ->
            val existingIdx = result.indexOfFirst { it.id == overridePattern.id }
            if (existingIdx >= 0) {
                result[existingIdx] = overridePattern
            } else {
                result.add(overridePattern)
            }
        }
        return result
    }

    private fun mergeCategoryDetection(base: CategoryDetectionConfig, override: CategoryDetectionConfig): CategoryDetectionConfig {
        return CategoryDetectionConfig(
            defaultCategory = override.defaultCategory.ifBlank { base.defaultCategory },
            drinkTriggers = DrinkTriggers(
                brandBased = override.drinkTriggers.brandBased,
                textKeywords = mergeListWithDedup(override.drinkTriggers.textKeywords, base.drinkTriggers.textKeywords)
            ),
            expressTriggers = ExpressTriggers(
                textKeywords = mergeListWithDedup(override.expressTriggers.textKeywords, base.expressTriggers.textKeywords),
                brandInText = override.expressTriggers.brandInText
            )
        )
    }

    private fun mergeHomepageDetection(base: HomepageDetectionConfig, override: HomepageDetectionConfig): HomepageDetectionConfig {
        return HomepageDetectionConfig(
            keywords = mergeListWithDedup(override.keywords, base.keywords),
            threshold = if (override.threshold != 3) override.threshold else base.threshold
        )
    }

    private fun mergeTextCleaning(base: TextCleaningConfig, override: TextCleaningConfig): TextCleaningConfig {
        return TextCleaningConfig(
            datetimePattern = override.datetimePattern.ifBlank { base.datetimePattern },
            corrections = override.corrections.ifEmpty { base.corrections },
            charRemovals = override.charRemovals.ifEmpty { base.charRemovals },
            spaceCollapse = override.spaceCollapse.ifBlank { base.spaceCollapse }
        )
    }

    private fun mergeValidation(base: ValidationConfig, override: ValidationConfig): ValidationConfig {
        return ValidationConfig(
            expressCode = mergeExpressValidation(base.expressCode, override.expressCode),
            foodCode = mergeFoodValidation(base.foodCode, override.foodCode)
        )
    }

    private fun mergeExpressValidation(base: ExpressValidation, override: ExpressValidation): ExpressValidation {
        return ExpressValidation(
            maxLength = if (override.maxLength != 12) override.maxLength else base.maxLength,
            rejectAllLetters = override.rejectAllLetters,
            rejectPhonePattern = override.rejectPhonePattern.ifBlank { base.rejectPhonePattern },
            rejectYearPrefix = override.rejectYearPrefix.ifBlank { base.rejectYearPrefix },
            rejectYearLength = if (override.rejectYearLength != 4) override.rejectYearLength else base.rejectYearLength,
            rejectDatePattern = override.rejectDatePattern.ifBlank { base.rejectDatePattern },
            phoneTail = PhoneTailConfig(
                length = if (override.phoneTail.length != 4) override.phoneTail.length else base.phoneTail.length,
                digitsOnly = override.phoneTail.digitsOnly,
                maskPattern = override.phoneTail.maskPattern.ifBlank { base.phoneTail.maskPattern },
                contextKeywords = mergeListWithDedup(override.phoneTail.contextKeywords, base.phoneTail.contextKeywords)
            )
        )
    }

    private fun mergeFoodValidation(base: FoodValidation, override: FoodValidation): FoodValidation {
        return FoodValidation(
            rejectYearPrefix = override.rejectYearPrefix.ifBlank { base.rejectYearPrefix },
            rejectYearLength = if (override.rejectYearLength != 4) override.rejectYearLength else base.rejectYearLength,
            rejectTimeContexts = override.rejectTimeContexts.ifEmpty { base.rejectTimeContexts },
            rejectTimeKeywords = override.rejectTimeKeywords.ifEmpty { base.rejectTimeKeywords },
            distractionWords = override.distractionWords.ifEmpty { base.distractionWords },
            distractionRange = if (override.distractionRange != 2) override.distractionRange else base.distractionRange
        )
    }

    private fun mergeScoring(base: ScoringConfig, override: ScoringConfig): ScoringConfig {
        return ScoringConfig(
            brandDetection = BrandScoring(
                keywordMatchWeight = if (override.brandDetection.keywordMatchWeight != 4) override.brandDetection.keywordMatchWeight else base.brandDetection.keywordMatchWeight,
                colonMatchWeight = if (override.brandDetection.colonMatchWeight != 1) override.brandDetection.colonMatchWeight else base.brandDetection.colonMatchWeight,
                exactMatchWeight = if (override.brandDetection.exactMatchWeight != 15) override.brandDetection.exactMatchWeight else base.brandDetection.exactMatchWeight
            ),
            expressCode = ExpressScoring(
                baseByBboxWidth = override.expressCode.baseByBboxWidth,
                dashMultiplier = if (override.expressCode.dashMultiplier != 20) override.expressCode.dashMultiplier else base.expressCode.dashMultiplier,
                letterMultiplier = if (override.expressCode.letterMultiplier != 5) override.expressCode.letterMultiplier else base.expressCode.letterMultiplier
            ),
            foodCode = FoodScoring(
                baseByBboxWidth = override.foodCode.baseByBboxWidth,
                brandOverrides = base.foodCode.brandOverrides.toMutableMap().apply {
                    override.foodCode.brandOverrides.forEach { (k, v) -> put(k, v) }
                }
            )
        )
    }

    private fun mergePickupLocation(base: PickupLocationConfig, override: PickupLocationConfig): PickupLocationConfig {
        return PickupLocationConfig(
            startKeywords = mergeListWithDedup(override.startKeywords, base.startKeywords),
            targetKeywords = mergeListWithDedup(override.targetKeywords, base.targetKeywords),
            stopKeywords = mergeListWithDedup(override.stopKeywords, base.stopKeywords),
            garbagePatterns = override.garbagePatterns.ifEmpty { base.garbagePatterns },
            proximityThreshold = if (override.proximityThreshold != 400) override.proximityThreshold else base.proximityThreshold
        )
    }

    private fun mergeListWithDedup(override: List<String>, base: List<String>): List<String> {
        val result = override.toMutableList()
        base.forEach { item ->
            if (item !in result) result.add(item)
        }
        return result
    }
}
