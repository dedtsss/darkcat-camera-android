package ru.darkcat.camera.crypto;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;

public final class AuthenticatedFileCipherTest {
    @Test public void encryptsAndDecryptsStreamingFormat() throws Exception {
        File source = File.createTempFile("darkcat-source", ".jpg"); File encrypted = File.createTempFile("darkcat-vault", ".dcv"); File decrypted = File.createTempFile("darkcat-decrypted", ".jpg");
        byte[] payload = "darkcat capture payload".getBytes(StandardCharsets.UTF_8); try (FileOutputStream output = new FileOutputStream(source)) { output.write(payload); }
        AuthenticatedFileCipher cipher = new AuthenticatedFileCipher(new SecretKeySpec(new byte[32], "AES")); AuthenticatedFileCipher.Result first = cipher.encrypt(source, encrypted); cipher.decrypt(encrypted, decrypted);
        assertArrayEquals(payload, java.nio.file.Files.readAllBytes(decrypted.toPath()));
        File encrypted2 = File.createTempFile("darkcat-vault-2", ".dcv"); AuthenticatedFileCipher.Result second = cipher.encrypt(source, encrypted2); assertNotEquals(first.sha256, second.sha256);
        source.delete(); encrypted.delete(); encrypted2.delete(); decrypted.delete();
    }
}
