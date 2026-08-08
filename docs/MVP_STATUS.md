# MVP status

Snapshot: 2026-08-08.

## Implemented

- Clean Android project, package/application id `ru.darkcat.camera`.
- CameraX preview with Camera2 backend, lifecycle binding, front/rear switch, tap-to-focus + AE metering, zoom, flash auto/on/off and torch, exposure compensation, orientation updates.
- Photo and real CameraX video capture.
- Optional location permission and non-blocking cached GPS/accuracy/altitude metadata.
- Grid and center crosshair overlays.
- Optional visible date/time/sequence/GPS/text/tags stamp before encryption.
- FAST photo/video capture directly to vault; EDIT photo capture into a real rotate/pinch-zoom editor.
- AES-256-GCM authenticated encryption with Android Keystore key, random internal names and encrypted thumbnails.
- SQLite media record with UUID, sequence, capture time, structured context/metadata, dimensions, duration, ciphertext size/checksum and upload state.
- Internal vault list, authenticated photo reopening, edit, delete and retry controls.
- WorkManager unique upload work, connected-network constraint, exponential retry, idempotency key and checksum-confirmed fake uploader.
- Unit tests for crypto roundtrip/corruption, metadata serialization, upload transitions and stale-temp policy; an instrumented DB test is included.

## Intentionally next

1. Real DarkCat API adapter with authenticated encrypted-at-client upload, resumable chunks, server confirmation and retention/delete policy.
2. Authenticated streaming video decryptor/player and video metadata/frame extraction improvements.
3. Full editor annotation layer: crop, undo/redo, text, colors, stroke, lines, arrows, ellipse, rectangle, freehand and SVG stickers.
4. Camera2Interop advanced controls: ISO, shutter, manual focus, WB presets/temperature, HDR/Night/Extensions/stabilization, capability and device-quirk registry.
5. SQLCipher/keystore-wrapped metadata DB decision, biometric lock policy, notification channel and production privacy/logging audit.
6. CRM deep-link `CaptureContext` input/output contract and migration to separate `camera-core`, `vault`, `upload` and UI Gradle modules if integration requires it.

## Verification limits

The current environment has Java/Git/GitHub CLI but no Android SDK `adb`, no emulator and no standalone Gradle executable. The Gradle wrapper is included and the build is attempted in this run; hardware camera quality, OEM behavior and permission UX cannot be honestly marked as device-tested without an emulator/Android device.
