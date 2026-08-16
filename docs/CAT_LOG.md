# CAT Log MVP

CAT Log is an embedded, local-only diagnostic layer in the DarkCat Camera process. It starts with the app process and uses one bounded in-memory queue plus one sequential writer; it adds no service, wakelock, polling loop, network upload, photo data or raw coordinates.

The active session writes NDJSON under app-private `cat-log/sessions/<session-id>/`. It retains at most five sessions, 10,000 events, and 2 MiB per session. When the queue or file limit is exceeded, capture is not blocked: the total is exposed in status/export and a later `logger.events_dropped` evidence event is written when capacity permits.

`Отметить проблему` is on the normal camera control strip and records `user.problem_marked` with a compact allowlisted camera/GPS/Field/Locker/storage snapshot. The Diagnostics screen provides explicit Start/Stop, note, mark, clear and ZIP export. Export is a user-selected Android share action; it creates `cat-session-YYYYMMDD-HHMM.zip` containing `cat-events.ndjson`, `session.json`, `app-info.json`, `device-info.json`, `exit-info.json`, and `user-notes.json`. It never uploads diagnostics.

For P7-UI-01 the top dashboard's margin is calculated from the runtime system-bar/display-cutout inset after converting that safe window coordinate into the overlay parent's coordinate. This preserves an edge-to-edge preview, avoids a Pixel-specific offset, and avoids applying a top inset twice when the parent was already decor-inset. One `layout.dashboard_safe_area` event is recorded per changed layout evidence, not for every layout pass.

For P7-NIGHT-01, `NIGHT_CAPTURE.md` documents the Camera2 Extension session boundary and the inherited engine's controlled reopen. CAT Log records the requested toggle plus the applied/restored pre-Night photo mode, session type, transition duration, and safe camera/GPS state. It does not sample preview frames or add a Night worker.

Existing CAT/Maestro code can correlate events without an exported endpoint by calling `CatLog.setTestCase("CAT-25")` before a scenario and `CatLog.clearTestCase()` afterwards. Only values matching `CAT-01` through `CAT-99` are accepted.

| CAT IDs | Expected CAT Log evidence family |
|---|---|
| 01–03 | `session.start`, `app.foreground`, `layout.dashboard_safe_area` |
| 04, 11–15, 42–46 | `storage.mode_changed`, `storage.write_started`, `storage.write_completed` / `storage.write_failed` |
| 05–08, 16–21, 24, 26 | safe GPS snapshot, state-change-only `gps.state_changed`, `gps.locker_*`, `camera.capture_rejected` where a gate blocks capture |
| 09–10, 34 | UI/settings actions and safe camera/storage state snapshot |
| 17–19, 50–51 | lens/zoom action plus current safe camera ID and zoom snapshot |
| 22–23, 27–33, 47 | `field.mode_*`, `gps.locker_*`, foreground/background and camera callback/failure evidence |
| 25, 36 | `camera.capture_requested`, `camera.capture_callback`, storage outcome and dropped-event count |
| 35, 52 | `night.toggle_requested`, `night.apply_*`, `night.restore_*`, camera/session evidence |
| 37–38, 53 | existing sync state remains separate; CAT Log records only local action/error context |
| 39–41, 48–49, 54 | session interruption/previous exit, export metadata, camera callback and local state evidence |

CAT Log records `PASS` only for directly proved local facts (for example a camera callback or completed local write). A requested camera/session transition is `PARTIAL` until a hardware run proves the observed preview/capture outcome.
