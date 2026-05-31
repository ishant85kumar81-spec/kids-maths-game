package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = KidsPink,
    onPrimary = Color.White,
    primaryContainer = KidsPinkDark,
    secondary = KidsBlueBtn,
    onSecondary = Color.White,
    secondaryContainer = KidsBlueBtnDark,
    tertiary = KidsPurple,
    onTertiary = Color.White,
    tertiaryContainer = KidsPurpleDark,
    background = ImmersiveBg,
    onBackground = ImmersiveTextDark,
    surface = Color.White,
    onSurface = ImmersiveTextDark,
)

private val DarkColorScheme = lightColorScheme( // Keeping it bright even in dark mode for kids game vibe
    primary = KidsPink,
    onPrimary = Color.White,
    primaryContainer = KidsPinkDark,
    secondary = KidsBlueBtn,
    onSecondary = Color.White,
    secondaryContainer = KidsBlueBtnDark,
    tertiary = KidsPurple,
    onTertiary = Color.White,
    tertiaryContainer = KidsPurpleDark,
    background = ImmersiveBg,
    onBackground = ImmersiveTextDark,
    surface = Color.White,
    onSurface = ImmersiveTextDark,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
