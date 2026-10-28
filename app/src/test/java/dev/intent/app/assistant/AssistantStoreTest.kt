package dev.intent.app.assistant

import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStoreTest {
    @Test fun ownerGateRejectsGuestsMembersAndMalformedRoles() {
        val caps = json("""{"hostMembership":1}""") as JsonObject
        assertTrue(
            assistantOwner(json("""{"id":"me","isAdministrator":true}"""), JsonObject(emptyMap()))
        )
        assertFalse(assistantOwner(json("""{"id":"me","isAdministrator":true}"""), caps))
        listOf("guest", "member", "unknown").forEach {
            assertFalse(
                assistantOwner(
                    json("""{"id":"me","isAdministrator":true,"hostRole":"$it"}"""),
                    caps
                )
            )
        }
        assertTrue(
            assistantOwner(
                json(
                    """{"id":"me","isAdministrator":true,"hostRole":"owner","hostMembershipRevision":1}"""
                ),
                caps
            )
        )
    }

    @Test fun threadsAreScopedSortedAndExcludeDelegatedAgents() {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        val store = AssistantStore({ method, params ->
            calls += method to params
            done(
                if (method == "principal.me") {
                    OWNER
                } else {
                    """{"agents":[
                {"id":"old","name":"Old","workspaceId":"__chief__","updatedAt":"2026-01-01"},
                {"id":"new","name":"New","updatedAt":"2026-02-01"},
                {"id":"other","name":"Other","workspaceId":"ws"},
                {"id":"child","name":"Child","createdByAgentId":"new"},
                {"id":"gone","name":"Gone","status":"deleted"}]}"""
                }
            )
        })
        store.refresh()
        assertEquals(listOf("new", "old"), store.state.value.threads.map { it.id })
        assertEquals("new", store.state.value.selectedId)
        assertEquals("__chief__", calls.last().second.text("workspaceId"))
        store.select("other")
        assertEquals("new", store.state.value.selectedId)
        store.select("old")
        store.refresh()
        assertEquals("old", store.state.value.selectedId)
    }

    @Test fun createRetryReusesKeyAndDuplicateTapDoesNotCreateAgain() {
        val pending = CompletableFuture<JsonElement>()
        val keys = mutableListOf<String?>()
        var attempt = 0
        val store = AssistantStore({ method, params ->
            when (method) {
                "principal.me" -> done(OWNER)
                "agent.list" -> done("""{"agents":[]}""")
                else -> {
                    keys += params.text("idempotencyKey")
                    assertEquals("chief-of-staff", params.text("specialistId"))
                    if (attempt++ ==
                        0
                    ) {
                        pending
                    } else {
                        done("""{"agent":{"id":"new","name":"Assistant"}}""")
                    }
                }
            }
        })
        store.refresh()
        store.create()
        store.refresh()
        store.create()
        assertEquals(1, keys.size)
        pending.completeExceptionally(IllegalStateException("Disconnected"))
        store.create()
        assertEquals(keys[0], keys[1])
        assertEquals("new", store.state.value.selectedId)
    }

    @Test fun principalChangeClearsThreadsAndCannotMutate() {
        var principal = OWNER
        val calls = mutableListOf<String>()
        val store = AssistantStore({ method, _ ->
            calls += method
            done(
                if (method ==
                    "principal.me"
                ) {
                    principal
                } else {
                    """{"agents":[{"id":"one","name":"One"}]}"""
                }
            )
        })
        store.refresh()
        principal = """{"id":"another","isAdministrator":true}"""
        store.create()
        assertTrue(store.state.value.threads.isEmpty())
        assertFalse(store.state.value.owner)
        assertFalse(calls.contains("agent.create"))
    }

    @Test fun deletionChoosesRemainingThreadAndCloseIgnoresLateRead() {
        val read = CompletableFuture<JsonElement>()
        var lists = 0
        val store = AssistantStore({ method, _ ->
            when (method) {
                "principal.me" -> done(OWNER)
                "agent.delete" -> done("""{"success":true}""")
                else -> if (lists++ ==
                    0
                ) {
                    done("""{"agents":[{"id":"a","name":"A"},{"id":"b","name":"B"}]}""")
                } else {
                    read
                }
            }
        })
        store.refresh()
        store.delete("a")
        assertEquals("b", store.state.value.selectedId)
        store.refresh()
        store.close()
        read.complete(json("""{"agents":[]}"""))
        assertEquals("b", store.state.value.selectedId)
    }
}

internal fun json(value: String): JsonElement = Json.parseToJsonElement(value)
internal fun done(value: String): CompletableFuture<JsonElement> =
    CompletableFuture.completedFuture(json(value))
private const val OWNER = """{"id":"owner","isAdministrator":true}"""
