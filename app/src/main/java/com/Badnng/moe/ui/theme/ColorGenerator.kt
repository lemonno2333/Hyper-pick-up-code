package com.Badnng.moe.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.CorePalette

object ColorGenerator {
    fun seedToColorScheme(seedColor: Int, isDark: Boolean): androidx.compose.material3.ColorScheme {
        val palette = CorePalette.of(seedColor)
        val p = palette.a1   // primary
        val s = palette.a2   // secondary
        val t = palette.a3   // tertiary
        val n = palette.n1   // neutral
        val nv = palette.n2  // neutralVariant

        return if (isDark) darkColorScheme(
            primary = Color(p.tone(80)),
            onPrimary = Color(p.tone(20)),
            primaryContainer = Color(p.tone(30)),
            onPrimaryContainer = Color(p.tone(90)),
            secondary = Color(s.tone(80)),
            onSecondary = Color(s.tone(20)),
            secondaryContainer = Color(s.tone(30)),
            onSecondaryContainer = Color(s.tone(90)),
            tertiary = Color(t.tone(80)),
            onTertiary = Color(t.tone(20)),
            tertiaryContainer = Color(t.tone(30)),
            onTertiaryContainer = Color(t.tone(90)),
            background = Color(n.tone(6)),
            onBackground = Color(n.tone(90)),
            surface = Color(n.tone(6)),
            onSurface = Color(n.tone(90)),
            surfaceVariant = Color(nv.tone(30)),
            onSurfaceVariant = Color(nv.tone(80)),
            outline = Color(nv.tone(60)),
            outlineVariant = Color(nv.tone(30)),
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
            inverseSurface = Color(n.tone(90)),
            inverseOnSurface = Color(n.tone(20)),
            inversePrimary = Color(p.tone(40)),
            scrim = Color(0xFF000000),
        ) else lightColorScheme(
            primary = Color(p.tone(40)),
            onPrimary = Color(p.tone(100)),
            primaryContainer = Color(p.tone(90)),
            onPrimaryContainer = Color(p.tone(10)),
            secondary = Color(s.tone(40)),
            onSecondary = Color(s.tone(100)),
            secondaryContainer = Color(s.tone(90)),
            onSecondaryContainer = Color(s.tone(10)),
            tertiary = Color(t.tone(40)),
            onTertiary = Color(t.tone(100)),
            tertiaryContainer = Color(t.tone(90)),
            onTertiaryContainer = Color(t.tone(10)),
            background = Color(n.tone(99)),
            onBackground = Color(n.tone(10)),
            surface = Color(n.tone(99)),
            onSurface = Color(n.tone(10)),
            surfaceVariant = Color(nv.tone(90)),
            onSurfaceVariant = Color(nv.tone(30)),
            outline = Color(nv.tone(50)),
            outlineVariant = Color(nv.tone(80)),
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            inverseSurface = Color(n.tone(20)),
            inverseOnSurface = Color(n.tone(95)),
            inversePrimary = Color(p.tone(80)),
            scrim = Color(0xFF000000),
        )
    }
}
