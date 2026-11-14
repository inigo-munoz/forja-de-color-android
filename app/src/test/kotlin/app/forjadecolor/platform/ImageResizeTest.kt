package app.forjadecolor.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageResizeTest {
    // Cases ported from the PWA's src/platform/image.test.ts.
    @Test
    fun `scales a 4000x3000 photo to 1600x1200`() {
        assertEquals(1600 to 1200, targetSize(4000, 3000, 1600))
    }

    @Test
    fun `scales a portrait photo by its longer side`() {
        assertEquals(1200 to 1600, targetSize(3000, 4000, 1600))
    }

    @Test
    fun `never upscales a small image`() {
        assertEquals(800 to 600, targetSize(800, 600, 1600))
    }

    @Test
    fun `rounds half up like Math round`() {
        // 999 * (1600 / 2000) = 799.2 → 799; 1001 * 0.8 = 800.8 → 801
        assertEquals(1600 to 799, targetSize(2000, 999, 1600))
        assertEquals(1600 to 801, targetSize(2000, 1001, 1600))
    }

    @Test
    fun `resizeToJpeg shrinks the long side to maxSide and re-encodes as JPEG`() {
        val source = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val out = resizeToJpeg(png, maxSide = 400)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(out, 0, out.size, bounds)
        assertEquals(400, bounds.outWidth)
        assertEquals(300, bounds.outHeight)
        assertEquals("image/jpeg", bounds.outMimeType)
    }

    @Test
    fun `resizeToJpeg turns a sideways camera photo upright`() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("camera", ".jpg").apply { deleteOnExit() }
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val out = resizeToJpeg(file.readBytes(), maxSide = 100)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(out, 0, out.size, bounds)
        assertEquals(50, bounds.outWidth)
        assertEquals(100, bounds.outHeight)
        file.delete()
    }
}
