package dev.intent.app.navigation

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class HostAuthorityStore(private val requestOwner: () -> CompletableFuture<Boolean>) :
    Closeable {
    private val mutableOwner = MutableStateFlow(false)
    val owner = mutableOwner.asStateFlow()
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun setConnected(connected: Boolean) {
        if (closed) return
        val token = ++generation
        mutableOwner.value = false
        if (connected) {
            requestOwner().whenComplete { owner, error ->
                synchronized(this) {
                    if (!closed && generation == token) {
                        mutableOwner.value = error == null && owner == true
                    }
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        generation++
        mutableOwner.value = false
    }
}
