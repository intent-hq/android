package dev.intent.core.protocol

import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Pins the DER leaf certificate, not its public key. */
object CertificateFingerprint {
    private const val MAX_PORT = 65535
    fun of(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it) }

    fun normalize(value: String): String {
        val compact = value.replace(":", "").uppercase()
        require(compact.matches(Regex("[0-9A-F]{64}"))) {
            "A SHA-256 certificate fingerprint is required"
        }
        return compact.chunked(2).joinToString(":")
    }

    /** Blocking TLS-only probe. The caller must confirm and store the result before connecting. */
    fun probe(host: String, port: Int, timeoutMillis: Int = 4000): String {
        require(host.isNotBlank() && port in 1..MAX_PORT && timeoutMillis > 0)
        var fingerprint: String? = null
        val trust = LeafTrustManager { fingerprint = of(it) }
        Socket().use { tcp ->
            tcp.connect(InetSocketAddress(host, port), timeoutMillis)
            val socket = tlsContext(
                trust
            ).socketFactory.createSocket(tcp, host, port, true) as SSLSocket
            socket.use {
                it.soTimeout = timeoutMillis
                it.startHandshake()
            }
        }
        return checkNotNull(fingerprint) { "The server did not present a certificate" }
    }
}

internal class LeafTrustManager(private val verify: (X509Certificate) -> Unit) : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?): Unit =
        throw CertificateException("Client certificates are not supported")

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Missing server certificate")
        verify(leaf)
    }
}

internal fun tlsContext(trust: X509TrustManager): SSLContext =
    SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
