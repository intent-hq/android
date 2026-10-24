package dev.intent.app.conversation

import dev.intent.core.protocol.models.ChatEntity
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.EntityDelta

/** Full-text encoding: repeated delivery replaces a block, never duplicates text. */
internal fun reduceConversation(
    messages: List<ConversationMessage>,
    delta: EntityDelta<ChatEntity>,
    agentId: String
): List<ConversationMessage> {
    val rows = messages.associateBy { it.id }.toMutableMap()
    (delta.added + delta.updated).filter { it.agentId == agentId }.forEach { entity ->
        val row =
            rows[entity.messageId]
                ?: ConversationMessage(entity.messageId, entity.role, agentId = agentId)
        val blocks = row.contentBlocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == entity.block.id }
        val old = blocks.getOrNull(index)
        val block = if (entity.streamingComplete == true) {
            entity.block
        } else {
            entity.block.copy(
                media = if (old?.media ==
                    null
                ) {
                    entity.block.media
                } else {
                    old.media.orEmpty() + entity.block.media.orEmpty()
                }
            )
        }
        if (index < 0) blocks.add(block) else blocks[index] = block
        rows[row.id] = row.copy(
            role = entity.role,
            contentBlocks = blocks,
            seq = entity.messageSeq ?: row.seq,
            timestamp = entity.timestamp ?: row.timestamp,
            metadata = entity.metadata ?: row.metadata,
            isStreaming = entity.role == "assistant" && entity.streamingComplete != true
        )
    }
    return rows.values.map { row ->
        row.copy(contentBlocks = row.contentBlocks.filterNot { it.id in delta.removedIds })
    }.sortedWith(
        compareBy<ConversationMessage> {
            it.seq ?: Long.MAX_VALUE
        }.thenBy { it.timestamp }
    )
}
