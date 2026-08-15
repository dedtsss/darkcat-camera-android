package ru.darkcat.camera.upload;

import android.content.Context;

import ru.darkcat.camera.data.MediaRecord;

import java.io.File;

public interface UploadProvider {
    UploadResult upload(Context context, MediaRecord record, File encryptedFile) throws Exception;
    final class UploadResult { public final boolean accepted; public final boolean verified; public final String message; public UploadResult(boolean accepted, boolean verified, String message) { this.accepted=accepted; this.verified=verified; this.message=message; } }
}
