# DarkCat Camera MVP-1 research decision

Date: 2026-08-20  
Scope: CameraX + service-owned lifecycle + full-resolution JPEG on Pixel 7.

## Bounded findings

The mature, compatible building blocks are AndroidX CameraX (`Preview` and
`ImageCapture`) and a foreground `LifecycleService` that owns the camera
provider/session. CameraX's `ImageCapture` is the supported full-resolution
JPEG path; the service must remain the single owner when the activity is
hidden or destroyed. Screen-off, lock-screen, Bluetooth volume control and
GPS are integration behavior and are not proven by source review.

Reviewed references (behavior and license only; no source copied):

* GrapheneOS Camera — Apache-2.0; privacy-first camera behavior and upstream
  Android camera integration reference.
* Myzel394/Alibi — GPL-3.0; background/lock recording behavior reference.
* zhanglinleo1-maker/Alibi-Cam — license and background-camera behavior must
  be rechecked before any reuse; not imported.
* anonfaded/FadCam — GPL-3.0; foreground-service recording and diagnostics
  reference; not imported.
* Android CameraX guide and samples — Apache-2.0/AndroidX terms; canonical
  API contract for `ProcessCameraProvider`, `Preview`, and `ImageCapture`.

## Decision

Use only AndroidX CameraX plus platform APIs in this repository. Implement a
foreground `LifecycleService` as the camera/session owner, expose a normal
activity UI with `PreviewView`, and request `ImageCapture` at
`CAPTURE_MODE_MINIMIZE_LATENCY`. Add screen-off/lock handling, volume and
Bluetooth media controls, GPS metadata, deterministic naming/indexing and an
optional technical stamp as explicit adapters around that owner.

No mature OSS combination was accepted wholesale: the reviewed alternatives
either use GPL terms, focus on video/background recording rather than a
CameraX JPEG MVP, or do not provide the required complete contract. This
keeps provenance clear and avoids copying incompatible code.

## Deferred / unproven

Physical Pixel 7 checks (screen off, lock, Bluetooth, GPS, full JPEG metadata,
camera hardware and thermal behavior), HTTPS Bruce update/diagnostic endpoint
compatibility, and GitHub Actions execution remain acceptance work.
