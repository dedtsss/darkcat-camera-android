package ru.darkcat.camera.data;

/** The only two user-facing photo storage destinations in DarkCat Camera 0.5. */
public enum StorageMode {
    VAULT("vault"),
    MEDIASTORE("mediastore");

    private final String preferenceValue;

    StorageMode(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public String preferenceValue() {
        return preferenceValue;
    }

    public static StorageMode fromPreference(String value) {
        for (StorageMode mode : values()) {
            if (mode.preferenceValue.equals(value)) return mode;
        }
        return VAULT;
    }
}
