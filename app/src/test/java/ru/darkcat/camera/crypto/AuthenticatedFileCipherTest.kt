package ru.darkcat.camera.crypto

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthenticatedFileCipherTest {
    @Test
    fun roundTripPreservesBytes() {
        val directory = Files.createTempDirectory("darkcat-crypto").toFile()
        val source = File(directory, "source.bin").apply { writeBytes(ByteArray(200_000) { (it % 251).toByte() }) }
        val encrypted = File(directory, "source.dcv")
        val cipher = AuthenticatedFileCipher(ByteArray(32) { (it + 1).toByte() })
        cipher.encryptFile(source, encrypted)
        assertArrayEquals(source.readBytes(), cipher.decryptBytes(encrypted))
    }

    @Test
    fun corruptionIsDetectedByGcmTag() {
        val directory = Files.createTempDirectory("darkcat-crypto-corrupt").toFile()
        val encrypted = File(directory, "source.dcv")
        val cipher = AuthenticatedFileCipher(ByteArray(32) { 7 })
        cipher.encryptBytes("private evidence".toByteArray(), encrypted)
        val bytes = encrypted.readBytes()
        bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        encrypted.writeBytes(bytes)
        assertThrows(Exception::class.java) { cipher.decryptBytes(encrypted) }
    }
}
