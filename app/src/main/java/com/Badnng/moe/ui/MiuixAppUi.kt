package com.Badnng.moe.ui

import com.Badnng.moe.ui.miuix.MiuixCaptureModeItem
import com.Badnng.moe.ui.miuix.MiuixChoiceChip
import com.Badnng.moe.ui.miuix.MiuixPermissionItem
import com.Badnng.moe.ui.miuix.MiuixPreferenceSection
import com.Badnng.moe.ui.miuix.MiuixPreferenceSwitchItem
import com.Badnng.moe.ui.miuix.MiuixSettingsGroup
import com.Badnng.moe.ui.miuix.MiuixSettingsGroupItem
import com.Badnng.moe.ui.miuix.MiuixSettingsGroupSwitchItem
import com.Badnng.moe.ui.miuix.MiuixSettingsListItem

val miuixAppUi = AppUi(
    settingsGroup = { modifier, content ->
        MiuixSettingsGroup(modifier, content)
    },
    settingsGroupItem = { title, description, position, onClick, trailing ->
        MiuixSettingsGroupItem(title, description, position, onClick, trailing)
    },
    settingsGroupSwitchItem = { title, description, position, checked, onCheckedChange ->
        MiuixSettingsGroupSwitchItem(title, description, position, checked, onCheckedChange)
    },
    settingsListItem = { title, description, onClick ->
        MiuixSettingsListItem(title, description, onClick)
    },
    preferenceSection = { title, content ->
        MiuixPreferenceSection(title, content)
    },
    permissionItem = { title, description, isGranted, actionButton ->
        MiuixPermissionItem(title, description, isGranted, actionButton)
    },
    captureModeItem = { title, description, selected, enabled, onClick ->
        MiuixCaptureModeItem(title, description, selected, enabled, onClick)
    },
    choiceChip = { label, selected, onClick, modifier ->
        MiuixChoiceChip(label, selected, onClick, modifier)
    },
    preferenceSwitchItem = { title, description, checked, onCheckedChange ->
        MiuixPreferenceSwitchItem(title, description, checked, onCheckedChange)
    },
)
