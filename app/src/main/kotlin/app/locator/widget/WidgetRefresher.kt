package app.locator.widget

import app.locator.data.ItemRepository
import app.locator.quick.QuickItemStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Keeps the widget in step with the data: whenever the items, the places or the chosen quick item change (a save,
 * a put-back, a rename or delete, a backup import), the widget is redrawn. The widget shows "where, how long ago"
 * for the quick item and has no periodic update of its own.
 */
class WidgetRefresher(
    private val repo: ItemRepository,
    private val quick: QuickItemStore,
    private val refresh: suspend () -> Unit,
    private val debounceMs: Long = 500,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(repo.homeItems(), repo.places(), quick.selectedId) { items, places, selected -> Triple(items, places, selected) }
            .distinctUntilChanged()
            .drop(1) // the first value is the state at start-up: the widget already shows it
            .debounce(debounceMs)
            .collect { refresh() }
    }
}
