package app.forjadecolor.ui.project

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import app.forjadecolor.data.ForjaDatabase
import app.forjadecolor.data.PhotoFiles
import app.forjadecolor.data.RoomRepository
import app.forjadecolor.domain.advice.AdvicePackage
import app.forjadecolor.domain.advice.AdvisorBridge
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.ui.theme.ForgeTheme
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Instant

/** Records what the screens hand to the AI and answers with what the test sets. */
class FakeBridge(var answer: String? = null, var result: SendResult = SendResult.SHARED) : AdvisorBridge {
    val sent = mutableListOf<AdvicePackage>()

    override suspend fun send(pkg: AdvicePackage): SendResult {
        sent += pkg
        return result
    }

    override fun readAnswer(): String? = answer
}

/** Picker and camera that hand back fixed bytes at once. */
class FakePhotoSources(var nextPhoto: ByteArray = byteArrayOf(9, 9, 9)) : PhotoSources {
    @Composable override fun picker(onError: (Throwable) -> Unit, onPhoto: (ByteArray) -> Unit): () -> Unit = { onPhoto(nextPhoto) }

    @Composable override fun camera(onError: (Throwable) -> Unit, onPhoto: (ByteArray) -> Unit): () -> Unit = { onPhoto(nextPhoto) }
}

/** An app over an in-memory Room database with a fixed clock, a fake bridge and fake photo inputs. */
class TestApp {
    val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, ForjaDatabase::class.java).allowMainThreadQueries().build()
    val repository = RoomRepository(db, PhotoFiles(File(context.filesDir, "photos-ui-test").apply { deleteRecursively() }))
    val bridge = FakeBridge()
    val photos = FakePhotoSources()
    val now: Instant = Instant.parse("2026-11-11T10:00:00Z")
    val container = AppContainer(context, repository = repository, advisorBridge = bridge, now = { now })

    fun project(id: String): Project? = runBlocking { repository.getProject(id) }

    fun projects(): List<Project> = runBlocking { repository.listProjects() }

    fun setContent(rule: ComposeContentTestRule, content: @Composable () -> Unit) = rule.setContent {
        CompositionLocalProvider(LocalPhotoSources provides photos) { ForgeTheme { content() } }
    }

    /** Like [setContent], but the returned tester can recreate the screen as Android does on rotation. */
    fun setRestorableContent(rule: ComposeContentTestRule, content: @Composable () -> Unit) = StateRestorationTester(rule).apply {
        setContent { CompositionLocalProvider(LocalPhotoSources provides photos) { ForgeTheme { content() } } }
    }

    /** Waits for the repository to reach a state; writes run on Room's own threads. */
    fun waitFor(rule: ComposeContentTestRule, condition: () -> Boolean) = rule.waitUntil(timeoutMillis = 5_000) {
        // Work handed back to the main thread (navigation after a write) runs only when its looper is idled.
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}

fun readFixture(name: String): String = File("../domain/src/test/resources/ai/$name").readText()
