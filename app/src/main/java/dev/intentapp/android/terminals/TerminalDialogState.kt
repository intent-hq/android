package dev.intentapp.android.terminals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class TerminalDialogState {
    var renaming by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var deleting by mutableStateOf<String?>(null)
}
