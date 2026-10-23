package com.pillbox.ui.medicines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pillbox.domain.DoseUnit
import com.pillbox.ui.today.PillIcon
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An optional photo of the box or the pill, so a medicine can be recognised by its look. It is saved inside the app
 * (one small JPEG per medicine) and never leaves the phone. It is not part of backups. No database change: the file name is the medicine id.
 */
object MedicinePhotos {
    private const val MAX_SIDE = 1024
    private fun dir(context: Context) = File(context.filesDir, "photos").also { it.mkdirs() }
    fun file(context: Context, medicineId: Long) = File(dir(context), "med_$medicineId.jpg")
    fun has(context: Context, medicineId: Long) = file(context, medicineId).exists()

    /** A file in the cache for the camera to write to, shared through the app's FileProvider. */
    fun captureFile(context: Context) = File(File(context.cacheDir, "photos_tmp").also { it.mkdirs() }, "capture.jpg")

    /** Reads the picture at [uri], turns it upright, shrinks it to at most 1024 px on the long side and saves it. */
    suspend fun save(context: Context, medicineId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching false
            val rotation = resolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val scale = MAX_SIDE.toFloat() / maxOf(raw.width, raw.height)
            val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rotation != 0f) postRotate(rotation) }
            val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            file(context, medicineId).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        }.getOrDefault(false)
    }

    fun delete(context: Context, medicineId: Long) { file(context, medicineId).delete() }
    fun deleteAll(context: Context) { File(context.filesDir, "photos").deleteRecursively() }

    /** The saved photo at about [maxSidePx] (for thumbnails), or null when there is none. */
    fun load(context: Context, medicineId: Long, maxSidePx: Int): Bitmap? {
        val f = file(context, medicineId)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

@Composable
private fun rememberPhoto(medicineId: Long, maxSideDp: Dp, version: Int): ImageBitmap? {
    val context = LocalContext.current
    val px = (maxSideDp.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    val state = produceState<ImageBitmap?>(null, medicineId, px, version) {
        value = withContext(Dispatchers.IO) { MedicinePhotos.load(context, medicineId, px)?.asImageBitmap() }
    }
    return state.value
}

/** The medicine's photo as a small square when there is one, otherwise its shape icon. */
@Composable
fun MedicineThumb(medicineId: Long, unit: DoseUnit, size: Dp = 36.dp, tint: Color? = null, version: Int = 0) {
    val photo = rememberPhoto(medicineId, size, version)
    if (photo != null) {
        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)))
    } else PillIcon(unit, tint, size)
}

/** The photo large, for the medicine screen and the full-screen reminder. Draws nothing without a photo. */
@Composable
fun MedicinePhotoLarge(medicineId: Long, modifier: Modifier = Modifier, height: Dp = 160.dp, version: Int = 0) {
    val photo = rememberPhoto(medicineId, 640.dp, version) ?: return
    Box(modifier.fillMaxWidth().height(height)) {
        Image(photo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp)))
    }
}
