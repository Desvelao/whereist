package app.locator.data

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class SearchTextTest {
    @Test fun stripsAccentsAndCase() = assertEquals("cajon", SearchText.normalize(" Cajón "))
    @Test fun keepsPlainText() = assertEquals("home keys", SearchText.normalize("Home Keys"))

    private fun row(note: String? = null, usualText: String? = null) = ItemWithCurrent(
        item = Item(id = 1, name = "Passport", emoji = null, captureGps = false, pinned = false, archived = false,
            searchText = "passport", createdAt = 1, usualText = usualText),
        currentRecordId = 1, currentDescription = "Drawer", currentNote = note, currentLatitude = null,
        currentLongitude = null, currentAccuracy = null, currentPhoto = null, currentRemindAt = null,
        currentPlaceId = null, currentAt = 1,
    )

    @Test fun matchesTheCurrentNote() = assertTrue(row(note = "Behind the blue box").matches("BLUE"))
    @Test fun matchesTheUsualSpot() = assertTrue(row(usualText = "Cajón del salón").matches("cajon"))
    @Test fun stillRejectsUnrelatedText() = assertFalse(row(note = "blue box").matches("green"))
}
