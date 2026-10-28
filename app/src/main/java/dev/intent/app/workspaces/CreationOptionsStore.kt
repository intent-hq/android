package dev.intent.app.workspaces

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CreationChoice(val id: String, val name: String, val selectable: Boolean = true)
class CreationOptionsStore(private val operations: WorkspaceOperations) {
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
}

private fun JsonObject.choice(): CreationChoice =
    CreationChoice(string("id"), runCatching { string("name") }.getOrDefault(string("id")))
