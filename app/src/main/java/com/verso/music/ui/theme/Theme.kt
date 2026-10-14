package com.verso.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Poweramp-inspired palette: deep-navy dark, warm orange accent.
// (Static fallback is now the default look — dynamic wallpaper theming
// is opt-in via the `dynamicColor` flag.)
private val PowerampDark = darkColorScheme(
    primary = Color(0xFFFF9E40),
    onPrimary = Color(0xFF2A1500),
    primaryContainer = Color(0xFF4A2C0A),
    onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFF8EA2C8),
    onSecondary = Color(0xFF101828),
    surface = Color(0xFF0B0E1A),
    onSurface = Color(0xFFE8EAF2),
    surfaceVariant = Color(0xFF151B2E),
    onSurfaceVariant = Color(0xFFA7B0C8),
    error = Color(0xFFFF6B6B),
)
private val PowerampLight = lightColorScheme(
    primary = Color(0xFFA85B00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB8),
    onPrimaryContainer = Color(0xFF2A1500),
    secondary = Color(0xFF3D4F74),
    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF1A1C24),
    surfaceVariant = Color(0xFFF1E4D2),
    onSurfaceVariant = Color(0xFF5A5C6E),
)

/** Subtle diagonal glow for header cards (dashboard stats, detail header). */
fun headerGradient(): Brush = Brush.linearGradient(
    0.0f to Color(0xFF26355E),
    0.55f to Color(0xFF141C36),
    1.0f to Color(0xFF0B0E1A),
)

@Composable
fun VersoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> PowerampDark
        else -> PowerampLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
