package dev.intent.app.git

import dev.intent.core.protocol.models.GitChangeMetadata
import dev.intent.core.protocol.models.GitCommitPage
import dev.intent.core.protocol.models.GitRoot
import dev.intent.core.protocol.models.GitStatus
import dev.intent.core.protocol.models.GitTrackedChange

internal fun enrichGitStatus(status: GitStatus, tracked: List<GitTrackedChange>): GitStatus {
    val metadata = tracked.groupBy { it.relativePath to (it.stage == "staged") }
    return status.copy(
        files = status.files.map { file ->
            val stage = if (file.staged) "staged" else "unstaged"
            val match = metadata[file.path to file.staged]?.firstOrNull { it.stage == stage }
            file.copy(
                metadata = match?.let {
                    GitChangeMetadata(it.stats, it.attribution?.agent?.agentName)
                }
            )
        }
    )
}

internal fun boundCommits(page: GitCommitPage, root: GitRoot): GitCommitPage {
    val boundary = page.items.indexOfFirst { it.hash == root.registeredCommitSha }
    return page.copy(
        items = if (boundary < 0) page.items else page.items.take(boundary),
        nextToken = if (boundary < 0) page.nextToken else null,
        boundarySha = root.registeredCommitSha
    )
}
