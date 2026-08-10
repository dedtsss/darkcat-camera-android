package ru.darkcat.camera.upload;

import android.content.Context;

import ru.darkcat.camera.crypto.SecureCredentialStore;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.MediaRecord;

import java.io.File;

public final class GenericWebDavProvider implements UploadProvider {
    @Override public UploadResult upload(Context context, MediaRecord record, File encryptedFile) throws Exception {
        String target = WebDavClient.appendPath(DarkCatSettings.baseUrl(context), DarkCatSettings.remoteFolder(context), encryptedFile.getName());
        UploadResult result = WebDavClient.put(target, SecureCredentialStore.get(context, "webdav_user"), SecureCredentialStore.get(context, "webdav_password"), "application/octet-stream", encryptedFile);
        if (!result.accepted) return result;
        try {
            return new UploadResult(true, WebDavClient.verify(target,
                    SecureCredentialStore.get(context, "webdav_user"),
                    SecureCredentialStore.get(context, "webdav_password"),
                    encryptedFile.length()), result.message);
        } catch (Exception verificationUnavailable) {
            return new UploadResult(true, false, "Upload accepted; verification unavailable");
        }
    }
}
