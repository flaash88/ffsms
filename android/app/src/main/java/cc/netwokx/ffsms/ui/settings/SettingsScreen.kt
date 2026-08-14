package cc.netwokx.ffsms.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.netwokx.ffsms.BuildConfig
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.ui.permissions.PermissionRequest
import cc.netwokx.ffsms.ui.permissions.openAppSettings
import cc.netwokx.ffsms.ui.permissions.rememberPermissionGate
import cc.netwokx.ffsms.ui.permissions.rememberPermissionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPrivacy: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val settings = state.settings

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

    LaunchedEffect(state.update) {
        when (val update = state.update) {
            UpdateState.UpToDate -> {
                snackbar.showSnackbar(context.getString(R.string.settings_update_none))
                vm.updateShown()
            }
            is UpdateState.Failed -> {
                snackbar.showSnackbar(
                    context.getString(R.string.settings_update_failed, update.reason),
                )
                vm.updateShown()
            }
            else -> Unit
        }
    }

    // Zurueck in den Hintergrund heisst: wieder zu. Sonst bleibt die Freigabe
    // stehen, das Handy wandert weiter, und der naechste findet die
    // Einstellungen offen vor - womit die Sperre nichts mehr taugt.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.lock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbar.showSnackbar(context.getString(R.string.settings_saved))
            vm.savedShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
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
            val locked = settings.pinProtected && !state.unlocked

            LockSection(
                protected = settings.pinProtected,
                unlocked = state.unlocked,
                pinError = state.pinError,
                onUnlock = vm::unlock,
                onLock = vm::lock,
                onSetPin = vm::setPin,
                onRemovePin = vm::removePin,
                onErrorShown = vm::pinErrorShown,
            )

            SectionTitle(stringResource(R.string.settings_backend))

            if (locked) {
                // Gesperrt heisst nicht unsichtbar: die Werte bleiben lesbar,
                // damit sich am Telefon jederzeit vorlesen laesst, was
                // eingestellt ist. Nur der API-Key nicht - der ist ein
                // Geheimnis und kein Diagnosewert.
                ReadOnlyRow(
                    label = stringResource(R.string.settings_backend_url),
                    value = settings.backendUrl,
                )
                ReadOnlyRow(
                    label = stringResource(R.string.settings_device_id),
                    value = settings.deviceId,
                )
                ReadOnlyRow(
                    label = stringResource(R.string.settings_api_key),
                    value = stringResource(
                        if (settings.apiKey.isBlank()) {
                            R.string.settings_locked_key_missing
                        } else {
                            R.string.settings_locked_key_set
                        },
                    ),
                )
                ReadOnlyRow(
                    label = stringResource(R.string.settings_sync_enabled),
                    value = stringResource(
                        if (settings.syncEnabled) R.string.state_on else R.string.state_off,
                    ),
                )
            } else {
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
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedButton(onClick = onOpenPrivacy) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null)
                    Text(stringResource(R.string.nav_privacy))
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_limits))

            Text(
                stringResource(
                    R.string.settings_today_used,
                    state.segmentsToday,
                    settings.maxSegmentsPerDay,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )

            if (locked) {
                ReadOnlyRow(
                    label = stringResource(R.string.settings_warn_threshold),
                    value = settings.warnThresholdSegments.toString(),
                )
                ReadOnlyRow(
                    label = stringResource(R.string.settings_max_campaign),
                    value = settings.maxSegmentsPerCampaign.toString(),
                )
                ReadOnlyRow(
                    label = stringResource(R.string.settings_max_day),
                    value = settings.maxSegmentsPerDay.toString(),
                )
            } else {
                NumberField(
                    value = settings.warnThresholdSegments,
                    label = stringResource(R.string.settings_warn_threshold),
                    description = stringResource(R.string.settings_warn_threshold_desc),
                    onCommit = vm::setWarnThreshold,
                )
                NumberField(
                    value = settings.maxSegmentsPerCampaign,
                    label = stringResource(R.string.settings_max_campaign),
                    description = stringResource(R.string.settings_max_campaign_desc),
                    onCommit = vm::setMaxPerCampaign,
                )
                NumberField(
                    value = settings.maxSegmentsPerDay,
                    label = stringResource(R.string.settings_max_day),
                    description = stringResource(R.string.settings_max_day_desc),
                    onCommit = vm::setMaxPerDay,
                )
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_update))

            Text(
                stringResource(
                    R.string.settings_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            when (val update = state.update) {
                is UpdateState.Available -> {
                    Text(
                        stringResource(
                            R.string.settings_update_available,
                            update.update.versionName,
                            update.update.sizeBytes / 1024 / 1024,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    update.update.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { vm.installUpdate(update.update) }) {
                        Text(stringResource(R.string.settings_update_install))
                    }
                    Text(
                        stringResource(R.string.settings_update_confirm_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                UpdateState.Downloading -> Text(
                    stringResource(R.string.settings_update_downloading),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> OutlinedButton(
                    onClick = vm::checkForUpdate,
                    enabled = update != UpdateState.Checking,
                ) {
                    Text(
                        stringResource(
                            if (update == UpdateState.Checking) {
                                R.string.settings_update_checking
                            } else {
                                R.string.settings_update_check
                            },
                        ),
                    )
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_permissions))

            PermissionRow(
                label = stringResource(R.string.settings_perm_sms),
                request = PermissionRequest.SMS,
            )
            PermissionRow(
                label = stringResource(R.string.settings_perm_contacts),
                request = PermissionRequest.CONTACTS,
            )
            PermissionRow(
                label = stringResource(R.string.settings_perm_notifications),
                request = PermissionRequest.NOTIFICATIONS,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/**
 * Nur lesbare Zeile fuer den gesperrten Zustand.
 *
 * Gesperrt heisst bewusst "nicht aenderbar", nicht "nicht sichtbar". Wer am
 * Telefon gefragt wird, welche Serveradresse eingestellt ist, soll nachsehen
 * koennen, ohne die PIN zu brauchen - und das Nachsehen ist genau der
 * Vorgang, bei dem sonst versehentlich etwas verstellt wird.
 */
@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * Sperre fuer Serverzugang und Obergrenzen.
 *
 * Der Grund ist nicht Geheimhaltung, sondern das versehentliche Verstellen:
 * auf einem geteilten Feuerwehrhandy sucht jemand nach etwas anderem, tippt
 * in ein Zahlenfeld und aendert dabei die Tagesobergrenze - also genau die
 * Sicherung, die einen zweiten 2100er-Vorfall verhindern soll. Die PIN
 * verlangt eine bewusste Handlung, bevor sich daran etwas aendert.
 *
 * Sie schuetzt nicht gegen jemanden, der die App zerlegen will. Das ist auch
 * nicht ihr Zweck.
 */
@Composable
private fun LockSection(
    protected: Boolean,
    unlocked: Boolean,
    pinError: Boolean,
    onUnlock: (String) -> Unit,
    onLock: () -> Unit,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
    onErrorShown: () -> Unit,
) {
    var entry by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (protected && !unlocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                )
                Text(
                    stringResource(
                        when {
                            !protected -> R.string.settings_lock_none
                            unlocked -> R.string.settings_lock_open
                            else -> R.string.settings_lock_closed
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            when {
                !protected -> {
                    Text(
                        stringResource(R.string.settings_lock_none_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { showPinDialog = true }) {
                        Text(stringResource(R.string.settings_lock_set))
                    }
                }

                unlocked -> {
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
                }

                else -> {
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

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 8

/**
 * Textfeld, das erst beim Verlassen speichert.
 * Bei jedem Tastendruck in DataStore zu schreiben waere unnoetig teuer und
 * wuerde den Cursor durch das Neuzeichnen springen lassen.
 */
@Composable
private fun DebouncedField(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    masked: Boolean = false,
) {
    var local by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = local,
        onValueChange = { local = it },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (masked) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
    if (local != value) {
        Button(onClick = { onCommit(local) }) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun NumberField(
    value: Int,
    label: String,
    description: String,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value.toString()) }
    val parsed = local.toIntOrNull()

    Column {
        OutlinedTextField(
            value = local,
            onValueChange = { local = it.filter(Char::isDigit) },
            label = { Text(label) },
            singleLine = true,
            isError = parsed == null || parsed <= 0,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(description, style = MaterialTheme.typography.bodySmall)
        if (parsed != null && parsed > 0 && parsed != value) {
            Button(onClick = { onCommit(parsed) }) { Text(stringResource(R.string.action_save)) }
        } else if (parsed == null || parsed <= 0) {
            Text(
                stringResource(R.string.settings_number_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Zeile je Berechtigung.
 *
 * Der Status kommt aus [rememberPermissionStatus] und wird bei jedem
 * ON_RESUME neu geprueft. Dadurch springt die Zeile auf "erteilt", sobald
 * der Benutzer aus den Systemeinstellungen zurueckkehrt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionRow(label: String, request: PermissionRequest) {
    val context = LocalContext.current
    val granted = rememberPermissionStatus(request)
    val gate = rememberPermissionGate(request) { }

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                stringResource(
                    if (granted) R.string.settings_perm_granted else R.string.settings_perm_missing,
                ),
                color = if (granted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        },
        trailingContent = {
            if (!granted) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = gate) {
                        Text(stringResource(R.string.settings_perm_request))
                    }
                    // Zweiter Weg fuer den Fall, dass Android gar nicht mehr
                    // fragt. Ohne ihn bleibt "Anfordern" wirkungslos und der
                    // Benutzer sitzt fest.
                    TextButton(onClick = { openAppSettings(context) }) {
                        Text(stringResource(R.string.perm_open_settings))
                    }
                }
            }
        },
    )
}
