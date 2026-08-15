package cc.netwokx.ffsms.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.ui.components.FfTopBar

/**
 * Serverkonfiguration - Adresse, Schluessel, Geraete-Kennung, Uebertragung.
 *
 * Ein eigener Bildschirm hinter einer PIN, weil diese vier Werte anders sind
 * als der Rest der Einstellungen: sie werden einmal eingerichtet und danach
 * nie wieder angefasst. Ein verstellter Grenzwert faellt beim naechsten
 * Verfassen auf; eine verstellte Serveradresse faellt gar nicht auf - die
 * App sendet weiter SMS, nur die Verbrauchszahlen kommen nirgends mehr an.
 * Bemerkt wird das erst, wenn die Rechnung eine Zahl nennt, der man nichts
 * mehr entgegenhalten kann.
 *
 * Die Sperre wird hier geprueft und nicht beim Antippen in den
 * Einstellungen. Damit ist sie auch dann noch wirksam, wenn Android den
 * Bildschirm nach dem Beenden der App aus dem Backstack wiederherstellt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val settings = state.settings

    // Zurueck in den Hintergrund heisst: wieder zu. Sonst bleibt die Freigabe
    // stehen, das Handy wandert weiter, und der naechste findet die
    // Serverkonfiguration offen vor - womit die Sperre nichts mehr taugt.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.lock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.test) {
        when (val test = state.test) {
            is ConnectionTest.Ok -> {
                snackbar.showSnackbar(context.getString(R.string.settings_test_ok, test.status))
                vm.testShown()
            }
            is ConnectionTest.Failed -> {
                val text = if (test.reason == "incomplete") {
                    context.getString(R.string.settings_test_incomplete)
                } else {
                    context.getString(R.string.settings_test_failed, test.reason)
                }
                snackbar.showSnackbar(text)
                vm.testShown()
            }
            else -> Unit
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbar.showSnackbar(context.getString(R.string.settings_saved))
            vm.savedShown()
        }
    }

    Scaffold(
        topBar = {
            FfTopBar(title = stringResource(R.string.server_config_title), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (settings == null) return@Scaffold

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.pinProtected && !state.unlocked) {
                PinPrompt(
                    pinError = state.pinError,
                    onUnlock = vm::unlock,
                    onErrorShown = vm::pinErrorShown,
                )
                return@Column
            }

            DebouncedField(
                value = settings.backendUrl,
                label = stringResource(R.string.settings_backend_url),
                onCommit = vm::setBackendUrl,
            )
            DebouncedField(
                value = settings.apiKey,
                label = stringResource(R.string.settings_api_key),
                onCommit = vm::setApiKey,
                masked = true,
            )
            DebouncedField(
                value = settings.deviceId,
                label = stringResource(R.string.settings_device_id),
                onCommit = vm::setDeviceId,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_sync_enabled))
                    Text(
                        stringResource(R.string.settings_sync_enabled_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settings.syncEnabled, onCheckedChange = vm::setSyncEnabled)
            }

            OutlinedButton(
                onClick = vm::testConnection,
                enabled = state.test != ConnectionTest.Running,
            ) {
                Text(
                    stringResource(
                        if (state.test == ConnectionTest.Running) {
                            R.string.settings_test_running
                        } else {
                            R.string.settings_test
                        },
                    ),
                )
            }

            HorizontalDivider()

            LockSection(
                protected = settings.pinProtected,
                onLock = vm::lock,
                onSetPin = vm::setPin,
                onRemovePin = vm::removePin,
            )
        }
    }
}

/**
 * PIN-Eingabe vor allem anderen.
 *
 * Kein Zaehler fuer Fehlversuche und keine Sperrzeit. Die PIN haelt niemanden
 * auf, der die App zerlegen will - sie soll verhindern, dass beim Suchen nach
 * etwas anderem versehentlich die Serveradresse verstellt wird. Eine Sperre
 * nach drei Fehlversuchen stuende genau dann im Weg, wenn unter Zeitdruck
 * etwas richtigzustellen ist.
 */
@Composable
private fun PinPrompt(
    pinError: Boolean,
    onUnlock: (String) -> Unit,
    onErrorShown: () -> Unit,
) {
    var entry by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Text(
                    stringResource(R.string.settings_lock_closed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                stringResource(R.string.settings_lock_closed_desc),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = entry,
                onValueChange = {
                    entry = it.filter(Char::isDigit).take(MAX_PIN_LENGTH)
                    if (pinError) onErrorShown()
                },
                label = { Text(stringResource(R.string.settings_lock_pin)) },
                singleLine = true,
                isError = pinError,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            if (pinError) {
                Text(
                    stringResource(R.string.settings_lock_wrong),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { onUnlock(entry); entry = "" },
                enabled = entry.length >= MIN_PIN_LENGTH,
            ) {
                Text(stringResource(R.string.settings_lock_unlock))
            }
        }
    }
}

/** Verwaltung der PIN, sichtbar nur im entsperrten Zustand. */
@Composable
private fun LockSection(
    protected: Boolean,
    onLock: () -> Unit,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
) {
    var showPinDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Text(
                    stringResource(
                        if (protected) R.string.settings_lock_open else R.string.settings_lock_none,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (protected) {
                Text(
                    stringResource(R.string.settings_lock_open_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onLock) {
                        Text(stringResource(R.string.settings_lock_now))
                    }
                    OutlinedButton(onClick = { showPinDialog = true }) {
                        Text(stringResource(R.string.settings_lock_change))
                    }
                    TextButton(onClick = onRemovePin) {
                        Text(stringResource(R.string.settings_lock_remove))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.settings_lock_none_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { showPinDialog = true }) {
                    Text(stringResource(R.string.settings_lock_set))
                }
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { onSetPin(it); showPinDialog = false },
        )
    }
}

/**
 * Neue PIN mit Wiederholung.
 *
 * Die Wiederholung ist kein Zierrat: eine vertippte PIN faellt sonst erst
 * auf, wenn sie gebraucht wird - und dann hilft nur noch Neuinstallieren,
 * womit alle Verteiler und der Verlauf verloren waeren.
 */
@Composable
private fun PinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val longEnough = first.length >= MIN_PIN_LENGTH
    val matching = first == second

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_lock_set)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_lock_set_desc, MIN_PIN_LENGTH),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it.filter(Char::isDigit).take(MAX_PIN_LENGTH) },
                    label = { Text(stringResource(R.string.settings_lock_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = second,
                    onValueChange = { second = it.filter(Char::isDigit).take(MAX_PIN_LENGTH) },
                    label = { Text(stringResource(R.string.settings_lock_pin_repeat)) },
                    singleLine = true,
                    isError = second.isNotEmpty() && !matching,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (second.isNotEmpty() && !matching) {
                    Text(
                        stringResource(R.string.settings_lock_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    stringResource(R.string.settings_lock_forgot_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(first) }, enabled = longEnough && matching) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

internal const val MIN_PIN_LENGTH = 4
internal const val MAX_PIN_LENGTH = 8
