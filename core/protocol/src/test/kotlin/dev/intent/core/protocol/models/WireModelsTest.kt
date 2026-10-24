package dev.intent.core.protocol.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireModelsTest {
    private val payloads = fixture("payloads.json").jsonObject

    @Test
    fun workspaceAndCommentPayloadsAcceptAdditiveFields() {
        roundTrip<Workspace>("Workspace")
        roundTrip<WorkspaceTaskStats>("WorkspaceTaskStats")
        roundTrip<WorkspaceAgentSummary>("WorkspaceAgentSummary")
        roundTrip<WorkspaceAgentInfo>("WorkspaceAgentInfo")
        roundTrip<WorkspaceListParams>("WorkspaceListParams")
        roundTrip<WorkspaceParams>("WorkspaceParams")
        roundTrip<WorkspaceListResult>("WorkspaceListResult")
        roundTrip<WorkspaceResult>("WorkspaceResult")
        roundTrip<WorkspaceDeleteParams>("WorkspaceDeleteParams")
        roundTrip<WorkspaceCancelDeleteResult>("WorkspaceCancelDeleteResult")
        roundTrip<SuccessResult>("SuccessResult")
        roundTrip<WorkspaceCreateParams>("WorkspaceCreateParams")
        roundTrip<WorkspaceUpdateParams>("WorkspaceUpdateParams")
        roundTrip<Comment>("Comment")
        roundTrip<CommentThreadSummary>("CommentThreadSummary")
        roundTrip<CommentListParams>("CommentListParams")
        roundTrip<CommentListResult>("CommentListResult")
        roundTrip<CommentGetThreadParams>("CommentGetThreadParams")
        roundTrip<CommentThreadResult>("CommentThreadResult")
    }

    @Test
    fun eventPayloadsAcceptAdditiveFields() {
        roundTrip<NoteChangedData>("NoteChangedData")
        roundTrip<WorkspaceCreatedData>("WorkspaceCreatedData")
        roundTrip<WorkspaceUpdatedData>("WorkspaceUpdatedData")
        roundTrip<WorkspaceDeletedData>("WorkspaceDeletedData")
        roundTrip<WorkspaceDeleteScheduledData>("WorkspaceDeleteScheduledData")
        roundTrip<WorkspaceActivityData>("WorkspaceActivityData")
        roundTrip<WorkspaceAttentionData>("WorkspaceAttentionData")
        roundTrip<WorkspaceDisplayStatusData>("WorkspaceDisplayStatusData")
        roundTrip<WorkspaceWaitingData>("WorkspaceWaitingData")
        roundTrip<WorkspaceScriptsRunningData>("WorkspaceScriptsRunningData")
        roundTrip<WorkspaceTaskStatsData>("WorkspaceTaskStatsData")
        roundTrip<WorkspaceContextData>("WorkspaceContextData")
        roundTrip<WorkspaceSetupCompletedData>("WorkspaceSetupCompletedData")
        roundTrip<TaskCreatedData>("TaskCreatedData")
        roundTrip<TaskStatusChangedData>("TaskStatusChangedData")
        roundTrip<TaskReadyData>("TaskReadyData")
        roundTrip<TaskReadyTrigger>("TaskReadyTrigger")
        roundTrip<TasksUpdatedData>("TasksUpdatedData")
        roundTrip<TaskAgentLinkData>("TaskAgentLinkData")
        roundTrip<CommentAddedData>("CommentAddedData")
        roundTrip<CommentDeletedData>("CommentDeletedData")
        roundTrip<CommentResolvedData>("CommentResolvedData")
    }

    @Test
    fun subscriptionPayloadsAcceptAdditiveFields() {
        roundTrip<EventsSubscribeParams>("EventsSubscribeParams")
        roundTrip<SubscriptionResult>("SubscriptionResult")
        roundTrip<UnsubscribeParams>("UnsubscribeParams")
        roundTrip<WorkspaceSubscribeParams>("WorkspaceSubscribeParams")
        roundTrip<NoteSubscribeParams>("NoteSubscribeParams")
        roundTrip<CommentSubscribeParams>("CommentSubscribeParams")
        roundTrip<EventNotification>("EventNotification")
        roundTrip<DomainEvent>("DomainEvent")
        roundTrip<EventActor>("EventActor")
    }

    @Test
    fun noteAndTaskPayloadsAcceptAdditiveFields() {
        roundTrip<Note>("Note")
        roundTrip<NoteMetadata>("NoteMetadata")
        roundTrip<TaskMetadata>("TaskMetadata")
        roundTrip<NoteListParams>("NoteListParams")
        roundTrip<NoteGetParams>("NoteGetParams")
        roundTrip<NoteParams>("NoteParams")
        roundTrip<NoteListResult>("NoteListResult")
        roundTrip<NoteResult>("NoteResult")
        roundTrip<NoteTaskRow>("NoteTaskRow")
        roundTrip<NoteTasksResult>("NoteTasksResult")
        roundTrip<WorkspaceTask>("WorkspaceTask")
        roundTrip<TaskListParams>("TaskListParams")
        roundTrip<TaskGetParams>("TaskGetParams")
        roundTrip<TaskListResult>("TaskListResult")
        roundTrip<TaskResult>("TaskResult")
        roundTrip<TaskUpdateNoteStatusParams>("TaskUpdateNoteStatusParams")
        roundTrip<TaskUpdateNoteStatusResult>("TaskUpdateNoteStatusResult")
        roundTrip<TaskUpdateStatusParams>("TaskUpdateStatusParams")
        roundTrip<TaskUpdateStatusResult>("TaskUpdateStatusResult")
    }

    @Test
    fun noteProjectionAndRevisionRemainDistinct() {
        val full = WireJson.decodeFromJsonElement<Note>(payloads.getValue("Note"))
        assertEquals(9223372036854775806L, full.rev)
        assertEquals("future_status", full.metadata?.task?.status)
        assertEquals(listOf("dep"), full.metadata?.task?.unmetDependsOn)
        val slim = WireJson.decodeFromString<Note>(
            """{"id":"spec","title":"Spec","contentPreview":"preview","contentLength":80}"""
        )
        assertNull(slim.content)
        assertEquals(80L, slim.contentLength)
        assertFalse(slim.contentTruncated)
    }

    @Test
    fun canonicalTruncatedReadFixturesDecodeWithoutLosingMarkers() {
        // Prepared contract fixtures test shape support, not capability availability.
        fixture("truncated-read.json").jsonObject.getValue("cases").jsonArray.forEach { case ->
            val expected = case.jsonObject.getValue("expect").jsonObject
            val row = JsonObject(
                expected + mapOf("id" to JsonPrimitive("spec"), "title" to JsonPrimitive("Spec"))
            )
            val note = WireJson.decodeFromJsonElement<Note>(row)
            assertEquals(expected.getValue("content").jsonPrimitive.content, note.content)
            assertEquals(
                expected["contentTruncated"]?.jsonPrimitive?.booleanOrNull ?: false,
                note.contentTruncated
            )
            assertEquals(expected["contentBytes"]?.jsonPrimitive?.longOrNull, note.contentBytes)
        }
    }

    @Test
    fun everyCatalogEventAndFutureEventPreservesItsEnvelope() {
        val types = fixture("event-types.json").jsonObject.getValue("types").jsonArray
        (types.map { it.jsonPrimitive.content } + "future:new-event").forEach { type ->
            val event = DomainEvent(
                type,
                "workspace",
                "event",
                "2026-10-24T19:00:00Z",
                EventActor("system"),
                JsonObject(mapOf("unknownData" to JsonArray(listOf(JsonPrimitive("keep")))))
            )
            assertEquals(
                event,
                WireJson.decodeFromString<DomainEvent>(WireJson.encodeToString(event))
            )
        }
    }

    @Test
    fun snapshotsAndSparseDeltasUseTheCorrectEntityShapes() {
        val note = """{"id":"task","title":"Task","specLinked":true,
            |"metadata":{"task":{"status":"waiting"}}}
        """.trimMargin()
        val push = WireJson.decodeFromString<SubscriptionPush<Note>>(
            """{"subscriptionId":"s","kind":"snapshot","seq":0,"snapshot":[$note]}"""
        )
        assertEquals(true, push.snapshot?.single()?.specLinked)
        assertEquals("waiting", push.snapshot?.single()?.metadata?.task?.status)
        val delta = WireJson.decodeFromString<SubscriptionPush<Workspace>>(
            """{"subscriptionId":"s","kind":"delta","seq":1,"delta":{"removedIds":["gone"]}}"""
        )
        assertEquals(listOf("gone"), delta.delta?.removedIds)
        assertTrue(delta.delta!!.added.isEmpty())
        assertNull(delta.snapshot)
        val tasks = WireJson.decodeFromJsonElement<TaskListResult>(
            payloads.getValue("TaskListResult")
        )
        assertEquals("example", tasks.tasks.single().status)
    }

    @Test
    fun omittedRequestOptionsStayAbsent() {
        assertEquals("{}", WireJson.encodeToString(WorkspaceListParams()))
        assertEquals(
            """{"workspaceId":"w","noteId":"spec"}""",
            WireJson.encodeToString(NoteGetParams("w", "spec"))
        )
        assertEquals("""{"subscriptionId":"s"}""", WireJson.encodeToString(UnsubscribeParams("s")))
    }

    private inline fun <reified T> roundTrip(name: String) {
        val source = payloads.getValue(name).jsonObject
        val withUnknown = JsonObject(source + ("futureField" to JsonPrimitive(true)))
        val decoded = WireJson.decodeFromJsonElement<T>(withUnknown)
        assertEquals(name, decoded, WireJson.decodeFromString<T>(WireJson.encodeToString(decoded)))
    }

    private fun fixture(name: String) =
        WireJson.parseToJsonElement(checkNotNull(javaClass.getResource("/models/$name")).readText())
}
