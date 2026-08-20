# Pixel 7 physical acceptance checklist

Run on a Pixel 7 with camera permission and a Bluetooth volume remote:

- [ ] Start camera with screen on; preview is live and Capture writes a full-resolution JPEG.
- [ ] Press Volume+ and confirm exactly one capture; repeat with Bluetooth media control.
- [ ] Turn screen off and lock; confirm the foreground service remains alive and capture completes. Screen-off locked Volume+ behavior is physical Pixel 7 acceptance; it is not source-proven.
- [ ] Unlock and confirm preview/session recovery without duplicate camera ownership.
- [ ] Confirm GPS permission, coordinates and timestamp are present when available; verify graceful `GPS unavailable` otherwise.
- [ ] Inspect JPEG dimensions, EXIF orientation/time, deterministic name and index behavior.
- [ ] Confirm no plaintext secret or credential is included in diagnostics/update traffic.
- [ ] Verify release/update and diagnostic endpoints are HTTPS-only and build identity matches the CI commit.

This checklist is intentionally not marked PASS by Cloud, emulator, or source-only checks.
