package app.locator.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.locator.data.Item
import app.locator.data.ItemWithCurrent
import app.locator.data.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LastSavedTextTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val places = listOf(Place(1, "Home", null), Place(2, "Garage", 1))

    private fun row(description: String? = null, placeId: Long? = null, lat: Double? = null, accuracy: Float? = null, recordId: Long? = 1) =
        ItemWithCurrent(
            item = Item(id = 1, name = "Car", emoji = null, captureGps = true, pinned = false, archived = false, searchText = "car", createdAt = 1),
            currentRecordId = recordId, currentDescription = description, currentNote = null, currentLatitude = lat,
            currentLongitude = lat?.let { 2.0 }, currentAccuracy = accuracy, currentPhoto = null, currentRemindAt = null,
            currentPlaceId = placeId, currentAt = if (recordId == null) null else System.currentTimeMillis(),
        )

    @Test fun noItemOrNoRecordGivesNothing() {
        assertNull(lastSavedText(context, null, places))
        assertNull(lastSavedText(context, row(recordId = null), places))
    }

    @Test fun textAndPlaceAreJoined() {
        val text = lastSavedText(context, row(description = "level -2", placeId = 2), places)!!
        assertEquals(true, text.startsWith("Last: ") && text.contains("level -2") && text.contains("Home › Garage"))
    }

    @Test fun aGpsOnlySaveShowsItsAccuracy() {
        val text = lastSavedText(context, row(lat = 1.0, accuracy = 8f), places)!!
        assertEquals(true, text.contains("±8"))
    }
}
