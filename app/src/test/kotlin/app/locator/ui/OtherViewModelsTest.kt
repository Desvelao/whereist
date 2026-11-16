package app.locator.ui

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.locator.R
import app.locator.data.AppDatabase
import app.locator.data.BackupService
import app.locator.data.ItemRepository
import app.locator.data.PhotoStore
import app.locator.data.UsualPlace
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Detail, Places and Settings view models, against an in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OtherViewModelsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: ItemRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ItemRepository(db.itemDao(), db.locationDao(), db.placeDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** The view models write on background threads, so wait for the condition instead of asserting at once. */
    private suspend fun eventually(check: suspend () -> Boolean) {
        withTimeout(5_000) {
            while (!check()) kotlinx.coroutines.delay(20)
        }
    }

    private fun detail(id: Long) = DetailViewModel(id, repo, { null }, { kotlinx.coroutines.flow.emptyFlow() })

    private suspend fun DetailViewModel.item() = withTimeout(5_000) { state.first { it.loaded && it.item != null } }.item!!

    @Test
    fun detailPutBackSavesTheUsualPlaceAsTheNewestLocation() = runBlocking {
        val garage = repo.addPlace("Garage", null)
        val id = repo.addItem("Drill", null, usual = UsualPlace(garage, "shelf"))
        repo.saveLocation(id, "kitchen")
        val vm = detail(id)
        assertTrue(vm.item().isOutOfPlace)
        vm.putBack()
        val row = withTimeout(5_000) { vm.state.first { it.item?.currentPlaceId == garage } }.item!!
        assertFalse(row.isOutOfPlace)
        assertEquals("shelf", row.currentDescription)
    }

    @Test
    fun detailPinArchiveUndoAndDelete() = runBlocking {
        val id = repo.addItem("Keys", null)
        repo.saveLocation(id, "drawer")
        repo.saveLocation(id, "pocket")
        val vm = detail(id)
        vm.setPinned(true)
        assertTrue(withTimeout(5_000) { vm.state.first { it.item?.item?.pinned == true } }.item!!.item.pinned)
        vm.setArchived(true)
        assertTrue(withTimeout(5_000) { vm.state.first { it.item?.item?.archived == true } }.item!!.item.archived)
        vm.undo()
        assertEquals("drawer", withTimeout(5_000) { vm.state.first { it.history.size == 1 && it.item?.currentDescription == "drawer" } }.item!!.currentDescription)
        vm.delete()
        assertNull(withTimeout(5_000) { vm.state.first { it.item == null } }.item)
    }

    @Test
    fun detailCreatePlaceReturnsNullForABlankName() = runBlocking {
        val vm = detail(repo.addItem("Keys", null))
        assertNull(vm.createPlace("  ", null))
        assertTrue(vm.createPlace("Home", null) != null)
    }

    @Test
    fun placesAddRenameDeleteAndTheDuplicateMessage() = runBlocking {
        val home = repo.addPlace("Home", null)
        repo.addPlace("Garage", home)
        val top = PlacesViewModel(null, repo)
        top.add("Office")
        eventually { repo.places().first().filter { it.parentId == null }.map { it.name }.toSet() == setOf("Home", "Office") }

        val homeVm = PlacesViewModel(home, repo)
        homeVm.rename("office") // a sibling with that name exists
        eventually { homeVm.message.value == R.string.place_name_taken }
        homeVm.messageShown()
        assertNull(homeVm.message.value)

        homeVm.rename("House")
        eventually { repo.places().first().any { it.id == home && it.name == "House" } }
        homeVm.delete()
        eventually { repo.places().first().none { it.id == home } }
        assertEquals(null, repo.places().first().single { it.name == "Garage" }.parentId) // moved up, not deleted
    }

    private suspend fun SettingsViewModel.result() =
        withTimeout(10_000) { status.first { it != BackupStatus.Idle && it != BackupStatus.Working } }

    private fun settings(): SettingsViewModel {
        val photos = PhotoStore(tmp.newFolder("files")).also { it.prepare() }
        return SettingsViewModel(repo, BackupService(db, photos), QuickItemStore(context, repo)) {}
    }

    @Test
    fun settingsExportThenImportReportsTheCounts() = runBlocking {
        val id = repo.addItem("Keys", null)
        repo.saveLocation(id, "drawer")
        val vm = settings()
        val file = File(tmp.newFolder("out"), "backup.zip")
        vm.export(Uri.fromFile(file), context.contentResolver)
        assertEquals(BackupStatus.Exported, vm.result())
        vm.statusShown()
        assertEquals(BackupStatus.Idle, vm.status.value)
        vm.import(Uri.fromFile(file), context.contentResolver)
        assertEquals(BackupStatus.Imported(items = 1, records = 1), vm.result())
    }

    @Test
    fun settingsImportOfAGarbageFileIsInvalidAndAMissingFileFails() = runBlocking {
        val vm = settings()
        val garbage = File(tmp.newFolder("bad"), "garbage.zip").also { it.writeText("not a zip") }
        vm.import(Uri.fromFile(garbage), context.contentResolver)
        assertEquals(BackupStatus.Invalid, vm.result())
        vm.statusShown()
        vm.import(Uri.fromFile(File(tmp.root, "missing.zip")), context.contentResolver)
        assertEquals(BackupStatus.Failed, vm.result())
    }

    @Test
    fun coordinatesAreFormattedWithFivePlacesAndADot() {
        assertEquals("40.41680, -3.70380", coordinates(40.4168, -3.7038))
    }
}
