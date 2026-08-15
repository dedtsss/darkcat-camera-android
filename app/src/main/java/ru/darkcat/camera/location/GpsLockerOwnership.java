package ru.darkcat.camera.location;

/**
 * Small, explicit ownership model for the one GPS Locker service.
 *
 * <p>Field Mode may require the locker temporarily, while the user can also
 * request it independently. Stopping one owner must never silently stop the
 * other.</p>
 */
public final class GpsLockerOwnership {
    private final boolean requestedByUser;
    private final boolean requestedByField;

    public GpsLockerOwnership(boolean requestedByUser, boolean requestedByField) {
        this.requestedByUser = requestedByUser;
        this.requestedByField = requestedByField;
    }

    public boolean isRequestedByUser() {
        return requestedByUser;
    }

    public boolean isRequestedByField() {
        return requestedByField;
    }

    public boolean isLockerRequired() {
        return requestedByUser || requestedByField;
    }

    public GpsLockerOwnership withUserRequest(boolean requested) {
        return new GpsLockerOwnership(requested, requestedByField);
    }

    public GpsLockerOwnership withFieldRequest(boolean requested) {
        return new GpsLockerOwnership(requestedByUser, requested);
    }

    public GpsLockerOwnership stopAll() {
        return new GpsLockerOwnership(false, false);
    }
}
