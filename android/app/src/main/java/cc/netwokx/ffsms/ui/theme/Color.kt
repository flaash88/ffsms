package cc.netwokx.ffsms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Palette "Einsatzrot".
 *
 * Die Leitregel: Rot gehoert den Zahlen, nicht der Zierde. Waeren Kopfzeile,
 * Navigation und jeder Knopf rot, dann waere die Warnung "60 SMS" nur eine
 * weitere rote Flaeche unter vielen - und Rot verlore seine Bedeutung genau
 * dort, wo diese App sie braucht.
 *
 * Deshalb sind die Flaechen Rauchanthrazit, die Farbe der Einsatzkleidung.
 * Florianrot erscheint als Signatur: der Streifen unter der Kopfzeile, der
 * Senden-Knopf, die Kostenzahl im Warnfall.
 */

/** Florianrot. Emblem, Streifen, Senden-Knopf, Kostenzahl im Warnfall. */
internal val Florianrot = Color(0xFFB3120C)
internal val FlorianrotDunkel = Color(0xFF7A0A06)
internal val FlorianrotHell = Color(0xFFFDECEB)
/** Aufgehellt fuer dunklen Grund - der reine Markenton haette zu wenig Kontrast. */
internal val FlorianrotAufDunkel = Color(0xFFFF8F86)

/** Rauchanthrazit. Kopfzeile. Warmes Schwarz mit Rotstich, kein Neutralgrau. */
internal val Rauchanthrazit = Color(0xFF221F1E)
internal val RauchanthrazitTief = Color(0xFF0F0E0D)

/** Loeschweiss. Minimal warm, damit Weiss auf Weiss nicht flimmert. */
internal val Loeschweiss = Color(0xFFF7F4F2)
internal val LoeschweissRein = Color(0xFFFFFFFF)

/** Warnbernstein. Ueber der Warnschwelle, UCS-2, "nicht mehr in Gruppe". */
internal val Warnbernstein = Color(0xFFA8620A)
internal val WarnbernsteinGrund = Color(0xFFFDF1E0)
internal val WarnbernsteinAufDunkel = Color(0xFFE0A44A)
internal val WarnbernsteinGrundDunkel = Color(0xFF382709)

/** Einsatzgruen. Zugestellt, Verbindung in Ordnung, Berechtigung erteilt. */
internal val Einsatzgruen = Color(0xFF2C6B48)
internal val EinsatzgruenGrund = Color(0xFFE6F2EC)
internal val EinsatzgruenAufDunkel = Color(0xFF7CC79B)
internal val EinsatzgruenGrundDunkel = Color(0xFF0F2B1D)

/** Rauch. Sekundaertext - Grau mit Rotstich, gehoert zur Familie. */
internal val Rauch = Color(0xFF6D625E)
internal val RauchHell = Color(0xFFA89E99)

internal val LinieHell = Color(0xFFE4DDD9)
internal val LinieDunkel = Color(0xFF322D2B)

internal val DunkelGrund = Color(0xFF161413)
internal val DunkelFlaeche = Color(0xFF1F1C1B)

/**
 * Farben, die Material 3 nicht kennt.
 *
 * Warnung und Sperre muessen auf einen Blick unterscheidbar sein: "ueber der
 * Warnschwelle" ist etwas anderes als "ueber der Obergrenze, Versand bricht
 * ab". Zwei Rottoene nebeneinander schaffen das nicht, schon gar nicht bei
 * Sonnenlicht auf einem Handydisplay vor dem Rüsthaus. Deshalb ist die
 * Statusfarbe vom Markenrot getrennt.
 */
@Immutable
data class FfColors(
    /** Grund der Kopfzeile. */
    val chrome: Color,
    val onChrome: Color,
    /** Der Streifen unter der Kopfzeile und das Emblem. */
    val brand: Color,
    val warn: Color,
    val warnContainer: Color,
    val ok: Color,
    val okContainer: Color,
    val critContainer: Color,
    val hairline: Color,
    val muted: Color,
)

internal val FfColorsHell = FfColors(
    chrome = Rauchanthrazit,
    onChrome = LoeschweissRein,
    brand = Florianrot,
    warn = Warnbernstein,
    warnContainer = WarnbernsteinGrund,
    ok = Einsatzgruen,
    okContainer = EinsatzgruenGrund,
    critContainer = FlorianrotHell,
    hairline = LinieHell,
    muted = Rauch,
)

internal val FfColorsDunkel = FfColors(
    chrome = RauchanthrazitTief,
    onChrome = Loeschweiss,
    brand = Color(0xFFC9241D),
    warn = WarnbernsteinAufDunkel,
    warnContainer = WarnbernsteinGrundDunkel,
    ok = EinsatzgruenAufDunkel,
    okContainer = EinsatzgruenGrundDunkel,
    critContainer = Color(0xFF3A1512),
    hairline = LinieDunkel,
    muted = RauchHell,
)

internal val LocalFfColors = staticCompositionLocalOf { FfColorsHell }

/** Zugriff wie auf die Material-Farben: `MaterialTheme.ff.warn`. */
val MaterialTheme.ff: FfColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFfColors.current
