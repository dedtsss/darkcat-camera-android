# Upstream research

Research snapshot: 2026-08-08 MSK. Repositories were cloned and inspected at source level, not only via README.

## Tella Android

- Official repository: [Horizontal-org/Tella-Android](https://github.com/Horizontal-org/Tella-Android)
- Snapshot: `main`, tag `v3.2.0(248)`, commit `b1a2a8afc25fb2d920c2ba5e210a738f4d32dacc`.
- License in the repository: MIT for the Tella code, with third-party notices in `NOTICE.txt`.
- FOSS comparison repository: [Horizontal-org/Tella-Android-FOSS](https://github.com/Horizontal-org/Tella-Android-FOSS), `master` at `2caf0177f98fe5f1106d04190270a5cda70ae7e1` (tag `v2.11.0(186)`). It removes trackers/proprietary dependencies and is useful for checking the open CameraX path.

### Actual stack and structure

The current Tella repository is a multi-module Java/Kotlin/XML application: `mobile`, `tella-vault`, `tella-keys`, `tella-database`, `shared-ui`, `tella-locking-ui`, and `pdfviewer`. Compose is limited to the PDF viewer module; the camera and vault UI are predominantly XML/view based.

The root build declares compile/target SDK 36, min SDK 21, AGP 8.9.1, Kotlin 2.2.0, CameraX 1.4.2, Hilt 2.57.1, Navigation 2.9.5, SQLCipher 4.6.1, AndroidX SQLite 2.4.0, WorkManager 2.9.1, Retrofit 3.0.0, OkHttp 4.12.0, Ktor 3.3.0, ExoPlayer 2.18.2 and RxJava 2.2.19. The FOSS branch is materially older: CameraX 1.3.4 and WorkManager 2.7.1.

### Camera

The relevant current source is `mobile/src/main/java/org/horizontal/tella/mobile/views/activity/camera/CameraActivity.kt`.

- CameraX `Preview`, `ImageCapture`, `VideoCapture<Recorder>`, `Recorder`, `PreviewView` and `ProcessCameraProvider` are used.
- `Camera2CameraInfo` is used for front/rear and flash capability detection; CameraX owns the lifecycle.
- `FocusMeteringAction` implements tap-to-focus and AE metering.
- A seek bar maps to `camera.cameraControl.setLinearZoom`.
- Flash modes use `ImageCapture` and torch uses `CameraControl.enableTorch`.
- Rotation is tracked by `OrientationEventListener`; target rotation is sent to ImageCapture/video and controls are rotated in the UI.
- CameraX writes a JPEG or MP4 to a Tella temporary file. JPEG bytes are then passed to `MediaFileHandler.saveJpegPhoto`; video is imported from the temporary file by `saveMp4Video`.
- Location is handled outside the camera use cases by `MetadataActivity`/`LocationProvider`. Capture can continue after permission/location fallback, but current Tella also has product-specific verification metadata and prompts.
- The current camera file still contains commented legacy CameraView code and Tella-specific `MetadataActivity`, `VaultFile`, upload scheduling and report-return modes. It is not a separable camera library.

### Vault and encryption

The useful source is `tella-vault/src/main/java/com/hzontal/tella_vault/BaseVault.java` and `CipherStreamUtils.java`.

- Encrypted content is stored under the configured vault root, normally app-private storage. Filenames are generated from a UUID-like `VaultFile.id` plus a MIME-derived extension.
- A `VaultFile` stores id, type, hash, path, MIME type, name, size, created time, duration, metadata and thumbnail bytes.
- The database is SQLCipher-backed. `tella-database` also has a media table and a media-upload table with status, uploaded size, retry count, metadata/manual flags and server id.
- `tella-keys` generates a 256-bit AES main key. The Android Keystore wrapper uses AES-GCM to wrap that key and requires recent device authentication for a short validity window.
- File content itself is written as a 16-byte IV followed by AES/CTR/NoPadding. The per-file AES key is derived with PBKDF2WithHmacSHA1, 1,000 iterations, using the filename as salt. This stream has no GCM/Poly1305 authentication tag; the separate SHA-256 field is metadata and is not a substitute for authenticated decryption.
- CameraX/codec output is temporarily plaintext in a private temp file. Tella deletes/imports these files via `MediaFileHandler`, but the lifecycle is still coupled to the camera and media handler code. Viewing/processing streams decrypted content and may materialize bytes for UI/player use.

Conclusion: Tella's vault API and file naming are useful design references, but its file cipher is not reused in DarkCat Camera. The DarkCat MVP uses AES-GCM with a Keystore-held key and authenticated corruption detection.

### Gallery, import and upload

Tella's gallery is a vault-backed list of `VaultFile` records with encrypted thumbnail bytes, media-type filters, viewers, metadata, delete, edit/import and sharing/connector flows. The data model is broad because the same gallery supports forms, audio, PDF, reports and Tella folders.

Tella schedules upload with WorkManager `NetworkType.CONNECTED` constraints. `TellaFileUploadSchedulerViewModel` and `ScheduleUploadReportFilesUseCase` enqueue a unique worker using `APPEND_OR_REPLACE` so captures arriving during an active upload are not silently dropped. The worker and database handle report grouping, server connectors, uploaded bytes and retries, but the flow is coupled to Tella reports and server records rather than a standalone media contract.

### Editor

Tella uses `com.vanniktech:android-image-cropper:4.5.0` for crop-related UI and `com.github.nak5ive:ink-android:1.0.3` for ink. The surrounding editor and metadata flows are coupled to Tella gallery objects. DarkCat does not copy those screens in the MVP; its first editor is a small real rotate/zoom pipeline with a clear seam for a future MIT/Apache annotation library.

## Open Camera

- Official source: [Open Camera SourceForge code](https://sourceforge.net/p/opencamera/code/).
- Snapshot: `master`, tag `v1.56.2`, commit `0dd4cbe78872df2c6e4eb6cee3fb0d5637b0f52e`.
- License: [GPL v3 or later](https://opencamera.org.uk/index.html#licence); Open Camera's own FAQ explicitly says an app using its source must be released under a GPL-compatible license unless separately licensed.

### What was inspected

The relevant source files are `app/src/main/java/net/sourceforge/opencamera/cameracontroller/CameraController2.java`, `CameraControllerManager2.java`, `preview/Preview.java`, `preview/camerasurface/*`, `ImageSaver.java`, `ExifHandler.java`, `LocationSupplier.java`, `VideoProfile.java` and `ui/DrawPreview.java`.

Open Camera's Camera2 implementation is mature because it owns a large compatibility layer: camera feature discovery/caching, `CameraCharacteristics` capability checks, device/OEM workarounds, explicit capture state machines for autofocus and AE precapture, flash/fake-precapture paths, burst/HDR/noise-reduction/focus bracketing, RAW readers, camera extensions, zoom/crop regions, high-speed video, video stabilization, orientation handling, error callbacks and device-specific recovery. `Preview` handles tap focus/metering, continuous focus, zoom gestures, preview transforms, video lifecycle and UI overlays. `ImageSaver`/`ExifHandler` handle GPS, timestamps, orientation, device EXIF filtering, stamps and post-processing.

### Feature decision for DarkCat

| Open Camera capability | DarkCat decision |
|---|---|
| Camera2 lifecycle, AF/AE state machines, capability discovery | Reproduce through CameraX public APIs; use Camera2 Interop only for capability reads and future manual controls |
| Preview, front/rear, tap-to-focus, zoom, flash/torch, rotation | MVP uses CameraX directly |
| Exposure compensation, auto exposure and auto white balance | MVP uses CameraX; manual exposure is an Advanced-panel extension |
| ISO, shutter time, manual focus, WB temperature | Future Camera2Interop controls, shown only when `CameraCharacteristics` says supported |
| Camera extensions / Night / HDR / stabilization | Future capability-gated options; no fake fallbacks |
| RAW, multi-shot HDR, noise reduction, focus bracketing, panorama | Not MVP; not needed for reliable CRM evidence capture |
| GPS/EXIF and visible stamps | DarkCat metadata is separate; optional visible JPEG stamp is rendered before encryption |
| OEM workarounds and error recovery | Build a device capability/quirk registry from field tests; do not copy the GPL compatibility layer |

### License review

No Open Camera source files, Java classes, resources, icons or code fragments were copied into this repository. Only public Android API behavior and architectural lessons were used. Therefore this project does not acquire Open Camera's GPL obligations from source reuse. This is an engineering record, not legal advice; a future decision to copy Open Camera code would require a deliberate GPL-compatible release or a separate commercial license.

## Other dependencies used by DarkCat

- AndroidX/Jetpack CameraX, WorkManager, AppCompat, Lifecycle, RecyclerView: Apache License 2.0.
- Gson: Apache License 2.0.
- Google Play services location: Apache License 2.0.
- Kotlin and Gradle/Android Gradle Plugin: Apache License 2.0.

The release process must generate a complete dependency notice before distribution.
