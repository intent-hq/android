package dev.intent.app.workspaces

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferWarningTextTest {
    @Test
    fun `known warnings have singular and plural grammar`() {
        assertEquals("1 agent is running", text("agents-running", "1 agent(s) are running"))
        assertEquals("2 agents are running", text("agents-running", "2 agent(s) are running"))
        assertEquals(
            "1 uncommitted file will be saved",
            text("uncommitted-changes", "1 uncommitted file(s) will be saved")
        )
        assertEquals(
            "2 uncommitted files will be saved",
            text("uncommitted-changes", "2 uncommitted file(s) will be saved")
        )
        assertEquals(
            "1 unmerged sandbox; its branches ride",
            text("unmerged-sandboxes", "1 unmerged sandbox(es); their branches ride")
        )
    }

    @Test
    fun `paths and unknown warnings are preserved`() {
        assertEquals(
            "Nested Git repositories or worktrees excluded from export (1): example/repo(s)",
            text(
                "nested-repos-skipped",
                "1 nested git repo(s)/worktree(s) will not travel with the export: example/repo(s)"
            )
        )
        assertEquals("Future warning", text("future", "Future warning"))
        assertEquals("Changed server message", text("agents-running", "Changed server message"))
    }

    private fun text(code: String, message: String) = transferWarningText(
        buildJsonObject {
            put("code", code)
            put("message", message)
        }
    )
}
