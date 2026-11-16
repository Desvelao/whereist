package app.locator.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.locator.data.AppDatabase
import app.locator.data.ItemRepository
import app.locator.location.Fix
import app.locator.location.LocationProvider
import app.locator.quick.QuickItemStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuickSaveViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ItemRepository
    private lateinit var quick: QuickItemStore
    private var now = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ItemRepository(db.itemDao(), db.locationDao(), db.placeDao())
        quick = QuickItemStore(context, repo)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(fix: Fix?) = QuickSaveViewModel(repo, { fix }, quick) { now }

    private suspend fun QuickSaveViewModel.awaitDone(): QuickState =
        withTimeout(5_000) { state.first { it !is QuickState.Loading && it !is QuickState.Fixing } }

    @Test
    fun withNoGpsItemThereIsNothingToSave() = runBlocking {
        repo.addItem("Keys", null)
        val vm = vm(Fix(1.0, 2.0, 5f))
        vm.run()
        assertEquals(QuickState.NoItem, vm.awaitDone())
    }

    @Test
    fun savesTheQuickItemWithTheFix() = runBlocking {
        val car = repo.addItem("Car", null, true)
        val vm = vm(Fix(40.0, -3.0, 6f))
        vm.run()
        val saved = vm.awaitDone() as QuickState.Saved
        assertEquals(car, saved.bar.itemId)
        assertEquals(null, saved.bar.poorFixMeters)
        assertEquals(40.0, repo.item(car).first()!!.currentLatitude!!, 1e-9)
    }

    @Test
    fun theChosenItemWinsOverTheFirstOne() = runBlocking {
        repo.addItem("Car", null, true)
        val bike = repo.addItem("Bike", null, true)
        quick.select(bike)
        val vm = vm(Fix(1.0, 2.0, 5f))
        vm.run()
        assertEquals(bike, (vm.awaitDone() as QuickState.Saved).bar.itemId)
    }

    @Test
    fun aPoorFixIsSavedWithAWarning() = runBlocking {
        repo.addItem("Car", null, true)
        val vm = vm(Fix(1.0, 2.0, 90f))
        vm.run()
        assertEquals(90, (vm.awaitDone() as QuickState.Saved).bar.poorFixMeters)
    }

    @Test
    fun noFixSavesNothingAndCanBeRetried() = runBlocking {
        val car = repo.addItem("Car", null, true)
        var fix: Fix? = null
        val vm = QuickSaveViewModel(repo, { fix }, quick) { now }
        vm.run()
        assertTrue(vm.awaitDone() is QuickState.NoFix)
        assertEquals(null, repo.item(car).first()!!.currentRecordId)

        fix = Fix(1.0, 2.0, 4f)
        vm.retry()
        assertTrue(vm.awaitDone() is QuickState.Saved)
    }

    @Test
    fun theReminderIsStartedRelativeToNow() = runBlocking {
        val car = repo.addItem("Car", null, true)
        val vm = vm(Fix(1.0, 2.0, 4f))
        vm.run()
        val saved = vm.awaitDone() as QuickState.Saved
        vm.setReminder(saved.bar.recordId, 90)
        assertEquals(now + 90 * 60_000L, withTimeout(5_000) { repo.item(car).first { it?.currentRemindAt != null } }!!.currentRemindAt)
    }
}
