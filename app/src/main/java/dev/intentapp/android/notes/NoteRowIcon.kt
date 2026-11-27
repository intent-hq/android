@file:Suppress("MagicNumber") // iOS TaskStatusIcon and TaskProgressRing reference geometry.

package dev.intentapp.android.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen

private val ICON_SIZE = 24.dp

/** iOS WorkspaceNoteIcon, 24dp. */
@Composable
internal fun NoteRowIcon(kind: NoteIconKind, modifier: Modifier = Modifier) {
    val sized = modifier.size(ICON_SIZE)
    when (kind) {
        NoteIconKind.Document -> DocumentIcon(R.drawable.ic_file, sized)
        NoteIconKind.Spec -> DocumentIcon(R.drawable.ic_file_list, sized)
        is NoteIconKind.Progress -> ProgressRing(kind.stats, sized)
        is NoteIconKind.Task -> TaskIcon(normalizedStatus(kind.status), sized)
    }
}

private fun normalizedStatus(status: String) = when (status) {
    "pending", "todo" -> "not_started"
    "done" -> "complete"
    "in-progress" -> "in_progress"
    else -> status
}

@Composable
private fun DocumentIcon(resource: Int, modifier: Modifier) {
    Icon(painterResource(resource), "Note", modifier, tint = IntentScreen.secondaryText)
}

@Composable
private fun TaskIcon(status: String, modifier: Modifier) {
    val label = Modifier.semantics { contentDescription = contextStatusLabel(status) }
    when (status) {
        "complete", "not_started", "cancelled" -> Checkbox(status, modifier.then(label))
        "review_required" -> Icon(
            painterResource(R.drawable.ic_eye),
            contextStatusLabel(status),
            modifier,
            tint = IntentScreen.secondaryText
        )
        else -> StatusCircle(status, modifier.then(label))
    }
}

@Composable
private fun Checkbox(status: String, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val complete = status == "complete"
    Box(
        modifier
            .alpha(if (status == "cancelled") 0.3f else 1f)
            .then(
                if (complete) {
                    Modifier.background(Accent, shape)
                } else {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.outline, shape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (status != "not_started") {
            Icon(
                painterResource(R.drawable.ic_check),
                null,
                Modifier.size(16.dp),
                tint = if (complete) Ink else IntentScreen.text
            )
        }
    }
}

@Composable
private fun ProgressRing(stats: TaskStats, modifier: Modifier) {
    Canvas(
        modifier.semantics {
            contentDescription = "${stats.completed} of ${stats.total} tasks complete"
        }
    ) {
        val stroke = size.minDimension * 2.5f / 14f
        val inset = stroke / 2
        val arc = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(Neutral, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
        val completed = stats.completed.toFloat() / stats.total
        val inProgress = stats.inProgress.toFloat() / stats.total
        if (stats.inProgress > 0) {
            drawArc(
                InProgress,
                -90f + completed * 360f,
                minOf(inProgress, 1f - completed) * 360f,
                false,
                topLeft,
                arc,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        if (stats.completed > 0) {
            drawArc(
                ProgressComplete,
                -90f,
                completed * 360f,
                false,
                topLeft,
                arc,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun StatusCircle(status: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val color = when (status) {
            "discussion_needed" -> Warning
            "blocked" -> Blocked
            "in_progress" -> InProgress
            else -> Neutral
        }
        when (status) {
            "discussion_needed", "blocked" -> drawCircle(color, 12f * unit)
            "in_progress" -> clipRect(left = 11.52f * unit) { drawCircle(color, 6.6f * unit) }
        }
        drawCircle(color, 10.8f * unit, style = Stroke(2.5f * unit))
        if (status == "in_progress") drawCircle(color, 10f * unit, style = Stroke(2f * unit))
        when (status) {
            "blocked" -> drawBlockedGlyph(unit)
            "discussion_needed" -> drawQuestion(measurer, Color.White, 12f, unit)
            "not_started", "in_progress", "waiting" -> Unit
            else -> drawQuestion(measurer, Neutral, 11f, unit)
        }
    }
}

private fun DrawScope.drawBlockedGlyph(unit: Float) {
    drawLine(
        Color.White,
        Offset(12f * unit, 5.5f * unit),
        Offset(12f * unit, 13.5f * unit),
        3f * unit,
        StrokeCap.Round
    )
    drawCircle(Color.White, 1.6f * unit, Offset(12f * unit, 17.5f * unit))
}

private fun DrawScope.drawQuestion(measurer: TextMeasurer, color: Color, size: Float, unit: Float) {
    val layout = measurer.measure(
        "?",
        TextStyle(
            color = color,
            fontSize = (size * unit / density).sp,
            fontWeight = FontWeight.Bold
        )
    )
    val bounds = Rect(Offset.Zero, this.size)
    drawText(
        layout,
        topLeft = Offset(
            bounds.center.x - layout.size.width / 2f,
            bounds.center.y - layout.size.height / 2f
        )
    )
}

// iOS Assets.xcassets TaskStatus* and TaskProgress* colors.
private val Neutral = Color(0x66999999)
private val Warning = Color(0xFFF59E0B)
private val Blocked = Color(0xFFEF4444)
private val InProgress = Color(0xFF00BCFF)
private val ProgressComplete = Color(0xFF10B981)
private val Accent = Color(0xFFDDEC61)
private val Ink = Color(0xFF080808)
