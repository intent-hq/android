package dev.intent.app.git

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import dev.intent.app.design.IntentColors
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.LocalIntentDarkTheme
import dev.intent.core.protocol.models.GitDiffLine
import dev.intent.core.protocol.models.GitHunk

/** A hunk owns one horizontal viewport; its line-number gutter never scrolls sideways. */
@Composable
internal fun DiffHunk(hunk: GitHunk) {
    val scroll = rememberScrollState()
    val numberWidth = hunk.lines.maxOfOrNull {
        maxOf(it.oldNumber ?: 0, it.newNumber ?: 0).toString().length
    } ?: 1
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
    ) {
        Text(
            "@@ -${hunk.oldStart},${hunk.oldLines} +${hunk.newStart},${hunk.newLines} @@",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = IntentScreen.secondaryText
        )
        if (scroll.maxValue > 0) {
            Text(
                "↔ Swipe code to see more",
                style = MaterialTheme.typography.bodySmall,
                color = IntentScreen.secondaryText
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
        ) {
            Column(Modifier.testTag("diff.gutter")) {
                hunk.lines.forEach { line ->
                    DiffText(
                        "${line.oldNumber.numberLabel(
                            numberWidth
                        )} ${line.newNumber.numberLabel(numberWidth)}",
                        IntentScreen.secondaryText
                    )
                }
            }
            Column(Modifier.weight(1f).horizontalScroll(scroll).testTag("diff.code")) {
                hunk.lines.forEach { line ->
                    DiffText(
                        line.marker + line.content.trimEnd('\n', '\r'),
                        diffLineColor(line.type, LocalIntentDarkTheme.current),
                        Modifier.testTag(
                            "diff.line.${line.type}.${line.newNumber ?: line.oldNumber}"
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        softWrap = false,
        maxLines = 1,
        color = color
    )
}

private fun Int?.numberLabel(width: Int) = (this?.toString() ?: "").padStart(width)

private val GitDiffLine.marker: String
    get() = when (type) {
        "Addition" -> "+"
        "Deletion" -> "−"
        else -> " "
    }

internal fun diffLineColor(type: String, dark: Boolean): Color = with(IntentColors) {
    when (type) {
        "Addition" -> if (dark) agentAvatarGlyphCompletedDark else agentAvatarGlyphCompletedLight
        "Deletion" -> if (dark) agentAvatarSurfaceFailedDark else diffDeletionLight
        else -> if (dark) themeForegroundDark else themeForegroundLight
    }
}
