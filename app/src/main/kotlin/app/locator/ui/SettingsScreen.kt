package app.locator.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import app.locator.AppLanguage
import app.locator.LocatorApp
import app.locator.ui.theme.ThemeMode
import app.locator.BuildConfig
import app.locator.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp)).padding(vertical = 6.dp, horizontal = 14.dp),
    ) { content() }
}

/** Appearance: follow the system, or force light or dark. */
@Composable
private fun AppearanceSelect() {
    val store = ((LocalContext.current.applicationContext) as LocatorApp).container.theme
    val mode by store.mode.collectAsStateWithLifecycle()
    Card {
        ThemeMode.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                    selected = option == mode,
                    onClick = { store.set(option) },
                    role = Role.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == mode, onClick = null)
                Text(
                    stringResource(
                        when (option) {
                            ThemeMode.SYSTEM -> R.string.appearance_system
                            ThemeMode.LIGHT -> R.string.appearance_light
                            ThemeMode.DARK -> R.string.appearance_dark
                        },
                    ),
                    Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** A select for the app language: the row shows the current choice and opens a list of languages. */
@Composable
private fun LanguageSelect() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppLanguage.current(context)) }
    var open by remember { mutableStateOf(false) }
    val systemLabel = stringResource(R.string.language_system)
    fun label(tag: String?) = if (tag == null) systemLabel else AppLanguage.displayName(tag)
    Card {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.DropdownList) { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_language), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(label(selected), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    (listOf<String?>(null) + AppLanguage.supported).forEach { tag ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = tag == selected,
                                onClick = {
                                    open = false
                                    if (tag != selected) {
                                        selected = tag
                                        AppLanguage.set(context, tag)
                                        // Android 13+ recreates the activity itself; older versions need it.
                                        if (android.os.Build.VERSION.SDK_INT < 33) (context as? android.app.Activity)?.recreate()
                                    }
                                },
                                role = Role.RadioButton,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = tag == selected, onClick = null)
                            Text(label(tag), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val archived by vm.archived.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val gpsItems by vm.gpsItems.collectAsStateWithLifecycle()
    val quickId by vm.quickItemId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri, context.contentResolver)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }
    val statusText = when (val s = status) {
        BackupStatus.Idle, BackupStatus.Working -> null
        BackupStatus.Exported -> stringResource(R.string.backup_exported)
        is BackupStatus.Imported -> stringResource(R.string.backup_imported, s.items, s.records)
        BackupStatus.Invalid -> stringResource(R.string.backup_invalid)
        BackupStatus.Failed -> stringResource(R.string.backup_failed)
    }
    LaunchedEffect(statusText) {
        if (statusText != null) {
            snackbar.showSnackbar(statusText)
            vm.statusShown()
        }
    }
    val backupName = stringResource(R.string.app_name).filter { it.isLetterOrDigit() }.lowercase()
    val today = remember { SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
                }
            }

            item { SectionLabel(stringResource(R.string.settings_appearance), Modifier.padding(start = 4.dp, top = 12.dp)) }
            item { AppearanceSelect() }

            item { SectionLabel(stringResource(R.string.settings_language), Modifier.padding(start = 4.dp, top = 12.dp)) }
            item { LanguageSelect() }

            item { SectionLabel(stringResource(R.string.quick_title), Modifier.padding(start = 4.dp, top = 12.dp)) }
            item {
                Text(
                    stringResource(R.string.quick_hint),
                    Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Card {
                    if (gpsItems.isEmpty()) {
                        Text(
                            stringResource(R.string.quick_no_item),
                            Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    gpsItems.forEach { gps ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = gps.id == quickId,
                                onClick = { vm.selectQuickItem(gps.id) },
                                role = Role.RadioButton,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = gps.id == quickId, onClick = null)
                            Text(
                                "${gps.emoji ?: stringResource(R.string.item_default_emoji)} ${gps.name}",
                                Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.backup_title), Modifier.padding(start = 4.dp, top = 12.dp)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillOutlinedButton(onClick = { exportLauncher.launch("$backupName-backup-$today.zip") }, enabled = status != BackupStatus.Working) {
                        Text(stringResource(R.string.backup_export))
                    }
                    PillOutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = status != BackupStatus.Working) {
                        Text(stringResource(R.string.backup_import))
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.settings_archived), Modifier.padding(start = 4.dp, top = 12.dp)) }
            item {
                Card {
                    if (archived.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_archived_empty),
                            Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    archived.forEach { row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${row.item.emoji ?: stringResource(R.string.item_default_emoji)} ${row.item.name}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            TextButton(onClick = { vm.restore(row.item.id) }) { Text(stringResource(R.string.action_restore)) }
                            TextButton(
                                onClick = { vm.delete(row.item.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.action_delete)) }
                        }
                    }
                }
            }

            item {
                val versionLabel = stringResource(R.string.settings_version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { showAbout = true }, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
                        Text(versionLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showAbout) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showAbout = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.settings_about)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodyMedium)
                    Text(BuildConfig.APP_URL, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAbout = false
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.APP_URL)))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.about_open)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(BuildConfig.APP_URL)); showAbout = false }) { Text(stringResource(R.string.about_copy)) }
                    TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmImport = null; vm.import(uri, context.contentResolver) }) {
                    Text(stringResource(R.string.action_import), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
