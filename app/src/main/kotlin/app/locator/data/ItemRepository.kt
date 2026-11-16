package app.locator.data

import app.locator.location.Fix
import kotlinx.coroutines.flow.Flow

/** Schedules the reminder alarm; faked in tests. */
interface ReminderScheduling {
    fun schedule(recordId: Long, atMillis: Long)
    fun cancel(recordId: Long)
}

data class ReminderTarget(val item: Item, val record: LocationRecord, val placePath: String? = null)

/** The item was deleted meanwhile (for example by another screen or an old widget target). */
class UnknownItemException(id: Long) : Exception("No item with id $id")

class PlaceNameTakenException(name: String) : Exception("A place named \"$name\" already exists there")

class ItemRepository(
    private val items: ItemDao,
    private val locations: LocationDao,
    private val places: PlaceDao,
    private val photos: PhotoStore? = null,
    private val reminders: ReminderScheduling? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun homeItems(archived: Boolean = false): Flow<List<ItemWithCurrent>> = items.observeAll(archived)

    fun item(id: Long): Flow<ItemWithCurrent?> = items.observe(id)

    fun history(itemId: Long): Flow<List<LocationRecord>> = locations.observeHistory(itemId)

    suspend fun addItem(name: String, emoji: String?, captureGps: Boolean = false, usual: UsualPlace = UsualPlace.NONE): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Item name must not be blank" }
        require(usual.placeId == null || places.get(usual.placeId) != null) { "Unknown place" }
        return items.insert(
            Item(
                name = clean,
                emoji = emoji.cleanEmoji(),
                captureGps = captureGps,
                pinned = false,
                archived = false,
                searchText = SearchText.normalize(clean),
                createdAt = clock(),
                usualPlaceId = usual.placeId,
                usualText = usual.text.cleanText(),
            ),
        )
    }

    /**
     * Adds an item and, when [locationText] or [locationPlaceId] is given, its first location. Everything is
     * validated before anything is written, so a bad place never leaves a half-created item.
     */
    suspend fun addItemWithLocation(
        name: String,
        emoji: String?,
        captureGps: Boolean,
        usual: UsualPlace,
        locationText: String?,
        locationPlaceId: Long?,
        gps: Fix? = null,
        photoPath: String? = null,
    ): Long {
        require(name.isNotBlank()) { "Item name must not be blank" }
        require(usual.placeId == null || places.get(usual.placeId) != null) { "Unknown place" }
        require(locationPlaceId == null || places.get(locationPlaceId) != null) { "Unknown place" }
        val hasLocation = locationText.cleanText() != null || locationPlaceId != null || gps != null
        require(photoPath == null || hasLocation) { "A photo needs a location" }
        val id = addItem(name, emoji, captureGps, usual)
        if (hasLocation) {
            locations.insert(
                LocationRecord(
                    itemId = id,
                    description = locationText.cleanText(),
                    note = null,
                    latitude = gps?.latitude,
                    longitude = gps?.longitude,
                    accuracyMeters = gps?.accuracyMeters,
                    photoPath = photoPath,
                    placeId = locationPlaceId,
                    createdAt = clock(),
                ),
            )
        }
        return id
    }

    /**
     * Renames the item. [usual] is left as is when null; pass [UsualPlace.NONE] to clear it. An unknown place id
     * is rejected.
     */
    suspend fun editItem(id: Long, name: String, emoji: String?, captureGps: Boolean? = null, usual: UsualPlace? = null) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Item name must not be blank" }
        val current = items.get(id) ?: return
        require(usual?.placeId == null || places.get(usual.placeId) != null) { "Unknown place" }
        items.update(
            current.copy(
                name = clean,
                emoji = emoji.cleanEmoji(),
                captureGps = captureGps ?: current.captureGps,
                searchText = SearchText.normalize(clean),
                usualPlaceId = if (usual != null) usual.placeId else current.usualPlaceId,
                usualText = if (usual != null) usual.text.cleanText() else current.usualText,
            ),
        )
    }

    /** Saves the item's usual place and spot as its new location. Returns the record id, or null if none is set. */
    suspend fun putBack(itemId: Long): Long? {
        val item = items.get(itemId) ?: return null
        if (item.usualPlaceId == null && item.usualText == null) return null
        return saveLocation(itemId, item.usualText, placeId = item.usualPlaceId)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        val current = items.get(id) ?: return
        items.update(current.copy(pinned = pinned))
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val current = items.get(id) ?: return
        items.update(current.copy(archived = archived))
    }

    /** Deletes the item; its records go with it (cascade), and so do their photo files. */
    suspend fun deleteItem(id: Long) {
        val paths = locations.photoPaths(id)
        val pending = locations.reminderIds(id)
        items.delete(id)
        paths.forEach { photos?.delete(it) }
        pending.forEach { reminders?.cancel(it) }
    }

    /**
     * Saves a location and returns the new record's id. It needs a text description, a place, or both
     * ("Kitchen drawer" in Home › Kitchen).
     */
    suspend fun saveLocation(itemId: Long, description: String?, note: String? = null, placeId: Long? = null): Long {
        val clean = description.cleanText()
        require(clean != null || placeId != null) { "A location needs a description or a place" }
        require(placeId == null || places.get(placeId) != null) { "Unknown place" }
        if (items.get(itemId) == null) throw UnknownItemException(itemId)
        return locations.insert(
            LocationRecord(itemId = itemId, description = clean, note = note.cleanText(), placeId = placeId, createdAt = clock()),
        )
    }

    fun places(): Flow<List<Place>> = places.observeAll()

    /** Adds a place inside [parentId] (null = top level). A sibling with the same name is reused, not duplicated. */
    suspend fun addPlace(name: String, parentId: Long?): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Place name must not be blank" }
        val all = places.all()
        require(parentId == null || all.any { it.id == parentId }) { "Unknown parent place" }
        val key = SearchText.normalize(clean)
        all.firstOrNull { it.parentId == parentId && SearchText.normalize(it.name) == key }?.let { return it.id }
        return places.insert(Place(name = clean, parentId = parentId))
    }

    suspend fun renamePlace(id: Long, name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Place name must not be blank" }
        val all = places.all()
        val place = all.firstOrNull { it.id == id } ?: return
        val key = SearchText.normalize(clean)
        if (all.any { it.id != id && it.parentId == place.parentId && SearchText.normalize(it.name) == key }) {
            throw PlaceNameTakenException(clean)
        }
        places.rename(id, clean)
    }

    /** Moves a place (and everything in it) under [newParentId]; not into itself or its own subtree. */
    suspend fun movePlace(id: Long, newParentId: Long?) {
        val all = places.all()
        val place = all.firstOrNull { it.id == id } ?: return
        require(newParentId == null || newParentId !in PlaceTree.subtree(all, id)) { "A place cannot go inside itself" }
        require(newParentId == null || all.any { it.id == newParentId }) { "Unknown parent place" }
        val key = SearchText.normalize(place.name)
        if (all.any { it.id != id && it.parentId == newParentId && SearchText.normalize(it.name) == key }) {
            throw PlaceNameTakenException(place.name)
        }
        places.setParent(id, newParentId)
    }

    /** Deletes a place. Places inside it move up a level; records keep their text and lose the place. */
    suspend fun deletePlace(id: Long) = places.deleteKeepingChildren(id)

    /** Saves a GPS fix and returns the new record's id. */
    suspend fun saveGps(itemId: Long, latitude: Double, longitude: Double, accuracyMeters: Float): Long {
        if (items.get(itemId) == null) throw UnknownItemException(itemId)
        return locations.insert(
            LocationRecord(
                itemId = itemId,
                description = null,
                note = null,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                createdAt = clock(),
            ),
        )
    }

    suspend fun setNote(recordId: Long, note: String?) = locations.updateNote(recordId, note.cleanText())

    /** Attaches a photo file to a record. If the record is gone (undone meanwhile) the file is deleted. */
    suspend fun attachPhoto(recordId: Long, path: String) {
        val record = locations.get(recordId)
        if (record == null) {
            photos?.delete(path)
            return
        }
        locations.updatePhoto(recordId, path)
        if (record.photoPath != null && record.photoPath != path) photos?.delete(record.photoPath)
    }

    /** Removes the newest record (and its photo). Returns false when there was nothing to undo. */
    suspend fun undoLast(itemId: Long): Boolean {
        val latest = locations.latest(itemId) ?: return false
        locations.delete(latest.id)
        photos?.delete(latest.photoPath)
        reminders?.cancel(latest.id)
        return true
    }

    /** Removes one record, for the "Undo" on the save bar. */
    suspend fun undoRecord(recordId: Long) {
        val record = locations.get(recordId) ?: return
        locations.delete(recordId)
        photos?.delete(record.photoPath)
        reminders?.cancel(recordId)
    }

    /** Starts the reminder of a record: stored, and an alarm is scheduled. */
    suspend fun setReminder(recordId: Long, atMillis: Long) {
        if (locations.get(recordId) == null) return
        locations.setRemindAt(recordId, atMillis)
        reminders?.schedule(recordId, atMillis)
    }

    suspend fun clearReminder(recordId: Long) {
        locations.setRemindAt(recordId, null)
        reminders?.cancel(recordId)
    }

    /** The item and record behind a fired alarm, or null if either is gone. */
    suspend fun reminderTarget(recordId: Long): ReminderTarget? {
        val record = locations.get(recordId) ?: return null
        // A stale alarm (for example one left from before a backup import) has no reminder behind it.
        if (record.remindAt == null) return null
        val item = items.get(record.itemId) ?: return null
        val path = record.placeId?.let { PlaceTree.pathOf(places.all(), it) }
        return ReminderTarget(item, record, path)
    }

    /**
     * After a reboot all alarms are gone: schedule the ones still in the future and hand the ones that went off
     * while the phone was off to [notify]. A missed reminder is cleared once [notify] reports it was shown; if it
     * could not be (notifications off) it is kept.
     */
    suspend fun restoreReminders(notify: (ReminderTarget) -> Boolean = { true }) {
        val now = clock()
        for (record in locations.withReminders()) {
            val at = record.remindAt ?: continue
            if (at > now) {
                reminders?.schedule(record.id, at)
            } else {
                val target = reminderTarget(record.id)
                if (target == null || notify(target)) locations.setRemindAt(record.id, null)
            }
        }
    }

    fun gpsItems(): Flow<List<Item>> = items.observeGpsItems()

    suspend fun gpsItemsNow(): List<Item> = items.gpsItems()

    /** The item's own most frequent descriptions first, then recent ones from other items. */
    suspend fun suggestions(itemId: Long, limit: Int = 8): List<String> =
        (locations.ownSuggestions(itemId, limit) + locations.otherSuggestions(itemId, limit))
            .distinctBy { SearchText.normalize(it) }
            .take(limit)

    private fun String?.cleanEmoji(): String? = this?.trim()?.ifEmpty { null }

    private fun String?.cleanText(): String? = this?.trim()?.ifEmpty { null }
}

/** Where an item normally lives: a place, some text, or both. */
data class UsualPlace(val placeId: Long?, val text: String?) {
    companion object {
        val NONE = UsualPlace(null, null)
    }
}
