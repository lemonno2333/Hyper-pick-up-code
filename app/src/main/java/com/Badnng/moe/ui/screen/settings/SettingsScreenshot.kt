package com.Badnng.moe.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.helper.AccessibilityShortcutHelper
import com.Badnng.moe.helper.RootHelper
import com.Badnng.moe.ui.component.CaptureModeItem
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ScreenshotSettingsContent(performHaptic: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 0.dp, scrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState()) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var captureMode by remember { mutableStateOf(prefs.getString("capture_mode", "media_projection") ?: "media_projection") }
    var volumeKeyShortcutEnabled by remember { mutableStateOf(prefs.getBoolean("volume_key_shortcut_enabled", false)) }
    var mediaProjectionNoPromptEnabled by remember { mutableStateOf(prefs.getBoolean("media_projection_no_prompt_enabled", false)) }
    var shizukuReady by remember { mutableStateOf(false) }
    var rootReady by remember { mutableStateOf(false) }
    val mediaProjectionNoPromptConflictActive = captureMode == "media_projection" && mediaProjectionNoPromptEnabled

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "capture_mode" -> captureMode = p.getString("capture_mode", "media_projection") ?: "media_projection"
                "media_projection_no_prompt_enabled" -> mediaProjectionNoPromptEnabled = p.getBoolean("media_projection_no_prompt_enabled", false)
                "volume_key_shortcut_enabled" -> volumeKeyShortcutEnabled = p.getBoolean("volume_key_shortcut_enabled", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val currentShortcutBackend = remember(captureMode, shizukuReady, rootReady, mediaProjectionNoPromptConflictActive) {
        if (mediaProjectionNoPromptConflictActive) return@remember null
        when (captureMode) {
            "shizuku" -> if (shizukuReady) "shizuku" else null
            "root" -> if (rootReady) "root" else null
            else -> null
        }
    }

    LaunchedEffect(Unit) {
        // Root 不需要高频检测；只检查一次 su 是否存在，避免在未使用时频繁触发 Magisk 提示。
        rootReady = withContext(Dispatchers.IO) { RootHelper.isSuAvailable() }
        while (true) {
            shizukuReady = withContext(Dispatchers.IO) { isShizukuReady() }
            if (!shizukuReady && captureMode == "shizuku") {
                captureMode = "media_projection"
                prefs.edit().putString("capture_mode", "media_projection").apply()
            }
            if (!rootReady && captureMode == "root") {
                captureMode = "media_projection"
                prefs.edit().putString("capture_mode", "media_projection").apply()
            }
            if ((captureMode == "media_projection" || mediaProjectionNoPromptConflictActive) && volumeKeyShortcutEnabled) {
                Thread {
                    if (rootReady) {
                        AccessibilityShortcutHelper.disableServiceWithRoot(context)
                    } else if (shizukuReady) {
                        AccessibilityShortcutHelper.disableServiceWithShizuku(context)
                    }
                }.start()
                volumeKeyShortcutEnabled = false
                prefs.edit().putBoolean("volume_key_shortcut_enabled", false).apply()
            } else if (!shizukuReady && !rootReady && volumeKeyShortcutEnabled) {
                volumeKeyShortcutEnabled = false
                prefs.edit().putBoolean("volume_key_shortcut_enabled", false).apply()
            }
            delay(1500)
        }
    }
    val isMiuix = rememberMiuixStyle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isMiuix) 0.dp else 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(if (isMiuix) 0.dp else 20.dp)
    ) {
        Spacer(Modifier.height(topPadding))
        SmallTitle(text = "截图技术方案")
        MiuixCard(modifier = Modifier.padding(horizontal = if (isMiuix) 12.dp else 0.dp)) {
            OverlayDropdownPreference(
                title = "截图技术方案",
                entries = listOf(
                    top.yukonga.miuix.kmp.basic.DropdownEntry(
                        items = listOf(
                            top.yukonga.miuix.kmp.basic.DropdownItem(
                                text = "共享屏幕",
                                selected = captureMode == "media_projection",
                                onClick = {
                                    performHaptic()
                                    captureMode = "media_projection"
                                    prefs.edit().putString("capture_mode", "media_projection").apply()
                                    if (volumeKeyShortcutEnabled) {
                                        Thread {
                                            if (rootReady) {
                                                AccessibilityShortcutHelper.disableServiceWithRoot(context)
                                            } else if (shizukuReady) {
                                                AccessibilityShortcutHelper.disableServiceWithShizuku(context)
                                            }
                                        }.start()
                                        volumeKeyShortcutEnabled = false
                                        prefs.edit().putBoolean("volume_key_shortcut_enabled", false).apply()
                                    }
                                }
                            ),
                            top.yukonga.miuix.kmp.basic.DropdownItem(
                                text = "纯 Shizuku 模式",
                                selected = captureMode == "shizuku",
                                enabled = shizukuReady,
                                onClick = {
                                    performHaptic()
                                    captureMode = "shizuku"
                                    prefs.edit().putString("capture_mode", "shizuku").apply()
                                }
                            ),
                            top.yukonga.miuix.kmp.basic.DropdownItem(
                                text = "Root 免授权",
                                selected = captureMode == "root",
                                enabled = rootReady,
                                onClick = {
                                    performHaptic()
                                    captureMode = "root"
                                    prefs.edit().putString("capture_mode", "root").apply()
                                }
                            )
                        )
                    )
                )
            )
        }

        SmallTitle(text = "Shizuku 相关设置")
        val noPromptEnabled = captureMode == "media_projection" && shizukuReady
        MiuixCard(
            onClick = {
                if (noPromptEnabled) {
                    performHaptic()
                    val targetEnabled = !mediaProjectionNoPromptEnabled
                    val success = AccessibilityShortcutHelper.setProjectMediaAppOpsWithShizuku(context, targetEnabled)
                    if (success) {
                        mediaProjectionNoPromptEnabled = targetEnabled
                        prefs.edit().putBoolean("media_projection_no_prompt_enabled", targetEnabled).apply()
                        if (targetEnabled && volumeKeyShortcutEnabled) {
                            Thread {
                                if (rootReady) {
                                    AccessibilityShortcutHelper.disableServiceWithRoot(context)
                                } else if (shizukuReady) {
                                    AccessibilityShortcutHelper.disableServiceWithShizuku(context)
                                }
                            }.start()
                            volumeKeyShortcutEnabled = false
                            prefs.edit().putBoolean("volume_key_shortcut_enabled", false).apply()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).alpha(if (noPromptEnabled) 1f else 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "共享屏幕免授权弹窗",
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            captureMode != "media_projection" -> "仅在共享屏幕模式下可用"
                            !shizukuReady -> "需要 Shizuku 运行并授权后才可开启"
                            else -> "开启后将跳过共享屏幕授权弹窗（与音量键快捷触发互斥）"
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Switch(
                    checked = mediaProjectionNoPromptEnabled,
                    onCheckedChange = null,
                    enabled = noPromptEnabled
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val shortcutEnabled = currentShortcutBackend != null
        MiuixCard(
            onClick = {
                if (shortcutEnabled) {
                    performHaptic()
                    val targetEnabled = !volumeKeyShortcutEnabled
                    if (targetEnabled) {
                        prefs.edit().putBoolean("skip_next_accessibility_connect", true).apply()
                    } else {
                        prefs.edit().putBoolean("skip_next_accessibility_connect", false).apply()
                    }
                    val success = when (currentShortcutBackend) {
                        "root" -> if (targetEnabled) {
                            AccessibilityShortcutHelper.configureShortcutWithRoot(context)
                        } else {
                            AccessibilityShortcutHelper.disableServiceWithRoot(context)
                        }
                        "shizuku" -> if (targetEnabled) {
                            AccessibilityShortcutHelper.configureShortcutWithShizuku(context)
                        } else {
                            AccessibilityShortcutHelper.disableServiceWithShizuku(context)
                        }
                        else -> false
                    }
                    if (success) {
                        volumeKeyShortcutEnabled = targetEnabled
                        prefs.edit().putBoolean("volume_key_shortcut_enabled", targetEnabled).apply()
                    } else {
                        prefs.edit().putBoolean("skip_next_accessibility_connect", false).apply()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).alpha(if (shortcutEnabled) 1f else 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "音量键快捷触发",
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            mediaProjectionNoPromptConflictActive -> "与「共享屏幕免授权弹窗」互斥"
                            captureMode == "root" && rootReady -> "通过 Root 一键配置无障碍快捷方式，启用后可使用音量+ -键快捷触发截图识别"
                            captureMode == "shizuku" && shizukuReady -> "通过 Shizuku 一键配置无障碍快捷方式，启用后可使用音量+ -键快捷触发截图识别"
                            captureMode == "media_projection" -> "请先选择 Root 或 Shizuku 截图方案后再启用"
                            else -> "当前方案不可用，请检查 Root/Shizuku 状态"
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Switch(
                    checked = volumeKeyShortcutEnabled,
                    onCheckedChange = null,
                    enabled = shortcutEnabled
                )
            }
        }
    }
}