package dev.intent.app.assistant

import dev.intent.core.protocol.models.ContentBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ProposalItem(
    val id: String,
    val title: String,
    val selected: Boolean,
    val disabled: Boolean
)

data class ActionProposal(
    val kind: String,
    val payload: JsonObject,
    val preview: JsonObject,
    val id: String
) {
    val title: String get() = preview.text("title").orEmpty()
    val summary: String? get() = preview.text("summary")
    val warnings: List<String> get() = (preview["warnings"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    val method: String? get() = if (kind == "bulk-op") {
        when (payload.text("operation")) {
            "workspace.bulkArchive" -> "workspace.archive"
            "workspace.bulkDelete" -> "workspace.delete"
            else -> null
        }
    } else {
        null
    }
    val creation: Boolean get() = kind == "workspace-create" &&
        payload.text("operation") == "workspace.create"
    val items: List<ProposalItem> get() {
        val previews = (preview["bulkItems"] as? JsonArray).orEmpty().map { it.objectValue() }
        return (payload["ids"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        }.distinct().map { id ->
            val row = previews.firstOrNull { it.text("id") == id } ?: JsonObject(emptyMap())
            ProposalItem(
                id,
                row.text("title") ?: id,
                (row["selected"] as? JsonPrimitive)?.booleanOrNull != false,
                (row["disabled"] as? JsonPrimitive)?.booleanOrNull == true
            )
        }
    }
    val initialSelection: Set<String> get() = items.filter {
        it.selected && !it.disabled
    }.map { it.id }.toSet()
    val creationParams: JsonObject get() = payload["params"].objectValue()
    private val creationPreview: JsonObject get() = preview["workspaceCreate"].objectValue()
    val creationTitle: String get() = creationParams.text("title")
        ?: creationPreview.text("title").orEmpty()
    val prompt: String get() = creationParams["initialAgent"].objectValue().text("prompt")
        ?: creationPreview.text("initialPrompt")
        ?: listOf("initialMessage", "initialPrompt", "prompt", "message")
            .firstNotNullOfOrNull { creationParams.text(it) }.orEmpty()
    val repository: String get() = creationParams.text("githubUrl")
        ?: creationParams.text("repositoryPath")
        ?: if (creationPreview.text("mode") == "sibling") {
            ""
        } else {
            creationPreview.text("githubUrl") ?: creationPreview.text("repoPath").orEmpty()
        }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth") // iOS request fallbacks.
    fun creationRequest(title: String, prompt: String, key: String): JsonObject {
        require(repository.isNotBlank()) {
            "This proposal has no repository. Ask the agent to propose it again."
        }
        val params = creationParams.toMutableMap()
        val sibling = creationPreview.text("mode") == "sibling"
        if (!sibling) {
            mapOf(
                "githubUrl" to "githubUrl",
                "repositoryPath" to "repoPath",
                "clonePath" to "clonePath",
                "isNewRepo" to "isNewRepo",
                "scope" to "scope"
            ).forEach { (target, source) ->
                if (target !in params) creationPreview[source]?.let { params[target] = it }
            }
        }
        if (!params["githubUrl"]?.jsonPrimitive?.contentOrNull.isNullOrBlank() &&
            params["clonePath"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
        ) {
            params.remove("repositoryPath")
        }
        if (title.isBlank()) {
            params.remove("title")
        } else {
            params["title"] =
                JsonPrimitive(title.trim())
        }
        if (params["idempotencyKey"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
            params["idempotencyKey"] =
                JsonPrimitive(key)
        }
        val agent = params["initialAgent"].objectValue().toMutableMap()
        agent.remove("agentId")
        if (prompt.isBlank()) {
            agent.remove("prompt")
        } else {
            agent["prompt"] =
                JsonPrimitive(prompt.trim())
        }
        val specialist =
            agent["specialist"] ?: creationPreview["specialist"] ?: params["specialist"]
        specialist?.let { agent["specialist"] = it }
        agent.putIfAbsent("name", JsonPrimitive("Agent"))
        agent.putIfAbsent("agentType", JsonPrimitive("workspace"))
        agent["metadata"] = buildJsonObject {
            agent["metadata"].objectValue().forEach { (key, value) -> put(key, value) }
            put("isInitialAgent", true)
            specialist?.let { put("specialist", it) }
        }
        params["initialAgent"] = JsonObject(agent)
        return JsonObject(params)
    }

    companion object {
        fun parse(block: ContentBlock): ActionProposal? {
            if (block.type != "resource" ||
                block.resource?.mimeType != "application/vnd.intent.proposal+json"
            ) {
                return null
            }
            return runCatching {
                val row = Json.parseToJsonElement(block.resource?.text.orEmpty()).jsonObject
                val preview = row["preview"]!!.jsonObject
                ActionProposal(
                    row["kind"]!!.jsonPrimitive.content,
                    row["payload"]!!.jsonObject,
                    preview,
                    row.text("applyToolCallId") ?: preview["title"]!!.jsonPrimitive.content
                )
            }.getOrNull()
        }
    }
}
