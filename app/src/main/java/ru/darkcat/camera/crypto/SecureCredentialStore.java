package ru.darkcat.camera.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.SecretKey;

/** Keystore-backed credentials; passwords/tokens never enter ordinary preferences or logs. */
public final class SecureCredentialStore {
    private static final String PREFS = "darkcat_secure_credentials";
    public static void put(Context context, String key, String value) {
        try {
            SecretKey secret = DarkCatKeyStore.credentialKey(); byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secret, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ciphertext.length]; System.arraycopy(iv, 0, all, 0, iv.length); System.arraycopy(ciphertext, 0, all, iv.length, ciphertext.length);
            prefs(context).edit().putString(key, Base64.encodeToString(all, Base64.NO_WRAP)).apply();
        } catch (Exception e) { throw new IllegalStateException("Secure credential storage unavailable", e); }
    }
    public static String get(Context context, String key) {
        String encoded = prefs(context).getString(key, null); if (encoded == null) return "";
        try {
            byte[] all = Base64.decode(encoded, Base64.DEFAULT); byte[] iv = new byte[12]; byte[] ciphertext = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12); System.arraycopy(all, 12, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, DarkCatKeyStore.credentialKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private SecureCredentialStore() { }
}
