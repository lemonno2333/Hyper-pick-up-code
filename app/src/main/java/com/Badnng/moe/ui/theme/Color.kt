package com.Badnng.moe.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.CorePalette

data class Md3Preset(
    val seed: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

private fun Int.toMd3Preset(): Md3Preset {
    val palette = CorePalette.of(this)
    return Md3Preset(
        seed = Color(this),
        primary = Color(palette.a1.tone(40)),
        secondary = Color(palette.a2.tone(40)),
        tertiary = Color(palette.a3.tone(40)),
    )
}

val Md3Presets = listOf(
    0xFF6750A4.toInt().toMd3Preset(), // Purple
    0xFF4355B9.toInt().toMd3Preset(), // Indigo
    0xFF0061A4.toInt().toMd3Preset(), // Blue
    0xFF006A60.toInt().toMd3Preset(), // Teal
    0xFF386A20.toInt().toMd3Preset(), // Green
    0xFF7D5700.toInt().toMd3Preset(), // Yellow
    0xFF8B5000.toInt().toMd3Preset(), // Orange
    0xFFBA1A1A.toInt().toMd3Preset(), // Red
    0xFF984061.toInt().toMd3Preset(), // Pink
    0xFF605D62.toInt().toMd3Preset(), // Neutral
    0xFF00629E.toInt().toMd3Preset(), // Cyan
)