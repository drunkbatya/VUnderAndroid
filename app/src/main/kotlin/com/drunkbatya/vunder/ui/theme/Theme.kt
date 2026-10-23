package com.drunkbatya.vunder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VUnderColors {
    val accent = Color(0xFF7F8EC7)
    val background = Color(0xFF1B1A1F)
    val groupedBackground = Color(0xFF141317)
    val surfaceRaised = Color(0xFF26252B)
    val separator = Color(0xFF38373D)
    val text = Color(0xFFFFFFFF)
    val secondaryText = Color(0x99EBEBF5)
    val destructive = Color(0xFFFF453A)
}

private val colorScheme = darkColorScheme(
    primary = VUnderColors.accent,
    onPrimary = Color.White,
    secondary = VUnderColors.accent,
    background = VUnderColors.background,
    onBackground = VUnderColors.text,
    surface = VUnderColors.background,
    onSurface = VUnderColors.text,
    surfaceVariant = VUnderColors.surfaceRaised,
    onSurfaceVariant = VUnderColors.secondaryText,
    surfaceContainer = VUnderColors.background,
    surfaceContainerLow = VUnderColors.groupedBackground,
    surfaceContainerHigh = VUnderColors.surfaceRaised,
    outlineVariant = VUnderColors.separator,
    error = VUnderColors.destructive,
)

@Composable
fun VUnderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
