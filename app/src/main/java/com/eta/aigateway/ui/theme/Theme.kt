package com.eta.aigateway.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 深海配色
private val AbyssTop = Color(0xFF060B16)
private val AbyssMid = Color(0xFF0C1526)
private val AbyssBottom = Color(0xFF101C33)
private val WhaleBlue = Color(0xFF5AA9FF)
private val WhaleCyan = Color(0xFF3DDCC4)
private val Foam = Color(0xFFEAF2FF)

private val DarkColors = darkColorScheme(
    primary = WhaleBlue,
    onPrimary = Color(0xFF00182E),
    primaryContainer = Color(0xFF1B3D63),
    onPrimaryContainer = Foam,
    secondary = WhaleCyan,
    onSecondary = Color(0xFF00201B),
    secondaryContainer = Color(0xFF17453E),
    onSecondaryContainer = Color(0xFFB8F5EA),
    background = AbyssMid,
    onBackground = Foam,
    surface = Color(0xFF131F35),
    onSurface = Foam,
    surfaceVariant = Color(0xFF1A2740),
    onSurfaceVariant = Color(0xFFA9BBD8),
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF3A0000),
    outline = Color(0xFF2C3B57)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1668D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = Color(0xFF001A3D),
    secondary = Color(0xFF00897B),
    background = Color(0xFFF2F7FF),
    onBackground = Color(0xFF0E1626),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1626),
    surfaceVariant = Color(0xFFE4ECF9),
    onSurfaceVariant = Color(0xFF46546F),
    error = Color(0xFFC62B2B),
    outline = Color(0xFFBCC9DE)
)

@Composable
fun AIGatewayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme) {
        // 深海渐变底
        val bg = if (darkTheme) {
            Brush.verticalGradient(listOf(AbyssTop, AbyssMid, AbyssBottom))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFEAF3FF), Color(0xFFF6FAFF)))
        }
        Box(Modifier.fillMaxSize().background(bg)) {
            content()
        }
    }
}
