package cc.netwokx.ffsms.ui.compose

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.domain.sms.SmsEncoding
import cc.netwokx.ffsms.ui.permissions.PermissionRequest
import cc.netwokx.ffsms.ui.permissions.hasPermission
import cc.netwokx.ffsms.ui.permissions.rememberPermissionGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onOpenGroups: () -> Unit,
    vm: ComposeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Die SMS-Berechtigung wird erst beim Sendeversuch angefragt - beim
    // ersten App-Start waere der Zusammenhang fuer den Benutzer nicht
    // erkennbar.
    val requestSmsPermission = rememberPermissionGate(PermissionRequest.SMS) { granted ->
        if (granted) vm.onSendRequested()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when {
            message == "queued" -> context.getString(R.string.confirm_queued)
            message == "no_recipients" -> context.getString(R.string.compose_no_recipients)
            message == "cleaned_nothing" -> context.getString(R.string.compose_cleaned_nothing)
            message.startsWith("cleaned:") -> {
                val (_, replaced, removed) = message.split(":")
                buildString {
                    append(context.getString(R.string.compose_cleaned_replaced, replaced.toInt()))
                    if (removed.toInt() > 0) {
                        append(context.getString(R.string.compose_cleaned_removed, removed.toInt()))
                    }
                }
            }
            else -> message
        }
        snackbar.showSnackbar(text)
        vm.onMessageShown()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.compose_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupSelector(state = state, onSelect = vm::onGroupSelected, onOpenGroups = onOpenGroups)

            OutlinedTextField(
                value = state.text,
                onValueChange = vm::onTextChanged,
                label = { Text(stringResource(R.string.compose_message_label)) },
                placeholder = { Text(stringResource(R.string.compose_message_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )

            CalculationCard(state)

            if (state.info.offenders.isNotEmpty()) {
                Ucs2Warning(state = state, onSanitize = vm::onSanitize)
            }

            Button(
                onClick = {
                    if (hasPermission(context, Manifest.permission.SEND_SMS)) {
                        vm.onSendRequested()
                    } else {
                        requestSmsPermission()
                    }
                },
                enabled = state.canSend,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.compose_send))
            }

            if (state.overHardLimit) {
                val limit = state.settings?.maxSegmentsPerCampaign ?: 0
                Text(
                    text = stringResource(R.string.compose_over_limit, limit),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.text.isBlank()) {
                Text(
                    text = stringResource(R.string.compose_empty_text),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    state.pending?.let { pending ->
        ConfirmSendDialog(
            recipients = pending.recipientCount,
            segmentsPerMessage = pending.info.segments,
            total = pending.totalSegments,
            encoding = pending.info.encoding,
            groupName = pending.groupName,
            savingBySanitize = state.savingBySanitize * pending.recipientCount,
            onDismiss = vm::onConfirmDismissed,
            onConfirm = vm::onConfirmed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSelector(
    state: ComposeUiState,
    onSelect: (Long) -> Unit,
    onOpenGroups: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.selectedGroup

    Column {
        Text(
            text = stringResource(R.string.compose_group_label),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { expanded = true }) {
                Text(
                    selected?.let { "${it.group.name} (${it.recipientCount})" }
                        ?: stringResource(R.string.compose_group_none),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.groups.forEach { group ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    R.string.groups_recipient_count,
                                    group.recipientCount,
                                ).let { "${group.group.name} — $it" },
                            )
                        },
                        onClick = {
                            onSelect(group.group.id)
                            expanded = false
                        },
                    )
                }
                if (state.groups.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.groups_new)) },
                        onClick = {
                            expanded = false
                            onOpenGroups()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Die Live-Anzeige. Wird bei jedem Tastendruck neu berechnet.
 *
 * Die Gesamtzahl steht bewusst gross und alleinstehend: sie ist die Zahl, die
 * am Monatsende auf der Rechnung auftaucht.
 */
@Composable
private fun CalculationCard(state: ComposeUiState) {
    val isUcs2 = state.info.encoding == SmsEncoding.UCS2
    val overThreshold = state.overWarnThreshold

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (overThreshold) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (isUcs2) R.string.compose_encoding_ucs2 else R.string.compose_encoding_gsm7,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (isUcs2) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.compose_encoding_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.compose_chars_segments,
                    state.info.charCount,
                    state.info.segments,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.compose_times_recipients,
                    state.recipientCount,
                    state.totalSegments,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.totalSegments.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (overThreshold) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.compose_total_label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (state.info.segments > 0) {
                Text(
                    text = stringResource(
                        R.string.compose_remaining,
                        state.info.remainingInLastSegment,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (overThreshold && !state.overHardLimit) {
                Text(
                    text = stringResource(
                        R.string.compose_over_threshold,
                        state.settings?.warnThresholdSegments ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Benennt die konkreten Zeichen, die UCS-2 erzwungen haben. */
@Composable
private fun Ucs2Warning(state: ComposeUiState, onSanitize: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.compose_ucs2_explain),
                style = MaterialTheme.typography.bodySmall,
            )
            state.info.offenders.forEach { offender ->
                Text(
                    text = stringResource(R.string.compose_ucs2_char, offender.label, offender.count),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.savingBySanitize > 0 && state.recipientCount > 0) {
                Text(
                    text = stringResource(
                        R.string.compose_saving,
                        state.savingBySanitize * state.recipientCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(onClick = onSanitize) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.compose_clean))
            }
        }
    }
}

/**
 * Bestaetigungsdialog.
 *
 * Die berechnete Gesamtzahl steht im Fliesstext, in der Checkbox UND auf dem
 * Bestaetigungsknopf. Ein reines "OK" gibt es hier nicht: der Knopf ist erst
 * aktiv, wenn die Zahl aktiv als geprueft markiert wurde.
 */
@Composable
private fun ConfirmSendDialog(
    recipients: Int,
    segmentsPerMessage: Int,
    total: Int,
    encoding: SmsEncoding,
    groupName: String,
    savingBySanitize: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var checked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.confirm_body,
                        total,
                        recipients,
                        segmentsPerMessage,
                        encoding.name,
                        groupName,
                    ) + if (encoding == SmsEncoding.UCS2 && savingBySanitize > 0) {
                        stringResource(R.string.confirm_body_ucs2_hint, savingBySanitize)
                    } else {
                        ""
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text(stringResource(R.string.confirm_checkbox, total))
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = checked) {
                Text(stringResource(R.string.confirm_send, total))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
