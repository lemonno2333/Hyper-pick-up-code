package com.Badnng.moe.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.Badnng.moe.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object BrandIconResolver {

    private val BUILTIN_MAPPINGS = mapOf(
        "麦当劳" to "ic_mcdonalds",
        "肯德基" to "ic_kfc", "KFC" to "ic_kfc",
        "瑞幸" to "ic_luckin", "喜茶" to "ic_heytea",
        "星巴克" to "ic_starbucks", "霸王茶姬" to "ic_chagee",
        "古茗" to "ic_goodme", "蜜雪冰城" to "ic_mixue"
    )

    private const val MIN_ICON_SIZE = 224

    data class IconMapping(val iconPath: String, val keywords: String)

    fun resolveCustomIconBitmap(context: Context, brandName: String?): Bitmap? {
        if (brandName.isNullOrBlank()) return null
        val mappings = getCustomMappings(context)
        for (mapping in mappings) {
            if (mapping.iconPath.isEmpty()) continue
            val keywordList = mapping.keywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (keywordList.any { brandName.contains(it, ignoreCase = true) }) {
                val file = File(mapping.iconPath)
                if (file.exists()) {
                    return BitmapFactory.decodeFile(file.absolutePath)
                }
            }
        }
        return null
    }

    fun resolveBuiltinFallbackResId(context: Context, brandName: String?, orderType: String?): Int {
        if (!brandName.isNullOrBlank()) {
            val builtinResName = BUILTIN_MAPPINGS[brandName]
            if (builtinResName != null) {
                val resId = context.resources.getIdentifier(builtinResName, "drawable", context.packageName)
                if (resId != 0) return resId
            }
        }
        return when (orderType) {
            "饮品" -> R.drawable.ic_drink
            "快递" -> R.drawable.ic_package
            else -> R.drawable.ic_restaurant
        }
    }

    fun saveCustomIcon(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val width = original.width
            val height = original.height
            val scale = maxOf(
                MIN_ICON_SIZE.toFloat() / width,
                MIN_ICON_SIZE.toFloat() / height,
                1f
            )
            val scaled = if (scale > 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (width * scale).toInt(),
                    (height * scale).toInt(),
                    true
                )
            } else {
                original
            }

            val iconDir = File(context.filesDir, "custom_icons").apply { mkdirs() }
            val filename = "icon_${System.currentTimeMillis()}.png"
            val file = File(iconDir, filename)

            FileOutputStream(file).use { fos ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            if (scaled !== original) scaled.recycle()
            original.recycle()

            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deleteCustomIcon(iconPath: String) {
        val file = File(iconPath)
        if (file.exists()) file.delete()
    }

    fun getCustomMappings(context: Context): List<IconMapping> {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_brand_icons", "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonStr)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                IconMapping(
                    iconPath = obj.optString("iconPath", ""),
                    keywords = obj.optString("keywords", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomMappings(context: Context, mappings: List<IconMapping>) {
        val jsonArray = JSONArray()
        for (mapping in mappings) {
            val obj = JSONObject().apply {
                put("iconPath", mapping.iconPath)
                put("keywords", mapping.keywords)
            }
            jsonArray.put(obj)
        }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("custom_brand_icons", jsonArray.toString()).apply()
    }
}
