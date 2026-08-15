package cc.netwokx.ffsms.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/*
 * Drei Rollen: Fliesstext, Ueberschrift, Zahl.
 *
 * Die Zahlen bekommen eine eigene Schrift, und das ist keine Kosmetik. In der
 * Verbrauchsliste stehen Segmentzahlen untereinander. Mit proportionalen
 * Ziffern rutschen sie gegeneinander, und ein Sprung von 45 auf 450 faellt
 * beim Ueberfliegen nicht auf. In fester Laufweite bilden die Spalten eine
 * Kante - eine Zahl, die aus der Reihe tanzt, sieht man, ohne sie zu lesen.
 * Genau dafuer gibt es die Liste.
 *
 * Ueberschriften traegt das Systemgrotesk mit engerer Laufweite und hoeherem
 * Gewicht. Eine echte Schmalschrift (Roboto Condensed) muesste als Datei ins
 * APK; das waere ein eigener Schritt und ist hier bewusst nicht gemacht.
 */

/** Fuer alles, was gezaehlt oder abgeglichen wird. */
val NumberFamily: FontFamily = FontFamily.Monospace

/** Die grosse Kostenzahl. Das einzige Element dieser Groesse in der App. */
val CostNumber = TextStyle(
    fontFamily = NumberFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-1).sp,
    textAlign = TextAlign.Start,
)

/** Kennzahlen in Kacheln und Kopfzeilen von Listen. */
val StatNumber = TextStyle(
    fontFamily = NumberFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.5).sp,
)

/** Zahlen im Fliess einer Zeile: Nummern, Zeitstempel, Versionen. */
val InlineNumber = TextStyle(
    fontFamily = NumberFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)

val FfTypography = Typography().let { base ->
    base.copy(
        displayMedium = base.displayMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
        ),
        // Knopfbeschriftungen. Ein Knopf sagt, was passiert - er darf dabei
        // fest auftreten.
        labelLarge = base.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        ),
        // Abschnittsbeschriftungen, immer in Grossbuchstaben gesetzt.
        labelMedium = base.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp,
        ),
    )
}
