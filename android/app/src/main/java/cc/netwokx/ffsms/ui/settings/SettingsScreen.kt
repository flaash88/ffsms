package cc.netwokx.ffsms.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
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
    onOpenServerConfig: () -> Unit,
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
            ServerConfigRow(
                backendUrl = settings.backendUrl,
                syncEnabled = settings.syncEnabled,
                pinProtected = settings.pinProtected,
                onOpen = onOpenServerConfig,
            )

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
 * Eingang zur Serverkonfiguration.
 *
 * Die eingestellte Adresse steht in der Zeile, obwohl der Bildschirm
 * dahinter gesperrt ist. Wer am Telefon gefragt wird, welcher Server
 * eingetragen ist, soll nachsehen koennen, ohne die PIN zu brauchen - und
 * das Nachsehen ist genau der Vorgang, bei dem sonst versehentlich etwas
 * verstellt wird. Der Schluessel steht nicht dabei: er ist ein Geheimnis
 * und kein Diagnosewert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerConfigRow(
    backendUrl: String,
    syncEnabled: Boolean,
    pinProtected: Boolean,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        ListItem(
            leadingContent = {
                Icon(
                    if (pinProtected) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                )
            },
            headlineContent = { Text(stringResource(R.string.server_config_title)) },
            supportingContent = {
                Column {
                    Text(backendUrl.ifBlank { stringResource(R.string.server_config_unset) })
                    Text(
                        stringResource(
                            if (syncEnabled) {
                                R.string.server_config_sync_on
                            } else {
                                R.string.server_config_sync_off
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
        )
    }
}


/**
 * Textfeld, das erst beim Verlassen speichert.
 * Bei jedem Tastendruck in DataStore zu schreiben waere unnoetig teuer und
 * wuerde den Cursor durch das Neuzeichnen springen lassen.
 */
@Composable
internal fun DebouncedField(
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
