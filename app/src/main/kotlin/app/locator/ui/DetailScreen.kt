package app.locator.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locator.R
import app.locator.data.LocationRecord
import app.locator.data.PhotoStore
import app.locator.data.UsualPlace
import app.locator.data.shareText
import app.locator.location.FixQuality
import app.locator.ui.theme.extras
import kotlinx.coroutines.launch

private fun formatDateTime(context: Context, millis: Long): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_ABBREV_WEEKDAY,
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(vm: DetailViewModel, photos: PhotoStore, onBack: () -> Unit, onOpenPlace: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val item = state.item
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.share_chooser)

    // The item is gone (deleted): leave the screen.
    LaunchedEffect(state.loaded, item == null) {
        if (state.loaded && item == null) onBack()
    }
    if (item == null) return

    val colors = MaterialTheme.colorScheme
    val buttonColors = IconButtonDefaults.iconButtonColors(containerColor = colors.background.copy(alpha = 0.6f), contentColor = colors.onSurface)

    Scaffold(containerColor = colors.background, contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Box(Modifier.fillMaxWidth().height(250.dp)) {
                    val photo = item.currentPhoto
                    if (photo != null) {
                        PhotoImage(photos, photo, Modifier.fillMaxSize(), maxEdge = 1600)
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh, colors.surfaceContainerLow)),
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = colors.primary.copy(alpha = 0.2f), modifier = Modifier.size(120.dp))
                        }
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0.5f), Color.Transparent, colors.background), 0f, Float.POSITIVE_INFINITY)))
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = onBack, colors = buttonColors) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { vm.setPinned(!item.item.pinned) }, colors = buttonColors) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = stringResource(if (item.item.pinned) R.string.action_unpin else R.string.action_pin),
                                    tint = if (item.item.pinned) colors.primary else colors.onSurface,
                                )
                            }
                            IconButton(onClick = { editing = true }, colors = buttonColors) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                        }
                    }
                    if (photo != null && item.currentAt != null) {
                        Text(
                            stringResource(R.string.detail_photo_from, formatDateTime(context, item.currentAt)).uppercase(),
                            Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurface.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            (item.item.emoji?.let { "$it " }.orEmpty()) + item.item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            item.currentDescription ?: item.currentPlaceId?.let { state.placePaths[it] }
                                ?: if (item.hasGps) gpsLabel(item.currentAccuracy) else stringResource(R.string.item_no_location),
                            style = MaterialTheme.typography.displaySmall,
                            color = if (item.currentRecordId != null) colors.onSurface else colors.onSurfaceVariant,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.hasGps) {
                            val good = item.currentAccuracy?.let { !FixQuality.isPoor(it) } ?: false
                            InfoChip(container = colors.surfaceContainerHighest) {
                                StatusDot(good)
                                Text(
                                    stringResource(if (good) R.string.gps_good else R.string.gps_weak, item.currentAccuracy?.toInt() ?: 0),
                                    Modifier.padding(start = 6.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                )
                            }
                        }
                        val currentPlace = item.currentPlaceId?.let { state.placePaths[it] }
                        if (currentPlace != null) {
                            // The chip is 32dp high; the clickable area around it is the 48dp touch target.
                            Box(
                                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(
                                    onClickLabel = stringResource(R.string.detail_open_place),
                                    role = Role.Button,
                                ) { onOpenPlace(item.currentPlaceId!!) },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                InfoChip(container = colors.surfaceContainerHighest) {
                                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.primary)
                                    Text(currentPlace, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
                                }
                            }
                        }
                        item.currentAt?.let {
                            InfoChip {
                                Text(
                                    stringResource(R.string.detail_saved_ago, ageLabel(it)),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = colors.onSurface.copy(alpha = 0.85f),
                                )
                            }
                        }
                        val remindAt = item.currentRemindAt
                        if (remindAt != null && remindAt > System.currentTimeMillis()) {
                            InfoChip(container = MaterialTheme.extras.warningContainer) {
                                Text(
                                    stringResource(
                                        R.string.detail_reminder_chip,
                                        DateUtils.formatDateTime(context, remindAt, DateUtils.FORMAT_SHOW_TIME),
                                    ),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                    color = MaterialTheme.extras.warning,
                                )
                            }
                            TextButton(onClick = { item.currentRecordId?.let(vm::cancelReminder) }) {
                                Text(stringResource(R.string.reminder_cancel))
                            }
                        }
                    }
                    item.currentNote?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

                    val usual = item.item.usualText?.let { text ->
                        item.item.usualPlaceId?.let { state.placePaths[it] }?.let { "$text · $it" } ?: text
                    } ?: item.item.usualPlaceId?.let { state.placePaths[it] }
                    if (usual != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.usual_row, usual),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                            if (item.isOutOfPlace(state.places)) {
                                PillButton(onClick = vm::putBack, modifier = Modifier.height(48.dp)) {
                                    Text(stringResource(R.string.action_put_back), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }

                    if (item.hasGps) {
                        val lat = item.currentLatitude!!
                        val lon = item.currentLongitude!!
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerLow).padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                coordinates(lat, lon),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            )
                            TextButton(onClick = {
                                scope.launch { clipboard.setClipEntry(ClipData.newPlainText("coordinates", coordinates(lat, lon)).toClipEntry()) }
                            }, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.action_copy)) }
                        }
                    }

                    if (item.hasGps) {
                        CompassCard(vm, item.currentLatitude!!, item.currentLongitude!!)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.hasGps) {
                            val lat = item.currentLatitude!!
                            val lon = item.currentLongitude!!
                            PillButton(
                                onClick = {
                                    val label = Uri.encode(item.item.name)
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$lat,$lon($label)"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: ActivityNotFoundException) {
                                    }
                                },
                                modifier = Modifier.weight(1.4f).height(52.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.action_navigate), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        PillOutlinedButton(
                            onClick = {
                                val where = item.currentDescription ?: item.currentPlaceId?.let { state.placePaths[it] }
                                val text = shareText(item.item.name, where, item.currentLatitude, item.currentLongitude)
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                try {
                                    context.startActivity(Intent.createChooser(send, shareTitle))
                                } catch (_: ActivityNotFoundException) {
                                }
                            },
                            enabled = item.currentRecordId != null,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text(stringResource(R.string.action_share), style = MaterialTheme.typography.labelLarge)
                        }
                        PillOutlinedButton(onClick = vm::undo, enabled = state.history.isNotEmpty(), modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(stringResource(R.string.action_undo), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.detail_history), Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)) }
            if (state.history.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.detail_history_empty),
                        Modifier.padding(horizontal = 20.dp),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            items(state.history.size, key = { state.history[it].id }) { index ->
                HistoryEntry(state.history[index], first = index == 0, last = index == state.history.lastIndex, photos = photos, placePaths = state.placePaths)
            }
            item {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { vm.setArchived(!item.item.archived) }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant)) {
                        Text(stringResource(if (item.item.archived) R.string.action_restore else R.string.action_archive))
                    }
                    TextButton(onClick = { confirmDelete = true }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = colors.error)) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }

    if (editing) {
        ItemFormDialog(
            title = stringResource(R.string.item_edit_title),
            initialName = item.item.name,
            initialEmoji = item.item.emoji.orEmpty(),
            initialCaptureGps = item.item.captureGps,
            places = state.places,
            initialUsual = UsualPlace(item.item.usualPlaceId, item.item.usualText),
            onCreatePlace = vm::createPlace,
            onDismiss = { editing = false },
            onConfirm = vm::edit,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = colors.surfaceContainerHigh,
            title = { Text(stringResource(R.string.delete_confirm_title, item.item.name)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) {
                    Text(stringResource(R.string.action_delete), color = colors.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** One step of the history timeline: a dot on a vertical line, the place, and a line of facts. */
@Composable
private fun HistoryEntry(record: LocationRecord, first: Boolean, last: Boolean, photos: PhotoStore, placePaths: Map<Long, String>) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val gps = record.latitude?.let { stringResource(R.string.gps_short, record.accuracyMeters?.toInt() ?: 0) }
    val photoWord = stringResource(R.string.meta_photo)
    val noteWord = stringResource(R.string.meta_note)
    val facts = listOfNotNull(
        formatDateTime(context, record.createdAt),
        gps,
        photoWord.takeIf { record.photoPath != null },
        noteWord.takeIf { record.note != null },
    ).joinToString(" · ")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(12.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.padding(top = 5.dp).size(12.dp).then(
                    if (first) Modifier.background(colors.primary, CircleShape) else Modifier.border(2.dp, colors.outline, CircleShape),
                ),
            )
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(colors.outlineVariant))
        }
        Row(Modifier.weight(1f).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    record.description ?: record.placeId?.let { placePaths[it] } ?: stringResource(R.string.location_gps, record.accuracyMeters?.toInt() ?: 0),
                    style = if (first) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                    color = if (first) colors.onSurface else colors.onSurface.copy(alpha = 0.85f),
                )
                if (record.description != null) {
                    record.placeId?.let { placePaths[it] }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Text(facts, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                record.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface.copy(alpha = 0.85f)) }
            }
            record.photoPath?.let {
                PhotoImage(photos, it, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), maxEdge = 300)
            }
        }
    }
}
