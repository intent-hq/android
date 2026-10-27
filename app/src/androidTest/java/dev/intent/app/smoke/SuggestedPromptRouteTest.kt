package dev.intent.app.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.intent.app.attachments.PhotoAttachmentSource
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.conversation.ConversationCommands
import dev.intent.app.conversation.ConversationRoute
import dev.intent.app.conversation.ConversationSendStore
import dev.intent.app.conversation.ConversationSession
import dev.intent.app.conversation.ConversationStore
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.attachments.PlacedAttachment
import dev.intent.core.protocol.attachments.TransferProgress
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.ChatPush
import dev.intent.core.protocol.models.CommandResult
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.ConversationPage
import dev.intent.core.protocol.models.MessageBlockResult
import dev.intent.core.protocol.models.QueueResult
import dev.intent.core.protocol.models.SendResult
import java.io.Closeable
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SuggestedPromptRouteTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun promptSendPreservesComposerAndPendingPhotoThroughFailure() {
        val source = PromptFixture()
        val photos = PhotoAttachmentStore(source)
        photos.add(listOf("pending-photo"))
        val before = photos.state.value
        ConversationStore(source.session, "agent").use { store ->
            ConversationSendStore(source, "agent").use { sends ->
                compose.setContent {
                    IntentTheme {
                        Surface(Modifier.fillMaxSize()) {
                            ConversationRoute(store, sends, photos = photos)
                        }
                    }
                }
                compose.onNodeWithText("Message").performTextInput("Keep my draft")
                compose.onNodeWithText("Run tests").performClick()
                compose.onNodeWithText("Review code").assertDoesNotExist()
                compose.onNodeWithText("Sending…").assertIsDisplayed()
                compose.onNodeWithText("Message").assertTextEquals("Message", "Keep my draft")
                compose.runOnIdle {
                    assertEquals(listOf("Run tests"), source.sent)
                    assertEquals(before, photos.state.value)
                    source.reply.completeExceptionally(IllegalStateException("timeout"))
                }
                compose.onNodeWithText("Message").assertTextEquals("Message", "Keep my draft")
                compose.runOnIdle { assertEquals(before, photos.state.value) }
            }
        }
        photos.close()
    }
}

private class PromptFixture :
    ConversationCommands,
    PhotoAttachmentSource {
    val sent = mutableListOf<String>()
    val reply = CompletableFuture<SendResult>()
    private val page = ConversationPage(
        "agent",
        listOf(
            ConversationMessage(
                "reply",
                "assistant",
                listOf(
                    ContentBlock(
                        "text",
                        text = "Done.\n<!-- suggested-prompts\nRun tests\nReview code\n-->"
                    )
                )
            )
        )
    )
    override fun send(
        id: String,
        content: String,
        metadata: JsonObject?
    ): CompletableFuture<SendResult> {
        check(metadata == null)
        sent.add(content)
        return reply
    }
    override fun queue() = CompletableFuture.completedFuture(QueueResult(true, emptyList()))
    override fun agent() = CompletableFuture.completedFuture(Agent("agent", "Agent"))
    override fun history(aroundId: String?) = CompletableFuture.completedFuture(page)
    override fun retract(id: String) = CompletableFuture.completedFuture(CommandResult(true))
    override fun watch(onRefresh: () -> Unit, onError: (Throwable) -> Unit) = Closeable {}
    val session = object : ConversationSession {
        override fun history(token: String) = CompletableFuture.completedFuture(page)
        override fun hydrate(messageId: String, blockId: String) =
            CompletableFuture<MessageBlockResult>()
        override fun subscribe(
            onPush: (ChatPush) -> Unit,
            onSubscribed: () -> Unit,
            onError: (Throwable) -> Unit
        ): Closeable {
            onSubscribed()
            onPush(ChatPush("s", "snapshot", 0, snapshot = page))
            return Closeable {}
        }
    }
    override fun prepare(uri: String) = CompletableFuture<File>()
    override fun upload(file: File, key: String, progress: (TransferProgress) -> Unit) =
        CompletableFuture<PlacedAttachment>()
}
