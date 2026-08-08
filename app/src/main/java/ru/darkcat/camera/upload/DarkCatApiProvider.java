package ru.darkcat.camera.upload;

import android.content.Context;

import ru.darkcat.camera.data.MediaRecord;

import java.io.File;

/** Production API is intentionally not implemented in PR2; this contract already uploads encrypted media. */
public final class DarkCatApiProvider implements UploadProvider {
    @Override public UploadResult upload(Context context, MediaRecord record, File encryptedFile) { return new UploadResult(false, false, "DarkCat API provider is a documented stub"); }
}
