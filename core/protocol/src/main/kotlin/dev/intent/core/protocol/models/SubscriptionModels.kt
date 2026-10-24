package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class EventsSubscribeParams(
    val eventTypes: List<String>,
    val workspaceId: String? = null,
    val replaceGroup: String? = null,
    val lossRecovery: Int? = null
)

@Serializable
data class SubscriptionResult(val subscriptionId: String, val lossRecovery: Int? = null)

@Serializable
data class UnsubscribeParams(val subscriptionId: String, val workspaceId: String? = null)

@Serializable
data class WorkspaceSubscribeParams(val includeRetired: Boolean? = null)

@Serializable
data class NoteSubscribeParams(
    val workspaceId: String,
    val projection: String? = null,
    val maxContentBytes: Long? = null
)

@Serializable
data class CommentSubscribeParams(val workspaceId: String, val noteId: String? = null)

@Serializable
data class SubscriptionPush<T>(
    val subscriptionId: String,
    val kind: String,
    val seq: Long,
    val snapshot: List<T>? = null,
    val delta: EntityDelta<T>? = null
)

@Serializable
data class EntityDelta<T>(
    val added: List<T> = emptyList(),
    val updated: List<T> = emptyList(),
    val removedIds: List<String> = emptyList()
)

@Serializable
data class EventNotification(val subscriptionId: String, val event: DomainEvent)

@Serializable
data class DomainEvent(
    val type: String,
    val workspaceId: String,
    val id: String,
    val timestamp: String,
    val actor: EventActor,
    val data: JsonObject
)

@Serializable
data class EventActor(val type: String, val id: String? = null, val name: String? = null)
