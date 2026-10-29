package dev.intent.core.protocol.browser

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** One browser owns a pinned socket and bounded loopback forwards. Never replays stream data. */
@Suppress("TooManyFunctions")
class BrowserTunnel internal constructor(
    factory: (TunnelTransport.Listener) -> TunnelTransport,
    private val supportsCredit: Boolean,
    private val workers: ExecutorService
) : Closeable {
    constructor(
        factory: (TunnelTransport.Listener) -> TunnelTransport,
        supportsCredit: Boolean
    ) : this(factory, supportsCredit, Executors.newCachedThreadPool())

    private val control = Executors.newSingleThreadScheduledExecutor()
    private val ready = CompletableFuture<Unit>()
    private val listeners = mutableMapOf<Int, ServerSocket>()
    private val streams = mutableMapOf<Int, TunnelStream>()
    private var nextId = 1
    private var bufferedBytes = 0

    @Volatile private var closed = false
    private val overflow = AtomicBoolean()
    private val queuedBytes = AtomicInteger()
    private val queuedFrames = AtomicInteger()
    private val transport = factory(object : TunnelTransport.Listener {
        override fun opened() = dispatch { ready.complete(Unit) }
        override fun received(bytes: ByteArray) {
            if (closed || overflow.get()) return
            val bytesPending = queuedBytes.addAndGet(bytes.size)
            val framesPending = queuedFrames.incrementAndGet()
            if (bytesPending > MAX_BUFFERED || framesPending > MAX_FRAMES) {
                if (overflow.compareAndSet(false, true)) {
                    dispatch { close() }
                }
            } else {
                dispatch {
                    try {
                        receive(TunnelFrame.decode(bytes))
                    } catch (
                        _: Exception
                    ) {
                        close()
                    } finally {
                        queuedBytes.addAndGet(-bytes.size)
                        queuedFrames.decrementAndGet()
                    }
                }
            }
        }
        override fun failed() = dispatch { close() }
    })

    init {
        control.schedule({ closeIfNotReady() }, OPEN_TIMEOUT, TimeUnit.SECONDS)
        control.scheduleAtFixedRate({ expire() }, OPEN_TIMEOUT, OPEN_TIMEOUT, TimeUnit.SECONDS)
        try {
            transport.connect()
        } catch (_: Exception) {
            close()
        }
    }

    // Convert URL, socket, and lifecycle errors into the asynchronous API result.
    @Suppress("TooGenericExceptionCaught")
    fun forward(value: String): CompletableFuture<String> {
        val result = CompletableFuture<String>()
        ready.whenComplete { _, error ->
            if (error != null) {
                result.completeExceptionally(error)
            } else {
                try {
                    result.complete(resolve(value))
                } catch (failure: Exception) {
                    result.completeExceptionally(failure)
                }
            }
        }
        return result
    }

    @Synchronized
    private fun resolve(value: String): String {
        check(!closed) { "Browser tunnel closed" }
        val url = PreviewUrl.parse(value)
        val port = PreviewUrl.remotePort(url, listeners.values.map { it.localPort }.toSet())
            ?: return value
        val listener = listeners[port] ?: run {
            check(listeners.size < MAX_LISTENERS) { "Too many preview ports" }
            ServerSocket(0, MAX_STREAMS, InetAddress.getByName("127.0.0.1")).also {
                listeners[port] = it
                workers.execute { acceptLoop(it, port) }
            }
        }
        return PreviewUrl.rewrite(url, listener.localPort).toASCIIString()
    }

    private fun acceptLoop(listener: ServerSocket, port: Int) {
        try {
            while (!listener.isClosed) accept(listener.accept(), port)
        } catch (_: IOException) { /* Closed listener ends admission. */ }
    }

    @Synchronized
    private fun accept(socket: Socket, port: Int) {
        if (closed || streams.size >= MAX_STREAMS || nextId == Int.MAX_VALUE) {
            socket.close()
        } else {
            val stream = TunnelStream(nextId++, socket)
            streams[stream.id] = stream
            send(
                TunnelFrame(
                    TunnelFrame.OPEN,
                    stream.id,
                    ByteBuffer.allocate(Short.SIZE_BYTES).putShort(port.toShort()).array()
                )
            )
        }
    }

    @Synchronized
    private fun receive(frame: TunnelFrame) {
        check(frame.opcode != TunnelFrame.OPEN && frame.opcode != TunnelFrame.CREDIT)
        val stream = streams[frame.streamId] ?: return
        when (frame.opcode) {
            TunnelFrame.OPEN_OK -> {
                check(!stream.opened && !stream.remoteClosed)
                stream.opened = true
                stream.touch()
                workers.execute { upstream(stream) }
                workers.execute { downstream(stream) }
            }
            TunnelFrame.OPEN_ERROR -> {
                check(!stream.opened)
                remove(stream, false)
            }
            TunnelFrame.DATA -> {
                check(stream.opened && !stream.remoteEOF && !stream.remoteClosed)
                if (frame.payload.isNotEmpty()) enqueue(stream, frame)
            }
            TunnelFrame.EOF -> {
                check(stream.opened && !stream.remoteEOF && !stream.remoteClosed)
                stream.remoteEOF = true
                enqueue(stream, frame)
            }
            TunnelFrame.CLOSE -> remoteClose(stream, frame)
        }
    }

    private fun remoteClose(stream: TunnelStream, frame: TunnelFrame) {
        if (!stream.opened) {
            remove(stream, false)
        } else if (!stream.remoteClosed) {
            stream.remoteClosed = true
            enqueue(stream, frame)
        }
    }

    private fun enqueue(stream: TunnelStream, frame: TunnelFrame) {
        val size = frame.payload.size
        if (size > MAX_BUFFERED - bufferedBytes ||
            size > TunnelFrame.MAX_PAYLOAD - stream.pendingBytes ||
            stream.pendingFrames >= MAX_FRAMES
        ) {
            remove(stream, true)
        } else {
            bufferedBytes += size
            stream.pendingBytes += size
            stream.pendingFrames++
            stream.touch()
            check(stream.downstream.offer(frame))
        }
    }

    private fun upstream(stream: TunnelStream) {
        try {
            val buffer = ByteArray(READ_SIZE)
            while (true) {
                val count = stream.socket.getInputStream().read(buffer)
                if (!upload(stream, count, buffer)) return
            }
        } catch (_: IOException) {
            remove(stream, true)
        }
    }

    @Synchronized
    private fun upload(stream: TunnelStream, count: Int, buffer: ByteArray): Boolean {
        if (streams[stream.id] !== stream || stream.remoteClosed) return false
        stream.touch()
        send(
            TunnelFrame(
                if (count < 0) TunnelFrame.EOF else TunnelFrame.DATA,
                stream.id,
                if (count < 0) byteArrayOf() else buffer.copyOf(count)
            )
        )
        return count >= 0
    }

    private fun downstream(stream: TunnelStream) {
        try {
            while (!stream.socket.isClosed) {
                val frame = stream.downstream.take()
                when (frame.opcode) {
                    TunnelFrame.DATA -> stream.socket.getOutputStream().write(frame.payload)
                    TunnelFrame.EOF -> stream.socket.shutdownOutput()
                    TunnelFrame.CLOSE -> {
                        remove(stream, false)
                        return
                    }
                }
                delivered(stream, frame)
            }
        } catch (_: IOException) {
            remove(stream, true)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @Synchronized
    private fun delivered(stream: TunnelStream, frame: TunnelFrame) {
        if (streams[stream.id] !== stream) return
        stream.pendingFrames--
        stream.pendingBytes -= frame.payload.size
        bufferedBytes -= frame.payload.size
        stream.touch()
        if (supportsCredit && frame.payload.isNotEmpty()) {
            send(
                TunnelFrame(
                    TunnelFrame.CREDIT,
                    stream.id,
                    ByteBuffer.allocate(Int.SIZE_BYTES).putInt(frame.payload.size).array()
                )
            )
        }
    }

    @Synchronized
    private fun remove(stream: TunnelStream, notify: Boolean) {
        if (streams.remove(stream.id) !== stream) return
        bufferedBytes -= stream.pendingBytes
        stream.socket.close()
        stream.downstream.clear()
        // Wake a worker waiting for data after a local failure or timeout.
        stream.downstream.offer(TunnelFrame(TunnelFrame.CLOSE, stream.id))
        if (notify) send(TunnelFrame(TunnelFrame.CLOSE, stream.id))
    }

    private fun send(frame: TunnelFrame) {
        if (!closed && !transport.send(frame.encode())) close()
    }

    private fun dispatch(action: () -> Unit) {
        if (!closed) {
            try {
                control.execute(action)
            } catch (
                _: RejectedExecutionException
            ) { /* Teardown won. */ }
        }
    }

    @Synchronized
    private fun closeIfNotReady() {
        if (!ready.isDone) close()
    }

    @Synchronized
    private fun expire() {
        val now = System.nanoTime()
        streams.values.toList().filter {
            now - it.lastActivity >
                TimeUnit.SECONDS.toNanos(if (it.opened) IDLE_TIMEOUT else OPEN_TIMEOUT)
        }.forEach { remove(it, true) }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        ready.completeExceptionally(IOException("Browser tunnel closed"))
        listeners.values.forEach { it.close() }
        listeners.clear()
        streams.values.toList().forEach { remove(it, false) }
        transport.close()
        workers.shutdownNow()
        control.shutdownNow()
    }

    companion object {
        private const val MAX_LISTENERS = 8
        private const val MAX_STREAMS = 32
        private const val MAX_BUFFERED = 8_388_608
        internal const val MAX_FRAMES = 128
        private const val READ_SIZE = 16_384
        private const val OPEN_TIMEOUT = 15L
        private const val IDLE_TIMEOUT = 300L
    }
}
