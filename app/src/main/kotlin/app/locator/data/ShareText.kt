package app.locator.data

import java.util.Locale

/**
 * Plain text for the system share sheet: "Keys: kitchen drawer", plus a maps link when the record has a GPS fix.
 * The link is only text; the app itself never touches the network.
 */
fun shareText(name: String, where: String?, latitude: Double?, longitude: Double?): String {
    val place = where?.trim().orEmpty()
    val head = if (place.isEmpty()) name else "$name: $place"
    if (latitude == null || longitude == null) return head
    val link = String.format(Locale.ROOT, "https://www.google.com/maps?q=%.6f,%.6f", latitude, longitude)
    return "$head\n$link"
}
