package dev.intent.app.mermaid

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MermaidDocumentTest {
    @Test
    fun `source is a JSON argument including hostile markup and streaming newlines`() {
        val source = "flowchart TD\n A[\"</script><script>alert('x')</script>\"]\\\n"
        val script = mermaidScript(source, true)
        val argument = script.removePrefix("window.renderMermaid(").removeSuffix(")")
        val payload = Json.parseToJsonElement(argument).jsonObject
        assertEquals(source, payload.getValue("source").jsonPrimitive.content)
        assertEquals("true", payload.getValue("dark").jsonPrimitive.content)
    }

    @Test
    fun `only exact bundled asset URLs are allowed`() {
        for (name in listOf("index.html", "renderer.css", "renderer.js", "pele.bundle.js")) {
            assertEquals(name, mermaidAsset(MERMAID_ORIGIN + name))
        }
        for (url in listOf(
            "https://example.com/renderer.js",
            "file:///etc/hosts",
            "content://secrets",
            "${MERMAID_ORIGIN}../secret",
            "${MERMAID_PAGE}?source=secret",
            "https://mermaid.intent.invalid.evil/index.html",
            "${MERMAID_ORIGIN}%69ndex.html"
        )) {
            assertNull(url, mermaidAsset(url))
        }
    }

    @Test
    fun `successful render uses a bounded height and errors request raw code fallback`() {
        assertEquals(120, mermaidHeight("{\"height\":120}"))
        assertEquals(MAX_DIAGRAM_HEIGHT, mermaidHeight("{\"height\":9000}"))
        for (reply in listOf(
            "null",
            "{}",
            "garbage",
            "{\"error\":true}",
            "{\"height\":-1}",
            "{\"height\":\"Infinity\"}"
        )) {
            assertNull(reply, mermaidHeight(reply))
        }
    }
}
