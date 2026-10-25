package dev.intent.core.protocol.browser

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Binary /tunnel messages, protocol section 1.4. */
class TunnelFrame(val opcode: Int, val streamId: Int, val payload: ByteArray = byteArrayOf()) {
    fun encode(): ByteArray = ByteBuffer.allocate(HEADER_SIZE + payload.size)
        .put(opcode.toByte()).putInt(streamId).put(payload).array()

    companion object {
        const val OPEN = 1
        const val OPEN_OK = 2
        const val OPEN_ERROR = 3
        const val DATA = 4
        const val EOF = 5
        const val CLOSE = 6
        const val CREDIT = 7
        const val HEADER_SIZE = 5
        const val MAX_PAYLOAD = 1_048_576
        fun decode(bytes: ByteArray): TunnelFrame {
            require(bytes.size in HEADER_SIZE..HEADER_SIZE + MAX_PAYLOAD)
            val buffer = ByteBuffer.wrap(bytes)
            val opcode = buffer.get().toInt()
            val id = buffer.int
            val payload = ByteArray(buffer.remaining()).also(buffer::get)
            when (opcode) {
                OPEN -> require(payload.size == Short.SIZE_BYTES)
                CREDIT -> require(
                    payload.size == Int.SIZE_BYTES && ByteBuffer.wrap(payload).int != 0
                )
                OPEN_OK, EOF, CLOSE -> require(payload.isEmpty())
                OPEN_ERROR -> Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload))
                DATA -> Unit
                else -> throw IllegalArgumentException("Invalid tunnel opcode")
            }
            return TunnelFrame(opcode, id, payload)
        }

        @Suppress("ReturnCount")
        fun creditSupported(version: String?): Boolean {
            if (version == null || !version.matches(Regex("[0-9]+\\.[0-9]+"))) return false
            val parts = version.split('.').map { it.toIntOrNull() ?: return false }
            return parts[0] > CREDIT_MAJOR || parts[0] == CREDIT_MAJOR && parts[1] >= CREDIT_MINOR
        }
        private const val CREDIT_MAJOR = 10
        private const val CREDIT_MINOR = 4
    }
}
