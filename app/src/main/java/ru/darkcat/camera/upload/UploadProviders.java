package ru.darkcat.camera.upload;

import android.content.Context;

import ru.darkcat.camera.data.DarkCatSettings;

public final class UploadProviders {
    public static UploadProvider forContext(Context context) {
        switch (DarkCatSettings.provider(context)) {
            case DarkCatSettings.PROVIDER_NEXTCLOUD: return new NextcloudPublicShareProvider();
            case DarkCatSettings.PROVIDER_WEBDAV: return new GenericWebDavProvider();
            case DarkCatSettings.PROVIDER_DARKCAT_API: return new DarkCatApiProvider();
            // Kept only for tests and compatibility with an explicitly stored old debug setting.
            case DarkCatSettings.PROVIDER_LOCAL: return new LocalFakeProvider();
            case DarkCatSettings.PROVIDER_OFF:
            default: return new DisabledUploadProvider();
        }
    }
    private UploadProviders() { }
}
