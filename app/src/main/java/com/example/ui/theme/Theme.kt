package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VictorDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnCyan,
    primaryContainer = Color(0xFF003844),
    onPrimaryContainer = Color(0xFF9DF2FF),
    secondary = RoyalViolet,
    onSecondary = OnViolet,
    secondaryContainer = Color(0xFF281A5E),
    onSecondaryContainer = Color(0xFFDDD5FF),
    tertiary = AmberPlayhead,
    onTertiary = Color(0xFF291800),
    tertiaryContainer = Color(0xFF422900),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceVariant,
    surfaceContainerHigh = StudioElevated,
    surfaceContainerHighest = Color(0xFF2A2F45),
    outline = StudioOutline,
    outlineVariant = Color(0xFF202436),
    error = CrimsonAccent,
    onError = Color(0xFF3B0010)
)

private val VictorLightColorScheme = lightColorScheme(
    primary = Color(0xFF00687A),
    onPrimary = Color.White,
    secondary = Color(0xFF593CE0),
    onSecondary = Color.White,
    tertiary = Color(0xFFB36B00),
    background = Color(0xFFF6F8FC),
    surface = Color.White,
    onBackground = Color(0xFF11131C),
    onSurface = Color(0xFF11131C)
)

@Composable
fun VictorMotionTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VictorDarkColorScheme else VictorLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
