package dev.intent.core.protocol.browser

import java.net.URI

object PreviewUrl {
    private val daemonHosts =
        setOf("daemon.localhost", "localhost", "127.0.0.1", "::1", "[::1]", "0.0.0.0")
    private const val MAX_PORT = 65535
    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443
    fun parse(value: String): URI = URI(value).also {
        require(it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank())
        require(it.rawUserInfo == null && (it.port == -1 || it.port in 1..MAX_PORT))
    }
    fun remotePort(url: URI, localPorts: Set<Int> = emptySet()): Int? {
        parse(url.toASCIIString())
        if (url.host.lowercase() !in daemonHosts) return null
        val port = if (url.port == -1) {
            if (url.scheme.equals("https", true)) HTTPS_PORT else HTTP_PORT
        } else {
            url.port
        }
        return port.takeUnless { url.host == "127.0.0.1" && port in localPorts }
    }
    fun rewrite(url: URI, port: Int): URI {
        require(port in 1..MAX_PORT)
        // Preserve encoded path, query and fragment without double escaping.
        return URI(
            "${url.scheme}://127.0.0.1:$port" +
                (url.rawPath ?: "") + (url.rawQuery?.let { "?$it" } ?: "") +
                (url.rawFragment?.let { "#$it" } ?: "")
        )
    }
}
