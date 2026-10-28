package dev.intent.app.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.attachments.PhotoAttachments
import dev.intent.app.attachments.PhotoPicker
import dev.intent.app.design.IntentButton
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTextField as OutlinedTextField
import dev.intent.core.protocol.models.Question
import dev.intent.core.protocol.models.QuestionOption

@Suppress("LongParameterList", "LongMethod") // Shared composer layout.
@Composable
fun ConversationComposer(
    state: ConversationSendState,
    onSend: (String) -> Boolean,
    onRetract: (String) -> Unit,
    onAnswer: (String, Map<String, QuestionAnswer>) -> Boolean,
    photos: PhotoAttachmentStore? = null,
    draft: ComposerDraft = remember { ComposerDraft() },
    onRetry: (String) -> Unit = {},
    onCheckDelivery: () -> Unit = {},
    clearOnSend: Boolean = true,
    onStop: () -> Unit = {},
    onEditQueued: (String, String) -> Unit = { _, _ -> },
    onSendNow: (String) -> Unit = {},
    onDismissQuestions: (String) -> Unit = {},
    questionPhotos: PhotoAttachmentStore? = null
) {
    val photoState = photos?.state?.collectAsState()?.value
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SendAttempts(state, onRetry, onCheckDelivery)
            QueuedConversationMessages(state, onRetract, onEditQueued, onSendNow)
            state.questions?.let {
                QuestionComposer(
                    it,
                    onAnswer,
                    onDismissQuestions,
                    state.dismissing != null,
                    questionPhotos
                )
            }
        }
        photos?.let { PhotoAttachments(it) }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                Modifier.padding(end = IntentSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    draft.text,
                    { draft.text = it },
                    Modifier.weight(1f).semantics { contentDescription = "Message" },
                    placeholder = { Text("Message") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                photos?.let { PhotoPicker(it) }
                if (state.running) {
                    IntentButton(
                        onClick = onStop,
                        enabled = !state.stopping,
                        modifier = Modifier.semantics { contentDescription = "Stop" }
                    ) {
                        Box(
                            Modifier.size(16.dp)
                                .background(LocalContentColor.current, RoundedCornerShape(2.dp))
                        )
                    }
                } else {
                    IntentButton(
                        onClick = {
                            if (onSend(draft.text) && clearOnSend) draft.text = ""
                        },
                        enabled =
                        (
                            clearOnSend ||
                                state.attempts.all {
                                    it.status == SendStatus.ACCEPTED ||
                                        it.status == SendStatus.QUEUED
                                }
                            ) &&
                            (photoState?.ready != false) &&
                            (draft.text.isNotBlank() || photoState?.photos?.isNotEmpty() == true)
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun SendAttempts(
    state: ConversationSendState,
    onRetry: (String) -> Unit,
    onCheckDelivery: () -> Unit
) {
    state.attempts.filter { attempt ->
        state.queue.none { it.id == attempt.confirmedId }
    }.forEach { attempt ->
        Text(attempt.content)
        Text(
            "${attempt.images.size + attempt.files.size} attachments".takeIf {
                attempt.images.isNotEmpty() ||
                    attempt.files.isNotEmpty()
            }.orEmpty()
        )
        Text(
            when (attempt.status) {
                SendStatus.SENDING -> "Sending…"
                SendStatus.NOT_SENT -> "Not sent"
                SendStatus.UNCONFIRMED -> "Delivery unconfirmed"
                SendStatus.ACCEPTED -> "Sent · Waiting for the transcript"
                SendStatus.QUEUED -> "Queued. Pull down to refresh."
            }
        )
        if (attempt.status == SendStatus.NOT_SENT) {
            TextButton(onClick = { onRetry(attempt.id) }) { Text("Retry send") }
        } else if (attempt.status == SendStatus.UNCONFIRMED) {
            TextButton(onClick = onCheckDelivery, enabled = !state.checking) {
                Text("Check delivery")
            }
        }
    }
}

@Composable
private fun QuestionComposer(
    set: QuestionSet,
    onAnswer: (String, Map<String, QuestionAnswer>) -> Boolean,
    onDismiss: (String) -> Unit,
    dismissing: Boolean,
    photos: PhotoAttachmentStore?
) {
    val attachments = photos?.state?.collectAsState()?.value
    var confirmingDismiss by remember(set.messageId) { mutableStateOf(false) }
    var answers by remember(set.messageId) { mutableStateOf(emptyMap<String, QuestionAnswer>()) }
    Column {
        set.questions.forEach { question ->
            val answer = answers[question.attachmentId] ?: QuestionAnswer()
            QuestionInput(question, answer) { answers = answers + (question.attachmentId to it) }
        }
        photos?.let {
            PhotoAttachments(it)
            PhotoPicker(it)
        }
        Row {
            TextButton(
                onClick = { onAnswer(set.messageId, answers) },
                enabled =
                !dismissing && attachments?.ready != false
            ) {
                Text("Send answers")
            }
            TextButton(onClick = { confirmingDismiss = true }, enabled = !dismissing) {
                Text("Dismiss questions")
            }
        }
    }
    if (confirmingDismiss) {
        AlertDialog(
            onDismissRequest = { confirmingDismiss = false },
            title = { Text("Dismiss questions?") },
            text = {
                Text(
                    "The questions will be dismissed without answers. They will not return after a reload."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDismiss = false
                    onDismiss(set.messageId)
                }, enabled = !dismissing) { Text("Dismiss") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDismiss = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun QuestionInput(
    question: Question,
    answer: QuestionAnswer,
    onChange: (QuestionAnswer) -> Unit
) {
    Column {
        Text(question.header, style = MaterialTheme.typography.titleSmall)
        Text(question.question)
        question.explanation?.let { Text(it) }
        question.options.forEach { option ->
            Row {
                Checkbox(option.label in answer.selected, { selected ->
                    val labels = when {
                        !selected -> answer.selected - option.label
                        question.multiSelect -> answer.selected + option.label
                        else -> listOf(option.label)
                    }
                    onChange(answer.copy(selected = labels, skipped = false))
                })
                Column {
                    Text(option.label)
                    option.description?.let { Text(it) }
                }
            }
        }
        OutlinedTextField(answer.other, {
            onChange(answer.copy(other = it, skipped = false))
        }, label = { Text("Other") })
        TextButton(onClick = { onChange(QuestionAnswer(skipped = true)) }) {
            Text(if (answer.skipped) "Skipped" else "Skip question")
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun ComposerPreview() {
    MaterialTheme {
        ConversationComposer(
            ConversationSendState(
                questions = QuestionSet(
                    "question",
                    listOf(
                        Question(
                            "q",
                            "Theme",
                            "Which theme?",
                            listOf(QuestionOption("Light"), QuestionOption("Dark"))
                        )
                    )
                )
            ),
            { true },
            {},
            { _, _ -> true }
        )
    }
}
