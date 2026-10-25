package dev.intent.app.git

import dev.intent.core.protocol.models.GitCommitPage
import dev.intent.core.protocol.models.GitDiff
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitStatus
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface GitSession {
    fun status(): CompletableFuture<GitStatus>
    fun commits(nextToken: String? = null): CompletableFuture<GitCommitPage>
    fun diff(file: GitFileStatus): CompletableFuture<List<GitDiff>>
    fun watch(onRefresh: () -> Unit, onError: (Throwable) -> Unit): Closeable
}

class RpcGitSession(private val session: RpcSession, private val workspaceId: String) : GitSession {
    override fun status() = session.request("git.status", params(), GitStatus.serializer())

    override fun commits(nextToken: String?) = session.request(
        "git.commits",
        buildJsonObject {
            put("workspaceId", workspaceId)
            put("limit", PAGE_SIZE)
            nextToken?.let { put("nextToken", it) }
        },
        GitCommitPage.serializer()
    )

    override fun diff(file: GitFileStatus) = session.request(
        "git.diffs",
        buildJsonObject {
            put("workspaceId", workspaceId)
            put("path", file.path)
            put("staged", file.staged)
        },
        ListSerializer(GitDiff.serializer())
    )

    override fun watch(onRefresh: () -> Unit, onError: (Throwable) -> Unit): Closeable =
        session.subscribe(
            SubscriptionSpec(
                "events.subscribe",
                buildJsonObject {
                    put("workspaceId", workspaceId)
                    put(
                        "eventTypes",
                        buildJsonArray {
                            add("changes:git-status")
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
                    val relevant = type == "changes:git-status" || type.startsWith("git:")
                    if (owner?.jsonPrimitive?.content == workspaceId &&
                        data?.get("gitRootId") == null &&
                        relevant
                    ) {
                        onRefresh()
                    }
                }.onFailure(onError)
            },
            onSubscribed = { onRefresh() },
            onError = onError
        )

    private fun params() = buildJsonObject { put("workspaceId", workspaceId) }
}

private const val PAGE_SIZE = 50
