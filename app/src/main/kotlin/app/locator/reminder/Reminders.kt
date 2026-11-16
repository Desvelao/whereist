package app.locator.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.locator.LocatorApp
import app.locator.MainActivity
import app.locator.R
import app.locator.data.PlaceTree
import app.locator.data.ReminderScheduling
import app.locator.data.ReminderTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarm for a reminder. It uses an inexact-while-idle alarm, so no exact-alarm permission is needed;
 * it can arrive a few minutes late, so set it with a margin before the time that matters.
 */
class AlarmReminderScheduler(private val context: Context) : ReminderScheduling {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(recordId: Long, atMillis: Long) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending(recordId))
    }

    override fun cancel(recordId: Long) {
        alarms.cancel(pending(recordId))
    }

    private fun pending(recordId: Long): PendingIntent {
        // Intents match by action and data, not extras, and the request code is only an Int: the record id in the
        // data makes every record's PendingIntent distinct and exact.
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.parse("locator://reminder/$recordId"))
            .putExtra(EXTRA_RECORD_ID, recordId)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTION_REMIND = "app.locator.REMIND"
        const val EXTRA_RECORD_ID = "recordId"
    }
}

class ReminderNotifier(private val context: Context) {
    /** Posts the notification. Returns false when it could not be shown (permission denied or notifications off). */
    @SuppressLint("MissingPermission") // checked below
    fun show(target: ReminderTarget): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        if (!granted || !manager.areNotificationsEnabled()) return false
        ensureChannel()
        val record = target.record
        val where = PlaceTree.whereLabel(record.description, target.placePath) ?: context.getString(R.string.location_gps, record.accuracyMeters?.toInt() ?: 0)
        val open = PendingIntent.getActivity(
            context, record.id.toInt(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title, target.item.name))
            .setContentText(context.getString(R.string.notif_text, where))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        val lat = record.latitude
        val lon = record.longitude
        if (lat != null && lon != null) {
            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$lat,$lon(${Uri.encode(target.item.name)})"))
            builder.addAction(
                0,
                context.getString(R.string.action_navigate),
                PendingIntent.getActivity(context, record.id.toInt(), geo, PendingIntent.FLAG_IMMUTABLE),
            )
        }
        manager.notify(record.id.toInt(), builder.build())
        return true
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_HIGH)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "reminders"
        const val LEGACY_CHANNEL_ID = "parking_timer"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getLongExtra(AlarmReminderScheduler.EXTRA_RECORD_ID, -1L)
        if (recordId < 0) return
        val container = (context.applicationContext as LocatorApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val target = container.repository.reminderTarget(recordId)
                // Keep the reminder when it could not be shown, so it stays visible in the app instead of vanishing.
                if (target == null || container.notifier.show(target)) container.repository.clearReminder(recordId)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms do not survive a reboot or an app update: schedule the pending reminders again, and notify the ones missed meanwhile. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as LocatorApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.repository.restoreReminders { container.notifier.show(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
