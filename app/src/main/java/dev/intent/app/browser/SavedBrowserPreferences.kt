package dev.intent.app.browser

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal class SavedBrowserPreferences(context: Context, connectionId: String, workspaceId: String) :
    SavedBrowserPersistence {
    private val preferences = context.getSharedPreferences("workspace-browsers", Context.MODE_PRIVATE)
    private val key = browserStorageKey(connectionId, workspaceId)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedBrowser.serializer())

    override fun load(): List<SavedBrowser> = preferences.getString(key, null)?.let {
        json.decodeFromString(serializer, it)
    }.orEmpty()

    override fun save(browsers: List<SavedBrowser>) {
        check(preferences.edit().putString(key, json.encodeToString(serializer, browsers)).commit())
    }
}

internal fun browserStorageKey(connectionId: String, workspaceId: String): String {
    require(connectionId.isNotBlank() && workspaceId.isNotBlank())
    return "${connectionId.length}:$connectionId${workspaceId.length}:$workspaceId"
}
