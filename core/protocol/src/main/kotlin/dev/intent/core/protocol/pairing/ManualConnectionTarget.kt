package dev.intent.core.protocol.pairing

import java.util.Base64

private const val MINIMUM_BLOB_BYTES = 37
private const val MINIMUM_MAP_PREFIX = 0xa1
private const val MAXIMUM_MAP_PREFIX = 0xa4
private val publicKeyHeader = byteArrayOf(0x61, 0x70, 0x58, 0x20)

/** Matches iOS's v0.4.0 blob classifier; the tunnel library validates the full contents. */
internal fun isTailcatAddress(value: String): Boolean {
    if (!value.startsWith("tc")) return false
    val encoded = value.drop(2)
    if (encoded.isEmpty() || !encoded.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-_"
        }
    ) return false
    val bytes = runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull() ?: return false
    return bytes.size >= MINIMUM_BLOB_BYTES &&
        (bytes[0].toInt() and 0xff) in MINIMUM_MAP_PREFIX..MAXIMUM_MAP_PREFIX &&
        publicKeyHeader.indices.all { bytes[it + 1] == publicKeyHeader[it] }
}
