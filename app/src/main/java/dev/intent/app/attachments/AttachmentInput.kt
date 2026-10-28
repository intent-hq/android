package dev.intent.app.attachments

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.intent.app.R
import dev.intent.app.design.IntentTextButton

/** Shared input for composers and question sets. Clipboard access requires an explicit tap. */
@Composable
fun PhotoPicker(store: PhotoAttachmentStore) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val media = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)
    ) { uris -> store.addMedia(uris.map { it.toString() }) }
    val files =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            store.addFiles(uris.map { it.toString() })
        }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_attach),
                "Attach files (${state.photos.size})"
            )
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Photos and videos") }, onClick = {
                expanded = false
                media.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            })
            DropdownMenuItem(text = { Text("Files") }, onClick = {
                expanded = false
                files.launch(arrayOf("*/*"))
            })
            DropdownMenuItem(text = { Text("Paste image") }, onClick = {
                expanded = false
                error =
                    runCatching {
                        pasteImages(context, store)
                    }.getOrDefault("Cannot read the copied image. Copy it again.")
            })
        }
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Cannot paste image") },
            text = { Text(message) },
            confirmButton = { IntentTextButton(onClick = { error = null }) { Text("OK") } }
        )
    }
}

@Suppress("ReturnCount") // Reject unreadable clipboard data before changing the selection.
internal fun pasteImages(context: Context, store: PhotoAttachmentStore): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = clipboard.primaryClip ?: return "Copy an image before pasting."
    val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    val images = uris.filter { context.contentResolver.getType(it)?.startsWith("image/") == true }
    if (images.isEmpty() ||
        images.size != clip.itemCount
    ) {
        return "Cannot read the copied image. Copy it again."
    }
    if (images.size >
        MAX_PHOTOS - store.state.value.photos.count { it.imageSelection }
    ) {
        return "Attach up to $MAX_PHOTOS images. Remove one first."
    }
    store.add(images.map { it.toString() })
    return null
}
