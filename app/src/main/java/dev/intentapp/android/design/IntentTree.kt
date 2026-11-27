package dev.intentapp.android.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// iOS WorkspaceFileTreeLayout and WorkspaceChangesLayout, shared by the Changes and Files trees.
internal const val TREE_MAX_DEPTH = 5
internal const val TREE_GUIDE_ALPHA = 0.3f
internal val TreeIndent = 22.dp
internal val TreeIconSize = 16.dp
internal val TreeFileIconSize = 13.dp
internal val TreeRowHeight = 36.dp
internal val TreeRowPadding = 7.dp
internal val TreeRowSpacing = 8.dp

internal fun treeIndentation(depth: Int): Dp = TreeIndent * minOf(depth, TREE_MAX_DEPTH)

/** Paths deeper than the maximum indentation keep their extra folders in the name. */
internal fun treeDisplayName(path: String, depth: Int): String =
    path.split('/').takeLast(maxOf(depth - TREE_MAX_DEPTH, 0) + 1).joinToString("/")

internal val treeNameStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    )

internal fun Modifier.treeGuides(depth: Int, color: Color, ownGuide: Boolean): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        val half = TreeIconSize.toPx() / 2
        for (level in 0 until minOf(depth, TREE_MAX_DEPTH)) {
            val x = treeIndentation(level).toPx() + half
            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
        }
        if (ownGuide) {
            val x = treeIndentation(depth).toPx() + half
            val top = size.height / 2 + half + 3.dp.toPx()
            if (top < size.height) drawLine(color, Offset(x, top), Offset(x, size.height), stroke)
        }
    }
