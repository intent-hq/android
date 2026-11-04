package dev.intent.app.files

import android.graphics.Bitmap
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import dev.intent.app.design.IntentTextButton as TextButton

/** Selectable native text, bounded layout, and a gutter that stays out of copied text. */
@Composable
internal fun WorkspaceTextPreview(text: String, modifier: Modifier = Modifier, path: String = "") {
    var wrap by rememberSaveable(path) { mutableStateOf(true) }
    val content = remember(text) { boundedWorkspaceText(text) }
    val tokens = remember(content, path) { workspaceCodeTokens(content.text, path) }
    val colors = MaterialTheme.colorScheme
    val styled = remember(tokens, colors) {
        SpannableString(content.text).apply {
            var offset = 0
            tokens.forEach { token ->
                val color = when (token.kind) {
                    "string" -> colors.onTertiaryContainer
                    "number" -> colors.secondary
                    "comment" -> colors.onSurfaceVariant
                    else -> colors.onSurface
                }
                setSpan(
                    ForegroundColorSpan(color.toArgb()),
                    offset,
                    offset + token.text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                offset += token.text.length
            }
        }
    }
    Column(modifier.fillMaxSize()) {
        TextButton(onClick = { wrap = !wrap }, modifier = Modifier.testTag("file.wrap")) {
            Text(if (wrap) "Line wrap: on" else "Line wrap: off")
        }
        if (content.truncated) {
            Text("Preview shortened for performance. Open in another app to read the full file.")
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxSize().testTag("file.text"),
            factory = { context -> NumberedTextView(context) },
            update = {
                if (it.tag !== styled) {
                    it.text = styled
                    it.tag = styled
                }
                it.setLineStarts(content.lineStarts)
                it.setTextColor(colors.onSurface.toArgb())
                it.gutterColor = colors.onSurfaceVariant.toArgb()
                it.gutterBackground = colors.background.toArgb()
                it.setHorizontallyScrolling(!wrap)
                if (wrap && it.scrollX != 0) it.scrollTo(0, it.scrollY)
                it.invalidate()
            }
        )
    }
}

@Composable
internal fun WorkspaceImagePreview(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    description: String = "File image"
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Column(modifier.fillMaxSize()) {
        Row {
            TextButton(onClick = {
                scale = (scale * ZOOM_STEP).coerceAtMost(MAX_ZOOM)
            }) { Text("Zoom in") }
            TextButton(onClick = {
                scale = (scale / ZOOM_STEP).coerceAtLeast(1f)
                offset =
                    Offset.Zero
            }) { Text("Zoom out") }
            TextButton(onClick = {
                scale = 1f
                offset = Offset.Zero
            }) { Text("Reset zoom") }
        }
        Box(
            Modifier.weight(1f).fillMaxSize().clipToBounds().transformable(transform),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap.asImageBitmap(),
                description,
                Modifier.fillMaxSize().testTag("file.image").graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
            )
        }
    }
}

private const val MAX_ZOOM = 8f
private const val ZOOM_STEP = 2f
