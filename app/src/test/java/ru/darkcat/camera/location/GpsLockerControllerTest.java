package ru.darkcat.camera.location;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GpsLockerControllerTest {
    @Test
    public void queryReevaluatesFixAgeEvenWithoutNewLocationCallback() {
        FakeClock clock = new FakeClock(TimeUnit.SECONDS.toNanos(100L));
        FakeLocker locker = new FakeLocker();
        GpsLockerController controller = new GpsLockerController(
                locker, clock, GpsPolicy.strictDefault());
        controller.start();
        locker.publish(GpsSnapshot.running(new LocationFix(
                1.0d, 2.0d, 3.0f, clock.nowNanos(), 1L, "gps")));
        assertEquals(GpsIndicator.GREEN, controller.getState().getIndicator());

        clock.now += TimeUnit.MILLISECONDS.toNanos(15_001L);
        assertEquals(GpsIndicator.RED, controller.getState().getIndicator());
        assertEquals(CaptureBlockReason.STALE_FIX,
                controller.getCaptureDecision().getBlockReason());
    }

    @Test
    public void policyCanChangeAtRuntimeAndListenersReceiveState() {
        FakeClock clock = new FakeClock(TimeUnit.SECONDS.toNanos(100L));
        FakeLocker locker = new FakeLocker();
        GpsLockerController controller = new GpsLockerController(
                locker, clock, GpsPolicy.strictDefault());
        List<GpsIndicator> indicators = new ArrayList<>();
        controller.addListener(state -> indicators.add(state.getIndicator()));
        controller.start();
        locker.publish(GpsSnapshot.running(new LocationFix(
                1.0d, 2.0d, 9.0f, clock.nowNanos(), 1L, "gps")));
        assertEquals(GpsIndicator.YELLOW, controller.getState().getIndicator());

        controller.setPolicy(GpsPolicy.strictDefault().withMaxAccuracyMeters(10.0f));
        assertEquals(GpsIndicator.GREEN, controller.getState().getIndicator());
        assertTrue(indicators.contains(GpsIndicator.YELLOW));
        assertTrue(indicators.contains(GpsIndicator.GREEN));

        controller.close();
        assertFalse(locker.started);
    }

    private static final class FakeClock implements ElapsedRealtimeClock {
        private long now;

        FakeClock(long now) {
            this.now = now;
        }

        @Override
        public long nowNanos() {
            return now;
        }
    }

    private static final class FakeLocker implements GpsLocker {
        private final List<Listener> listeners = new ArrayList<>();
        private GpsSnapshot snapshot = GpsSnapshot.stopped();
        private boolean started;

        @Override
        public void start() {
            started = true;
            publish(GpsSnapshot.running(null));
        }

        @Override
        public void stop() {
            started = false;
            publish(GpsSnapshot.stopped());
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public GpsSnapshot getSnapshot() {
            return snapshot;
        }

        @Override
        public void addListener(Listener listener) {
            listeners.add(listener);
            listener.onGpsSnapshot(snapshot);
        }

        @Override
        public void removeListener(Listener listener) {
            listeners.remove(listener);
        }

        void publish(GpsSnapshot next) {
            snapshot = next;
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onGpsSnapshot(next);
            }
        }
    }
}
