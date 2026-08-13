package cc.netwokx.ffsms.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.netwokx.ffsms.R

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
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
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
                highlight = false,
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

            Section(
                title = stringResource(R.string.privacy_never_header),
                highlight = true,
            ) {
                Text(stringResource(R.string.privacy_never_text), style = MaterialTheme.typography.bodyMedium)
            }

            Section(title = stringResource(R.string.privacy_local_header), highlight = false) {
                Text(stringResource(R.string.privacy_local_text), style = MaterialTheme.typography.bodyMedium)
            }

            Section(title = stringResource(R.string.privacy_third_header), highlight = false) {
                Text(stringResource(R.string.privacy_third_text), style = MaterialTheme.typography.bodyMedium)
            }

            Section(title = stringResource(R.string.privacy_consent_header), highlight = false) {
                Text(stringResource(R.string.privacy_consent_text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    highlight: Boolean,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
