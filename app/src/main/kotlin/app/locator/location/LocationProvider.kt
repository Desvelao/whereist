package app.locator.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Which platform providers can answer with the permissions granted. */
object LocationProviders {
    /** GPS needs the fine permission; the network provider works with either. */
    fun usable(fine: Boolean, coarse: Boolean): List<String> = buildList {
        if (fine) add(LocationManager.GPS_PROVIDER)
        if (fine || coarse) add(LocationManager.NETWORK_PROVIDER)
    }
}

data class Fix(val latitude: Double, val longitude: Double, val accuracyMeters: Float)

/** Hides the platform behind an interface so ViewModels can be tested with a fake. */
fun interface LocationProvider {
    /** One current fix, or null when none could be obtained (no permission, provider off, timeout). */
    suspend fun currentFix(): Fix?
}

object FixQuality {
    /** Above this accuracy (metres) the pin is not trustworthy and the app asks for a photo or note. */
    const val POOR_ACCURACY_METERS = 30f

    fun isPoor(accuracyMeters: Float): Boolean = accuracyMeters > POOR_ACCURACY_METERS
}

/** Platform LocationManager only: no Play Services, no API keys. */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * GPS and network are asked at the same time. A good fix is returned at once; a weak one (typical indoors,
     * where GPS never answers) waits a few seconds for the other provider to do better, then the best is used.
     */
    override suspend fun currentFix(): Fix? = coroutineScope {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val providers = LocationProviders.usable(fine, coarse).filter { enabled(it) }
        val results = Channel<Fix?>(providers.size)
        providers.forEach { provider ->
            launch {
                val fix = withTimeoutOrNull(TIMEOUT_MS) { request(provider) }?.let {
                    Fix(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE)
                }
                results.send(fix)
            }
        }
        var best: Fix? = null
        var received = 0
        while (received < providers.size) {
            val next = if (best == null) results.receive() else withTimeoutOrNull(WEAK_GRACE_MS) { results.receive() } ?: break
            received++
            if (next != null && (best == null || next.accuracyMeters < best.accuracyMeters)) best = next
            if (best != null && !FixQuality.isPoor(best.accuracyMeters)) break
        }
        coroutineContext.job.cancelChildren()
        best
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun enabled(provider: String) = try {
        manager.isProviderEnabled(provider)
    } catch (_: IllegalArgumentException) {
        false
    }

    @SuppressLint("MissingPermission") // the caller asks for the permission first; a missing one is caught below
    private suspend fun request(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, Runnable::run) { location ->
                continuation.resume(location)
            }
        } catch (_: SecurityException) {
            continuation.resume(null)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L

        /** After a weak first fix, how long to wait for the other provider to give a better one. */
        const val WEAK_GRACE_MS = 5_000L
    }
}
