package dev.intent.app.attachments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import dev.intent.app.design.IntentTextButton as TextButton
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ImageFileActions(file: File, reference: MediaReference) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember(file) { mutableStateOf<String?>(null) }
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("image/*")
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val result =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                requireNotNull(
                                    context.contentResolver.openOutputStream(uri)
                                ).use { output ->
                                    file.inputStream().use { it.copyTo(output) }
                                }
                            }
                        }
                    status = if (result.isSuccess) "Image saved." else "Cannot save image."
                }
            }
        }
    Row {
        TextButton(onClick = {
            save.launch(reference.path?.substringAfterLast('/') ?: reference.name)
        }) {
            Text("Save image")
        }
        TextButton(onClick = {
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) { runCatching { copyImage(context, file) } }
                status = if (result.isSuccess) "Image copied." else "Cannot copy image."
            }
        }) { Text("Copy image") }
    }
    status?.let { Text(it) }
}

/** Clipboard must outlive the preview's temporary directory. Keep one stable copy. */
private fun copyImage(context: Context, file: File) {
    val directory = File(context.cacheDir, "attachments/clipboard").apply { mkdirs() }
    val target = File(directory, "image.png")
    val bitmap = decodePhoto(file)
    try {
        target.outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, PNG_QUALITY, it))
        }
    } finally {
        bitmap.recycle()
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachments", target)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Image", uri))
}

private const val PNG_QUALITY = 100
