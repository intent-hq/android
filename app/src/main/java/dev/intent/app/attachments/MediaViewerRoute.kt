package dev.intent.app.attachments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.intent.app.files.FilePreviewContent
import dev.intent.app.files.PreviewContent
import dev.intent.app.files.PreviewState
import dev.intent.app.files.WorkspacePreviewKind
import dev.intent.app.files.previewContent
import dev.intent.app.files.workspacePreviewKind
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalChromeActions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun mediaPreviewKind(reference: MediaReference): WorkspacePreviewKind = when {
    reference.mimeType == "application/pdf" -> WorkspacePreviewKind.PDF
    reference.mimeType == "text/html" -> WorkspacePreviewKind.HTML
    reference.mimeType?.startsWith("video/") == true -> WorkspacePreviewKind.VIDEO
    reference.mimeType?.startsWith("image/") == true -> WorkspacePreviewKind.IMAGE
    else -> workspacePreviewKind(reference.path ?: reference.name).let {
        if (reference.image && it == WorkspacePreviewKind.TEXT) WorkspacePreviewKind.IMAGE else it
    }
}

/** Reuses the Files renderers. The attachment cache retains the file across dismissal. */
@Composable
fun MediaViewerRoute(file: File, reference: MediaReference, onBack: () -> Unit) {
    val context = LocalContext.current
    var state by remember(file, reference) { mutableStateOf(PreviewState(file)) }
    var attempt by remember(file, reference) { mutableStateOf(0) }
    var openError by remember { mutableStateOf(false) }
    LaunchedEffect(file, reference, attempt) {
        state = PreviewState(file)
        val result = withContext(Dispatchers.IO) {
            runCatching { previewContent(file, mediaPreviewKind(reference)) }
        }
        state =
            PreviewState(
                file,
                result.getOrNull(),
                result.exceptionOrNull()?.let {
                    "Cannot preview file."
                }
            )
    }
    Dialog(onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalChromeActions provides emptyList()) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    CompactAppBar(reference.name, onBack)
                    TextButton(onClick = {
                        runCatching { openMedia(context, file, reference) }.onFailure {
                            openError =
                                true
                        }
                    }) { Text("Open in another app") }
                    if (openError) Text("No app can open this file.")
                    if (state.content is FilePreviewContent.ImageFile) {
                        ImageFileActions(
                            file,
                            reference
                        )
                    }
                    PreviewContent(state) { attempt++ }
                }
            }
        }
    }
}
