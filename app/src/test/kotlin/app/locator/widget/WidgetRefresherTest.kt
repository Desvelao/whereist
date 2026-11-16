package app.locator.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.locator.data.AppDatabase
import app.locator.data.ItemRepository
import app.locator.quick.QuickItemStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class WidgetRefresherTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ItemRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshes = AtomicInteger()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ItemRepository(db.itemDao(), db.locationDao(), db.placeDao())
        WidgetRefresher(repo, QuickItemStore(context, repo), { refreshes.incrementAndGet() }, debounceMs = 20).start(scope)
    }

    @After
    fun tearDown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        db.close()
    }

    private suspend fun waitFor(count: Int) = withTimeout(5_000) { while (refreshes.get() < count) delay(20) }

    @Test
    fun aSaveRedrawsTheWidgetButStartingUpDoesNot() = runBlocking {
        delay(300)
        assertEquals(0, refreshes.get())
        val car = repo.addItem("Car", null, true)
        waitFor(1)
        repo.saveLocation(car, "Level -2")
        waitFor(2)
        repo.putBack(car) // no usual place: nothing changes, nothing is redrawn
        delay(300)
        assertEquals(2, refreshes.get())
    }
}
