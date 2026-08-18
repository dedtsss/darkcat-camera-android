package ru.darkcat.camera.ui;

/**
 * Correlates the asynchronous CameraExtension session and capture callbacks.
 *
 * <p>The Android callback API does not carry a session generation on every
 * callback, and sequence completion does not carry the capture request.  This
 * small identity-based model gives the controller an explicit ownership
 * boundary so a late OEM callback cannot complete a newer Night capture.</p>
 */
public final class NightExtensionLifecycle {
    public enum SessionState { IDLE, REQUESTED, READY, FAILED, CLOSED }

    public static final int NO_SEQUENCE = -1;

    public static final class SessionHandle {
        public final long generation;
        public final int extension;
        private final boolean night;
        private Object session;

        private SessionHandle(long generation, int extension, boolean night) {
            this.generation = generation;
            this.extension = extension;
            this.night = night;
        }
    }

    public static final class CaptureHandle {
        public final long generation;
        public final long captureId;
        public final Object request;
        private final SessionHandle session;
        private int sequenceId = NO_SEQUENCE;
        private boolean started;
        private boolean finished;

        private CaptureHandle(SessionHandle session, long captureId, Object request) {
            this.session = session;
            this.generation = session.generation;
            this.captureId = captureId;
            this.request = request;
        }
    }

    private long nextGeneration;
    private long nextCaptureId;
    private SessionHandle currentSession;
    private CaptureHandle currentCapture;
    private SessionState state = SessionState.IDLE;

    public synchronized SessionHandle requestSession(int extension, boolean night) {
        SessionHandle handle = new SessionHandle(++nextGeneration, extension, night);
        currentSession = handle;
        currentCapture = null;
        state = night ? SessionState.REQUESTED : SessionState.IDLE;
        return night ? handle : null;
    }

    public synchronized boolean configured(SessionHandle handle, Object session) {
        if (!isCurrent(handle) || !handle.night || state != SessionState.REQUESTED || session == null) return false;
        handle.session = session;
        state = SessionState.READY;
        return true;
    }

    public synchronized boolean failed(SessionHandle handle) {
        if (!isCurrent(handle) || !handle.night || state == SessionState.CLOSED) return false;
        currentCapture = null;
        state = SessionState.FAILED;
        return true;
    }

    public synchronized boolean closed(SessionHandle handle, Object session) {
        if (!isCurrent(handle) || !handle.night) return false;
        if (handle.session != null && session != null && handle.session != session) return false;
        currentCapture = null;
        state = SessionState.CLOSED;
        return true;
    }

    public synchronized CaptureHandle requestCapture(SessionHandle handle, Object session, Object request) {
        if (!isCurrent(handle) || state != SessionState.READY || handle.session != session || request == null) return null;
        CaptureHandle capture = new CaptureHandle(handle, ++nextCaptureId, request);
        currentCapture = capture;
        return capture;
    }

    public synchronized boolean bindSequence(CaptureHandle capture, int sequenceId) {
        if (!isCurrent(capture) || sequenceId < 0 || capture.finished) return false;
        capture.sequenceId = sequenceId;
        return true;
    }

    public synchronized boolean captureStarted(Object session, Object request) {
        if (currentCapture == null || currentCapture.finished || !isCurrent(currentCapture)
                || currentCapture.session.session != session || currentCapture.request != request) return false;
        currentCapture.started = true;
        return true;
    }

    public synchronized boolean acceptsProgress(Object session, Object request) {
        return currentCapture != null && !currentCapture.finished && isCurrent(currentCapture)
                && currentCapture.session.session == session && currentCapture.request == request;
    }

    public synchronized boolean completed(Object session, int sequenceId) {
        if (!matchesSequence(session, sequenceId)) return false;
        currentCapture.finished = true;
        currentCapture = null;
        return true;
    }

    public synchronized boolean aborted(Object session, int sequenceId) {
        if (!matchesSequence(session, sequenceId)) return false;
        currentCapture.finished = true;
        currentCapture = null;
        return true;
    }

    public synchronized boolean isCurrentSession(Object session) {
        return currentSession != null && currentSession.session == session
                && state == SessionState.READY;
    }

    /** True while the current controller generation still belongs to Night, including after close. */
    public synchronized boolean isNightSessionCallback(Object session) {
        return currentSession != null && currentSession.night && currentSession.session == session;
    }

    public synchronized SessionState state() { return state; }
    public synchronized long generation() { return currentSession == null ? 0L : currentSession.generation; }
    public synchronized boolean hasCapture() { return currentCapture != null && !currentCapture.finished; }

    private boolean matchesSequence(Object session, int sequenceId) {
        return currentCapture != null && !currentCapture.finished && isCurrent(currentCapture)
                && currentCapture.session.session == session && currentCapture.sequenceId == sequenceId;
    }

    private boolean isCurrent(SessionHandle handle) {
        return handle != null && currentSession == handle;
    }

    private boolean isCurrent(CaptureHandle capture) {
        return capture != null && currentCapture == capture && isCurrent(capture.session)
                && capture.session.session != null;
    }
}
