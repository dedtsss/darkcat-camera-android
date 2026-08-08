# DarkCat Camera architecture

## Decision

DarkCat Camera is a clean single-module Android application for the first slice, not a Tella fork. The product boundary is deliberately narrow:

`CameraX/Camera2 backend -> private temporary capture -> AES-GCM vault -> SQLite metadata -> WorkManager MediaUploader`

The modules are package seams inside `app` today and can become Gradle modules later without changing the domain interfaces.

## Why clean implementation

Tella already solves much of offline media handling, but its camera activity is coupled to Tella reports, metadata return intents, Tella-specific `VaultFile`, RxJava/Hilt application state and many unrelated screens. Extracting it would leave a large dependency surface and preserve an unauthenticated file cipher. A small clean project keeps camera reliability, the security model and the CRM seam explicit. The decision is reversible at the API level: a future Tella vault adapter could implement the repository contract if its cryptographic and licensing requirements are accepted.

## Runtime components

- `MainActivity`: lifecycle-owned camera screen. CameraX 1.6.1 binds Preview, ImageCapture and VideoCapture; it exposes front/rear, tap-to-focus, continuous AF/AE defaults, zoom, flash/torch, exposure compensation, grid, crosshair, stamp and FAST/EDIT.
- `LocationProvider`: non-blocking LocationManager cache. It observes GPS/network when permission is available and returns the most recent fix; capture never waits indefinitely.
- `VaultRepository`: assigns UUID/sequence, creates encrypted media and encrypted thumbnails, records structured metadata and cleans temporary files.
- `MediaDatabase`: SQLiteOpenHelper schema for media metadata and upload state. It is private app data and is not exposed through MediaStore.
- `AuthenticatedFileCipher`/`FileCrypto`: AES-256-GCM with a random 12-byte IV per file. Android Keystore stores the non-exportable master key alias `darkcat.camera.vault.v1`.
- `UploadScheduler`/`UploadWorker`: unique WorkManager 2.11.2 work per media UUID, connected-network constraint and exponential backoff. The `MediaUploader` interface is the future DarkCat/S3/WebDAV/Nextcloud boundary; the default implementation is a deterministic local fake that verifies the encrypted-file checksum.
- `VaultActivity`/`MediaViewerActivity`: internal gallery and authenticated in-app photo view. No ACTION_VIEW or public-storage URI is used.
- `EditorActivity`: real first-stage rotate and pinch-zoom editor. Save produces one final JPEG which enters the same vault pipeline. The annotation seam is intentionally separate for crop/text/lines/arrows/shapes/stickers.

## Data and state

`CaptureContext` is independent of CRM UI and carries `crmObjectId`, `inspectionId`, `taskId`, `userId` and custom tags. The structured `CaptureMetadata` holds GPS, accuracy, altitude, orientation, comments, tags and stamp options. It is serialized in a DB JSON column while high-value query fields are also stored in columns.

The upload state machine is:

`CAPTURED -> ENCRYPTED -> QUEUED -> UPLOADING -> UPLOADED -> VERIFIED -> LOCAL_DELETE_PENDING -> LOCAL_DELETED`

Failure branches are `FAILED_RETRYABLE` and `FAILED_PERMANENT`. The MVP does not delete local media after verification; deletion policy is intentionally a repository decision to be enabled only after a real server contract and retention policy exist.

## Upload models reserved by the interface

1. Encrypted-at-client upload: `MediaUploader` receives the `.dcv` ciphertext and checksum. The server stores opaque ciphertext and confirms the checksum/idempotency key.
2. HTTPS plaintext upload: a future adapter can decrypt to a short-lived private stream or server-side session, but this must be an explicit security choice. It is not implemented by the fake uploader.

The MVP uses model 1 and does not hard-code any cloud provider or CRM endpoint.

## Camera behavior

CameraX is the reliability baseline. Camera2 Interop is reserved for capability inspection and advanced controls. The app does not expose ISO/shutter/manual focus/HDR/Night unless a future implementation confirms support on the active camera. Preview overlays are view-only and never enter the captured bytes. The optional stamp is a separate bitmap render before encryption.

Video capture is real CameraX Recorder output to an app-private cache file, followed by encrypted vault import and deletion of the plaintext source. Streaming encryption is not claimed in this slice; a future video module should evaluate a pipe/MediaCodec path or a short-lived internal staging file with crash cleanup.

## Android lifecycle and permissions

The manifest requests camera, microphone, coarse/fine location and notification permissions. Notification permission is reserved for a future visible upload notification; the MVP does not start a foreground service. WorkManager owns queued upload execution across process restarts/reboots. Scoped/public MediaStore access is not required because the app never saves primary media to DCIM/Pictures/Movies.
