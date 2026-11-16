package app.forjadecolor.platform

import android.content.Intent
import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingShareTest {
    @Test
    fun `shared text becomes the AI answer`() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "{\"kind\":\"recipe\"}")
        assertEquals(IncomingShare.Text("{\"kind\":\"recipe\"}"), parseIncomingShare(intent, OWN))
    }

    @Test
    fun `shared image becomes the painted image`() {
        val uri = Uri.parse("content://com.example.gallery/image/1")
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        assertEquals(IncomingShare.Image(uri), parseIncomingShare(intent, OWN))
    }

    @Test
    fun `a shared file path is refused, so no app can make Forja read its own private files`() {
        val uri = Uri.parse("file:///data/data/app.forjadecolor/databases/forja.db")
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        assertNull(parseIncomingShare(intent, OWN))
    }

    @Test
    fun `an image from the app's own file provider is refused`() {
        val uri = Uri.parse("content://$OWN/camera/capture.jpg")
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
        assertNull(parseIncomingShare(intent, OWN))
    }

    @Test
    fun `an image from the app's own file provider is refused even behind a user id prefix`() {
        val uri = Uri.parse("content://0@$OWN/camera/capture.jpg")
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
        assertNull(parseIncomingShare(intent, OWN))
    }

    @Test
    fun `an unrelated intent is ignored`() {
        assertNull(parseIncomingShare(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), OWN))
        assertNull(parseIncomingShare(Intent(Intent.ACTION_SEND).setType("text/plain"), OWN))
        assertNull(parseIncomingShare(null, OWN))
    }

    @Test
    fun `consume hands the share over once`() {
        val shares = IncomingShares()
        shares.offer(IncomingShare.Text("answer"))
        assertEquals(IncomingShare.Text("answer"), shares.current.value)
        assertEquals(IncomingShare.Text("answer"), shares.consume())
        assertNull(shares.current.value)
        assertNull(shares.consume())
    }
}

private const val OWN = "app.forjadecolor.files"
