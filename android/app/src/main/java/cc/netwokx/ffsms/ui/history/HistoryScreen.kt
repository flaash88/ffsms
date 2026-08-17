package cc.netwokx.ffsms.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.data.db.CampaignEntity
import cc.netwokx.ffsms.data.db.CampaignStatus
import cc.netwokx.ffsms.ui.components.EdgeCard
import cc.netwokx.ffsms.ui.components.FfTopBar
import cc.netwokx.ffsms.ui.components.NumberText
import cc.netwokx.ffsms.ui.components.StatTile
import cc.netwokx.ffsms.ui.components.StatusPill
import cc.netwokx.ffsms.ui.components.Tone
import cc.netwokx.ffsms.ui.theme.ff
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ROW_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenCampaign: (String) -> Unit,
    vm: HistoryViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(vm::exportTo) }

    LaunchedEffect(state.exportResult) {
        val result = state.exportResult ?: return@LaunchedEffect
        snackbar.showSnackbar(
            context.getString(
                if (result) R.string.history_export_done else R.string.history_export_failed,
            ),
        )
        vm.exportResultShown()
    }

    Scaffold(
        topBar = {
            FfTopBar(
                title = stringResource(R.string.history_title),
                actions = {
                    IconButton(onClick = { exportLauncher.launch(vm.suggestedFileName()) }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.history_export),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            UsageCard(
                week = state.weekSegments,
                month = state.monthSegments,
                monthLimit = state.monthLimit,
                unsynced = state.unsyncedCount,
            )

            if (state.campaigns.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.history_empty)) }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.campaigns, key = { it.id }) { campaign ->
                        CampaignRow(campaign) { onOpenCampaign(campaign.id) }
                    }
                }
            }
        }
    }
}

/**
 * Verbrauch der laufenden Woche und des laufenden Monats.
 *
 * Diese Zahlen stammen aus dem lokalen Sendeprotokoll. Sie funktionieren
 * ohne Netz und ohne Backend - der Gerätebetreiber soll seinen Verbrauch
 * jederzeit sehen koennen, auch wenn der Server steht.
 */
@Composable
private fun UsageCard(week: Int, month: Int, monthLimit: Int, unsynced: Int) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Summe vor Detail: wer die App zur Kostenkontrolle oeffnet, hat seine
        // Antwort in der ersten Sekunde und muss nicht scrollen.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                label = stringResource(R.string.history_usage_week),
                value = week.toString(),
                unit = stringResource(R.string.history_usage_unit),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.history_usage_month),
                value = if (monthLimit > 0) "$month/$monthLimit" else month.toString(),
                unit = stringResource(R.string.history_usage_unit),
                modifier = Modifier.weight(1f),
            )
        }
        // Bleibt stehen: dieser Satz ist der Grund, warum den Zahlen zu trauen
        // ist. Sie stammen aus dem Sendeprotokoll am Geraet, nicht aus einem
        // Server, der offline sein kann.
        Text(
            text = stringResource(R.string.history_usage_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.ff.muted,
        )
        if (unsynced > 0) {
            StatusPill(
                text = stringResource(R.string.history_unsynced, unsynced),
                tone = Tone.NEUTRAL,
            )
        }
    }
}

@Composable
private fun CampaignRow(campaign: CampaignEntity, onClick: () -> Unit) {
    val aborted = campaign.status == CampaignStatus.ABORTED
    val failed = campaign.failedCount
    // Nichts angekommen ist etwas anderes als "ein paar Ausfaelle": das eine
    // ist ein kaputtes Geraet, das andere ein schlecht erreichter Empfaenger.
    val allFailed = failed > 0 && failed >= campaign.recipientCount

    // Der Chip darf gruen sein, die Kante nicht. Eine Kante an jedem Eintrag
    // in Signalfarbe waere Dekoration: was hervorsticht, kann nur hervorstechen,
    // solange nicht alles hervorsticht. Farbig wird die Kante deshalb nur bei
    // einem Problem, sonst bleibt sie ein duenner Strich.
    val tone = when {
        aborted || allFailed -> Tone.CRITICAL
        failed > 0 -> Tone.WARN
        campaign.status == CampaignStatus.COMPLETED -> Tone.OK
        else -> Tone.NEUTRAL
    }
    val edgeTone = when {
        aborted || allFailed -> Tone.CRITICAL
        failed > 0 -> Tone.WARN
        else -> Tone.NEUTRAL
    }

    EdgeCard(tone = edgeTone, modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberText(
                text = ROW_FORMAT.format(
                    Instant.ofEpochMilli(campaign.createdAt).atZone(ZoneId.systemDefault()),
                ),
                modifier = Modifier.weight(1f),
            )
            // Bei Fehlschlaegen zeigt der Chip die Fehlerzahl statt
            // "abgeschlossen". Der Status stimmt zwar - der Lauf ist durch -,
            // beantwortet aber nicht die Frage, die man an dieser Stelle hat:
            // ist es angekommen?
            StatusPill(
                text = when {
                    allFailed -> stringResource(R.string.history_row_all_failed)
                    failed > 0 -> stringResource(R.string.history_row_failed, failed)
                    else -> stringResource(statusLabel(campaign.status))
                },
                tone = tone,
            )
        }
        Text(
            text = campaign.groupName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        NumberText(
            text = stringResource(
                R.string.history_row_summary,
                campaign.recipientCount,
                campaign.totalSegments,
                campaign.encoding,
            ),
            color = when {
                aborted || allFailed -> MaterialTheme.colorScheme.error
                failed > 0 -> MaterialTheme.ff.warn
                else -> MaterialTheme.ff.muted
            },
        )
    }
}

internal fun statusLabel(status: CampaignStatus): Int = when (status) {
    CampaignStatus.QUEUED -> R.string.campaign_status_queued
    CampaignStatus.RUNNING -> R.string.campaign_status_running
    CampaignStatus.COMPLETED -> R.string.campaign_status_completed
    CampaignStatus.ABORTED -> R.string.campaign_status_aborted
}
