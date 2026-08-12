# Night capture study — DarkCat Camera 0.5

## What 0.5 does now

DarkCat uses the official Camera2/OEM Night extension only when the active camera reports `CameraExtensionCharacteristics.EXTENSION_NIGHT` on Android 12 or newer. The compact `Ночь` control is disabled if that capability is absent and is disabled while Field Mode is active. The retained Linked/Open Camera engine applies the extension through its existing `X_Night` photo mode; DarkCat does not replace the camera session or add CameraX.

Diagnostics exports, per logical and physical camera, the official Night-extension availability, extension modes, exposure-time and ISO ranges, AE/OIS modes, reprocessing/ZSL signals, and low-light-boost availability when the platform exposes that key. These are capabilities, not image-quality results.

## Deliberately not implemented

There is no custom long-exposure or video ring buffer in 0.5. There is also no custom multi-frame Night stack. A capability report cannot prove that a particular OEM Night result is stable, aligned, or usable on a selected physical lens.

## Candidate follow-up design (not enabled)

If OEM Night is unavailable and a later hardware study justifies it, the bounded design is a user-visible still-photo flow: capture a short 1–3 second bounded burst, reject frames with gyroscope/motion and alignment error, align surviving frames, merge only after sufficient overlap, and retain the sharpest individual fallback when alignment fails. It would require its own memory budget, cancellation semantics, EXIF/metadata policy, Field Mode interaction rules, and real-device validation. It must not run while locked in Field Mode and must not be presented as a zero-lag capture path.

## Hardware evidence required

Run the Night rows in `PIXEL7_TEST.md` on a real Pixel 7/GrapheneOS and Xiaomi 12 Lite/HyperOS. Attach the redacted diagnostics JSON and A/B notes. Until then, all Night entries are **PENDING**, not PASS.
