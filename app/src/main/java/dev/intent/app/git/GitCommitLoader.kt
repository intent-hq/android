package dev.intent.app.git

import dev.intent.core.protocol.models.GitBranchFile
import dev.intent.core.protocol.models.GitCommitDetails
import dev.intent.core.protocol.models.GitDiff
import dev.intent.core.protocol.models.GitFileContent
import dev.intent.core.protocol.models.GitRoot
import dev.intent.core.protocol.session.RpcSession
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class GitCommitLoader(
    private val session: RpcSession,
    private val workspaceId: String,
    private val root: GitRoot?
) {
    fun load(hash: String, path: String): CompletableFuture<List<GitDiff>> {
        val contents = if (root == null) primary(hash, path) else secondary(hash, path)
        return contents.thenApplyAsync { (old, new) ->
            listOf(GitDiff(path, buildGitHunks(old, new)))
        }
    }

    private fun primary(hash: String, path: String): CompletableFuture<Pair<String, String>> =
        session.request(
            "git.branchDiff",
            params {
                put("baseRef", "$hash^")
                put("targetRef", hash)
                put("paths", buildJsonArray { add(path) })
            },
            ListSerializer(GitBranchFile.serializer())
        ).thenCompose { files ->
            val file = files.firstOrNull { it.file == path }
            if (file != null) {
                CompletableFuture.completedFuture(file.oldContent to file.newContent)
            } else {
                content(path, hash).thenApply { "" to it }
            }
        }

    private fun secondary(hash: String, path: String): CompletableFuture<Pair<String, String>> =
        session.request(
            "git.commitDetails",
            params { put("commitHash", "$hash^") },
            GitCommitDetails.serializer()
        ).thenCompose { parent ->
            val old = if (parent.date.isEmpty()) {
                CompletableFuture.completedFuture(
                    ""
                )
            } else {
                content(path, "$hash^")
            }
            old.thenCombine(content(path, hash)) { before, after -> before to after }
        }

    private fun content(path: String, ref: String) = session.request(
        "git.showFile",
        params {
            put("filePath", path)
            put("ref", ref)
        },
        GitFileContent.serializer()
    ).thenApply { it.content }

    private fun params(block: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("workspaceId", workspaceId)
        root?.let { put("gitRootId", it.id) }
        block()
    }
}
