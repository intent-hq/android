package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable

@Serializable
data class TaskUpdateParams(
    val workspaceId: String,
    val noteId: String,
    val line: Int,
    val status: String,
    val expected: String? = null
)

@Serializable
data class TaskUpdateResult(
    val ok: Boolean,
    val noteId: String,
    val lineNumber: Int,
    val previousText: String,
    val newText: String,
    val status: String
)
