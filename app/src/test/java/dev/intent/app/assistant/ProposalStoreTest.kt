package dev.intent.app.assistant

import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalStoreTest {
    @Test fun invalidCreationParametersAllowCorrectedEdits() {
        val server = ProposalFake()
        server.invalidCreate = true
        val store = ProposalStore(server::request, "__chief__", "agent")
        store.apply(creation(), "message", emptySet(), "First", "Original")
        assertTrue(store.state.value.attempts.isEmpty())
        store.apply(creation(), "message", emptySet(), "Corrected", "New instructions")
        assertEquals("Corrected", server.creations.last().text("title"))
        assertEquals("applied", server.resolution)
    }

    @Test fun partialBulkSuccessRetriesOnlyUnconfirmedItems() {
        val server = ProposalFake()
        val store = ProposalStore(server::request, "__chief__", "agent")
        val proposal = bulk()
        server.failSecond = true
        store.apply(proposal, "message", setOf("a", "b"))
        assertEquals(setOf("a"), store.state.value.completed["message:p"])
        assertNotNull(store.state.value.error)
        store.apply(proposal, "message", setOf("a", "b"))
        assertEquals(listOf("a", "b", "b"), server.mutations)
        assertEquals("applied", store.state.value.resolutions["p"])
        store.apply(proposal, "message", setOf("a", "b"))
        assertEquals(3, server.mutations.size)
    }

    @Test fun duplicateApplyIsBlockedWhileMutationIsPending() {
        val server = ProposalFake()
        server.hold = CompletableFuture()
        val store = ProposalStore(server::request, "__chief__", "agent")
        store.apply(bulk(), "message", setOf("a"))
        store.apply(bulk(), "message", setOf("a"))
        assertEquals(listOf("a"), server.mutations)
        server.hold!!.complete(json("""{"success":true}"""))
        assertEquals("applied", store.state.value.resolutions["p"])
    }

    @Test fun staleMessageAndDisabledItemsCannotApply() {
        val server = ProposalFake()
        val store = ProposalStore(server::request, "__chief__", "agent")
        store.apply(bulk(), "old-message", setOf("a"))
        store.apply(bulk(), "message", setOf("disabled", "unlisted"))
        assertTrue(server.mutations.isEmpty())
        assertTrue(server.resolution == null)
    }

    @Test fun dismissResolvesWithoutWorkspaceMutation() {
        val server = ProposalFake()
        val store = ProposalStore(server::request, "__chief__", "agent")
        store.dismiss(bulk(), "message")
        assertEquals("dismissed", store.state.value.resolutions["p"])
        assertTrue(server.mutations.isEmpty())
    }

    @Test fun creationReceiptPreventsDuplicateAfterConfirmationFailure() {
        val server = ProposalFake()
        server.failResolve = true
        val store = ProposalStore(server::request, "__chief__", "agent")
        val proposal = creation()
        store.apply(proposal, "message", emptySet())
        assertEquals("created", store.state.value.created["message:p"])
        store.dismiss(proposal, "message")
        assertNull(server.resolution)
        store.apply(proposal, "message", emptySet(), "Changed", "Changed")
        assertEquals(1, server.creations.size)
        assertEquals("applied", server.resolution)
    }

    @Test fun uncertainCreationRetriesFrozenRequestAndStableKey() {
        val server = ProposalFake()
        server.failCreate = true
        val store = ProposalStore(server::request, "__chief__", "agent")
        store.apply(creation(), "message", emptySet(), "First", "Original")
        store.apply(creation(), "message", emptySet(), "Changed", "Changed")
        assertEquals(2, server.creations.size)
        assertEquals(server.creations[0], server.creations[1])
        assertEquals("First", server.creations[1].text("title"))
        assertNotNull(server.creations[1].text("idempotencyKey"))
    }
}

private fun bulk() = ActionProposal(
    "bulk-op",
    json("""{"operation":"workspace.bulkArchive","ids":["a","b","disabled"]}""") as JsonObject,
    json(
        """{"title":"Archive workspaces","bulkItems":[{"id":"disabled","disabled":true}]}"""
    ) as JsonObject,
    "p"
)
private fun creation() = ActionProposal(
    "workspace-create",
    json(
        """{"operation":"workspace.create","params":{"repositoryPath":"/repo","title":"New"}}"""
    ) as JsonObject,
    json("""{"title":"Create workspace"}""") as JsonObject,
    "p"
)

private class ProposalFake {
    val mutations = mutableListOf<String?>()
    val creations = mutableListOf<JsonObject>()
    var resolution: String? = null
    var failSecond = false
    var failResolve = false
    var failCreate = false
    var invalidCreate = false
    var hold: CompletableFuture<JsonElement>? = null

    @Suppress("CyclomaticComplexMethod") // Fake responses and failure injection.
    fun request(method: String, params: JsonObject): CompletableFuture<JsonElement> =
        when (method) {
            "principal.me" -> done("""{"id":"owner","isAdministrator":true}""")
            "agent.get" -> done(
                if (resolution ==
                    null
                ) {
                    """{"agent":{"metadata":{"pendingProposals":[{"proposalId":"p","messageId":"message"}]}}}"""
                } else {
                    """{"agent":{"metadata":{"proposalResolutions":{"p":"$resolution"}}}}"""
                }
            )
            "workspace.archive" -> {
                val id = params.text("workspaceId")
                mutations += id
                if (id == "b" && failSecond) {
                    failSecond = false
                    failed()
                } else {
                    hold ?: done("""{"success":true}""")
                }
            }
            "workspace.create" -> {
                creations += params
                if (invalidCreate) {
                    invalidCreate = false
                    CompletableFuture<JsonElement>().apply {
                        completeExceptionally(
                            dev.intent.core.protocol.session.RpcException(
                                dev.intent.core.protocol.RpcError(-32602, "Invalid title")
                            )
                        )
                    }
                } else {
                    if (failCreate) {
                        failCreate = false
                        failed()
                    } else {
                        done("""{"workspace":{"id":"created"}}""")
                    }
                }
            }
            "agent.resolveProposal" -> if (failResolve) {
                failResolve = false
                failed()
            } else {
                resolution = params.text("outcome")
                done("""{"success":true,"outcome":"$resolution"}""")
            }
            else -> error(method)
        }
    private fun failed() = CompletableFuture<JsonElement>().apply {
        completeExceptionally(IllegalStateException("Failed"))
    }
}
