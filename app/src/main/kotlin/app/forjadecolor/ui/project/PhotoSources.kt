package app.forjadecolor.ui.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.forjadecolor.ui.components.rememberCameraCapture
import app.forjadecolor.ui.components.rememberPhotoPicker

/**
 * Where screens get photos from: the system Photo Picker and the camera, both handing back resized JPEG bytes.
 * Tests provide a fake through [LocalPhotoSources], since neither system activity runs under Robolectric.
 */
interface PhotoSources {
    @Composable fun picker(onError: (Throwable) -> Unit, onPhoto: (ByteArray) -> Unit): () -> Unit

    @Composable fun camera(onError: (Throwable) -> Unit, onPhoto: (ByteArray) -> Unit): () -> Unit
}

object SystemPhotoSources : PhotoSources {
    @Composable override fun picker(onError: (Throwable) -> Unit, onPhoto: (ByteArray) -> Unit) = rememberPhotoPicker(onError, onPhoto)

    @Composable override fun camera(onError: (Throwable) -> Unit, onPhoto: (ByteArray) -> Unit) = rememberCameraCapture(onError, onPhoto)
}

val LocalPhotoSources = staticCompositionLocalOf<PhotoSources> { SystemPhotoSources }
