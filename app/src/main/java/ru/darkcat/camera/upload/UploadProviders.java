package ru.darkcat.camera.upload;

import android.content.Context;

import ru.darkcat.camera.data.DarkCatSettings;

public final class UploadProviders {
    public static UploadProvider forContext(Context context) {
        switch (DarkCatSettings.provider(context)) {
            case DarkCatSettings.PROVIDER_NEXTCLOUD: return new NextcloudPublicShareProvider();
            case DarkCatSettings.PROVIDER_WEBDAV: return new GenericWebDavProvider();
            case DarkCatSettings.PROVIDER_DARKCAT_API: return new DarkCatApiProvider();
            default: return new LocalFakeProvider();
        }
    }
    private UploadProviders() { }
}
