package dev.intent.app.notes

import dev.intent.core.protocol.models.NoteResult
import dev.intent.core.protocol.models.NoteTasksResult
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SessionNoteSource(private val session: RpcSession) : NoteSource {
    override fun note(workspaceId: String, noteId: String) = session.request(
        "note.get",
        params(workspaceId, noteId),
        NoteResult.serializer()
    ).thenApply {
        it.note
    }

    override fun tasks(workspaceId: String, noteId: String) = session.request(
        "note.listTasks",
        params(workspaceId, noteId),
        NoteTasksResult.serializer()
    ).thenApply {
        it.tasks
    }

    override fun watch(
        workspaceId: String,
        onRefresh: () -> Unit,
        onEvent: (NoteEvent) -> Unit,
        onError: (Throwable) -> Unit
    ) = session.subscribe(
        SubscriptionSpec(
            "events.subscribe",
            buildJsonObject {
                put("workspaceId", workspaceId)
                put(
                    "eventTypes",
                    buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("note:updated"))
                        add(kotlinx.serialization.json.JsonPrimitive("note:created"))
                        add(kotlinx.serialization.json.JsonPrimitive("note:deleted"))
                        add(kotlinx.serialization.json.JsonPrimitive("task:*"))
                    }
                )
            },
            notificationMethod = "events.event"
        ),
        onMessage = { payload ->
            val event = payload["event"]?.jsonObject ?: payload
            if (event["workspaceId"]?.jsonPrimitive?.content == workspaceId) {
                onEvent(
                    NoteEvent(
                        event["type"]?.jsonPrimitive?.content.orEmpty(),
                        event["data"]?.jsonObject?.get("noteId")?.jsonPrimitive?.content
                    )
                )
            }
        },
        onSubscribed = { onRefresh() },
        onError = onError
    )

    private fun params(workspaceId: String, noteId: String): JsonObject = buildJsonObject {
        put("workspaceId", workspaceId)
        put("noteId", noteId)
    }
}
