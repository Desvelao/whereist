package app.locator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locator.R
import app.locator.data.ItemWithCurrent
import app.locator.data.Place
import app.locator.data.PlaceEntry
import app.locator.data.PlaceTree
import kotlinx.coroutines.launch

/**
 * Picks a place from the tree, or "no place". A new place can be created inside the highlighted one
 * (or at the top level when nothing is highlighted).
 */
@Composable
fun PlacePickerDialog(
    title: String,
    places: List<Place>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: suspend (name: String, parentId: Long?) -> Long?,
    onDismiss: () -> Unit,
    noneLabel: String = stringResource(R.string.place_none),
    excluded: Set<Long> = emptySet(),
) {
    var highlight by remember { mutableStateOf(selectedId) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val paths = remember(places) { PlaceTree.paths(places) }
    val rows = remember(places, excluded) { PlaceTree.ordered(places).filter { it.first.id !in excluded } }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    item {
                        PickerRow(label = noneLabel, depth = 0, selected = highlight == null) { highlight = null }
                    }
                    items(rows, key = { it.first.id }) { (place, depth) ->
                        PickerRow(label = place.name, depth = depth, selected = highlight == place.id) { highlight = place.id }
                    }
                }
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.place_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { creating = false; newName = "" }) { Text(stringResource(R.string.action_cancel)) }
                        TextButton(
                            enabled = newName.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    onCreate(newName, highlight)?.let { highlight = it }
                                    creating = false
                                    newName = ""
                                }
                            },
                        ) { Text(stringResource(R.string.action_save)) }
                    }
                } else {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            highlight?.let { stringResource(R.string.place_new_inside, paths[it] ?: "") } ?: stringResource(R.string.place_new_top),
                            Modifier.padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(highlight); onDismiss() }) { Text(stringResource(R.string.place_choose)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PickerRow(label: String, depth: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(start = (depth * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Browse a place: the places inside it, and everything stored in it or below it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacesScreen(
    vm: PlacesViewModel,
    onBack: () -> Unit,
    onOpenPlace: (Long) -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val view by vm.view.collectAsStateWithLifecycle()
    val allPlaces by vm.places.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val messageText = message?.let { stringResource(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            vm.messageShown()
        }
    }
    val current = view?.current
    // The place was deleted: leave the screen.
    LaunchedEffect(view?.exists) {
        if (view?.exists == false) onBack()
    }
    val content = view ?: return

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
                    Text(
                        current?.name ?: stringResource(R.string.places_title),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (current != null && content.breadcrumb.size > 1) {
                item {
                    Text(
                        content.paths[current.id].orEmpty(),
                        Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(onClick = { adding = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.place_new), Modifier.padding(start = 6.dp))
                    }
                    if (current != null) {
                        PillOutlinedButton(onClick = { renaming = true }) { Text(stringResource(R.string.place_rename)) }
                        PillOutlinedButton(onClick = { moving = true }) { Text(stringResource(R.string.place_move)) }
                        TextButton(
                            onClick = { deleting = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.place_delete)) }
                    }
                }
            }
            if (content.children.isEmpty() && current == null) {
                item {
                    Text(
                        stringResource(R.string.place_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(content.children, key = { "place-${it.place.id}" }) { entry ->
                PlaceCard(entry) { onOpenPlace(entry.place.id) }
            }
            if (current != null) {
                item { SectionLabel(stringResource(R.string.place_stored_here), Modifier.padding(start = 4.dp, top = 12.dp)) }
                if (content.items.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.place_nothing_here),
                            Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(content.items, key = { "item-${it.item.id}" }) { row ->
                    StoredItemCard(row, content.paths[row.currentPlaceId], onClick = { onOpenItem(row.item.id) })
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.place_new)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.place_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.add(name); adding = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (renaming && current != null) {
        var name by remember { mutableStateOf(current.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.place_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.place_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.rename(name); renaming = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (moving && current != null) {
        PlacePickerDialog(
            title = stringResource(R.string.place_move_title, current.name),
            places = allPlaces,
            selectedId = current.parentId,
            noneLabel = stringResource(R.string.place_all),
            excluded = PlaceTree.subtree(allPlaces, current.id),
            onSelect = vm::move,
            onCreate = vm::createPlace,
            onDismiss = { moving = false },
        )
    }
    if (deleting && current != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.place_delete_title, current.name)) },
            text = { Text(stringResource(R.string.place_delete_body)) },
            confirmButton = {
                TextButton(onClick = { deleting = false; vm.delete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PlaceCard(entry: PlaceEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.childCount > 0) stringResource(R.string.place_summary, entry.itemCount, entry.childCount)
                else stringResource(R.string.place_summary_things, entry.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StoredItemCard(row: ItemWithCurrent, placePath: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(row.item.emoji ?: stringResource(R.string.item_default_emoji))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.item.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(row.currentDescription ?: placePath.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.currentDescription != null && placePath != null) {
                    Text(placePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                row.currentAt?.let { Text(ageLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
