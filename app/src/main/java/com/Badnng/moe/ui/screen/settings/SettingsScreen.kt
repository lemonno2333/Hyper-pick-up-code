package com.Badnng.moe.ui.screen.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.R
import com.Badnng.moe.service.CaptureTileService
import com.Badnng.moe.ui.component.SettingsListItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

enum class SettingsPage {
    Main, Preference, Permission, Screenshot, KeepAlive, Storage, About, Sponsor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onSubPageStatusChange: (Boolean) -> Unit = {}
) {
    var currentPage by remember { mutableStateOf(SettingsPage.Main) }
    var previousPage by remember { mutableStateOf(SettingsPage.Main) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var isPredictiveBackInProgress by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(currentPage) {
        onSubPageStatusChange(currentPage != SettingsPage.Main)
        if (currentPage != SettingsPage.Main) previousPage = currentPage
    }

    PredictiveBackHandler(enabled = currentPage != SettingsPage.Main) { backEvent: Flow<BackEventCompat> ->
        isPredictiveBackInProgress = true
        try {
            backEvent.collect { event ->
                backProgress = event.progress
                backSwipeEdge = event.swipeEdge
            }
            performHaptic()
            currentPage = SettingsPage.Main
        } catch (e: CancellationException) {
            currentPage = previousPage
        } finally {
            isPredictiveBackInProgress = false
            backProgress = 0f
        }
    }

    val currentScale = if (isPredictiveBackInProgress) 1f - (backProgress * 0.08f) else 1f
    val currentTranslationX = if (isPredictiveBackInProgress) {
        val multiplier = if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
        backProgress * 100f * multiplier
    } else 0f
    val currentCornerRadius = if (isPredictiveBackInProgress) (backProgress * 32).dp else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        MainSettingsList(onNavigate = { performHaptic(); currentPage = it })

        AnimatedVisibility(
            visible = currentPage != SettingsPage.Main,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            val displayPage = if (currentPage != SettingsPage.Main) currentPage else previousPage
            val title = when (displayPage) {
                SettingsPage.Preference -> "偏好设置"
                SettingsPage.Permission -> "权限与保活"
                SettingsPage.Screenshot -> "截图方式"
                SettingsPage.KeepAlive -> "保活设置"
                SettingsPage.Storage -> "清理空间"
                SettingsPage.About -> "关于"
                SettingsPage.Sponsor -> "赞助"
                else -> ""
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        translationX = currentTranslationX
                        shape = RoundedCornerShape(currentCornerRadius)
                        clip = true
                    }
                    .border(
                        width = if (isPredictiveBackInProgress) 1.dp else 0.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = backProgress),
                        shape = RoundedCornerShape(currentCornerRadius)
                    )
                    .background(MaterialTheme.colorScheme.background)
            ) {
                SubPage(
                    title = title,
                    page = displayPage,
                    performHaptic = performHaptic,
                    onNavigate = { performHaptic(); currentPage = it },
                    onBack = {
                        performHaptic()
                        currentPage = SettingsPage.Main
                    }
                )
            }
        }
    }
}

@Composable
fun MainSettingsList(onNavigate: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "设置", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(24.dp))
        SettingsListItem(title = "偏好设置", description = "管理自行习惯的设置", onClick = { onNavigate(SettingsPage.Preference) })
        SettingsListItem(title = "权限与保活", description = "管理权限和防止系统清理后台", onClick = { onNavigate(SettingsPage.Permission) })
        SettingsListItem(title = "截图方式", description = "管理App截图的方式", onClick = { onNavigate(SettingsPage.Screenshot) })

        SettingsListItem(
            title = "添加到控制中心",
            description = "将「截图识别」磁贴添加到控制中心快捷栏",
            onClick = { performHaptic(); requestAddTile(context) }
        )

        SettingsListItem(title = "清理空间", description = "管理App占用的缓存与截图空间", onClick = { onNavigate(SettingsPage.Storage) })
        SettingsListItem(title = "关于", description = "应用信息与开源许可", onClick = { onNavigate(SettingsPage.About) })
        SettingsListItem(title = "赞助", description = "支持项目持续更新", onClick = { onNavigate(SettingsPage.Sponsor) })
        Spacer(modifier = Modifier.height(100.dp))
    }
}

private fun requestAddTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE) as StatusBarManager
        statusBarManager.requestAddTileService(
            ComponentName(context, CaptureTileService::class.java),
            "截图识别",
            Icon.createWithResource(context, R.drawable.note),
            {}, {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPage(
    title: String,
    page: SettingsPage,
    performHaptic: () -> Unit,
    onNavigate: (SettingsPage) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
        TopAppBar(
            title = { Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        when (page) {
            SettingsPage.Screenshot -> ScreenshotSettingsContent(performHaptic)
            SettingsPage.Permission -> PermissionSettingsContent(performHaptic)
            SettingsPage.Preference -> PreferenceSettingsContent(performHaptic, onNavigate)
            SettingsPage.KeepAlive -> KeepAliveSettingsContent(performHaptic)
            SettingsPage.Storage -> StorageSettingsContent(performHaptic, prefs)
            SettingsPage.About -> AboutSettingsContent(performHaptic)
            SettingsPage.Sponsor -> SponsorSettingsContent()
            SettingsPage.Main -> {}
        }
    }
}