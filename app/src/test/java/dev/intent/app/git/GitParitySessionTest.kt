package dev.intent.app.git

import dev.intent.core.protocol.models.GitRoot
import dev.intent.core.protocol.session.RpcSession
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitParitySessionTest {
    @Test
    fun primaryTrackingPrecedesForcedStatusAndHistoryMatchesIos() = connected { source, server ->
        val status = source.status()
        val tracking = server.request()
        assertEquals("file-tracking.getChanges", tracking.method)
        assertEquals("""{"workspaceId":"ws"}""", tracking.params.toString())
        server.reply(tracking, """{"changes":[],"truncated":false}""")
        val git = server.request()
        assertEquals("git.status", git.method)
        assertEquals("""{"workspaceId":"ws","forceRefresh":true}""", git.params.toString())
        server.reply(git, """{"files":[{"path":"fresh","status":"?","staged":false}]}""")
        assertEquals("fresh", status.get(2, TimeUnit.SECONDS).files.single().path)
        val page = source.commits("next")
        val history = server.request()
        assertEquals("file-tracking.loadCommits", history.method)
        assertEquals(JsonPrimitive(false), history.params!!.jsonObject["includeOlder"])
        assertEquals(JsonPrimitive(50), history.params!!.jsonObject["limit"])
        assertEquals(JsonPrimitive("next"), history.params!!.jsonObject["nextToken"])
        server.reply(history, """{"commits":[],"boundarySha":"base"}""")
        assertEquals("base", page.get(2, TimeUnit.SECONDS).boundarySha)
    }

    @Test
    fun secondarySnapshotSkipsTrackingAndBoundsHistory() = connected { source, server ->
        val root = GitRoot("r", "/r", registeredCommitSha = "base")
        val status = source.status(root)
        val git = server.request()
        assertEquals("git.status", git.method)
        assertEquals(JsonPrimitive("r"), git.params!!.jsonObject["gitRootId"])
        assertEquals(JsonPrimitive(true), git.params!!.jsonObject["forceRefresh"])
        server.reply(git, "{}")
        status.get(2, TimeUnit.SECONDS)
        val page = source.commits(root = root)
        val history = server.request()
        assertEquals("git.commits", history.method)
        assertNull(history.params!!.jsonObject["includeOlder"])
        server.reply(history, """{"items":[{"hash":"new"},{"hash":"base"}],"nextToken":"next"}""")
        assertEquals(listOf("new"), page.get(2, TimeUnit.SECONDS).items.map { it.hash })
        assertNull(page.get().nextToken)
    }

    @Test
    fun primaryCommitUsesBranchDiffAndRootCommitFallsBackToShowFile() = connected {
            source,
            server
        ->
        val diff = source.commitDiff("hash", "a", null)
        val request = server.request()
        assertEquals("git.branchDiff", request.method)
        assertEquals(
            """{"workspaceId":"ws","baseRef":"hash^","targetRef":"hash","paths":["a"]}""",
            request.params.toString()
        )
        server.reply(request, "[]")
        val content = server.request()
        assertEquals("git.showFile", content.method)
        assertEquals(
            """{"workspaceId":"ws","filePath":"a","ref":"hash"}""",
            content.params.toString()
        )
        server.reply(content, """{"content":"added\n"}""")
        assertEquals(
            "Addition",
            diff.get(2, TimeUnit.SECONDS).single().hunks.single().lines.single().type
        )
    }

    @Test
    fun secondaryCommitReadsParentAndBothScopedFileVersions() = connected { source, server ->
        val diff = source.commitDiff("hash", "a", GitRoot("r", "/r"))
        val parent = server.request()
        assertEquals("git.commitDetails", parent.method)
        assertEquals(JsonPrimitive("hash^"), parent.params!!.jsonObject["commitHash"])
        assertEquals(JsonPrimitive("r"), parent.params!!.jsonObject["gitRootId"])
        server.reply(parent, """{"date":"2026-10-01"}""")
        val reads = listOf(server.request(), server.request())
        assertTrue(
            reads.all {
                it.method == "git.showFile" &&
                    it.params!!.jsonObject["gitRootId"] == JsonPrimitive("r")
            }
        )
        assertEquals(
            setOf(JsonPrimitive("hash^"), JsonPrimitive("hash")),
            reads.map {
                it.params!!.jsonObject["ref"]
            }.toSet()
        )
        reads.forEach { read ->
            val value = if (read.params!!.jsonObject["ref"] ==
                JsonPrimitive("hash^")
            ) {
                "old"
            } else {
                "new"
            }
            server.reply(read, """{"content":"$value\n"}""")
        }
        assertEquals(
            listOf("Deletion", "Addition"),
            diff.get(2, TimeUnit.SECONDS).single().hunks.single().lines.map {
                it.type
            }
        )
    }

    private fun connected(test: (RpcGitSession, GitTestTransport) -> Unit) {
        val transports = LinkedBlockingQueue<GitTestTransport>()
        RpcSession({
            GitTestTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            test(RpcGitSession(rpc, "ws"), server)
        }
    }
}
