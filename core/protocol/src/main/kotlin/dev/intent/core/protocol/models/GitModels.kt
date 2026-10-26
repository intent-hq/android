package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class GitFileStatus(
    val path: String,
    val status: String,
    val staged: Boolean,
    val mode: String? = null,
    val oldSha: String? = null,
    val newSha: String? = null,
    @Transient val metadata: GitChangeMetadata? = null
)

@Serializable
data class GitStatus(
    val branch: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val diverged: Boolean = false,
    val files: List<GitFileStatus> = emptyList(),
    val hasUncommittedChanges: Boolean = false,
    val hasUntrackedFiles: Boolean = false,
    val filesTruncated: Boolean = false,
    val totalFiles: Int? = null,
    val hasUpstream: Boolean = false,
    val unpushedCount: Int? = null
)

@Serializable
data class GitDiff(val path: String, val hunks: List<GitHunk> = emptyList())

@Serializable
data class GitHunk(
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<GitDiffLine> = emptyList()
)

@Serializable
data class GitDiffLine(
    val type: String,
    val content: String,
    val oldNumber: Int? = null,
    val newNumber: Int? = null
)

@Serializable
data class GitCommit(
    val hash: String,
    val sha: String = hash.take(SHORT_HASH_LENGTH),
    val author: String = "",
    val email: String = "",
    val date: String = "",
    val message: String = "",
    val agentId: String? = null,
    val linkedNoteId: String? = null,
    val isPushed: Boolean? = null
)

@Serializable
data class GitCommitPage(
    val items: List<GitCommit> = emptyList(),
    val nextToken: String? = null,
    val boundarySha: String? = null
)

private const val SHORT_HASH_LENGTH = 7
