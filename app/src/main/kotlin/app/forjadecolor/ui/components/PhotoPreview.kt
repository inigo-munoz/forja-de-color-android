package app.forjadecolor.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.forjadecolor.R

/**
 * The photo just taken or chosen, shown before it is used, so the user sees what they picked.
 * Falls back to the "Photo added" text when the bytes cannot be decoded.
 */
@Composable
fun PhotoPreview(bytes: ByteArray, tag: String) {
    val image = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    val description = stringResource(R.string.t8_photo_ready)
    if (image != null) {
        Image(
            image,
            contentDescription = description,
            modifier = Modifier.size(160.dp).clip(RoundedCornerShape(8.dp)).testTag(tag),
            contentScale = ContentScale.Crop,
        )
    } else {
        Text(description, modifier = Modifier.testTag(tag))
    }
}
