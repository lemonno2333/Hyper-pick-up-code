package com.Badnng.moe.ui.component

import androidx.compose.runtime.mutableStateOf

/**
 * 全局 BottomSheet 模糊状态
 * 任何 BottomSheet 显示时设置为 true，MiuixMainContent 读取并显示模糊
 */
object BlurState {
    val isAnySheetVisible = mutableStateOf(false)

    fun show() { isAnySheetVisible.value = true }
    fun hide() { isAnySheetVisible.value = false }
}
