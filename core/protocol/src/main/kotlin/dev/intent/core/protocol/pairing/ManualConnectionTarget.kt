package dev.intent.core.protocol.pairing

import java.util.Base64

private const val MINIMUM_BLOB_BYTES = 37
private const val MINIMUM_MAP_PREFIX = 0xa1
private const val MAXIMUM_MAP_PREFIX = 0xa4
private const val BYTE_MASK = 0xff
private const val TAILCAT_PREFIX = "tc"
private val encodedAddress = Regex("[A-Za-z0-9_-]+")

@Suppress("MagicNumber") // CBOR public-key field header from the Tailcat wire format.
private val publicKeyHeader = byteArrayOf(0x61, 0x70, 0x58, 0x20)

/** Matches iOS's v0.4.0 blob classifier; the tunnel library validates the full contents. */
internal fun isTailcatAddress(value: String): Boolean {
    if (!value.startsWith(TAILCAT_PREFIX)) return false
    val encoded = value.drop(TAILCAT_PREFIX.length)
    val bytes = runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull()
    return encodedAddress.matches(encoded) && bytes?.let(::isTailcatBlob) == true
}

private fun isTailcatBlob(bytes: ByteArray): Boolean = bytes.size >= MINIMUM_BLOB_BYTES &&
    (bytes[0].toInt() and BYTE_MASK) in MINIMUM_MAP_PREFIX..MAXIMUM_MAP_PREFIX &&
    publicKeyHeader.indices.all { bytes[it + 1] == publicKeyHeader[it] }
