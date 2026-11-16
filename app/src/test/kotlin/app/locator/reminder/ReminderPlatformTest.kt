package app.locator.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import app.locator.data.Item
import app.locator.data.LocationRecord
import app.locator.data.ReminderTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReminderPlatformTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    @Test
    fun everyRecordHasItsOwnAlarmAndCancelOnlyRemovesThatOne() {
        val scheduler = AlarmReminderScheduler(app)
        scheduler.schedule(1, 10_000)
        scheduler.schedule(2, 20_000)
        assertEquals(2, alarms.scheduledAlarms.size)
        scheduler.schedule(1, 30_000) // rescheduling the same record replaces its alarm
        assertEquals(2, alarms.scheduledAlarms.size)
        scheduler.cancel(1)
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(20_000L, alarms.scheduledAlarms.single().triggerAtMs)
    }

    private val target = ReminderTarget(
        Item(id = 1, name = "Car", emoji = null, captureGps = true, pinned = false, archived = false, searchText = "car", createdAt = 1),
        LocationRecord(id = 7, itemId = 1, description = "level -2", note = null, remindAt = 5_000, createdAt = 1),
    )

    @Test
    fun theNotifierShowsWhenAllowedAndReportsFalseWhenNotificationsAreOff() {
        if (Build.VERSION.SDK_INT >= 33) shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
        manager.setNotificationsEnabled(false)
        assertFalse(ReminderNotifier(app).show(target))
        manager.setNotificationsEnabled(true)
        assertTrue(ReminderNotifier(app).show(target))
    }
}
