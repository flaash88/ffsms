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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import cc.netwokx.ffsms.data.db.RecipientStatusRow
import cc.netwokx.ffsms.data.db.SendStatus
import cc.netwokx.ffsms.send.AbortReason
import cc.netwokx.ffsms.send.smsErrorText

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
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
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
                    Column {
                        Text(
                            campaign.groupName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(
                                R.string.detail_total,
                                campaign.recipientCount,
                                campaign.totalSegments,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "${campaign.encoding} · ${stringResource(statusLabel(campaign.status))}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        campaign.abortedReason?.let { code ->
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
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.detail_message),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(campaign.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.detail_recipients),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(state.recipients, key = { it.msisdn }) { row ->
                RecipientRow(row = row, onRetry = { vm.retry(row.msisdn) })
            }
        }
    }
}

@Composable
private fun RecipientRow(row: RecipientStatusRow, onRetry: () -> Unit) {
    val failed = row.status == SendStatus.FAILED.name
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (failed) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.displayName ?: row.msisdn, style = MaterialTheme.typography.bodyLarge)
                Text(
                    row.msisdn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(stringResource(sendStatusLabel(row.status)))
                        if (failed && row.errorCode != null) append(" · ${smsErrorText(row.errorCode)}")
                        val attempt = row.attempt
                        if (attempt != null && attempt > 1) {
                            append(" · ")
                            append(stringResource(R.string.send_attempt, attempt))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Neuversuch NUR fuer einzelne fehlgeschlagene Empfaenger. Es gibt
            // bewusst keinen "alle wiederholen"-Knopf.
            if (failed) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(stringResource(R.string.detail_retry))
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
