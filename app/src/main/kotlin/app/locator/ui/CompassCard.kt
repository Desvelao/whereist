package app.locator.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locator.R
import app.locator.location.Geo

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Arrow and distance back to a saved pin. It only takes fixes while it is on screen, and asks for the foreground
 * location permission the first time it is turned on.
 */
@Composable
fun CompassCard(vm: DetailViewModel, latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun granted() = LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var enabled by remember { mutableStateOf(granted()) }
    var denied by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        enabled = result.values.any { it }
        denied = !enabled
    }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerLow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!enabled) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (denied) R.string.compass_permission_denied else R.string.compass_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = { request.launch(LOCATION_PERMISSIONS) }) { Text(stringResource(R.string.compass_show)) }
            }
            return@Column
        }
        val state by remember(latitude, longitude) { vm.compass(latitude, longitude) }.collectAsStateWithLifecycle(initialValue = null)
        val compass = state
        if (compass == null) {
            Text(stringResource(R.string.compass_waiting), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val arrow = compass.arrowRotation
            if (arrow != null) {
                val tint = colors.primary
                Canvas(Modifier.size(56.dp)) {
                    rotate(arrow, pivot = center) {
                        val path = Path().apply {
                            moveTo(center.x, 0f)
                            lineTo(size.width * 0.85f, size.height)
                            lineTo(center.x, size.height * 0.75f)
                            lineTo(size.width * 0.15f, size.height)
                            close()
                        }
                        drawPath(path, tint)
                    }
                }
            }
            Column {
                Text(Geo.formatDistance(compass.distanceMeters), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(if (compass.weakFix) R.string.compass_weak_fix else R.string.compass_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
