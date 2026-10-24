package dev.intent.core.protocol.pairing

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The backend must atomically replace ciphertext and keep it outside backups. */
interface CredentialBytes {
    fun read(): ByteArray?

    fun write(value: ByteArray)

    fun delete()
}

/** Supplies a non-exportable Android Keystore key in production. */
fun interface CredentialKey {
    fun get(): SecretKey
}

/** One paired server record. Errors propagate: damaged or unavailable credentials are not a missing pairing. */
class CredentialStore(private val bytes: CredentialBytes, private val key: CredentialKey) {
    @Synchronized
    fun save(payload: PairingPayload) {
        requireTrustedFingerprint(payload)
        val plaintext = Json.encodeToString(payload).encodeToByteArray()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key.get())
            cipher.updateAAD(CONTEXT)
            check(cipher.iv.size == IV_SIZE)
            bytes.write(byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(plaintext))
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    fun load(): PairingPayload? {
        val record = bytes.read() ?: return null
        require(record.size >= 1 + IV_SIZE + TAG_BYTES && record[0] == VERSION) {
            "Invalid credential record"
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key.get(),
            GCMParameterSpec(
                TAG_BITS,
                record.copyOfRange(
                    1,
                    1 + IV_SIZE
                )
            )
        )
        cipher.updateAAD(CONTEXT)
        val plaintext = cipher.doFinal(record.copyOfRange(1 + IV_SIZE, record.size))
        return try {
            Json.decodeFromString<PairingPayload>(
                plaintext.decodeToString()
            ).also(::requireTrustedFingerprint)
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    fun clear() {
        bytes.delete()
    }

    private fun requireTrustedFingerprint(payload: PairingPayload) {
        val fingerprint = payload.certFingerprint.orEmpty()
        require(
            fingerprint.matches(Regex("[0-9a-fA-F]{64}|(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}"))
        ) {
            "A verified SHA-256 certificate fingerprint is required"
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_SIZE = 12
        const val TAG_BYTES = 16
        const val TAG_BITS = 128
        val CONTEXT = "intent.android.pairing.v1".encodeToByteArray()
    }
}
