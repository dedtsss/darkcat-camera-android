package ru.darkcat.camera.field;

/** Serialized ownership state for the Activity and service Camera2 clients. */
public final class FieldCameraOwnership {
    public enum Owner { ACTIVITY, SERVICE }

    private Owner owner = Owner.ACTIVITY;
    private long generation;

    public synchronized long handoffToActivity() {
        owner = Owner.ACTIVITY;
        return ++generation;
    }

    public synchronized long handoffToService() {
        owner = Owner.SERVICE;
        return ++generation;
    }

    public synchronized Owner owner() { return owner; }

    public synchronized long generation() { return generation; }
}
