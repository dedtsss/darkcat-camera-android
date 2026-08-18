# AP-260817-1631 · DarkCat Field/Night stabilization

Status: IMPLEMENTATION COMPLETE; physical device acceptance remains pending on 2026-08-18. The prior `SUCCESS` wording was superseded by the post-review audit and is now replaced by this validated continuation result.

Verified published candidate:

- Branch: `agent/field-night-stabilization`
- Candidate baseline commit: `3bdb2941ce01ac1be2e8671816184ce439baa4ba` (`[build-apk] Stabilize Field night capture and standby`)
- Continuation checkpoints: `4915d06`, `eacd921`, `df27b41`, `986dd3b`, `3972eca`, `6e7eb1f`
- Draft PR: `dedtsss/darkcat-camera-android#7`, base `agent/cat-log-mvp`
- GitHub Actions run `32037511790`: SUCCESS for the exact candidate commit
- Existing useful work: effective Field→Vault policy, HOT/GRACE/STANDBY mechanism, Field/storage/thermal diagnostics, and tests

Post-review blocker groups addressed:

- Close the Field ON / visible-Activity ownership race so service-owned Camera2 cannot start before `activityVisible` ownership is established.
- Implement/fix the Night/CameraExtension lifecycle path required by the task contract.
- Route Night final JPEG through DarkCat stamp/metadata/effective-storage/recovery handling.
- Add CAT preview-health/FPS/stall diagnostics and Night progress + motion correlation.
- Fix CAT integrity/lifecycle issues, including `null` lines, `event_count` mismatch, and writer/clear/export lifecycle behavior.
- Replace continuous normal-rate accelerometer standby sensing with the requested low-power motion-sensor hierarchy/fallback design.

Final validation pass: `./gradlew testDebugUnitTest compileDebugAndroidTestJavaWithJavac assembleDebug lintDebug` — PASS. `git diff --check` — PASS.

The next `[build-apk]` candidate is now permitted by the staged workflow, but has not been created in this continuation. No merge or deploy was performed.

Physical acceptance remains pending for Pixel OEM Night quality and Xiaomi Field HOT/GRACE/STANDBY, storage, and preview behavior.

Release boundary remains unchanged: continue on this existing branch and Draft PR; no merge/deploy; do not modify or close DarkCat Camera PR #5. The next `[build-apk]` candidate should only be produced after the blocking implementation items above are addressed and validated.
