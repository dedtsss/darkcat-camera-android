# CAT UI Test v0.2 — local smoke and Maestro Cloud mapping

IDs `01`–`36` are stable in flows, reports and evidence names. `CLOUD_AUTO` means the stated Cloud observable can pass only after its exact flow succeeds. `CLOUD_PARTIAL` preserves the automated subset but never upgrades an unobserved system, OEM or physical property. `PHYSICAL_REQUIRED` needs a real device/human observation and never inherits a Cloud PASS.

| ID | Cloud class | Flow / evidence | Automated observable | Boundary preserved in result |
|---:|:---:|---|---|---|
| 01 | CLOUD_AUTO only with fresh Cloud install; otherwise CLOUD_PARTIAL | `01-launch-main.yaml`, `01-launch-main.png` | CAT UI appears and old startup text is absent. | Default local `install -r` is not a clean-install test. |
| 02 | CLOUD_AUTO | `01-launch-main.yaml`, `02-main.png` | Top bar, thumbnail, shutter and lens controls are reachable. | AI visual artifact is required before declaring no overlap. |
| 03 | PHYSICAL_REQUIRED | `01-launch-main.yaml`, `03-maestro-main.png` | Maestro navigation evidence exists. | Android/system screenshot behavior is not proven by `takeScreenshot`. |
| 04 | CLOUD_PARTIAL | `02-storage-gps.yaml`, `04-storage-toggle.png` | Storage control is reachable. | Color/persistence remains device evidence. |
| 05 | CLOUD_PARTIAL | `02-storage-gps.yaml`, `05-gps-panel.png` | GPS owner, accuracy and state labels are visible. | Real location quality/service behavior needs a location-enabled device. |
| 06 | CLOUD_PARTIAL | `02-storage-gps.yaml` | Persistent Locker action is exposed. | Start without Settings and changing accuracy need device evidence. |
| 07 | PHYSICAL_REQUIRED | — | — | Observe live GNSS progression near a window/outside. |
| 08 | CLOUD_PARTIAL | `02-storage-gps.yaml` | GPS panel returns to CAT UI. | Ordinary-camera ownership remains physical. |
| 09 | CLOUD_PARTIAL | `03-settings.yaml`, `09-settings-capture.png` | Capture controls are reachable. | No approved static Cloud baseline yet. |
| 10 | CLOUD_PARTIAL | `03-settings.yaml` | Resolution control is reachable. | Dimensions/persistence depend on a real camera. |
| 11 | CLOUD_PARTIAL | `cloud/11-13-14-storage-system-ui.yaml`, `cloud-11-system-gallery.png` | Gallery capture plus system-Photos probe. | Absent Photos is `CLOUD_LIMITATION`; public MediaStore remains physical confirmation. |
| 12 | CLOUD_PARTIAL | `04-gallery-mode.yaml`, `12-gallery-viewer.png` | Gallery-mode viewer exposes Edit/Share/Delete. | Swipe/Delete are intentionally not automated. |
| 13 | CLOUD_PARTIAL | `cloud/11-13-14-storage-system-ui.yaml`, `cloud-13-darkcat-vault-gallery.png` | Vault capture appears in internal DarkCat Gallery. | System Gallery cannot prove absence without a deterministic Cloud media query. |
| 14 | CLOUD_PARTIAL | `cloud/11-13-14-storage-system-ui.yaml`, `cloud-14-share-chooser.png` | Android share chooser is AI/text checked. | URI receiver delivery is only PASS when Cloud exposes a deterministic receiver. |
| 15 | PHYSICAL_REQUIRED | settings/gallery/viewer Maestro evidence | Relevant screens are reached. | Android/system screenshots are not proven by Maestro screenshots. |
| 16 | CLOUD_PARTIAL | `05-lens-rotate-stamp.yaml`, `16-lenses.png` | Lens chooser opens without raw Camera ID labels. | Human review records meaningful real lens labels. |
| 17 | PHYSICAL_REQUIRED | `05-lens-rotate-stamp.yaml` | Capability lens UI is reachable. | Sequential physical-lens switching needs a real camera. |
| 18 | CLOUD_PARTIAL | `05-lens-rotate-stamp.yaml` | Zoom UI is reachable. | Useful values need device review. |
| 19 | PHYSICAL_REQUIRED | — | — | Verify a real wider field-of-view for any sub-1x choice. |
| 20 | CLOUD_AUTO | `cloud/20-21-orientation-stamp.yaml`, `cloud-20-*` | Portrait → landscape → portrait plus hard AI clipping/overlap/black-preview check. | A Cloud preview is not physical camera validation. |
| 21 | CLOUD_AUTO for displayed mock-location stamp; PHYSICAL_REQUIRED for GNSS accuracy | `cloud/20-21-orientation-stamp.yaml`, `cloud-21-technical-stamp.png` | Fixed coordinates, accuracy presentation, sequence and in-image unclipped stamp are AI/extract-text checked. | Mock location does not establish real GNSS accuracy. |
| 22 | CLOUD_AUTO | `cloud/22-23-25-field-ownership-volume.yaml`, `cloud-22-23-*` | Field becomes active under fixed location. | Not a Pixel 7/GrapheneOS PASS. |
| 23 | CLOUD_AUTO | `cloud/22-23-25-field-ownership-volume.yaml`, `cloud-22-23-*` | Field GPS Locker ownership text is visible. | No real GNSS accuracy claim. |
| 24 | PHYSICAL_REQUIRED | — | — | Wait for real accuracy better than 7 m. |
| 25 | CLOUD_AUTO for sequence when the Cloud Camera2 frame is usable; CLOUD_PARTIAL for its proved virtual-camera error; PHYSICAL_REQUIRED for haptic | `cloud/22-23-25-field-ownership-volume.yaml`, `cloud-25-volume-up.png` or `cloud-25-virtual-camera-limitation.png` | `pressKey: volume up` changes CAT sequence unless Cloud reports `Failed to take picture`, which is retained as virtual-Camera2 evidence. | Perceived haptic and physical Camera2 capture remain manual. |
| 26 | PHYSICAL_REQUIRED | — | — | Real poor-GNSS strict block and distinct fail haptic. |
| 27 | CLOUD_PARTIAL + PHYSICAL_REQUIRED | `cloud/27-28-29-cloud-lock-return.yaml` | `pressKey: lock` is issued. | Cloud has no fixed 30-second physical lock proof. |
| 28 | CLOUD_PARTIAL + PHYSICAL_REQUIRED | `cloud/27-28-29-cloud-lock-return.yaml` | Cloud sends Volume+ while lock simulation is active. | No secure-lockscreen/haptic PASS. |
| 29 | CLOUD_AUTO for foreground UI; PHYSICAL_REQUIRED for warm GPS | `cloud/27-28-29-cloud-lock-return.yaml`, `cloud-29-*` | `power` and `launchApp stopApp:false` return to Field/GPS UI. | Cloud state is not physical unlock/GNSS proof. |
| 30 | CLOUD_AUTO | `cloud/30-31-field-off-ownership.yaml`, `cloud-30-*` | Field OFF leaves Locker off when no user request exists. | Not Pixel/GrapheneOS acceptance. |
| 31 | CLOUD_AUTO | `cloud/30-31-field-off-ownership.yaml`, `cloud-31-*` | User-owned persistent GPS survives Field OFF, then flow removes it. | Not Pixel/GrapheneOS acceptance. |
| 32 | CLOUD_AUTO when system shade is exposed | `cloud/32-33-notification-stop-all.yaml`, `cloud-32-*` | System swipe opens notification and `Остановить всё` leaves Field/Locker off. | Missing shade is `CLOUD_LIMITATION`, never an app bug. |
| 33 | CLOUD_AUTO for text; PHYSICAL_REQUIRED for runtime truth | `cloud/32-33-notification-stop-all.yaml`, `cloud-33-*` | Notification has no false recovery text. | Camera/GPS runtime truth remains physical. |
| 34 | CLOUD_AUTO for controls; PHYSICAL_REQUIRED for perception | `cloud/34-35-static-field-night.yaml`, `cloud-34-*` | Haptic presets and test buttons are visible. | Perceived vibration strength remains manual. |
| 35 | CLOUD_AUTO capability gate; PHYSICAL_REQUIRED for OEM Night | `cloud/34-35-static-field-night.yaml`, `cloud-35-*` | Cloud either exposes enabled OEM capability or an honest disabled/unavailable Night state. | No Xiaomi/OEM Night PASS. |
| 36 | CLOUD_PARTIAL | `07-burst.yaml`, `36-burst-stability.png` | Runner checks CAT sequence increase by at least three after rapid shutter actions. | Camera stability still needs device evidence. |

The local runner writes status, action, expected/actual, evidence and failure/blocker reason for every ID to `cat-ui-results.json` and `cat-ui-results.md`. A non-run flow is `BLOCKED`, not `PASS`; use the Cloud class above when handing off Cloud artifacts.
