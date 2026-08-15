# DarkCat Camera 0.5: storage and Gallery

DarkCat owns two explicit photo destinations. The quick shield chip and `Хранилище` settings change only the destination of subsequent simple JPEG captures; they do not move or rewrite existing originals.

| Mode | Destination | Durability and privacy |
| --- | --- | --- |
| `Vault` | app-private encrypted Vault | JPEG first gets a durable recovery handoff, then AES-256-GCM, encrypted thumbnail and Vault queue record. |
| `MediaStore Gallery` | `Pictures/DarkCat` | JPEG is written through scoped MediaStore with `IS_PENDING` where Android supports it, then indexed locally for DarkCat Gallery. |

The normal Gallery combines those two DarkCat-owned indexes only. It does not silently import unrelated phone media. Its cards open a viewer with swipe navigation, explicit Share/Delete/Info and an explicit Editor action; long press enables multi-select Share/Delete. Editing creates a new output and never auto-opens after capture or destroys the source original.

`FLAG_SECURE` is intentionally absent from DarkCat activities in 0.5. Vault encryption and the lockscreen-safe foreground-service model remain unchanged; the user may now use normal Android screenshots and system sharing deliberately.

Field Mode remains service-owned. Its Camera2 owner fsyncs a JPEG to `files/darkcat-field-capture` before destination routing. The temporary file is removed only after Vault recovery or MediaStore publication succeeds; a process-death retry keeps the file for recovery.

The cloud queue currently uploads encrypted Vault records. A MediaStore Gallery photo is deliberately not silently copied into an encrypted upload queue; this keeps the selected storage mode honest and avoids a surprise second copy.
