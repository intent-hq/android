package dev.intent.app.git

import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitCommitFile
import dev.intent.core.protocol.models.GitDiff
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitRoot
import dev.intent.core.protocol.models.GitStatus

data class GitState(
    val roots: List<GitRoot> = emptyList(),
    val selectedRoot: GitRoot? = null,
    val repositoryError: String? = null,
    val boundarySha: String? = null,
    val selectedCommit: GitCommit? = null,
    val commitFiles: List<GitCommitFile> = emptyList(),
    val commitLoading: Boolean = false,
    val commitError: String? = null,
    val status: GitStatus = GitStatus(),
    val commits: List<GitCommit> = emptyList(),
    val nextToken: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val selected: GitFileStatus? = null,
    val diffs: List<GitDiff> = emptyList(),
    val diffLoading: Boolean = false,
    val error: String? = null,
    val diffError: String? = null
)
