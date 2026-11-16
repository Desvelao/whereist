package app.locator.data

import androidx.room.Database
import androidx.room.RoomDatabase

// Any schema change needs a version bump, a real Migration (plus a migration test) and the exported schema in app/schemas/.
@Database(entities = [Item::class, LocationRecord::class, Place::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun locationDao(): LocationDao
    abstract fun placeDao(): PlaceDao
}
