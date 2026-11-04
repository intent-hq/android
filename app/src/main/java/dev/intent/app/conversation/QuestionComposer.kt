package dev.intent.app.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.attachments.PhotoAttachments
import dev.intent.app.attachments.PhotoPicker
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTextField as OutlinedTextField
import dev.intent.core.protocol.models.Question
import dev.intent.core.protocol.models.QuestionOption

@Suppress("LongMethod") // Keeps answers and page state alive when the question body collapses.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionComposer(
    set: QuestionSet,
    onAnswer: (String, Map<String, QuestionAnswer>) -> Boolean,
    onDismiss: (String) -> Unit,
    dismissing: Boolean,
    photos: PhotoAttachmentStore?
) {
    if (set.questions.isEmpty()) return
    val attachments = photos?.state?.collectAsState()?.value
    var confirmingDismiss by remember(set.messageId) { mutableStateOf(false) }
    var answers by remember(set.messageId) { mutableStateOf(emptyMap<String, QuestionAnswer>()) }
    var page by remember(set.messageId) { mutableIntStateOf(0) }
    var expanded by remember(set.messageId) { mutableStateOf(true) }
    var submitted by remember(set.messageId) { mutableStateOf(false) }
    val index = page.coerceIn(set.questions.indices)
    val question = set.questions[index]
    val answer = answers[question.attachmentId] ?: QuestionAnswer()
    val enabled = !dismissing && !submitted
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                    Text(
                        if (expanded) {
                            "Question ${index + 1} of ${set.questions.size} · Hide"
                        } else {
                            "Questions · ${set.questions.size} · Show"
                        }
                    )
                }
                IconButton(onClick = { confirmingDismiss = true }, enabled = enabled) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss questions")
                }
            }
            if (expanded) {
                key(question.attachmentId) {
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    ) {
                        QuestionInput(question, answer, enabled) {
                            answers = answers + (question.attachmentId to it)
                        }
                        photos?.let {
                            PhotoAttachments(it)
                            PhotoPicker(it)
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (index > 0) {
                        TextButton(onClick = {
                            page = index - 1
                        }, enabled = enabled) { Text("Back") }
                    }
                    TextButton(onClick = {
                        answers =
                            answers + (question.attachmentId to QuestionAnswer(skipped = true))
                        if (index < set.questions.lastIndex) page = index + 1
                    }, enabled = enabled) {
                        Text(if (answer.skipped) "Skipped" else "Skip question")
                    }
                    if (index < set.questions.lastIndex) {
                        TextButton(onClick = {
                            page = index + 1
                        }, enabled = enabled) { Text("Next") }
                    } else {
                        TextButton(onClick = {
                            submitted = onAnswer(set.messageId, answers)
                        }, enabled = enabled && attachments?.ready != false) {
                            Text("Send answers")
                        }
                    }
                }
            }
        }
    }
    if (confirmingDismiss) {
        DismissQuestionsDialog(
            enabled,
            onCancel = { confirmingDismiss = false },
            onDismiss = {
                confirmingDismiss = false
                onDismiss(set.messageId)
            }
        )
    }
}

@Composable
private fun DismissQuestionsDialog(enabled: Boolean, onCancel: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Dismiss questions?") },
        text = {
            Text(
                "The questions will be dismissed without answers. They will not return after a reload."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = enabled) { Text("Dismiss") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun QuestionInput(
    question: Question,
    answer: QuestionAnswer,
    enabled: Boolean,
    onChange: (QuestionAnswer) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(question.header, style = MaterialTheme.typography.labelMedium)
        Text(question.question, style = MaterialTheme.typography.titleSmall)
        question.explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Column(if (question.multiSelect) Modifier else Modifier.selectableGroup()) {
            question.options.forEach { option ->
                QuestionOptionRow(
                    option,
                    option.label in answer.selected,
                    question.multiSelect,
                    enabled
                ) {
                    val labels = when {
                        !question.multiSelect -> listOf(option.label)
                        option.label in answer.selected -> answer.selected - option.label
                        else -> answer.selected + option.label
                    }
                    onChange(answer.copy(selected = labels, skipped = false))
                }
            }
        }
        OutlinedTextField(
            answer.other,
            { onChange(answer.copy(other = it, skipped = false)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text("Other") },
            maxLines = 3
        )
    }
}

@Composable
private fun QuestionOptionRow(
    option: QuestionOption,
    selected: Boolean,
    multiSelect: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val selection = if (multiSelect) {
        Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox) { onClick() }
    } else {
        Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        Modifier.fillMaxWidth().then(selection).heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (multiSelect) {
            Checkbox(selected, onCheckedChange = null, enabled = enabled)
        } else {
            RadioButton(selected, onClick = null, enabled = enabled)
        }
        Column(Modifier.weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.bodyMedium)
            option.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
