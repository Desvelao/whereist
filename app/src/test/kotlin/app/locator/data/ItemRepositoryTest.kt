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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ItemRepositoryTest {
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

    private fun tick() { now += 1_000 }

    @Test
    fun savingForADeletedItemFailsWithAClearError() = runBlocking {
        val id = repo.addItem("Keys", null)
        repo.deleteItem(id)
        assertTrue(runCatching { repo.saveLocation(id, "drawer") }.exceptionOrNull() is UnknownItemException)
        assertTrue(runCatching { repo.saveGps(id, 1.0, 2.0, 5f) }.exceptionOrNull() is UnknownItemException)
    }

    @Test
    fun anItemInsideItsUsualPlacesSubtreeIsNotOutOfPlace() = runBlocking {
        val home = repo.addPlace("Home", null)
        val garage = repo.addPlace("Garage", home)
        val shelf = repo.addPlace("Shelf B", garage)
        val kitchen = repo.addPlace("Kitchen", home)
        val id = repo.addItem("Drill", null, usual = UsualPlace(garage, null))
        repo.saveLocation(id, null, placeId = shelf)
        val places = repo.places().first()
        assertFalse(repo.homeItems().first().single().isOutOfPlace(places))
        repo.saveLocation(id, null, placeId = kitchen)
        assertTrue(repo.homeItems().first().single().isOutOfPlace(places))
    }

    @Test
    fun newestRecordIsTheCurrentLocation() = runBlocking {
        val id = repo.addItem("Home keys", "🔑")
        repo.saveLocation(id, "kitchen drawer"); tick()
        repo.saveLocation(id, "coat pocket", "left side")
        val row = repo.homeItems().first().single()
        assertEquals("coat pocket", row.currentDescription)
        assertEquals("left side", row.currentNote)
        assertEquals(now, row.currentAt)
    }

    @Test
    fun itemWithoutRecordsHasNoCurrentLocation() = runBlocking {
        repo.addItem("Passport", null)
        val row = repo.homeItems().first().single()
        assertNull(row.currentDescription)
        assertNull(row.currentAt)
    }

    @Test
    fun historyIsNewestFirstAndUndoRemovesTheLatest() = runBlocking {
        val id = repo.addItem("Keys", null)
        repo.saveLocation(id, "a"); tick()
        repo.saveLocation(id, "b"); tick()
        repo.saveLocation(id, "c")
        assertEquals(listOf("c", "b", "a"), repo.history(id).first().map { it.description })
        assertTrue(repo.undoLast(id))
        assertEquals(listOf("b", "a"), repo.history(id).first().map { it.description })
        assertEquals("b", repo.item(id).first()!!.currentDescription)
    }

    @Test
    fun undoWithNoHistoryDoesNothing() = runBlocking {
        val id = repo.addItem("Keys", null)
        assertFalse(repo.undoLast(id))
    }

    @Test
    fun deletingAnItemDeletesItsRecords() = runBlocking {
        val id = repo.addItem("Keys", null)
        repo.saveLocation(id, "a")
        repo.deleteItem(id)
        assertTrue(repo.homeItems().first().isEmpty())
        assertTrue(repo.history(id).first().isEmpty())
    }

    @Test
    fun suggestionsListOwnMostFrequentFirstThenOthers() = runBlocking {
        val keys = repo.addItem("Keys", null)
        val wallet = repo.addItem("Wallet", null)
        repo.saveLocation(keys, "drawer"); tick()
        repo.saveLocation(keys, "pocket"); tick()
        repo.saveLocation(keys, "pocket"); tick()
        repo.saveLocation(wallet, "desk"); tick()
        repo.saveLocation(wallet, "Drawer") // same place as the keys' "drawer": not listed twice
        assertEquals(listOf("pocket", "drawer", "desk"), repo.suggestions(keys))
    }

    @Test
    fun archivedItemsAreHiddenFromHomeAndPinnedComeFirst() = runBlocking {
        val a = repo.addItem("A", null); tick()
        val b = repo.addItem("B", null); tick()
        val c = repo.addItem("C", null)
        repo.setPinned(a, true)
        repo.setArchived(b, true)
        assertEquals(listOf("A", "C"), repo.homeItems().first().map { it.item.name })
        assertEquals(listOf("B"), repo.homeItems(archived = true).first().map { it.item.name })
        repo.setArchived(b, false)
        assertEquals(setOf(a, b, c), repo.homeItems().first().map { it.item.id }.toSet())
    }

    @Test
    fun blankNamesAndLocationsAreRejected() {
        runBlocking {
            val id = repo.addItem("Keys", null)
            assertThrowsIllegalArgument { repo.addItem("  ", null) }
            assertThrowsIllegalArgument { repo.saveLocation(id, " ") }
            assertThrowsIllegalArgument { repo.editItem(id, "", null) }
        }
    }

    @Test
    fun editUpdatesNameEmojiAndSearchText() = runBlocking {
        val id = repo.addItem("Keys", null)
        repo.editItem(id, " Llaves Cajón ", "🗝️")
        val item = repo.item(id).first()!!.item
        assertEquals("Llaves Cajón", item.name)
        assertEquals("🗝️", item.emoji)
        assertEquals("llaves cajon", item.searchText)
    }

    private suspend fun assertThrowsIllegalArgument(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun putBackSavesTheUsualPlaceAndClearsOutOfPlace() = runBlocking {
        val garage = repo.addPlace("Garage", null)
        val id = repo.addItem("Bike", null)
        repo.editItem(id, "Bike", null, usual = UsualPlace(garage, "second shelf"))
        repo.saveLocation(id, "friend's house"); tick()
        assertTrue(repo.homeItems().first().single().isOutOfPlace)

        repo.putBack(id)
        val row = repo.homeItems().first().single()
        assertEquals("second shelf", row.currentDescription)
        assertEquals(garage, row.currentPlaceId)
        assertFalse(row.isOutOfPlace)
    }

    @Test
    fun anItemWithoutUsualPlaceOrRecordIsNeverOutOfPlace() = runBlocking {
        val id = repo.addItem("Keys", null)
        assertFalse(repo.homeItems().first().single().isOutOfPlace)
        repo.saveLocation(id, "drawer")
        assertFalse(repo.homeItems().first().single().isOutOfPlace)
        assertNull(repo.putBack(id))
        repo.editItem(id, "Keys", null, usual = UsualPlace(null, "Drawer"))
        assertFalse(repo.homeItems().first().single().isOutOfPlace) // accent/case-insensitive match
    }

    @Test
    fun deletingTheUsualPlaceClearsItAndUnknownPlacesAreRejected() = runBlocking {
        val garage = repo.addPlace("Garage", null)
        val id = repo.addItem("Bike", null)
        repo.editItem(id, "Bike", null, usual = UsualPlace(garage, null))
        repo.deletePlace(garage)
        assertNull(repo.homeItems().first().single().item.usualPlaceId)
        assertThrowsIllegalArgument { repo.editItem(id, "Bike", null, usual = UsualPlace(999, null)) }
    }
}
