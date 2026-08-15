package ru.darkcat.camera.crypto;

import android.annotation.SuppressLint;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

@SuppressLint("NewApi")
public final class DarkCatKeyStore {
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "DarkCatCameraVaultAes256Gcm";
    private static final String CREDENTIAL_ALIAS = "DarkCatCameraCredentialsAes256Gcm";

    public static SecretKey vaultKey() throws Exception { return getOrCreate(ALIAS); }
    public static SecretKey credentialKey() throws Exception { return getOrCreate(CREDENTIAL_ALIAS); }

    private static SecretKey getOrCreate(String alias) throws Exception {
        KeyStore store = KeyStore.getInstance(STORE); store.load(null);
        if (store.containsAlias(alias)) return ((KeyStore.SecretKeyEntry) store.getEntry(alias, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private DarkCatKeyStore() { }
}
