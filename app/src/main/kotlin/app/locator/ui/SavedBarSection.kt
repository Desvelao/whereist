package app.locator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.locator.R
import app.locator.data.PhotoStore
import app.locator.ui.theme.extras

/** Reminder presets, in minutes. */
val ReminderPresets = listOf(30, 60, 90, 120, 180)

@Composable
fun reminderLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.reminder_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.reminder_hours, minutes / 60)
    else -> stringResource(R.string.reminder_hours_minutes, minutes / 60, minutes % 60)
}

/** What the buttons after a save do. Each one asks for what it needs (camera app, notification permission). */
class SavedActions(val photo: () -> Unit, val note: () -> Unit, val reminder: () -> Unit)

/**
 * Holds the photo, note and reminder behaviour that follows a save, and draws [content] with the actions.
 * The two surfaces that show it (Home's floating card, the quick-save panel) differ only in looks.
 */
@Composable
fun SavedActionsHost(
    saved: SavedBar,
    photos: PhotoStore,
    onPhoto: (recordId: Long, path: String) -> Unit,
    onNote: (recordId: Long, note: String) -> Unit,
    onReminder: (recordId: Long, minutes: Int) -> Unit,
    onMessage: (Int) -> Unit,
    onInteract: () -> Unit = {},
    content: @Composable (SavedActions) -> Unit,
) {
    val context = LocalContext.current
    var noting by remember { mutableStateOf(false) }
    var timing by remember { mutableStateOf(false) }
    var pendingMinutes by remember { mutableStateOf<Int?>(null) }
    val takePhoto = rememberPhotoCapture(photos, onPhoto)
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val minutes = pendingMinutes
        pendingMinutes = null
        if (minutes != null) {
            if (granted) onReminder(saved.recordId, minutes) else onMessage(R.string.reminder_permission_denied)
        }
    }

    fun startReminder(minutes: Int) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingMinutes = minutes
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onReminder(saved.recordId, minutes)
        }
    }

    content(
        SavedActions(
            photo = { onInteract(); takePhoto(saved.recordId) },
            note = { onInteract(); noting = true },
            reminder = { onInteract(); timing = true },
        ),
    )

    if (noting) {
        NoteDialog(onDismiss = { noting = false }, onConfirm = { onNote(saved.recordId, it) })
    }
    if (timing) {
        ReminderDialog(onDismiss = { timing = false }, onConfirm = ::startReminder)
    }
}

/** Round badge: amber warning for a weak fix, teal check otherwise. */
@Composable
fun SaveBadge(warning: Boolean, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val container = if (warning) MaterialTheme.extras.warning else MaterialTheme.colorScheme.primary
    val content = if (warning) MaterialTheme.extras.onWarning else MaterialTheme.colorScheme.onPrimary
    Box(Modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            if (warning) Icons.Filled.Warning else Icons.Filled.Check,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size / 2),
        )
    }
}

/** Home's floating card after a save: add a photo or a note, set a reminder, or undo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedBarSection(
    saved: SavedBar,
    photos: PhotoStore,
    onPhoto: (recordId: Long, path: String) -> Unit,
    onNote: (recordId: Long, note: String) -> Unit,
    onReminder: (recordId: Long, minutes: Int) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onMessage: (Int) -> Unit,
    onInteract: () -> Unit = {},
) {
    SavedActionsHost(saved, photos, onPhoto, onNote, onReminder, onMessage, onInteract) { actions ->
        Surface(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SaveBadge(warning = saved.poorFixMeters != null)
                    Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.bar_saved, saved.itemName), style = MaterialTheme.typography.titleSmall)
                        saved.poorFixMeters?.let {
                            Text(
                                stringResource(R.string.bar_poor_fix, it),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FlowRow(
                    Modifier.padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PillButton(onClick = actions.photo) { Text(stringResource(R.string.action_add_photo)) }
                    PillOutlinedButton(onClick = actions.note) { Text(stringResource(R.string.action_add_note)) }
                    PillOutlinedButton(onClick = actions.reminder) { Text(stringResource(R.string.action_reminder)) }
                    TextButton(onClick = onUndo, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Text(stringResource(R.string.action_undo_short))
                    }
                }
            }
        }
    }
}

@Composable
fun NoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.action_add_note)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.save_note_hint)) },
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note); onDismiss() }, enabled = note.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(onDismiss: () -> Unit, onConfirm: (minutes: Int) -> Unit) {
    var custom by remember { mutableStateOf("") }
    val customMinutes = custom.toIntOrNull()?.takeIf { it in 1..MAX_REMINDER_MINUTES }
    val context = LocalContext.current
    // Only when the permission is fine but notifications are switched off in the system settings; before the
    // permission was ever asked, the normal flow asks for it.
    val permissionOk = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val notificationsOff = permissionOk && !NotificationManagerCompat.from(context).areNotificationsEnabled()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.reminder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.reminder_hint), style = MaterialTheme.typography.bodyMedium)
                if (notificationsOff) {
                    Text(stringResource(R.string.reminder_notifications_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderPresets.forEach { minutes ->
                        SuggestionChip(
                            onClick = { onConfirm(minutes); onDismiss() },
                            label = { Text(reminderLabel(minutes)) },
                            shape = CircleShape,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.reminder_custom)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(customMinutes!!); onDismiss() }, enabled = customMinutes != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_REMINDER_MINUTES = 24 * 60
