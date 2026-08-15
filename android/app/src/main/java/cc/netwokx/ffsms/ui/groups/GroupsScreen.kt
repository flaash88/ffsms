package cc.netwokx.ffsms.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.data.db.GroupWithCount
import cc.netwokx.ffsms.ui.components.EdgeCard
import cc.netwokx.ffsms.ui.components.FfTopBar
import cc.netwokx.ffsms.ui.components.NumberText
import cc.netwokx.ffsms.ui.components.StatusPill
import cc.netwokx.ffsms.ui.components.Tone
import cc.netwokx.ffsms.ui.theme.StatNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onOpenGroup: (Long) -> Unit,
    vm: GroupsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<GroupWithCount?>(null) }
    var deleting by remember { mutableStateOf<GroupWithCount?>(null) }

    Scaffold(
        topBar = { FfTopBar(stringResource(R.string.groups_title)) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.groups_new))
            }
        },
    ) { padding ->
        if (state.groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.groups_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.groups, key = { it.group.id }) { group ->
                    GroupRow(
                        group = group,
                        onClick = { onOpenGroup(group.group.id) },
                        onRename = { renaming = group },
                        onDelete = { deleting = group },
                    )
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = stringResource(R.string.groups_new),
            label = stringResource(R.string.groups_new_hint),
            initial = "",
            onDismiss = { showCreate = false },
            onConfirm = {
                vm.createGroup(it)
                showCreate = false
            },
        )
    }

    renaming?.let { group ->
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.groups_new_hint),
            initial = group.group.name,
            onDismiss = { renaming = null },
            onConfirm = {
                vm.renameGroup(group.group.id, it)
                renaming = null
            },
        )
    }

    deleting?.let { group ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.groups_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.groups_delete_text,
                        group.group.name,
                        group.recipientCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteGroup(group.group.id)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun GroupRow(
    group: GroupWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    // Dieselbe Kante wie im Verlauf, mit derselben Bedeutung. Ein Verteiler
    // ohne gueltige Empfaenger ist rot, weil er nicht senden kann - wer ihn
    // auswaehlt und auf Senden tippt, erreicht niemanden. Ungueltige Nummern
    // darin sind bernstein: sie werden uebersprungen und kosten nichts.
    val gueltig = group.recipientCount - group.invalidCount
    val tone = when {
        gueltig <= 0 -> Tone.CRITICAL
        group.invalidCount > 0 -> Tone.WARN
        else -> Tone.NEUTRAL
    }

    EdgeCard(tone = tone, modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                when {
                    gueltig <= 0 -> StatusPill(
                        text = stringResource(R.string.groups_no_valid),
                        tone = Tone.CRITICAL,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    group.invalidCount > 0 -> StatusPill(
                        text = stringResource(R.string.groups_invalid_short, group.invalidCount),
                        tone = Tone.WARN,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // Die Empfaengerzahl steht rechts als eigene Spalte in fester
            // Laufweite. Wer eine Aussendung plant, ueberfliegt diese Spalte -
            // sie ist der Multiplikator der Kosten.
            NumberText(
                text = gueltig.toString(),
                style = StatNumber,
                color = if (gueltig <= 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_rename))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
