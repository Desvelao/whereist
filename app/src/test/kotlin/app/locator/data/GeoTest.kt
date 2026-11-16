package app.locator.data

import app.locator.location.Geo
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test fun oneDegreeOfLatitudeIsAbout111Km() =
        assertEquals(111_195.0, Geo.distanceMeters(0.0, 0.0, 1.0, 0.0), 100.0)

    @Test fun samePointIsZero() = assertEquals(0.0, Geo.distanceMeters(40.0, -3.0, 40.0, -3.0), 0.001)

    @Test fun bearingsPointToTheCompassPoints() {
        assertEquals(0.0, Geo.bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.01)
        assertEquals(90.0, Geo.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.01)
        assertEquals(180.0, Geo.bearingDegrees(0.0, 0.0, -1.0, 0.0), 0.01)
        assertEquals(270.0, Geo.bearingDegrees(0.0, 0.0, 0.0, -1.0), 0.01)
    }

    @Test fun relativeBearingWrapsAround() {
        assertEquals(350.0, Geo.relativeBearing(0.0, 10.0), 0.001)
        assertEquals(20.0, Geo.relativeBearing(10.0, 350.0), 0.001)
    }

    @Test fun formatsDistances() {
        assertEquals("12 m", Geo.formatDistance(12.4))
        assertEquals("1.3 km", Geo.formatDistance(1_300.0))
        assertEquals("27 km", Geo.formatDistance(27_000.0))
    }
}
