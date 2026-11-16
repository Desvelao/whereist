package app.locator.location

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure geometry for the compass: all angles in degrees, distances in metres. */
object Geo {
    private const val EARTH_RADIUS = 6_371_000.0

    /** Great-circle (haversine) distance between two points. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing from point 1 to point 2, clockwise from true north, in [0, 360). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    /** Angle to rotate an arrow that points up so it points at the target, given the phone's [heading]. */
    fun relativeBearing(bearing: Double, heading: Double): Double = normalize(bearing - heading)

    fun normalize(degrees: Double): Double = ((degrees % 360) + 360) % 360

    /** "12 m", "480 m", "1.3 km", "27 km". */
    fun formatDistance(meters: Double): String = when {
        meters < 1_000 -> "${meters.roundToInt()} m"
        meters < 10_000 -> String.format(Locale.ROOT, "%.1f km", meters / 1_000)
        else -> "${(meters / 1_000).roundToInt()} km"
    }
}
