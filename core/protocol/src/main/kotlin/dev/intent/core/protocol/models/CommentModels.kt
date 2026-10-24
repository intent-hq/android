package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Comment(
    val id: String,
    val threadId: String,
    val type: String,
    val content: String,
    val author: String,
    val authorType: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val noteId: String? = null,
    val parentId: String? = null,
    val authorPrincipalId: String? = null,
    val authorIdentity: JsonObject? = null,
    val anchor: JsonObject? = null,
    val anchorText: String? = null,
    val anchorContext: JsonObject? = null,
    val suggestionDiff: JsonObject? = null,
    val agentId: String? = null,
    val isOrphaned: Boolean? = null
)

@Serializable
data class CommentThreadSummary(
    val threadId: String,
    val noteId: String,
    val status: String,
    val createdAt: String,
    val lastActivity: String,
    val latestCommentAuthor: String,
    val latestCommentAuthorType: String,
    val latestCommentAt: String,
    val commentCount: Int,
    val targetedText: String? = null,
    val anchorId: String? = null,
    val latestCommentAuthorPrincipalId: String? = null,
    val latestCommentAuthorIdentity: JsonObject? = null,
    val comments: List<Comment>? = null
)

@Serializable
data class CommentListParams(
    val workspaceId: String,
    val noteId: String,
    val since: String? = null,
    val authorType: String? = null,
    val status: String? = null,
    val includeComments: Boolean? = null
)

@Serializable
data class CommentListResult(
    val threads: List<CommentThreadSummary>,
    val totalThreads: Int,
    val totalComments: Int
)

@Serializable
data class CommentGetThreadParams(
    val workspaceId: String,
    val noteId: String,
    val threadId: String? = null,
    val commentId: String? = null
)

@Serializable
data class CommentThreadResult(
    val threadId: String,
    val noteId: String,
    val rootComment: Comment,
    val replies: List<Comment>,
    val totalComments: Int,
    val status: String
)
