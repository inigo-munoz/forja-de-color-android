package app.forjadecolor.platform

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Largest photo read from another app or the picker; camera photos and AI images are far smaller. */
const val MAX_PHOTO_BYTES: Long = 40L * 1024 * 1024

/** Largest backup file read; a backup holds every photo, but already shrunk to 1600 px. */
const val MAX_BACKUP_BYTES: Long = 100L * 1024 * 1024

/** A file from outside the app is bigger than it is allowed to be. */
class FileTooLargeException(limit: Long) : IOException("file larger than $limit bytes")

/**
 * Reads the whole stream, but stops with [FileTooLargeException] as soon as it passes [maxBytes]:
 * a huge or endless file from another app must not fill the memory and crash the app.
 */
fun InputStream.readAtMost(maxBytes: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) return out.toByteArray()
        total += n
        if (total > maxBytes) throw FileTooLargeException(maxBytes)
        out.write(buffer, 0, n)
    }
}
