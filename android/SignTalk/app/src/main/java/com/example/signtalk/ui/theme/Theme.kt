package com.example.signtalk.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = LightSurface,
    secondary = AmberAccent,
    background = LightBackground,
    surface = LightSurface,
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryLight,
    onPrimary = DarkBackground,
    secondary = AmberAccent,
    background = DarkBackground,
    surface = DarkSurface,
    error = ErrorRed
)

@Composable
fun SignTalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // keep the SignTalk brand palette consistent by default
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SignTalkTypography,
        content = content
    )
}
