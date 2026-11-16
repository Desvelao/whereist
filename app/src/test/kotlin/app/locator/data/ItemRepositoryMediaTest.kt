package app.locator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ItemRepositoryMediaTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var photos: PhotoStore
    private lateinit var repo: ItemRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        photos = PhotoStore(tmp.root).also { it.prepare() }
        repo = ItemRepository(db.itemDao(), db.locationDao(), db.placeDao(), photos) { now }
    }

    @After
    fun tearDown() = db.close()

    private fun newPhoto(): String = photos.newPath().also { photos.file(it).writeText("jpg") }

    @Test
    fun gpsRecordIsTheCurrentLocationWithCoordinatesAndAccuracy() = runBlocking {
        val car = repo.addItem("Car", "🚗", captureGps = true)
        repo.saveGps(car, 40.4168, -3.7038, 12.5f)
        val row = repo.item(car).first()!!
        assertTrue(row.item.captureGps)
        assertTrue(row.hasGps)
        assertEquals(40.4168, row.currentLatitude!!, 1e-9)
        assertEquals(-3.7038, row.currentLongitude!!, 1e-9)
        assertEquals(12.5f, row.currentAccuracy!!, 0f)
        assertNull(row.currentDescription)
    }

    @Test
    fun noteAndPhotoAttachToTheRecord() = runBlocking {
        val car = repo.addItem("Car", null, true)
        val record = repo.saveGps(car, 1.0, 2.0, 50f)
        val photo = newPhoto()
        repo.setNote(record, "  level -2, row C ")
        repo.attachPhoto(record, photo)
        val row = repo.item(car).first()!!
        assertEquals("level -2, row C", row.currentNote)
        assertEquals(photo, row.currentPhoto)
    }

    @Test
    fun replacingAPhotoDeletesTheOldFile() = runBlocking {
        val car = repo.addItem("Car", null, true)
        val record = repo.saveGps(car, 1.0, 2.0, 5f)
        val first = newPhoto()
        val second = newPhoto()
        repo.attachPhoto(record, first)
        repo.attachPhoto(record, second)
        assertFalse(photos.file(first).exists())
        assertTrue(photos.file(second).exists())
    }

    @Test
    fun attachingToAnUndoneRecordDeletesTheNewFile() = runBlocking {
        val car = repo.addItem("Car", null, true)
        val record = repo.saveGps(car, 1.0, 2.0, 5f)
        repo.undoRecord(record)
        val photo = newPhoto()
        repo.attachPhoto(record, photo)
        assertFalse(photos.file(photo).exists())
    }

    @Test
    fun undoAndDeleteRemovePhotoFiles() = runBlocking {
        val car = repo.addItem("Car", null, true)
        val r1 = repo.saveGps(car, 1.0, 2.0, 5f)
        now += 1_000
        val r2 = repo.saveGps(car, 3.0, 4.0, 5f)
        val p1 = newPhoto()
        val p2 = newPhoto()
        repo.attachPhoto(r1, p1)
        repo.attachPhoto(r2, p2)
        assertTrue(repo.undoLast(car))
        assertFalse(photos.file(p2).exists())
        assertTrue(photos.file(p1).exists())
        repo.deleteItem(car)
        assertFalse(photos.file(p1).exists())
    }

    @Test
    fun editingCanSwitchGpsCaptureOn() = runBlocking {
        val id = repo.addItem("Bike", null)
        assertFalse(repo.item(id).first()!!.item.captureGps)
        repo.editItem(id, "Bike", null, captureGps = true)
        assertTrue(repo.item(id).first()!!.item.captureGps)
    }
}
