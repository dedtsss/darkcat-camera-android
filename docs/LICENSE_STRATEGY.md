# License strategy

DarkCat Camera is an intentional GPL fork. It is not an MIT application and must not be presented as one.

- Base repository: `https://github.com/UrbanVue/linked_camera`
- Imported upstream: tag `v1.4`, commit `7a504c0329aef71adb4d191e3f28910e762b74ea`
- Upstream license: GNU GPLv3; the upstream `LICENSE` and GPL text are retained.
- Open Camera origin: Open Camera by Mark Harman, as acknowledged by Linked Camera and its source notices.
- Linked Camera attribution: retained in the imported README, source tree and license materials.
- DarkCat-specific additions: `ru.darkcat.camera.*` vault, metadata, capture context, queue/provider adapter, editor integration and crosshair overlay.
- PR #1 reuse: the AES-GCM/Keystore design, protected metadata concepts, recovery-safe intent and WorkManager queue concepts were reviewed and reimplemented as Java layers on the Linked Camera base. The CameraX camera layer from PR #1 was not carried over.
- PhotoEditor is an independent MIT-licensed dependency and is listed in `THIRD_PARTY_NOTICES.md`.

DarkCat CRM is a separate application. No CRM source code is included here. The future CRM integration is an Intent contract only.

Distribution of this derived application must remain GPLv3-compatible and must provide corresponding source and notices.
