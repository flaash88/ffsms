package cc.netwokx.ffsms.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.ui.components.FfTopBar
import cc.netwokx.ffsms.ui.components.SectionLabel
import cc.netwokx.ffsms.ui.theme.ff

/**
 * Datenschutz-Screen.
 *
 * Listet im Klartext auf, welche Felder das Geraet verlassen und welche
 * ausdruecklich nicht. Die Liste der uebertragenen Felder entspricht 1:1
 * [cc.netwokx.ffsms.data.remote.CampaignUploadDto] - wer dort ein Feld
 * ergaenzt, muss diesen Screen mit anpassen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            FfTopBar(title = stringResource(R.string.privacy_title), onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyMedium)

            Section(
                title = stringResource(R.string.privacy_sent_header),
                accent = SectionAccent.OK,
            ) {
                listOf(
                    R.string.privacy_sent_device,
                    R.string.privacy_sent_campaign,
                    R.string.privacy_sent_time,
                    R.string.privacy_sent_recipients,
                    R.string.privacy_sent_segments,
                    R.string.privacy_sent_encoding,
                    R.string.privacy_sent_failed,
                    R.string.privacy_sent_aborted,
                ).forEach { res ->
                    Text("•  " + stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Die einzige Stelle in der App, an der Rot etwas Beruhigendes
            // bedeutet - und sie funktioniert nur, weil die gruene Liste
            // direkt darueber steht.
            Section(
                title = stringResource(R.string.privacy_never_header),
                accent = SectionAccent.CRITICAL,
            ) {
                Text(stringResource(R.string.privacy_never_text), style = MaterialTheme.typography.bodyMedium)
            }

            Section(title = stringResource(R.string.privacy_local_header)) {
                Text(stringResource(R.string.privacy_local_text), style = MaterialTheme.typography.bodyMedium)
            }

            Section(title = stringResource(R.string.privacy_third_header)) {
                Text(stringResource(R.string.privacy_third_text), style = MaterialTheme.typography.bodyMedium)
            }

            Section(title = stringResource(R.string.privacy_consent_header)) {
                Text(stringResource(R.string.privacy_consent_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private enum class SectionAccent { NONE, OK, CRITICAL }

/**
 * Beide Listen gleich gesetzt, unterschieden nur durch die farbige Kante:
 * gruen, was das Geraet verlaesst, rot, was es nie verlaesst. Gleiches
 * Gewicht fuer beide - die zweite Liste ist die wichtigere Zusage.
 */
@Composable
private fun Section(
    title: String,
    accent: SectionAccent = SectionAccent.NONE,
    content: @Composable () -> Unit,
) {
    val edge = when (accent) {
        SectionAccent.OK -> MaterialTheme.ff.ok
        SectionAccent.CRITICAL -> MaterialTheme.colorScheme.error
        SectionAccent.NONE -> MaterialTheme.ff.hairline
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(edge),
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionLabel(
                    text = title,
                    color = if (accent == SectionAccent.NONE) MaterialTheme.ff.muted else edge,
                )
                content()
            }
        }
    }
}
