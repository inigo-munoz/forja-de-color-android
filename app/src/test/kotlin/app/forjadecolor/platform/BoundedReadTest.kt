package app.forjadecolor.platform

import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BoundedReadTest {
    @Test
    fun `reads a stream up to the limit`() {
        assertContentEquals(byteArrayOf(1, 2, 3), ByteArrayInputStream(byteArrayOf(1, 2, 3)).readAtMost(3))
    }

    @Test
    fun `refuses a stream over the limit instead of filling memory`() {
        assertFailsWith<FileTooLargeException> { ByteArrayInputStream(ByteArray(4)).readAtMost(3) }
    }
}
