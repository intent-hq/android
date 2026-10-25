package dev.intent.app.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PairingUiStateStoreTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun persistsOnlyPresentationPreference() = runBlocking {
        val file = File(directory.root, "ui.preferences_pb")
        val job = Job()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(
                Dispatchers.IO + job
            )
        ) { file }
        val store = PairingUiStateStore(dataStore)
        assertFalse(store.manualEntryExpanded.first())
        store.setManualEntryExpanded(true)
        assertTrue(store.manualEntryExpanded.first())
        job.cancelAndJoin()
        val reloadJob = Job()
        try {
            val reloaded =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(
                        Dispatchers.IO + reloadJob
                    )
                ) { file }
            assertTrue(PairingUiStateStore(reloaded).manualEntryExpanded.first())
            assertTrue(
                reloaded.data.first().asMap().keys.all {
                    it.name ==
                        "manual_entry_expanded"
                }
            )
        } finally {
            reloadJob.cancelAndJoin()
        }
    }
}
