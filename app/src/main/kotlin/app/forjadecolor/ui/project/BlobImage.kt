package app.forjadecolor.ui.project

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import app.forjadecolor.R
import app.forjadecolor.domain.storage.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a stored photo; null when the blob is missing (a deleted file) or cannot be decoded. */
suspend fun loadImage(repository: Repository, blobId: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = repository.getBlob(blobId)?.bytes ?: return@runCatching null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** A stored photo, or a plain placeholder when its file is missing or unreadable: never a crash. */
@Composable
fun BlobImage(repository: Repository, blobId: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val image by produceState<ImageBitmap?>(null, blobId) { value = loadImage(repository, blobId) }
    val current = image
    if (current != null) {
        Image(current, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        PhotoPlaceholder(modifier)
    }
}

@Composable
fun PhotoPlaceholder(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.t8_photo_missing)
    Box(
        modifier
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description }
            .testTag("photo_placeholder"),
    )
}
