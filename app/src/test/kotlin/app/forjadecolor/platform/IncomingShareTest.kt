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
        assertEquals(IncomingShare.Text("{\"kind\":\"recipe\"}"), parseIncomingShare(intent))
    }

    @Test
    fun `shared image becomes the painted image`() {
        val uri = Uri.parse("content://com.example.gallery/image/1")
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        assertEquals(IncomingShare.Image(uri), parseIncomingShare(intent))
    }

    @Test
    fun `an unrelated intent is ignored`() {
        assertNull(parseIncomingShare(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
        assertNull(parseIncomingShare(Intent(Intent.ACTION_SEND).setType("text/plain")))
        assertNull(parseIncomingShare(null))
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
