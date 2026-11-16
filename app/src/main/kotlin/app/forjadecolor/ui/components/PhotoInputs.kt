package app.forjadecolor.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.forjadecolor.platform.fileAuthority
import app.forjadecolor.platform.resizeToJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Opens the system Photo Picker (images only; no storage permission needed) and hands back the chosen
 * photo already resized to JPEG. Returns the function that opens the picker.
 */
@Composable
fun rememberPhotoPicker(onError: (Throwable) -> Unit = {}, onPhoto: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { deliver(context, uri, onPhoto, onError) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/**
 * Takes a photo with the camera app into `cacheDir/camera/` and hands it back resized to JPEG.
 * No CAMERA permission is declared: the camera app takes the picture, so none is needed.
 * Returns the function that opens the camera.
 */
@Composable
fun rememberCameraCapture(onError: (Throwable) -> Unit = {}, onPhoto: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Saved so the result still finds its file if the activity is recreated while the camera is open.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val file = File(path)
        if (!saved) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            deliver(context, Uri.fromFile(file), onPhoto, onError)
            withContext(Dispatchers.IO) { file.delete() }
        }
    }
    return remember(launcher) {
        {
            try {
                val file = File(context.cacheDir, "camera").apply { mkdirs() }.resolve("capture-${UUID.randomUUID()}.jpg")
                pending = file.path
                launcher.launch(FileProvider.getUriForFile(context, fileAuthority(context), file))
            } catch (e: Exception) {
                pending = null
                onError(e) // no camera app, or the file could not be created
            }
        }
    }
}

private suspend fun deliver(context: Context, uri: Uri, onPhoto: (ByteArray) -> Unit, onError: (Throwable) -> Unit) {
    val result = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("photo unavailable")
            resizeToJpeg(bytes)
        }
    }
    result.fold(onPhoto, onError)
}

/**
 * A photo picked or taken but not saved yet, kept when Android recreates the screen (rotating the phone,
 * often while the camera is open). The bytes are too big for the saved-state bundle, so they are parked in
 * `cacheDir/pending-photos/` under an id that is itself saved; the file is overwritten, never piled up.
 */
@Composable
fun rememberPendingPhoto(): MutableState<ByteArray?> {
    val context = LocalContext.current
    val id = rememberSaveable { UUID.randomUUID().toString() }
    val file = remember(id) { File(File(context.cacheDir, "pending-photos"), "$id.jpg") }
    return rememberSaveable(
        saver = Saver(
            save = { state ->
                val bytes = state.value
                if (bytes == null) {
                    file.delete()
                    false
                } else {
                    runCatching { file.parentFile?.mkdirs(); file.writeBytes(bytes) }.isSuccess
                }
            },
            restore = { saved -> mutableStateOf(if (saved) runCatching { file.readBytes() }.getOrNull() else null) },
        ),
    ) { mutableStateOf(null) }
}
