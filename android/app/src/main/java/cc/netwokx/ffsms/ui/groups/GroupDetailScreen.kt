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

    val requestContacts = rememberPermissionGate(PermissionRequest.CONTACTS) { granted ->
        if (granted) {
            vm.loadContacts()
            showContactPicker = true
        }
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
