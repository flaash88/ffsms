package cc.netwokx.ffsms.ui.groups

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import cc.netwokx.ffsms.data.contacts.DeviceContact
import cc.netwokx.ffsms.data.contacts.DeviceContactGroup
import cc.netwokx.ffsms.ui.permissions.PermissionRequest
import cc.netwokx.ffsms.ui.permissions.hasPermission
import cc.netwokx.ffsms.ui.permissions.rememberPermissionGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: Long,
    onBack: () -> Unit,
    vm: GroupDetailViewModel = viewModel(),
) {
    LaunchedEffect(groupId) { vm.bind(groupId) }

    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var showContactPicker by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }

    var contactsDenied by remember { mutableStateOf(false) }

    val requestContacts = rememberPermissionGate(PermissionRequest.CONTACTS) { granted ->
        if (granted) {
            vm.loadContacts()
            showContactPicker = true
        } else {
            // Die App bleibt ohne Kontaktzugriff voll nutzbar - das muss der
            // Benutzer an dieser Stelle auch erfahren, sonst wirkt die
            // Ablehnung wie eine Sackgasse.
            contactsDenied = true
        }
    }

    LaunchedEffect(contactsDenied) {
        if (contactsDenied) {
            snackbar.showSnackbar(context.getString(R.string.import_no_contacts_permission))
            contactsDenied = false
        }
    }

    LaunchedEffect(state.lastSync) {
        val result = state.lastSync ?: return@LaunchedEffect
        val text = if (!result.ran) {
            context.getString(R.string.contactgroup_sync_skipped, result.skipped.orEmpty())
        } else {
            buildString {
                append(context.getString(R.string.contactgroup_sync_added, result.added))
                if (result.missing > 0) {
                    append(context.getString(R.string.contactgroup_sync_missing, result.missing))
                }
            }
        }
        snackbar.showSnackbar(text)
        vm.syncShown()
    }

    LaunchedEffect(state.lastImport) {
        val result = state.lastImport ?: return@LaunchedEffect
        val text = buildString {
            append(context.getString(R.string.import_result, result.added))
            if (result.duplicates > 0) {
                append(context.getString(R.string.import_result_duplicates, result.duplicates))
            }
            if (result.invalid.isNotEmpty()) {
                append(context.getString(R.string.import_result_invalid, result.invalid.size))
            }
        }
        snackbar.showSnackbar(text)
        vm.importShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.groupName) },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = {
                    if (hasPermission(context, Manifest.permission.READ_CONTACTS)) {
                        vm.loadContacts()
                        showContactPicker = true
                    } else {
                        requestContacts()
                    }
                }) {
                    Icon(Icons.Default.Contacts, contentDescription = null)
                    Text(stringResource(R.string.group_detail_add_contacts))
                }
                OutlinedButton(onClick = { showManual = true }) {
                    Icon(Icons.Default.Dialpad, contentDescription = null)
                    Text(stringResource(R.string.group_detail_add_manual))
                }
            }

            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = {
                    if (hasPermission(context, Manifest.permission.READ_CONTACTS)) {
                        vm.loadContactGroups()
                        showGroupPicker = true
                    } else {
                        requestContacts()
                    }
                }) {
                    Icon(Icons.Default.Group, contentDescription = null)
                    Text(stringResource(R.string.contactgroup_choose))
                }
            }

            ContactGroupCard(
                linked = state.linkedContactGroup,
                autoSync = state.autoSync,
                syncing = state.syncing,
                missingCount = state.missingCount,
                onSyncNow = vm::syncNow,
                onAutoSyncChanged = vm::setAutoSync,
                onUnlink = vm::unlinkContactGroup,
                onRemoveMissing = vm::removeMissing,
            )

            Text(
                text = stringResource(R.string.groups_recipient_count, state.recipients.count { it.valid }),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (state.recipients.any { !it.valid }) {
                Text(
                    text = stringResource(R.string.group_detail_invalid_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.recipients.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.group_detail_empty))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.recipients, key = { it.id }) { recipient ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        recipient.displayName ?: recipient.msisdn,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        recipient.msisdn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!recipient.valid) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(stringResource(R.string.group_detail_invalid_badge)) },
                                    )
                                }
                                if (recipient.missingInContactGroup) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(stringResource(R.string.contactgroup_missing_badge)) },
                                    )
                                }
                                IconButton(onClick = { vm.deleteRecipient(recipient.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContactPicker) {
        ContactPickerDialog(
            contacts = state.contacts,
            loading = state.contactsLoading,
            onDismiss = { showContactPicker = false },
            onConfirm = { selected ->
                vm.importContacts(selected)
                showContactPicker = false
            },
        )
    }

    if (showGroupPicker) {
        ContactGroupPickerDialog(
            groups = state.contactGroups,
            loading = state.contactGroupsLoading,
            onDismiss = { showGroupPicker = false },
            onConfirm = { group, autoSync ->
                vm.linkContactGroup(group, autoSync)
                showGroupPicker = false
            },
        )
    }

    if (showManual) {
        ManualNumberDialog(
            onDismiss = { showManual = false },
            onConfirm = { numbers, name ->
                vm.addManual(numbers, name)
                showManual = false
            },
        )
    }
}

/**
 * Mehrfachauswahl aus dem Adressbuch.
 *
 * Abweichung von der Vorgabe "System-Kontaktpicker": ACTION_PICK liefert immer
 * nur einen Kontakt zurueck, fuer 30 Empfaenger waeren das 30 Durchlaeufe.
 * Deshalb ein eigener Dialog mit echter Mehrfachauswahl auf denselben Daten.
 */
@Composable
private fun ContactPickerDialog(
    contacts: List<DeviceContact>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<DeviceContact>) -> Unit,
) {
    val selected = remember { mutableStateMapOf<String, DeviceContact>() }
    var query by remember { mutableStateOf("") }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.displayName.contains(query, ignoreCase = true) || it.number.contains(query)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_detail_add_contacts)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.group_detail_manual_name)) },
                )
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.contactId + it.number }) { contact ->
                            val key = contact.contactId + contact.number
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selected.containsKey(key),
                                    onCheckedChange = { checked ->
                                        if (checked) selected[key] = contact else selected.remove(key)
                                    },
                                )
                                Column {
                                    Text(contact.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOfNotNull(contact.number, contact.label).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected.values.toList()) },
                enabled = selected.isNotEmpty(),
            ) {
                Text(stringResource(R.string.action_add) + " (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ManualNumberDialog(
    onDismiss: () -> Unit,
    onConfirm: (numbers: String, name: String?) -> Unit,
) {
    var numbers by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_detail_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = numbers,
                    onValueChange = { numbers = it },
                    label = { Text(stringResource(R.string.group_detail_manual_number)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_detail_manual_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.group_detail_manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(numbers, name.takeIf { it.isNotBlank() }) },
                enabled = numbers.isNotBlank(),
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Zeigt die verknuepfte Kontaktgruppe und was der letzte Abgleich ergab.
 *
 * Der Hinweis auf fehlende Empfaenger ist bewusst prominent: sie werden vom
 * Abgleich nur markiert, nie automatisch entfernt. Ein Alarmierungsverteiler,
 * der sich still verkleinert, waere der schlimmste denkbare Fehler - also muss
 * ein Mensch die Entscheidung treffen, und dafuer muss er sie sehen.
 */
@Composable
private fun ContactGroupCard(
    linked: String?,
    autoSync: Boolean,
    syncing: Boolean,
    missingCount: Int,
    onSyncNow: () -> Unit,
    onAutoSyncChanged: (Boolean) -> Unit,
    onUnlink: () -> Unit,
    onRemoveMissing: () -> Unit,
) {
    if (linked == null) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (missingCount > 0) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.contactgroup_linked, linked),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.contactgroup_autosync))
                    Text(
                        stringResource(R.string.contactgroup_autosync_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = autoSync, onCheckedChange = onAutoSyncChanged)
            }

            if (missingCount > 0) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.contactgroup_missing_hint, missingCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onRemoveMissing) {
                    Text(stringResource(R.string.contactgroup_missing_remove, missingCount))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSyncNow, enabled = !syncing) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Text(
                        stringResource(
                            if (syncing) R.string.contactgroup_syncing else R.string.contactgroup_sync_now,
                        ),
                    )
                }
                TextButton(onClick = onUnlink) {
                    Text(stringResource(R.string.contactgroup_unlink))
                }
            }
        }
    }
}

/**
 * Auswahl einer Kontaktgruppe.
 *
 * Gezeigt werden nur selbst angelegte Labels mit mindestens einem Mitglied -
 * Systemgruppen wie "Markiert in Android" waeren als Verteiler wertlos.
 */
@Composable
private fun ContactGroupPickerDialog(
    groups: List<DeviceContactGroup>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (DeviceContactGroup, Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf<DeviceContactGroup?>(null) }
    var autoSync by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contactgroup_choose)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.contactgroup_choose_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    groups.isEmpty() -> Text(
                        stringResource(R.string.contactgroup_none),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )

                    else -> LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(groups, key = { it.id }) { group ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selected?.id == group.id,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) group else null
                                    },
                                )
                                Column {
                                    Text(group.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOfNotNull(
                                            stringResource(
                                                R.string.contactgroup_members,
                                                group.memberCount,
                                            ),
                                            group.accountName,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }

                if (groups.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoSync, onCheckedChange = { autoSync = it })
                        Text(
                            stringResource(R.string.contactgroup_autosync),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selected?.let { onConfirm(it, autoSync) } },
                enabled = selected != null,
            ) { Text(stringResource(R.string.contactgroup_link)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
