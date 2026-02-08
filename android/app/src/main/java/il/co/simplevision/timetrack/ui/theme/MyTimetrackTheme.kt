package il.co.simplevision.timetrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Matches iOS accent from AGENTS.md:
// Color(red: 0.25, green: 0.62, blue: 0.60) -> #409E99
private val Accent = Color(0xFF409E99)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3E5E2),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4A6361),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E5),
    onSecondaryContainer = Color(0xFF051F1E),
    background = Color(0xFFF8FAF9),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFF8FAF9),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E3),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7977),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD1CB),
    onPrimary = Color(0xFF003735),
    primaryContainer = Accent,
    onPrimaryContainer = Color(0xFFDFF6F3),
    secondary = Color(0xFFB0CCC9),
    onSecondary = Color(0xFF1B3533),
    secondaryContainer = Color(0xFF334B49),
    onSecondaryContainer = Color(0xFFCCE8E5),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C7),
    outline = Color(0xFF889392),
)

@Composable
fun MyTimetrackTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content,
    )
}

