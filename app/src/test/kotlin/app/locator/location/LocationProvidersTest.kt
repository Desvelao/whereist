package app.locator.location

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationProvidersTest {
    @Test fun fineLocationUsesGpsAndNetwork() =
        assertEquals(listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER), LocationProviders.usable(fine = true, coarse = true))

    @Test fun coarseOnlySkipsGpsInsteadOfWaitingForIt() =
        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), LocationProviders.usable(fine = false, coarse = true))

    @Test fun noPermissionMeansNoProvider() =
        assertEquals(emptyList<String>(), LocationProviders.usable(fine = false, coarse = false))
}
