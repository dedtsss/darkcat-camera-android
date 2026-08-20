# DarkCat Camera MVP-1 research decision

Date: 2026-08-20  
Scope: CameraX + service-owned lifecycle + full-resolution JPEG on Pixel 7.

## Bounded findings

AndroidX CameraX (`Preview` and `ImageCapture`) plus a foreground
`LifecycleService` provide the compatible building blocks. The service owns the
provider/session; the activity supplies only the normal `PreviewView` surface
and commands. Screen-off, lock-screen, Bluetooth, GPS, and OEM behavior are
integration behavior and are not proven by source review.

Research verification boundary: the external shell had no network access in
this run. These are exact pointers for later review, not verified license
conclusions. No external source code was copied.

* GrapheneOS Camera: https://github.com/GrapheneOS/Camera — license status
  unverified here; inspect LICENSE and the relevant source commit before reuse.
* Myzel394/Alibi: https://github.com/Myzel394/Alibi — behavior pointer only;
  license unverified here and no code imported.
* zhanglinleo1-maker/Alibi-Cam: https://github.com/zhanglinleo1-maker/Alibi-Cam
  — license and background behavior unverified; not imported.
* anonfaded/FadCam: https://github.com/anonfaded/FadCam — behavior pointer only;
  license unverified here and no code imported.
* CameraX guide: https://developer.android.com/media/camera/camerax — API
  pointer only; dependency notices must be checked for distribution.

## Decision

Use only AndroidX CameraX plus platform APIs in this repository. Implement the
service-owned graph, modern camera/location foreground service, full-resolution
JPEG with EXIF, deterministic naming/indexing, GPS state and a separate
quality-94 visual stamp derivative. MediaSession/VolumeProvider handles media
and remote-volume paths; no ineffective volume broadcast receiver is used.

## Unverified acceptance

Physical Pixel 7 checks, HTTPS Bruce endpoint compatibility, tagged Actions
execution, and all device acceptance remain unverified. Cloud, emulator, or
source-only checks must not be reported as Pixel 7 success.
