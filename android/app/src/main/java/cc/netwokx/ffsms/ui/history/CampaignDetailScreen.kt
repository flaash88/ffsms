package cc.netwokx.ffsms.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cc.netwokx.ffsms.data.db.CampaignStatus
import cc.netwokx.ffsms.data.db.RecipientStatusRow
import cc.netwokx.ffsms.data.db.SendStatus
import cc.netwokx.ffsms.send.AbortReason
import cc.netwokx.ffsms.send.smsErrorText
import cc.netwokx.ffsms.ui.components.FfTopBar
import cc.netwokx.ffsms.ui.components.HazardStripe
import cc.netwokx.ffsms.ui.components.NumberText
import cc.netwokx.ffsms.ui.components.SectionLabel
import cc.netwokx.ffsms.ui.components.StatusPill
import cc.netwokx.ffsms.ui.components.Tone
import cc.netwokx.ffsms.ui.theme.StatNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignDetailScreen(
    campaignId: String,
    onBack: () -> Unit,
    vm: CampaignDetailViewModel = viewModel(),
) {
    LaunchedEffect(campaignId) { vm.bind(campaignId) }

    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.retryStarted) {
        val msisdn = state.retryStarted ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(R.string.detail_retry_started, msisdn))
        vm.retryShown()
    }

    Scaffold(
        topBar = {
            FfTopBar(title = stringResource(R.string.detail_title), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val campaign = state.campaign
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (campaign != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            campaign.groupName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        // Dasselbe Zahlenmuster wie im Verlauf, nur eine Ebene
                        // tiefer. Wiederkehrende Formen sind hier wichtiger
                        // als Abwechslung.
                        NumberText(
                            text = stringResource(
                                R.string.detail_total,
                                campaign.recipientCount,
                                campaign.totalSegments,
                            ),
                            style = StatNumber,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(
                                text = campaign.encoding,
                                tone = if (campaign.encoding == "UCS2") Tone.WARN else Tone.OK,
                            )
                            StatusPill(
                                text = stringResource(statusLabel(campaign.status)),
                                tone = when (campaign.status) {
                                    CampaignStatus.ABORTED -> Tone.CRITICAL
                                    CampaignStatus.COMPLETED -> Tone.OK
                                    else -> Tone.NEUTRAL
                                },
                            )
                        }
                        campaign.abortedReason?.let { code ->
                            HazardStripe(modifier = Modifier.padding(top = 4.dp))
                            Text(
                                stringResource(
                                    R.string.detail_aborted,
                                    AbortReason.fromCode(code)?.message ?: code,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            SectionLabel(stringResource(R.string.detail_message))
                            Text(campaign.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.detail_recipients)) }

            items(state.recipients, key = { it.msisdn }) { row ->
                RecipientRow(row = row, onRetry = { vm.retry(row.msisdn) })
            }
        }
    }
}

@Composable
private fun RecipientRow(row: RecipientStatusRow, onRetry: () -> Unit) {
    val failed = row.status == SendStatus.FAILED.name
    val tone = when (row.status) {
        SendStatus.DELIVERED.name -> Tone.OK
        SendStatus.FAILED.name -> Tone.CRITICAL
        else -> Tone.NEUTRAL
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.displayName ?: row.msisdn,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    NumberText(text = row.msisdn)
                }
                StatusPill(text = stringResource(sendStatusLabel(row.status)), tone = tone)
            }
            if (failed) {
                val attempt = row.attempt
                val reason = if (row.errorCode != null) smsErrorText(row.errorCode) else ""
                val attemptText = if (attempt != null && attempt > 1) {
                    stringResource(R.string.send_attempt, attempt)
                } else {
                    ""
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberText(
                        text = listOf(reason, attemptText).filter { it.isNotEmpty() }.joinToString(" · "),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    // Neuversuch NUR fuer einzelne fehlgeschlagene Empfaenger.
                    // Es gibt bewusst keinen "alle wiederholen"-Knopf: diese
                    // App wiederholt nichts von selbst, und die Gestaltung darf
                    // keinen Weg anbieten, den der Code nicht hat.
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(stringResource(R.string.detail_retry))
                    }
                }
            }
        }
    }
}

private fun sendStatusLabel(status: String?): Int = when (status) {
    SendStatus.SENDING.name -> R.string.send_status_sending
    SendStatus.SENT.name -> R.string.send_status_sent
    SendStatus.DELIVERED.name -> R.string.send_status_delivered
    SendStatus.FAILED.name -> R.string.send_status_failed
    else -> R.string.send_status_pending
}
