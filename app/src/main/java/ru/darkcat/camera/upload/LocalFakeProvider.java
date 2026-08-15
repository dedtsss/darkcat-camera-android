package ru.darkcat.camera.upload;

import android.content.Context;

import ru.darkcat.camera.data.MediaRecord;

import java.io.File;

public final class LocalFakeProvider implements UploadProvider {
    @Override public UploadResult upload(Context context, MediaRecord record, File encryptedFile) { return new UploadResult(true, true, "local fake provider"); }
}
