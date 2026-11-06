package com.example.calculator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = OrangeKey,
    secondary = LightGrayKey,
    background = PureBlack,
    surface = PureBlack,
    onPrimary = PureWhite,
    onSecondary = PureBlack,
    onBackground = PureWhite,
    onSurface = PureWhite
)

@Composable
fun CalculatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
