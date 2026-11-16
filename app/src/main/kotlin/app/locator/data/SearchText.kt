package app.locator.data

import java.text.Normalizer

object SearchText {
    private val marks = Regex("\\p{Mn}+")

    /** Lowercase and strip accents, so "Cajón" and "cajon" match. */
    fun normalize(text: String): String =
        marks.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFD), "").lowercase()
}

/**
 * Search ignores case and accents; it matches the item's name, its current description and note, its place's path
 * and its usual spot.
 */
fun ItemWithCurrent.matches(query: String, placePath: String? = null): Boolean {
    val q = SearchText.normalize(query)
    if (q.isEmpty()) return true
    return item.searchText.contains(q) ||
        (currentDescription?.let { SearchText.normalize(it).contains(q) } ?: false) ||
        (currentNote?.let { SearchText.normalize(it).contains(q) } ?: false) ||
        (item.usualText?.let { SearchText.normalize(it).contains(q) } ?: false) ||
        (placePath?.let { SearchText.normalize(it).contains(q) } ?: false)
}
