package app.forjadecolor.domain.storage

import app.forjadecolor.domain.projects.BaseSetting
import app.forjadecolor.domain.projects.PaintingStyle
import kotlinx.serialization.json.jsonPrimitive

import app.forjadecolor.domain.ForjaJson
import app.forjadecolor.domain.isoString
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.projects.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.io.encoding.Base64

// The backup file is the contract shared with the PWA (src/storage/backup.ts): same shape, same
// validation messages. Pinned by BackupTest against a backup exported by the PWA's own code.

@Serializable
data class BackupBlob(val type: String, val base64: String)

@Serializable
data class BackupFile(
    val app: String = APP,
    val version: Int = 1,
    val exportedAt: String,
    val inventory: Inventory,
    val projects: List<Project>,
    val blobs: Map<String, BackupBlob>,
) {
    companion object {
        const val APP = "forja-de-color"
    }
}

sealed interface ImportResult {
    data object Ok : ImportResult
    data class Failed(val error: String) : ImportResult
}

private const val DAY_MS = 86_400_000L

suspend fun exportAll(repo: Repository, now: Instant): BackupFile {
    val projects = repo.listProjects()
    val blobs = linkedMapOf<String, BackupBlob>()
    for (id in projects.flatMap { it.blobIds }.distinct()) {
        val blob = repo.getBlob(id) ?: continue
        blobs[id] = BackupBlob(blob.type, Base64.encode(blob.bytes))
    }
    val exportedAt = isoString(now)
    val file = BackupFile(exportedAt = exportedAt, inventory = repo.getInventory(), projects = projects, blobs = blobs)
    repo.setMeta(MetaKey.LAST_EXPORT_AT, exportedAt)
    return file
}

fun encodeBackup(file: BackupFile): String = ForjaJson.encodeToString(BackupFile.serializer(), file)

fun backupFileName(file: BackupFile): String = "forja-de-color-${file.exportedAt.take(10)}.json"

/** The file's JSON, or null when it is not JSON at all (validation then says "not a backup file"). */
fun parseBackupText(text: String): JsonElement? = try {
    ForjaJson.parseToJsonElement(text)
} catch (e: IllegalArgumentException) {
    null
}

private fun JsonObject.strings(vararg keys: String) = keys.all { (this[it] as? JsonPrimitive)?.isString == true }

/** Optional doneZones on an advice entry: distinct integer indices into its recipe's zones. */
private fun doneZonesProblem(project: JsonObject, projectIndex: Int): String? {
    (project["advice"] as JsonArray).forEachIndexed { i, entry ->
        if (entry !is JsonObject || "doneZones" !in entry) return@forEachIndexed
        val zones = ((entry["response"] as? JsonObject)?.get("zones") as? JsonArray)?.size ?: 0
        val done = entry["doneZones"] as? JsonArray
        val indices = done?.map { z -> (z as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull }
        val valid = indices != null && indices.none { it == null || it < 0 || it >= zones } && indices.toSet().size == indices.size
        if (!valid) return "project #${projectIndex + 1}, advice #${i + 1} has invalid done zones: each must be a different zone index below $zones"
    }
    return null
}

/** Why a file cannot be restored, or null when it is a valid backup. */
// Wire names of the optional project look, as the PWA writes them.
private val STYLE_WIRES = PaintingStyle.entries.map { ForjaJson.encodeToJsonElement(PaintingStyle.serializer(), it).jsonPrimitive.content }
private val BASE_WIRES = BaseSetting.entries.map { ForjaJson.encodeToJsonElement(BaseSetting.serializer(), it).jsonPrimitive.content }

/** A JSON string's text, or null for anything else. */
private fun wire(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it.isString }?.content

fun validateBackup(file: JsonElement?): String? {
    if (file !is JsonObject) return "not a backup file"
    if ((file["app"] as? JsonPrimitive)?.takeIf { it.isString }?.content != BackupFile.APP) return "this file is not a Forja de Color backup"
    val version = file["version"] as? JsonPrimitive
    if (version == null || version.isString || version.doubleOrNull != 1.0) return "unsupported backup version: ${version?.content ?: "undefined"}"
    val inv = file["inventory"] as? JsonObject
    val paints = inv?.get("paints") as? JsonArray
    val brushes = inv?.get("brushes") as? JsonArray
    if (paints == null || brushes == null) return "backup has no valid inventory"
    val badPaint = paints.indexOfFirst { !(it is JsonObject && it.strings("brand", "code", "name", "range", "type", "hex")) }
    if (badPaint >= 0) return "paint #${badPaint + 1} needs brand, code, name, range, type and hex"
    val badBrush = brushes.indexOfFirst { !(it is JsonObject && it.strings("code", "name")) }
    if (badBrush >= 0) return "brush #${badBrush + 1} needs code and name"
    val projects = file["projects"] as? JsonArray ?: return "backup has no valid projects"
    val badProject = projects.indexOfFirst {
        !(it is JsonObject && it.strings("id", "name", "createdAt", "status", "notes") && it["progress"] is JsonArray && it["advice"] is JsonArray)
    }
    if (badProject >= 0) return "project #${badProject + 1} is incomplete"
    val badLook = projects.indexOfFirst { p ->
        val o = p as JsonObject
        ("style" in o && wire(o["style"]) !in STYLE_WIRES) || ("baseSetting" in o && wire(o["baseSetting"]) !in BASE_WIRES)
    }
    if (badLook >= 0) return "project #${badLook + 1} has an unknown painting style or base setting"
    projects.forEachIndexed { i, p -> doneZonesProblem(p as JsonObject, i)?.let { return it } }
    val blobs = file["blobs"] as? JsonObject ?: return "backup has no valid photos"
    if (blobs.values.any { !(it is JsonObject && it.strings("type", "base64")) }) return "backup has a damaged photo"
    return null
}

/** Validates the whole file first; only then replaces everything. */
suspend fun importAll(repo: Repository, file: JsonElement?): ImportResult {
    validateBackup(file)?.let { return ImportResult.Failed(it) }
    val backup = try {
        ForjaJson.decodeFromJsonElement(BackupFile.serializer(), file!!)
    } catch (e: IllegalArgumentException) {
        return ImportResult.Failed("could not read the backup: ${e.message}")
    }
    val decoded = try {
        backup.blobs.map { (id, b) -> id to StoredBlob(b.type, Base64.decode(b.base64)) }
    } catch (e: IllegalArgumentException) {
        return ImportResult.Failed("backup has a damaged photo")
    }
    return try {
        repo.replaceAll(backup.inventory, backup.projects, decoded)
        ImportResult.Ok
    } catch (e: Exception) {
        ImportResult.Failed("could not restore the backup: ${e.message}")
    }
}

fun shouldRemindBackup(lastExportAt: String?, now: Instant, everyDays: Int = 7): Boolean {
    if (lastExportAt == null) return true
    val last = try {
        Instant.parse(lastExportAt)
    } catch (e: DateTimeParseException) {
        return false // JavaScript compares against NaN here, which never reminds.
    }
    return now.toEpochMilli() - last.toEpochMilli() >= everyDays * DAY_MS
}
