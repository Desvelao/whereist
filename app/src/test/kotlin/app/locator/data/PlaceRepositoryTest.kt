package app.locator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ItemRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ItemRepository(db.itemDao(), db.locationDao(), db.placeDao()) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun paths() = PlaceTree.paths(repo.places().first()).values.toSet()

    @Test
    fun placesNestAndAnExistingSiblingIsReusedNotDuplicated() = runBlocking {
        val home = repo.addPlace("Home", null)
        val garage = repo.addPlace(" Garage ", home)
        assertEquals(garage, repo.addPlace("garage", home))
        assertEquals(setOf("Home", "Home › Garage"), paths())
        // The same name under another parent is a different place.
        val office = repo.addPlace("Office", null)
        assertTrue(repo.addPlace("Garage", office) != garage)
    }

    @Test
    fun blankNamesAndUnknownParentsAreRejected() = runBlocking {
        try { repo.addPlace(" ", null); throw AssertionError() } catch (_: IllegalArgumentException) {}
        try { repo.addPlace("X", 999); throw AssertionError() } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun aRecordCanBeFiledInAPlaceWithOrWithoutText() = runBlocking {
        val garage = repo.addPlace("Garage", repo.addPlace("Home", null))
        val drill = repo.addItem("Drill", null)
        repo.saveLocation(drill, null, null, garage)
        var row = repo.item(drill).first()!!
        assertNull(row.currentDescription)
        assertEquals(garage, row.currentPlaceId)
        now += 1_000
        repo.saveLocation(drill, "second shelf", "left", garage)
        row = repo.item(drill).first()!!
        assertEquals("second shelf", row.currentDescription)
        assertEquals(garage, row.currentPlaceId)
    }

    @Test
    fun aLocationNeedsTextOrAPlaceAndTheKnownPlace() = runBlocking {
        val item = repo.addItem("Keys", null)
        try { repo.saveLocation(item, " ", null, null); throw AssertionError() } catch (_: IllegalArgumentException) {}
        try { repo.saveLocation(item, null, null, 999); throw AssertionError() } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun renamingToASiblingsNameIsRefused() = runBlocking {
        val home = repo.addPlace("Home", null)
        val garage = repo.addPlace("Garage", home)
        repo.addPlace("Attic", home)
        try { repo.renamePlace(garage, "attic"); throw AssertionError() } catch (_: PlaceNameTakenException) {}
        repo.renamePlace(garage, "Workshop")
        assertTrue("Home › Workshop" in paths())
    }

    @Test
    fun movingNeverPutsAPlaceInsideItselfAndRespectsNameClashes() = runBlocking {
        val home = repo.addPlace("Home", null)
        val garage = repo.addPlace("Garage", home)
        val shelf = repo.addPlace("Shelf B", garage)
        try { repo.movePlace(home, shelf); throw AssertionError() } catch (_: IllegalArgumentException) {}
        try { repo.movePlace(garage, garage); throw AssertionError() } catch (_: IllegalArgumentException) {}

        repo.movePlace(shelf, home)
        assertTrue("Home › Shelf B" in paths())
        repo.movePlace(garage, null)
        assertTrue("Garage" in paths())

        val office = repo.addPlace("Office", null)
        repo.addPlace("Shelf B", office)
        try { repo.movePlace(shelf, office); throw AssertionError() } catch (_: PlaceNameTakenException) {}
    }

    @Test
    fun deletingAPlaceMovesItsChildrenUpAndKeepsTheRecordsText() = runBlocking {
        val home = repo.addPlace("Home", null)
        val garage = repo.addPlace("Garage", home)
        val shelf = repo.addPlace("Shelf B", garage)
        val drill = repo.addItem("Drill", null)
        repo.saveLocation(drill, "second shelf", null, garage)

        repo.deletePlace(garage)

        assertEquals(setOf("Home", "Home › Shelf B"), paths())
        assertEquals(home, repo.places().first().single { it.id == shelf }.parentId)
        val row = repo.item(drill).first()!!
        assertEquals("second shelf", row.currentDescription)
        assertNull(row.currentPlaceId)
    }

    @Test
    fun deletingATopLevelPlaceMakesItsChildrenTopLevel() = runBlocking {
        val home = repo.addPlace("Home", null)
        repo.addPlace("Garage", home)
        repo.deletePlace(home)
        assertEquals(setOf("Garage"), paths())
    }

    @Test
    fun searchMatchesThePlacePathIgnoringAccents() = runBlocking {
        val cocina = repo.addPlace("Cocina", repo.addPlace("Casa", null))
        val keys = repo.addItem("Llaves", null)
        repo.saveLocation(keys, null, null, cocina)
        val row = repo.item(keys).first()!!
        val path = PlaceTree.pathOf(repo.places().first(), row.currentPlaceId)
        assertTrue(row.matches("CASA", path))
        assertTrue(row.matches("casa › coc", path))
        assertTrue(!row.matches("garaje", path))
    }

    @Test
    fun addItemWithLocationSavesUsualPlaceAndFirstLocation() = runBlocking {
        val shed = repo.addPlace("Shed", null)
        val id = repo.addItemWithLocation("Drill", null, false, UsualPlace(shed, " top shelf "), "box", shed)
        val row = repo.item(id).first()!!
        assertEquals(shed, row.item.usualPlaceId)
        assertEquals("top shelf", row.item.usualText)
        assertEquals(shed, row.currentPlaceId)
        assertEquals(1, repo.history(id).first().size)
    }

    @Test
    fun addItemWithLocationWithoutLocationCreatesNoRecordAndBadPlaceCreatesNothing() = runBlocking {
        val id = repo.addItemWithLocation("Keys", null, false, UsualPlace.NONE, " ", null)
        assertTrue(repo.history(id).first().isEmpty())
        val failed = runCatching { repo.addItemWithLocation("Bad", null, false, UsualPlace.NONE, null, 999L) }
        assertTrue(failed.exceptionOrNull() is IllegalArgumentException)
        assertEquals(1, repo.homeItems().first().size)
    }

    @Test
    fun addItemWithLocationKeepsGpsAndPhotoOnTheFirstRecordAndRejectsPhotoWithoutLocation() = runBlocking {
        val id = repo.addItemWithLocation("Car", null, true, UsualPlace.NONE, "level -2", null, app.locator.location.Fix(1.0, 2.0, 8f), "photos/a.jpg")
        val record = repo.history(id).first().single()
        assertEquals(1.0, record.latitude!!, 0.0)
        assertEquals("level -2", record.description)
        assertEquals("photos/a.jpg", record.photoPath)
        val failed = runCatching { repo.addItemWithLocation("Bike", null, false, UsualPlace.NONE, null, null, null, "photos/b.jpg") }
        assertTrue(failed.exceptionOrNull() is IllegalArgumentException)
        assertEquals(1, repo.homeItems().first().size)
    }
}
