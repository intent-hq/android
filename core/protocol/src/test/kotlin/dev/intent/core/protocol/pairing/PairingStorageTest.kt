package dev.intent.core.protocol.pairing

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingStorageTest {
    private val fingerprint = "ab".repeat(32)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val bytes = MemoryBytes()
    private val store = CredentialStore(bytes) { key }

    @Test
    fun pairingInputsPersistAcrossStoreInstances() {
        val inputs = listOf(
            requireNotNull(
                PairingPayload.parse(
                    "intent://pair?v=1&host=192.168.1.10,host.local&port=5181&fp=$fingerprint&token=secret"
                )
            ),
            requireNotNull(
                PairingPayload.parse(
                    "intent://pair?host=host.local&port=5181&certFingerprint=$fingerprint&token=secret&path=/ws"
                )
            ),
            requireNotNull(
                PairingPayload.parse(
                    "intent://pair?port=5181&fp=$fingerprint&token=secret&tc=tc-example"
                )
            ),
            ManualPairingInput(
                " host.local ",
                "5181",
                "secret",
                fingerprint,
                "tc-example"
            ).toPayload()
        )
        inputs.forEach { payload ->
            store.save(payload)
            assertEquals(payload, CredentialStore(bytes) { key }.load())
            val ciphertext = requireNotNull(bytes.value).decodeToString()
            assertFalse(ciphertext.contains("secret"))
            assertFalse(ciphertext.contains("host.local"))
            assertFalse(payload.toString().contains("secret"))
        }
    }

    @Test
    fun supportsCanonicalPrecedenceAndPercentEncoding() {
        val payload =
            requireNotNull(
                PairingPayload.parse(
                    "intent://pair?host=h%2C%5B%3A%3A1%5D&port=1&token=a+b%2Bc%3D&fp=aa&certFingerprint=bb"
                )
            )
        assertEquals(listOf("h", "[::1]"), payload.hosts)
        assertEquals("a+b+c=", payload.token)
        assertEquals("aa", payload.certFingerprint)
        assertEquals(
            "bb",
            PairingPayload.parse(
                "intent://pair?host=h&port=1&token=t&fp=&certFingerprint=bb"
            )?.certFingerprint
        )
    }

    @Test
    fun rejectsMalformedAndAmbiguousInputs() {
        val base = "intent://pair?host=h&port=5181&token=t"
        listOf(
            "not a uri", base.replace("intent:", "https:"), base.replace("pair?", "other?"),
            base.replace("pair?", "user@pair?"), base.replace("pair?", "pair:1?"),
            base.replace("pair?", "pair/path?"), "$base#fragment", "$base&token=other", "$base&v=2",
            "$base&port=0", base.replace("5181", "65536"), base.replace("5181", "abc"),
            base.replace("token=t", "token="), base.replace("host=h", "host="),
            base.replace("host=h", "host=a,,b"), base.replace("host=h", "host=https%3A%2F%2Fa"),
            base.replace("host=h", "host=::::"), base.replace("host=h", "host=999.1.1.1"),
            base.replace("host=h", "host=a..b"), "$base&fp=aa&fp=bb", "$base&tc=a&tc=b",
            base.replace("token=t", "token=%0A"), base.replace("token=t", "token=%zz")
        ).forEach { assertNull(it, PairingPayload.parse(it)) }
        assertThrows(IllegalArgumentException::class.java) {
            ManualPairingInput("h", "0", "t").toPayload()
        }
    }

    @Test
    fun missingFingerprintCanParseButCannotBecomeSavedCredentials() {
        val payload =
            requireNotNull(PairingPayload.parse("intent://pair?host=h&port=1&token=t&fp=&tc="))
        assertNull(payload.certFingerprint)
        assertNull(payload.tcAddress)
        assertThrows(IllegalArgumentException::class.java) { store.save(payload) }
        assertThrows(IllegalArgumentException::class.java) {
            store.save(payload.copy(certFingerprint = "aa"))
        }
        assertNull(store.load())
    }

    @Test
    fun encryptionUsesFreshNonceAndDetectsTampering() {
        val payload = ManualPairingInput("h", "5181", "secret", fingerprint).toPayload()
        store.save(payload)
        val first = requireNotNull(bytes.value).copyOf()
        store.save(payload)
        assertFalse(first.contentEquals(bytes.value))
        val record = requireNotNull(bytes.value)
        record[record.lastIndex] = (record.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { store.load() }
        assertNotNull(bytes.value)
    }

    @Test
    fun wrongKeyAndInvalidRecordsFailWithoutDeletingData() {
        store.save(ManualPairingInput("h", "1", "t", fingerprint).toPayload())
        val before = requireNotNull(bytes.value).copyOf()
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(AEADBadTagException::class.java) { CredentialStore(bytes) { otherKey }.load() }
        assertArrayEquals(before, bytes.value)
        bytes.value = byteArrayOf(2)
        assertThrows(IllegalArgumentException::class.java) { store.load() }
        store.clear()
        assertNull(store.load())
    }

    private class MemoryBytes : CredentialBytes {
        var value: ByteArray? = null

        override fun read(): ByteArray? = value

        override fun write(value: ByteArray) {
            this.value = value.copyOf()
        }

        override fun delete() {
            value = null
        }
    }
}
