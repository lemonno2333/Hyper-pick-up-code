package com.Badnng.moe.ui

import androidx.compose.runtime.Composable
import com.Badnng.moe.ui.component.CaptureModeItem
import com.Badnng.moe.ui.component.ChoiceChip
import com.Badnng.moe.ui.component.GroupPosition
import com.Badnng.moe.ui.component.PermissionItem
import com.Badnng.moe.ui.component.PreferenceSection
import com.Badnng.moe.ui.component.PreferenceSwitchItem
import com.Badnng.moe.ui.component.SettingsGroup
import com.Badnng.moe.ui.component.SettingsGroupItem
import com.Badnng.moe.ui.component.SettingsGroupSwitchItem
import com.Badnng.moe.ui.component.SettingsListItem

val md3eAppUi = AppUi(
    settingsGroup = { modifier, content -> SettingsGroup(modifier, content) },
    settingsGroupItem = { title, description, position, onClick, trailing ->
        SettingsGroupItem(title, description, position, onClick, trailing)
    },
    settingsGroupSwitchItem = { title, description, position, checked, onCheckedChange ->
        SettingsGroupSwitchItem(title, description, position, checked, onCheckedChange)
    },
    settingsListItem = { title, description, onClick ->
        SettingsListItem(title, description, onClick)
    },
    preferenceSection = { title, content ->
        PreferenceSection(title, content)
    },
    permissionItem = { title, description, isGranted, actionButton ->
        PermissionItem(title, description, isGranted, actionButton)
    },
    captureModeItem = { title, description, selected, enabled, onClick ->
        CaptureModeItem(title, description, selected, enabled, onClick)
    },
    choiceChip = { label, selected, onClick, modifier ->
        ChoiceChip(label, selected, onClick, modifier)
    },
    preferenceSwitchItem = { title, description, checked, onCheckedChange ->
        PreferenceSwitchItem(title, description, checked, onCheckedChange)
    },
)
