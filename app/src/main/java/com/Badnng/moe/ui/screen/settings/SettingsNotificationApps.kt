package com.Badnng.moe.ui.screen.settings

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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

@Composable
fun NotificationAppsSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current
    var appList by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var enabledApps by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Md3LoadingIndicator(
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = "选择需要监听通知的应用，开启后将自动识别这些应用通知中的取件码和取餐码",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 18.sp
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("搜索应用名称或包名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp)
                    )
                }

                if (activeApps.isNotEmpty()) {
                    item {
                        Text(
                            text = "已启用 (${activeApps.size})",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
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

                if (inactiveApps.isNotEmpty()) {
                    item {
                        Text(
                            text = "未启用 (${inactiveApps.size})",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
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
}

@Composable
private fun Md3LoadingIndicator(
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val c = center

        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = radius,
            center = c,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )

        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            ),
            topLeft = androidx.compose.ui.geometry.Offset(c.x - radius, c.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        )

        drawCircle(
            color = color.copy(alpha = scale),
            radius = radius * 0.25f * scale,
            center = c
        )
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

    Surface(
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = packageName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
