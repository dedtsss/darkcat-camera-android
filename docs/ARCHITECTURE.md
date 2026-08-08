# DarkCat Camera architecture

DarkCat Camera is a separate Android application (`ru.darkcat.camera`). DarkCat CRM is not a module or dependency of this APK.

## Layers

- `com.linkedcamera.app.*`: imported Linked Camera/Open Camera camera UI and camera engine. It remains the source of ordinary capture quality and device compatibility.
- `ru.darkcat.camera.data`: `CaptureContext`, media records, settings and SQLite state.
- `ru.darkcat.camera.crypto`: Android Keystore key generation, streaming AES-256-GCM file format and credential encryption.
- `ru.darkcat.camera.vault`: recovery-safe capture adapter, encrypted vault, thumbnails, protected gallery and crosshair burn-in.
- `ru.darkcat.camera.upload`: structured WorkManager queue and provider interface.
- `ru.darkcat.camera.ui`: DarkCat settings, editor, vault viewer and preview controls.

## Capture pipeline

`CAPTURE → optional EDIT → optional crosshair stamp → recovery-pending copy → streaming AES-GCM → UUID vault → SQLite ENCRYPTED/QUEUED → WorkManager provider → remote verification → KEEP LOCAL or verified delete`

Secure Mode is ON by default on Android 6+; its camera boundary is connected to Linked Camera's file and MediaStore completion callbacks. MediaStore photos are intercepted while pending. A source is deleted only after a recovery copy exists and the vault database row is committed. On encryption/database/disk/process failure, recovery-pending plaintext is deliberately retained for explicit recovery.

Secure Mode OFF leaves normal Linked Camera/MediaStore behavior available.

## Crosshair geometry

The preview crosshair is a child of Linked Camera's actual `preview` FrameLayout and is added after the camera surface is created, so its center is the preview/crop center rather than the screen center. STAMP draws the same centered geometry into the final JPEG bitmap after camera processing. Default is OFF.

## Intent contract

Future CRM callers may send action `ru.darkcat.camera.action.CAPTURE` with `ru.darkcat.camera.extra.CAPTURE_CONTEXT` JSON or individual extras: `CRM_OBJECT_ID`, `INSPECTION_ID`, `TASK_ID`, `USER_ID`, and `CUSTOM_TAGS`. Missing context is valid.
