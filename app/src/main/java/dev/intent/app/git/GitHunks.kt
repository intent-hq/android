package dev.intent.app.git

import dev.intent.core.protocol.models.GitDiffLine
import dev.intent.core.protocol.models.GitHunk

internal fun buildGitHunks(old: String, new: String): List<GitHunk> {
    require('\u0000' !in old && '\u0000' !in new) { "Binary file. A text diff is not available." }
    require(old.toByteArray().size + new.toByteArray().size <= MAXIMUM_BYTES) { TOO_LARGE }
    val before = contentLines(old)
    val after = contentLines(new)
    require(
        before.size + after.size <= MAXIMUM_LINES &&
            before.size.toLong() * after.size <= MAXIMUM_COMPARISONS
    ) {
        TOO_LARGE
    }
    return groupHunks(compareLines(before, after))
}

private fun contentLines(content: String): List<String> {
    if (content.isEmpty()) return emptyList()
    val lines = content.split('\n').toMutableList()
    if (content.endsWith('\n')) {
        lines.removeAt(lines.lastIndex)
    } else {
        lines[lines.lastIndex] += "  ⟵ No newline at end of file"
    }
    return lines
}

private fun compareLines(before: List<String>, after: List<String>): List<GitDiffLine> {
    val lengths = Array(before.size + 1) { IntArray(after.size + 1) }
    for (old in before.indices.reversed()) {
        for (new in after.indices.reversed()) {
            lengths[old][new] = if (before[old] == after[new]) {
                lengths[old + 1][new + 1] + 1
            } else {
                maxOf(lengths[old + 1][new], lengths[old][new + 1])
            }
        }
    }
    val lines = mutableListOf<GitDiffLine>()
    var old = 0
    var new = 0
    while (old < before.size || new < after.size) {
        when {
            old < before.size && new < after.size && before[old] == after[new] -> {
                lines.add(GitDiffLine("Context", before[old], old + 1, new + 1))
                old++
                new++
            }
            old < before.size &&
                (new == after.size || lengths[old + 1][new] >= lengths[old][new + 1]) -> {
                lines.add(GitDiffLine("Deletion", before[old], old + 1, null))
                old++
            }
            else -> {
                lines.add(GitDiffLine("Addition", after[new], null, new + 1))
                new++
            }
        }
    }
    return lines
}

private fun groupHunks(lines: List<GitDiffLine>): List<GitHunk> {
    val ranges = mutableListOf<IntRange>()
    lines.indices.filter { lines[it].type != "Context" }.forEach { index ->
        val range = maxOf(0, index - CONTEXT_LINES)..minOf(lines.lastIndex, index + CONTEXT_LINES)
        val last = ranges.lastOrNull()
        if (last != null &&
            last.last + 1 >= range.first
        ) {
            ranges[ranges.lastIndex] = last.first..range.last
        } else {
            ranges.add(range)
        }
    }
    return ranges.map { range ->
        val selected = lines.slice(range)
        val oldNumbers = selected.mapNotNull { it.oldNumber }
        val newNumbers = selected.mapNotNull { it.newNumber }
        GitHunk(
            oldNumbers.firstOrNull()
                ?: lines.take(range.first).lastOrNull { it.oldNumber != null }?.oldNumber
                ?: 0,
            oldNumbers.size,
            newNumbers.firstOrNull()
                ?: lines.take(range.first).lastOrNull { it.newNumber != null }?.newNumber
                ?: 0,
            newNumbers.size,
            selected
        )
    }
}

private const val MAXIMUM_BYTES = 524_288
private const val MAXIMUM_LINES = 10_000
private const val MAXIMUM_COMPARISONS = 4_000_000
private const val CONTEXT_LINES = 3
private const val TOO_LARGE =
    "This diff is too large to display on this device. Open it in the desktop app."
