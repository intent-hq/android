package dev.intent.app.attachments

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalAttachmentMedia = staticCompositionLocalOf<MediaSource?> { null }

@Composable
fun PhotoPicker(store: PhotoAttachmentStore) {
    val state by store.state.collectAsState()
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)
        ) { uris ->
            store.add(uris.map { it.toString() })
        }
    IconButton(
        onClick = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        enabled =
        state.photos.size < MAX_PHOTOS
    ) {
        Icon(
            painterResource(R.drawable.ic_attach),
            "Attach photos (${state.photos.size}/$MAX_PHOTOS)"
        )
    }
}

@Composable
fun PhotoAttachments(store: PhotoAttachmentStore) {
    val state by store.state.collectAsState()
    Column {
        state.photos.forEach { photo ->
            androidx.compose.runtime.key(photo.key) {
                Row {
                    photo.file?.let { file ->
                        MediaPreview(
                            MediaReference("Selected photo", true),
                            remember(file) {
                                MediaSource {
                                    java.util.concurrent.CompletableFuture.completedFuture(file)
                                }
                            },
                            false
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(photoStatus(photo))
                        if (photo.error !=
                            null
                        ) {
                            TextButton(onClick = {
                                store.retry(photo.key)
                            }) { Text("Retry upload") }
                        }
                    }
                    TextButton(onClick = { store.remove(photo.key) }) { Text("Remove") }
                }
            }
        }
    }
}

private fun photoStatus(photo: PendingPhoto): String = photo.error ?: if (photo.placed != null) {
    "Photo ready"
} else {
    "${photo.progress.stage.name.lowercase()}: ${photo.progress.transferred} bytes"
}

@Suppress("CyclomaticComplexMethod") // Independent load, decode, retry, and external-open states.
@Composable
fun MediaPreview(
    reference: MediaReference,
    source: MediaSource? = LocalAttachmentMedia.current,
    actions: Boolean = true
) {
    val context = LocalContext.current
    val store = remember(reference, source) { source?.let { MediaLoadStore(it, reference) } }
    val state = store?.state?.collectAsState()?.value ?: MediaLoadState()
    var bitmap by remember(reference, source) { mutableStateOf<Bitmap?>(null) }
    var error by remember(reference, source) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(state.file) {
        bitmap = null
        error = null
        state.file?.takeIf { reference.image }?.let { file ->
            val result = withContext(Dispatchers.IO) { runCatching { decodePhoto(file) } }
            bitmap = result.getOrNull()
            if (result.isFailure) error = "Cannot decode image. Open it in another app."
        }
    }
    DisposableEffect(store) {
        if (reference.image) store?.load()
        onDispose { store?.close() }
    }
    Column {
        Text(reference.name)
        MediaStatus(state.loading, error ?: state.error, bitmap, actions)
        if (source == null) Text("Connect to load attachment")
        val failed = state.error != null || error != null
        val needsDownload = !reference.image && state.file == null
        if (failed || needsDownload) {
            TextButton(onClick = { store?.load() }, enabled = !state.loading && source != null) {
                Text(
                    if (state.error !=
                        null ||
                        error != null
                    ) {
                        "Retry"
                    } else {
                        "Download"
                    }
                )
            }
        }
        if (actions && state.file != null) {
            TextButton(onClick = {
                runCatching {
                    openMedia(context, requireNotNull(state.file), reference)
                }.onFailure {
                    error =
                        "No app can open this attachment."
                }
            }) { Text("Open") }
        }
    }
}

@Composable
private fun MediaStatus(loading: Boolean, error: String?, bitmap: Bitmap?, fullSize: Boolean) {
    if (loading) CircularProgressIndicator(Modifier.size(24.dp))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    bitmap?.let {
        Image(
            it.asImageBitmap(),
            "Attached image",
            if (fullSize) Modifier.heightIn(max = 320.dp) else Modifier.size(64.dp)
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
