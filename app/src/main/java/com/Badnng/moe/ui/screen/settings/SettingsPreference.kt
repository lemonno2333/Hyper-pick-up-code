package com.Badnng.moe.ui.screen.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.R
import com.Badnng.moe.ui.component.CaptureModeItem
import com.Badnng.moe.ui.component.ChoiceChip
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.PreferenceSwitchItem

@Composable
fun PreferenceSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 700
    val isFoldableDevice = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    }
    var navAlignment by remember { mutableStateOf(prefs.getString("nav_alignment", "center") ?: "center") }
    var largeScreenNavAdaptiveEnabled by remember {
        mutableStateOf(prefs.getBoolean("large_screen_nav_adaptive_enabled", true))
    }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean("monet_enabled", true)) }
    var amoledPureBlack by remember { mutableStateOf(prefs.getBoolean("amoled_pure_black", false)) }
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var showOnboardingOnNextLaunch by remember { mutableStateOf(prefs.getBoolean("show_onboarding_on_next_launch", false)) }
    var customHue by remember { mutableFloatStateOf(260f) }
    var selectedColorInt by remember { mutableIntStateOf(prefs.getInt("theme_color", Color(0xFF6750A4).toArgb())) }
    var networkUpdateEnabled by remember { mutableStateOf(prefs.getBoolean("network_update_enabled", false)) }
    var updateChannel by remember { mutableStateOf(prefs.getString("update_channel", "stable") ?: "stable") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        PreferenceSection(title = "底栏位置") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLargeScreen || isFoldableDevice) {
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    ) {
                        PreferenceSwitchItem(
                            title = "底栏自适应",
                            description = "根据主页纵向滑动区域自动切换底栏到左/中/右（仅 大屏/折叠 设备生效）",
                            checked = largeScreenNavAdaptiveEnabled,
                            onCheckedChange = {
                                largeScreenNavAdaptiveEnabled = it
                                prefs.edit().putBoolean("large_screen_nav_adaptive_enabled", it).apply()
                                performHaptic()
                            }
                        )
                    }
                }
                AnimatedVisibility(
                    visible = !(isLargeScreen || isFoldableDevice) || !largeScreenNavAdaptiveEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("left" to "靠左", "center" to "居中", "right" to "靠右").forEach { (key, label) ->
                            ChoiceChip(
                                label = label,
                                selected = navAlignment == key,
                                onClick = {
                                    performHaptic()
                                    navAlignment = key
                                    prefs.edit().putString("nav_alignment", key).apply()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        PreferenceSection(title = "交互反馈") {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                PreferenceSwitchItem(
                    title = "震动反馈",
                    description = "开启后点击按钮、切换分类时会有触感反馈",
                    checked = hapticEnabled,
                    onCheckedChange = {
                        hapticEnabled = it
                        prefs.edit().putBoolean("haptic_enabled", it).apply()
                        performHaptic()
                    }
                )
            }
        }

        PreferenceSection(title = "外观设置") {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                PreferenceSwitchItem(
                    title = "莫奈取色 (Dynamic Color)",
                    description = "开启后主题色将跟随系统壁纸自动变化",
                    checked = monetEnabled,
                    onCheckedChange = {
                        performHaptic()
                        monetEnabled = it
                        prefs.edit().putBoolean("monet_enabled", it).apply()
                    }
                )
            }
        }
        AnimatedVisibility(visible = !monetEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { PreferenceSection(title = "自定义主题色") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("滑动调节色相", style = MaterialTheme.typography.bodySmall); Slider(value = customHue, onValueChange = { customHue = it }, valueRange = 0f..360f, modifier = Modifier.fillMaxWidth()); val previewColor = remember(customHue) { Color.hsv(customHue, 0.7f, 0.9f) }; Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(previewColor).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)); Button(onClick = { performHaptic(); selectedColorInt = previewColor.toArgb(); prefs.edit().putInt("theme_color", selectedColorInt).apply() }, shape = RoundedCornerShape(15.dp), modifier = Modifier.weight(1f).height(56.dp)) { Text("应用颜色") } } ; Text("MD3 建议色", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)); val md3Colors = listOf(0xFF6750A4, 0xFF006A60, 0xFF984061, 0xFF005AC1, 0xFF605D62, 0xFF3B6939); Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { md3Colors.forEach { colorLong -> val color = Color(colorLong); Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(color).border(width = if (selectedColorInt == color.toArgb()) 3.dp else 0.dp, color = MaterialTheme.colorScheme.primary, shape = CircleShape).clickable { performHaptic(); selectedColorInt = color.toArgb(); prefs.edit().putInt("theme_color", selectedColorInt).apply() }) } } } } }
        PreferenceSection(title = "显示模式") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统").forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                performHaptic()
                                themeMode = key
                                prefs.edit().putString("theme_mode", key).apply()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == key,
                            onClick = {
                                performHaptic()
                                themeMode = key
                                prefs.edit().putString("theme_mode", key).apply()
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontSize = 16.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(top = 8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    PreferenceSwitchItem(
                        title = "Amoled 纯黑深色",
                        description = "仅在深色模式生效，让背景和表面接近纯黑",
                        checked = amoledPureBlack,
                        onCheckedChange = {
                            performHaptic()
                            amoledPureBlack = it
                            prefs.edit().putBoolean("amoled_pure_black", it).apply()
                        }
                    )
                }
            }
        }

        PreferenceSection(title = "引导设置") {
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            ) {
                PreferenceSwitchItem(
                    title = "下次启动时打开引导页面",
                    description = "开启后，彻底停止App再启动会显示引导页面，完成引导后自动关闭",
                    checked = showOnboardingOnNextLaunch,
                    onCheckedChange = {
                        performHaptic()
                        showOnboardingOnNextLaunch = it
                        prefs.edit().putBoolean("show_onboarding_on_next_launch", it).apply()
                    }
                )
            }
        }

        PreferenceSection(title = "联网更新") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    PreferenceSwitchItem(
                        title = "联网更新",
                        description = "仅用于检测App新版本并下载，不用于其他用途",
                        checked = networkUpdateEnabled,
                        onCheckedChange = {
                            performHaptic()
                            networkUpdateEnabled = it
                            prefs.edit().putBoolean("network_update_enabled", it).apply()
                        }
                    )
                }

                AnimatedVisibility(
                    visible = networkUpdateEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "更新通道",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        CaptureModeItem(
                            title = "接收正式版更新",
                            description = "只接收稳定版本的更新",
                            selected = updateChannel == "stable",
                            onClick = {
                                performHaptic()
                                updateChannel = "stable"
                                prefs.edit().putString("update_channel", "stable").apply()
                            }
                        )
                        CaptureModeItem(
                            title = "接收测试版更新",
                            description = "接收所有版本的更新，包括测试版",
                            selected = updateChannel == "dev",
                            onClick = {
                                performHaptic()
                                updateChannel = "dev"
                                prefs.edit().putString("update_channel", "dev").apply()
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}
