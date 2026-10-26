package dev.intent.app.git

import dev.intent.core.protocol.models.GitCommitDetails
import dev.intent.core.protocol.models.GitCommitFile
import dev.intent.core.protocol.models.GitCommitPage
import dev.intent.core.protocol.models.GitDiff
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitRoot
import dev.intent.core.protocol.models.GitRoots
import dev.intent.core.protocol.models.GitStatus
import dev.intent.core.protocol.models.GitTrackedChanges
import dev.intent.core.protocol.models.GitTrackedCommits
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface GitSession {
    fun roots(): CompletableFuture<List<GitRoot>>
    fun status(root: GitRoot? = null): CompletableFuture<GitStatus>
    fun commits(nextToken: String? = null, root: GitRoot? = null): CompletableFuture<GitCommitPage>
    fun diff(file: GitFileStatus, root: GitRoot? = null): CompletableFuture<List<GitDiff>>
    fun commitFiles(hash: String, root: GitRoot?): CompletableFuture<List<GitCommitFile>>
    fun commitDiff(hash: String, path: String, root: GitRoot?): CompletableFuture<List<GitDiff>>
    fun watch(onRefresh: () -> Unit, onError: (Throwable) -> Unit): Closeable
}

class RpcGitSession(private val session: RpcSession, private val workspaceId: String) : GitSession {
    override fun roots() =
        session.request("gitRoot.list", params(), GitRoots.serializer()).thenApply {
            it.gitRoots
        }

    override fun status(root: GitRoot?): CompletableFuture<GitStatus> {
        val tracked = if (root == null) {
            session.request(
                "file-tracking.getChanges",
                params(),
                GitTrackedChanges.serializer()
            ).thenApply {
                it.changes
            }
        } else {
            CompletableFuture.completedFuture(emptyList())
        }
        return tracked.thenCompose { changes ->
            session.request(
                "git.status",
                params(root) {
                    put("forceRefresh", true)
                },
                GitStatus.serializer()
            )
                .thenApply { enrichGitStatus(it, changes) }
        }
    }

    override fun commits(nextToken: String?, root: GitRoot?): CompletableFuture<GitCommitPage> {
        val request = params(root) {
            put("limit", PAGE_SIZE)
            nextToken?.let { put("nextToken", it) }
            if (root == null) put("includeOlder", false)
        }
        return if (root == null) {
            session.request(
                "file-tracking.loadCommits",
                request,
                GitTrackedCommits.serializer()
            ).thenApply {
                GitCommitPage(it.commits, it.nextToken, it.boundarySha)
            }
        } else {
            session.request("git.commits", request, GitCommitPage.serializer()).thenApply {
                boundCommits(it, root)
            }
        }
    }

    override fun diff(file: GitFileStatus, root: GitRoot?) = session.request(
        "git.diffs",
        params(root) {
            put("path", file.path)
            put("staged", file.staged)
        },
        ListSerializer(GitDiff.serializer())
    )

    override fun commitFiles(hash: String, root: GitRoot?) = session.request(
        "git.commitDetails",
        params(root) { put("commitHash", hash) },
        GitCommitDetails.serializer()
    ).thenApply { it.fileDetails }

    override fun commitDiff(
        hash: String,
        path: String,
        root: GitRoot?
    ): CompletableFuture<List<GitDiff>> =
        GitCommitLoader(session, workspaceId, root).load(hash, path)

    override fun watch(onRefresh: () -> Unit, onError: (Throwable) -> Unit): Closeable =
        session.subscribe(
            SubscriptionSpec(
                "events.subscribe",
                buildJsonObject {
                    put("workspaceId", workspaceId)
                    put(
                        "eventTypes",
                        buildJsonArray {
                            add("changes:tracked")
                            add("changes:git-status")
                            add("gitRoot:*")
                            add("git:*")
                        }
                    )
                },
                notificationMethod = "events.event"
            ),
            onMessage = { payload ->
                runCatching {
                    val event = payload["event"]?.jsonObject ?: payload
                    val data = event["data"] as? JsonObject
                    val owner = data?.get("workspaceId") ?: event["workspaceId"]
                    val type = event["type"]?.jsonPrimitive?.content.orEmpty()
                    val relevant = type == "changes:git-status" ||
                        type == "changes:tracked" ||
                        type.startsWith("git:") ||
                        type.startsWith("gitRoot:")
                    if (owner?.jsonPrimitive?.content == workspaceId &&
                        relevant
                    ) {
                        onRefresh()
                    }
                }.onFailure(onError)
            },
            onSubscribed = { onRefresh() },
            onError = onError
        )

    private fun params(
        root: GitRoot? = null,
        block: JsonObjectBuilder.() -> Unit = {
        }
    ) = buildJsonObject {
        put("workspaceId", workspaceId)
        root?.let { put("gitRootId", it.id) }
        block()
    }
}

private const val PAGE_SIZE = 50
