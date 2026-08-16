# CAT UI Test v0.1

CAT UI is a small Maestro-based smoke layer for the existing DarkCat Camera 0.5 UI. It preserves checklist IDs `01..36`, uses the visible product surface plus stable resource IDs, and does not replace the camera engine, add a backend, or create a dashboard.

## Expanded Maestro Cloud suite

The stable eight-flow smoke under `.maestro/flows/` remains independent and unchanged. The self-contained Cloud expansion is under `.maestro/cloud/` and is launched only by the manual **Maestro Cloud expanded CAT** GitHub Actions workflow. The workflow builds the APK in GitHub Actions (never on Bruce), then uploads `.maestro/cloud` with the GitHub repository's `MAESTRO_API_KEY` and `MAESTRO_PROJECT_ID` secrets.

It covers CAT `11`, `13`, `14`, `20`–`23`, `25`, `27`–`35` with Cloud-supported `setLocation`, orientation, lock/power/volume keys, Android notification shade, cross-app launch, AI visual assertions and AI text extraction. Read `.maestro/cloud/README.md` before interpreting failures: Gallery/receiver availability, lockscreen semantics, haptic perception, real GNSS and physical-camera claims deliberately remain bounded.

For targeted Cloud evidence, dispatch with `include_tags=cat-20` (orientation/stamp) or another exact `cat-XX` tag. On the PR #5 branch, the GitHub workflow also runs CAT-20 first after a push and proceeds to the full `cloud-expanded` suite only if that target succeeds; this exists because GitHub does not register a `workflow_dispatch` file until it is on the default branch. Dispatch with the default `include_tags=cloud-expanded` also runs target then full regression.

## Prerequisites

- Linux shell.
- Java 17 or newer (`java -version`).
- Android SDK Platform-Tools with `adb` on `PATH`.
- Maestro CLI installed through the supported Linux installer, for example:

  ```sh
  curl -Ls "https://get.maestro.mobile.dev" | bash
  ```

- A debug APK built from this branch when the runner should install it:

  ```sh
  ./gradlew assembleDebug
  ```

Maestro and ADB are intentionally not vendored. Android Studio GUI is not required.

## One-command Linux runs

For exactly one connected Android device:

```sh
./scripts/run-cat-ui.sh --apk app/build/outputs/apk/debug/app-debug.apk
```

For an explicit device, which is required when more than one device is connected:

```sh
./scripts/run-cat-ui.sh --device <ANDROID_SERIAL> --apk app/build/outputs/apk/debug/app-debug.apk
```

The runner does not clear app state by default. This protects test-created media and makes a repeatable installed-APK pilot possible. Use a disposable test profile/device for destructive checks. The script never deletes unrelated user data and returns non-zero on a failed Maestro assertion or preflight blocker.

### Explicit fresh-install mode

`adb install -r` and `launchApp: clearState: false` are deliberately **not** a clean-install test. They preserve app data. To test check `01` as a clean install, use only a disposable device/profile and explicitly opt in:

```sh
./scripts/run-cat-ui.sh --device <ANDROID_SERIAL> \
  --apk app/build/outputs/apk/debug/app-debug.apk --fresh-install
```

`--fresh-install` first uninstalls `ru.darkcat.camera`, then installs the APK without `-r`; it deletes all DarkCat app data on that selected device. It is never implied by `--apk` and never runs by default.

## Pixel 7 / GrapheneOS

Enable developer options and USB debugging on the real Pixel 7, then identify its ADB serial:

```sh
adb devices -l
./scripts/run-cat-ui.sh --device <PIXEL_7_ADB_SERIAL> --apk app/build/outputs/apk/debug/app-debug.apk
```

The `<PIXEL_7_ADB_SERIAL>` value must be replaced with the actual connected device ID. The command is Linux-only and does not imply that a Pixel 7 is available to the current runner.

### Opt-in Pixel 7 Field Mode pilot

The default suite never starts Field Mode. On a real **Google Pixel 7** test profile, grant Camera, precise Location and notification permissions first, leave Field Mode and persistent user GPS OFF, then run:

```sh
./scripts/run-cat-ui.sh --device <PIXEL_7_ADB_SERIAL> \
  --apk app/build/outputs/apk/debug/app-debug.apk --pixel7-field
```

The runner requires an explicit serial and rejects any device whose `ro.product.model` is not exactly `Pixel 7`. It runs the tagged `cat-pixel7-hardware` flows for `22`, `23`, `25`, `30`–`33`, restores its test-created persistent GPS request, and never converts an emulator result into a hardware PASS.

To opt in to the lock/Volume+ sequence as well:

```sh
./scripts/run-cat-ui.sh --device <PIXEL_7_ADB_SERIAL> \
  --apk app/build/outputs/apk/debug/app-debug.apk --pixel7-field --pixel7-field-lock
```

This sends `KEYCODE_POWER`, waits 30 seconds, sends `KEYCODE_VOLUME_UP`, wakes the screen, and waits up to 90 seconds for the operator to unlock normally. No PIN, password, fingerprint or other credential is requested, read or automated. Checks `24`, `26`, `27` and the haptic/GNSS parts of `25`, `28`, `29` remain manual/partial unless real evidence proves them.

## Emulator validation

An emulator is useful for selectors, orientation commands, screenshots and settings navigation, but it cannot prove lens field of view, GNSS progression, OEM Night semantics, haptic strength, locked-screen behavior, MediaStore visibility in another app, or Pixel/GrapheneOS behavior.

With an Android SDK emulator available:

```sh
emulator -list-avds
emulator -avd <AVD_NAME>
adb devices -l
./scripts/run-cat-ui.sh --device <EMULATOR_SERIAL> --apk app/build/outputs/apk/debug/app-debug.apk
```

If KVM/virtualization is unavailable, record that exact blocker in the run report and continue with shell/YAML/Gradle validation. Do not provision unrelated infrastructure or call emulator results hardware evidence.

## Flows and artifacts

Flows live in `.maestro/flows/`:

- `01-launch-main.yaml` — `01..03`;
- `02-storage-gps.yaml` — `04..08`;
- `03-settings.yaml` — `09`, `10`, `34`, `35`;
- `04-gallery-mode.yaml` — explicitly sets Gallery mode before `11`, `12`, `15`;
- `04-vault-mode.yaml` — explicitly sets Vault mode before `13`, `14`, `15`;
- `05-lens-rotate-stamp.yaml` — `16..21`;
- `06-field-mode.yaml` — `34`, `35` configuration surface only;
- `07-burst.yaml` — `36`.

Hardware-only Pixel 7 flows live under `.maestro/hardware/` and only the explicit `--pixel7-field` runner flag invokes them. This keeps device-affecting Field Mode and lock behavior out of ordinary and emulator runs.

Every flow carries stable `cat-XX` tags. Mandatory evidence commands are present for `02`, `05`, `09`, `16`, `20` (portrait and landscape) and `21`. The mapping and rationale for every check are in [CAT_UI_CHECKLIST.md](CAT_UI_CHECKLIST.md).

Each run writes to:

```text
build/cat-ui/<run-id>/
├── cat-ui-junit.xml
├── cat-ui-report.html
├── cat-ui-results.json
├── cat-ui-results.md
├── console.log
├── commands.txt
├── device-info.txt
├── flow-results.tsv
├── actuals.tsv
├── CAT_UI_CHECKLIST.md
└── maestro/            # per-flow screenshots/logs/JUnit metadata when supported
```

`cat-ui-results.json` and `cat-ui-results.md` are the authoritative per-check handoff: each `01..36` contains `PASS | FAIL | PARTIAL | MANUAL | BLOCKED`, action, expected, automatically observed actual when available, evidence paths, and an explicit reason for every failure/blocker. The HTML merely renders the same local result data; it is not a dashboard or custom test engine. The JUnit report retains one testcase per CAT check, while Maestro-native per-flow output remains under `maestro/` when supported.

## Interpreting results

- `PASS` is emitted only when the associated flow actually ran and completed, with mandatory evidence present.
- `PARTIAL` is evidence for the observable subset only; read the boundary in the mapping before reporting it.
- `MANUAL` must be completed on the named device with evidence.
- `BLOCKED` means a required flow could not run (for example, missing ADB/Maestro, no device, or omitted opt-in Pixel flow); it is not a test PASS.

The runner and report must never infer a Pixel 7 PASS from an emulator, APK build, protocol state, or VPS availability. Physical results are recorded only after a real Pixel 7 / GrapheneOS execution.
