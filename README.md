# DarkCat Camera Android

Clean Android implementation for protected, offline-first photo/video capture for DarkCat CRM.

The first slice is intentionally small but real: CameraX preview/capture, front/rear switching, tap-to-focus, zoom, flash/torch, exposure compensation, optional location, visible grid/crosshair/stamp, FAST and EDIT photo flows, AES-GCM protected private storage, metadata SQLite records, a vault screen, and a persistent WorkManager upload abstraction with a deterministic fake uploader.

Build with `./gradlew test assembleDebug`.
