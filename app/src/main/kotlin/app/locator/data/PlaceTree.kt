package app.locator.data

/** Pure helpers over the flat list of places (parentId links): paths, tree order, subtrees. All cycle-safe. */
object PlaceTree {
    const val SEPARATOR = " › "

    /** "Home › Garage › Shelf B" for every place. */
    fun paths(places: List<Place>): Map<Long, String> {
        val byId = places.associateBy { it.id }
        return places.associate { place ->
            val names = ArrayDeque<String>()
            val seen = mutableSetOf<Long>()
            var current: Place? = place
            while (current != null && seen.add(current.id)) {
                names.addFirst(current.name)
                current = current.parentId?.let(byId::get)
            }
            place.id to names.joinToString(SEPARATOR)
        }
    }

    fun pathOf(places: List<Place>, id: Long?): String? = id?.let { paths(places)[it] }

    /** "second shelf · Home › Garage" when both are set, whichever one is set otherwise, null when neither. */
    fun whereLabel(description: String?, placePath: String?): String? = when {
        description != null && placePath != null -> "$description · $placePath"
        else -> description ?: placePath
    }

    /** Depth-first order, siblings by name, with each place's depth (0 = top level). */
    fun ordered(places: List<Place>): List<Pair<Place, Int>> {
        val ids = places.map { it.id }.toSet()
        val children = places.groupBy { it.parentId?.takeIf { parent -> parent in ids } }
        val out = mutableListOf<Pair<Place, Int>>()
        val seen = mutableSetOf<Long>()
        fun visit(parent: Long?, depth: Int) {
            children[parent].orEmpty().sortedWith(compareBy({ SearchText.normalize(it.name) }, { it.id })).forEach { place ->
                if (seen.add(place.id)) {
                    out += place to depth
                    visit(place.id, depth + 1)
                }
            }
        }
        visit(null, 0)
        return out
    }

    /** The place and everything inside it. */
    fun subtree(places: List<Place>, id: Long): Set<Long> {
        val children = places.groupBy { it.parentId }
        val out = mutableSetOf<Long>()
        val stack = ArrayDeque(listOf(id))
        while (stack.isNotEmpty()) {
            val next = stack.removeLast()
            if (out.add(next)) children[next].orEmpty().forEach { stack.addLast(it.id) }
        }
        return out
    }

    /** Root to [id], inclusive. */
    fun breadcrumb(places: List<Place>, id: Long): List<Place> {
        val byId = places.associateBy { it.id }
        val out = ArrayDeque<Place>()
        val seen = mutableSetOf<Long>()
        var current = byId[id]
        while (current != null && seen.add(current.id)) {
            out.addFirst(current)
            current = current.parentId?.let(byId::get)
        }
        return out.toList()
    }
}

data class PlaceEntry(val place: Place, val itemCount: Int, val childCount: Int)

data class PlaceView(
    val current: Place?,
    val breadcrumb: List<Place>,
    val children: List<PlaceEntry>,
    /** Everything whose current location is this place or anything inside it, newest first. */
    val items: List<ItemWithCurrent>,
    val paths: Map<Long, String>,
    /** False when the requested place no longer exists. */
    val exists: Boolean,
)

object PlaceBrowser {
    fun build(places: List<Place>, items: List<ItemWithCurrent>, currentId: Long?): PlaceView {
        val current = currentId?.let { id -> places.firstOrNull { it.id == id } }
        val exists = currentId == null || current != null
        val childPlaces = places.filter { it.parentId == currentId }
            .sortedWith(compareBy({ SearchText.normalize(it.name) }, { it.id }))
        val entries = childPlaces.map { child ->
            val inside = PlaceTree.subtree(places, child.id)
            PlaceEntry(child, items.count { it.currentPlaceId in inside }, places.count { it.parentId == child.id })
        }
        val here = if (current != null) PlaceTree.subtree(places, current.id) else emptySet()
        return PlaceView(
            current = current,
            breadcrumb = if (current != null) PlaceTree.breadcrumb(places, current.id) else emptyList(),
            children = entries,
            items = items.filter { it.currentPlaceId != null && it.currentPlaceId in here }
                .sortedByDescending { it.currentAt ?: 0L },
            paths = PlaceTree.paths(places),
            exists = exists,
        )
    }
}
