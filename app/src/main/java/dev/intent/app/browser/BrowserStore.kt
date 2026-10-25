package dev.intent.app.browser

import dev.intent.core.protocol.browser.BrowserTunnel
import dev.intent.core.protocol.browser.PreviewUrl
import dev.intent.core.protocol.session.RpcSession
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrowserState(
    val url: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val revision: Long = 0
)

class BrowserStore(
    private val openTunnel: () -> CompletableFuture<BrowserTunnel>,
    private val closeTunnel: (BrowserTunnel) -> Unit
) : Closeable {
    constructor(session: RpcSession) : this(session::openBrowser, session::closeBrowser)
    private val mutableState = MutableStateFlow(BrowserState())
    val state = mutableState.asStateFlow()
    private var tunnel: BrowserTunnel? = null
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun open(value: String) {
        if (closed) return
        val ticket = ++generation
        mutableState.value = BrowserState(loading = true, revision = ticket)
        try {
            tunnel?.let(closeTunnel)
            tunnel = null
            PreviewUrl.parse(value)
            openTunnel().whenCompleteAsync { proxy, error -> resolved(ticket, value, proxy, error) }
        } catch (_: Exception) {
            failed(ticket)
        }
    }

    @Synchronized
    private fun resolved(ticket: Long, value: String, proxy: BrowserTunnel?, error: Throwable?) {
        if (closed || ticket != generation) {
            proxy?.let(closeTunnel)
        } else if (error != null || proxy == null) {
            failed(ticket)
        } else {
            tunnel = proxy
            proxy.forward(value).whenCompleteAsync { url, failure ->
                forwarded(ticket, url, failure)
            }
        }
    }

    @Synchronized
    private fun forwarded(ticket: Long, url: String?, error: Throwable?) {
        if (closed || ticket != generation) return
        if (error != null) {
            failed(ticket)
        } else {
            mutableState.value = BrowserState(url = url, loading = true, revision = ticket)
        }
    }

    @Synchronized
    fun loaded(ticket: Long) {
        if (!closed && ticket == generation) mutableState.value = state.value.copy(loading = false)
    }

    @Synchronized
    fun failed(ticket: Long) {
        if (!closed && ticket == generation) {
            mutableState.value = state.value.copy(
                url = null,
                loading = false,
                error = "Cannot load this preview. Check the server URL and connection, then retry."
            )
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        generation++
        tunnel?.let(closeTunnel)
        tunnel = null
    }
}
