package dev.intent.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pairingUiDataStore by preferencesDataStore(name = "pairing_ui")

/** Only presentation preferences belong here. Server details and secrets stay in CredentialStore. */
class PairingUiStateStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.pairingUiDataStore)

    val manualEntryExpanded: Flow<Boolean> = dataStore.data.map {
        it[MANUAL_ENTRY_EXPANDED] ?: false
    }

    suspend fun setManualEntryExpanded(expanded: Boolean) {
        dataStore.edit { it[MANUAL_ENTRY_EXPANDED] = expanded }
    }

    private companion object {
        val MANUAL_ENTRY_EXPANDED = booleanPreferencesKey("manual_entry_expanded")
    }
}
