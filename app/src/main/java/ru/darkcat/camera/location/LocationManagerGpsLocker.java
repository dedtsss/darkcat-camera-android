package ru.darkcat.camera.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Google-free GNSS locker backed by {@link LocationManager#GPS_PROVIDER}.
 *
 * <p>The owner must promote its service to foreground with type {@code location}
 * before calling {@link #start()} when running outside a visible Activity. This
 * class deliberately neither starts a service nor acquires a wake lock.</p>
 */
public final class LocationManagerGpsLocker implements GpsLocker, LocationListener {
    public static final long UPDATE_INTERVAL_MILLIS = 1_000L;
    private static final float MIN_DISTANCE_METERS = 0.0f;

    private final Context context;
    private final LocationManager locationManager;
    private final Handler callbackHandler;
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Object lock = new Object();

    private volatile GpsSnapshot snapshot = GpsSnapshot.stopped();
    private boolean started;
    private boolean starting;
    private boolean registered;

    private final Runnable ageTicker = new Runnable() {
        @Override
        public void run() {
            if (!isStarted()) {
                return;
            }
            dispatchSnapshot(snapshot);
            callbackHandler.postDelayed(this, UPDATE_INTERVAL_MILLIS);
        }
    };

    public LocationManagerGpsLocker(Context context) {
        this(
                context,
                Objects.requireNonNull(
                        (LocationManager) context.getApplicationContext()
                                .getSystemService(Context.LOCATION_SERVICE),
                        "LocationManager unavailable"),
                Looper.getMainLooper());
    }

    public LocationManagerGpsLocker(
            Context context,
            LocationManager locationManager,
            Looper callbackLooper) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.locationManager = Objects.requireNonNull(locationManager, "locationManager");
        this.callbackHandler = new Handler(Objects.requireNonNull(callbackLooper, "callbackLooper"));
    }

    @Override
    public void start() {
        synchronized (lock) {
            if (started && (starting || registered)) {
                return;
            }
            started = true;
            starting = true;
        }
        LocationRepository.publishLockerSnapshot(snapshot, false);

        if (!hasFineLocationPermission()) {
            finishFailedStart();
            publishStartFailure(GpsSourceStatus.PERMISSION_DENIED);
            return;
        }
        if (!hasGpsProvider()) {
            finishFailedStart();
            publishStartFailure(GpsSourceStatus.PROVIDER_UNAVAILABLE);
            return;
        }

        try {
            Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (isLocationEnabled()) {
                publish(GpsSnapshot.running(lastKnown == null ? null : toFix(lastKnown)));
            } else {
                // Register anyway: LocationListener will receive onProviderEnabled() and recover
                // without requiring an app/service restart when the user enables location.
                publish(GpsSnapshot.unavailable(GpsSourceStatus.LOCATION_DISABLED));
            }
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_INTERVAL_MILLIS,
                    MIN_DISTANCE_METERS,
                    this,
                    callbackHandler.getLooper());
            boolean keepRegistration;
            synchronized (lock) {
                starting = false;
                keepRegistration = started;
                registered = keepRegistration;
            }
            if (!keepRegistration) {
                locationManager.removeUpdates(this);
                return;
            }
            LocationRepository.publishLockerSnapshot(snapshot, true);
            callbackHandler.removeCallbacks(ageTicker);
            callbackHandler.postDelayed(ageTicker, UPDATE_INTERVAL_MILLIS);
        } catch (SecurityException denied) {
            finishFailedStart();
            publishStartFailure(GpsSourceStatus.PERMISSION_DENIED);
        } catch (IllegalArgumentException unavailable) {
            finishFailedStart();
            publishStartFailure(GpsSourceStatus.PROVIDER_UNAVAILABLE);
        } catch (RuntimeException platformFailure) {
            finishFailedStart();
            publishStartFailure(GpsSourceStatus.ERROR);
        }
    }

    @Override
    public void stop() {
        boolean shouldUnregister;
        synchronized (lock) {
            if (!started) {
                return;
            }
            started = false;
            starting = false;
            shouldUnregister = registered;
            registered = false;
        }
        callbackHandler.removeCallbacks(ageTicker);
        if (shouldUnregister) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
                // Permission can be revoked while the service is running.
            }
        }
        publish(GpsSnapshot.stopped());
    }

    @Override
    public boolean isStarted() {
        synchronized (lock) {
            return started;
        }
    }

    @Override
    public GpsSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public void addListener(Listener listener) {
        Listener checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        checked.onGpsSnapshot(snapshot);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!isStarted() || location == null) {
            return;
        }
        publish(GpsSnapshot.running(toFix(location)));
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (isStarted() && LocationManager.GPS_PROVIDER.equals(provider)) {
            LocationFix retained = snapshot.hasFix() ? snapshot.getFix() : null;
            publish(GpsSnapshot.running(retained));
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (isStarted() && LocationManager.GPS_PROVIDER.equals(provider)) {
            publish(GpsSnapshot.unavailable(GpsSourceStatus.LOCATION_DISABLED));
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (!isStarted() || !LocationManager.GPS_PROVIDER.equals(provider)) {
            return;
        }
        if (status == LocationProvider.OUT_OF_SERVICE) {
            publish(GpsSnapshot.unavailable(GpsSourceStatus.PROVIDER_UNAVAILABLE));
        } else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE && !snapshot.hasFix()) {
            publish(GpsSnapshot.running(null));
        }
    }

    private boolean hasFineLocationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private boolean isLocationEnabled() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && !locationManager.isLocationEnabled()) {
                return false;
            }
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean hasGpsProvider() {
        try {
            return locationManager.getProvider(LocationManager.GPS_PROVIDER) != null;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static LocationFix toFix(Location location) {
        return new LocationFix(
                location.getLatitude(),
                location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : LocationFix.ACCURACY_UNAVAILABLE,
                location.getElapsedRealtimeNanos(),
                location.getTime(),
                location.getProvider() == null ? "" : location.getProvider());
    }

    private void publish(GpsSnapshot next) {
        snapshot = Objects.requireNonNull(next, "next");
        LocationRepository.publishLockerSnapshot(next, isStarted());
        dispatchSnapshot(next);
    }

    private void finishFailedStart() {
        synchronized (lock) {
            starting = false;
        }
        LocationRepository.publishLockerSnapshot(snapshot, false);
    }

    private void publishStartFailure(GpsSourceStatus status) {
        if (isStarted()) {
            publish(GpsSnapshot.unavailable(status));
        }
    }

    private void dispatchSnapshot(GpsSnapshot value) {
        for (Listener listener : listeners) {
            try {
                listener.onGpsSnapshot(value);
            } catch (RuntimeException ignored) {
                // One UI/notification observer must not stop GNSS delivery to others.
            }
        }
    }
}
