package app.locator

import android.app.Application
import app.locator.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LocatorApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Every save made while the process is alive (app, quick save, reminders) redraws the widget.
        WidgetRefresher(container.repository, container.quickItem, container::refreshWidget).start(scope)
    }
}
