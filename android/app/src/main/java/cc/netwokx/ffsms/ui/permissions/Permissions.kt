package cc.netwokx.ffsms.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cc.netwokx.ffsms.R

fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Oeffnet die Systemeinstellungen dieser App.
 *
 * Der einzige Weg zurueck, wenn Android eine Berechtigung dauerhaft verweigert
 * hat: der Anfragedialog erscheint dann gar nicht mehr, der Systemaufruf kommt
 * sofort als "abgelehnt" zurueck.
 */
fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * Beobachtet den Status einer Berechtigung und prueft ihn bei jedem
 * ON_RESUME neu.
 *
 * Noetig, weil der Benutzer die Berechtigung in den Systemeinstellungen
 * setzt und dann zurueckkommt: eine einmalige Pruefung beim Aufbau des
 * Bildschirms wuerde weiterhin "fehlt" anzeigen, obwohl alles erteilt ist.
 */
@Composable
fun rememberPermissionStatus(request: PermissionRequest): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasPermission(context, request.permission)) }

    DisposableEffect(lifecycleOwner, request) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasPermission(context, request.permission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return granted
}

enum class PermissionRequest(
    val permission: String,
    val titleRes: Int,
    val textRes: Int,
    /** Text fuer den Fall, dass Android die Berechtigung verweigert hat. */
    val deniedTextRes: Int,
) {
    SMS(
        Manifest.permission.SEND_SMS,
        R.string.perm_sms_title,
        R.string.perm_sms_text,
        R.string.perm_denied_sms,
    ),
    CONTACTS(
        Manifest.permission.READ_CONTACTS,
        R.string.perm_contacts_title,
        R.string.perm_contacts_text,
        R.string.perm_denied_contacts,
    ),
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            Manifest.permission.INTERNET
        },
        R.string.perm_notifications_title,
        R.string.perm_notifications_text,
        R.string.perm_denied_notifications,
    ),
}

/**
 * Fragt eine Berechtigung an und zeigt VORHER eine Begruendung im Klartext.
 *
 * Der Systemdialog allein erklaert nicht, warum eine Feuerwehr-App SMS
 * versenden will. Gerade bei SEND_SMS ist die Begruendung wichtig, damit die
 * Berechtigung nicht reflexhaft abgelehnt wird.
 *
 * Wird die Anfrage abgelehnt, folgt ein zweiter Dialog mit dem Weg in die
 * Systemeinstellungen. Ohne den landet der Benutzer in einer Sackgasse: hat
 * Android die Berechtigung einmal dauerhaft verweigert, erscheint der
 * Systemdialog nicht mehr, der Aufruf kommt sofort als "abgelehnt" zurueck -
 * und auf dem Bildschirm passiert sichtbar nichts.
 */
@Composable
fun rememberPermissionGate(
    request: PermissionRequest,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) showDenied = true
        onResult(granted)
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                onResult(false)
            },
            title = { Text(stringResource(request.titleRes)) },
            text = { Text(stringResource(request.textRes)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(request.permission)
                }) { Text(stringResource(R.string.perm_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    onResult(false)
                }) { Text(stringResource(R.string.perm_deny)) }
            },
        )
    }

    if (showDenied) {
        AlertDialog(
            onDismissRequest = { showDenied = false },
            title = { Text(stringResource(R.string.perm_denied_title)) },
            text = { Text(stringResource(request.deniedTextRes)) },
            confirmButton = {
                TextButton(onClick = {
                    showDenied = false
                    openAppSettings(context)
                }) { Text(stringResource(R.string.perm_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showDenied = false }) {
                    Text(stringResource(R.string.action_later))
                }
            },
        )
    }

    return {
        if (hasPermission(context, request.permission)) onResult(true) else showRationale = true
    }
}
