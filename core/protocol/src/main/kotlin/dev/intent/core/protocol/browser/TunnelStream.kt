package dev.intent.core.protocol.browser

import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue

internal class TunnelStream(val id: Int, val socket: Socket) {
    val downstream = ArrayBlockingQueue<TunnelFrame>(BrowserTunnel.MAX_FRAMES)
    var opened = false
    var remoteEOF = false
    var remoteClosed = false
    var pendingBytes = 0
    var pendingFrames = 0
    var lastActivity = System.nanoTime()
    fun touch() {
        lastActivity = System.nanoTime()
    }
}
