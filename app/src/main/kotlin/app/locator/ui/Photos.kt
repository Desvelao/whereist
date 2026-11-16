package app.locator.ui

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.locator.R
import app.locator.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Returns a function that opens the system camera for a record. The system camera app does the work, so the
 * app needs no CAMERA permission. The file only counts once the camera reports success.
 */
@Composable
fun rememberPhotoCapture(photos: PhotoStore, onTaken: (recordId: Long, path: String) -> Unit): (Long) -> Unit {
    val context = LocalContext.current
    val taken by rememberUpdatedState(onTaken)
    var pendingRecord by rememberSaveable { mutableLongStateOf(-1L) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPath
        if (path != null) {
            val file = photos.file(path)
            if (success && pendingRecord >= 0 && file.length() > 0) taken(pendingRecord, path) else photos.delete(path)
        }
        pendingPath = null
    }
    return { recordId ->
        photos.prepare()
        val path = photos.newPath()
        val file = photos.file(path)
        file.createNewFile()
        pendingRecord = recordId
        pendingPath = path
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        try {
            launcher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            photos.delete(path)
            pendingPath = null
        }
    }
}

/** Decodes a stored photo off the main thread, scaled down, with the camera's rotation applied. */
@Composable
fun PhotoImage(photos: PhotoStore, path: String, modifier: Modifier = Modifier, maxEdge: Int = 1024) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { loadPhoto(photos, path, maxEdge) }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = stringResource(R.string.photo_description),
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

private fun loadPhoto(photos: PhotoStore, path: String, maxEdge: Int): Bitmap? {
    val file = try {
        photos.file(path)
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxEdge || bounds.outHeight / (sample * 2) >= maxEdge) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return decoded
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
}
