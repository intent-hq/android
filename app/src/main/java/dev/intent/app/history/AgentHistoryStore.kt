package dev.intent.app.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Local visits only; identifiers are scoped to the saved connection. */
data class AgentVisit(
    val connectionId: String,
    val workspaceId: String,
    val agentId: String,
    val agentName: String,
    val workspaceTitle: String? = null,
    val visitedAt: Long = System.currentTimeMillis()
)

class AgentHistoryStore(read: () -> String? = { null }, private val write: (String) -> Unit = {}) {
    private val mutableEntries = MutableStateFlow(decode(read()).take(LIMIT))
    val entries = mutableEntries.asStateFlow()

    fun record(visit: AgentVisit) {
        val previous = entries.value.firstOrNull { it.sameAgent(visit) }
        save(
            (
                listOf(
                    visit.copy(workspaceTitle = visit.workspaceTitle ?: previous?.workspaceTitle)
                ) +
                    entries.value.filterNot { it.sameAgent(visit) }
                ).take(LIMIT)
        )
    }

    fun removeWorkspace(connectionId: String, workspaceId: String) = save(
        entries.value.filterNot { it.connectionId == connectionId && it.workspaceId == workspaceId }
    )

    fun visible(connectionIds: Set<String>): List<AgentVisit> =
        entries.value.filter { it.connectionId in connectionIds }

    fun clear() = save(emptyList())

    private fun save(entries: List<AgentVisit>) {
        write(
            JsonArray(
                entries.map {
                    buildJsonObject {
                        put("connectionId", it.connectionId)
                        put("workspaceId", it.workspaceId)
                        put("agentId", it.agentId)
                        put("agentName", it.agentName)
                        put("workspaceTitle", it.workspaceTitle)
                        put("visitedAt", it.visitedAt)
                    }
                }
            ).toString()
        )
        mutableEntries.value = entries
    }

    companion object {
        const val LIMIT = 30

        fun open(context: Context): AgentHistoryStore {
            val preferences = context.getSharedPreferences("agent-history", Context.MODE_PRIVATE)
            return AgentHistoryStore(
                { preferences.getString("visits", null) },
                { preferences.edit().putString("visits", it).apply() }
            )
        }

        private fun decode(value: String?): List<AgentVisit> = runCatching {
            value?.let {
                Json.parseToJsonElement(it).jsonArray.map { element ->
                    val row = element.jsonObject
                    AgentVisit(
                        row.getValue("connectionId").jsonPrimitive.content,
                        row.getValue("workspaceId").jsonPrimitive.content,
                        row.getValue("agentId").jsonPrimitive.content,
                        row.getValue("agentName").jsonPrimitive.content,
                        row["workspaceTitle"]?.jsonPrimitive?.contentOrNull,
                        row.getValue("visitedAt").jsonPrimitive.long
                    )
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}

private fun AgentVisit.sameAgent(other: AgentVisit): Boolean =
    connectionId == other.connectionId && agentId == other.agentId
