# Night capture study — DarkCat Camera 0.5

## What 0.5 does now

DarkCat uses the official Camera2/OEM Night extension only when the active camera reports `CameraExtensionCharacteristics.EXTENSION_NIGHT` on Android 12 or newer. The compact `Ночь` control is disabled if that capability is absent and is disabled while Field Mode is active. The retained Linked/Open Camera engine applies the extension through its existing `X_Night` photo mode; DarkCat does not replace the camera session or add CameraX.

The Night toggle is a Camera Extension session boundary, not a preview-frame filter: the retained engine configures `X_Night` as an extension session and deliberately performs one controlled camera reopen on an extension/non-extension transition. This matches the [Camera2 extension-session contract](https://developer.android.com/reference/android/hardware/camera2/CameraExtensionSession): creating a new session closes the prior session, extension sessions own device-specific multi-frame processing, and they may override normal capture settings. The retained `MainActivity.updateForSettings()` implementation separately records the observed upstream risk: stopping/starting an extension session in place can leave preview frames absent or session creation hanging. The P7 hypothesis is therefore a leaked `X_Night` preference/repeated reconcile rather than a custom DarkCat frame processor.

For P7-NIGHT-01, the product-side fix preserves the actual pre-Night photo mode in `darkcat_night_restore_photo_mode`, restores it explicitly when Night is switched off, and removes that marker after scheduling the one normal restore transition. Repeated reconcile calls are idempotent and do not request another transition when the selected mode already matches. CAT Log records `night.toggle_requested`, `night.apply_started/completed`, and `night.restore_started/completed`; the completion is `PARTIAL` until the real-device preview is observed.

Diagnostics exports, per logical and physical camera, the official Night-extension availability, extension modes, exposure-time and ISO ranges, AE/OIS modes, reprocessing/ZSL signals, and low-light-boost availability when the platform exposes that key. These are capabilities, not image-quality results.

## Deliberately not implemented

There is no custom long-exposure or video ring buffer in 0.5. There is also no custom multi-frame Night stack. A capability report cannot prove that a particular OEM Night result is stable, aligned, or usable on a selected physical lens.

## Candidate follow-up design (not enabled)

If OEM Night is unavailable and a later hardware study justifies it, the bounded design is a user-visible still-photo flow: capture a short 1–3 second bounded burst, reject frames with gyroscope/motion and alignment error, align surviving frames, merge only after sufficient overlap, and retain the sharpest individual fallback when alignment fails. It would require its own memory budget, cancellation semantics, EXIF/metadata policy, Field Mode interaction rules, and real-device validation. It must not run while locked in Field Mode and must not be presented as a zero-lag capture path.

## Hardware evidence required

Run the Night rows in `PIXEL7_TEST.md` on a real Pixel 7/GrapheneOS and Xiaomi 12 Lite/HyperOS. Attach the redacted diagnostics JSON and A/B notes. Until then, all Night entries are **PENDING**, not PASS.
