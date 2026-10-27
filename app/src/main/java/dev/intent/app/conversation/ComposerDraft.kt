package dev.intent.app.conversation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Composer text stays independent of suggested prompt sends. */
class ComposerDraft {
    var text by mutableStateOf("")
}
