package dev.intent.app.conversation

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationDraftStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clear() {
        context.getSharedPreferences(
            "conversation-drafts",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test fun savesRestoresAndRemovesEmptyTextAcrossStoreInstances() {
        val draft = ConversationDraftStore(context, "connection").draft("workspace", "agent")
        draft.text = "  Keep whitespace\nDraft text  "
        val restored = ConversationDraftStore(context, "connection").draft("workspace", "agent")
        assertEquals(draft.text, restored.text)
        restored.text = ""
        assertEquals(
            "",
            ConversationDraftStore(context, "connection").draft("workspace", "agent").text
        )
        assertEquals(
            0,
            context.getSharedPreferences("conversation-drafts", Context.MODE_PRIVATE).all.size
        )
    }

    @Test fun scopesByConnectionWorkspaceAndAgentWithoutDelimiterCollisions() {
        ConversationDraftStore(context, "a:b").draft("c", "d").text = "private draft"
        assertEquals("", ConversationDraftStore(context, "a").draft("b:c", "d").text)
        assertEquals("", ConversationDraftStore(context, "other").draft("c", "d").text)
        assertEquals("", ConversationDraftStore(context, "a:b").draft("other", "d").text)
        assertEquals("", ConversationDraftStore(context, "a:b").draft("c", "other").text)
    }

    @Test fun confirmsOnlyTheSentRevisionAndPersistsClear() {
        val store = ConversationDraftStore(context, "connection")
        val draft = store.draft("workspace", "agent")
        draft.text = "first"
        val first = draft.confirmation()
        draft.text = "second"
        first()
        assertEquals("second", store.draft("workspace", "agent").text)
        draft.confirmation()()
        assertEquals("", store.draft("workspace", "agent").text)
    }
}
