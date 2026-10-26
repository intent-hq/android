package dev.intent.app.workspaces

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CreationChoice(val id: String, val name: String, val selectable: Boolean = true)
data class CreationOptions(
    val providers: List<CreationChoice>,
    val specialists: List<CreationChoice>
)
data class WorkspaceDraft(
    val title: String,
    val repositoryPath: String = "",
    val baseRef: String = "",
    val prompt: String = "",
    val provider: String = "",
    val model: String = "",
    val specialist: String = ""
) {
    fun params(): JsonObject = buildJsonObject {
        require(title.isNotBlank()) { "Enter a workspace title" }
        require(model.isBlank() || provider.isNotBlank()) { "Choose a provider for the model" }
        put("title", title.trim())
        if (repositoryPath.isNotBlank()) put("repositoryPath", repositoryPath.trim())
        if (baseRef.isNotBlank()) put("baseRef", baseRef.trim())
        if (prompt.isNotBlank()) {
            put(
                "initialAgent",
                buildJsonObject {
                    put("prompt", prompt)
                    if (provider.isNotBlank()) put("provider", provider)
                    if (model.isNotBlank()) put("model", model)
                    if (specialist.isNotBlank()) put("specialist", specialist)
                }
            )
        }
    }
}

class CreationOptionsStore(private val operations: WorkspaceOperations) {
    fun load(): CreationOptions = CreationOptions(
        loadProviders(),
        operations.request("specialist.list").rows("specialists").map { it.choice() }
    )

    fun loadProviders(): List<CreationChoice> {
        val catalog = operations.request("providers.catalog").rows("providers")
        val settings = operations.request(
            "settings.get",
            buildJsonObject {
                put("path", "providers.enabled")
            }
        )
        require(settings.string("path") == "providers.enabled")
        val enabled = if (settings["value"] ==
            JsonNull
        ) {
            JsonObject(emptyMap())
        } else {
            settings.obj("value")
        }
        val authentication = operations.request("host.providerAuthStatus").rows("providers")
        return catalog.filter {
            it["visible"] == JsonPrimitive(true) &&
                (
                    it["canBeDisabled"] == JsonPrimitive(false) ||
                        enabled[it.string("id")] == JsonPrimitive(true)
                    )
        }.map { entry ->
            val choice = entry.choice()
            val auth = authentication.firstOrNull {
                it.string("id") == choice.id
            }?.get("authenticated")
            val suffix = when (auth) {
                JsonPrimitive(true) -> ""
                JsonPrimitive(false) -> " (sign in on host)"
                else -> " (sign-in status unknown)"
            }
            choice.copy(name = choice.name + suffix, selectable = auth != JsonPrimitive(false))
        }
    }

    fun models(provider: String): List<CreationChoice> = operations.request(
        "models.list",
        buildJsonObject { put("providerId", provider) }
    ).rows("models").map { it.choice() }

    fun create(draft: WorkspaceDraft): String =
        operations.request("workspace.create", draft.params()).obj("workspace").string("id")
}

private fun JsonObject.choice(): CreationChoice =
    CreationChoice(string("id"), runCatching { string("name") }.getOrDefault(string("id")))
