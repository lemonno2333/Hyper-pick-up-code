package com.Badnng.moe.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.Badnng.moe.rules.RecognitionRuleEngine
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class TextRecognitionHelper(private val context: Context) {
    // 保留 ML Kit 条码扫描
    private val barcodeScanner = BarcodeScanning.getClient()

    // PaddleOCR 文字识别
    val paddleOcr = PaddleOcrHelper.getInstance(context)

    private val engine = RecognitionRuleEngine
    private val drinkBrands get() = engine.getAllDrinkNames()
    private val foodBrands get() = engine.getAllFoodNames()
    private val expressBrandKeywords get() = engine.getAllExpressKeywords()
    private val homePageKeywords get() = engine.getHomepageKeywords()

    /**
     * 初始化 OCR 引擎（需要在应用启动时调用）
     */
    fun initOcr(): Boolean {
        return paddleOcr.init()
    }

    suspend fun recognizeAll(bitmap: Bitmap, sourceApp: String? = null, sourcePkg: String? = null): RecognitionResult {
        Log.d("RecognitionMonitor", "=== recognizeAll 开始 ===")

        // 确保 OCR 已初始化（最多等待 3 秒）
        var waitCount = 0
        while (!paddleOcr.isInitialized && waitCount < 30) {
            kotlinx.coroutines.delay(100)
            waitCount++
        }
        if (!paddleOcr.isInitialized) {
            Log.e("RecognitionMonitor", "OCR 未初始化，尝试同步初始化")
            paddleOcr.init()
        }

        // 使用 PaddleOCR 进行文字识别
        val ocrResult = paddleOcr.recognize(bitmap)
        Log.d("RecognitionMonitor", "OCR result: ${if (ocrResult != null) "not null, blocks=${ocrResult.textBlocks.size}" else "NULL"}")
        val rawFullText = ocrResult?.fullText ?: ""
        val textBlocks = ocrResult?.textBlocks ?: emptyList()

        // 保留 ML Kit 条码扫描
        val image = InputImage.fromBitmap(bitmap, 0)
        val barcodeResult = try {
            withContext(Dispatchers.Main) {
                barcodeScanner.process(image).await()
            }
        } catch (e: Exception) { null }

        val mergedText = cleanChineseText(rawFullText)
        Log.d("RecognitionMonitor", "rawFullText length=${rawFullText.length}, mergedText length=${mergedText.length}")
        Log.d("RecognitionMonitor", "mergedText preview=${mergedText.take(100)}")

        val hasTakeoutKeywords = mergedText.contains("取餐") || mergedText.contains("取茶") ||
                mergedText.contains("取件") || mergedText.contains("取性") ||
                mergedText.contains("验证码") || mergedText.contains("券码") ||
                mergedText.contains("订单") || mergedText.contains("准备完毕") ||
                mergedText.contains("领取") || mergedText.contains("取件码") ||
                mergedText.contains("取養")

        val homePageElementCount = homePageKeywords.count { mergedText.contains(it) }
        // 快递页面不做首页判断（快递页不存在首页误判问题，强行跳过会漏掉取件码）
        // 同时提高阈值到3，避免"我的包裹"这类文字误触发
        val isLikelyHomePage = homePageElementCount >= 3

        var qrCode = barcodeResult?.firstOrNull()?.rawValue
        if (qrCode != null && (qrCode.contains("http://", ignoreCase = true) || qrCode.contains("https://", ignoreCase = true))) {
            qrCode = null
        }

        var takeoutCode: String? = null
        var pickupLocation: String? = null

        var detectedBrand: String? = sourcePkg?.let { engine.getBrandByPackage(it) }

        // 检测到啡快口令，品牌直接设为星巴克
        if (mergedText.contains("啡快口令")) {
            detectedBrand = "星巴克"
        } else if (detectedBrand == null) {
            val brandHits = mutableMapOf<String, Int>()
            val scoringConfig = engine.rules.scoring.brandDetection
            for (brand in engine.getDrinkBrands() + engine.getFoodBrands()) {
                if (brand.keywords.isNotEmpty() && brand.keywords.any { mergedText.contains(it) }) {
                    val score = brand.scoringOverrides["exact_match_weight"] ?: scoringConfig.exactMatchWeight
                    brandHits[brand.name] = (brandHits[brand.name] ?: 0) + score
                }
                if (mergedText.contains(brand.name, ignoreCase = true)) {
                    val colonScore = if (Regex("${Regex.escape(brand.name)}[:：]\\d").containsMatchIn(mergedText)) scoringConfig.colonMatchWeight else scoringConfig.keywordMatchWeight
                    brandHits[brand.name] = (brandHits[brand.name] ?: 0) + colonScore
                }
                for (alias in brand.aliases) {
                    if (mergedText.contains(alias, ignoreCase = true)) {
                        val colonScore = if (Regex("${Regex.escape(alias)}[:：]\\d").containsMatchIn(mergedText)) scoringConfig.colonMatchWeight else scoringConfig.keywordMatchWeight
                        brandHits[brand.name] = (brandHits[brand.name] ?: 0) + colonScore
                    }
                }
            }
            detectedBrand = brandHits.maxByOrNull { it.value }?.key
        }

        if (detectedBrand == "KFC") detectedBrand = "肯德基"

        // 先判断 category，再根据 category 走不同的识别路径
        val catConfig = engine.rules.categoryDetection
        var category = catConfig.defaultCategory
        if ((catConfig.drinkTriggers.brandBased && drinkBrands.contains(detectedBrand)) ||
            catConfig.drinkTriggers.textKeywords.any { mergedText.contains(it) }
        ) {
            category = "饮品"
        } else if (
            catConfig.expressTriggers.textKeywords.any { mergedText.contains(it) } ||
            (catConfig.expressTriggers.brandInText && expressBrandKeywords.any { mergedText.contains(it) })
        ) {
            category = "快递"
        }

        if (!isLikelyHomePage || category == "快递") {
            takeoutCode = if (category == "快递") {
                extractExpressCode(textBlocks, mergedText)
            } else {
                extractFoodCode(textBlocks, mergedText, detectedBrand, qrCode)
            }
        }

        if (detectedBrand == "瑞幸" && qrCode == null) {
            takeoutCode = null
        }

        pickupLocation = findPickupLocation(mergedText, textBlocks)

        Log.d("RecognitionMonitor", "------------------------------------")
        Log.d("RecognitionMonitor", "Source App: $sourceApp")
        Log.d("RecognitionMonitor", "Source Package: $sourcePkg")
        Log.d("RecognitionMonitor", "Full Text: $mergedText")
        Log.d("RecognitionMonitor", "Extracted Code: $takeoutCode")
        Log.d("RecognitionMonitor", "QR Data: $qrCode")
        Log.d("RecognitionMonitor", "Category: $category")
        Log.d("RecognitionMonitor", "Brand: $detectedBrand")
        Log.d("RecognitionMonitor", "Pickup Location: $pickupLocation")
        Log.d("RecognitionMonitor", "------------------------------------")

        return RecognitionResult(takeoutCode, qrCode, category, detectedBrand, rawFullText, pickupLocation)
    }

    // ─────────── 快递取件码识别 ───────────
    private fun extractExpressCode(
        blocks: List<PaddleOcrHelper.TextBlock>,
        mergedText: String
    ): String? {
        val expressKeywords = engine.getExpressTriggerKeywords()

        fun pickCandidate(source: String, contextText: String): String? {
            // 使用引擎中的正则模式按优先级匹配
            for (pattern in engine.getExpressPatterns()) {
                val compiled = engine.getCompiledPattern(pattern.id) ?: continue
                val match = compiled.find(source)
                if (match != null) {
                    val value = match.value
                    if (isInvalidExpressCode(value)) continue
                    if (isLikelyPhoneTail(value, contextText)) continue
                    return value
                }
            }
            return null
        }

        // 第一步：精确从"取件码:"后截取
        val datetimePatternStr = engine.rules.textCleaning.datetimePattern
        val datetimeRegex = Regex(datetimePatternStr)
        for (i in blocks.indices) {
            val block = blocks[i]
            val text = block.text.replace("\n", "")
                .replace(datetimeRegex, "")
                .replace(" ", "")
            Log.d("RecognitionMonitor", "ExpressBlock: [$text]")
            val matchedKeyword = expressKeywords.firstOrNull { text.contains(it) } ?: continue
            val afterKeyword = text.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            Log.d("RecognitionMonitor", "AfterKeyword: [$afterKeyword]")

            val fromSameBlock = pickCandidate(afterKeyword, text)
            if (fromSameBlock != null) return fromSameBlock

            if (afterKeyword.isBlank()) {
                for (lookAhead in 1..3) {
                    val next = blocks.getOrNull(i + lookAhead) ?: break
                    val nextText = next.text.replace("\n", "").replace(" ", "")
                    val fromNext = pickCandidate(nextText, nextText)
                    if (fromNext != null) return fromNext
                }
            }
        }

        // 第二步：兜底——在含快递关键词的文本中按权重筛选
        val hasExpressKeyword = expressKeywords.any { mergedText.contains(it) }
        val hasExpressBrand = expressBrandKeywords.any { mergedText.contains(it) }
        if (!hasExpressKeyword && !hasExpressBrand) return null
        val fallbackPattern = engine.getCompiledPattern("express_fallback") ?: return null
        val scoringConfig = engine.rules.scoring.expressCode
        val candidates = mutableListOf<Pair<String, Int>>()
        for (block in blocks) {
            val text = block.text.replace(" ", "").replace("\n", "")
            for (match in fallbackPattern.findAll(text)) {
                val value = match.value
                if (isInvalidExpressCode(value)) continue
                if (isLikelyPhoneTail(value, text)) continue
                var weight = (block.boundingBox?.width() ?: 0) * value.length
                if (value.contains("-")) weight *= scoringConfig.dashMultiplier
                if (value.any { it.isLetter() }) weight *= scoringConfig.letterMultiplier
                candidates.add(value to weight)
            }
        }
        candidates.sortByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun isInvalidExpressCode(value: String): Boolean {
        val vc = engine.rules.validation.expressCode
        if (vc.rejectAllLetters && value.all { it.isLetter() }) return true
        if (Regex(vc.rejectPhonePattern).matches(value)) return true
        if (value.startsWith(vc.rejectYearPrefix) && value.length == vc.rejectYearLength) return true
        if (Regex(vc.rejectDatePattern).matches(value)) return true
        if (value.length > vc.maxLength) return true
        return false
    }

    private fun isLikelyPhoneTail(value: String, contextText: String): Boolean {
        val ptConfig = engine.rules.validation.expressCode.phoneTail
        if (value.length != ptConfig.length) return false
        if (ptConfig.digitsOnly && !value.all { it.isDigit() }) return false

        val escaped = Regex.escape(value)
        if (Regex("${ptConfig.maskPattern}$escaped").containsMatchIn(contextText)) return true

        val keywords = ptConfig.contextKeywords.joinToString("|")
        val phoneContextPattern = Regex(
            "($keywords)[^\\n]{0,12}(\\*{0,6})$escaped"
        )
        if (phoneContextPattern.containsMatchIn(contextText)) return true

        return false
    }

    // ─────────── 餐食/饮品取餐码识别 ───────────
    private fun extractFoodCode(
        blocks: List<PaddleOcrHelper.TextBlock>,
        mergedText: String,
        detectedBrand: String?,
        qrCode: String?
    ): String? {
        // 餐饮排队叫号场景（如"小桌 A3"）优先提取短码，避免被通用取餐码规则漏掉。
        val queueKeywords = listOf("叫号", "取号", "过号", "排队", "迎宾台", "到店就餐", "还需等待", "桌安排")
        val queueHitCount = queueKeywords.count { mergedText.contains(it) }
        if (queueHitCount >= 2) {
            val queuePatterns = listOf(
                // 小桌A3 / 中桌B12 / 大桌C5
                Regex("(小桌|中桌|大桌)\\s*([A-Z]{1,2}\\d{1,3}|\\d{1,3}[A-Z]{1,2})"),
                // A3号 / A3桌 / A3台 / A3DK号（允许中间有 0~2 位噪声字母）
                Regex("([A-Z]{1,2}\\d{1,3}|\\d{1,3}[A-Z]{1,2})(?=[A-Z]{0,2}号|\\s*(?:号|桌|台|单))")
            )

            fun pickQueueCode(text: String): String? {
                val normalized = text.replace(" ", "").replace("\n", "")
                // 先尝试带桌型的格式，命中后返回完整展示文本（如：小桌 A3）。
                val deskWithCode = queuePatterns[0].find(normalized)
                if (deskWithCode != null) {
                    val desk = deskWithCode.groupValues[1]
                    val code = deskWithCode.groupValues[2]
                    if (code.length in 2..5 && code.any { it.isLetter() } && code.any { it.isDigit() }) {
                        return "$desk $code"
                    }
                }

                // 再回退到纯号位码（如 A3号 / A3桌）。
                val plain = queuePatterns[1].find(normalized)
                if (plain != null) {
                    val code = plain.groupValues[1]
                    if (code.length in 2..5 && code.any { it.isLetter() } && code.any { it.isDigit() }) {
                        return code
                    }
                }
                return null
            }

            for (block in blocks) {
                val c = pickQueueCode(block.text)
                if (c != null) return c
            }
            pickQueueCode(mergedText)?.let { return it }
        }

        // 口令型取餐码全局优先（如 M707.你的脚步有力量），很多页面会把口令放在"取餐码"前面。
        val foodHintKeywords = listOf("取餐码", "取餐号", "取单码", "取单号", "取茶号", "待取餐", "当前订单")
        if (foodHintKeywords.any { mergedText.contains(it) }) {
            val sloganPattern = Regex("([A-Z][A-Z0-9]{2,9}[.．][\\u4e00-\\u9fa5A-Za-z0-9]{2,24})")
            val fromBlocks = blocks.asSequence()
                .map { it.text.replace(" ", "").replace("\n", "") }
                .mapNotNull { txt -> sloganPattern.find(txt)?.groupValues?.get(1) }
                .firstOrNull()
            if (fromBlocks != null && !isInvalidFoodCode(fromBlocks, mergedText, detectedBrand)) {
                return fromBlocks
            }
            val fromMerged = sloganPattern.find(mergedText)?.groupValues?.get(1)
            if (fromMerged != null && !isInvalidFoodCode(fromMerged, mergedText, detectedBrand)) {
                return fromMerged
            }
        }

        // 星巴克啡快口令识别：格式为 "数字.文字" 如 "17.超常发挥"
        if (detectedBrand == "星巴克" || mergedText.contains("啡快口令")) {
            for (block in blocks) {
                val text = block.text.replace(" ", "").replace("\n", "")
                // 匹配 "数字.文字" 或 "数字．文字" 格式
                val starbucksMatch = Regex("(\\d{1,3}[.．][\\u4e00-\\u9fa5]{2,10})").find(text)
                if (starbucksMatch != null) {
                    return starbucksMatch.value
                }
            }
        }

        val foodKeywords = listOf("取单码", "取单号", "取餐号", "取餐码", "取茶号", "取货码", "券码", "订单号", "取性码", "取養号")
        val hasFoodKeywords = mergedText.contains("取餐") || mergedText.contains("取茶") ||
                mergedText.contains("验证码") || mergedText.contains("券码") ||
                mergedText.contains("订单") || mergedText.contains("准备完毕") ||
                mergedText.contains("领取") || mergedText.contains("取養") ||
                mergedText.contains("取单") || mergedText.contains("取货")

        var targetKeywordRect: android.graphics.Rect? = null

        // 第一步：精确从关键词后截取
        for (block in blocks) {
            val text = block.text.replace(" ", "").replace("\n", "")
            val keywordPattern = "(取单码|取单号|取餐号|取餐码|取茶号|取货码|券码|订单号|取性码|取養号)"
            val forwardMatch = Regex("$keywordPattern[:：]?([A-Z0-9]{3,10})").find(text)
            if (forwardMatch != null) {
                val code = forwardMatch.groupValues[2]
                if (!isInvalidFoodCode(code, text, detectedBrand)) {
                    return code
                }
            }
            val reverseMatch = Regex("([A-Z0-9]{3,10})[:：]?$keywordPattern").find(text)
            if (reverseMatch != null) {
                val code = reverseMatch.groupValues[1]
                if (!isInvalidFoodCode(code, text, detectedBrand)) {
                    return code
                }
            }
            val matchedKeyword = foodKeywords.firstOrNull { text.contains(it) } ?: continue
            targetKeywordRect = block.boundingBox
            val afterKeyword = text.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            // 口令型取餐码（如 M707.你的脚步有力量）优先提取完整文本
            val sloganCodeMatch = Regex("([A-Z][A-Z0-9]{2,9}[.．][\\u4e00-\\u9fa5A-Za-z0-9]{2,24})").find(afterKeyword)
            if (sloganCodeMatch != null) {
                val sloganCode = sloganCodeMatch.groupValues[1]
                if (!isInvalidFoodCode(sloganCode, afterKeyword, detectedBrand)) {
                    return sloganCode
                }
            }
            val match = Regex("[A-Z0-9]{3,10}").find(afterKeyword)
            if (match != null && !foodKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                if (!isInvalidFoodCode(match.value, afterKeyword, detectedBrand)) {
                    return match.value
                }
            }
        }

        // 第二步：在关键词附近 block 中搜索
        if (targetKeywordRect != null) {
            val candidates = blocks.mapNotNull { block ->
                val box = block.boundingBox ?: return@mapNotNull null
                val text = block.text.replace(" ", "").replace("\n", "")
                if (Regex("^[A-Z0-9]{3,10}$").matches(text) && !isInvalidFoodCode(text, text, detectedBrand)) {
                    val dist = Math.abs((box.top + box.bottom) / 2 - (targetKeywordRect!!.top + targetKeywordRect!!.bottom) / 2)
                    if (dist < 400) text to dist else null
                } else null
            }.sortedBy { it.second }
            candidates.firstOrNull()?.first?.let { return it }
        }

        // 第三步：全文兜底按权重搜索
        if (!hasFoodKeywords) return null
        val pattern = Regex("(?<![a-zA-Z0-9])([A-Z0-9]{3,10})(?![a-zA-Z0-9])")
        val candidates = blocks.flatMap { block ->
            val text = block.text.replace(" ", "").replace("\n", "")
            pattern.findAll(text).mapNotNull { match ->
                val value = match.value
                if (value.length == 3 && value.all { it.isDigit() }) {
                    val aroundStart = (match.range.first - 6).coerceAtLeast(0)
                    val aroundEnd = (match.range.last + 6).coerceAtMost(text.lastIndex)
                    val around = text.substring(aroundStart, aroundEnd + 1)
                    val nearKeyword = foodKeywords.any { around.contains(it) }
                    if (!nearKeyword) return@mapNotNull null
                }
                if (isInvalidFoodCode(value, text, detectedBrand)) return@mapNotNull null
                var weight = (block.boundingBox?.width() ?: 0) * value.length
                if (detectedBrand == "肯德基" || detectedBrand == "麦当劳") {
                    if (value.length >= 4 && value.any { it.isLetter() }) weight *= 20
                    if (value.length == 5 && value.all { it.isDigit() }) weight *= 5
                }
                if (detectedBrand == "喜茶" && value.length == 4 && value.all { it.isDigit() }) weight *= 5
                value to weight
            }.toList()
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun isInvalidFoodCode(value: String, context: String, detectedBrand: String?): Boolean {
        val vc = engine.rules.validation.foodCode
        if (value.startsWith(vc.rejectYearPrefix) && value.length == vc.rejectYearLength) return true
        if (vc.rejectTimeContexts.any { context.contains(it) }) {
            if (value.all { it.isDigit() || it == ':' || it == '/' }) return true
        }
        if (vc.rejectTimeKeywords.any { context.contains(it) }) return true
        val lowerContext = context.lowercase()
        if (vc.distractionWords.any { word ->
                lowerContext.contains(word) && lowerContext.indexOf(word) in (lowerContext.indexOf(value) - vc.distractionRange)..(lowerContext.indexOf(value) + value.length + vc.distractionRange)
            }) return true
        return false
    }

    private fun findPickupLocation(mergedText: String, blocks: List<PaddleOcrHelper.TextBlock>): String? {
        val plConfig = engine.rules.pickupLocation
        val startKeywords = plConfig.startKeywords
        val targetKeywords = plConfig.targetKeywords
        val stopKeywords = plConfig.stopKeywords

        fun isGarbageMatch(location: String): Boolean {
            for (pattern in plConfig.garbagePatterns) {
                if (location.contains(pattern)) return true
            }
            return false
        }

        fun locationScore(location: String): Int {
            var score = location.length
            for (keyword in targetKeywords) {
                if (location.contains(keyword)) score += 20
            }
            return score
        }

        val candidates = mutableListOf<Pair<String, Int>>()

        val addressPattern = Regex("地址[:：\\s]*(.{4,80}?(?:${targetKeywords.joinToString("|")}))")
        val addressMatch = addressPattern.find(mergedText)
        if (addressMatch != null) {
            val loc = truncateLocation(addressMatch.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc) + 1000)
        }
        val addressFallback = Regex("地址[:：\\s]*([^,，。！!?；;.\\n]{4,60})")
        val fallbackMatch = addressFallback.find(mergedText)
        if (fallbackMatch != null) {
            val candidate = truncateLocation(fallbackMatch.groupValues[1])
            if (candidate.length > 8 && !isGarbageMatch(candidate)) {
                candidates.add(candidate to locationScore(candidate) + 500)
            }
        }

        val locWithTargetPattern = Regex("(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{2,60}?(?:${targetKeywords.joinToString("|")}))")
        for (match in locWithTargetPattern.findAll(mergedText)) {
            val loc = truncateLocation(match.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc) + 100)
        }

        val locToVerbPattern = Regex(
            "(?:${startKeywords.joinToString("|")})([^,，。！!?;？\\s]{2,60}?)(?=[,，。！!?;？\\s]*(?:${stopKeywords.joinToString("|")}))"
        )
        val locMatch2 = locToVerbPattern.find(mergedText)
        if (locMatch2 != null) {
            val loc = truncateLocation(locMatch2.groupValues[1])
            if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc))
        }

        for (block in blocks) {
            val text = block.text.replace("\n", "").replace(" ", "")
            if (targetKeywords.any { text.contains(it) }) {
                val loc = truncateLocation(text)
                if (!isGarbageMatch(loc)) candidates.add(loc to locationScore(loc))
            }
        }

        return candidates.maxByOrNull { it.second }?.first
    }

    private fun cleanChineseText(text: String): String {
        val cleanConfig = engine.rules.textCleaning
        var result = text
            .replace(Regex(cleanConfig.datetimePattern), "")
            .replace(Regex(cleanConfig.spaceCollapse), "")
            .replace("\n", "")
        for (removal in cleanConfig.charRemovals) {
            result = result.replace(removal, "")
        }
        for ((from, to) in engine.getTextCorrections()) {
            result = result.replace(from, to)
        }
        return result
    }

    private fun truncateLocation(location: String): String {
        val stopKeywords = listOf("请凭", "靖凭", "取件", "领取", "复制", "查看", "日已接收", "已接收", "日已签收", "日已到", "点击", "联系", "如有", "如有疑问", "取您的")
        var result = location
        // 分号通常是地址段的分隔符，直接截断
        val semicolonIdx = result.indexOf(';')
        if (semicolonIdx != -1) result = result.substring(0, semicolonIdx)
        for (stop in stopKeywords) {
            val index = result.indexOf(stop)
            if (index != -1) result = result.substring(0, index)
        }
        return result.replace("[,，。！!?;？;|\\s]+$".toRegex(), "")
    }

    // ─────────── 纯文字识别（用于划选文字处理） ───────────
    fun recognizeFromText(text: String): RecognitionResult {
        val mergedText = cleanChineseText(text)

        // 品牌识别
        var detectedBrand: String? = null
        val brandHits = mutableMapOf<String, Int>()
        val scoringConfig = engine.rules.scoring.brandDetection
        for (brand in engine.getDrinkBrands() + engine.getFoodBrands()) {
            if (brand.keywords.isNotEmpty() && brand.keywords.any { mergedText.contains(it) }) {
                val score = brand.scoringOverrides["exact_match_weight"] ?: scoringConfig.exactMatchWeight
                brandHits[brand.name] = (brandHits[brand.name] ?: 0) + score
            }
            if (mergedText.contains(brand.name, ignoreCase = true)) {
                val colonScore = if (Regex("${Regex.escape(brand.name)}[:：]\\d").containsMatchIn(mergedText)) scoringConfig.colonMatchWeight else scoringConfig.keywordMatchWeight
                brandHits[brand.name] = (brandHits[brand.name] ?: 0) + colonScore
            }
            for (alias in brand.aliases) {
                if (mergedText.contains(alias, ignoreCase = true)) {
                    val colonScore = if (Regex("${Regex.escape(alias)}[:：]\\d").containsMatchIn(mergedText)) scoringConfig.colonMatchWeight else scoringConfig.keywordMatchWeight
                    brandHits[brand.name] = (brandHits[brand.name] ?: 0) + colonScore
                }
            }
        }
        detectedBrand = brandHits.maxByOrNull { it.value }?.key
        if (detectedBrand == "KFC") detectedBrand = "肯德基"

        // 检测到啡快口令，品牌直接设为星巴克
        if (mergedText.contains("啡快口令")) {
            detectedBrand = "星巴克"
        }

        // 类型判断
        val catConfig = engine.rules.categoryDetection
        var category = catConfig.defaultCategory
        if ((catConfig.drinkTriggers.brandBased && drinkBrands.contains(detectedBrand)) ||
            catConfig.drinkTriggers.textKeywords.any { mergedText.contains(it) }
        ) {
            category = "饮品"
        } else if (
            catConfig.expressTriggers.textKeywords.any { mergedText.contains(it) } ||
            (catConfig.expressTriggers.brandInText && expressBrandKeywords.any { mergedText.contains(it) })
        ) {
            category = "快递"
        }

        // 提取取件码
        val takeoutCode = if (category == "快递") {
            extractExpressCodeFromText(mergedText)
        } else {
            extractFoodCodeFromText(mergedText, detectedBrand)
        }

        // 提取取货地点
        val pickupLocation = findPickupLocation(mergedText, emptyList())

        Log.d("ProcessTextRecognition", "------------------------------------")
        Log.d("ProcessTextRecognition", "Input Text: $mergedText")
        Log.d("ProcessTextRecognition", "Extracted Code: $takeoutCode")
        Log.d("ProcessTextRecognition", "Category: $category")
        Log.d("ProcessTextRecognition", "Brand: $detectedBrand")
        Log.d("ProcessTextRecognition", "Pickup Location: $pickupLocation")
        Log.d("ProcessTextRecognition", "------------------------------------")

        return RecognitionResult(takeoutCode, null, category, detectedBrand, text, pickupLocation)
    }

    private fun extractExpressCodeFromText(mergedText: String): String? {
        val expressKeywords = engine.getExpressTriggerKeywords()
        val hasExpressKeyword = expressKeywords.any { mergedText.contains(it) }
        val hasExpressBrand = expressBrandKeywords.any { mergedText.contains(it) }

        // 第一步：精确从关键词后截取
        val matchedKeyword = expressKeywords.firstOrNull { mergedText.contains(it) }
        if (matchedKeyword != null) {
            val afterKeyword = mergedText.substringAfter(matchedKeyword).trimStart(':', '：', ' ')

            // 使用引擎中的正则模式按优先级匹配
            for (pattern in engine.getExpressPatterns()) {
                val compiled = engine.getCompiledPattern(pattern.id) ?: continue
                val match = compiled.find(afterKeyword)
                if (match != null &&
                    !isInvalidExpressCode(match.value) &&
                    !isLikelyPhoneTail(match.value, mergedText)
                ) {
                    return match.value
                }
            }
        }

        // 第二步：兜底——按权重筛选
        if (!hasExpressKeyword && !hasExpressBrand) return null
        val fallbackPattern = engine.getCompiledPattern("express_fallback") ?: return null
        val scoringConfig = engine.rules.scoring.expressCode
        val candidates = fallbackPattern.findAll(mergedText).mapNotNull { match ->
            val value = match.value
            if (isInvalidExpressCode(value)) return@mapNotNull null
            if (isLikelyPhoneTail(value, mergedText)) return@mapNotNull null
            var weight = value.length
            if (value.contains("-")) weight *= scoringConfig.dashMultiplier
            if (value.any { it.isLetter() }) weight *= scoringConfig.letterMultiplier
            value to weight
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    private fun extractFoodCodeFromText(mergedText: String, detectedBrand: String?): String? {
        val foodKeywords = engine.getFoodHintKeywords()
        val foodTriggerKeywords = engine.getFoodTriggerKeywords()
        val hasFoodKeywords = foodTriggerKeywords.any { mergedText.contains(it) }

        // 口令型取餐码全局优先（如 M707.你的脚步有力量）
        if (hasFoodKeywords) {
            val sloganPattern = engine.getCompiledPattern("slogan_code")
            if (sloganPattern != null) {
                val sloganCode = sloganPattern.find(mergedText)?.groupValues?.get(1)
                if (sloganCode != null && !isInvalidFoodCode(sloganCode, mergedText, detectedBrand)) {
                    return sloganCode
                }
            }
        }

        // 使用引擎中的关键词正则
        val keywordPatternConfig = engine.rules.codeExtraction.food.patterns.keywordPattern
        if (keywordPatternConfig != null) {
            val forwardRegex = engine.getCompiledPattern("${keywordPatternConfig.id}_forward")
            if (forwardRegex != null) {
                val forwardMatch = forwardRegex.find(mergedText)
                if (forwardMatch != null) {
                    val code = forwardMatch.groupValues[1]
                    if (!isInvalidFoodCode(code, mergedText, detectedBrand)) return code
                }
            }
            val reverseRegex = engine.getCompiledPattern("${keywordPatternConfig.id}_reverse")
            if (reverseRegex != null) {
                val reverseMatch = reverseRegex.find(mergedText)
                if (reverseMatch != null) {
                    val code = reverseMatch.groupValues[1]
                    if (!isInvalidFoodCode(code, mergedText, detectedBrand)) return code
                }
            }
        }

        // 第一步：精确从关键词后截取
        val matchedKeyword = foodKeywords.firstOrNull { mergedText.contains(it) }
        if (matchedKeyword != null) {
            val afterKeyword = mergedText.substringAfter(matchedKeyword).trimStart(':', '：', ' ')
            // 口令型取餐码（如 M707.你的脚步有力量）优先提取完整文本
            val sloganPattern = engine.getCompiledPattern("slogan_code")
            if (sloganPattern != null) {
                val sloganCodeMatch = sloganPattern.find(afterKeyword)
                if (sloganCodeMatch != null) {
                    val sloganCode = sloganCodeMatch.groupValues[1]
                    if (!isInvalidFoodCode(sloganCode, afterKeyword, detectedBrand)) {
                        return sloganCode
                    }
                }
            }
            val match = Regex("[A-Z0-9]{3,10}").find(afterKeyword)
            if (match != null && !foodKeywords.any { it.contains(match.value) || match.value.contains(it) }) {
                if (!isInvalidFoodCode(match.value, afterKeyword, detectedBrand)) {
                    return match.value
                }
            }
        }

        // 第二步：全文兜底按权重搜索
        if (!hasFoodKeywords) return null
        val fallbackPattern = engine.getCompiledPattern("food_fallback") ?: return null
        val foodScoring = engine.rules.scoring.foodCode
        val brandScoring = foodScoring.brandOverrides[detectedBrand]
        val candidates = fallbackPattern.findAll(mergedText).mapNotNull { match ->
            val value = match.value
            if (value.length == 3 && value.all { it.isDigit() }) {
                val aroundStart = (match.range.first - 6).coerceAtLeast(0)
                val aroundEnd = (match.range.last + 6).coerceAtMost(mergedText.lastIndex)
                val around = mergedText.substring(aroundStart, aroundEnd + 1)
                val nearKeyword = foodKeywords.any { around.contains(it) }
                if (!nearKeyword) return@mapNotNull null
            }
            if (isInvalidFoodCode(value, mergedText, detectedBrand)) return@mapNotNull null
            var weight = value.length
            if (brandScoring != null) {
                if (value.length >= 4 && value.any { it.isLetter() }) weight *= brandScoring.lengthGte4LetterMultiplier
                if (value.length == 5 && value.all { it.isDigit() }) weight *= brandScoring.length5DigitMultiplier
                if (value.length == 4 && value.all { it.isDigit() }) weight *= brandScoring.length4DigitMultiplier
            }
            value to weight
        }.sortedByDescending { it.second }
        return candidates.firstOrNull()?.first
    }

    /**
     * 多取件码识别 - 用于识别一张图片中的多个快递取件码
     */
    suspend fun recognizeMultipleCodes(bitmap: Bitmap, sourceApp: String? = null, sourcePkg: String? = null): MultiRecognitionResult {
        Log.d("RecognitionMonitor", "=== recognizeMultipleCodes 开始 ===")
        
        // 确保 OCR 已初始化
        var waitCount = 0
        while (!paddleOcr.isInitialized && waitCount < 30) {
            kotlinx.coroutines.delay(100)
            waitCount++
        }
        if (!paddleOcr.isInitialized) {
            paddleOcr.init()
        }
        
        // 使用 PaddleOCR 进行文字识别
        val ocrResult = paddleOcr.recognize(bitmap)
        val rawFullText = ocrResult?.fullText ?: ""
        val textBlocks = ocrResult?.textBlocks ?: emptyList()
        
        // 保留 ML Kit 条码扫描
        val image = InputImage.fromBitmap(bitmap, 0)
        val barcodeResult = try {
            withContext(Dispatchers.Main) {
                barcodeScanner.process(image).await()
            }
        } catch (e: Exception) { null }
        
        val mergedText = cleanChineseText(rawFullText)
        Log.d("RecognitionMonitor", "多取件码识别 - 全文: ${mergedText.take(200)}")
        
        // 查找所有取件码和对应的快递品牌
        val orders = mutableListOf<RecognitionResult>()
        
        // 方法1：基于"取件码"关键词查找（支持"取件码123456"和"123456取件码"两种顺序）
        val expressKeywords = listOf("取件码", "取性码", "请凭", "靖凭")
        val keywordPattern = expressKeywords.joinToString("|")
        // 多码识别关键词提取：优先精确抓快递柜三段码，且必须包含数字，避免把 ZTO 这类品牌词识别成取件码。
        val rawCodePattern =
            "([0-9]{1,2}-[0-9]{1,2}-[0-9]{4}|[A-Z]{1,3}[0-9]{0,3}-[0-9]{3,8}|[A-Z0-9]{1,4}-[A-Z0-9]{3,8}|[A-Z]{1,3}[0-9]{4,8}|[0-9]{4,8})"
        // 关键：加边界，避免从长运单号中截取 4-8 位尾段误当取件码
        val codePattern = "(?<![A-Z0-9-])$rawCodePattern(?![A-Z0-9-])"
        val forwardPattern = Regex("(?:$keywordPattern)[:：\\s]*$codePattern")
        val reversePattern = Regex("$codePattern[:：\\s]*(?:$keywordPattern)")
        val allForwardMatches = forwardPattern.findAll(mergedText).toList()
        val allReverseMatches = reversePattern.findAll(mergedText).toList()
        Log.d(
            "RecognitionMonitor",
            "找到 ${allForwardMatches.size} 个正向匹配, ${allReverseMatches.size} 个反向匹配"
        )

        val addOrderIfValid: (String, IntRange) -> Unit = { code, range ->
            if (!isInvalidExpressCode(code) && !isLikelyPhoneTail(code, mergedText)) {
                // 查找这个取件码对应的快递品牌
                val brand = findBrandForCode(code, mergedText, range)
                
                // 查找取货地点
                val pickupLocation = findPickupLocation(mergedText, textBlocks)
                
                val order = RecognitionResult(
                    code = code,
                    qr = null,
                    type = "快递",
                    brand = brand,
                    fullText = rawFullText,
                    pickupLocation = pickupLocation
                )
                orders.add(order)
                
                Log.d("RecognitionMonitor", "识别到取件码: $code, 品牌: $brand")
            }
        }
        allForwardMatches.forEach { match ->
            addOrderIfValid(match.groupValues[1], match.range)
        }
        allReverseMatches.forEach { match ->
            addOrderIfValid(match.groupValues[1], match.range)
        }
        
        // 方法2：如果方法1找到的取件码不足，尝试基于快递品牌名称查找
        if (orders.isEmpty()) {
            val expressBrands = listOf("圆通快递", "中通快递", "申通快递", "韵达快递", "顺丰快递", "极兔快递", "德邦快递")
            for (brand in expressBrands) {
                if (mergedText.contains(brand)) {
                    // 在品牌名称附近查找取件码
                    val brandIndex = mergedText.indexOf(brand)
                    val nearbyText = mergedText.substring(
                        maxOf(0, brandIndex - 50),
                        minOf(mergedText.length, brandIndex + brand.length + 100)
                    )
                    
                    // 查找附近的取件码
                    val codePattern = Regex("取件码[:：\\s]*([A-Z0-9-]{3,12})")
                    val codeMatch = codePattern.find(nearbyText)
                    if (codeMatch != null) {
                        val code = codeMatch.groupValues[1]
                        if (!isInvalidExpressCode(code) && !isLikelyPhoneTail(code, mergedText)) {
                            val pickupLocation = findPickupLocation(mergedText, textBlocks)
                            val order = RecognitionResult(
                                code = code,
                                qr = null,
                                type = "快递",
                                brand = brand,
                                fullText = rawFullText,
                                pickupLocation = pickupLocation
                            )
                            orders.add(order)
                            Log.d("RecognitionMonitor", "基于品牌找到取件码: $code, 品牌: $brand")
                        }
                    }
                }
            }
        }

        // 方法3：无"取件码"关键词时，基于快递品牌上下文提取连字符/数字码
        // 仅在方法1/2完全找不到取件码时启用，避免把日期/运单号等数字误当成额外取件码。
        if (orders.isEmpty() && expressBrandKeywords.any { mergedText.contains(it) }) {
            // 常见三段式取件码（如 40-2-7253）。mergedText 可能会把手机号尾号与取件码粘连在一起（****591440-2-7253），
            // 这时使用带"边界"的兜底正则会漏匹配；先专门扫一遍三段数字连字符码，保证不漏。
            val pickupLocation = findPickupLocation(mergedText, textBlocks)
            val lockerPattern = Regex("([0-9]{1,2}-[0-9]{1,2}-[0-9]{3,5})")
            for (m in lockerPattern.findAll(mergedText)) {
                val code = m.groupValues[1]
                if (isInvalidExpressCode(code)) continue
                if (isLikelyPhoneTail(code, mergedText)) continue
                val brand = findBrandForCode(code, mergedText, m.range)
                orders.add(
                    RecognitionResult(
                        code = code,
                        qr = null,
                        type = "快递",
                        brand = brand,
                        fullText = rawFullText,
                        pickupLocation = pickupLocation
                    )
                )
            }

            // 纯数字兜底提取收紧为 5~8 位，避免把 "0003""0312" 这类日期/计数噪声识别成取件码。
            val fallbackPattern = Regex("(?<![A-Z0-9-])([0-9]{1,2}-[0-9]{1,2}-[0-9]{3,5}|[A-Z0-9]{1,4}-[A-Z0-9]{3,8}|[0-9]{5,8})(?![A-Z0-9-])")
            val fallbackMatches = fallbackPattern.findAll(mergedText).toList()
            for (m in fallbackMatches) {
                val code = m.groupValues[1]
                if (isInvalidExpressCode(code)) continue
                if (isLikelyPhoneTail(code, mergedText)) continue
                val brand = findBrandForCode(code, mergedText, m.range)
                orders.add(
                    RecognitionResult(
                        code = code,
                        qr = null,
                        type = "快递",
                        brand = brand,
                        fullText = rawFullText,
                        pickupLocation = pickupLocation
                    )
                )
            }
        }
        
        // 去重
        val uniqueOrders = orders.distinctBy { it.code }
        
        Log.d("RecognitionMonitor", "多取件码识别完成，共识别到 ${uniqueOrders.size} 个取件码")
        
        return MultiRecognitionResult(
            orders = uniqueOrders,
            hasMultipleCodes = uniqueOrders.size > 1
        )
    }
    
    /**
     * 根据取件码位置查找对应的快递品牌
     */
    private fun findBrandForCode(code: String, text: String, codeRange: IntRange): String? {
        val expressBrands = listOf(
            "圆通快递" to "圆通",
            "中通快递" to "中通",
            "申通快递" to "申通",
            "韵达快递" to "韵达",
            "顺丰快递" to "顺丰",
            "极兔快递" to "极兔",
            "德邦快递" to "德邦"
        )
        
        // 在取件码附近查找快递品牌
        val startIndex = maxOf(0, codeRange.first - 200)
        val endIndex = minOf(text.length, codeRange.last + 200)
        val nearbyText = text.substring(startIndex, endIndex)
        
        for ((fullName, shortName) in expressBrands) {
            if (nearbyText.contains(fullName)) {
                return shortName
            }
        }
        
        // 如果没找到完整品牌名，尝试查找简写
        for ((fullName, shortName) in expressBrands) {
            if (nearbyText.contains(shortName)) {
                return shortName
            }
        }
        
        return "快递"
    }
    
    fun close() {
        barcodeScanner.close()
        // 不要关闭 paddleOcr，因为它是单例，应该保持初始化状态
    }
}

data class RecognitionResult(val code: String?, val qr: String?, val type: String, val brand: String?, val fullText: String, val pickupLocation: String? = null)

/**
 * 多取件码识别结果
 */
data class MultiRecognitionResult(
    val orders: List<RecognitionResult>,
    val hasMultipleCodes: Boolean
)
