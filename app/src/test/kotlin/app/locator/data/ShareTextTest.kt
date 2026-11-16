package app.locator.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextTest {
    @Test fun textOnly() = assertEquals("Keys: kitchen drawer", shareText("Keys", "kitchen drawer", null, null))

    @Test fun blankDescriptionUsesNameOnly() = assertEquals("Keys", shareText("Keys", "  ", null, null))

    @Test fun gpsAddsLink() = assertEquals(
        "Car: level -2\nhttps://www.google.com/maps?q=40.416775,-3.703790",
        shareText("Car", "level -2", 40.416775, -3.70379),
    )
}
