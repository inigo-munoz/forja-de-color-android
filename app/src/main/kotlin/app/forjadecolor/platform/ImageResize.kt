package app.forjadecolor.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Size that fits `maxSide` on the longer side; never upscales. Same rounding as the PWA (JavaScript Math.round). */
fun targetSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    val scale = minOf(1.0, maxSide.toDouble() / max(width, height))
    return Math.round(width * scale).toInt() to Math.round(height * scale).toInt()
}

/**
 * Shrinks a photo so its longer side is at most `maxSide`, turned upright by its EXIF orientation
 * (camera photos are often stored sideways), re-encoded as JPEG.
 */
fun resizeToJpeg(bytes: ByteArray, maxSide: Int = 1600, quality: Int = 85): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }

    val orientation = exifOrientation(bytes)
    val upright = uprightMatrix(orientation)
    val swapsSides = orientation in SIDEWAYS
    val (uprightW, uprightH) =
        if (swapsSides) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
    val (targetW, targetH) = targetSize(uprightW, uprightH, maxSide)

    // Decode at the largest power-of-two reduction that still keeps at least the target size.
    var sample = 1
    while (max(uprightW, uprightH) / (sample * 2) >= max(targetW, targetH)) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalArgumentException("could not decode image")

    val rotated = if (upright.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, upright, true)
    val scaled = if (rotated.width == targetW && rotated.height == targetH) rotated else Bitmap.createScaledBitmap(rotated, targetW, targetH, true)

    val out = ByteArrayOutputStream()
    check(scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "could not encode image" }
    listOf(decoded, rotated, scaled).distinct().forEach { it.recycle() }
    return out.toByteArray()
}

private val SIDEWAYS = setOf(
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSVERSE,
    ExifInterface.ORIENTATION_ROTATE_270,
)

private fun exifOrientation(bytes: ByteArray): Int = try {
    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
} catch (e: Exception) {
    ExifInterface.ORIENTATION_NORMAL // no readable EXIF (PNG, stripped JPEG): keep as is
}

private fun uprightMatrix(orientation: Int) = Matrix().apply {
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
    }
}
