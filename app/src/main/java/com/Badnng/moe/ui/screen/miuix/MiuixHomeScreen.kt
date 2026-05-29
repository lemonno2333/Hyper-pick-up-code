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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.Icons
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val viewModel: OrderViewModel = viewModel()

    var isManaging by remember { mutableStateOf(false) }
    var isScrollingDown by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

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

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
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

    // 底栏：覆盖在 Scaffold 上方，支持 Start/Center/End 位置
    // 标准底栏（非悬浮）
    AnimatedVisibility(
        visible = !isEditMode && !isManaging && !useFloatingNavBar,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        NavigationBar(modifier = Modifier.fillMaxWidth()) {
            NavigationBarItem(
                selected = pagerState.currentPage == 0,
                onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                icon = Icons.Default.Home,
                label = "主页"
            )
            NavigationBarItem(
                selected = pagerState.currentPage == 1,
                onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                icon = MiuixIcons.Regular.Edit,
                label = "规则"
            )
            NavigationBarItem(
                selected = pagerState.currentPage == 2,
                onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                icon = MiuixIcons.Regular.Settings,
                label = "设置"
            )
        }
    }

    // 悬浮底栏
    if (!isEditMode && !isManaging && useFloatingNavBar) {
        val barOffsetX = when (navAlignment) {
            "left" -> (-80).dp
            "right" -> 80.dp
            else -> 0.dp
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.offset(x = barOffsetX)) {
                FloatingNavigationBar {
                    FloatingNavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        icon = Icons.Default.Home,
                        label = "主页"
                    )
                    FloatingNavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { performHaptic(); coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        icon = MiuixIcons.Regular.Edit,
                        label = "规则"
                    )
                    FloatingNavigationBarItem(
                        selected = pagerState.currentPage == 2,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val scrollState = androidx.compose.foundation.rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .padding(innerPadding)
        ) {
            when (page) {
                SettingsPage.Screenshot -> com.Badnng.moe.ui.screen.settings.ScreenshotSettingsContent(performHaptic, 0.dp, scrollState)
                SettingsPage.Permission -> com.Badnng.moe.ui.screen.settings.PermissionSettingsContent(performHaptic, 0.dp, scrollState)
                SettingsPage.Preference -> com.Badnng.moe.ui.screen.settings.PreferenceSettingsContent(performHaptic, onNavigate, 0.dp, scrollState)
                SettingsPage.KeepAlive -> com.Badnng.moe.ui.screen.settings.KeepAliveSettingsContent(performHaptic, 0.dp, scrollState)
                SettingsPage.Storage -> com.Badnng.moe.ui.screen.settings.StorageSettingsContent(performHaptic, prefs, 0.dp, scrollState)
                SettingsPage.About -> com.Badnng.moe.ui.screen.settings.AboutSettingsContent(performHaptic, 0.dp, scrollState)
                SettingsPage.Sponsor -> com.Badnng.moe.ui.screen.settings.SponsorSettingsContent(0.dp, scrollState)
                SettingsPage.NotificationApps -> com.Badnng.moe.ui.screen.settings.NotificationAppsSettingsContent(performHaptic, 0.dp)
                SettingsPage.Main -> {}
            }
        }
    }
}
