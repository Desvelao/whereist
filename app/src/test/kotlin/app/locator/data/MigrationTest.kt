package app.locator.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds a real version-1 database from the exported schema (app/schemas/.../1.json), then opens it through Room
 * with the migrations. Room validates the migrated tables against the current schema when it opens.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun createVersion(file: File, version: Int) {
        val schema = File("schemas/app.locator.data.AppDatabase/$version.json").readText()
        val database = JSONObject(schema).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices")
            for (j in 0 until (indices?.length() ?: 0)) {
                db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = database.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.execSQL("INSERT INTO Item (id, name, emoji, captureGps, pinned, archived, searchText, createdAt) VALUES (1, 'Keys', NULL, 0, 0, 0, 'keys', 10)")
        db.execSQL("INSERT INTO LocationRecord (id, itemId, description, note, createdAt) VALUES (1, 1, 'drawer', 'top left', 20)")
        if (version >= 2) db.execSQL("UPDATE LocationRecord SET photoPath = 'photos/a.jpg', latitude = 1.5 WHERE id = 1")
        db.version = version
        db.close()
    }

    @Test
    fun migrate1To4KeepsRowsAndAddsGpsPhotoReminderAndPlaces() = runBlocking {
        val file = File(tmp.root, "v1.db")
        createVersion(file, 1)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val record = db.locationDao().all().single()
            assertEquals("drawer", record.description)
            assertEquals("top left", record.note)
            assertEquals(20L, record.createdAt)
            assertNull(record.latitude)
            assertNull(record.longitude)
            assertNull(record.accuracyMeters)
            assertNull(record.photoPath)
            assertNull(record.remindAt)
            assertNull(record.placeId)
            assertEquals("Keys", db.itemDao().all().single().name)
            // The new columns are usable after the migration.
            db.locationDao().insert(LocationRecord(itemId = 1, description = null, note = null, latitude = 1.0, longitude = 2.0, accuracyMeters = 3f, createdAt = 30))
            assertEquals(2, db.locationDao().all().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate2To3KeepsGpsAndPhotoAndAddsTheReminderColumn() = runBlocking {
        val file = File(tmp.root, "v2.db")
        createVersion(file, 2)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val record = db.locationDao().all().single()
            assertEquals("photos/a.jpg", record.photoPath)
            assertEquals(1.5, record.latitude!!, 1e-9)
            assertNull(record.remindAt)
            db.locationDao().setRemindAt(record.id, 99L)
            assertEquals(99L, db.locationDao().get(record.id)!!.remindAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate3To4KeepsRecordsWithoutAPlaceAndPlacesWork() = runBlocking {
        val file = File(tmp.root, "v3.db")
        createVersion(file, 3)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val record = db.locationDao().all().single()
            assertEquals("drawer", record.description)
            assertEquals("photos/a.jpg", record.photoPath)
            assertNull(record.placeId)

            // The new table and the new column are usable, and the SET NULL rule holds.
            val home = db.placeDao().insert(Place(name = "Home", parentId = null))
            val garage = db.placeDao().insert(Place(name = "Garage", parentId = home))
            db.locationDao().insert(LocationRecord(itemId = 1, description = null, note = null, placeId = garage, createdAt = 50))
            assertEquals(garage, db.locationDao().all().last().placeId)
            db.placeDao().deleteKeepingChildren(garage)
            assertNull(db.locationDao().all().last().placeId)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate4To5AddsTheUsualPlaceColumns() = runBlocking {
        val file = File(tmp.root, "v4.db")
        createVersion(file, 4)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val item = db.itemDao().get(1)!!
            assertEquals("Keys", item.name)
            assertNull(item.usualPlaceId)
            assertNull(item.usualText)

            val shelf = db.placeDao().insert(Place(name = "Shelf", parentId = null))
            db.itemDao().update(item.copy(usualPlaceId = shelf, usualText = "top"))
            assertEquals(shelf, db.itemDao().get(1)!!.usualPlaceId)
            db.placeDao().deleteKeepingChildren(shelf)
            assertNull(db.itemDao().get(1)!!.usualPlaceId)
        } finally {
            db.close()
        }
    }
}
