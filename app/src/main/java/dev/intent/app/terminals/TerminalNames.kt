package dev.intent.app.terminals

import android.content.SharedPreferences

/** Names belong to this device and a daemon boot, never to a reused PTY id. */
interface TerminalNames {
    fun get(bootId: String, terminalId: String): String?
    fun set(bootId: String, terminalId: String, name: String?)
}

internal class MemoryTerminalNames : TerminalNames {
    private val names = mutableMapOf<Pair<String, String>, String>()
    override fun get(bootId: String, terminalId: String): String? = names[bootId to terminalId]
    override fun set(bootId: String, terminalId: String, name: String?) {
        if (name == null) names.remove(bootId to terminalId) else names[bootId to terminalId] = name
    }
}

internal class SavedTerminalNames(
    private val preferences: SharedPreferences,
    private val workspaceId: String
) : TerminalNames {
    private fun key(bootId: String, terminalId: String) =
        listOf(workspaceId, bootId, terminalId).joinToString(":") { "${it.length}:$it" }

    override fun get(bootId: String, terminalId: String): String? =
        preferences.getString(key(bootId, terminalId), null)

    override fun set(bootId: String, terminalId: String, name: String?) {
        preferences.edit().putString(key(bootId, terminalId), name).apply()
    }
}
