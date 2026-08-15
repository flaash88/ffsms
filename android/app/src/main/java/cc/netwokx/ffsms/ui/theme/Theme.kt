package cc.netwokx.ffsms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/*
 * Kein Dynamic Color.
 *
 * Androids Material You wuerde die Farben vom Hintergrundbild des Geraets
 * ableiten. Auf einem Feuerwehrhandy hiesse das: die Warnfarbe der App haengt
 * davon ab, welches Foto jemand als Hintergrund eingestellt hat. Die Farben
 * hier tragen Bedeutung und werden deshalb festgelegt, nicht errechnet.
 */

private val LightColors = lightColorScheme(
    primary = Florianrot,
    onPrimary = LoeschweissRein,
    primaryContainer = FlorianrotHell,
    onPrimaryContainer = FlorianrotDunkel,
    secondary = Rauchanthrazit,
    onSecondary = LoeschweissRein,
    secondaryContainer = Loeschweiss,
    onSecondaryContainer = Rauchanthrazit,
    background = Loeschweiss,
    onBackground = Color(0xFF1B1817),
    surface = LoeschweissRein,
    onSurface = Color(0xFF1B1817),
    surfaceVariant = Loeschweiss,
    onSurfaceVariant = Rauch,
    outline = Color(0xFFCFC5BF),
    outlineVariant = LinieHell,
    error = Florianrot,
    onError = LoeschweissRein,
    errorContainer = FlorianrotHell,
    onErrorContainer = FlorianrotDunkel,
)

private val DarkColors = darkColorScheme(
    primary = FlorianrotAufDunkel,
    onPrimary = Color(0xFF4A0603),
    primaryContainer = Color(0xFF3A1512),
    onPrimaryContainer = Color(0xFFFFD9D4),
    secondary = Loeschweiss,
    onSecondary = RauchanthrazitTief,
    secondaryContainer = Color(0xFF2A2523),
    onSecondaryContainer = Loeschweiss,
    background = DunkelGrund,
    onBackground = Color(0xFFECE7E4),
    surface = DunkelFlaeche,
    onSurface = Color(0xFFECE7E4),
    surfaceVariant = Color(0xFF262221),
    onSurfaceVariant = RauchHell,
    outline = Color(0xFF4A423F),
    outlineVariant = LinieDunkel,
    error = FlorianrotAufDunkel,
    onError = Color(0xFF4A0603),
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = Color(0xFFFFD9D4),
)

@Composable
fun FfSmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Ein Alarmierungsgeraet wird nachts bedient. Der Dunkelmodus ist hier
    // kein Zugestaendnis an den Geschmack, sondern der Normalfall um drei Uhr
    // frueh - und deshalb kein invertiertes Hellthema, sondern ein eigener
    // Satz Farben.
    CompositionLocalProvider(
        LocalFfColors provides if (darkTheme) FfColorsDunkel else FfColorsHell,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = FfTypography,
            content = content,
        )
    }
}
