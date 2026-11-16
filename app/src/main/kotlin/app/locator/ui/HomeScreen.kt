package app.locator.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locator.R
import app.locator.ui.theme.extras
import app.locator.data.ItemWithCurrent
import app.locator.data.PhotoStore
import app.locator.data.PlaceTree
import app.locator.location.FixQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    photos: PhotoStore,
    onOpenItem: (Long) -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val bar by vm.bar.collectAsStateWithLifecycle()
    val fixing by vm.fixing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<ItemWithCurrent?>(null) }
    var pendingHere by remember { mutableStateOf<ItemWithCurrent?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val target = pendingHere
        pendingHere = null
        if (target != null) {
            if (granted.values.any { it }) vm.captureHere(target) else vm.showMessage(R.string.here_permission_denied)
        }
    }
    fun here(target: ItemWithCurrent) {
        val hasLocation = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (hasLocation) {
            vm.captureHere(target)
        } else {
            pendingHere = target
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            vm.messageShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            bar?.let { saved ->
                SavedBarSection(
                    saved = saved,
                    photos = photos,
                    onPhoto = vm::attachPhoto,
                    onNote = vm::addNote,
                    onReminder = vm::setReminder,
                    onUndo = vm::undoBar,
                    onDismiss = vm::dismissBar,
                    onMessage = vm::showMessage,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(60.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_item))
            }
        },
    ) { padding ->
        val pinned = state.items.filter { it.item.pinned }
        val others = state.items.filterNot { it.item.pinned }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                    IconButton(
                        onClick = onOpenPlaces,
                        modifier = Modifier.size(48.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.action_places))
                    }
                    Spacer(Modifier.size(8.dp))
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(48.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                }
            }
            item { SearchPill(state.query, vm::setQuery) }
            if (state.outOfPlaceCount > 0) {
                item {
                    FilterChip(
                        selected = state.outOfPlaceOnly,
                        onClick = { vm.setOutOfPlaceOnly(!state.outOfPlaceOnly) },
                        label = { Text(stringResource(R.string.filter_out_of_place, state.outOfPlaceCount)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            when {
                !state.loaded -> Unit
                state.items.isEmpty() -> item {
                    Text(
                        text = if (state.query.isBlank()) stringResource(R.string.home_empty)
                        else stringResource(R.string.home_no_results, state.query),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    if (pinned.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.home_section_pinned), Modifier.padding(start = 4.dp, top = 8.dp)) }
                        items(pinned, key = { it.item.id }) { row ->
                            ItemCard(row, state.placePaths[row.currentPlaceId], row.item.id in state.outOfPlaceIds, fixing == row.item.id, { saving = row }, { here(row) }, { onOpenItem(row.item.id) })
                        }
                    }
                    if (others.isNotEmpty()) {
                        if (pinned.isNotEmpty()) {
                            item { SectionLabel(stringResource(R.string.home_section_other), Modifier.padding(start = 4.dp, top = 10.dp)) }
                        }
                        items(others, key = { it.item.id }) { row ->
                            ItemCard(row, state.placePaths[row.currentPlaceId], row.item.id in state.outOfPlaceIds, fixing == row.item.id, { saving = row }, { here(row) }, { onOpenItem(row.item.id) })
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        ItemFormDialog(
            title = stringResource(R.string.item_new_title),
            places = places,
            photos = photos,
            onFix = vm::currentFix,
            onCreatePlace = vm::createPlace,
            onDismiss = { adding = false },
            onSubmitNew = { input, _ -> vm.addItem(input) },
        )
    }
    saving?.let { target ->
        SaveSheet(vm, target, onDismiss = { saving = null })
    }
}

@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/**
 * One item. The location is the biggest line (it is the answer to "where is it?"). Tapping the card opens the
 * save sheet; tapping the icon tile opens the details.
 */
@Composable
private fun ItemCard(row: ItemWithCurrent, placePath: String?, outOfPlace: Boolean, fixing: Boolean, onSave: () -> Unit, onHere: () -> Unit, onDetails: () -> Unit) {
    val hasLocation = row.currentRecordId != null
    val shape = RoundedCornerShape(20.dp)
    val dash = MaterialTheme.colorScheme.outlineVariant
    val base = Modifier.fillMaxWidth().clip(shape)
    val surface = if (hasLocation) base.background(MaterialTheme.colorScheme.surfaceContainerLow)
    else base.drawBehind {
        drawRoundRect(
            color = dash,
            cornerRadius = CornerRadius(20.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
        )
    }
    Row(
        surface.clickable(onClick = onSave).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(
            row.item.emoji ?: stringResource(R.string.item_default_emoji),
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = stringResource(R.string.action_details), onClick = onDetails),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.item.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = when {
                    row.currentDescription != null -> row.currentDescription
                    placePath != null -> placePath
                    row.hasGps -> gpsLabel(row.currentAccuracy)
                    else -> stringResource(R.string.item_tap_to_save)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (hasLocation) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasLocation) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.currentDescription != null && placePath != null) {
                        Text(
                            placePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (row.hasGps) {
                        StatusDot(good = row.currentAccuracy?.let { !FixQuality.isPoor(it) } ?: false)
                        Text(
                            stringResource(R.string.gps_short, row.currentAccuracy?.toInt() ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    row.currentAt?.let {
                        Text(ageLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (outOfPlace) {
                        Text(
                            stringResource(R.string.out_of_place),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.extras.warning,
                        )
                    }
                }
            }
        }
        if (row.item.captureGps) {
            if (fixing) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
            } else {
                PillButton(onClick = onHere, contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.action_here))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SaveSheet(vm: HomeViewModel, target: ItemWithCurrent, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var placeId by remember { mutableStateOf(target.currentPlaceId) }
    var pickingPlace by remember { mutableStateOf(false) }
    val places by vm.places.collectAsStateWithLifecycle()
    val placePath = remember(places, placeId) { PlaceTree.pathOf(places, placeId) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(target.item.id) {
        suggestions = vm.suggestions(target.item.id)
        focus.requestFocus()
    }

    // A place alone is enough to save, but not the place the item is already in: that would be a duplicate.
    val canSave = description.isNotBlank() || (placeId != null && placeId != target.currentPlaceId)

    fun save(text: String) {
        vm.saveLocation(target, text.ifBlank { null }, note.ifBlank { null }, placeId)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(target.item.emoji ?: stringResource(R.string.item_default_emoji), size = 44.dp)
                Text(stringResource(R.string.save_title, target.item.name), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text(stringResource(R.string.save_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { pickingPlace = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.place_field_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        placePath ?: stringResource(R.string.place_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (placePath != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.save_suggestions))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s ->
                            val match = description.isNotBlank() && s.startsWith(description.trim(), ignoreCase = true)
                            SuggestionChip(
                                onClick = { save(s) },
                                label = { Text(s, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)) },
                                modifier = Modifier.height(44.dp),
                                shape = CircleShape,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (match) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent,
                                ),
                                border = BorderStroke(
                                    if (match) 1.5.dp else 1.dp,
                                    if (match) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ),
                            )
                        }
                    }
                    Text(stringResource(R.string.save_tip), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.save_note_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            PillButton(onClick = { save(description) }, enabled = canSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (pickingPlace) {
        PlacePickerDialog(
            title = stringResource(R.string.place_picker_title),
            places = places,
            selectedId = placeId,
            onSelect = { placeId = it },
            onCreate = vm::createPlace,
            onDismiss = { pickingPlace = false },
        )
    }
}
