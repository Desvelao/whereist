package app.locator.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** An item together with its current location (its newest record), if any. */
data class ItemWithCurrent(
    @Embedded val item: Item,
    val currentRecordId: Long?,
    val currentDescription: String?,
    val currentNote: String?,
    val currentLatitude: Double?,
    val currentLongitude: Double?,
    val currentAccuracy: Float?,
    val currentPhoto: String?,
    val currentRemindAt: Long?,
    val currentPlaceId: Long?,
    val currentAt: Long?,
) {
    val hasGps: Boolean get() = currentLatitude != null && currentLongitude != null

    /** True when it has a usual place or spot and its current location is somewhere else. */
    val isOutOfPlace: Boolean get() = isOutOfPlace(emptyList())

    /**
     * Like [isOutOfPlace], but with the place tree: being in a place inside the usual place ("Home › Garage ›
     * Shelf B" for "Home › Garage") counts as in place.
     */
    fun isOutOfPlace(places: List<Place>): Boolean {
        if (currentRecordId == null) return false
        val usualPlace = item.usualPlaceId
        val usualText = item.usualText
        if (usualPlace == null && usualText == null) return false
        val placeOk = usualPlace == null || currentPlaceId == usualPlace ||
            (currentPlaceId != null && currentPlaceId in PlaceTree.subtree(places, usualPlace))
        val textOk = usualText == null ||
            (currentDescription != null && SearchText.normalize(currentDescription) == SearchText.normalize(usualText))
        return !(placeOk && textOk)
    }
}

private const val WITH_CURRENT = """
    SELECT i.*, r.id AS currentRecordId, r.description AS currentDescription, r.note AS currentNote,
        r.latitude AS currentLatitude, r.longitude AS currentLongitude, r.accuracyMeters AS currentAccuracy,
        r.photoPath AS currentPhoto, r.remindAt AS currentRemindAt, r.placeId AS currentPlaceId, r.createdAt AS currentAt
    FROM Item i
    LEFT JOIN LocationRecord r ON r.id = (
        SELECT id FROM LocationRecord WHERE itemId = i.id ORDER BY createdAt DESC, id DESC LIMIT 1
    )
"""

@Dao
interface ItemDao {
    @Insert
    suspend fun insert(item: Item): Long

    @Insert
    suspend fun insertAll(items: List<Item>)

    @Update
    suspend fun update(item: Item)

    @Query("SELECT * FROM Item WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Query("SELECT * FROM Item ORDER BY id")
    suspend fun all(): List<Item>

    @Query("DELETE FROM Item WHERE id = :id")
    suspend fun delete(id: Long)

    /** Items that can be saved with a GPS fix, for quick save. */
    @Query("SELECT * FROM Item WHERE captureGps = 1 AND archived = 0 ORDER BY pinned DESC, id")
    fun observeGpsItems(): Flow<List<Item>>

    @Query("SELECT * FROM Item WHERE captureGps = 1 AND archived = 0 ORDER BY pinned DESC, id")
    suspend fun gpsItems(): List<Item>

    @Query("DELETE FROM Item")
    suspend fun deleteAll()

    @Query("$WITH_CURRENT WHERE i.archived = :archived ORDER BY i.pinned DESC, COALESCE(r.createdAt, i.createdAt) DESC, i.id DESC")
    fun observeAll(archived: Boolean): Flow<List<ItemWithCurrent>>

    @Query("$WITH_CURRENT WHERE i.id = :id")
    fun observe(id: Long): Flow<ItemWithCurrent?>
}

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(record: LocationRecord): Long

    @Insert
    suspend fun insertAll(records: List<LocationRecord>)

    @Query("SELECT * FROM LocationRecord WHERE id = :id")
    suspend fun get(id: Long): LocationRecord?

    @Query("SELECT * FROM LocationRecord ORDER BY id")
    suspend fun all(): List<LocationRecord>

    @Query("DELETE FROM LocationRecord")
    suspend fun deleteAll()

    @Query("SELECT * FROM LocationRecord WHERE itemId = :itemId ORDER BY createdAt DESC, id DESC")
    fun observeHistory(itemId: Long): Flow<List<LocationRecord>>

    @Query("SELECT * FROM LocationRecord WHERE itemId = :itemId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latest(itemId: Long): LocationRecord?

    @Query("DELETE FROM LocationRecord WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE LocationRecord SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    @Query("UPDATE LocationRecord SET photoPath = :photoPath WHERE id = :id")
    suspend fun updatePhoto(id: Long, photoPath: String?)

    @Query("UPDATE LocationRecord SET remindAt = :remindAt WHERE id = :id")
    suspend fun setRemindAt(id: Long, remindAt: Long?)

    @Query("SELECT * FROM LocationRecord WHERE remindAt IS NOT NULL")
    suspend fun withReminders(): List<LocationRecord>

    @Query("SELECT id FROM LocationRecord WHERE itemId = :itemId AND remindAt IS NOT NULL")
    suspend fun reminderIds(itemId: Long): List<Long>

    @Query("SELECT photoPath FROM LocationRecord WHERE itemId = :itemId AND photoPath IS NOT NULL")
    suspend fun photoPaths(itemId: Long): List<String>

    /** The item's own descriptions, most frequent first, then most recent. */
    @Query(
        "SELECT description FROM LocationRecord WHERE itemId = :itemId AND description IS NOT NULL AND description != '' " +
            "GROUP BY description ORDER BY COUNT(*) DESC, MAX(createdAt) DESC LIMIT :limit",
    )
    suspend fun ownSuggestions(itemId: Long, limit: Int): List<String>

    /** Descriptions used by other items, most recent first. */
    @Query(
        "SELECT description FROM LocationRecord WHERE itemId != :itemId AND description IS NOT NULL AND description != '' " +
            "GROUP BY description ORDER BY MAX(createdAt) DESC LIMIT :limit",
    )
    suspend fun otherSuggestions(itemId: Long, limit: Int): List<String>
}

@Dao
abstract class PlaceDao {
    @Insert
    abstract suspend fun insert(place: Place): Long

    @Insert
    abstract suspend fun insertAll(places: List<Place>)

    @Query("SELECT * FROM Place ORDER BY id")
    abstract suspend fun all(): List<Place>

    @Query("SELECT * FROM Place ORDER BY name COLLATE NOCASE, id")
    abstract fun observeAll(): Flow<List<Place>>

    @Query("SELECT * FROM Place WHERE id = :id")
    abstract suspend fun get(id: Long): Place?

    @Query("UPDATE Place SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("UPDATE Place SET parentId = :parentId WHERE id = :id")
    abstract suspend fun setParent(id: Long, parentId: Long?)

    @Query("UPDATE Place SET parentId = :newParentId WHERE parentId = :oldParentId")
    abstract suspend fun reparentChildren(oldParentId: Long, newParentId: Long?)

    @Query("DELETE FROM Place WHERE id = :id")
    abstract suspend fun deleteRow(id: Long)

    @Query("DELETE FROM Place")
    abstract suspend fun deleteAll()

    /** Deletes a place; the places inside it move up to its parent, and its records keep their text. */
    @Transaction
    open suspend fun deleteKeepingChildren(id: Long) {
        val place = get(id) ?: return
        reparentChildren(id, place.parentId)
        deleteRow(id)
    }
}
