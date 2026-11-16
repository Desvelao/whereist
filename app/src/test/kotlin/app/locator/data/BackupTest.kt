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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var photos: PhotoStore
    private lateinit var repo: ItemRepository
    private lateinit var backup: BackupService
    private val scheduler = FakeScheduler()
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        photos = PhotoStore(tmp.newFolder("files")).also { it.prepare() }
        repo = ItemRepository(db.itemDao(), db.locationDao(), db.placeDao(), photos) { now }
        backup = BackupService(db, photos, scheduler) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(): Long {
        val home = repo.addPlace("Home", null)
        val garage = repo.addPlace("Garage", home)
        val car = repo.addItem("Car", "🚗", captureGps = true)
        val record = repo.saveGps(car, 40.4168, -3.7038, 8f)
        val photo = photos.newPath()
        photos.file(photo).writeText("jpeg-bytes")
        repo.attachPhoto(record, photo)
        repo.setNote(record, "level -2")
        now += 1_000
        val keys = repo.addItem("Llaves", null)
        repo.saveLocation(keys, "Cajón", null, garage)
        repo.setPinned(keys, true)
        return car
    }

    @Test
    fun exportThenImportRestoresEverythingIncludingPhotos() = runBlocking {
        seed()
        val before = db.itemDao().all() to db.locationDao().all()
        val placesBefore = db.placeDao().all()
        assertEquals(2, placesBefore.size)
        val zip = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

        repo.deleteItem(before.first[0].id)
        repo.deleteItem(before.first[1].id)
        placesBefore.forEach { repo.deletePlace(it.id) }
        photos.deleteAll()
        assertTrue(db.itemDao().all().isEmpty())

        val result = backup.import(ByteArrayInputStream(zip))
        assertEquals(ImportResult(items = 2, records = 2, photos = 1, places = 2), result)
        assertEquals(placesBefore.sortedBy { it.id }, db.placeDao().all().sortedBy { it.id })
        assertEquals(before.first, db.itemDao().all())
        assertEquals(before.second, db.locationDao().all())
        val photoPath = db.locationDao().all().mapNotNull { it.photoPath }.single()
        assertEquals("jpeg-bytes", photos.file(photoPath).readText())
    }

    @Test
    fun importCancelsAlarmsOfTheReplacedRecords() = runBlocking {
        val car = seed()
        val zip = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()
        val record = db.locationDao().all().first { it.itemId == car }.id
        scheduler.schedule(record, 99_000L)
        db.locationDao().setRemindAt(record, 99_000L)
        backup.import(ByteArrayInputStream(zip))
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun importReplacesExistingData() = runBlocking {
        seed()
        val zip = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()
        repo.addItem("Extra", null)
        backup.import(ByteArrayInputStream(zip))
        assertEquals(listOf("Car", "Llaves"), db.itemDao().all().map { it.name })
    }

    @Test
    fun aDamagedBackupLeavesCurrentDataUntouched() = runBlocking {
        seed()
        val garbage = zipOf("data.json" to "{ not json".toByteArray())
        try {
            backup.import(ByteArrayInputStream(garbage))
            throw AssertionError("expected BackupException")
        } catch (_: BackupException) {
        }
        assertEquals(2, db.itemDao().all().size)
        assertEquals(2, db.locationDao().all().size)
    }

    @Test
    fun aZipWithoutDataJsonIsRejected() = runBlocking {
        try {
            backup.import(ByteArrayInputStream(zipOf("photos/a.jpg" to byteArrayOf(1))))
            throw AssertionError("expected BackupException")
        } catch (_: BackupException) {
        }
    }

    @Test
    fun hostileEntryNamesAreIgnoredAndNothingEscapesTheFilesFolder() = runBlocking {
        val json = BackupCodec.encode(emptyList(), emptyList(), 0)
        val zip = zipOf(
            "data.json" to json.toByteArray(),
            "../evil.txt" to byteArrayOf(1),
            "photos/../../evil2.txt" to byteArrayOf(1),
            "/abs/evil3.txt" to byteArrayOf(1),
        )
        backup.import(ByteArrayInputStream(zip))
        assertFalse(tmp.root.resolve("evil.txt").exists())
        assertFalse(tmp.root.resolve("evil2.txt").exists())
        assertEquals(emptyList<String>(), photos.root.resolve("photos").list()!!.toList())
        assertFalse(photos.root.resolve("import-staging").exists())
    }

    @Test
    fun aMissingPhotoFileKeepsTheRecordButDropsTheReference() = runBlocking {
        val item = Item(id = 1, name = "Car", emoji = null, captureGps = true, pinned = false, archived = false, searchText = "car", createdAt = 1)
        val record = LocationRecord(id = 1, itemId = 1, description = "x", note = null, photoPath = "photos/missing.jpg", createdAt = 2)
        val zip = zipOf("data.json" to BackupCodec.encode(listOf(item), listOf(record), 0).toByteArray())
        backup.import(ByteArrayInputStream(zip))
        assertNull(db.locationDao().all().single().photoPath)
        assertEquals("Car", repo.homeItems().first().single().item.name)
    }

    @Test
    fun codecRejectsUnknownVersionOrphanRecordsAndBadPhotoPaths() {
        fun decodeFails(json: String) = try {
            BackupCodec.decode(json)
            false
        } catch (_: BackupException) {
            true
        }
        assertTrue(decodeFails("""{"version":99,"items":[],"records":[]}"""))
        assertTrue(decodeFails("""{"version":1,"items":[],"records":[{"id":1,"itemId":7,"createdAt":1}]}"""))
        val item = """{"id":1,"name":"A","captureGps":false,"pinned":false,"archived":false,"createdAt":1}"""
        assertTrue(decodeFails("""{"version":1,"items":[$item],"records":[{"id":1,"itemId":1,"photo":"../x.jpg","createdAt":1}]}"""))
        assertFalse(decodeFails("""{"version":1,"items":[$item],"records":[{"id":1,"itemId":1,"description":"d","createdAt":1}]}"""))
    }

    @Test
    fun aVersion1BackupWithoutPlacesStillImports() = runBlocking {
        val json = """{"version":1,"exportedAt":0,"items":[{"id":1,"name":"Keys","captureGps":false,"pinned":false,"archived":false,"createdAt":1}],
            "records":[{"id":1,"itemId":1,"description":"drawer","createdAt":2}]}"""
        val result = backup.import(ByteArrayInputStream(zipOf("data.json" to json.toByteArray())))
        assertEquals(ImportResult(items = 1, records = 1, photos = 0, places = 0), result)
        assertNull(db.locationDao().all().single().placeId)
    }

    @Test
    fun codecRejectsBrokenPlaceTrees() {
        fun fails(places: String, recordPlace: String = "null"): Boolean = try {
            BackupCodec.decode(
                """{"version":2,"places":$places,"items":[{"id":1,"name":"A","captureGps":false,"pinned":false,"archived":false,"createdAt":1}],
                "records":[{"id":1,"itemId":1,"description":"d","placeId":$recordPlace,"createdAt":1}]}""",
            )
            false
        } catch (_: BackupException) {
            true
        }
        assertTrue(fails("""[{"id":1,"name":"A","parentId":2},{"id":2,"name":"B","parentId":1}]"""))
        assertTrue(fails("""[{"id":1,"name":"A","parentId":9}]"""))
        assertTrue(fails("""[{"id":1,"name":"A"},{"id":1,"name":"B"}]"""))
        assertTrue(fails("""[{"id":1,"name":" "}]"""))
        assertTrue(fails("[]", recordPlace = "5"))
        assertFalse(fails("""[{"id":1,"name":"A"}]""", recordPlace = "1"))
    }

    @Test
    fun placesAreWrittenParentsFirst() {
        val json = BackupCodec.encode(
            emptyList(), emptyList(), 0,
            places = listOf(Place(3, "Shelf", 2), Place(2, "Garage", 1), Place(1, "Home", null)),
        )
        assertEquals(listOf(1L, 2L, 3L), BackupCodec.decode(json).places.map { it.id })
    }

    @Test
    fun siblingPlacesWithTheSameNameAreMergedAndReferencesFollow() {
        val item = Item(id = 1, name = "Bike", emoji = null, captureGps = false, pinned = false, archived = false,
            searchText = "bike", createdAt = 1, usualPlaceId = 3, usualText = null)
        val record = LocationRecord(id = 1, itemId = 1, description = null, note = null, placeId = 4, createdAt = 1)
        val json = BackupCodec.encode(
            listOf(item), listOf(record), 0,
            places = listOf(Place(1, "Home", null), Place(2, "Garage", 1), Place(3, "garagé", 1), Place(4, "Shelf", 3)),
        )
        val data = BackupCodec.decode(json)
        assertEquals(listOf(1L, 2L, 4L), data.places.map { it.id })
        assertEquals(2L, data.places.single { it.id == 4L }.parentId)
        assertEquals(2L, data.items.single().usualPlaceId)
        assertEquals(4L, data.records.single().placeId)
    }

    @Test
    fun aPhotoPathSharedByTwoRecordsIsKeptOnlyOnTheFirst() {
        val item = Item(id = 1, name = "Car", emoji = null, captureGps = false, pinned = false, archived = false,
            searchText = "car", createdAt = 1)
        val a = LocationRecord(id = 1, itemId = 1, description = "a", note = null, photoPath = "photos/x.jpg", createdAt = 1)
        val b = a.copy(id = 2, description = "b")
        val records = BackupCodec.decode(BackupCodec.encode(listOf(item), listOf(a, b), 0, emptyList())).records
        assertEquals(listOf("photos/x.jpg", null), records.map { it.photoPath })
    }

    @Test
    fun usualPlaceSurvivesAV3RoundTripAndOlderBackupsHaveNone() {
        val place = Place(id = 5, name = "Garage", parentId = null)
        val item = Item(id = 1, name = "Bike", emoji = null, captureGps = false, pinned = false, archived = false,
            searchText = "bike", createdAt = 1, usualPlaceId = 5, usualText = "shelf")
        val back = BackupCodec.decode(BackupCodec.encode(listOf(item), emptyList(), 0, listOf(place))).items.single()
        assertEquals(5L, back.usualPlaceId)
        assertEquals("shelf", back.usualText)

        val v2 = """{"version":2,"places":[],"items":[{"id":1,"name":"A","captureGps":false,"pinned":false,"archived":false,"createdAt":1}],"records":[]}"""
        val old = BackupCodec.decode(v2).items.single()
        assertNull(old.usualPlaceId)
        assertNull(old.usualText)

        val bad = """{"version":3,"places":[],"items":[{"id":1,"name":"A","captureGps":false,"pinned":false,"archived":false,"createdAt":1,"usualPlaceId":9}],"records":[]}"""
        assertTrue(try { BackupCodec.decode(bad); false } catch (_: BackupException) { true })
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
