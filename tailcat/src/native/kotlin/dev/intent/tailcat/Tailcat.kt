package dev.intent.tailcat

import tailcatkit.Client
import tailcatkit.Forwarder
import tailcatkit.Tailcatkit

object Tailcat : TunnelClientFactory {
    override val available = true

    override fun createClient(token: String): TunnelClient = NativeTunnelClient(Tailcatkit.newClient(token))
}

private class NativeTunnelClient(
    private val client: Client,
) : TunnelClient {
    override fun ping(timeoutMs: Int) = client.ping(timeoutMs.toLong())

    override fun forward(remotePort: Int): TunnelForwarder = NativeTunnelForwarder(client.forward(remotePort.toLong()))

    override fun close() = client.close()
}

private class NativeTunnelForwarder(
    private val forwarder: Forwarder,
) : TunnelForwarder {
    override val localPort: Int get() = forwarder.port().toInt()

    override fun close() = forwarder.close()
}
