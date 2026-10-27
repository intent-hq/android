package dev.intent.app.connection

import android.content.Context
import dev.intent.app.storage.createCredentialStore
import dev.intent.core.protocol.pairing.CredentialStore
import dev.intent.core.protocol.pairing.PairingPayload
import java.io.File
import java.util.UUID

/** Each immutable record gets its own encrypted payload. IDs contain no server or token data. */
data class SavedConnection(
    val id: String,
    val payload: PairingPayload,
    val metadata: ConnectionMetadata = ConnectionMetadata()
) {
    override fun toString(): String = "SavedConnection($id)"
}

interface ConnectionRepository {
    fun list(): List<SavedConnection>
    fun activeId(): String?
    fun select(id: String?)
    fun add(payload: PairingPayload): SavedConnection
    fun remove(id: String)
    fun updateMetadata(expected: SavedConnection, metadata: ConnectionMetadata): Boolean = false
    fun refresh(expected: SavedConnection, payload: PairingPayload): Boolean = false
}

class SavedConnections(
    private val ids: () -> List<String>,
    private val store: (String) -> CredentialStore,
    private val readActive: () -> String?,
    private val writeActive: (String?) -> Unit,
    private val readMetadata: (String) -> ConnectionMetadata = { ConnectionMetadata() },
    private val writeMetadata: (String, ConnectionMetadata?) -> Unit = { _, _ -> }
) : ConnectionRepository {
    override fun list(): List<SavedConnection> = ids().mapNotNull { id ->
        store(id).load()?.let { SavedConnection(id, it, readMetadata(id)) }
    }

    override fun activeId(): String? = readActive()
    override fun select(id: String?) = writeActive(id)

    override fun add(payload: PairingPayload): SavedConnection {
        val id = UUID.randomUUID().toString()
        store(id).save(payload)
        return SavedConnection(id, payload)
    }

    override fun refresh(expected: SavedConnection, payload: PairingPayload): Boolean {
        if (activeId() != expected.id || store(expected.id).load() != expected.payload) return false
        require(
            payload.token == expected.payload.token &&
                payload.certFingerprint == expected.payload.certFingerprint &&
                payload.port == expected.payload.port
        )
        store(expected.id).save(payload)
        return true
    }

    override fun updateMetadata(expected: SavedConnection, metadata: ConnectionMetadata): Boolean {
        if (activeId() != expected.id || store(expected.id).load() != expected.payload) return false
        writeMetadata(expected.id, metadata)
        return true
    }

    override fun remove(id: String) {
        writeMetadata(id, null)
        store(id).clear()
        if (activeId() == id) select(null)
    }

    companion object {
        fun open(context: Context): SavedConnections {
            val app = context.applicationContext
            val directory = File(app.noBackupFilesDir, "connections").apply { mkdirs() }
            val preferences = app.getSharedPreferences("active_connection", Context.MODE_PRIVATE)
            return SavedConnections(
                ids = {
                    directory.listFiles().orEmpty().map {
                        it.name.removeSuffix(".bak").removeSuffix(".enc")
                    }.distinct()
                        .filter { runCatching { UUID.fromString(it) }.isSuccess }.sorted()
                },
                store = { id ->
                    require(UUID.fromString(id).toString() == id)
                    createCredentialStore(app, "connections/$id.enc")
                },
                readActive = { preferences.getString("id", null) },
                readMetadata = { id ->
                    ConnectionMetadata(
                        preferences.getString("name.$id", null),
                        preferences.getLong("last.$id", 0L).takeIf { it > 0 }
                    )
                },
                writeMetadata = { id, value ->
                    check(
                        preferences.edit().putString("name.$id", value?.name)
                            .putLong("last.$id", value?.lastConnectedAt ?: 0L).commit()
                    )
                },
                writeActive = { id ->
                    check(preferences.edit().putString("id", id).commit()) {
                        "Cannot save the active connection"
                    }
                }
            )
        }
    }
}
