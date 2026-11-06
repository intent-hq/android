@file:Suppress("MagicNumber") // Geometry and glyph transforms from iOS AssistantLaunchButton.

package dev.intent.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.intent.app.R

@Composable
internal fun IntentLauncherButton(progress: Float, open: Boolean, onClick: () -> Unit) {
    val fraction = progress.coerceIn(0f, 1f)
    Surface(
        onClick = onClick,
        modifier = Modifier.size((80 - 32 * fraction).dp).semantics {
            contentDescription = if (open) "Close app launcher" else "Open app launcher"
        },
        shape = RoundedCornerShape((40 - 20 * fraction).dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = (10 * (1 - fraction)).dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            val glyph = Modifier.size((22 + 2 * fraction).dp)
            Icon(
                painterResource(R.drawable.intent_dandelion),
                null,
                glyph.graphicsLayer {
                    rotationZ = 90 * fraction
                    scaleX = 1 - 0.4f * fraction
                    scaleY = scaleX
                    alpha = 1 - fraction
                }
            )
            Icon(
                Icons.Default.Close,
                null,
                glyph.graphicsLayer {
                    rotationZ = -90 * (1 - fraction)
                    scaleX = 0.6f + 0.4f * fraction
                    scaleY = scaleX
                    alpha = fraction
                }
            )
        }
    }
}
