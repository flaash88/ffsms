package cc.netwokx.ffsms.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
            SectionTitle(stringResource(R.string.settings_backend))

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
