package dev.intent.app.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHunksTest {
    @Test
    fun modificationKeepsContextAndLineNumbers() {
        val hunk = buildGitHunks("one\nold\nend\n", "one\nnew\nend\n").single()
        assertEquals(
            listOf("Context", "Deletion", "Addition", "Context"),
            hunk.lines.map {
                it.type
            }
        )
        assertEquals(listOf(1, 2, null, 3), hunk.lines.map { it.oldNumber })
        assertEquals(listOf(1, null, 2, 3), hunk.lines.map { it.newNumber })
        assertEquals(3, hunk.oldLines)
        assertEquals(3, hunk.newLines)
        assertTrue(buildGitHunks("same\n", "same\n").isEmpty())
    }

    @Test
    fun additionsDeletionsAndNewlineChangesAreVisible() {
        assertEquals(0, buildGitHunks("", "new\n").single().oldStart)
        assertEquals(0, buildGitHunks("old\n", "").single().newLines)
        val lines = buildGitHunks("same", "same\n").single().lines
        assertEquals("Deletion", lines.first().type)
        assertTrue(lines.first().content.contains("No newline"))
    }

    @Test
    fun distantEditsHaveSeparateHunks() {
        val middle = (1..20).joinToString("\n")
        assertEquals(2, buildGitHunks("old\n$middle\nold\n", "new\n$middle\nnew\n").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun binaryIsRejected() {
        buildGitHunks("\u0000", "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun excessiveComparisonIsRejected() {
        buildGitHunks("x\n".repeat(3000), "y\n".repeat(3000))
    }
}
