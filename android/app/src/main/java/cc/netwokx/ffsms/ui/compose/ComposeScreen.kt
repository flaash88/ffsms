package cc.netwokx.ffsms.ui.compose

import android.Manifest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.domain.sms.SmsEncoding
import cc.netwokx.ffsms.ui.components.CostLevel
import cc.netwokx.ffsms.ui.components.CostTile
import cc.netwokx.ffsms.ui.components.FfTopBar
import cc.netwokx.ffsms.ui.components.HazardStripe
import cc.netwokx.ffsms.ui.components.NumberText
import cc.netwokx.ffsms.ui.components.StatusPill
import cc.netwokx.ffsms.ui.components.Tone
import cc.netwokx.ffsms.ui.permissions.PermissionRequest
import cc.netwokx.ffsms.ui.permissions.hasPermission
import cc.netwokx.ffsms.ui.permissions.rememberPermissionGate
import cc.netwokx.ffsms.ui.theme.ff

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
        topBar = { FfTopBar(stringResource(R.string.compose_title)) },
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
            if (state.overMonthLimit) {
                Text(
                    text = stringResource(
                        R.string.compose_over_month,
                        state.monthAfter,
                        state.monthLimit,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
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
    val level = when {
        state.overHardLimit -> CostLevel.CRITICAL
        state.overWarnThreshold -> CostLevel.WARN
        else -> CostLevel.NEUTRAL
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Encoding und Zeichenzahl stehen ueber der Kostenzahl, nicht darunter:
        // sie erklaeren, wie die Zahl zustande kommt.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                text = stringResource(
                    if (isUcs2) R.string.compose_encoding_ucs2 else R.string.compose_encoding_gsm7,
                ),
                tone = if (isUcs2) Tone.WARN else Tone.OK,
            )
            Spacer(Modifier.width(8.dp))
            NumberText(
                text = stringResource(
                    R.string.compose_chars_segments,
                    state.info.charCount,
                    state.info.segments,
                ),
            )
        }

        if (isUcs2) {
            Text(
                text = stringResource(R.string.compose_encoding_warning),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.ff.warn,
            )
        }

        CostTile(
            value = state.totalSegments,
            label = stringResource(R.string.compose_total_label),
            level = level,
            footnote = stringResource(
                R.string.compose_times_recipients,
                state.recipientCount,
                state.totalSegments,
            ),
        )

        // Der Monat, und zwar der Stand NACH dieser Aussendung. Die Zahl, die
        // hier interessiert, ist nicht "wieviel habe ich verbraucht", sondern
        // "wo stehe ich, wenn ich jetzt sende".
        MonthBudget(state)

        if (state.info.segments > 0 && level == CostLevel.NEUTRAL) {
            Text(
                text = stringResource(
                    R.string.compose_remaining,
                    state.info.remainingInLastSegment,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ff.muted,
            )
        }
        if (state.overWarnThreshold && !state.overHardLimit) {
            Text(
                text = stringResource(
                    R.string.compose_over_threshold,
                    state.settings?.warnThresholdSegments ?: 0,
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.ff.warn,
            )
        }
    }
}

/**
 * Monatsbudget mit Balken.
 *
 * Der Tarif enthaelt eine feste Zahl an SMS; alles darueber wird einzeln
 * verrechnet. Das ist die Grenze, die am Monatsende auf der Rechnung steht -
 * anders als die Tagesgrenze, die nur einen Ausrutscher abfaengt.
 *
 * Gezeigt wird der Stand NACH dieser Aussendung, mit dem Zuwachs als
 * dunklerer Teil des Balkens. Wer 40 von 500 verbraucht hat und 480
 * verschicken will, soll das sehen, bevor er tippt - und nicht danach,
 * wenn der Worker mitten im Versand abbricht.
 */
@Composable
private fun MonthBudget(state: ComposeUiState) {
    val limit = state.monthLimit
    if (limit <= 0) return

    val ff = MaterialTheme.ff
    val farbe = when {
        state.overMonthLimit -> MaterialTheme.colorScheme.error
        state.monthTight -> ff.warn
        else -> ff.muted
    }
    val anteilVerbraucht = (state.monthUsed.toFloat() / limit).coerceIn(0f, 1f)
    val anteilGesamt = (state.monthAfter.toFloat() / limit).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberText(
                text = stringResource(
                    R.string.compose_month_budget,
                    state.monthAfter,
                    limit,
                ),
                color = farbe,
                modifier = Modifier.weight(1f),
            )
            if (state.totalSegments > 0) {
                NumberText(
                    text = stringResource(R.string.compose_month_used_before, state.monthUsed),
                    color = ff.muted,
                )
            }
        }
        // Zwei Balken uebereinander: hell der Stand nach dem Senden, dunkel
        // der bereits verbrauchte Teil. Damit ist der Zuwachs dieser einen
        // Aussendung als Differenz sichtbar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(ff.hairline, RoundedCornerShape(4.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(anteilGesamt)
                    .height(8.dp)
                    .background(farbe.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(anteilVerbraucht)
                    .height(8.dp)
                    .background(farbe, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/**
 * Benennt die konkreten Zeichen, die UCS-2 erzwungen haben.
 *
 * Bernstein, nicht Rot: das hier ist teuer, aber nicht verboten. Rot bliebe
 * fuer die Obergrenze reserviert, hinter der der Versand tatsaechlich
 * abbricht.
 */
@Composable
private fun Ucs2Warning(state: ComposeUiState, onSanitize: () -> Unit) {
    val saving = state.savingBySanitize * state.recipientCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.ff.warnContainer, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.ff.warn, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.compose_ucs2_explain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.ff.warn,
        )
        state.info.offenders.forEach { offender ->
            NumberText(
                text = stringResource(R.string.compose_ucs2_char, offender.label, offender.count),
                color = MaterialTheme.ff.warn,
            )
        }
        // Der Knopf nennt den Betrag statt der Taetigkeit. Ein Knopf, der
        // sagt, was er bringt, wird gedrueckt.
        OutlinedButton(
            onClick = onSanitize,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.ff.warn),
            border = BorderStroke(1.dp, MaterialTheme.ff.warn),
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (saving > 0) {
                    stringResource(R.string.compose_clean_saving, saving)
                } else {
                    stringResource(R.string.compose_clean)
                },
            )
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
        title = {
            // Der Warnstreifen erscheint an genau zwei Stellen in der App.
            // Hier markiert er die Schwelle, hinter der Geld ausgegeben wird -
            // dasselbe Zeichen, das im Ruesthaus vor einer Absturzkante steht.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HazardStripe()
                Text(stringResource(R.string.confirm_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CostTile(
                    value = total,
                    label = stringResource(R.string.confirm_total_label),
                    level = CostLevel.CRITICAL,
                )
                NumberText(
                    text = stringResource(
                        R.string.confirm_breakdown,
                        recipients,
                        segmentsPerMessage,
                        encoding.name,
                    ),
                )
                Text(
                    text = stringResource(R.string.confirm_group, groupName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (encoding == SmsEncoding.UCS2 && savingBySanitize > 0) {
                    Text(
                        text = stringResource(R.string.confirm_ucs2_hint, savingBySanitize),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.ff.warn,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.ff.critContainer, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp)),
                ) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text(
                        text = stringResource(R.string.confirm_checkbox, total),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 12.dp),
                    )
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
