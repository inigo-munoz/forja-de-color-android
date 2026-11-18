package app.forjadecolor.domain.projects

import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.isoString
import kotlinx.serialization.SerialName
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ProgressPhoto(
    val id: String,
    /** ISO date */
    val takenAt: String,
    /** Blob stored by the Repository */
    val blobId: String,
)

@Serializable
enum class ProjectStatus {
    @SerialName("in-progress") IN_PROGRESS,
    @SerialName("done") DONE,
}

@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: String,
    val status: ProjectStatus,
    /** The main photo: list, header, Before/after and "Paint it with ChatGPT". */
    val initialPhotoBlobId: String? = null,
    val targetImageBlobId: String? = null,
    val notes: String,
    val progress: List<ProgressPhoto>,
    val advice: List<AdviceEntry>,
    /** Null: my method. Left out of the JSON when null, like in the PWA. */
    val style: PaintingStyle? = null,
    /** Null: no setting for the base. */
    val baseSetting: BaseSetting? = null,
    /** Free-text theme for the base; null when there is none (left out of the JSON). */
    val baseMotif: String? = null,
    /** Up to [MAX_ANGLES] more photos of the unpainted miniature; left out of the JSON when empty. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val extraPhotoBlobIds: List<String> = emptyList(),
) {
    /** Every photo the project references, for backups and clean-up. */
    val blobIds: List<String> get() = listOfNotNull(initialPhotoBlobId) + extraPhotoBlobIds + listOfNotNull(targetImageBlobId) + progress.map { it.blobId }

    /** The main photo and its angles, in order: what the advisor sends for start and primed. */
    val photos: List<String> get() = listOfNotNull(initialPhotoBlobId) + extraPhotoBlobIds

    /** The look the prompts follow; a project saved before styles existed is painted with my method. */
    val look: Look get() = Look(style ?: PaintingStyle.MY_METHOD, baseSetting, baseMotif)
}

/** Extra angles a project keeps besides its main photo. */
const val MAX_ANGLES = 3

/** Adds an angle; ignored once the project has [MAX_ANGLES]. */
fun Project.addAngle(blobId: String): Project =
    if (extraPhotoBlobIds.size >= MAX_ANGLES) this else copy(extraPhotoBlobIds = extraPhotoBlobIds + blobId)

fun Project.removeAngle(blobId: String): Project = copy(extraPhotoBlobIds = extraPhotoBlobIds - blobId)

/** Sets the look; my method is stored as null, like older projects and the PWA. */
fun Project.setLook(look: Look): Project =
    copy(style = look.style.takeIf { it != PaintingStyle.MY_METHOD }, baseSetting = look.baseSetting, baseMotif = cleanMotif(look.baseMotif))

fun createProject(name: String, now: Instant, id: String): Project =
    Project(id, name, isoString(now), ProjectStatus.IN_PROGRESS, notes = "", progress = emptyList(), advice = emptyList())

fun Project.setNotes(notes: String): Project = copy(notes = notes)

fun Project.addProgressPhoto(photo: ProgressPhoto): Project = copy(progress = (progress + photo).sortedBy { it.takenAt })

fun Project.addAdvice(entry: AdviceEntry): Project = copy(advice = advice + entry)

fun Project.markDone(): Project = copy(status = ProjectStatus.DONE)

fun Project.reopen(): Project = copy(status = ProjectStatus.IN_PROGRESS)

/** Ticks a recipe zone off (or back on); keeps doneZones sorted and unique. */
fun Project.setZoneDone(entryId: String, zone: Int, done: Boolean): Project = copy(
    advice = advice.map { e ->
        if (e.id != entryId) e else e.copy(doneZones = (if (done) e.doneZones + zone else e.doneZones - zone).distinct().sorted())
    },
)
