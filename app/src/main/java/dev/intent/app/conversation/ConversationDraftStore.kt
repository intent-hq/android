package dev.intent.app.conversation

import android.content.Context
import java.security.MessageDigest

/** Private, backup-excluded text only. Keys contain no connection credentials. */
class ConversationDraftStore(context: Context, connectionScope: String) {
    private val preferences = context.getSharedPreferences(
        "conversation-drafts",
        Context.MODE_PRIVATE
    )
    private val scope = connectionScope

    fun draft(workspaceId: String, agentId: String): ComposerDraft {
        require(scope.isNotBlank())
        val key = listOf(scope, workspaceId, agentId).joinToString("") { "${it.length}:$it" }
            .toByteArray().let { MessageDigest.getInstance("SHA-256").digest(it) }
            .joinToString("") { "%02x".format(it) }
        return ComposerDraft(preferences.getString(key, "").orEmpty()) { text ->
            val editor = preferences.edit()
            if (text.isEmpty()) editor.remove(key) else editor.putString(key, text)
            editor.apply()
        }
    }
}
