package dev.intent.core.protocol.browser

import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTunnelTest {
    @Test
    fun `relay waits for open and drains data before eof close`() {
        val fake = FakeTunnel()
        BrowserTunnel(fake::create, true).use { tunnel ->
            val url =
                URI(
                    tunnel.forward(
                        "http://daemon.localhost:3000/a%20b?q=%2F#x"
                    ).get(5, TimeUnit.SECONDS)
                )
            assertEquals("127.0.0.1", url.host)
            assertEquals("/a%20b", url.rawPath)
            assertEquals("q=%2F", url.rawQuery)
            Socket(url.host, url.port).use { local ->
                local.soTimeout = 5000
                val open = fake.next()
                assertEquals(TunnelFrame.OPEN, open.opcode)
                assertEquals(3000, ByteBuffer.wrap(open.payload).short.toInt())
                local.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".toByteArray())
                assertNull(fake.sent.poll(100, TimeUnit.MILLISECONDS))
                fake.receive(TunnelFrame(TunnelFrame.OPEN_OK, open.streamId))
                assertEquals(TunnelFrame.DATA, fake.next().opcode)
                fake.receive(TunnelFrame(TunnelFrame.DATA, open.streamId, "reply".toByteArray()))
                assertEquals("reply", String(local.getInputStream().readNBytes(5)))
                val credit = fake.next()
                assertEquals(TunnelFrame.CREDIT, credit.opcode)
                assertEquals(5, ByteBuffer.wrap(credit.payload).int)
                fake.receive(TunnelFrame(TunnelFrame.EOF, open.streamId))
                assertEquals(-1, local.getInputStream().read())
                // Remote EOF does not prevent more client uploads.
                local.getOutputStream().write("more".toByteArray())
                assertEquals(TunnelFrame.DATA, fake.next().opcode)
                local.shutdownOutput()
                assertEquals(TunnelFrame.EOF, fake.next().opcode)
                fake.receive(TunnelFrame(TunnelFrame.CLOSE, open.streamId))
            }
        }
        assertTrue(fake.closed)
    }

    @Test
    fun `old daemon receives no credit and teardown closes listeners`() {
        val fake = FakeTunnel()
        val tunnel = BrowserTunnel(fake::create, false)
        val url = URI(tunnel.forward("http://localhost:4000/").get(5, TimeUnit.SECONDS))
        Socket(url.host, url.port).use { socket ->
            socket.soTimeout = 5000
            val id = fake.next().streamId
            fake.receive(TunnelFrame(TunnelFrame.OPEN_OK, id))
            fake.receive(TunnelFrame(TunnelFrame.DATA, id, byteArrayOf(42)))
            assertEquals(42, socket.getInputStream().read())
            assertNull(fake.sent.poll(100, TimeUnit.MILLISECONDS))
            tunnel.close()
            assertEquals(-1, socket.getInputStream().read())
        }
        assertTrue(tunnel.forward("http://localhost:4000").isCompletedExceptionally)
        assertThrows(java.net.ConnectException::class.java) { Socket(url.host, url.port).close() }
    }

    @Test
    fun `open error terminates stream and malformed direction closes tunnel`() {
        val fake = FakeTunnel()
        BrowserTunnel(fake::create, true).use { tunnel ->
            val url = URI(tunnel.forward("http://localhost:4000/").get(5, TimeUnit.SECONDS))
            Socket(url.host, url.port).use { socket ->
                socket.soTimeout = 5000
                fake.receive(
                    TunnelFrame(
                        TunnelFrame.OPEN_ERROR,
                        fake.next().streamId,
                        "refused".toByteArray()
                    )
                )
                assertEquals(-1, socket.getInputStream().read())
            }
            fake.receive(TunnelFrame(TunnelFrame.CREDIT, 99, byteArrayOf(0, 0, 0, 1)))
            assertTrue(fake.closedSignal.poll(5, TimeUnit.SECONDS)!!)
        }
    }

    @Test
    fun `close before upgrade fails pending URL resolution`() {
        val fake = FakeTunnel(autoOpen = false)
        val tunnel = BrowserTunnel(fake::create, false)
        val result = tunnel.forward("http://localhost:4000")
        tunnel.close()
        assertTrue(result.isCompletedExceptionally)
        fake.listener.opened()
        assertTrue(fake.closed)
    }
}

internal class FakeTunnel(private val autoOpen: Boolean = true) : TunnelTransport {
    lateinit var listener: TunnelTransport.Listener
    val sent = LinkedBlockingQueue<TunnelFrame>()
    val closedSignal = LinkedBlockingQueue<Boolean>()
    var closed = false
    fun create(listener: TunnelTransport.Listener): TunnelTransport {
        this.listener = listener
        return this
    }
    override fun connect() {
        if (autoOpen) listener.opened()
    }
    override fun send(bytes: ByteArray): Boolean {
        sent.add(TunnelFrame.decode(bytes))
        return !closed
    }
    override fun close() {
        closed = true
        closedSignal.offer(true)
    }
    fun next(): TunnelFrame = checkNotNull(sent.poll(5, TimeUnit.SECONDS))
    fun receive(frame: TunnelFrame) = listener.received(frame.encode())
}
