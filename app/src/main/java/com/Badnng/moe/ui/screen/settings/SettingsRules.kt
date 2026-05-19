package com.Badnng.moe.ui.screen.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.rules.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RulesSettingsContent(performHaptic: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rules by remember { mutableStateOf(RecognitionRuleEngine.rules) }
    var onlineSources by remember { mutableStateOf(emptyList<OnlineRuleSource>()) }

    var localExpanded by remember { mutableStateOf(true) }
    var onlineExpanded by remember { mutableStateOf(true) }

    var showAddSourceDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<OnlineRuleSource?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    // 自动更新计时器（分钟）
    var autoUpdateCountdown by remember { mutableIntStateOf(0) }
    var isAutoUpdating by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                    }
                    val result = RecognitionRuleEngine.importFromJson(json)
                    result.fold(
                        onSuccess = { importedRules ->
                            RecognitionRuleEngine.saveLocalRules(importedRules)
                            rules = RecognitionRuleEngine.rules
                            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { os ->
                            os.write(RecognitionRuleEngine.exportToJson(rules).toByteArray())
                        }
                    }
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        onlineSources = RecognitionRuleEngine.loadOnlineSources()
    }

    // 自动更新计时器
    LaunchedEffect(isAutoUpdating) {
        if (isAutoUpdating) {
            while (autoUpdateCountdown > 0) {
                delay(60_000L) // 每分钟更新一次
                autoUpdateCountdown--
                if (autoUpdateCountdown <= 0) {
                    isAutoUpdating = false
                    // 执行自动更新
                    onlineSources.filter { it.enabled }.forEach { source ->
                        val updater = RuleOnlineUpdater(context)
                        val result = updater.fetchAndSaveSource(source)
                        result.fold(
                            onSuccess = { (updated, newSource) ->
                                val newSources = onlineSources.toMutableList().apply {
                                    val idx = indexOfFirst { it.id == source.id }
                                    if (idx >= 0) set(idx, newSource)
                                }
                                RecognitionRuleEngine.saveOnlineSources(newSources)
                                onlineSources = newSources
                                if (updated) {
                                    RecognitionRuleEngine.reload(context)
                                    rules = RecognitionRuleEngine.rules
                                }
                            },
                            onFailure = { /* 忽略错误 */ }
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val bottomInsets = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = bottomInsets + 16.dp)
        ) {
            // 本地规则
            item {
                SectionCard(
                    title = "本地规则",
                    subtitle = "${rules.brands.drink.size + rules.brands.food.size + rules.brands.express.size} 条规则",
                    expanded = localExpanded,
                    onToggle = { performHaptic(); localExpanded = !localExpanded }
                ) {
                    // 内置默认规则
                    RuleRow(
                        name = "内置默认规则",
                        count = rules.brands.drink.size + rules.brands.food.size + rules.brands.express.size,
                        details = listOf(
                            "饮品品牌" to rules.brands.drink.size,
                            "餐食品牌" to rules.brands.food.size,
                            "快递关键词" to rules.brands.express.size,
                            "取件码触发词" to rules.codeExtraction.express.triggerKeywords.size,
                            "取餐码触发词" to rules.codeExtraction.food.triggerKeywords.size,
                            "首页关键词" to rules.homepageDetection.keywords.size,
                            "OCR 纠错" to rules.textCleaning.corrections.size
                        ),
                        performHaptic = performHaptic
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 本地自定义规则
                    RuleRow(
                        name = "本地自定义规则",
                        count = null,
                        details = null,
                        performHaptic = performHaptic,
                        expandable = false
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("从文件导入") {
                                performHaptic()
                                importLauncher.launch(arrayOf("application/json", "text/plain"))
                            }
                            ActionButton("导出到文件") {
                                performHaptic()
                                exportLauncher.launch("recognition_rules.json")
                            }
                            ActionButton("恢复默认", isDestructive = true) {
                                performHaptic()
                                showResetDialog = true
                            }
                        }
                    }
                }
            }

            // 在线规则
            item {
                SectionCard(
                    title = "在线规则",
                    subtitle = if (isAutoUpdating) "自动更新中 ${autoUpdateCountdown}分钟" else "${onlineSources.size} 个规则源",
                    expanded = onlineExpanded,
                    onToggle = { performHaptic(); onlineExpanded = !onlineExpanded }
                ) {
                    if (onlineSources.isEmpty()) {
                        Text(
                            text = "暂无在线规则源",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            onlineSources.forEachIndexed { index, source ->
                                OnlineSourceRow(
                                    source = source,
                                    performHaptic = performHaptic,
                                    onToggle = { enabled ->
                                        scope.launch {
                                            val updated = source.copy(enabled = enabled)
                                            val newSources = onlineSources.toMutableList().apply { set(index, updated) }
                                            RecognitionRuleEngine.saveOnlineSources(newSources)
                                            onlineSources = newSources
                                        }
                                    },
                                    onUpdate = {
                                        scope.launch {
                                            val updater = RuleOnlineUpdater(context)
                                            val result = updater.fetchAndSaveSource(source)
                                            result.fold(
                                                onSuccess = { (updated, newSource) ->
                                                    val newSources = onlineSources.toMutableList().apply { set(index, newSource) }
                                                    RecognitionRuleEngine.saveOnlineSources(newSources)
                                                    onlineSources = newSources
                                                    if (updated) {
                                                        RecognitionRuleEngine.reload(context)
                                                        rules = RecognitionRuleEngine.rules
                                                        Toast.makeText(context, "更新成功", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "规则已是最新", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                onFailure = { e ->
                                                    Toast.makeText(context, "更新失败: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    },
                                    onEdit = { performHaptic(); editingSource = source },
                                    onDelete = {
                                        performHaptic()
                                        scope.launch {
                                            val newSources = onlineSources.toMutableList().apply { removeAt(index) }
                                            RecognitionRuleEngine.saveOnlineSources(newSources)
                                            onlineSources = newSources
                                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { performHaptic(); showAddSourceDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("添加规则源")
                        }

                        OutlinedButton(
                            onClick = {
                                performHaptic()
                                if (isAutoUpdating) {
                                    isAutoUpdating = false
                                    autoUpdateCountdown = 0
                                } else {
                                    isAutoUpdating = true
                                    autoUpdateCountdown = 30 // 默认30分钟
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isAutoUpdating) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = if (isAutoUpdating) ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Icon(
                                if (isAutoUpdating) Icons.Default.Stop else Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isAutoUpdating) "停止更新" else "自动更新")
                        }
                    }
                }
            }

            // 版本信息
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "规则版本: ${rules.schemaVersion} | 更新时间: ${rules.updatedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 添加/编辑规则源对话框
    if (showAddSourceDialog || editingSource != null) {
        OnlineSourceDialog(
            source = editingSource,
            onDismiss = {
                performHaptic()
                showAddSourceDialog = false
                editingSource = null
            },
            onSave = { newSource ->
                scope.launch {
                    val newSources = if (editingSource != null) {
                        onlineSources.map { if (it.id == newSource.id) newSource else it }
                    } else {
                        onlineSources + newSource
                    }
                    RecognitionRuleEngine.saveOnlineSources(newSources)
                    onlineSources = newSources
                    showAddSourceDialog = false
                    editingSource = null
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 恢复默认对话框
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认规则") },
            text = { Text("确定要删除所有自定义规则并恢复默认值吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        performHaptic()
                        scope.launch {
                            RecognitionRuleEngine.deleteLocalRules(context)
                            rules = RecognitionRuleEngine.rules
                            showResetDialog = false
                            Toast.makeText(context, "已恢复默认规则", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { performHaptic(); showResetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun RuleRow(
    name: String,
    count: Int?,
    details: List<Pair<String, Int>>?,
    performHaptic: () -> Unit,
    expandable: Boolean = true,
    content: @Composable (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        performHaptic()
                        if (expandable) expanded = !expanded
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (count != null) {
                        Text(
                            text = "$count 条",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                if (expandable) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 展开内容（不计入触摸区域）
            AnimatedVisibility(
                visible = expanded && expandable,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    details?.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$value",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 非展开内容（如按钮）
            if (!expandable && content != null) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun OnlineSourceRow(
    source: OnlineRuleSource,
    performHaptic: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val lastUpdatedText = if (source.lastUpdated > 0) dateFormat.format(Date(source.lastUpdated)) else "从未更新"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = source.url,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                    Text(
                        text = "${source.updateIntervalMinutes}分钟 | $lastUpdatedText",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = source.enabled,
                    onCheckedChange = { performHaptic(); onToggle(it) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { performHaptic(); onUpdate() }) {
                    Text("更新", fontSize = 12.sp)
                }
                TextButton(onClick = { performHaptic(); onEdit() }) {
                    Text("编辑", fontSize = 12.sp)
                }
                TextButton(
                    onClick = { performHaptic(); onDelete() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = if (isDestructive) ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ) else ButtonDefaults.outlinedButtonColors()
    ) {
        Text(text)
    }
}

@Composable
private fun OnlineSourceDialog(
    source: OnlineRuleSource?,
    onDismiss: () -> Unit,
    onSave: (OnlineRuleSource) -> Unit
) {
    var name by remember { mutableStateOf(source?.name ?: "") }
    var url by remember { mutableStateOf(source?.url ?: "") }
    var updateInterval by remember(source?.id) { mutableStateOf((source?.updateIntervalMinutes ?: 1440).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source != null) "编辑规则源" else "添加规则源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如: 社区规则包") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com/rules.json") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = updateInterval,
                    onValueChange = { updateInterval = it.filter { c -> c.isDigit() } },
                    label = { Text("更新间隔 (分钟)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        val interval = maxOf(1, updateInterval.toIntOrNull() ?: 1440)
                        val newSource = if (source != null) {
                            source.copy(name = name.trim(), url = url.trim(), updateIntervalMinutes = interval)
                        } else {
                            OnlineRuleSource(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                url = url.trim(),
                                updateIntervalMinutes = interval
                            )
                        }
                        onSave(newSource)
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
