package dev.intent.app.navigation

import java.io.IOException
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostAuthorityStoreTest {
    @Test
    fun failedOwnershipReadRecoversOnSameSessionReconnect() {
        val reads = mutableListOf<CompletableFuture<Boolean>>()
        val store = HostAuthorityStore { CompletableFuture<Boolean>().also(reads::add) }
        store.setConnected(true)
        reads.single().completeExceptionally(IOException("transport lost"))
        assertFalse(store.owner.value)
        store.setConnected(false)
        store.setConnected(true)
        reads.last().complete(true)
        assertTrue(store.owner.value)
    }

    @Test
    fun obsoleteOwnerReplyCannotGrantAccessAfterDisconnectOrNewAdmission() {
        val reads = mutableListOf<CompletableFuture<Boolean>>()
        val store = HostAuthorityStore { CompletableFuture<Boolean>().also(reads::add) }
        store.setConnected(true)
        store.setConnected(false)
        store.setConnected(true)
        reads.first().complete(true)
        assertFalse(store.owner.value)
        reads.last().complete(false)
        assertFalse(store.owner.value)
    }

    @Test
    fun closedAuthorityCannotBeRestoredByPendingRead() {
        val read = CompletableFuture<Boolean>()
        val store = HostAuthorityStore { read }
        store.setConnected(true)
        store.close()
        read.complete(true)
        store.setConnected(true)
        assertFalse(store.owner.value)
    }
}
