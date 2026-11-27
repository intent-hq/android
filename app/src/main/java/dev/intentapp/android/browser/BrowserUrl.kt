package dev.intentapp.android.browser

import java.net.URI

/** iOS WorkspaceBrowserError.invalidURL copy. */
internal const val INVALID_BROWSER_URL =
    "Enter an http or https address without a username or password, such as localhost:3000."

private val schemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
private const val MAX_PORT = 65_535

/**
 * Mirrors iOS WorkspaceBrowserURL.normalize: trims, adds http:// when no scheme is typed, and
 * rejects non-web schemes, credentials, whitespace, backslashes, empty hosts and invalid ports.
 * Local host names stay as typed because Android's tunnel maps every loopback alias itself.
 */
internal fun normalizeBrowserUrl(input: String): String? {
    val trimmed = input.trim()
    val qualified = if (schemePrefix.containsMatchIn(trimmed)) trimmed else "http://$trimmed"
    val url = runCatching { URI(qualified) }.getOrNull()
    return url?.takeIf { trimmed.isNotEmpty() && trimmed.none(::isForbidden) && isWebAddress(it) }
        ?.takeUnless {
            qualified.substringAfter("://").takeWhile { c -> c !in "/?#" }.endsWith(":")
        }
        ?.let {
            "${it.scheme.lowercase()}://${it.rawAuthority.lowercase()}" + it.rawPath.orEmpty() +
                (it.rawQuery?.let { q -> "?$q" } ?: "") + (it.rawFragment?.let { f -> "#$f" } ?: "")
        }
}

private fun isForbidden(char: Char) = char.isWhitespace() || char.isISOControl() || char == '\\'

private fun isWebAddress(url: URI): Boolean {
    val host = url.host
    return url.scheme?.lowercase() in setOf("http", "https") &&
        !host.isNullOrEmpty() &&
        !host.contains('%') &&
        url.rawUserInfo == null &&
        (url.port == -1 || url.port in 1..MAX_PORT)
}
