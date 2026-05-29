package com.Badnng.moe.ui.component

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.ui.miuix.MiuixCaptureModeItem
import com.Badnng.moe.ui.miuix.MiuixChoiceChip
import com.Badnng.moe.ui.miuix.MiuixPermissionItem
import com.Badnng.moe.ui.miuix.MiuixPreferenceSection
import com.Badnng.moe.ui.miuix.MiuixPreferenceSwitchItem
import com.Badnng.moe.ui.miuix.MiuixSettingsGroup
import com.Badnng.moe.ui.miuix.MiuixSettingsGroupItem
import com.Badnng.moe.ui.miuix.MiuixSettingsGroupSwitchItem
import com.Badnng.moe.ui.miuix.MiuixSettingsListItem

enum class GroupPosition { First, Middle, Last, Single }

private fun groupItemShape(position: GroupPosition, radius: Int = 16): Shape = when (position) {
    GroupPosition.First -> RoundedCornerShape(topStart = radius.dp, topEnd = radius.dp)
    GroupPosition.Last -> RoundedCornerShape(bottomStart = radius.dp, bottomEnd = radius.dp)
    GroupPosition.Single -> RoundedCornerShape(radius.dp)
    GroupPosition.Middle -> RectangleShape
}

@Composable
private fun isMiuixStyle(): Boolean {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var uiStyle by remember { mutableStateOf(prefs.getString("ui_style", "md3e") ?: "md3e") }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "ui_style") uiStyle = p.getString(key, "md3e") ?: "md3e"
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return uiStyle == "miuix"
}

@Composable
fun PreferenceSwitchItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    if (isMiuixStyle()) {
        MiuixPreferenceSwitchItem(title, description, checked, onCheckedChange)
    } else {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (isMiuixStyle()) {
        MiuixChoiceChip(label, selected, onClick, modifier)
    } else {
        val chipBorder = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        }
        Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = chipBorder, modifier = modifier) {
            Box(modifier = Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) { Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp) }
        }
    }
}

@Composable
fun PreferenceSection(title: String, content: @Composable () -> Unit) {
    if (isMiuixStyle()) {
        MiuixPreferenceSection(title, content)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
fun PermissionItem(title: String, description: String, isGranted: Boolean, actionButton: @Composable (() -> Unit)? = null) {
    if (isMiuixStyle()) {
        MiuixPermissionItem(title, description, isGranted, actionButton)
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Icon(imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = null, tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336), modifier = Modifier.size(28.dp))
                }
                Text(text = description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                if (!isGranted) { Spacer(Modifier.height(4.dp)); actionButton?.invoke() }
            }
        }
    }
}

@Composable
fun CaptureModeItem(title: String, description: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (isMiuixStyle()) {
        MiuixCaptureModeItem(title, description, selected, enabled, onClick)
    } else {
        val modeBorder = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.7f else 0.35f))
        }
        Surface(onClick = { if (enabled) onClick() }, shape = RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else if (!enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = modeBorder, modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp)); Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                }
                RadioButton(selected = selected, onClick = { if (enabled) onClick() }, enabled = enabled)
            }
        }
    }
}

@Composable
fun SettingsListItem(title: String, description: String?, onClick: () -> Unit) {
    if (isMiuixStyle()) {
        MiuixSettingsListItem(title, description, onClick)
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        ) {
            ListItem(headlineContent = { Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium) }, supportingContent = if (description != null) { { Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) } } else null, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }
}

// ─── Pixel 风格分组组件 ───

@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (isMiuixStyle()) {
        MiuixSettingsGroup(modifier, content)
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        ) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsGroupItem(
    title: String,
    description: String? = null,
    position: GroupPosition,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    if (isMiuixStyle()) {
        MiuixSettingsGroupItem(title, description, position, onClick, trailing)
    } else {
        Surface(
            onClick = onClick,
            shape = groupItemShape(position),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        if (description != null) {
                            Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                    trailing?.invoke()
                }
                if (position == GroupPosition.First || position == GroupPosition.Middle) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsGroupSwitchItem(
    title: String,
    description: String? = null,
    position: GroupPosition,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    if (isMiuixStyle()) {
        MiuixSettingsGroupSwitchItem(title, description, position, checked, onCheckedChange)
    } else {
        Surface(
            shape = groupItemShape(position),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        if (description != null) {
                            Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                    Switch(checked = checked, onCheckedChange = onCheckedChange)
                }
                if (position == GroupPosition.First || position == GroupPosition.Middle) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
