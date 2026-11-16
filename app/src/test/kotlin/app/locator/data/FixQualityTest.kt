package app.locator.data

import app.locator.location.FixQuality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixQualityTest {
    @Test fun thirtyMetresIsStillGood() = assertFalse(FixQuality.isPoor(30f))
    @Test fun aboveThirtyMetresIsPoor() = assertTrue(FixQuality.isPoor(30.1f))
    @Test fun unknownAccuracyIsPoor() = assertTrue(FixQuality.isPoor(Float.MAX_VALUE))
}
