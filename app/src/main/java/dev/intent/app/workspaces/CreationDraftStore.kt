package dev.intent.app.workspaces

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Private connection-scoped draft, excluded from Android backup. */
class CreationDraftStore(context: Context, connectionScope: String) {
    private val key = MessageDigest.getInstance("SHA-256").digest(connectionScope.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val directory = File(context.noBackupFilesDir, "creation-drafts/$key").also {
        it.mkdirs()
    }
    private val file = File(directory, "draft.json")
    private val json = Json { ignoreUnknownKeys = true }
    fun load(): WorkspaceDraft = runCatching {
        json.decodeFromString<WorkspaceDraft>(file.readText())
    }.getOrDefault(WorkspaceDraft())

    @Synchronized
    fun save(draft: WorkspaceDraft) {
        val temporary = File(directory, "draft.tmp")
        temporary.writeText(json.encodeToString(draft))
        check(temporary.renameTo(file)) { "Cannot save creation draft" }
    }
}
