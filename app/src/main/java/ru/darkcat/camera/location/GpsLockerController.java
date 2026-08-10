package ru.darkcat.camera.location;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Integration facade used by camera UI, notification, and capture triggers.
 * Every read re-evaluates age against elapsed realtime, so a cached fix cannot
 * remain green after location delivery stops.
 */
public final class GpsLockerController implements GpsLocker.Listener, GpsCaptureGate {
    public interface Listener {
        void onGpsStateChanged(GpsState state);
    }

    private final GpsLocker locker;
    private final ElapsedRealtimeClock clock;
    private final GpsStateEvaluator stateEvaluator;
    private final GpsCapturePolicy capturePolicy;
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    private volatile GpsPolicy policy;
    private volatile GpsSnapshot snapshot;
    private boolean subscribed;

    public GpsLockerController(GpsLocker locker, ElapsedRealtimeClock clock, GpsPolicy policy) {
        this(locker, clock, policy, new GpsStateEvaluator());
    }

    public GpsLockerController(
            GpsLocker locker,
            ElapsedRealtimeClock clock,
            GpsPolicy policy,
            GpsStateEvaluator stateEvaluator) {
        this.locker = Objects.requireNonNull(locker, "locker");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.stateEvaluator = Objects.requireNonNull(stateEvaluator, "stateEvaluator");
        this.capturePolicy = new GpsCapturePolicy(stateEvaluator);
        this.snapshot = locker.getSnapshot();
    }

    public synchronized void start() {
        if (!subscribed) {
            locker.addListener(this);
            subscribed = true;
        }
        locker.start();
    }

    public synchronized void stop() {
        locker.stop();
    }

    /** Detaches the observer too; intended for a permanently destroyed owner. */
    public synchronized void close() {
        locker.stop();
        if (subscribed) {
            locker.removeListener(this);
            subscribed = false;
        }
    }

    public boolean isStarted() {
        return locker.isStarted();
    }

    public GpsState getState() {
        return stateEvaluator.evaluate(snapshot, clock.nowNanos(), policy);
    }

    @Override
    public CaptureDecision getCaptureDecision() {
        return capturePolicy.evaluate(snapshot, clock.nowNanos(), policy);
    }

    public LocationFix getLastFix() {
        return snapshot.getFix();
    }

    public GpsPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(GpsPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        dispatch(getState());
    }

    public void addListener(Listener listener) {
        Listener checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        checked.onGpsStateChanged(getState());
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onGpsSnapshot(GpsSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        dispatch(getState());
    }

    private void dispatch(GpsState state) {
        for (Listener listener : listeners) {
            try {
                listener.onGpsStateChanged(state);
            } catch (RuntimeException ignored) {
                // A presentation observer cannot break the capture gate/location source.
            }
        }
    }
}
