package dev.intent.tailcat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidInterfacesTest {
    @Test
    fun `maps Java interface fields and both address families`() {
        val flags = AndroidInterfaces.flags(true, false, false, true, true)
        val record = InterfaceSnapshot(
            "wlan0",
            7,
            1500,
            flags,
            listOf("192.0.2.8/24", "fe80::1%7/64")
        )
        val mapped = Json.parseToJsonElement(AndroidInterfaces.encode(listOf(record)))
            .jsonArray.single().jsonObject
        assertEquals("wlan0", mapped.getValue("Name").jsonPrimitive.content)
        assertEquals("7", mapped.getValue("Index").jsonPrimitive.content)
        assertEquals("1500", mapped.getValue("MTU").jsonPrimitive.content)
        assertEquals("19", mapped.getValue("Flags").jsonPrimitive.content)
        assertEquals(
            record.addresses,
            mapped.getValue("Addrs").jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals(12, AndroidInterfaces.flags(false, true, true, false, false))
        assertEquals("[]", AndroidInterfaces.encode(emptyList()))
    }
}
