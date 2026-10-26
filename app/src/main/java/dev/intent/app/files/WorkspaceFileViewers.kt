package dev.intent.app.files

import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.intent.app.design.IntentScreen

/** Native selectable text wraps to the viewport, without a giant Compose paragraph. */
@Composable
internal fun WorkspaceTextPreview(text: String, modifier: Modifier = Modifier) {
    val color = IntentScreen.text.toArgb()
    AndroidView(
        modifier = modifier.fillMaxSize().testTag("file.text"),
        factory = { context ->
            TextView(context).apply {
                typeface = Typeface.MONOSPACE
                textSize = TEXT_SIZE_SP
                setTextIsSelectable(true)
                setHorizontallyScrolling(false)
                movementMethod = ScrollingMovementMethod()
                setPadding(TEXT_PADDING, TEXT_PADDING, TEXT_PADDING, TEXT_PADDING)
                setTextColor(color)
                this.text = text
            }
        },
        update = { it.setTextColor(color) }
    )
}

@Composable
internal fun WorkspaceImagePreview(bitmap: Bitmap, modifier: Modifier = Modifier) {
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
                "File image",
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
private const val TEXT_SIZE_SP = 16f
private const val TEXT_PADDING = 16
