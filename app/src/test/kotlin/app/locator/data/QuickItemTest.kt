package app.locator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickItemTest {
    private fun item(id: Long) =
        Item(id = id, name = "i$id", emoji = null, captureGps = true, pinned = false, archived = false, searchText = "i$id", createdAt = 0)

    @Test fun usesTheChosenItemWhenItIsStillACandidate() = assertEquals(2L, QuickItem.resolve(2, listOf(item(1), item(2)))!!.id)
    @Test fun fallsBackToTheFirstWhenTheChoiceIsGone() = assertEquals(1L, QuickItem.resolve(9, listOf(item(1), item(2)))!!.id)
    @Test fun fallsBackToTheFirstWhenNothingWasChosen() = assertEquals(1L, QuickItem.resolve(null, listOf(item(1)))!!.id)
    @Test fun noCandidatesMeansNoItem() = assertNull(QuickItem.resolve(1, emptyList()))
}
