package ru.darkcat.camera.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;

/** Keystore-backed credentials; passwords/tokens never enter ordinary preferences or logs. */
public final class SecureCredentialStore {
    private static final String PREFS = "darkcat_secure_credentials";
    public static void put(Context context, String key, String value) {
        putAll(context, Collections.singletonMap(key, value));
    }

    /** Encrypts every value first, then commits one atomic preferences transaction. */
    public static void putAll(Context context, Map<String, String> values) {
        try {
            SecretKey secret = DarkCatKeyStore.credentialKey();
            Map<String, String> encrypted = new LinkedHashMap<>();
            for (Map.Entry<String, String> value : values.entrySet()) {
                byte[] clear = value.getValue() == null ? new byte[0]
                        : value.getValue().getBytes(StandardCharsets.UTF_8);
                byte[] envelope = CredentialCipher.encrypt(secret, clear);
                encrypted.put(value.getKey(), Base64.encodeToString(envelope, Base64.NO_WRAP));
            }
            SharedPreferences.Editor editor = prefs(context).edit();
            for (Map.Entry<String, String> value : encrypted.entrySet())
                editor.putString(value.getKey(), value.getValue());
            if (!editor.commit()) throw new IllegalStateException("Secure credential commit failed");
        } catch (Exception e) { throw new IllegalStateException("Secure credential storage unavailable", e); }
    }
    public static String get(Context context, String key) {
        String encoded = prefs(context).getString(key, null); if (encoded == null) return "";
        try {
            byte[] envelope = Base64.decode(encoded, Base64.DEFAULT);
            return new String(CredentialCipher.decrypt(DarkCatKeyStore.credentialKey(), envelope), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private SecureCredentialStore() { }
}
