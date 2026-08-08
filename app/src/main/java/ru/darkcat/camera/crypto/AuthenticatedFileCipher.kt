package ru.darkcat.camera.crypto

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class EncryptionResult(val ciphertextSize: Long, val sha256: String)

/** AES-GCM file format. The tag is written by CipherOutputStream after the payload. */
class AuthenticatedFileCipher(private val key: SecretKey) {
    constructor(keyBytes: ByteArray) : this(
        SecretKeySpec(keyBytes.also { require(it.size == 32) { "DarkCat vault keys must be 256-bit" } }, "AES"),
    )

    fun encryptFile(source: File, destination: File): EncryptionResult =
        FileInputStream(source).use { input -> FileOutputStream(destination).use { output -> encrypt(input, output) } }

    fun encryptBytes(source: ByteArray, destination: File): EncryptionResult =
        ByteArrayInputStream(source).use { input -> FileOutputStream(destination).use { output -> encrypt(input, output) } }

    fun decryptFile(source: File, destination: File) {
        FileInputStream(source).use { input -> FileOutputStream(destination).use { output -> decrypt(input, output) } }
    }

    fun decryptBytes(source: File): ByteArray =
        FileInputStream(source).use { input -> ByteArrayOutputStream().use { output -> decrypt(input, output); output.toByteArray() } }

    private fun encrypt(input: InputStream, rawOutput: OutputStream): EncryptionResult {
        val iv = ByteArray(IV_LENGTH).also(SecureRandom()::nextBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        val digestOutput = object : OutputStream() {
            var size = 0L
            override fun write(value: Int) {
                rawOutput.write(value)
                digest.update(value.toByte())
                size++
            }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                rawOutput.write(bytes, offset, length)
                digest.update(bytes, offset, length)
                size += length
            }
            override fun flush() = rawOutput.flush()
            override fun close() = rawOutput.close()
        }
        digestOutput.use { output ->
            output.write(MAGIC)
            output.write(iv)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            }
            CipherOutputStream(BufferedOutputStream(output), cipher).use { cipherOutput ->
                input.copyTo(cipherOutput, BUFFER_SIZE)
            }
            return EncryptionResult(digestOutput.size, digest.digest().toHex())
        }
    }

    private fun decrypt(rawInput: InputStream, output: OutputStream) {
        val input = BufferedInputStream(rawInput)
        val magic = input.readFully(MAGIC.size)
        check(magic.contentEquals(MAGIC)) { "Invalid DarkCat vault header" }
        val iv = input.readFully(IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        CipherInputStream(input, cipher).use { cipherInput -> cipherInput.copyTo(output, BUFFER_SIZE) }
        output.flush()
    }

    private fun InputStream.readFully(size: Int): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(result, offset, size - offset)
            check(count >= 0) { "Truncated DarkCat vault file" }
            offset += count
        }
        return result
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_LENGTH = 12
        private const val BUFFER_SIZE = 64 * 1024
        private val MAGIC = byteArrayOf('D'.code.toByte(), 'C'.code.toByte(), 'V'.code.toByte(), 1)
    }
}
