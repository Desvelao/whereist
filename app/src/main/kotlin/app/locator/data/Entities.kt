package app.locator.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["usualPlaceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("usualPlaceId")],
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String?,
    val captureGps: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val searchText: String, // name, lowercased, accents stripped (see SearchText.normalize)
    val createdAt: Long, // epoch millis
    val usualPlaceId: Long? = null, // where it normally lives ("put back" target); null when not set
    val usualText: String? = null, // usual spot as text, alone or next to the place ("second shelf")
)

/** Append-only: the newest record of an item is its current location. */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        // Deleting a place keeps the records (their text stays); they just lose the place.
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("itemId", "createdAt"), Index("placeId")],
)
data class LocationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val description: String?,
    val note: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val photoPath: String? = null, // relative to the app files dir, e.g. photos/<uuid>.jpg
    val remindAt: Long? = null, // reminder time, epoch millis; not part of backups
    val placeId: Long? = null, // reusable nested place (Home > Garage > Shelf B)
    val createdAt: Long, // epoch millis
)

/** A reusable, nestable place such as Home > Garage > Shelf B. Deleting one moves its children up a level. */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Place::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parentId")],
)
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long?,
)
