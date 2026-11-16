package app.locator

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.Room
import app.locator.data.AppDatabase
import app.locator.data.BackupService
import app.locator.data.ItemRepository
import app.locator.data.MIGRATION_1_2
import app.locator.data.MIGRATION_2_3
import app.locator.data.MIGRATION_3_4
import app.locator.data.MIGRATION_4_5
import app.locator.data.PhotoStore
import app.locator.location.AndroidHeadingProvider
import app.locator.location.AndroidLocationProvider
import app.locator.location.HeadingProvider
import app.locator.location.LocationProvider
import app.locator.quick.QuickItemStore
import app.locator.reminder.AlarmReminderScheduler
import app.locator.reminder.ReminderNotifier
import app.locator.ui.theme.ThemeStore
import app.locator.widget.LocatorWidget

/** Hand-written dependency wiring: four screens do not justify a DI framework. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "locator.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    val photos: PhotoStore by lazy { PhotoStore(appContext.filesDir) }

    val repository: ItemRepository by lazy {
        ItemRepository(database.itemDao(), database.locationDao(), database.placeDao(), photos, AlarmReminderScheduler(appContext))
    }

    val backup: BackupService by lazy { BackupService(database, photos, AlarmReminderScheduler(appContext)) }

    val location: LocationProvider by lazy { AndroidLocationProvider(appContext) }

    val heading: HeadingProvider by lazy { AndroidHeadingProvider(appContext) }

    val quickItem: QuickItemStore by lazy { QuickItemStore(appContext, repository) }

    val theme: ThemeStore by lazy { ThemeStore(appContext) }

    val notifier: ReminderNotifier by lazy { ReminderNotifier(appContext) }

    /** Redraws the home-screen widget (it shows the quick-save item). */
    suspend fun refreshWidget() = LocatorWidget().updateAll(appContext)
}
