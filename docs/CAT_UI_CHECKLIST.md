# CAT UI Test v0.1 — checklist mapping

IDs `01`–`36` are stable in the flows, report and evidence names. `AUTO` means a deterministic assertion can become `PASS` only after its exact flow succeeded. `PIXEL AUTO` is the same, but only after the explicit `--pixel7-field` run verifies the selected device reports model `Pixel 7`. `PARTIAL` preserves a real automated observable but never upgrades an unobserved visual or hardware condition to PASS. `MANUAL` needs a human/device observation.

| ID | Class | Flow / evidence | Automated observable | Boundary preserved in result |
|---:|:---:|---|---|---|
| 01 | AUTO only with `--fresh-install`; otherwise PARTIAL | `01-launch-main.yaml`, `01-launch-main.png` | CAT UI appears and old startup text is absent. | Default `install -r` is not a clean-install test and remains PARTIAL. |
| 02 | PARTIAL | `01-launch-main.yaml`, `02-main.png` | Top bar, thumbnail, shutter and lens controls are reachable. | Readability/no-overlap is visual review. |
| 03 | MANUAL | `01-launch-main.yaml`, `03-maestro-main.png` | Maestro navigation evidence exists. | Android/system screenshot behavior is not proven by Maestro `takeScreenshot`. |
| 04 | PARTIAL | `02-storage-gps.yaml`, `04-storage-toggle.png` | Storage control is reachable. | Color/persistence needs device evidence. |
| 05 | PARTIAL | `02-storage-gps.yaml`, `05-gps-panel.png` | GPS owner, accuracy and state labels are visible. | Real location quality and service behavior need a location-enabled device. |
| 06 | PARTIAL | `02-storage-gps.yaml` | Persistent Locker action is exposed. | Start without Settings and changing accuracy need device evidence. |
| 07 | MANUAL | — | — | Observe live GNSS progression near a window/outside. |
| 08 | PARTIAL | `02-storage-gps.yaml` | GPS panel returns to CAT UI. | User/ordinary-camera ownership needs device evidence. |
| 09 | PARTIAL | `03-settings.yaml`, `09-settings-capture.png` | Capture controls are reachable. | Clipping/layout/readability is visual review. |
| 10 | PARTIAL | `03-settings.yaml` | Real resolution control is reachable. | Device dimensions and persistence need a real camera. |
| 11 | PARTIAL | `04-gallery-mode.yaml`, `11-gallery-mode.png`, `12-gallery-viewer.png`, `15-gallery-screen.png` | Flow explicitly sets Gallery mode, captures, records CAT sequence change and opens viewer. | External MediaStore Gallery/Pictures/DarkCat visibility remains device evidence. |
| 12 | PARTIAL | `04-gallery-mode.yaml`, `12-gallery-viewer.png` | Gallery-mode viewer exposes Edit/Share/Delete. | Swipe and Delete are not run on user content. |
| 13 | PARTIAL | `04-vault-mode.yaml`, `13-vault-mode.png`, `13-vault-viewer.png`, `15-vault-gallery-screen.png` | Flow explicitly sets Vault mode, captures, records CAT sequence change and opens viewer. | Absence from the system gallery remains device evidence. |
| 14 | PARTIAL | `04-vault-mode.yaml`, `13-vault-viewer.png` | Vault viewer exposes Share. | Android chooser and receiving-app access need device evidence. |
| 15 | MANUAL | settings/gallery/viewer Maestro evidence | Relevant screens are reached. | Android/system screenshots on each screen are not proven by Maestro screenshots. |
| 16 | PARTIAL | `05-lens-rotate-stamp.yaml`, `16-lenses.png` | Lens chooser opens and rejects raw Camera ID labels. | Human review records understandable real lens labels. |
| 17 | PARTIAL | `05-lens-rotate-stamp.yaml` | Capability-derived chooser is reached. | Sequential physical-lens switching must be run on a real camera. |
| 18 | PARTIAL | `05-lens-rotate-stamp.yaml` | Zoom-capable surface is reached. | Values must be useful on the device. |
| 19 | MANUAL | — | — | Verify a real wider field-of-view for any sub-1x choice. |
| 20 | PARTIAL | `05-lens-rotate-stamp.yaml`, `20-portrait.png`, `20-landscape.png` | CAT chrome stays visible across orientation commands. | Preview crop/black-field layout needs evidence review. |
| 21 | PARTIAL | `05-lens-rotate-stamp.yaml`, `21-technical-stamp.png` | Post-shutter stamp evidence is captured. | Coordinates, accuracy, sequence and real-frame placement need review. |
| 22 | PIXEL AUTO | `hardware/field-enable-gps.yaml`, `22-field-active.png` | Field is active and Field-owned GPS is visible. | Requires a real opt-in Pixel 7 run. |
| 23 | PIXEL AUTO | `hardware/field-enable-gps.yaml`, `23-field-gps-owner.png`, `23-field-user-gps.png` | Field ownership persists while a user GPS request is toggled and restored. | Requires a real opt-in Pixel 7 run. |
| 24 | MANUAL | — | — | Wait for real accuracy better than 7 m. |
| 25 | PARTIAL | `hardware/field-volume.yaml`, `25-volume-up.png` | Volume+ event is sent and runner checks CAT sequence change. | Perceived haptic remains manual. |
| 26 | MANUAL | — | — | Real poor-GNSS strict block and distinct fail haptic. |
| 27 | MANUAL | runner lock sequence | Runner sends power and waits 30 seconds only with `--pixel7-field-lock`. | Actual locked state duration is operator-observed. |
| 28 | PARTIAL | runner lock sequence + `hardware/field-return.yaml` | Runner sends Volume+ while locked and preserves evidence of the return attempt. | Locked capture/haptic is not PASS without physical evidence. |
| 29 | PARTIAL | `hardware/field-return.yaml`, `29-field-return.png` | After normal operator unlock, CAT UI and Field state become reachable. | GPS warm/non-zero state needs physical evidence. |
| 30 | PIXEL AUTO | `hardware/field-off-ownership.yaml`, `30-field-off.png` | Field OFF with no user request leaves GPS Locker off. | Requires real opt-in Pixel 7 evidence. |
| 31 | PIXEL AUTO | `hardware/field-off-ownership.yaml`, `31-user-gps-survives.png` | User-owned Locker survives Field OFF, then the test restores it to off. | Requires real opt-in Pixel 7 evidence. |
| 32 | PIXEL AUTO | `hardware/field-notification-start.yaml` + `field-notification.yaml`, `32-stop-all.png` | Notification `Остановить всё` leaves Field and Locker off. | Requires accessible notification UI on real Pixel 7. |
| 33 | PARTIAL | `hardware/field-notification-start.yaml` + `field-notification.yaml`, `33-field-notification.png` | Notification has no false recovery text. | Truthfulness of camera/GPS runtime state still needs hardware evidence. |
| 34 | PARTIAL | `06-field-mode.yaml`, `34-haptics-settings.png` | Haptic presets and both test buttons are visible. | Perceived strength is manual. |
| 35 | PARTIAL | `03-settings.yaml` | OEM Night capability gate is visible. | Actual capability/unavailable semantics are device-specific. |
| 36 | PARTIAL | `07-burst.yaml`, `36-burst-stability.png` | Runner checks CAT sequence increase by at least three after rapid shutter actions. | Camera stability still needs device evidence. |

The runner writes the final status, action, expected/actual, evidence and failure/blocker reason for **every** ID to `cat-ui-results.json` and `cat-ui-results.md`. A non-run flow becomes `BLOCKED`, not `PASS`; a visual/system/hardware boundary stays `PARTIAL` or `MANUAL`.
