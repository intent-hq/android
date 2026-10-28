package dev.intent.app.attachments

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentTextButton as TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalAttachmentMedia = staticCompositionLocalOf<MediaSource?> { null }

@Composable
fun PhotoAttachments(store: PhotoAttachmentStore) {
    val state by store.state.collectAsState()
    Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.photos.forEach { photo ->
            androidx.compose.runtime.key(photo.key) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    photo.file?.takeIf { photo.description?.image == true }?.let { file ->
                        MediaPreview(
                            MediaReference("Selected photo", true),
                            remember(file) {
                                MediaSource {
                                    java.util.concurrent.CompletableFuture.completedFuture(file)
                                }
                            },
                            false,
                            showName = false
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(photo.description?.name ?: "Attachment", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(photoStatus(photo))
                        if (photo.error !=
                            null
                        ) {
                            TextButton(onClick = {
                                store.retry(photo.key)
                            }) { Text("Retry upload") }
                        }
                    }
                    TextButton(onClick = { store.remove(photo.key) }) {
                        Text(
                            if (photo.error == null &&
                                photo.placed == null
                            ) {
                                "Cancel upload"
                            } else {
                                "Remove"
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun photoStatus(photo: PendingPhoto): String = photo.error ?: if (photo.placed != null) {
    "Attachment ready"
} else {
    "${photo.progress.stage.name.lowercase()}: ${photo.progress.transferred} bytes"
}

@Suppress("CyclomaticComplexMethod") // Independent load, decode, retry, and external-open states.
@Composable
fun MediaPreview(
    reference: MediaReference,
    source: MediaSource? = LocalAttachmentMedia.current,
    actions: Boolean = true,
    showName: Boolean = true
) {
    val isImage = mediaPreviewKind(reference) == dev.intent.app.files.WorkspacePreviewKind.IMAGE
    var viewer by remember(reference, source) { mutableStateOf(false) }
    val store = remember(reference, source) { source?.let { MediaLoadStore(it, reference) } }
    val state = store?.state?.collectAsState()?.value ?: MediaLoadState()
    var bitmap by remember(reference, source) { mutableStateOf<Bitmap?>(null) }
    var error by remember(reference, source) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(state.file) {
        bitmap = null
        error = null
        state.file?.takeIf { isImage }?.let { file ->
            val result = withContext(Dispatchers.IO) { runCatching { decodePhoto(file) } }
            bitmap = result.getOrNull()
            if (result.isFailure) error = "Cannot decode image. Open it in another app."
        }
    }
    DisposableEffect(store) {
        if (isImage) store?.load()
        onDispose { store?.close() }
    }
    Column {
        if (showName) Text(reference.name)
        MediaStatus(state.loading, error ?: state.error, bitmap, actions) {
            if (actions) {
                viewer =
                    true
            }
        }
        if (source == null) Text("Connect to load attachment")
        val failed = state.error != null || error != null
        val needsDownload = !isImage && state.file == null
        if (failed || needsDownload) {
            TextButton(
                onClick = {
                    viewer = actions
                    store?.load()
                },
                enabled =
                !state.loading && source != null
            ) {
                Text(
                    if (state.error !=
                        null ||
                        error != null
                    ) {
                        "Retry"
                    } else {
                        "Open attachment"
                    }
                )
            }
        }
        if (actions && state.file != null) {
            TextButton(onClick = { viewer = true }) { Text("Open") }
        }
    }
    if (viewer) state.file?.let { MediaViewerRoute(it, reference) { viewer = false } }
}

@Composable
private fun MediaStatus(
    loading: Boolean,
    error: String?,
    bitmap: Bitmap?,
    fullSize: Boolean,
    onOpen: () -> Unit = {}
) {
    if (loading) CircularProgressIndicator(Modifier.size(24.dp).testTag("media.loading"))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    bitmap?.let {
        Image(
            it.asImageBitmap(),
            "Attached image",
            if (fullSize) {
                Modifier.heightIn(
                    max = 320.dp
                ).clickable(onClick = onOpen)
            } else {
                Modifier.size(64.dp)
            }
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun MediaStatesPreview() {
    MaterialTheme {
        Column {
            MediaStatus(true, null, null, true)
            MediaStatus(false, "Cannot load attachment. Retry.", null, true)
        }
    }
}
