package ru.darkcat.camera.upload;

import android.content.Context;

import java.io.File;

import ru.darkcat.camera.data.MediaRecord;

/** Explicit provider for offline-only operation. It never pretends an upload happened. */
public final class DisabledUploadProvider implements UploadProvider {
    @Override public UploadResult upload(Context context, MediaRecord record, File encryptedFile) {
        return new UploadResult(false, false, "Синхронизация отключена");
    }
}
