package cc.netwokx.ffsms.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import cc.netwokx.ffsms.R

fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** POST_NOTIFICATIONS gibt es erst ab Android 13; davor gilt sie als erteilt. */
fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)

enum class PermissionRequest(
    val permission: String,
    val titleRes: Int,
    val textRes: Int,
) {
    SMS(Manifest.permission.SEND_SMS, R.string.perm_sms_title, R.string.perm_sms_text),
    CONTACTS(Manifest.permission.READ_CONTACTS, R.string.perm_contacts_title, R.string.perm_contacts_text),
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            Manifest.permission.INTERNET
        },
        R.string.perm_notifications_title,
        R.string.perm_notifications_text,
    ),
}

/**
 * Fragt eine Berechtigung an und zeigt VORHER eine Begruendung im Klartext.
 *
 * Der Systemdialog allein erklaert nicht, warum eine Feuerwehr-App SMS
 * versenden will. Gerade bei SEND_SMS ist die Begruendung wichtig, damit die
 * Berechtigung nicht reflexhaft abgelehnt wird.
 */
@Composable
fun rememberPermissionGate(
    request: PermissionRequest,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onResult(granted) }

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

    return {
        if (hasPermission(context, request.permission)) onResult(true) else showRationale = true
    }
}
