package com.Badnng.moe.ui.component

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * 全局 BottomSheet 模糊状态
 * 任何 BottomSheet 显示时设置为 true，MiuixMainContent 读取并显示模糊
 * progress: 0f=完全关闭, 1f=完全展开，跟随 Sheet 拖拽实时变化
 */
object BlurState {
    val isAnySheetVisible = mutableStateOf(false)
    val progress = mutableFloatStateOf(0f)

    fun show() { isAnySheetVisible.value = true }
    fun hide() { isAnySheetVisible.value = false; progress.floatValue = 0f }
    fun updateProgress(value: Float) { progress.floatValue = value.coerceIn(0f, 1f) }
}
