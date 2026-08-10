package ru.darkcat.camera.vault;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class VaultCommitIdentityTest {
    @Test public void sameRecoveryPathHasStableUuidAcrossRetries() throws Exception {
        File directory = Files.createTempDirectory("darkcat-vault-id").toFile();
        File firstPath = new File(directory, "capture-42.jpg");
        File equivalentPath = new File(directory, "." + File.separator + "capture-42.jpg");

        String first = VaultCommitIdentity.forRecoveryFile(firstPath);
        String retry = VaultCommitIdentity.forRecoveryFile(equivalentPath);

        assertEquals(first, retry);
        assertEquals(first, UUID.fromString(first).toString());
        directory.delete();
    }

    @Test public void differentRecoveryPathsCannotCreateDuplicateIdentity() throws Exception {
        File directory = Files.createTempDirectory("darkcat-vault-id-distinct").toFile();
        assertNotEquals(VaultCommitIdentity.forRecoveryFile(new File(directory, "a.jpg")),
                VaultCommitIdentity.forRecoveryFile(new File(directory, "b.jpg")));
        directory.delete();
    }
}
