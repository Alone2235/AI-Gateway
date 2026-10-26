package com.eta.aigateway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 深海鲸蓝配色
private val DeepSea = Color(0xFF0B1120)
private val DeepSeaSurface = Color(0xFF141B2D)
private val WhaleBlue = Color(0xFF4A9EFF)
private val WhaleCyan = Color(0xFF33D6C0)
private val Foam = Color(0xFFE8F1FF)

private val DarkColors = darkColorScheme(
    primary = WhaleBlue,
    onPrimary = Color(0xFF00172E),
    primaryContainer = Color(0xFF17385E),
    onPrimaryContainer = Foam,
    secondary = WhaleCyan,
    onSecondary = Color(0xFF00201B),
    background = DeepSea,
    onBackground = Foam,
    surface = DeepSeaSurface,
    onSurface = Foam,
    surfaceVariant = Color(0xFF1D2740),
    onSurfaceVariant = Color(0xFFA8B8D8),
    error = Color(0xFFFF6B6B),
    outline = Color(0xFF2E3D5C)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1668D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = Color(0xFF001A3D),
    secondary = Color(0xFF009688),
    background = Color(0xFFF7FAFF),
    onBackground = Color(0xFF10182A),
    surface = Color.White,
    onSurface = Color(0xFF10182A),
    surfaceVariant = Color(0xFFE6EDF8),
    onSurfaceVariant = Color(0xFF4A5772),
    error = Color(0xFFD32029),
    outline = Color(0xFFC2CFE4)
)

@Composable
fun AIGatewayTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
