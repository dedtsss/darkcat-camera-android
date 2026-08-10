package ru.darkcat.camera.upload;

import android.content.Context;
import android.net.Uri;

import ru.darkcat.camera.crypto.SecureCredentialStore;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.MediaRecord;

import java.io.File;

/** Public-share preset: token/password are translated to Nextcloud's public WebDAV endpoint. */
public final class NextcloudPublicShareProvider implements UploadProvider {
    @Override public UploadResult upload(Context context, MediaRecord record, File encryptedFile) throws Exception {
        Uri share = Uri.parse(DarkCatSettings.nextcloudShare(context));
        String token = share.getLastPathSegment(); if (token == null || token.isEmpty()) throw new IllegalArgumentException("Nextcloud share URL has no token");
        String base = share.getScheme() + "://" + share.getAuthority();
        String target = WebDavClient.appendPath(base + "/public.php/webdav", DarkCatSettings.remoteFolder(context), encryptedFile.getName());
        UploadResult result = WebDavClient.put(target, token, SecureCredentialStore.get(context, "nextcloud_password"), "application/octet-stream", encryptedFile);
        if (!result.accepted) return result;
        try {
            return new UploadResult(true, WebDavClient.verify(target, token,
                    SecureCredentialStore.get(context, "nextcloud_password"),
                    encryptedFile.length()), result.message);
        } catch (Exception verificationUnavailable) {
            return new UploadResult(true, false, "Upload accepted; verification unavailable");
        }
    }
}
