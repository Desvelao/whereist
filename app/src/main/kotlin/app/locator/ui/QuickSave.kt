package app.locator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.locator.data.UnknownItemException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.locator.R
import app.locator.data.ItemRepository
import app.locator.data.PhotoStore
import app.locator.location.FixQuality
import app.locator.location.LocationProvider
import app.locator.quick.QuickItemStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface QuickState {
    data object Loading : QuickState
    data object NoItem : QuickState
    data object PermissionDenied : QuickState
    data class Fixing(val itemName: String) : QuickState
    data class NoFix(val itemName: String) : QuickState
    data class Saved(val bar: SavedBar) : QuickState
}

/** Saves a GPS fix for the quick-save item. Runs in a foreground screen, so no background location is needed. */
class QuickSaveViewModel(
    private val repo: ItemRepository,
    private val location: LocationProvider,
    private val quick: QuickItemStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow<QuickState>(QuickState.Loading)
    val state: StateFlow<QuickState> = _state
    private var started = false

    fun run() {
        if (started) return
        started = true
        viewModelScope.launch {
            val item = quick.current()
            if (item == null) {
                _state.value = QuickState.NoItem
                return@launch
            }
            _state.value = QuickState.Fixing(item.name)
            val fix = location.currentFix()
            if (fix == null) {
                _state.value = QuickState.NoFix(item.name)
                started = false
                return@launch
            }
            val id = try {
                repo.saveGps(item.id, fix.latitude, fix.longitude, fix.accuracyMeters)
            } catch (_: UnknownItemException) {
                _state.value = QuickState.NoItem
                return@launch
            }
            val poor = if (FixQuality.isPoor(fix.accuracyMeters)) fix.accuracyMeters.toInt() else null
            _state.value = QuickState.Saved(
                SavedBar(item.id, item.name, id, poor, goodAccuracyMeters = if (poor == null) fix.accuracyMeters.toInt() else null),
            )
        }
    }

    fun permissionDenied() {
        _state.value = QuickState.PermissionDenied
    }

    fun retry() {
        _state.value = QuickState.Loading
        started = false
        run()
    }

    fun addNote(recordId: Long, note: String) {
        viewModelScope.launch { repo.setNote(recordId, note) }
    }

    fun attachPhoto(recordId: Long, path: String) {
        viewModelScope.launch { repo.attachPhoto(recordId, path) }
    }

    fun setReminder(recordId: Long, minutes: Int) {
        viewModelScope.launch { repo.setReminder(recordId, clock() + minutes * 60_000L) }
    }

    fun undo(recordId: Long) {
        viewModelScope.launch { repo.undoRecord(recordId) }
    }
}

@Composable
fun QuickSaveScreen(vm: QuickSaveViewModel, photos: PhotoStore, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) vm.run() else vm.permissionDenied()
    }
    LaunchedEffect(Unit) {
        val hasLocation = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (hasLocation) vm.run() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    var interacted by remember { mutableStateOf(false) }
    val saved = state as? QuickState.Saved
    // A good fix needs nothing more: close by itself unless the user starts adding details.
    val autoClose = saved != null && saved.bar.poorFixMeters == null && !interacted
    val progress = remember { Animatable(1f) }
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(saved?.bar?.recordId, autoClose) {
        if (autoClose) {
            progress.snapTo(1f)
            progress.animateTo(0f, tween(AUTO_CLOSE_MS.toInt(), easing = LinearEasing))
            close()
        }
    }

    Box(Modifier.fillMaxSize().clickable(onClick = onClose)) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(enabled = false) {}) {
            when (val s = state) {
                is QuickState.Saved -> SavedPanel(
                    saved = s.bar,
                    photos = photos,
                    showCountdown = autoClose,
                    progress = { progress.value },
                    vm = vm,
                    onClose = onClose,
                    onInteract = { interacted = true },
                )
                else -> StatusPanel(s, onRetry = vm::retry, onClose = onClose, onOpenApp = onOpenApp)
            }
        }
    }
}

@Composable
private fun PanelSurface(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SavedPanel(
    saved: SavedBar,
    photos: PhotoStore,
    showCountdown: Boolean,
    progress: () -> Float,
    vm: QuickSaveViewModel,
    onClose: () -> Unit,
    onInteract: () -> Unit,
) {
    val context = LocalContext.current
    SavedActionsHost(
        saved = saved,
        photos = photos,
        onPhoto = vm::attachPhoto,
        onNote = vm::addNote,
        onReminder = { id, minutes ->
            vm.setReminder(id, minutes)
            Toast.makeText(context, R.string.reminder_set, Toast.LENGTH_SHORT).show()
        },
        onMessage = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
        onInteract = onInteract,
    ) { actions ->
        PanelSurface {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SaveBadge(warning = saved.poorFixMeters != null, size = 52.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.quick_saved_title, saved.itemName), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = saved.poorFixMeters?.let { stringResource(R.string.bar_poor_fix, it) }
                            ?: saved.goodAccuracyMeters?.let { stringResource(R.string.quick_saved_good, it) }
                            ?: stringResource(R.string.quick_saved_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                }
            }
            if (showCountdown) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillOutlinedButton(onClick = actions.photo, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_add_photo), maxLines = 1) }
                PillOutlinedButton(onClick = actions.note, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_add_note), maxLines = 1) }
                PillOutlinedButton(onClick = actions.reminder, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_reminder), maxLines = 1) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = { vm.undo(saved.recordId); onClose() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) { Text(stringResource(R.string.action_undo_short)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

@Composable
private fun StatusPanel(state: QuickState, onRetry: () -> Unit, onClose: () -> Unit, onOpenApp: () -> Unit) {
    PanelSurface {
        when (state) {
            QuickState.Loading -> Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            is QuickState.Fixing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.quick_saving, state.itemName), style = MaterialTheme.typography.titleLarge)
            }
            is QuickState.NoFix -> {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SaveBadge(warning = true, size = 52.dp)
                    Text(stringResource(R.string.here_no_fix), style = MaterialTheme.typography.bodyLarge)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.action_dismiss)) }
                    PillButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
            QuickState.NoItem -> {
                Text(stringResource(R.string.quick_no_item), style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.action_dismiss)) }
                    PillButton(onClick = onOpenApp) { Text(stringResource(R.string.action_open_app)) }
                }
            }
            QuickState.PermissionDenied -> {
                Text(stringResource(R.string.here_permission_denied), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_dismiss)) }
            }
            is QuickState.Saved -> Unit
        }
    }
}

private const val AUTO_CLOSE_MS = 4_000L
