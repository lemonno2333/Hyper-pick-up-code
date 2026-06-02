package com.Badnng.moe.ui.screen.miuix

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.Icons
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import com.Badnng.moe.ui.screen.rememberSaveablePagerState
import com.Badnng.moe.ui.screen.settings.SettingsPage
import com.Badnng.moe.viewmodel.OrderViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults

// 顶层路由
sealed interface HomeRoute : NavKey {
    data object Main : HomeRoute
    data class SettingsSubPage(val page: SettingsPage) : HomeRoute
}

@Composable
fun MiuixHomeScreen(
    modifier: Modifier = Modifier,
    intentToProcess: Intent? = null,
    pagerState: androidx.compose.foundation.pager.PagerState? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
    var useFloatingNavBar by remember { mutableStateOf(prefs.getBoolean("use_floating_nav_bar", false)) }
    var navAlignment by remember { mutableStateOf(prefs.getString("nav_alignment", "center") ?: "center") }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "haptic_enabled" -> hapticEnabled = p.getBoolean(key, true)
                "use_floating_nav_bar" -> useFloatingNavBar = p.getBoolean(key, false)
                "nav_alignment" -> navAlignment = p.getString(key, "center") ?: "center"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val backStack = remember { mutableStateListOf<HomeRoute>(HomeRoute.Main) }

    val homeEntryProvider = remember(backStack) {
        entryProvider<NavKey> {
            entry<HomeRoute.Main> {
                MiuixMainContent(
                    modifier = modifier,
                    intentToProcess = intentToProcess,
                    hapticEnabled = hapticEnabled,
                    useFloatingNavBar = useFloatingNavBar,
                    navAlignment = navAlignment,
                    externalPagerState = pagerState,
                    onNavigateToSettingsSubPage = { page ->
                        backStack.add(HomeRoute.SettingsSubPage(page))
                    }
                )
            }
            entry<HomeRoute.SettingsSubPage> { route ->
                MiuixSettingsSubPageDirect(
                    page = route.page,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigate = { page ->
                        backStack.add(HomeRoute.SettingsSubPage(page))
                    }
                )
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = homeEntryProvider,
    )

    NavDisplay(
        entries = entries,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        transitionEffects = NavDisplayTransitionEffects.Default,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MiuixMainContent(
    modifier: Modifier,
    intentToProcess: Intent?,
    hapticEnabled: Boolean,
    useFloatingNavBar: Boolean,
    navAlignment: String = "center",
    externalPagerState: androidx.compose.foundation.pager.PagerState? = null,
    onNavigateToSettingsSubPage: (SettingsPage) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val pagerState = externalPagerState ?: rememberSaveablePagerState(pageCount = { 3 })
    val currentPage by remember { androidx.compose.runtime.derivedStateOf { pagerState.currentPage } }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val viewModel: OrderViewModel = viewModel()

    var isManaging by remember { mutableStateOf(false) }
    var isScrollingDown by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val activity = context as? android.app.Activity

    // 主页面按返回键时，从最近任务移除卡片
    androidx.activity.compose.BackHandler(enabled = !isEditMode && !isManaging) {
        activity?.finishAndRemoveTask()
    }

    val performHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val targetBottomBarBias = when (navAlignment) {
        "left" -> -1f
        "right" -> 1f
        else -> 0f
    }
    val animatedBottomBarBias by animateFloatAsState(
        targetValue = targetBottomBarBias,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 260f),
        label = "bottomBarBias"
    )

    // 模糊效果
    val backdrop = com.Badnng.moe.ui.miuix.rememberMiuixBackdrop()
    val blurEnabled = backdrop != null

    Box(modifier = modifier.fillMaxSize()) {
    // 将 backdrop 应用到 Scaffold，这样底栏才能采样到背后的内容
    val scaffoldModifier = if (backdrop != null) {
        Modifier.fillMaxSize().layerBackdrop(backdrop)
    } else {
        Modifier.fillMaxSize()
    }

    Scaffold(
        modifier = scaffoldModifier,
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1 // 预加载相邻页面，减少切换时重组
        ) { page ->
            androidx.compose.runtime.key(page) {
                when (page) {
                    0 -> MiuixCaptureScreen(
                        padding = innerPadding,
                        onScrollStateChange = { isScrollingDown = it },
                        onEditModeChange = { isEditMode = it },
                        onAddClick = { showBottomSheet = true },
                        navAlignment = navAlignment,
                        useFloatingNavBar = useFloatingNavBar
                    )
                    1 -> MiuixRulesScreen(padding = innerPadding)
                    2 -> MiuixSettingsScreen(
                        padding = innerPadding,
                        onNavigateToSubPage = onNavigateToSettingsSubPage
                    )
                }
            }
        }
    }

    // 添加记录底部弹窗
    val addOrderViewModel: OrderViewModel = viewModel()
    com.Badnng.moe.ui.component.AddOrderBottomSheet(
        show = showBottomSheet,
        viewModel = addOrderViewModel,
        onDismiss = { showBottomSheet = false }
    )

    // 底栏：覆盖在 Scaffold 上方，支持 Start/Center/End 位置
    // 标准底栏（非悬浮）
    AnimatedVisibility(
        visible = !isEditMode && !isManaging && !useFloatingNavBar,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        val barColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface
        Box(
            modifier = if (blurEnabled && backdrop != null) {
                Modifier.fillMaxWidth().textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 25f,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(
                            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.8f)),
                        ),
                    ),
                )
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            NavigationBar(modifier = Modifier.fillMaxWidth(), color = barColor) {
                NavigationBarItem(
                    selected = currentPage == 0,
                    onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    icon = Icons.Default.Home,
                    label = "主页"
                )
                NavigationBarItem(
                    selected = currentPage == 1,
                    onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    icon = MiuixIcons.Regular.Edit,
                    label = "规则"
                )
                NavigationBarItem(
                    selected = currentPage == 2,
                    onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    icon = MiuixIcons.Regular.Settings,
                    label = "设置"
                )
            }
        }
    }

    // 悬浮底栏
    AnimatedVisibility(
        visible = !isEditMode && !isManaging && useFloatingNavBar,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val barOffsetX = when (navAlignment) {
            "left" -> (-80).dp
            "right" -> 80.dp
            else -> 0.dp
        }
        val floatingBarColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val floatingHighlight = remember(isDark) {
            if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
        }
        val floatingBarModifier = if (blurEnabled && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius),
                blurRadius = 25f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(0.6f)),
                    ),
                ),
                highlight = floatingHighlight,
            )
        } else {
            Modifier
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.offset(x = barOffsetX)) {
                FloatingNavigationBar(
                    modifier = floatingBarModifier,
                    color = floatingBarColor
                ) {
                    FloatingNavigationBarItem(
                        selected = currentPage == 0,
                        onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        icon = Icons.Default.Home,
                        label = "主页"
                    )
                    FloatingNavigationBarItem(
                        selected = currentPage == 1,
                        onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        icon = MiuixIcons.Regular.Edit,
                        label = "规则"
                    )
                    FloatingNavigationBarItem(
                        selected = currentPage == 2,
                        onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        icon = MiuixIcons.Regular.Settings,
                        label = "设置"
                    )
                }
            }
        }
    }
    } // Box
}

@Composable
private fun MiuixSettingsSubPageDirect(
    page: SettingsPage,
    onBack: () -> Unit,
    onNavigate: (SettingsPage) -> Unit = {}
) {
    val title = when (page) {
        SettingsPage.Preference -> "偏好设置"
        SettingsPage.Permission -> "权限与保活"
        SettingsPage.Screenshot -> "截图方式"
        SettingsPage.KeepAlive -> "保活设置"
        SettingsPage.Storage -> "清理空间"
        SettingsPage.About -> "关于"
        SettingsPage.Sponsor -> "赞助"
        SettingsPage.NotificationApps -> "通知识别应用管理"
        SettingsPage.Credits -> "致谢"
        SettingsPage.Main -> ""
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val haptic = LocalHapticFeedback.current
    val performHaptic = {
        if (prefs.getBoolean("haptic_enabled", true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // 模糊效果 - 和示例项目 NavigateTestPage 一致的实现
    val blurSupported = top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if (blurSupported) {
        top.yukonga.miuix.kmp.blur.rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    val blurEnabled = backdrop != null

    if (page == SettingsPage.About) {
        // 关于页面：自包含 Scaffold（照搬示例项目 AboutPage）
        com.Badnng.moe.ui.screen.settings.AboutSettingsContent(
            performHaptic = performHaptic,
            topPadding = 0.dp,
            scrollState = androidx.compose.foundation.rememberScrollState(),
            onNavigateToCredits = { onNavigate(SettingsPage.Credits) },
            onBack = onBack
        )
    } else {
        Scaffold(
            topBar = {
                val topBarColor = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface
                com.Badnng.moe.ui.miuix.MiuixBlurredBar(backdrop = backdrop, blurEnabled = blurEnabled) {
                    TopAppBar(
                        title = title,
                        color = topBarColor,
                        scrollBehavior = topAppBarScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    MiuixIcons.Regular.Back,
                                    contentDescription = "返回"
                                )
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            val scrollState = androidx.compose.foundation.rememberScrollState()
            val topBarHeight = innerPadding.calculateTopPadding()
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                ) {
                    when (page) {
                        SettingsPage.Screenshot -> com.Badnng.moe.ui.screen.settings.ScreenshotSettingsContent(performHaptic, topBarHeight, scrollState)
                        SettingsPage.Permission -> com.Badnng.moe.ui.screen.settings.PermissionSettingsContent(performHaptic, topBarHeight, scrollState)
                        SettingsPage.Preference -> com.Badnng.moe.ui.screen.settings.PreferenceSettingsContent(performHaptic, onNavigate, topBarHeight, scrollState)
                        SettingsPage.KeepAlive -> com.Badnng.moe.ui.screen.settings.KeepAliveSettingsContent(performHaptic, topBarHeight, scrollState)
                        SettingsPage.Storage -> com.Badnng.moe.ui.screen.settings.StorageSettingsContent(performHaptic, prefs, topBarHeight + 26.dp, scrollState)
                        SettingsPage.Sponsor -> com.Badnng.moe.ui.screen.settings.SponsorSettingsContent(topBarHeight, scrollState)
                        SettingsPage.NotificationApps -> com.Badnng.moe.ui.screen.settings.NotificationAppsSettingsContent(performHaptic, topBarHeight + 8.dp)
                        SettingsPage.Credits -> com.Badnng.moe.ui.screen.settings.CreditsSettingsContent(performHaptic, topBarHeight, scrollState)
                        SettingsPage.Main -> {}
                        else -> {}
                    }
                }
            }
        }
    }
}
