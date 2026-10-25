package dev.intent.tailcat

import java.io.IOException

/** Only sanitized native error text is eligible for route logging. */
class NativeTunnelException(detail: String, credential: String) :
    IOException("Native tunnel failed") {
    val diagnosticDetail: String =
        detail.let { if (credential.isEmpty()) it else it.replace(credential, "[redacted]") }
            .replace(Regex("tc[A-Za-z0-9_+/=-]{16,}"), "[redacted]")
            .replace(Regex("(?i)(token|credential|secret|key)=[^\\s,;]+"), "$1=[redacted]")
            .replace(Regex("[A-Fa-f0-9]{64,}"), "[redacted]")
            .replace('\n', ' ').replace('\r', ' ')
}
