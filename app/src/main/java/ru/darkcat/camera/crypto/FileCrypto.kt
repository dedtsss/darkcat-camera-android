package ru.darkcat.camera.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class FileCrypto(context: Context) {
    private val applicationContext = context.applicationContext

    private fun cipher(): AuthenticatedFileCipher = AuthenticatedFileCipher(loadOrCreateKey())

    fun encryptFile(source: java.io.File, destination: java.io.File): EncryptionResult = cipher().encryptFile(source, destination)

    fun encryptBytes(source: ByteArray, destination: java.io.File): EncryptionResult = cipher().encryptBytes(source, destination)

    fun decryptBytes(source: java.io.File): ByteArray = cipher().decryptBytes(source)

    @Suppress("UNUSED_VARIABLE")
    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "darkcat.camera.vault.v1"
    }
}
