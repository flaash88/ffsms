package cc.netwokx.ffsms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FireRed = Color(0xFFB3120C)
private val FireRedDark = Color(0xFF7A0A06)
private val FireRedLight = Color(0xFFFFDAD5)

private val LightColors = lightColorScheme(
    primary = FireRed,
    onPrimary = Color.White,
    primaryContainer = FireRedLight,
    onPrimaryContainer = FireRedDark,
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = FireRedDark,
    onPrimaryContainer = FireRedLight,
)

@Composable
fun FfSmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
