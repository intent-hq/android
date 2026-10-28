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
    val revision: Long = 0,
    val requestedUrl: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val resolvedRevision: Long = 0
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
        mutableState.value =
            state.value.copy(loading = true, error = null, revision = ticket, requestedUrl = value)
        try {
            PreviewUrl.parse(value)
            val pending = tunnel?.let { CompletableFuture.completedFuture(it) } ?: openTunnel()
            pending.whenCompleteAsync { proxy, error -> resolved(ticket, value, proxy, error) }
        } catch (_: Exception) {
            failed(ticket)
        }
    }

    @Synchronized
    private fun resolved(ticket: Long, value: String, proxy: BrowserTunnel?, error: Throwable?) {
        if (closed || ticket != generation) {
            proxy?.takeUnless { it === tunnel }?.let(closeTunnel)
        } else if (error != null || proxy == null) {
            failed(ticket, TUNNEL_UNAVAILABLE)
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
            tunnel?.let(closeTunnel)
            tunnel = null
            failed(ticket, TUNNEL_UNAVAILABLE)
        } else {
            mutableState.value =
                state.value.copy(url = url, loading = true, resolvedRevision = ticket)
        }
    }

    @Synchronized
    fun navigated(ticket: Long, address: String, canGoBack: Boolean, canGoForward: Boolean) {
        if (!closed && ticket == generation) {
            mutableState.value = state.value.copy(
                requestedUrl = address,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    @Synchronized
    fun loading(ticket: Long) {
        if (!closed && ticket == generation) mutableState.value = state.value.copy(loading = true)
    }

    @Synchronized
    fun loaded(ticket: Long) {
        if (!closed && ticket == generation) mutableState.value = state.value.copy(loading = false)
    }

    @Synchronized
    fun failed(ticket: Long, message: String = PREVIEW_UNAVAILABLE) {
        if (!closed && ticket == generation) {
            mutableState.value = state.value.copy(
                url = null,
                loading = false,
                error = message
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

private const val TUNNEL_UNAVAILABLE =
    "The browser tunnel is unavailable. Reconnect and update Intent on the host, then retry."

private const val PREVIEW_UNAVAILABLE =
    "Cannot load this preview. Check the server URL and connection, then retry."
