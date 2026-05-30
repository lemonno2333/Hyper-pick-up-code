package com.Badnng.moe.ui.screen.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.Badnng.moe.service.NotificationListenerRecognitionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NotificationAppsSettingsContent(performHaptic: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    var appList by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var enabledApps by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = NotificationListenerRecognitionService.getAllInstalledApps(context)
                .map { it.packageName to it.label }
            val enabled = NotificationListenerRecognitionService.getEnabledApps(context)
            appList = apps
            enabledApps = enabled
            isLoading = false
        }
    }

    val filteredApps = remember(appList, searchText) {
        if (searchText.isBlank()) appList
        else appList.filter { (pkg, label) ->
            label.contains(searchText, ignoreCase = true) ||
                    pkg.contains(searchText, ignoreCase = true)
        }
    }

    val activeApps = filteredApps.filter { (pkg, _) -> enabledApps[pkg] == true }
    val inactiveApps = filteredApps.filter { (pkg, _) -> enabledApps[pkg] != true }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            InfiniteProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = 16.dp + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
            )
        ) {
            // 搜索框
            item {
                SearchBar(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    inputField = {
                        InputField(
                            query = searchText,
                            onQueryChange = { searchText = it },
                            onSearch = { expanded = false },
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            label = "搜索应用名称或包名"
                        )
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    // 搜索建议区域（可选）
                }
            }

            // 提示卡片 - 和权限与保活一样的样式
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "选择需要监听通知的应用，开启后将自动识别这些应用通知中的取件码和取餐码",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 已启用
            if (activeApps.isNotEmpty()) {
                item {
                    SmallTitle(text = "已启用 (${activeApps.size})")
                }
                items(activeApps, key = { it.first }) { (pkg, label) ->
                    AppToggleItem(
                        context = context,
                        packageName = pkg,
                        label = label,
                        enabled = true,
                        onToggle = { newEnabled ->
                            performHaptic()
                            NotificationListenerRecognitionService.setAppEnabled(context, pkg, newEnabled)
                            enabledApps = NotificationListenerRecognitionService.getEnabledApps(context)
                        }
                    )
                }
            }

            // 未启用
            if (inactiveApps.isNotEmpty()) {
                item {
                    SmallTitle(text = "未启用 (${inactiveApps.size})")
                }
                items(inactiveApps, key = { it.first }) { (pkg, label) ->
                    AppToggleItem(
                        context = context,
                        packageName = pkg,
                        label = label,
                        enabled = false,
                        onToggle = { newEnabled ->
                            performHaptic()
                            NotificationListenerRecognitionService.setAppEnabled(context, pkg, newEnabled)
                            enabledApps = NotificationListenerRecognitionService.getEnabledApps(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppToggleItem(
    context: android.content.Context,
    packageName: String,
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var icon by remember { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            icon = try { context.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }
        }
    }

    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon?.let {
                Image(
                    bitmap = it.toBitmap(192, 192).asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.size(40.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = packageName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
