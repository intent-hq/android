package dev.intent.app.status

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Keep full trigger text only in the selected sheet, never in a list or event journal. */
internal fun scriptSummary(row: JsonObject): JsonObject {
    listOf(
        "monitorId",
        "workspaceId",
        "agentId",
        "scriptId",
        "runId",
        "createdAt",
        "expiresAt"
    ).forEach {
        require(!row.text(it).isNullOrEmpty()) { "Invalid script watch identity" }
    }
    require(row.text("scriptName") != null && row.text("mode") in setOf("command", "service"))
    require(row.text("state") == "active")
    row.number("lineCount")?.let { require(it in 1..MAX_LINES) }
    val fields = row.filterKeys { it != "outputPattern" }.toMutableMap()
    row.text("outputPattern")?.let { pattern ->
        var bytes = 0
        val preview = StringBuilder()
        pattern.codePoints().toArray().forEach { point ->
            val scalar = String(Character.toChars(point))
            val size = scalar.toByteArray(Charsets.UTF_8).size
            if (bytes + size <= PREVIEW_BYTES) preview.append(scalar)
            bytes += size
        }
        fields["outputPatternPreview"] = JsonPrimitive(preview.toString())
        fields["outputPatternTruncated"] = JsonPrimitive(bytes > PREVIEW_BYTES)
    }
    require(
        (fields["outputPatternPreview"] as? JsonPrimitive)?.content?.toByteArray(
            Charsets.UTF_8
        )?.size?.let {
            it <=
                PREVIEW_BYTES
        } !=
            false
    )
    return JsonObject(fields)
}

private const val MAX_LINES = 1_000_000L
private const val PREVIEW_BYTES = 160
