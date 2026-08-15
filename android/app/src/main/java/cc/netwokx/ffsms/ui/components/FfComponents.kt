package cc.netwokx.ffsms.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.ui.theme.CostNumber
import cc.netwokx.ffsms.ui.theme.InlineNumber
import cc.netwokx.ffsms.ui.theme.StatNumber
import cc.netwokx.ffsms.ui.theme.ff

/**
 * Kopfzeile mit dem roten Streifen.
 *
 * Auf jedem Bildschirm dieselbe: Rauchanthrazit, weisse Schrift, drei
 * Bildpunkte Florianrot als Unterkante. Der Streifen ist die einzige
 * Markenflaeche der App und ersetzt eine vollflaechig rote Leiste - eine
 * solche haette jede Warnung im Inhalt entwertet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FfTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.ff.chrome,
                titleContentColor = MaterialTheme.ff.onChrome,
                navigationIconContentColor = MaterialTheme.ff.onChrome,
                actionIconContentColor = MaterialTheme.ff.onChrome,
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.ff.brand),
        )
    }
}

/**
 * Diagonale Warnschraffur.
 *
 * Nur an zwei Stellen: ueber dem Bestaetigungsdialog und ueber einer
 * abgebrochenen Aussendung. Sonst nirgends - ein Warnzeichen, das ueberall
 * klebt, warnt vor nichts mehr.
 */
@Composable
fun HazardStripe(modifier: Modifier = Modifier) {
    val color = MaterialTheme.ff.brand
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val step = 16f
        val h = size.height
        var x = -h
        while (x < size.width + h) {
            drawLine(
                color = color,
                start = Offset(x, h),
                end = Offset(x + h, 0f),
                strokeWidth = 6f,
            )
            x += step
        }
    }
}

/** Wie ernst die Zahl gerade ist. */
enum class CostLevel { NEUTRAL, WARN, CRITICAL }

/**
 * Die Kostenanzeige - das Herzstueck der App.
 *
 * Vierzig Bildpunkte, feste Laufweite, allein in ihrer Kachel. Sie ist das
 * einzige Element dieser Groesse; man liest sie, ohne hinzusehen.
 *
 * Im Warnfall wechselt nicht nur die Farbe, sondern auch Grund und Rahmen.
 * Das traegt auch bei Sonnenlicht und bei einer Rotsehschwaeche - rund acht
 * Prozent der Maenner. Ein Zustand, der nur an der Farbe haengt, ist fuer
 * diese Leute gar kein Zustand.
 */
@Composable
fun CostTile(
    value: Int,
    label: String,
    level: CostLevel = CostLevel.NEUTRAL,
    modifier: Modifier = Modifier,
    footnote: String? = null,
) {
    val ff = MaterialTheme.ff
    val accent = when (level) {
        CostLevel.NEUTRAL -> MaterialTheme.colorScheme.onSurface
        CostLevel.WARN -> ff.warn
        CostLevel.CRITICAL -> MaterialTheme.colorScheme.error
    }
    val container = when (level) {
        CostLevel.NEUTRAL -> MaterialTheme.colorScheme.surface
        CostLevel.WARN -> ff.warnContainer
        CostLevel.CRITICAL -> ff.critContainer
    }
    val edge = if (level == CostLevel.NEUTRAL) ff.hairline else accent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(12.dp))
            .border(if (level == CostLevel.NEUTRAL) 1.dp else 2.dp, edge, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value.toString(), style = CostNumber, color = accent)
            Box(modifier = Modifier.width(10.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = InlineNumber,
                color = MaterialTheme.ff.muted,
            )
        }
    }
}

/** Was ein Chip aussagt. */
enum class Tone { OK, WARN, CRITICAL, NEUTRAL }

/**
 * Statuschip.
 *
 * Immer ein Wort, nie ein Symbol allein: "zugestellt", "fehlgeschlagen",
 * "ungueltig". Symbole sind auf einem Handy im Ruesthaus zu klein, und wer
 * sie einmal falsch deutet, deutet sie immer falsch.
 */
@Composable
fun StatusPill(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    val ff = MaterialTheme.ff
    val fg = when (tone) {
        Tone.OK -> ff.ok
        Tone.WARN -> ff.warn
        Tone.CRITICAL -> MaterialTheme.colorScheme.error
        Tone.NEUTRAL -> ff.muted
    }
    val bg = when (tone) {
        Tone.OK -> ff.okContainer
        Tone.WARN -> ff.warnContainer
        Tone.CRITICAL -> ff.critContainer
        Tone.NEUTRAL -> Color.Transparent
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = modifier
            .background(bg, RoundedCornerShape(50))
            .then(
                if (tone == Tone.NEUTRAL) {
                    Modifier.border(1.dp, ff.hairline, RoundedCornerShape(50))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Abschnittsbeschriftung. Immer in Grossbuchstaben, immer gedaempft. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.ff.muted,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}

/** Kennzahl in einer Kachel, etwa "178 Segmente". */
@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.ff.hairline, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionLabel(label)
        Text(text = value, style = StatNumber, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.ff.muted,
        )
    }
}

/** Text in fester Laufweite. Fuer alles, was gezaehlt oder abgeglichen wird. */
@Composable
fun NumberText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = InlineNumber,
    color: Color = MaterialTheme.ff.muted,
) {
    Text(text = text, style = style, color = color, modifier = modifier)
}
