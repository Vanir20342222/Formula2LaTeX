package com.formula2latex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.formula2latex.data.settings.ThemePreference

@Composable
fun FormulaTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val light = lightColorScheme(
        primary = Color(0xFF4E46B4),
        secondary = Color(0xFF006B5E),
        tertiary = Color(0xFF8A4A00),
        background = Color(0xFFF8F7FC),
        surface = Color(0xFFFFFBFF),
        surfaceVariant = Color(0xFFE6E1EC),
    )
    val dark = darkColorScheme(
        primary = Color(0xFFC5C0FF),
        secondary = Color(0xFF59DBC5),
        tertiary = Color(0xFFFFB870),
        background = Color(0xFF121218),
        surface = Color(0xFF1A1A22),
        surfaceVariant = Color(0xFF34333D),
    )
    val amoled = darkColorScheme(
        primary = Color(0xFFC5C0FF),
        secondary = Color(0xFF59DBC5),
        tertiary = Color(0xFFFFB870),
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color(0xFF18181E),
        surfaceContainer = Color(0xFF08080A),
        surfaceContainerHigh = Color(0xFF121216),
    )
    val scheme = when (preference) {
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) dark else light
        ThemePreference.LIGHT -> light
        ThemePreference.DARK -> dark
        ThemePreference.AMOLED -> amoled
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
