package app.forjadecolor.data

import java.io.File
import java.util.UUID

/** Photo bytes on disk. File names are random, so nothing from a (possibly untrusted) backup becomes a path. */
class PhotoFiles(private val dir: File) {
    /** Writes the bytes under a fresh name and returns it; the file appears complete or not at all. */
    fun write(bytes: ByteArray): String {
        dir.mkdirs()
        val name = UUID.randomUUID().toString()
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(File(dir, name))) { "could not store photo" }
        return name
    }

    fun read(name: String): ByteArray? = File(dir, name).takeIf { it.isFile }?.readBytes()

    fun delete(name: String) {
        File(dir, name).delete()
    }

    fun names(): Set<String> = dir.listFiles().orEmpty().filter { it.isFile }.map { it.name }.toSet()
}
