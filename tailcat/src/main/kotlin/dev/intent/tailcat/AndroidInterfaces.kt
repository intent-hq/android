package dev.intent.tailcat

import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object AndroidInterfaces {
    private const val FLAG_UP = 1
    private const val FLAG_BROADCAST = 2
    private const val FLAG_LOOPBACK = 4
    private const val FLAG_POINT_TO_POINT = 8
    private const val FLAG_MULTICAST = 16

    fun current(): String = encode(
        Collections.list(NetworkInterface.getNetworkInterfaces()).mapNotNull { iface ->
            try {
                InterfaceSnapshot(
                    iface.name,
                    iface.index,
                    iface.mtu,
                    flags(
                        iface.isUp,
                        iface.isLoopback,
                        iface.isPointToPoint,
                        iface.supportsMulticast(),
                        iface.interfaceAddresses.any {
                            it.broadcast !=
                                null
                        }
                    ),
                    iface.interfaceAddresses.mapNotNull { address ->
                        address.address?.hostAddress?.let {
                            "$it/${address.networkPrefixLength}"
                        }
                    }
                )
            } catch (_: SocketException) {
                // Interfaces can disappear between enumeration and inspection.
                null
            }
        }
    )

    fun flags(
        up: Boolean,
        loopback: Boolean,
        pointToPoint: Boolean,
        multicast: Boolean,
        broadcast: Boolean
    ): Int = (if (up) FLAG_UP else 0) or (if (broadcast) FLAG_BROADCAST else 0) or
        (if (loopback) FLAG_LOOPBACK else 0) or (if (pointToPoint) FLAG_POINT_TO_POINT else 0) or
        (if (multicast) FLAG_MULTICAST else 0)

    fun encode(interfaces: List<InterfaceSnapshot>): String = JsonArray(
        interfaces.map { iface ->
            buildJsonObject {
                put("Name", iface.name)
                put("Index", iface.index)
                put("MTU", iface.mtu)
                put("Flags", iface.flags)
                put("Addrs", JsonArray(iface.addresses.map(::JsonPrimitive)))
            }
        }
    ).toString()
}

internal data class InterfaceSnapshot(
    val name: String,
    val index: Int,
    val mtu: Int,
    val flags: Int,
    val addresses: List<String>
)
