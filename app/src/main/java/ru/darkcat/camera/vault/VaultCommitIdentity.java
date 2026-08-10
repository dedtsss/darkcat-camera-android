package ru.darkcat.camera.vault;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable, non-secret identity for retrying one app-private recovery file. */
final class VaultCommitIdentity {
    private static final String DOMAIN = "darkcat-vault-recovery-v1\n";

    static String forRecoveryFile(File file) throws IOException {
        if (file == null) throw new IOException("recovery file is missing");
        return UUID.nameUUIDFromBytes((DOMAIN + file.getCanonicalPath())
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private VaultCommitIdentity() { }
}
