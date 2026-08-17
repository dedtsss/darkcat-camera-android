# AP-260817-1631 · DarkCat Field/Night stabilization

Status: implementation complete; candidate ready for remote validation.

Implemented the smallest coherent fix on `agent/field-night-stabilization`:

- Preserved the existing OEM Camera2 Night/extension pipeline and JPEG quality path.
- Added explicit service camera ownership handoff diagnostics and independent Field camera startup.
- Kept Field persistent while adding accelerometer-driven HOT/GRACE/3-minute STANDBY behavior; STANDBY pauses repeating work and motion returns it to HOT.
- Applied Vault as Field’s effective destination without changing the saved user storage preference.
- Routed Field JPEG staging, publication, recovery, and CAT diagnostics through the effective destination.
- Added bounded Field ownership, motion, trigger/result, storage, and thermal/power CAT evidence.
- Added JVM coverage for standby transitions and effective storage policy.

Local validation (SDK `/home/codex/Android/Sdk`):

- `testDebugUnitTest` — PASS
- `compileDebugAndroidTestJavaWithJavac` — PASS
- `assembleDebug` — PASS
- `lintDebug` — PASS
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 7,212,687 bytes

Remote evidence and handoff:

- GitHub Actions heavy validation: run after publication; evidence will be recorded in issue #325.
- Draft PR: target `agent/cat-log-mvp`; no merge/deploy.
- Physical Pixel OEM Night check: PENDING.
- Physical Xiaomi Field/standby/storage check: PENDING.
- DarkCat PR #5: untouched.

The final candidate commit SHA, Actions run, artifact URL/checksum, and Draft PR URL are recorded in the terminal issue update.
