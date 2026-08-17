# AP-260817-1631 · Implementation report

## Scope and safety

This candidate starts from `7ce9d5f53ed354fc9daa83d74e095c9c81ac9033` on the CAT Log MVP line. The existing Pixel OEM Night and Camera2 extension flow was retained; no CameraX or custom weaker Night implementation was introduced.

## Changes

`FieldCameraSessionOwner` now exposes an explicit standby transition that stops and resumes the existing Camera2 repeating request without closing/reopening the session. `FieldModeService` keeps the service persistent, uses a batched normal-rate accelerometer listener, records ownership transitions, and applies HOT → GRACE → STANDBY after three stationary minutes; motion returns the service to HOT.

`DarkCatSettings.effectiveStorageMode` makes Field captures use Vault while preserving the configured preference. Field publication and legacy recovery use this effective policy. Field staging/commit/recovery, capture outcomes, motion transitions, ownership, and bounded thermal/power heartbeats are emitted through the existing CAT Log APIs and privacy allowlist.

## Verification

The following completed successfully locally:

```text
./gradlew testDebugUnitTest compileDebugAndroidTestJavaWithJavac assembleDebug lintDebug --no-daemon
```

The generated debug APK is `app/build/outputs/apk/debug/app-debug.apk` (7,212,687 bytes). Remote GitHub Actions validation is required for the final candidate and is reported in issue #325. Device validation remains PENDING for Pixel OEM Night quality and Xiaomi Field behavior.

## Release boundary

One final `[build-apk]` candidate commit is used for the Draft PR into `agent/cat-log-mvp`. No merge, deploy, or change to DarkCat PR #5 is authorized.
