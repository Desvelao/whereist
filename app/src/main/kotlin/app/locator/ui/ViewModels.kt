package app.locator.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locator.R
import app.locator.data.BackupException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import app.locator.data.BackupService
import app.locator.data.Item
import app.locator.data.UnknownItemException
import app.locator.data.ItemRepository
import app.locator.data.QuickItem
import app.locator.quick.QuickItemStore
import app.locator.data.ItemWithCurrent
import app.locator.data.LocationRecord
import app.locator.data.Place
import app.locator.data.PlaceBrowser
import app.locator.data.PlaceNameTakenException
import app.locator.data.PlaceTree
import app.locator.data.PlaceView
import app.locator.data.UsualPlace
import app.locator.data.matches
import app.locator.location.FixQuality
import app.locator.location.Geo
import app.locator.location.HeadingProvider
import app.locator.location.Fix
import app.locator.location.LocationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val query: String = "",
    val items: List<ItemWithCurrent> = emptyList(),
    val loaded: Boolean = false,
    /** "Home › Garage" for every place id, to show where a record was filed. */
    val placePaths: Map<Long, String> = emptyMap(),
    /** Only items that are not where they usually live. */
    val outOfPlaceOnly: Boolean = false,
    val outOfPlaceCount: Int = 0,
    /** Ids of the items (among all non-archived ones) that are away from their usual place. */
    val outOfPlaceIds: Set<Long> = emptySet(),
)

/** The bar shown after a save: add a photo or a note, or undo it. */
data class SavedBar(
    val itemId: Long,
    val itemName: String,
    val recordId: Long,
    val poorFixMeters: Int?,
    /** Accuracy of a good GPS fix, for the confirmation text; null for text saves. */
    val goodAccuracyMeters: Int? = null,
)

class HomeViewModel(private val repo: ItemRepository, private val location: LocationProvider) : ViewModel() {
    private val query = MutableStateFlow("")

    private val outOfPlaceOnly = MutableStateFlow(false)

    val state: StateFlow<HomeState> = combine(repo.homeItems(), query, repo.places(), outOfPlaceOnly) { items, q, places, onlyOut ->
        val paths = PlaceTree.paths(places)
        val outIds = items.filter { it.isOutOfPlace(places) }.mapTo(mutableSetOf()) { it.item.id }
        val outCount = outIds.size
        // With nothing out of place the chip disappears, so never leave the list filtered by it.
        val filterOut = onlyOut && outCount > 0
        val shown = items.filter { it.matches(q, it.currentPlaceId?.let(paths::get)) && (!filterOut || it.item.id in outIds) }
        HomeState(q, shown, loaded = true, placePaths = paths, outOfPlaceOnly = filterOut, outOfPlaceCount = outCount, outOfPlaceIds = outIds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setOutOfPlaceOnly(value: Boolean) {
        outOfPlaceOnly.value = value
    }

    val places: StateFlow<List<Place>> = repo.places()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _bar = MutableStateFlow<SavedBar?>(null)
    val bar: StateFlow<SavedBar?> = _bar

    /** The item whose GPS fix is being taken, if any. */
    private val _fixing = MutableStateFlow<Long?>(null)
    val fixing: StateFlow<Long?> = _fixing

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message

    fun setQuery(value: String) {
        query.value = value
    }

    fun addItem(input: NewItemInput) {
        viewModelScope.launch {
            repo.addItemWithLocation(input.name, input.emoji, input.captureGps, input.usual, input.locationText, input.locationPlaceId, input.gps, input.photoPath)
            _message.value = R.string.item_saved
        }
    }

    fun saveLocation(item: ItemWithCurrent, description: String?, note: String?, placeId: Long? = null) {
        viewModelScope.launch {
            val id = try {
                repo.saveLocation(item.item.id, description, note, placeId)
            } catch (_: UnknownItemException) {
                return@launch // deleted meanwhile; the list already dropped it
            }
            _bar.value = SavedBar(item.item.id, item.item.name, id, poorFixMeters = null)
        }
    }

    /** Takes a GPS fix and saves it. The caller has already checked the location permission. */
    fun captureHere(item: ItemWithCurrent) {
        if (_fixing.value != null) return
        viewModelScope.launch {
            _fixing.value = item.item.id
            val fix = try {
                location.currentFix()
            } finally {
                _fixing.value = null
            }
            if (fix == null) {
                _message.value = R.string.here_no_fix
                return@launch
            }
            val id = try {
                repo.saveGps(item.item.id, fix.latitude, fix.longitude, fix.accuracyMeters)
            } catch (_: UnknownItemException) {
                return@launch
            }
            val poor = if (FixQuality.isPoor(fix.accuracyMeters)) fix.accuracyMeters.toInt() else null
            _bar.value = SavedBar(item.item.id, item.item.name, id, poor, goodAccuracyMeters = if (poor == null) fix.accuracyMeters.toInt() else null)
        }
    }

    /** One GPS fix for the new-item form; the caller has already checked the location permission. */
    suspend fun currentFix(): app.locator.location.Fix? = location.currentFix()

    fun addNote(recordId: Long, note: String) {
        viewModelScope.launch { repo.setNote(recordId, note) }
    }

    fun attachPhoto(recordId: Long, path: String) {
        viewModelScope.launch { repo.attachPhoto(recordId, path) }
    }

    fun setReminder(recordId: Long, minutes: Int) {
        viewModelScope.launch {
            repo.setReminder(recordId, System.currentTimeMillis() + minutes * 60_000L)
            _message.value = R.string.reminder_set
        }
    }

    fun undoBar() {
        val current = _bar.value ?: return
        _bar.value = null
        viewModelScope.launch { repo.undoRecord(current.recordId) }
    }

    fun dismissBar() {
        _bar.value = null
    }

    fun showMessage(@StringRes id: Int) {
        _message.value = id
    }

    fun messageShown() {
        _message.value = null
    }

    suspend fun suggestions(itemId: Long): List<String> = repo.suggestions(itemId)

    /** Creates a place from the picker; null if the name is blank. */
    suspend fun createPlace(name: String, parentId: Long?): Long? =
        try {
            repo.addPlace(name, parentId)
        } catch (_: IllegalArgumentException) {
            null
        }
}

data class DetailState(
    val item: ItemWithCurrent? = null,
    val history: List<LocationRecord> = emptyList(),
    val loaded: Boolean = false,
    val placePaths: Map<Long, String> = emptyMap(),
    val places: List<Place> = emptyList(),
)

/** Where the saved pin is from here. [heading] is null when the phone has no compass: then only the distance is shown. */
data class CompassState(val distanceMeters: Double, val bearing: Double, val heading: Float?, val weakFix: Boolean) {
    /** Degrees to turn an upward-pointing arrow so it points at the pin; null without a heading. */
    val arrowRotation: Float? get() = heading?.let { Geo.relativeBearing(bearing, it.toDouble()).toFloat() }

    companion object {
        fun of(fix: Fix, latitude: Double, longitude: Double, heading: Float?) = CompassState(
            distanceMeters = Geo.distanceMeters(fix.latitude, fix.longitude, latitude, longitude),
            bearing = Geo.bearingDegrees(fix.latitude, fix.longitude, latitude, longitude),
            heading = heading,
            weakFix = FixQuality.isPoor(fix.accuracyMeters),
        )
    }
}

class DetailViewModel(
    private val itemId: Long,
    private val repo: ItemRepository,
    private val location: LocationProvider,
    private val headings: HeadingProvider,
    private val fixIntervalMs: Long = 5_000,
) : ViewModel() {
    val state: StateFlow<DetailState> = combine(repo.item(itemId), repo.history(itemId), repo.places()) { item, history, places ->
        DetailState(item, history, loaded = true, placePaths = PlaceTree.paths(places), places = places)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    /**
     * Distance and direction to a saved pin. A fix is taken every few seconds and the heading streams in, but only
     * while this flow is collected (the compass card is on screen), so nothing runs in the background.
     */
    fun compass(latitude: Double, longitude: Double): Flow<CompassState?> {
        val fixes = flow {
            while (true) {
                emit(location.currentFix())
                delay(fixIntervalMs)
            }
        }
        val heading = headings.headings().map<Float, Float?> { it }.onStart { emit(null) }
        return combine(fixes, heading) { fix, h -> fix?.let { CompassState.of(it, latitude, longitude, h) } }
    }

    fun edit(name: String, emoji: String?, captureGps: Boolean, usual: UsualPlace) {
        viewModelScope.launch { repo.editItem(itemId, name, emoji, captureGps, usual) }
    }

    /** Saves the usual place as the new location. */
    fun putBack() {
        viewModelScope.launch { repo.putBack(itemId) }
    }

    /** Creates a place from the picker; null if the name is blank. */
    suspend fun createPlace(name: String, parentId: Long?): Long? =
        try {
            repo.addPlace(name, parentId)
        } catch (_: IllegalArgumentException) {
            null
        }

    fun setPinned(pinned: Boolean) {
        viewModelScope.launch { repo.setPinned(itemId, pinned) }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { repo.setArchived(itemId, archived) }
    }

    fun undo() {
        viewModelScope.launch { repo.undoLast(itemId) }
    }

    fun cancelReminder(recordId: Long) {
        viewModelScope.launch { repo.clearReminder(recordId) }
    }

    fun delete() {
        viewModelScope.launch { repo.deleteItem(itemId) }
    }
}

/** Browsing one place (or the top level when [placeId] is null) and managing it. */
class PlacesViewModel(private val placeId: Long?, private val repo: ItemRepository) : ViewModel() {
    val view: StateFlow<PlaceView?> = combine(repo.places(), repo.homeItems()) { places, items ->
        PlaceBrowser.build(places, items, placeId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val places: StateFlow<List<Place>> = repo.places()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message

    fun messageShown() {
        _message.value = null
    }

    /** Adds a place inside the one being browsed. */
    fun add(name: String) {
        viewModelScope.launch { runCatchingPlace { repo.addPlace(name, placeId) } }
    }

    fun rename(name: String) {
        val id = placeId ?: return
        viewModelScope.launch { runCatchingPlace { repo.renamePlace(id, name) } }
    }

    fun move(newParentId: Long?) {
        val id = placeId ?: return
        viewModelScope.launch { runCatchingPlace { repo.movePlace(id, newParentId) } }
    }

    fun delete() {
        val id = placeId ?: return
        viewModelScope.launch { repo.deletePlace(id) }
    }

    suspend fun createPlace(name: String, parentId: Long?): Long? =
        try {
            repo.addPlace(name, parentId)
        } catch (_: IllegalArgumentException) {
            null
        }

    private suspend fun runCatchingPlace(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: PlaceNameTakenException) {
            _message.value = R.string.place_name_taken
        } catch (_: IllegalArgumentException) {
            _message.value = R.string.place_invalid
        }
    }
}

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Working : BackupStatus
    data object Exported : BackupStatus
    data class Imported(val items: Int, val records: Int) : BackupStatus
    /** The file is not a usable backup (damaged, wrong format, newer than this app). */
    data object Invalid : BackupStatus
    data object Failed : BackupStatus
}

class SettingsViewModel(
    private val repo: ItemRepository,
    private val backup: BackupService,
    private val quick: QuickItemStore,
    private val refreshWidget: suspend () -> Unit,
) : ViewModel() {
    val gpsItems: StateFlow<List<Item>> = repo.gpsItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The item quick save will use: the chosen one, or the first GPS item. */
    val quickItemId: StateFlow<Long?> = combine(repo.gpsItems(), quick.selectedId) { items, selected ->
        QuickItem.resolve(selected, items)?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectQuickItem(id: Long) {
        quick.select(id)
        viewModelScope.launch { refreshWidget() }
    }

    val archived: StateFlow<List<ItemWithCurrent>> = repo.homeItems(archived = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status: StateFlow<BackupStatus> = _status

    fun restore(id: Long) {
        viewModelScope.launch { repo.setArchived(id, false) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteItem(id) }
    }

    fun export(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working
            _status.value = try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open the file")
                backup.export(out)
                BackupStatus.Exported
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BackupStatus.Failed
            }
        }
    }

    fun import(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working
            _status.value = try {
                val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open the file")
                val result = input.use { backup.import(it) }
                BackupStatus.Imported(result.items, result.records)
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupException) {
                BackupStatus.Invalid
            } catch (_: Exception) {
                BackupStatus.Failed
            }
        }
    }

    fun statusShown() {
        _status.value = BackupStatus.Idle
    }
}
