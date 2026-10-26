package dev.intent.core.protocol.models

import kotlinx.serialization.Serializable

@Serializable
data class GitRoot(
    val id: String,
    val path: String,
    val repoName: String? = null,
    val branch: String? = null,
    val registeredCommitSha: String? = null
) {
    val displayName: String get() = repoName?.takeIf { it.isNotBlank() }
        ?: path.trimEnd('/').substringAfterLast('/')
}

@Serializable
data class GitRoots(val gitRoots: List<GitRoot>)

@Serializable
data class GitTrackedChanges(val changes: List<GitTrackedChange>, val truncated: Boolean)

@Serializable
data class GitTrackedChange(
    val relativePath: String,
    val stage: String,
    val stats: GitChangeStats,
    val attribution: GitChangeAttribution? = null
)

@Serializable
data class GitChangeStats(val additions: Int, val deletions: Int, val binary: Boolean = false)

@Serializable
data class GitChangeAttribution(val agent: GitChangeAgent? = null)

@Serializable
data class GitChangeAgent(val agentName: String? = null)

data class GitChangeMetadata(val stats: GitChangeStats, val agentName: String?)

@Serializable
data class GitTrackedCommits(
    val commits: List<GitCommit>,
    val nextToken: String? = null,
    val boundarySha: String? = null
)

@Serializable
data class GitCommitFile(val path: String, val additions: Int = 0, val deletions: Int = 0)

@Serializable
data class GitCommitDetails(
    val fileDetails: List<GitCommitFile> = emptyList(),
    val date: String = ""
)

@Serializable
data class GitBranchFile(val file: String, val oldContent: String, val newContent: String)

@Serializable
data class GitFileContent(val content: String)
