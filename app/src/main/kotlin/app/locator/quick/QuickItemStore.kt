package app.locator.quick

import android.content.Context
import app.locator.data.Item
import app.locator.data.ItemRepository
import app.locator.data.QuickItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which GPS item the widget and the app shortcut save. Stored in preferences (not part of backups). */
class QuickItemStore(context: Context, private val repo: ItemRepository) {
    private val prefs = context.applicationContext.getSharedPreferences("quick", Context.MODE_PRIVATE)
    private val _selectedId = MutableStateFlow(prefs.getLong(KEY, -1L).takeIf { it >= 0 })
    val selectedId: StateFlow<Long?> = _selectedId

    fun select(id: Long) {
        prefs.edit().putLong(KEY, id).apply()
        _selectedId.value = id
    }

    suspend fun current(): Item? = QuickItem.resolve(_selectedId.value, repo.gpsItemsNow())

    private companion object {
        const val KEY = "itemId"
    }
}
