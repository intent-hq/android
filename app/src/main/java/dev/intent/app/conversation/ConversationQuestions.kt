package dev.intent.app.conversation

import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.Question
import dev.intent.core.protocol.models.WireJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val QUESTION_MIME = "application/vnd.intent.question+json"

data class QuestionSet(val messageId: String, val questions: List<Question>)
data class QuestionAnswer(
    val selected: List<String> = emptyList(),
    val other: String = "",
    val skipped: Boolean = false
)

internal fun ContentBlock.isQuestion(): Boolean = resource?.mimeType == QUESTION_MIME ||
    (metadata?.get("resource") as? JsonObject)?.string("mimeType") == QUESTION_MIME

internal fun ContentBlock.question(): Question? {
    val text = if (isQuestion()) {
        resource?.text ?: (metadata?.get("resource") as? JsonObject)?.string("text")
    } else {
        null
    }
    val payload = text
        ?: return null
    return runCatching { WireJson.decodeFromString(Question.serializer(), payload) }.getOrNull()
        ?.takeIf { question ->
            question.header.isNotBlank() &&
                question.question.isNotBlank() &&
                question.options.size >= 2 &&
                question.options.all { it.label.isNotBlank() }
        }
}

internal fun JsonObject?.string(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject?.answeredQuestion(): String? =
    if (string("type") == "question_answers") string("answeredQuestionsMessageId") else null

internal fun ConversationMessage.answeredQuestion(): String? = metadata.answeredQuestion()
    ?: contentBlocks.firstNotNullOfOrNull { it.messageMetadata.answeredQuestion() }

internal fun flattenAnswers(set: QuestionSet, answers: Map<String, QuestionAnswer>): String =
    set.questions.joinToString("\n\n") { question ->
        val answer = answers[question.attachmentId] ?: QuestionAnswer(skipped = true)
        val choices = answer.selected.filter { label -> question.options.any { it.label == label } }
            .let { if (question.multiSelect) it else it.take(1) }
        val parts =
            choices +
                listOfNotNull(answer.other.trim().takeIf { it.isNotEmpty() }?.let { "(Other) $it" })
        val text = if (answer.skipped || parts.isEmpty()) "(skipped)" else parts.joinToString(", ")
        "Q: ${question.question}\nA: $text"
    }
