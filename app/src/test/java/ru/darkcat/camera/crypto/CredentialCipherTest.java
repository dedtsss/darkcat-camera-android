package ru.darkcat.camera.crypto;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public final class CredentialCipherTest {
    private final SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");

    @Test public void roundTripUsesProviderGeneratedIv() throws Exception {
        byte[] plaintext = "nextcloud secret".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] envelope = CredentialCipher.encrypt(key, plaintext, cipher);

        assertArrayEquals(cipher.getIV(), Arrays.copyOf(envelope, CredentialCipher.IV_LENGTH));
        assertArrayEquals(plaintext, CredentialCipher.decrypt(key, envelope));
    }

    @Test public void generatedIvChangesForEveryEncryption() throws Exception {
        byte[] plaintext = "same secret".getBytes(StandardCharsets.UTF_8);
        byte[] first = CredentialCipher.encrypt(key, plaintext);
        byte[] second = CredentialCipher.encrypt(key, plaintext);

        assertFalse(Arrays.equals(Arrays.copyOf(first, CredentialCipher.IV_LENGTH),
                Arrays.copyOf(second, CredentialCipher.IV_LENGTH)));
        assertFalse(Arrays.equals(first, second));
    }

    @Test public void decryptsLegacyIvCiphertextLayout() throws Exception {
        byte[] iv = new byte[CredentialCipher.IV_LENGTH];
        Arrays.fill(iv, (byte) 7);
        Cipher legacy = Cipher.getInstance("AES/GCM/NoPadding");
        legacy.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] plaintext = "old credential".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = legacy.doFinal(plaintext);
        byte[] envelope = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, envelope, 0, iv.length);
        System.arraycopy(ciphertext, 0, envelope, iv.length, ciphertext.length);

        assertArrayEquals(plaintext, CredentialCipher.decrypt(key, envelope));
    }

    @Test public void corruptionIsRejected() throws Exception {
        byte[] envelope = CredentialCipher.encrypt(key, "secret".getBytes(StandardCharsets.UTF_8));
        envelope[envelope.length - 1] ^= 1;

        assertThrows(GeneralSecurityException.class, () -> CredentialCipher.decrypt(key, envelope));
    }

    @Test public void truncatedEnvelopeIsRejected() {
        assertThrows(GeneralSecurityException.class,
                () -> CredentialCipher.decrypt(key, new byte[CredentialCipher.IV_LENGTH]));
    }
}
