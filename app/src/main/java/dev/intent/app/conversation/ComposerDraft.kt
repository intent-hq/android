package dev.intent.app.conversation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Composer text stays independent of suggestions and changes made during a send. */
class ComposerDraft(initialText: String = "", private val save: (String) -> Unit = {}) {
    private var value by mutableStateOf(initialText)
    private var revision = 0L
    var text: String
        get() = value
        set(text) {
            value = text
            revision++
            save(text)
        }

    fun confirmation(): () -> Unit {
        val sentRevision = revision
        return { if (revision == sentRevision) text = "" }
    }
}
