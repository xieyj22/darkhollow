package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonRed,
    secondary = SkyBlue,
    tertiary = PureGold,
    background = DeepBlack,
    surface = DarkSlate,
    onPrimary = DarkCrimson, // text on primary button (Dark purple on lavender)
    onSecondary = DarkSlate,
    onTertiary = DarkSlate,
    onBackground = SoftGrey,
    onSurface = SoftGrey
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
