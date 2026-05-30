package com.Badnng.moe.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.Badnng.moe.ui.LocalAppUi
import com.Badnng.moe.ui.md3eAppUi
import com.Badnng.moe.ui.miuixAppUi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixIndication

@Composable
fun 澎湃记Theme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system")) }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean("monet_enabled", true)) }
    var amoledPureBlack by remember { mutableStateOf(prefs.getBoolean("amoled_pure_black", false)) }
    var seedColorInt by remember { mutableIntStateOf(prefs.getInt("theme_color", Color(0xFF6750A4).toArgb())) }
    var uiStyle by remember { mutableStateOf(prefs.getString("ui_style", "md3e")) }
    var keyColorIndex by remember { mutableIntStateOf(prefs.getInt("key_color_index", 0)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "theme_mode" -> themeMode = p.getString(key, "system")
                "monet_enabled" -> monetEnabled = p.getBoolean(key, true)
                "amoled_pure_black" -> amoledPureBlack = p.getBoolean(key, false)
                "theme_color" -> seedColorInt = p.getInt(key, Color(0xFF6750A4).toArgb())
                "ui_style" -> uiStyle = p.getString(key, "md3e")
                "key_color_index" -> keyColorIndex = p.getInt(key, 0)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    // ─── Miuix Key Color 预设（提前定义，供 Material3 和 Miuix 共用）───
    val miuixKeyColorPresets = listOf(
        null,  // 默认（使用 seedColorInt）
        Color(0xFF1976D2),  // 蓝色
        Color(0xFF7B1FA2),  // 紫色
        Color(0xFFD32F2F),  // 红色
        Color(0xFFFF6F00),  // 橙色
        Color(0xFF388E3C),  // 绿色
        Color(0xFF00838F),  // 青色
    )
    val miuixKeyColor = if (monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        null  // Monet 模式下不使用自定义 key color
    } else if (keyColorIndex > 0 && keyColorIndex < miuixKeyColorPresets.size) {
        miuixKeyColorPresets[keyColorIndex]
    } else {
        Color(seedColorInt)
    }

    // ─── Material3 配色方案（已有屏幕使用） ───
    // Miuix 模式下使用 Miuix 的 keyColor 作为 Material3 的种子色，确保颜色一致
    val materialSeedColor = if (uiStyle == "miuix" && miuixKeyColor != null) {
        miuixKeyColor.toArgb()
    } else {
        seedColorInt
    }
    val baseColorScheme = when {
        monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> ColorGenerator.seedToColorScheme(materialSeedColor, isDark = darkTheme)
    }

    val colorScheme = if (darkTheme && amoledPureBlack) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color.Black
        )
    } else baseColorScheme

    // ─── Miuix ThemeController ───
    val miuixColorSchemeMode = when {
        monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            when (themeMode) {
                "light" -> ColorSchemeMode.MonetLight
                "dark" -> ColorSchemeMode.MonetDark
                else -> ColorSchemeMode.MonetSystem
            }
        }
        else -> when (themeMode) {
            "light" -> ColorSchemeMode.Light
            "dark" -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
    }

    // 使用 remember + keys 重建 ThemeController
    val miuixController = remember(miuixColorSchemeMode, miuixKeyColor, darkTheme) {
        ThemeController(
            colorSchemeMode = miuixColorSchemeMode,
            keyColor = miuixKeyColor,
            isDark = darkTheme
        )
    }

    val appUi = if (uiStyle == "miuix") miuixAppUi else md3eAppUi

    CompositionLocalProvider(LocalAppUi provides appUi) {
        if (uiStyle == "miuix") {
            // Miuix 模式：MiuixTheme 作为根主题
            MiuixTheme(controller = miuixController) {
                // 内层保留 MaterialExpressiveTheme，确保未迁移的 Material3 组件颜色正确
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = Typography,
                ) {
                    // MaterialExpressiveTheme 会覆盖 LocalIndication，需要重新提供 MiuixIndication
                    val indicationColor = MiuixTheme.colorScheme.onBackground
                    val miuixIndication = remember(indicationColor) { MiuixIndication(color = indicationColor) }
                    CompositionLocalProvider(LocalIndication provides miuixIndication) {
                        content()
                    }
                }
            }
        } else {
            MaterialExpressiveTheme(
                colorScheme = colorScheme,
                typography = Typography,
                content = content
            )
        }
    }
}
