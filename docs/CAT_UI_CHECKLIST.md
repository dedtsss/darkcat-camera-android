# CAT UI Test v0.1 — checklist mapping

This is the checked-in mapping for the 36-point DarkCat Camera pilot. IDs `01`–`36` are stable and are repeated in Maestro flow names, tags, screenshot names and reports.

`AUTO` means the flow contains a deterministic assertion for the requested observable behavior. `PARTIAL` means Maestro exercises only the observable portion and the remaining acceptance condition needs device evidence or a human check. `MANUAL` means automation must not convert the requested hardware or system behavior into a false pass.

| ID | Class | Flow / evidence | Automated observable | Remaining boundary |
|---:|:---:|---|---|---|
| 01 | AUTO | `01-launch-main.yaml`, `01-launch-main.png` | Launches `ru.darkcat.camera`, waits for CAT IDs, rejects visible Open Camera/What's New startup text. | Clean-install permission wording varies by Android/OEM. |
| 02 | AUTO | `01-launch-main.yaml`, `02-main.png` | Asserts top bar, last-shot, shutter and lens controls. | Readability/no overlap is confirmed by evidence review. |
| 03 | AUTO | `01-launch-main.yaml`, `03-system-main.png` | Produces a Maestro screenshot artifact. | Android screenshot storage must be checked on the named device. |
| 04 | PARTIAL | `02-storage-gps.yaml`, `04-storage-toggle.png` | Taps the shield/storage control and keeps the stable control visible. | Green/gray semantic state and persistence need device evidence. |
| 05 | PARTIAL | `02-storage-gps.yaml`, `05-gps-panel.png` | Opens live GPS panel and asserts owner, accuracy and state text. | Real accuracy and GPS Locker action need a location-enabled device. |
| 06 | PARTIAL | `02-storage-gps.yaml` | Confirms the GPS panel exposes the persistent-Locker action. | Service start without Settings and changing accuracy are device checks. |
| 07 | MANUAL | — | — | Observe changing GNSS values over time near a window/outside. |
| 08 | PARTIAL | `02-storage-gps.yaml` | Confirms GPS panel can be closed without leaving the camera. | Locker stop/ordinary-camera location ownership needs device evidence. |
| 09 | AUTO | `03-settings.yaml`, `09-settings-capture.png` | Opens Съёмка and asserts resolution, WB, brightness, flash, Night and Save controls. | Screenshot review confirms no clipping or overlap. |
| 10 | PARTIAL | `03-settings.yaml` | Reaches the real resolution control on the active camera. | Selecting a dimension and persistence require a real camera. |
| 11 | PARTIAL | `04-gallery-vault-viewer.yaml` | Captures through the shutter and follows the last-shot path. | MediaStore/Pictures/DarkCat visibility in another app is not asserted. |
| 12 | PARTIAL | `04-gallery-vault-viewer.yaml`, `12-viewer.png` | Opens the last shot and asserts Viewer Edit/Share/Delete controls. | Swipe behavior and destructive Delete remain device checks. |
| 13 | PARTIAL | `04-gallery-vault-viewer.yaml` | Exercises the same viewer path after the current storage state. | Absence from system Gallery and presence in DarkCat Gallery need device evidence. |
| 14 | PARTIAL | `04-gallery-vault-viewer.yaml` | Viewer exposes Share. | Chooser recipient access must be verified manually. |
| 15 | PARTIAL | `03-settings.yaml`, `04-gallery-vault-viewer.yaml` | Reaches Settings and Viewer screenshot points. | Android screenshot behavior on each screen needs device evidence. |
| 16 | PARTIAL | `05-lens-rotate-stamp.yaml`, `16-lenses.png` | Opens Объективы, asserts the chooser and rejects raw Camera ID labels. | Human review records understandable real lens names. |
| 17 | PARTIAL | `05-lens-rotate-stamp.yaml` | Reaches the capability-derived lens chooser. | Sequential switching without a hang is physical-camera validation. |
| 18 | PARTIAL | `05-lens-rotate-stamp.yaml` | Captures the chooser/zoom-capable UI path. | Useful zoom values must be reviewed on the device. |
| 19 | MANUAL | — | — | Verify real sub-1x field of view, not a digital crop. |
| 20 | PARTIAL | `05-lens-rotate-stamp.yaml`, `20-portrait.png`, `20-landscape.png` | Changes orientation and asserts CAT chrome remains present. | Preview crop/black-field correctness needs evidence review. |
| 21 | PARTIAL | `05-lens-rotate-stamp.yaml`, `21-technical-stamp.png` | Produces a post-shutter evidence screenshot. | Coordinates, accuracy, sequence plausibility and frame placement require review. |
| 22 | PARTIAL | `06-field-mode.yaml`, `22-field-settings.png` | Asserts Field settings and safety text. | Field start and auto GPS ownership need a real device. |
| 23 | MANUAL | — | — | Verify GPS owner explicitly reports `Field Mode` while persistent toggle is exercised. |
| 24 | MANUAL | — | — | Wait for and record a real accuracy better than 7 m. |
| 25 | PARTIAL | `06-field-mode.yaml` | Exposes the Volume+/haptic configuration controls. | Volume+ capture is device-testable; perceived vibration is manual. |
| 26 | MANUAL | — | — | Confirm strict GPS block and distinct fail haptic below 7 m. |
| 27 | MANUAL | — | — | Lock using the real power action and observe the 30–60 second state. |
| 28 | MANUAL | — | — | Volume+ while a real Pixel 7 is locked; no fake lockscreen path. |
| 29 | MANUAL | — | — | Unlock timing and GPS warm state require a physical run. |
| 30 | PARTIAL | `06-field-mode.yaml` | Exercises the visible Field settings surface. | Field-owned Locker stop after disabling needs device evidence. |
| 31 | MANUAL | — | — | Verify user-owned Locker survives Field OFF. |
| 32 | MANUAL | — | — | Verify notification Stop all stops both owners. |
| 33 | MANUAL | — | — | Inspect Android notifications for truthful camera/GPS state. |
| 34 | PARTIAL | `03-settings.yaml`, `34-haptics-settings.png` | Asserts both haptic settings and test buttons. | Physical perceived strength remains manual. |
| 35 | PARTIAL | `03-settings.yaml` | Asserts the OEM Night capability-gated setting is visible. | PASS requires a device capability report; unavailable must remain unavailable. |
| 36 | PARTIAL | `07-burst.yaml`, `36-burst-stability.png` | Sends three rapid shutter actions and asserts CAT chrome/thumbnail controls remain. | Real sequence advancement and no camera hang need device evidence. |

## Coverage summary

- AUTO: `01`, `02`, `03`, `09`.
- PARTIAL: `04`, `05`, `06`, `08`, `10`–`18`, `20`–`22`, `25`, `30`, `34`–`36`.
- MANUAL: `07`, `19`, `23`, `24`, `26`–`29`, `31`–`33`.

The mapping intentionally does not claim a Pixel 7 or emulator PASS. A physical run must attach its artifacts and record the actual device ID, Android version and observed evidence separately.
