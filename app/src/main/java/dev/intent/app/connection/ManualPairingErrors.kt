package dev.intent.app.connection

import dev.intent.core.protocol.CertificateFingerprint
import dev.intent.core.protocol.pairing.ManualPairingInput

internal data class ManualPairingErrors(
    val host: String?,
    val port: String?,
    val token: String?,
    val fingerprint: String?
) {
    val isValid: Boolean get() = listOf(host, port, token, fingerprint).all { it == null }
}

internal fun manualPairingErrors(input: ManualPairingInput): ManualPairingErrors =
    ManualPairingErrors(
        host = if (runCatching {
                input.copy(port = "443", token = "validation").toPayload()
            }.isFailure
        ) {
            "Enter a host name or IP address."
        } else {
            null
        },
        port = if (runCatching {
                input.copy(host = "desktop", token = "validation").toPayload()
            }.isFailure
        ) {
            "Enter a port from 1 to 65535."
        } else {
            null
        },
        token = if (runCatching {
                input.copy(host = "desktop", port = "443").toPayload()
            }.isFailure
        ) {
            "Enter the pairing token from your desktop."
        } else {
            null
        },
        fingerprint = if (input.certFingerprint.isNotBlank() &&
            runCatching { CertificateFingerprint.normalize(input.certFingerprint.trim()) }.isFailure
        ) {
            "Enter 64 hexadecimal digits, with optional colons."
        } else {
            null
        }
    )
