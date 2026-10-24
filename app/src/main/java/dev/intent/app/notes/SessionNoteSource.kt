package dev.intent.app.notes

import dev.intent.app.comments.RpcCommentSession
import dev.intent.core.protocol.models.NoteResult
import dev.intent.core.protocol.models.NoteTasksResult
import dev.intent.core.protocol.models.TaskUpdateNoteStatusParams
import dev.intent.core.protocol.models.TaskUpdateNoteStatusResult
import dev.intent.core.protocol.models.TaskUpdateParams
import dev.intent.core.protocol.models.TaskUpdateResult
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SessionNoteSource(private val session: RpcSession) : NoteSource {
    fun comments(workspaceId: String, noteId: String) =
        RpcCommentSession(session, workspaceId, noteId)

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

    override fun updateLine(
        workspaceId: String,
        noteId: String,
        line: Int,
        status: String,
        expected: String?
    ) = session.request(
        "task.update",
        WireJson.encodeToJsonElement(
            TaskUpdateParams(workspaceId, noteId, line, status, expected)
        ).jsonObject,
        TaskUpdateResult.serializer()
    )

    override fun updateStatus(
        workspaceId: String,
        noteId: String,
        status: String,
        expectedVersion: Long
    ) = session.request(
        "task.updateNoteStatus",
        WireJson.encodeToJsonElement(
            TaskUpdateNoteStatusParams(workspaceId, noteId, status, expectedVersion)
        ).jsonObject,
        TaskUpdateNoteStatusResult.serializer()
    )

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
