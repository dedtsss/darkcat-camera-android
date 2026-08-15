package ru.darkcat.camera.crypto;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Streaming AES-256-GCM vault format. The GCM tag is authenticated on decrypt. */
public final class AuthenticatedFileCipher {
    private static final byte[] MAGIC = new byte[]{'D','C','V',1};
    private static final int IV_LENGTH = 12;
    private static final int BUFFER = 64 * 1024;
    private final SecretKey key;

    public AuthenticatedFileCipher(SecretKey key) { this.key = key; }

    public Result encrypt(File source, File destination) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             FileOutputStream file = new FileOutputStream(destination);
             DigestOutput output = new DigestOutput(new BufferedOutputStream(file), digest)) {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            // Android Keystore randomized-encryption keys reject a caller-provided IV.
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != IV_LENGTH) throw new GeneralSecurityException("AES-GCM provider returned an unsupported IV");
            output.write(MAGIC); output.write(iv);
            byte[] buffer = new byte[BUFFER];
            int count;
            while ((count = input.read(buffer)) != -1) {
                byte[] encrypted = cipher.update(buffer, 0, count);
                if (encrypted != null && encrypted.length > 0) output.write(encrypted);
            }
            byte[] finalBlock = cipher.doFinal();
            if (finalBlock != null && finalBlock.length > 0) output.write(finalBlock);
            // A successful return means the authenticated header, ciphertext and GCM tag have
            // reached the filesystem, rather than merely a userspace BufferedOutputStream.
            output.flush();
            file.getFD().sync();
            return new Result(destination.length(), hex(digest.digest()));
        }
    }

    public void decrypt(File source, File destination) throws Exception {
        File parent = destination.getAbsoluteFile().getParentFile();
        File temporary = File.createTempFile(".darkcat-decrypt-", ".tmp", parent);
        boolean authenticated = false;
        try {
            try (InputStream file = new BufferedInputStream(new FileInputStream(source));
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] magic = readFully(file, MAGIC.length); if (!java.util.Arrays.equals(magic, MAGIC)) throw new SecurityException("Invalid DarkCat vault header");
                byte[] iv = readFully(file, IV_LENGTH);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                try (CipherInputStream decrypted = new CipherInputStream(file, cipher)) { copy(decrypted, output); }
                output.getFD().sync();
            }
            authenticated = true;
            if (destination.exists() && !destination.delete()) throw new java.io.IOException("Unable to replace decrypted destination");
            if (!temporary.renameTo(destination)) throw new java.io.IOException("Unable to commit decrypted destination");
        } finally {
            // Unauthenticated plaintext is never exposed at the requested destination.
            if (!authenticated || temporary.exists()) { //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    public byte[] decryptBytes(File source) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        File temp = File.createTempFile("darkcat-decrypt", ".tmp");
        try { decrypt(source, temp); try (InputStream input = new FileInputStream(temp)) { copy(input, output); } return output.toByteArray(); }
        finally { //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private static void copy(InputStream input, OutputStream output) throws Exception { byte[] buffer = new byte[BUFFER]; int n; while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n); output.flush(); }
    private static byte[] readFully(InputStream input, int length) throws Exception { byte[] result = new byte[length]; int offset = 0; while (offset < length) { int n = input.read(result, offset, length - offset); if (n < 0) throw new SecurityException("Truncated DarkCat vault file"); offset += n; } return result; }
    private static String hex(byte[] value) { StringBuilder result = new StringBuilder(); for (byte b : value) result.append(String.format(java.util.Locale.US, "%02x", b)); return result.toString(); }
    public static final class Result { public final long size; public final String sha256; Result(long size, String sha256) { this.size = size; this.sha256 = sha256; } }
    private static final class DigestOutput extends OutputStream {
        private final OutputStream output; private final MessageDigest digest;
        DigestOutput(OutputStream output, MessageDigest digest) { this.output = output; this.digest = digest; }
        @Override public void write(int b) throws java.io.IOException { output.write(b); digest.update((byte)b); }
        @Override public void write(byte[] b, int o, int l) throws java.io.IOException { output.write(b,o,l); digest.update(b,o,l); }
        @Override public void flush() throws java.io.IOException { output.flush(); }
        @Override public void close() throws java.io.IOException { output.close(); }
    }
}
