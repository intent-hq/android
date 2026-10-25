package dev.intent.app.conversation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared by the composer and transcript chips; selecting a chip never sends without review. */
class ComposerDraft {
    var text by mutableStateOf("")
}
