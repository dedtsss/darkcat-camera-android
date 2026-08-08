# MVP status

This PR is the Linked Camera integration vertical slice, not a release sign-off.

Implemented:

- Linked Camera v1.4 camera base and DarkCat applicationId/branding.
- AES-256-GCM streaming vault, Keystore key, encrypted thumbnail, random UUID names and recovery-pending lifecycle.
- FAST and working EDIT flow. Editor uses MIT PhotoEditor 3.1.0 for pinch/zoom, text, freehand/shapes, undo/redo; DarkCat adds crop and rotate controls. Stickers and a richer marker palette remain follow-up work.
- Crosshair OFF/PREVIEW/STAMP, default OFF, with configurable color/size/thickness.
- SQLite structured media state and WorkManager retry queue.
- Nextcloud Public Share, Generic WebDAV, Local/Fake and DarkCat API stub providers. Remote verification is HEAD/content-length best effort.
- Photo metadata includes capture time, best-effort last-known GPS fields, tags and CaptureContext. Linked Camera EXIF/GPS handling remains intact.
- Protected gallery/viewer, open/edit/delete/retry actions and secure-window flags.

Not yet hardware-validated: camera/photo/video behavior on a real device, MediaStore edge cases, camera-quality parity on individual devices and remote WebDAV server variations.
