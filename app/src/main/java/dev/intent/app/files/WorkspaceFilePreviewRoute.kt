package dev.intent.app.files

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import dev.intent.app.attachments.MediaReference
import dev.intent.app.attachments.decodePhoto
import dev.intent.app.attachments.openMedia
import dev.intent.app.design.IntentScreen
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalChromeActions
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** Full-screen child route; the Context composition retains its scroll and tree state. */
@Composable
fun WorkspaceFilePreviewRoute(source: WorkspaceFileSource, path: String, onBack: () -> Unit) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalChromeActions provides emptyList()) {
            Surface(Modifier.fillMaxSize(), color = IntentScreen.canvas) {
                Column {
                    CompactAppBar(path.substringAfterLast('/'), onBack)
                    FilePreviewBody(source, path, Modifier.weight(1f))
                }
            }
        }
    }
}

internal sealed interface FilePreviewContent {
    data class TextFile(val text: String) : FilePreviewContent
    data class ImageFile(val bitmap: Bitmap) : FilePreviewContent
    data class PdfFile(val file: File) : FilePreviewContent
    data class VideoFile(val file: File) : FilePreviewContent
    data class HtmlFile(val text: String) : FilePreviewContent
    data object Unsupported : FilePreviewContent
}

internal data class PreviewState(
    val file: File? = null,
    val content: FilePreviewContent? = null,
    val error: String? = null
)

@Suppress("TooGenericExceptionCaught") // RPC, file, and decoder failures share the retry state.
@Composable
private fun FilePreviewBody(source: WorkspaceFileSource, path: String, modifier: Modifier) {
    val context = LocalContext.current
    var attempt by remember(path, source) { mutableStateOf(0) }
    var state by remember(path, source) { mutableStateOf(PreviewState()) }
    var openError by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path, source, attempt) {
        state = PreviewState()
        val directory = Files.createTempDirectory(
            File(context.cacheDir, "attachments").apply {
                mkdirs()
            }.toPath(),
            "preview-"
        ).toFile()
        try {
            state = runInterruptible(Dispatchers.IO) { loadPreview(source, path, directory) }
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = PreviewState(error = previewError(path))
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                directory.deleteRecursively()
            }
        }
    }
    Column(modifier.fillMaxSize()) {
        state.file?.let { file ->
            TextButton(onClick = {
                runCatching {
                    openMedia(context, file, MediaReference(file.name, false, path = path))
                }
                    .onFailure { openError = "No app can open this file." }
            }) { Text("Open in another app") }
        }
        openError?.let { Text(it, color = IntentScreen.error) }
        if (state.content is FilePreviewContent.ImageFile) {
            state.file?.let {
                dev.intent.app.attachments.ImageFileActions(
                    it,
                    MediaReference(it.name, true, path = path)
                )
            }
        }
        PreviewContent(state) { attempt++ }
    }
}

@Composable
internal fun ColumnScope.PreviewContent(state: PreviewState, onRetry: () -> Unit) {
    when (val content = state.content) {
        is FilePreviewContent.PdfFile -> WorkspacePdfPreview(content.file, Modifier.weight(1f))
        is FilePreviewContent.VideoFile -> WorkspaceVideoPreview(content.file, Modifier.weight(1f))
        is FilePreviewContent.HtmlFile -> WorkspaceHtmlPreview(content.text, Modifier.weight(1f))
        is FilePreviewContent.TextFile -> WorkspaceTextPreview(
            content.text,
            Modifier.weight(1f)
        )
        is FilePreviewContent.ImageFile -> WorkspaceImagePreview(
            content.bitmap,
            Modifier.weight(1f)
        )
        FilePreviewContent.Unsupported -> Text(
            "Preview not available yet for this file.",
            Modifier.padding(IntentScreen.pageInsets)
        )
        null -> if (state.error == null) {
            Text("Loading file…", Modifier.padding(IntentScreen.pageInsets))
        } else {
            Column(Modifier.padding(IntentScreen.pageInsets)) {
                Text(requireNotNull(state.error), color = IntentScreen.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

private fun loadPreview(source: WorkspaceFileSource, path: String, directory: File): PreviewState {
    val kind = workspacePreviewKind(path)
    val file = source.download(
        path,
        directory,
        previewByteLimit(kind)
    )
    return PreviewState(file, previewContent(file, kind))
}

internal fun previewContent(file: File, kind: WorkspacePreviewKind): FilePreviewContent {
    check(file.length() <= previewByteLimit(kind))
    return when (kind) {
        WorkspacePreviewKind.PDF -> FilePreviewContent.PdfFile(file)
        WorkspacePreviewKind.VIDEO -> FilePreviewContent.VideoFile(file)
        WorkspacePreviewKind.HTML -> FilePreviewContent.HtmlFile(readWorkspaceText(file))
        WorkspacePreviewKind.IMAGE -> FilePreviewContent.ImageFile(
            decodePhoto(file, IMAGE_MAX_EDGE)
        )
        WorkspacePreviewKind.TEXT -> runCatching {
            FilePreviewContent.TextFile(readWorkspaceText(file))
        }
            .getOrElse { FilePreviewContent.Unsupported }
        WorkspacePreviewKind.UNSUPPORTED -> FilePreviewContent.Unsupported
    }
}

private fun previewError(path: String): String = when (workspacePreviewKind(path)) {
    WorkspacePreviewKind.TEXT ->
        "Cannot load text. Check your connection and retry. " +
            "Text previews support UTF-8 files up to 5 MiB."
    else ->
        "Cannot load file. Check your connection and retry. " +
            "Preview size limit: ${previewByteLimit(
                workspacePreviewKind(path)
            ) / BYTES_PER_MIB} MiB."
}

private const val BYTES_PER_MIB = 1024 * 1024
