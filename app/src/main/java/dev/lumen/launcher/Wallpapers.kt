package dev.lumen.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Longest side, in px, that an imported wallpaper is scaled down to (enough for a 1080x2340 screen). */
private const val IMPORT_MAX_SIDE = 2600

/** Decodes [file] scaled so its longest side is at most about [maxSide] px. Null if it can't be read. */
fun decodeSampled(file: File, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Loads [file] off the main thread; null until it is ready or if there is no file. */
@Composable
fun rememberImage(file: File?, maxSide: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file, maxSide) {
        value = file?.let { f -> withContext(Dispatchers.IO) { decodeSampled(f, maxSide)?.asImageBitmap() } }
    }
    return image
}

/** Copies the image at [uri] into [dir] as a downscaled JPEG. Returns the new file, or null on failure. */
fun importWallpaper(contentResolver: android.content.ContentResolver, uri: Uri, dir: File, name: String): File? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= IMPORT_MAX_SIDE) sample *= 2
    val bitmap = contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val out = File(dir, "$name.jpg")
    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    bitmap.recycle()
    out
}.getOrNull()
