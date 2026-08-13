# CAT UI Test v0.1

CAT UI is a small Maestro-based smoke layer for the existing DarkCat Camera 0.5 UI. It preserves checklist IDs `01..36`, uses the visible product surface plus stable resource IDs, and does not replace the camera engine, add a backend, or create a dashboard.

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

## Pixel 7 / GrapheneOS

Enable developer options and USB debugging on the real Pixel 7, then identify its ADB serial:

```sh
adb devices -l
./scripts/run-cat-ui.sh --device <PIXEL_7_ADB_SERIAL> --apk app/build/outputs/apk/debug/app-debug.apk
```

The `<PIXEL_7_ADB_SERIAL>` value must be replaced with the actual connected device ID. The command is Linux-only and does not imply that a Pixel 7 is available to the current runner.

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
- `04-gallery-vault-viewer.yaml` — `11..15`;
- `05-lens-rotate-stamp.yaml` — `16..21`;
- `06-field-mode.yaml` — `22..35` surface checks;
- `07-burst.yaml` — `36`.

Every flow carries stable `cat-XX` tags. Mandatory evidence commands are present for `02`, `05`, `09`, `16`, `20` (portrait and landscape) and `21`. The mapping and rationale for every check are in [CAT_UI_CHECKLIST.md](CAT_UI_CHECKLIST.md).

Each run writes to:

```text
build/cat-ui/<run-id>/
├── cat-ui-junit.xml
├── cat-ui-report.html
├── console.log
├── commands.txt
├── CAT_UI_CHECKLIST.md
└── maestro/            # screenshots/logs/command metadata from Maestro when supported
```

The HTML file is a small human-readable run summary with links to evidence; it is not a dashboard or custom test engine. The JUnit file is the Maestro output when the installed CLI supports its JUnit formatter, with a deterministic preflight result when the run cannot start.

## Interpreting results

- `AUTO` is a real deterministic UI assertion.
- `PARTIAL` is evidence for the observable subset only; read the boundary in the mapping before reporting it.
- `MANUAL` must be completed on the named device with evidence.
- `BLOCKED` means the runner could not start (for example, missing ADB/Maestro or no device); it is not a test PASS.

The runner and report must never infer a Pixel 7 PASS from an emulator, APK build, protocol state, or VPS availability. Physical results are recorded only after a real Pixel 7 / GrapheneOS execution.
