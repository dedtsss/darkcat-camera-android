# Expanded Maestro Cloud suite

Run this directory separately from `.maestro/flows/`; the existing eight-flow smoke remains unchanged.

```sh
maestro cloud --app-file app-debug.apk --flows .maestro/cloud --device-os android-34 --device-model pixel_6
```

The suite is intentionally self-contained per flow because Maestro Cloud resets devices between flows. It uses fixed mocked Moscow coordinates (`55.755826`, `37.617300`) only to make UI/location output deterministic; this is not a GNSS accuracy result.

`assertNoDefectsWithAI` and the stable-UI `assertWithAI` checks are hard gates here (`optional: false`) and their Cloud reports are required evidence for visual outcomes. `extractTextWithAI` records stamp and chooser text as Cloud evidence. Camera-preview pixels are explicitly excluded from the CAT-20 AI chrome check: Cloud can expose a black virtual-camera feed even while the camera UI is healthy. That observation is retained as `CLOUD_PARTIAL` / `CLOUD_LIMITATION`, never as a claim that the preview was physically validated.

The cropped CAT-34 Field-settings card uses `takeScreenshot` and an `assertScreenshot`/`cropOn` guard behind `CLOUD_APPROVED_VISUAL_BASELINES=true`. The workflow defaults it to `false` until a reviewed image is committed at `baselines/cloud-settings-field-card.png` from the exact Cloud API/device. A dynamic camera preview, another device, or an unreviewed first run is never an acceptable baseline. Do not use a full-screen baseline for the preview.

## First-failure classification

Classify a new failure before changing app code:

1. `TEST_BUG` — a selector, timing assumption, or test assertion disagrees with a successful Cloud hierarchy/video.
2. `CLOUD_LIMITATION` — the Cloud image lacks Google Photos/a receiving app, does not expose notification shade/lock behavior, or cannot provide a physical camera/GNSS property. Preserve the Cloud video, hierarchy, and screenshot.
3. `APP_BUG` — a stable product assertion fails after the hierarchy/video excludes the two cases above.
4. `UNKNOWN` — insufficient evidence; retain artifacts and do not change product behavior.

`com.google.android.apps.photos` is intentionally launched only by the CAT-11/13/14 system-gallery probe. If it is absent, that isolated flow is `CLOUD_PARTIAL` with `CLOUD_LIMITATION`; it is not an application failure. Likewise, the Android share sheet must be visible for CAT-14, but a real hand-off is only automated when the Cloud image exposes a deterministic receiver.

CAT-27/28 are Cloud lock simulations. Maestro 2.8.0 can issue `lock`, `volume up`, and `power`, but it has no fixed-duration sleep primitive, so this suite cannot prove a physical 30-second secure lockscreen or haptic.

## Bruce orchestration

GitHub Actions builds and publishes the APK artifact only. Maestro Cloud runs are deliberately launched from Bruce with its protected `MAESTRO_CLOUD_API` and `MAESTRO_CLOUD_PROJECT_ID`; do not add or mirror those values in GitHub Secrets. Download the artifact for the exact successful build run, verify `BUILD_INFO` and `SHA256SUMS`, then invoke the installed Maestro CLI from Bruce. Start with `--include-tags cat-20`; run `--include-tags cloud-expanded` only after it passes.
