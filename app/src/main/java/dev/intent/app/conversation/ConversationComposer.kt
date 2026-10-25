package dev.intent.app.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.attachments.PhotoAttachments
import dev.intent.app.attachments.PhotoPicker
import dev.intent.core.protocol.models.Question
import dev.intent.core.protocol.models.QuestionOption

@Suppress("LongParameterList") // Composer actions and its photo store.
@Composable
fun ConversationComposer(
    state: ConversationSendState,
    onSend: (String) -> Boolean,
    onRetract: (String) -> Unit,
    onAnswer: (String, Map<String, QuestionAnswer>) -> Boolean,
    photos: PhotoAttachmentStore? = null,
    draft: ComposerDraft = remember { ComposerDraft() }
) {
    val photoState = photos?.state?.collectAsState()?.value
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SendAttempts(state)
            QueuedConversationMessages(state, onRetract)
            state.questions?.let { QuestionComposer(it, onAnswer) }
        }
        photos?.let { PhotoAttachments(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            photos?.let { PhotoPicker(it) }
            OutlinedTextField(draft.text, {
                draft.text = it
            }, Modifier.weight(1f), label = { Text("Message") }, maxLines = 4)
            TextButton(
                onClick = {
                    if (onSend(draft.text)) draft.text = ""
                },
                enabled =
                (photoState?.ready != false) &&
                    (draft.text.isNotBlank() || photoState?.photos?.isNotEmpty() == true)
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun SendAttempts(state: ConversationSendState) {
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
                SendStatus.UNCONFIRMED -> "Delivery unconfirmed. Pull down to refresh."
                SendStatus.ACCEPTED -> "Accepted. Waiting for the transcript."
                SendStatus.QUEUED -> "Queued. Pull down to refresh."
            }
        )
    }
}

@Composable
private fun QuestionComposer(
    set: QuestionSet,
    onAnswer: (String, Map<String, QuestionAnswer>) -> Boolean
) {
    var answers by remember(set.messageId) { mutableStateOf(emptyMap<String, QuestionAnswer>()) }
    Column {
        set.questions.forEach { question ->
            val answer = answers[question.attachmentId] ?: QuestionAnswer()
            QuestionInput(question, answer) { answers = answers + (question.attachmentId to it) }
        }
        TextButton(onClick = { onAnswer(set.messageId, answers) }) { Text("Send answers") }
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
