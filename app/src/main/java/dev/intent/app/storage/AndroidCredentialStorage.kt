package dev.intent.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.intent.core.protocol.pairing.CredentialBytes
import dev.intent.core.protocol.pairing.CredentialKey
import dev.intent.core.protocol.pairing.CredentialStore
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Create one store per application and call its disk operations from an IO dispatcher. */
fun createCredentialStore(context: Context, filename: String = "pairing.enc"): CredentialStore =
    CredentialStore(
        AtomicCredentialBytes(File(context.applicationContext.noBackupFilesDir, filename)),
        AndroidCredentialKey
    )

private object AndroidCredentialKey : CredentialKey {
    private const val ALIAS = "dev.intent.pairing.v1"
    private const val KEY_SIZE_BITS = 256
    private const val PROVIDER = "AndroidKeyStore"

    @Synchronized
    override fun get(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(KEY_SIZE_BITS)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }
}

private class AtomicCredentialBytes(file: File) : CredentialBytes {
    private val atomicFile = AtomicFile(file)

    override fun read(): ByteArray? = try {
        atomicFile.openRead().use { it.readBytes() }
    } catch (error: FileNotFoundException) {
        if (atomicFile.baseFile.exists()) throw error
        null
    }

    override fun write(value: ByteArray) {
        val stream = atomicFile.startWrite()
        try {
            stream.write(value)
            atomicFile.finishWrite(stream)
        } catch (error: IOException) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    override fun delete() {
        atomicFile.delete()
        check(!atomicFile.baseFile.exists()) { "Could not delete credentials" }
    }
}
