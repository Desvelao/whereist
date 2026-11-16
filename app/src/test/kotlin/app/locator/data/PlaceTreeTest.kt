package app.locator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTreeTest {
    private val home = Place(1, "Home", null)
    private val garage = Place(2, "Garage", 1)
    private val shelf = Place(3, "Shelf B", 2)
    private val kitchen = Place(4, "kitchen", 1)
    private val office = Place(5, "Office", null)
    private val all = listOf(shelf, office, kitchen, garage, home)

    private fun item(id: Long, placeId: Long?, at: Long = id) = ItemWithCurrent(
        item = Item(id = id, name = "i$id", emoji = null, captureGps = false, pinned = false, archived = false, searchText = "i$id", createdAt = 0),
        currentRecordId = if (placeId != null) id else null,
        currentDescription = null, currentNote = null, currentLatitude = null, currentLongitude = null,
        currentAccuracy = null, currentPhoto = null, currentRemindAt = null, currentPlaceId = placeId, currentAt = at,
    )

    @Test
    fun whereLabelCombinesTextAndPlace() {
        assertEquals("second shelf · Home › Garage", PlaceTree.whereLabel("second shelf", "Home › Garage"))
        assertEquals("second shelf", PlaceTree.whereLabel("second shelf", null))
        assertEquals("Home › Garage", PlaceTree.whereLabel(null, "Home › Garage"))
        assertEquals(null, PlaceTree.whereLabel(null, null))
    }

    @Test
    fun pathsJoinNamesFromTheRoot() {
        val paths = PlaceTree.paths(all)
        assertEquals("Home", paths[1])
        assertEquals("Home › Garage › Shelf B", paths[3])
    }

    @Test
    fun orderedIsDepthFirstBySiblingNameIgnoringCase() {
        assertEquals(
            listOf("Home" to 0, "Garage" to 1, "Shelf B" to 2, "kitchen" to 1, "Office" to 0),
            PlaceTree.ordered(all).map { it.first.name to it.second },
        )
    }

    @Test
    fun subtreeIncludesThePlaceAndEverythingInsideIt() {
        assertEquals(setOf(1L, 2L, 3L, 4L), PlaceTree.subtree(all, 1))
        assertEquals(setOf(2L, 3L), PlaceTree.subtree(all, 2))
        assertEquals(setOf(5L), PlaceTree.subtree(all, 5))
    }

    @Test
    fun breadcrumbRunsFromTheRoot() {
        assertEquals(listOf("Home", "Garage", "Shelf B"), PlaceTree.breadcrumb(all, 3).map { it.name })
    }

    @Test
    fun aLoopInTheDataDoesNotHangAnything() {
        val a = Place(1, "A", 2)
        val b = Place(2, "B", 1)
        assertTrue(PlaceTree.paths(listOf(a, b)).isNotEmpty())
        assertEquals(setOf(1L, 2L), PlaceTree.subtree(listOf(a, b), 1))
        assertEquals(2, PlaceTree.breadcrumb(listOf(a, b), 1).size)
        PlaceTree.ordered(listOf(a, b))
    }

    @Test
    fun browsingAPlaceListsEverythingStoredUnderIt() {
        val items = listOf(item(1, 3, at = 10), item(2, 2, at = 30), item(3, 4, at = 20), item(4, null), item(5, 5))
        val garageView = PlaceBrowser.build(all, items, 2)
        assertEquals(listOf(2L, 1L), garageView.items.map { it.item.id }) // newest first, shelf included
        assertEquals(listOf("Shelf B"), garageView.children.map { it.place.name })
        assertEquals(1, garageView.children.single().itemCount)

        val homeView = PlaceBrowser.build(all, items, 1)
        assertEquals(setOf(1L, 2L, 3L), homeView.items.map { it.item.id }.toSet())
        assertEquals(listOf("Garage" to 2, "kitchen" to 1), homeView.children.map { it.place.name to it.itemCount })
        assertEquals(listOf("Home"), homeView.breadcrumb.map { it.name })
    }

    @Test
    fun theTopLevelListsOnlyPlacesAndMissingPlacesAreFlagged() {
        val items = listOf(item(1, 3))
        val root = PlaceBrowser.build(all, items, null)
        assertEquals(listOf("Home", "Office"), root.children.map { it.place.name })
        assertEquals(emptyList<Long>(), root.items.map { it.item.id })
        assertTrue(root.exists)
        assertFalse(PlaceBrowser.build(all, items, 99).exists)
    }
}
