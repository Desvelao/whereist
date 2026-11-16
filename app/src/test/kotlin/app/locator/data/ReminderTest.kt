package app.locator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeScheduler : ReminderScheduling {
    val scheduled = mutableMapOf<Long, Long>()
    override fun schedule(recordId: Long, atMillis: Long) { scheduled[recordId] = atMillis }
    override fun cancel(recordId: Long) { scheduled.remove(recordId) }
}

@RunWith(RobolectricTestRunner::class)
class ReminderTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ItemRepository
    private val scheduler = FakeScheduler()
    private var now = 10_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ItemRepository(db.itemDao(), db.locationDao(), db.placeDao(), null, scheduler) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun carRecord(): Pair<Long, Long> {
        val car = repo.addItem("Car", null, true)
        return car to repo.saveGps(car, 1.0, 2.0, 5f)
    }

    @Test
    fun settingAReminderStoresItAndSchedulesTheAlarm() = runBlocking {
        val (car, record) = carRecord()
        repo.setReminder(record, 50_000)
        assertEquals(mapOf(record to 50_000L), scheduler.scheduled)
        assertEquals(50_000L, repo.item(car).first()!!.currentRemindAt)
    }

    @Test
    fun clearingCancelsTheAlarmAndTheStoredTime() = runBlocking {
        val (car, record) = carRecord()
        repo.setReminder(record, 50_000)
        repo.clearReminder(record)
        assertEquals(emptyMap<Long, Long>(), scheduler.scheduled)
        assertNull(repo.item(car).first()!!.currentRemindAt)
    }

    @Test
    fun undoAndDeleteCancelTheAlarm() = runBlocking {
        val (car, record) = carRecord()
        repo.setReminder(record, 50_000)
        repo.undoRecord(record)
        assertEquals(emptyMap<Long, Long>(), scheduler.scheduled)

        val second = repo.saveGps(car, 1.0, 2.0, 5f)
        repo.setReminder(second, 60_000)
        repo.deleteItem(car)
        assertEquals(emptyMap<Long, Long>(), scheduler.scheduled)
    }

    @Test
    fun reminderTargetCarriesThePlacePath() = runBlocking {
        val car = repo.addItem("Car", null, true)
        val home = repo.addPlace("Home", null)
        val garage = repo.addPlace("Garage", home)
        val record = repo.saveLocation(car, "second spot", null, garage)
        repo.setReminder(record, 50_000)
        assertEquals("Home › Garage", repo.reminderTarget(record)!!.placePath)

        val textOnly = repo.saveLocation(car, "street", null)
        repo.setReminder(textOnly, 60_000)
        assertNull(repo.reminderTarget(textOnly)!!.placePath)
    }

    @Test
    fun aStaleAlarmForARecordWithoutReminderHasNoTarget() = runBlocking {
        val car = repo.addItem("Car", null, true)
        val record = repo.saveLocation(car, "street", null)
        assertNull(repo.reminderTarget(record))
    }

    @Test
    fun aReminderOnAMissingRecordIsIgnored() = runBlocking {
        repo.setReminder(999, 50_000)
        assertEquals(emptyMap<Long, Long>(), scheduler.scheduled)
    }

    @Test
    fun afterARebootFutureRemindersAreRescheduledAndMissedOnesAreReturned() = runBlocking {
        val (_, future) = carRecord()
        val keys = repo.addItem("Keys", null)
        val missed = repo.saveLocation(keys, "drawer")
        repo.setReminder(future, 50_000)
        repo.setReminder(missed, 5_000)
        scheduler.scheduled.clear() // the reboot wiped the alarms

        val late = mutableListOf<ReminderTarget>()
        repo.restoreReminders { late += it; true }

        assertEquals(mapOf(future to 50_000L), scheduler.scheduled)
        assertEquals(listOf("Keys"), late.map { it.item.name })
        assertNull(db.locationDao().get(missed)!!.remindAt)
        assertEquals(50_000L, db.locationDao().get(future)!!.remindAt)
    }

    @Test
    fun aMissedReminderThatCouldNotBeShownIsKept() = runBlocking {
        val keys = repo.addItem("Keys", null)
        val missed = repo.saveLocation(keys, "drawer")
        repo.setReminder(missed, 5_000)

        repo.restoreReminders { false } // notifications are off

        assertEquals(5_000L, db.locationDao().get(missed)!!.remindAt)
        repo.restoreReminders { true }
        assertNull(db.locationDao().get(missed)!!.remindAt)
    }

    @Test
    fun reminderTargetIsNullWhenTheRecordIsGone() = runBlocking {
        val (_, record) = carRecord()
        repo.undoRecord(record)
        assertNull(repo.reminderTarget(record))
    }

    @Test
    fun gpsItemsListsOnlyActiveGpsItems() = runBlocking {
        val car = repo.addItem("Car", null, true)
        val bike = repo.addItem("Bike", null, true)
        repo.addItem("Keys", null)
        repo.setArchived(bike, true)
        assertEquals(listOf(car), repo.gpsItemsNow().map { it.id })
        assertEquals(listOf(car), repo.gpsItems().first().map { it.id })
    }
}
