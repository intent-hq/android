package dev.intentapp.android.scripts

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceScriptDiscoveryTest {
    @Test
    fun `package scripts use the lockfile manager, quote unsafe names and pick modes`() {
        val rows = WorkspaceScriptDiscovery.scripts(
            "workspace",
            mapOf(
                "package.json" to
                    """{"scripts":{"dev":"vite","build":"tsc","my task":"x","-bad":"x","empty":" "}}"""
            ),
            setOf("package.json", "pnpm-lock.yaml")
        )
        assertEquals(
            listOf("pnpm run build", "pnpm run dev", "pnpm run 'my task'"),
            rows.map { it.command }
        )
        assertEquals(listOf("command", "service", "command"), rows.map { it.mode })
        assertEquals("manifest:package.json:dev", rows[1].id)
        assertFalse(rows.any { it.registered })
    }

    @Test
    fun `declared package manager wins and invalid declarations fall back`() {
        fun manager(declared: String) = WorkspaceScriptDiscovery.scripts(
            "workspace",
            mapOf("package.json" to """{"packageManager":"$declared","scripts":{"a":"x"}}"""),
            setOf("yarn.lock")
        ).single().command.substringBefore(' ')
        assertEquals("bun", manager("bun@1.1.0"))
        assertEquals("yarn", manager("deno@1"))
        assertEquals("yarn", manager("npm@"))
    }

    @Test
    fun `make cargo and python targets get unique prefixed names`() {
        val rows = WorkspaceScriptDiscovery.scripts(
            "workspace",
            mapOf(
                "package.json" to """{"scripts":{"test":"jest"}}""",
                "Makefile" to "test lint: deps\n\techo\n.PHONY: test\nbuild:\n",
                "Cargo.toml" to "[package]\nname = \"x\"\n",
                "pyproject.toml" to "[tool.pytest.ini_options]\n[tool.ruff]\n[tool.mypy]\n"
            ),
            emptySet()
        )
        assertEquals(
            listOf(
                "test", "make:test", "lint", "build", "cargo:build", "cargo:test", "check",
                "python:test", "python:lint", "format", "typecheck"
            ),
            rows.map { it.name }
        )
        assertEquals("ruff format .", rows.first { it.name == "format" }.command)
        assertEquals("mypy .", rows.first { it.name == "typecheck" }.command)
    }

    @Test
    fun `file read accepts bare and bounded results`() {
        assertEquals("text", fileText(JsonPrimitive("text")))
        assertEquals("text", fileText(buildJsonObject { put("content", "text") }))
        assertNull(fileText(JsonPrimitive(1)))
    }

    @Test
    fun `output cleanup removes terminal control sequences`() {
        val raw = "\u001B]0;title\u0007\u001B[1;31mfail\u001B[0m\r\nstep 1\rstep 2\n\u0008done\n\n"
        assertEquals("fail\nstep 2\ndone", cleanScriptOutput(raw))
    }
}
