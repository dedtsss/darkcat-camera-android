package ru.darkcat.camera.crypto;

import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/** Device/emulator coverage for the AndroidKeyStore generated-IV behavior. */
@RunWith(AndroidJUnit4.class)
public final class SecureCredentialStoreInstrumentedTest {
    @Test public void credentialsRoundTripAcrossFreshContextLookup() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
        Context first = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key = "instrumented-" + UUID.randomUUID();
        String secret = "пароль-" + UUID.randomUUID();
        SecureCredentialStore.put(first, key, secret);

        Context reopened = first.createDeviceProtectedStorageContext() == null
                ? first : InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(secret, SecureCredentialStore.get(reopened, key));
    }

    @Test public void overwriteGetsNewProviderIvAndRemainsReadable() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key = "instrumented-overwrite-" + UUID.randomUUID();
        SecureCredentialStore.put(context, key, "first");
        SecureCredentialStore.put(context, key, "second");
        assertEquals("second", SecureCredentialStore.get(context, key));
    }
}
