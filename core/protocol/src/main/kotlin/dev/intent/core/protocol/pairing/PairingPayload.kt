package dev.intent.core.protocol.pairing

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import kotlinx.serialization.Serializable

/** Untrusted pairing input. A missing fingerprint requires explicit certificate verification before saving. */
@Serializable
data class PairingPayload(
    val hosts: List<String>,
    val port: Int,
    val token: String,
    val certFingerprint: String? = null,
    val tcAddress: String? = null
) {
    init {
        require(port in 1..MAX_PORT) { "Invalid pairing port" }
        require(token.isNotBlank() && token.none { it.isISOControl() }) { "Invalid pairing token" }
        require(hosts.all(::usableHost)) { "Invalid pairing host" }
        require(
            tcAddress == null ||
                (
                    tcAddress.isNotBlank() &&
                        tcAddress.none {
                            it.isWhitespace() || it.isISOControl()
                        }
                    )
        ) {
            "Invalid tunnel address"
        }
        require(hosts.isNotEmpty() || tcAddress != null) {
            "Pairing needs a host or tunnel address"
        }
    }

    override fun toString(): String = "PairingPayload(redacted)"

    companion object {
        private const val MAX_PORT = 65535
        private val uniqueKeys = setOf("host", "port", "token", "fp", "certFingerprint", "tc", "v")

        /** QR codes contain the same URI as deep links. Unknown parameters remain forward-compatible. */
        fun parse(code: String): PairingPayload? = try {
            parseUri(URI(code))
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: URISyntaxException) {
            null
        }

        private fun parseUri(uri: URI): PairingPayload? {
            require(uri.scheme == "intent" && uri.rawAuthority == "pair")
            require(uri.rawFragment == null && uri.path in listOf("", "/"))
            val pairs = requireNotNull(uri.rawQuery).split('&').map {
                val parts = it.split('=', limit = 2)
                decode(parts[0]) to decode(parts.getOrElse(1) { "" })
            }
            require(uniqueKeys.none { key -> pairs.count { it.first == key } > 1 })
            val values = pairs.toMap()
            require(values["v"] == null || values["v"] == "1")
            return PairingPayload(
                hosts =
                values["host"].orEmpty().takeIf { it.isNotEmpty() }?.split(',') ?: emptyList(),
                port = requireNotNull(values["port"]?.toIntOrNull()),
                token = requireNotNull(values["token"]),
                certFingerprint = listOf(values["fp"], values["certFingerprint"]).firstOrNull {
                    !it.isNullOrEmpty()
                },
                tcAddress = values["tc"]?.takeIf { it.isNotEmpty() }
            )
        }

        // URI query values use percent encoding; a literal plus is not form-encoded whitespace.
        private fun decode(value: String): String =
            URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    }
}

private const val MAX_HOST_LENGTH = 253
private const val MAX_LABEL_LENGTH = 63
private const val IPV4_OCTETS = 4
private const val MAX_OCTET = 255

private fun usableHost(host: String): Boolean = when {
    ':' in host -> usableIpv6(host)
    host.isEmpty() || host.length > MAX_HOST_LENGTH -> false
    host.all { it.isDigit() || it == '.' } -> usableIpv4(host)
    else -> host.split('.').all(::usableLabel)
}

private fun usableIpv6(host: String): Boolean {
    val literal = if (host.startsWith('[') && host.endsWith(']')) host.drop(1).dropLast(1) else host
    if (!literal.all { it in "0123456789abcdefABCDEF:" }) return false
    return runCatching { InetAddress.getByName(literal) is Inet6Address }.getOrDefault(false)
}

private fun usableIpv4(host: String): Boolean {
    val labels = host.split('.')
    return labels.size == IPV4_OCTETS &&
        labels.all {
            it.toIntOrNull() in 0..MAX_OCTET && (it == "0" || !it.startsWith('0'))
        }
}

private fun usableLabel(label: String): Boolean = label.length in 1..MAX_LABEL_LENGTH &&
    !label.startsWith('-') &&
    !label.endsWith('-') &&
    label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "_-" }

/** Manual entry shares all validation with QR and deep-link inputs. */
data class ManualPairingInput(
    val host: String,
    val port: String,
    val token: String,
    val certFingerprint: String = "",
    val tcAddress: String = ""
) {
    fun toPayload(): PairingPayload = PairingPayload(
        hosts =
        host.trim().takeIf {
            it.isNotEmpty()
        }?.split(',')?.map(String::trim) ?: emptyList(),
        port = requireNotNull(port.trim().toIntOrNull()) { "Invalid pairing port" },
        token = token.trim(),
        certFingerprint = certFingerprint.trim().takeIf { it.isNotEmpty() },
        tcAddress = tcAddress.trim().takeIf { it.isNotEmpty() }
    )

    override fun toString(): String = "ManualPairingInput(redacted)"
}
