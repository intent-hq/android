package dev.intent.core.protocol

import dev.intent.core.protocol.models.GitCommitPage
import dev.intent.core.protocol.models.GitDiff
import dev.intent.core.protocol.models.GitStatus
import dev.intent.core.protocol.models.WireJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitModelsTest {
    @Test
    fun statusPreservesStagedSideGitlinksAndTruncation() {
        val status = WireJson.decodeFromString(
            GitStatus.serializer(),
            """{"branch":"main","ahead":0,"behind":0,"diverged":false,
                "files":[{"path":"packages/lib","status":"M","staged":true,
                "mode":"160000","oldSha":"abc","newSha":"def"}],
                "filesTruncated":true,"totalFiles":5001,"hasUpstream":false,"future":1}"""
        )
        assertTrue(status.files.single().staged)
        assertEquals("160000", status.files.single().mode)
        assertEquals(5001, status.totalFiles)
        assertNull(status.unpushedCount)
    }

    @Test
    fun diffUsesBareArrayAndPreservesLineNumbersAndUnknownTypes() {
        val diffs = WireJson.decodeFromString(
            ListSerializer(GitDiff.serializer()),
            """[{"path":"a.kt","hunks":[{"oldStart":1,"oldLines":1,"newStart":1,"newLines":1,
                "lines":[{"type":"Deletion","content":"old","oldNumber":1},
                {"type":"Addition","content":"new","newNumber":1},
                {"type":"Future","content":"marker"}]}]},{"path":"binary","hunks":[]}]"""
        )
        assertEquals(1, diffs.first().hunks.single().lines.first().oldNumber)
        assertNull(diffs.first().hunks.single().lines.first().newNumber)
        assertEquals("Future", diffs.first().hunks.single().lines.last().type)
        assertTrue(diffs.last().hunks.isEmpty())
    }

    @Test
    fun historyPreservesFullHashAttributionAndContinuation() {
        val page = WireJson.decodeFromString(
            GitCommitPage.serializer(),
            """{"items":[{"hash":"abcdef012345","sha":"abcdef0","author":"Sam","email":"s@example.com",
                "date":"2026-10-24T12:00:00Z","message":"fix: example","agentId":"agent-1",
                "linkedNoteId":"spec"}],"nextToken":"opaque"}"""
        )
        assertEquals("abcdef012345", page.items.single().hash)
        assertEquals("agent-1", page.items.single().agentId)
        assertEquals("opaque", page.nextToken)
        assertEquals(
            page,
            WireJson.decodeFromString(
                GitCommitPage.serializer(),
                WireJson.encodeToString(GitCommitPage.serializer(), page)
            )
        )
    }
}
