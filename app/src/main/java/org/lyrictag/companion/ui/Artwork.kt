package org.lyrictag.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage

/** Album-art thumb with crossfade; note glyph placeholder while loading. */
@Composable
fun TrackThumb(url: String?, size: Dp, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) {
        Box(
            modifier.then(
                Modifier.size(size).clip(RoundedCornerShape(size / 5))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "♪", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        return
    }
    AsyncImage(
        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(url).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.then(
            Modifier.size(size).clip(RoundedCornerShape(size / 5))
        ),
    )
}
