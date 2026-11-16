package app.locator.data

object QuickItem {
    /** The chosen quick-save item if it is still a candidate, otherwise the first candidate, otherwise none. */
    fun resolve(storedId: Long?, candidates: List<Item>): Item? =
        candidates.firstOrNull { it.id == storedId } ?: candidates.firstOrNull()
}
