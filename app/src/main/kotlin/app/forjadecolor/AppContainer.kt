package app.forjadecolor

import android.content.Context
import app.forjadecolor.data.ForjaDatabase
import app.forjadecolor.data.PhotoFiles
import app.forjadecolor.data.RoomRepository
import app.forjadecolor.domain.advice.AdvisorBridge
import app.forjadecolor.domain.storage.Repository
import app.forjadecolor.platform.AndroidShareBridge
import app.forjadecolor.platform.IncomingShares
import java.io.File
import java.time.Instant

/** Composition root wiring Room, the photo files and the share bridge; each can be overridden in tests. */
class AppContainer(
    context: Context,
    repository: Repository? = null,
    advisorBridge: AdvisorBridge? = null,
    /** Current time; screens use it for `createdAt`, `takenAt`, `receivedAt` and the backup reminder. */
    val now: () -> Instant = Instant::now,
) {
    private val appContext = context.applicationContext
    private val repositoryOverride = repository

    private val roomRepository: RoomRepository by lazy {
        RoomRepository(ForjaDatabase.create(appContext), PhotoFiles(File(appContext.filesDir, "photos")))
    }

    val repository: Repository by lazy { repositoryOverride ?: roomRepository }
    val advisorBridge: AdvisorBridge by lazy { advisorBridge ?: AndroidShareBridge(appContext, this.repository) }

    /** Text or images other apps shared into Forja de Color, waiting for a screen to route them. */
    val incomingShares = IncomingShares()

    /** Cleans photo files left by a crash; only the Room-backed store has files to clean. */
    suspend fun removeOrphanPhotos() {
        if (repositoryOverride == null) roomRepository.removeOrphanPhotos()
    }
}
