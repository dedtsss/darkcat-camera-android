# Linked Camera upstream record

## Exact source

- Repository: `https://github.com/UrbanVue/linked_camera`
- Branch/tag: `main` / `v1.4`
- Commit: `7a504c0329aef71adb4d191e3f28910e762b74ea`
- License: GPL-3.0
- Imported into this repository with merge commit `268dab4` (the upstream history remains reachable).

## What Linked Camera adds over Open Camera

Linked Camera is already an Open Camera-derived application. Its documented product changes include Nextcloud public-share upload through WebDAV, offline/Wi-Fi queue behavior, upload notifications and field-collection branding/workflow. The fork also contains the current Open Camera camera capability handling and compatibility code.

## Preserved in DarkCat Camera

The imported camera core remains responsible for Camera2/legacy camera handling, autofocus and continuous focus, tap focus, exposure/ISO/shutter/manual focus, white balance, HDR/noise reduction/burst/RAW, zoom, flash/torch, front/rear cameras, resolutions, video, stabilization, GPS/geotagging, orientation, grids/guides, stamps and device compatibility.

## DarkCat changes

DarkCat adds an adapter at the completed-media boundary rather than replacing the camera engine:

1. Secure Mode intercepts completed photo files and MediaStore items before gallery publication, copies them to a recovery-pending app-private file, streams AES-256-GCM encryption into a UUID vault file, commits SQLite metadata and only then removes the source.
2. Secure video capture keeps MediaStore output pending or skips the public file scan while a background stream copy/encryption runs. Video is not passed through the photo editor.
3. FAST and EDIT workflows, a protected gallery/viewer, the crosshair overlay/stamp, CRM CaptureContext and persistent upload state are added.
4. The existing upstream Nextcloud path remains available in the camera core; the new provider abstraction handles DarkCat encrypted media through Nextcloud Public Share, Generic WebDAV, a local fake provider and a documented DarkCat API stub.

No attempt is made to rename or relicense the GPL camera source as MIT.
