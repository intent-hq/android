package dev.intent.app.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.intent.core.protocol.models.ContentBlock

/** Prose and named groups remain boundaries; only adjacent unnamed activity is combined. */
internal fun groupToolSections(sections: List<TranscriptSection>): List<TranscriptSection> {
    val result = mutableListOf<TranscriptSection>()
    sections.forEach { section ->
        val previous = result.lastOrNull()
        if (section.isToolActivity() && previous != null && previous.isToolActivity()) {
            result[result.lastIndex] = previous.copy(blocks = previous.blocks + section.blocks)
        } else {
            result += section
        }
    }
    return result
}

private fun TranscriptSection.isToolActivity(): Boolean =
    name == null && blocks.isNotEmpty() && blocks.all { it.isToolActivity() }

internal fun toolActivityLabel(blocks: List<ContentBlock>): String {
    val errors = blocks.count { it.isError == true }
    return "Tool activity · ${blocks.size} items" + if (errors > 0) " · $errors failed" else ""
}

@Composable
internal fun TranscriptSectionBlocks(
    blocks: List<ContentBlock>,
    hydrating: Set<String>,
    onHydrate: (ContentBlock) -> Unit
) {
    val groups = groupToolSections(
        blocks.mapIndexed { index, block ->
            TranscriptSection(block.id ?: "block:$index", blocks = listOf(block))
        }
    )
    groups.forEach { group ->
        androidx.compose.runtime.key(group.id) {
            if (group.blocks.all { it.isToolActivity() }) {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Column {
                    CompactDisclosure(toolActivityLabel(group.blocks), expanded) {
                        expanded = !expanded
                    }
                    if (expanded) ToolActivityBlocks(group.blocks, hydrating, onHydrate)
                }
            } else {
                val block = group.blocks.single()
                TranscriptBlock(block, block.id in hydrating) { onHydrate(block) }
            }
        }
    }
}

@Composable
internal fun ToolActivityBlocks(
    blocks: List<ContentBlock>,
    hydrating: Set<String>,
    onHydrate: (ContentBlock) -> Unit
) {
    blocks.forEachIndexed { index, block ->
        androidx.compose.runtime.key(block.id ?: "block:$index") {
            TranscriptBlock(block, block.id in hydrating) { onHydrate(block) }
        }
    }
}
