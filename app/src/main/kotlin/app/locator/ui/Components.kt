package app.locator.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import app.locator.ui.theme.extras
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.locator.R
import app.locator.data.Age
import app.locator.data.AgeFormatter
import app.locator.data.Place
import app.locator.data.PlaceTree
import app.locator.data.UsualPlace

fun ageText(context: Context, thenMillis: Long): String = when (val age = AgeFormatter.age(System.currentTimeMillis(), thenMillis)) {
    Age.JustNow -> context.getString(R.string.age_now)
    is Age.Minutes -> context.getString(R.string.age_minutes, age.n)
    is Age.Hours -> context.getString(R.string.age_hours, age.n)
    is Age.Days -> context.getString(R.string.age_days, age.n)
}

@Composable
fun ageLabel(thenMillis: Long): String = ageText(LocalContext.current, thenMillis)

@Composable
fun gpsLabel(accuracy: Float?): String =
    stringResource(R.string.location_gps, accuracy?.toInt() ?: 0)

fun coordinates(latitude: Double, longitude: Double): String =
    String.format(java.util.Locale.ROOT, "%.5f, %.5f", latitude, longitude)

/** Small upper-case section heading ("PINNED", "HISTORY"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Rounded tile that holds an item's emoji. */
@Composable
fun IconTile(text: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

/** The small coloured status dot used for "GPS ±8 m". */
@Composable
fun StatusDot(good: Boolean, modifier: Modifier = Modifier) {
    val color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.extras.warning
    Box(modifier.size(8.dp).background(color, CircleShape))
}

/** Pill-shaped chip for facts about a location (accuracy, age, reminder). */
@Composable
fun InfoChip(
    modifier: Modifier = Modifier,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.defaultMinSize(minHeight = 32.dp).background(container, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Filled teal pill: the one main action of a screen or a row. */
@Composable
fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun PillOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        content = content,
    )
}

/** What the new-item form collects: the item, its usual place and, optionally, where it is now. */
data class NewItemInput(
    val name: String,
    val emoji: String?,
    val captureGps: Boolean,
    val usual: UsualPlace,
    val locationText: String?,
    val locationPlaceId: Long?,
    val gps: app.locator.location.Fix? = null,
    /** A photo already stored through [PhotoStore]; it is attached to the first location. */
    val photoPath: String? = null,
)

/**
 * Name + emoji form, used to add and to edit an item. With [onSubmitNew] it is the new-item form: it also asks for
 * the current location and offers "Save and add another".
 */
@Composable
fun ItemFormDialog(
    title: String,
    initialName: String = "",
    initialEmoji: String = "",
    initialCaptureGps: Boolean = false,
    /** When set, the form also edits the item's usual place (picked from these places). */
    places: List<Place>? = null,
    initialUsual: UsualPlace = UsualPlace.NONE,
    onCreatePlace: suspend (name: String, parentId: Long?) -> Long? = { _, _ -> null },
    onDismiss: () -> Unit,
    /** New-item form only: a GPS fix for "Here" (the form asks for the permission itself), and the photo store. */
    onFix: (suspend () -> app.locator.location.Fix?)? = null,
    photos: app.locator.data.PhotoStore? = null,
    onSubmitNew: ((input: NewItemInput, addAnother: Boolean) -> Unit)? = null,
    onConfirm: (name: String, emoji: String?, captureGps: Boolean, usual: UsualPlace) -> Unit = { _, _, _, _ -> },
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    var captureGps by remember { mutableStateOf(initialCaptureGps) }
    var usualPlaceId by remember { mutableStateOf(initialUsual.placeId) }
    var usualText by remember { mutableStateOf(initialUsual.text.orEmpty()) }
    var pickingPlace by remember { mutableStateOf(false) }
    var locationText by remember { mutableStateOf("") }
    var locationPlaceId by remember { mutableStateOf<Long?>(null) }
    var pickingLocation by remember { mutableStateOf(false) }
    val nameFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Staged "Here" fix as [lat, lon, accuracy] and photo path; both survive rotation and the camera round trip.
    var fixValues by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var fixing by remember { mutableStateOf(false) }
    var hereError by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fix = fixValues?.let { app.locator.location.Fix(it[0], it[1], it[2].toFloat()) }
    val hasLocation = locationText.isNotBlank() || locationPlaceId != null || fix != null
    val canSave = name.isNotBlank() && (photoPath == null || hasLocation)
    fun input() = NewItemInput(
        name, emoji.ifBlank { null }, captureGps, UsualPlace(usualPlaceId, usualText.ifBlank { null }),
        locationText.ifBlank { null }, locationPlaceId, fix, photoPath,
    )
    fun discardPhoto() {
        photos?.delete(photoPath)
        photoPath = null
    }
    fun dismiss() {
        discardPhoto()
        onDismiss()
    }
    fun takeFix() {
        if (fixing || onFix == null) return
        scope.launch {
            fixing = true
            hereError = null
            val result = try { onFix() } finally { fixing = false }
            if (result == null) hereError = R.string.here_no_fix else fixValues = doubleArrayOf(result.latitude, result.longitude, result.accuracyMeters.toDouble())
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) takeFix() else hereError = R.string.here_permission_denied
    }
    val takePhoto = photos?.let {
        rememberPhotoCapture(it) { _, path ->
            if (photoPath != path) photos.delete(photoPath)
            photoPath = path
        }
    }
    AlertDialog(
        onDismissRequest = ::dismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.item_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.focusRequester(nameFocus),
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(8) },
                    label = { Text(stringResource(R.string.item_emoji)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.item_capture_gps), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = captureGps, onCheckedChange = { captureGps = it })
                }
                if (onSubmitNew != null && places != null) {
                    SectionLabel(stringResource(R.string.item_location_section), Modifier.padding(top = 16.dp))
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        label = { Text(stringResource(R.string.save_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { pickingLocation = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(PlaceTree.pathOf(places, locationPlaceId) ?: stringResource(R.string.place_field_label))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onFix != null) {
                            PillOutlinedButton(
                                onClick = {
                                    val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                                    if (granted) takeFix() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                },
                                enabled = !fixing,
                            ) { Text(stringResource(R.string.action_here)) }
                        }
                        if (takePhoto != null) {
                            PillOutlinedButton(onClick = { takePhoto(0L) }) { Text(stringResource(R.string.action_add_photo)) }
                        }
                    }
                    if (fixing) Text(stringResource(R.string.item_fixing), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    if (fix != null) {
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.location_gps, fix.accuracyMeters.toInt()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { fixValues = null }) { Text(stringResource(R.string.action_remove)) }
                        }
                    }
                    hereError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    if (photoPath != null && photos != null) {
                        PhotoImage(photos, photoPath!!, Modifier.padding(top = 8.dp).size(96.dp).clip(RoundedCornerShape(12.dp)), maxEdge = 256)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (hasLocation) stringResource(R.string.item_photo_added) else stringResource(R.string.item_photo_needs_location),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasLocation) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = ::discardPhoto) { Text(stringResource(R.string.action_remove)) }
                        }
                    }
                }
                if (places != null) {
                    val usualPath = PlaceTree.pathOf(places, usualPlaceId)
                    OutlinedTextField(
                        value = usualText,
                        onValueChange = { usualText = it },
                        label = { Text(stringResource(R.string.usual_text)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    TextButton(onClick = { pickingPlace = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(usualPath ?: stringResource(R.string.usual_pick_place))
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (onSubmitNew != null) {
                    TextButton(
                        onClick = {
                            onSubmitNew(input(), true)
                            name = ""
                            emoji = ""
                            locationText = ""
                            locationPlaceId = null
                            fixValues = null
                            photoPath = null
                            hereError = null
                            runCatching { nameFocus.requestFocus() }
                        },
                        enabled = canSave,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.action_save_add_another)) }
                }
                TextButton(
                    onClick = {
                        if (onSubmitNew != null) {
                            onSubmitNew(input(), false)
                        } else {
                            onConfirm(name, emoji.ifBlank { null }, captureGps, UsualPlace(usualPlaceId, usualText.ifBlank { null }))
                        }
                        photoPath = null
                        onDismiss()
                    },
                    enabled = canSave,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = ::dismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingPlace && places != null) {
        PlacePickerDialog(
            title = stringResource(R.string.usual_picker_title),
            places = places,
            selectedId = usualPlaceId,
            onSelect = { usualPlaceId = it },
            onCreate = onCreatePlace,
            onDismiss = { pickingPlace = false },
        )
    }
    if (pickingLocation && places != null) {
        PlacePickerDialog(
            title = stringResource(R.string.place_picker_title),
            places = places,
            selectedId = locationPlaceId,
            onSelect = { locationPlaceId = it },
            onCreate = onCreatePlace,
            onDismiss = { pickingLocation = false },
        )
    }
}

