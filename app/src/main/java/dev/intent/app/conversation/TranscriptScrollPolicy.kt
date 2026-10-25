package dev.intent.app.conversation

import dev.intent.core.protocol.models.ConversationMessage

internal data class TranscriptViewport(
    val atBottom: Boolean,
    val atTop: Boolean,
    val scrolling: Boolean,
    val nextToken: String?,
    val loading: Boolean
)

/** Content growth alone never resumes following. Only scrolling back to the bottom does. */
internal data class TranscriptScrollPolicy(
    val following: Boolean = true,
    val hasNewMessages: Boolean = false,
    val requestedToken: String? = null,
    val tail: ConversationMessage? = null,
    val hasLeftBottom: Boolean = false
) {
    fun pause() = copy(following = false)

    fun resume() = copy(following = true, hasNewMessages = false, hasLeftBottom = false)

    fun observe(viewport: TranscriptViewport): TranscriptScrollPolicy {
        val next = when {
            !viewport.atBottom -> copy(hasLeftBottom = true)
            viewport.scrolling && hasLeftBottom -> resume()
            else -> this
        }
        return if (!viewport.atTop) next.copy(requestedToken = null) else next
    }

    fun contentChanged(message: ConversationMessage?) = copy(
        tail = message,
        hasNewMessages = hasNewMessages || (!following && tail != null && message != tail)
    )

    fun shouldLoad(viewport: TranscriptViewport) = viewport.atTop &&
        !viewport.loading &&
        viewport.nextToken != null &&
        viewport.nextToken != requestedToken
}
