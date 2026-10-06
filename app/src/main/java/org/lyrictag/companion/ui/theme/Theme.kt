package org.lyrictag.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Static fallback palette: warm amber accent on dark neutrals, deep-plum light.
private val FallbackDark = darkColorScheme(
    primary = Color(0xFFE8B44B),
    onPrimary = Color(0xFF2A1F00),
    primaryContainer = Color(0xFF5A4310),
    onPrimaryContainer = Color(0xFFFFE9C4),
    secondary = Color(0xFFD3C5A5),
    surface = Color(0xFF161310),
    onSurface = Color(0xFFF0E9DC),
    surfaceVariant = Color(0xFF2A2521),
)
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0A3),
    onPrimaryContainer = Color(0xFF2A1F00),
    secondary = Color(0xFF6B5D3F),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFF1E8D8),
)

@Composable
fun LyricTagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
