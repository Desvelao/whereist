package app.locator.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.locator.data.AppDatabase
import app.locator.data.ItemRepository
import app.locator.data.UsualPlace
import app.locator.location.Fix
import app.locator.location.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ItemRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ItemRepository(db.itemDao(), db.locationDao(), db.placeDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun searchIgnoresCaseAndAccentsOnNameAndCurrentLocation() = runBlocking {
        val keys = repo.addItem("Home keys", null)
        val car = repo.addItem("Car", null)
        repo.saveLocation(keys, "Cajón de la cocina")
        repo.saveLocation(car, "Level -2")
        val vm = HomeViewModel(repo) { null }

        withTimeout(5_000) { vm.state.first { it.loaded && it.items.size == 2 } }

        vm.setQuery("CAJON")
        assertEquals(listOf("Home keys"), withTimeout(5_000) { vm.state.first { it.query == "CAJON" } }.items.map { it.item.name })

        vm.setQuery("car")
        assertEquals(listOf("Car"), withTimeout(5_000) { vm.state.first { it.query == "car" } }.items.map { it.item.name })

        vm.setQuery("zzz")
        assertEquals(emptyList<String>(), withTimeout(5_000) { vm.state.first { it.query == "zzz" } }.items.map { it.item.name })
    }

    @Test
    fun hereSavesAGoodFixAndShowsTheBar() = runBlocking {
        val car = repo.addItem("Car", null, captureGps = true)
        val vm = HomeViewModel(repo, LocationProvider { Fix(40.0, -3.0, 5f) })
        val row = withTimeout(5_000) { vm.state.first { it.items.size == 1 } }.items.single()

        vm.captureHere(row)

        val bar = withTimeout(5_000) { vm.bar.first { it != null } }!!
        assertEquals(null, bar.poorFixMeters)
        assertEquals(car, bar.itemId)
        assertEquals(40.0, repo.item(car).first()!!.currentLatitude!!, 1e-9)
    }

    @Test
    fun aPoorFixIsSavedButTheBarAsksForAPhotoOrNote() = runBlocking {
        repo.addItem("Car", null, captureGps = true)
        val vm = HomeViewModel(repo, LocationProvider { Fix(40.0, -3.0, 85f) })
        val row = withTimeout(5_000) { vm.state.first { it.items.size == 1 } }.items.single()

        vm.captureHere(row)

        assertEquals(85, withTimeout(5_000) { vm.bar.first { it != null } }!!.poorFixMeters)
    }

    @Test
    fun noFixSavesNothingAndReportsIt() = runBlocking {
        val car = repo.addItem("Car", null, captureGps = true)
        val vm = HomeViewModel(repo) { null }
        val row = withTimeout(5_000) { vm.state.first { it.items.size == 1 } }.items.single()

        vm.captureHere(row)

        withTimeout(5_000) { vm.message.first { it != null } }
        assertEquals(null, vm.bar.value)
        assertEquals(null, repo.item(car).first()!!.currentRecordId)
    }

    @Test
    fun undoOnTheBarRemovesTheSavedRecord() = runBlocking {
        val car = repo.addItem("Car", null, captureGps = true)
        val vm = HomeViewModel(repo, LocationProvider { Fix(1.0, 2.0, 5f) })
        val row = withTimeout(5_000) { vm.state.first { it.items.size == 1 } }.items.single()
        vm.captureHere(row)
        withTimeout(5_000) { vm.bar.first { it != null } }

        vm.undoBar()

        assertEquals(null, vm.bar.value)
        assertEquals(null, withTimeout(5_000) { repo.item(car).first { it?.currentRecordId == null } }!!.currentRecordId)
    }

    @Test
    fun searchAlsoFindsThingsByTheirPlace() = runBlocking {
        val home = repo.addPlace("Home", null)
        val garage = repo.addPlace("Garage", home)
        val drill = repo.addItem("Drill", null)
        val keys = repo.addItem("Keys", null)
        repo.saveLocation(drill, "second shelf", null, garage)
        repo.saveLocation(keys, "drawer")
        val vm = HomeViewModel(repo) { null }
        withTimeout(5_000) { vm.state.first { it.items.size == 2 } }

        vm.setQuery("garage")

        assertEquals(listOf("Drill"), withTimeout(5_000) { vm.state.first { it.query == "garage" } }.items.map { it.item.name })
    }

    @Test
    fun outOfPlaceFilterShowsOnlyItemsAwayFromTheirUsualPlace() = runBlocking {
        val bike = repo.addItem("Bike", null)
        val keys = repo.addItem("Keys", null)
        repo.editItem(bike, "Bike", null, usual = UsualPlace(null, "garage"))
        repo.editItem(keys, "Keys", null, usual = UsualPlace(null, "drawer"))
        repo.saveLocation(bike, "friend's house")
        repo.saveLocation(keys, "drawer")
        val vm = HomeViewModel(repo) { null }

        assertEquals(1, withTimeout(5_000) { vm.state.first { it.loaded && it.items.size == 2 } }.outOfPlaceCount)
        vm.setOutOfPlaceOnly(true)
        val shown = withTimeout(5_000) { vm.state.first { it.outOfPlaceOnly } }
        assertEquals(listOf("Bike"), shown.items.map { it.item.name })

        repo.putBack(bike)
        val after = withTimeout(5_000) { vm.state.first { it.outOfPlaceCount == 0 } }
        assertEquals(2, after.items.size) // the filter drops itself when nothing is out of place
    }
}
