package dev.intent.app.notes

import dev.intent.app.attachments.MediaReference
import dev.intent.app.attachments.noteMediaReference

data class NoteMediaPart(val text: String, val reference: MediaReference? = null)

@Suppress("MagicNumber") // Markdown regex capture groups.
fun noteMediaParts(text: String): List<NoteMediaPart> {
    val parts = mutableListOf<NoteMediaPart>()
    var start = 0
    noteMediaPattern.findAll(text).forEach { match ->
        val reference = noteMediaReference(
            match.groupValues[3],
            match.groupValues[2],
            match.groupValues[1] == "!"
        )
        if (reference != null) {
            if (start <
                match.range.first
            ) {
                parts.add(NoteMediaPart(text.substring(start, match.range.first)))
            }
            parts.add(NoteMediaPart(match.value, reference))
            start = match.range.last + 1
        }
    }
    if (start < text.length) parts.add(NoteMediaPart(text.substring(start)))
    return parts
}

private val noteMediaPattern = Regex("""(!?)\[([^]\n]*)]\((intent://local/file/[^\s)]+)\)""")
