package dev.intent.app.workspaces

import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.messageBlock
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Persist before each mutation. An uncertain message is checked, never blindly resent. */
class CreationSubmission(
    private val operations: WorkspaceOperations,
    private val upload: (String) -> AttachmentUploadService,
    private val save: (WorkspaceDraft) -> Unit
) {
    // Keep persisted checkpoints adjacent to the mutations they protect.
    @Suppress("LongMethod")
    fun submit(input: WorkspaceDraft): String {
        var draft = input
        if (draft.submitted == null) {
            require(draft.media.all { File(it.path).isFile }) {
                "An attachment is missing. Remove it and select it again."
            }
            draft = draft.copy(submitted = draft.params(deferPrompt = draft.media.isNotEmpty()))
            save(draft)
        }
        if (draft.workspaceId.isBlank()) {
            val response = operations.request("workspace.create", requireNotNull(draft.submitted))
            draft = draft.copy(
                workspaceId = response.obj("workspace").string("id"),
                agentId = (response["initialAgent"] as? JsonObject)?.let {
                    runCatching { it.string("id") }.getOrNull()
                }.orEmpty()
            )
            save(draft)
        }
        if (draft.media.isNotEmpty()) {
            check(draft.agentId.isNotBlank()) {
                "Initial agent is missing. Check the workspace list."
            }
            if (draft.messageId.isNotBlank()) {
                check(confirmed(draft)) {
                    "First message is unconfirmed. Check status again; it will not be sent twice."
                }
            } else {
                val service = upload(draft.workspaceId)
                val blocks = draft.media.map { media ->
                    service.placeFile(
                        File(media.path),
                        media.name,
                        media.mimeType,
                        media.key,
                        forceChunked = media.mimeType.startsWith("video/")
                    ).join().messageBlock(media.image)
                }
                draft = draft.copy(messageId = UUID.randomUUID().toString())
                save(draft)
                val result = sendFirstMessage(
                    draft,
                    buildJsonObject {
                        put("workspaceId", draft.workspaceId)
                        put("agentId", draft.agentId)
                        put("messageId", draft.messageId)
                        put("userAppMessageId", draft.messageId)
                        put("content", draft.prompt.trim())
                        put(
                            "imageBlocks",
                            JsonArray(
                                blocks.filter {
                                    it["type"] ==
                                        JsonPrimitive("image")
                                }
                            )
                        )
                        put(
                            "fileBlocks",
                            JsonArray(
                                blocks.filter {
                                    it["type"] == JsonPrimitive("file")
                                }
                            )
                        )
                    }
                )
                check(result["success"] == JsonPrimitive(true)) {
                    "First message is unconfirmed. Check status."
                }
            }
        }
        return draft.workspaceId
    }

    @Suppress("TooGenericExceptionCaught") // Only transport proof permits a new send attempt.
    private fun sendFirstMessage(draft: WorkspaceDraft, params: JsonObject): JsonObject = try {
        operations.request("agent.sendMessage", params)
    } catch (failure: Exception) {
        if (creationRequestNotSent(failure)) save(draft.copy(messageId = ""))
        throw failure
    }

    private fun confirmed(draft: WorkspaceDraft): Boolean {
        val params = buildJsonObject {
            put("workspaceId", draft.workspaceId)
            put("agentId", draft.agentId)
            put("aroundMessageId", draft.messageId)
            put("limit", CREATION_HISTORY_LIMIT)
        }
        val history = operations.request("agent.getConversation", params).rows("messages")
        val queue = operations.request("agent.getQueue", params).rows("queue")
        return (history + queue).any { row ->
            val metadata = row["metadata"] as? JsonObject ?: row["messageMetadata"] as? JsonObject
            listOf(row["id"], row["appMessageId"], metadata?.get("userAppMessageId"))
                .contains(JsonPrimitive(draft.messageId))
        }
    }
}

private const val CREATION_HISTORY_LIMIT = 100

internal fun creationRequestNotSent(error: Throwable): Boolean {
    var cause = error
    while (cause is java.util.concurrent.ExecutionException ||
        cause is java.util.concurrent.CompletionException
    ) {
        cause = cause.cause ?: break
    }
    return cause is dev.intent.core.protocol.session.SessionDisconnectedException &&
        cause.requestNotSent
}
