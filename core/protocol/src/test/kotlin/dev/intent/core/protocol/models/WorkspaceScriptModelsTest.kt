package dev.intent.core.protocol.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceScriptModelsTest {
    @Test
    fun `starting and future transition statuses are live`() {
        listOf("starting", "running", "restarting", "future-transition").forEach {
            assertTrue(WorkspaceScriptRuntime(it).isLive)
        }
        listOf("idle", "exited").forEach { assertFalse(WorkspaceScriptRuntime(it).isLive) }
    }

    @Test
    fun `runtime snapshots clear absent terminal fields and retain unknown exit codes`() {
        val exited = WireJson.decodeFromString(
            WorkspaceScriptRuntime.serializer(),
            """{"status":"exited","restartCount":0,"exitCode":-1,"error":"exit status unobservable"}"""
        )
        assertEquals(-1, exited.exitCode)
        val starting = WireJson.decodeFromString(
            WorkspaceScriptRuntime.serializer(),
            """{"status":"starting","restartCount":0}"""
        )
        assertNull(starting.exitCode)
        assertNull(starting.error)
        assertNull(starting.detectedUrl)
        assertFalse(starting.previouslyRunning)
    }
}
