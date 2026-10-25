package dev.intent.app.mermaid

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val MERMAID_ORIGIN = "https://mermaid.intent.invalid/"
internal const val MERMAID_PAGE = "${MERMAID_ORIGIN}index.html"
internal const val MAX_DIAGRAM_HEIGHT = 600
internal const val MAX_SOURCE_LENGTH = 50_000

internal fun mermaidAsset(url: String): String? = url.removePrefix(MERMAID_ORIGIN).takeIf {
    url.startsWith(MERMAID_ORIGIN) &&
        it in setOf(
            "index.html",
            "renderer.css",
            "renderer.js",
            "pele.bundle.js"
        )
}

internal fun mermaidScript(source: String, dark: Boolean): String {
    val payload =
        JsonObject(mapOf("source" to JsonPrimitive(source), "dark" to JsonPrimitive(dark)))
    return "window.renderMermaid($payload)"
}

/** A missing or malformed reply uses the same literal-code fallback as a parse error. */
internal fun mermaidHeight(reply: String): Int? = try {
    val result = Json.parseToJsonElement(reply).jsonObject
    val height = result["height"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it > 0 }
    if (result["error"]?.jsonPrimitive?.booleanOrNull == true ||
        height == null
    ) {
        null
    } else {
        height.coerceIn(1.0, MAX_DIAGRAM_HEIGHT.toDouble()).toInt()
    }
} catch (_: IllegalArgumentException) {
    null
}
