# Статус DarkCat Camera 0.5 Field

PR #2 — архитектурно-рефакторинговый vertical slice на базе Linked Camera/Open Camera. Это hardware-ready сборка для проверки, а не production sign-off. PR должен оставаться OPEN/DRAFT и не должен быть merged до аппаратного теста.

Primary targets: Google Pixel 7 / актуальная пользовательская GrapheneOS и Xiaomi 12 Lite / Android 14 HyperOS 1. Аппаратный тест: **NO**.

## Реализовано в коде

- `applicationId` `ru.darkcat.camera`, версия `0.5.0-field`, Camera2-first product defaults с Camera1 compatibility fallback в Advanced.
- 0.5 добавляет явные Vault/MediaStore Gallery destinations, quick shield toggle, unified Gallery/Viewer/explicit Editor, live GPS repository, configurable haptics, 4:3 capability default, physical-lens labels/zoom presets и Night capability study. Field Mode остаётся service-owned.
- Существующий Linked/Open Camera Camera2 engine, logical/physical camera support и capability-based fallback сохранены.
- Continuous-picture AF и latency-oriented shutter path без обязательного многосекундного autofocus на каждом кадре; tap focus остаётся upstream-функцией.
- Runtime tracking Camera2 AF/AE/AWB state без блокирующего ожидания.
- Max Speed и Sharp Priority decision core; гироскопический motion sampler, hard maximum дополнительной задержки 200 ms.
- Best Frame core и advisory runtime monitor: уменьшенный TextureView preview, variance-of-Laplacian, motion/3A/time score и bounded metadata ring.
- Camera2 ZSL request/fallback и diagnostics reprocessing capability report.
- GPS Locker на Android `LocationManager.GPS_PROVIDER`, location FGS, live accuracy, GREEN/YELLOW/RED, strict 7 m default и monotonic stale policy.
- Field Mode P0: user-started camera FGS, private notification/actions, wake lock, strong service endpoint, service-owned Camera2 session, durable JPEG handoff, MediaSession/VolumeProvider and physical Volume+ diagnostics.
- Два разных haptic: короткий success после фактического JPEG callback и заметный двухимпульсный fail; post-capture error не даёт fail haptic.
- Sequence reservation после успешного camera callback, отдельная video sequence, FIFO photo capture tickets.
- Русская product camera chrome скрывает наложенные upstream controls; обычные настройки сгруппированы по съёмке, геолокации, меткам, хранилищу, синхронизации, полевому режиму, видео и Advanced.
- Persisted tags/active tags UI; выбранные tags добавляются к durable CaptureContext и metadata.
- Чёрный технический stamp block справа снизу для coordinates/accuracy/sequence/tags/custom text; final flatten использует JPEG quality 100.
- Crosshair OFF/PREVIEW/STAMP с настройкой цвета/размера/толщины; STAMP re-encode использует JPEG quality 100.
- WYSIWYG geometry: center-crop mapping, preview/final crosshair alignment, technical stamp preview and PNG/WebP watermark with single/tiled layout.
- Camera-style top/bottom UI with system-bar/display-cutout insets; screenshots и recents намеренно разрешены на всех DarkCat экранах, а Vault encryption и lockscreen-safe FGS остаются защитой данных.
- Shooting point domain model: spatial+temporal clustering, DRAFT/REVIEWED/UPLOADING/PUBLISHED/LOCKED lifecycle, merge/split guards, point batch metadata and point gallery.
- AES-256-GCM vault, random provider-generated IV, Android Keystore, UUID filenames, encrypted thumbnails и credential-store IV crash fix.
- Прямой atomic recovery handoff стандартного secure JPEG из camera callback (`tmp`/`fsync`/rename), app-private journal без TTL, restart resume и serial asynchronous post-capture executor.
- Durable external-reference journal для завершённого secure-видео: большой private copy возобновляется после process death по стабильному ID, а upstream fallback сохраняет материал при невозможности записать journal.
- Идемпотентный Vault commit по recovery identity, ciphertext `fsync`, sampled 512 px thumbnails и lifecycle cleanup decrypted viewer cache.
- Storage preflight и реальный private emergency reserve 64 МиБ: при неожиданном disk-full резерв освобождается для одной повторной atomic recovery-write; persistent storage-blocked снимается только после capacity check и `fsync` probe.
- Валидируемая upload state machine, WorkManager queue, provider Off/Nextcloud Public Share/Generic WebDAV/DarkCat API stub и KEEP LOCAL default.
- WebDAV verification hardening: HEAD без точного Content-Length остаётся UPLOADED, а не VERIFIED.
- Vault/Gallery Viewer с deliberate screenshots, camera capability diagnostics JSON без media/coordinates и Night capability section.
- Object-based русский editor: interactive crop, freehand, line/rectangle/oval/arrow/text, reselect, move, pinch scale/rotate, color/stroke, delete и bounded undo/redo; recovery-safe JPEG 100 Save.
- Unit tests для crypto, corruption, GPS, sequence/tickets, tags/formatting, upload transitions/verification/retention, recovery и capture scoring/decision logic.

## Реализовано частично

| Область | Фактическая граница |
| --- | --- |
| Field Mode | Service-owned Camera2 session и strong endpoint bridge реализованы; physical lockscreen/BT routing требуют hardware test, а recovery JPEG без GPS metadata после process death требует последующей ручной обработки. |
| Screen off / real lock | Архитектура не обходит lockscreen и сохраняет visible FGS. Фактическое удержание camera session политикой Pixel/GrapheneOS не проверено. |
| Bluetooth Volume+ | Visible Activity key path и Field MediaSession/remote VolumeProvider adapter существуют. Обычный HID Volume+ не гарантирован как media button; locked routing требует Pixel test. |
| Sharp Priority | Очень короткое motion-based окно реализуется без AF wait. Результат по резкости требует A/B на устройстве. |
| Best Frame | Scoring/ring/capability/fallback есть, но выбранный Camera2 JPEG ещё не заменяется кадром из YUV/PRIVATE reprocessing ring. |
| ZSL | Engine выставляет `CONTROL_ENABLE_ZSL` в совместимом обычном Camera2 still path. Реальное HAL/ZSL поведение не подтверждено. |
| Wide/ultrawide | Используется upstream physical-ID architecture. Понятное соответствие 0.5×/1× и доступность Pixel 7 должны быть подтверждены diagnostics/hardware test. |
| Technical stamp | Чёрный block и выбранные строки подключены; shutter-time non-stale LocationFix проходит через photo ticket и durable sidecar. Без такого fix координаты остаются пустыми, а более поздняя точка не подставляется. Геометрию/читаемость нужно проверить на Pixel. |
| Product UI | Русская оболочка и task-oriented settings существуют. Lens button пока делегирует upstream physical-camera chooser и не гарантирует понятные `0.5×/1×` labels до Pixel capability mapping. |
| EDIT | Object operations и интерактивный crop реализованы в first-party Canvas layer. Gesture ergonomics, rotation/state restoration и full-resolution memory/performance на Pixel не проверены; bitmap decode остаётся memory-sensitive. |
| Sync UX | Русский экран counters/records, время последней принятой отправки, «Отправить сейчас», повтор ошибок и подтверждение metered override реализованы; весь flow требует server/network проверки. |
| Shooting points | Pure clustering/model/gallery and batch metadata are implemented. Upload API integration, server-side pointShareUrl issuance and publish lock need backend/real-server verification. |
| Advanced secure/video recovery | Основной standard JPEG пишет recovery напрямую; завершённое видео получает durable external-reference до async copy/restart resume. Скрытые multi-frame/RAW combinations и video source deletion на разных OEM MediaStore требуют fault-injection/hardware теста; production-гарантия каждой комбинации не заявляется. |

## Не заявляется готовым

- Pixel 7/GrapheneOS hardware behavior;
- Bluetooth Volume+ capture при настоящей блокировке;
- camera session survival после screen off/lock/process pressure;
- автоматический выбор итогового JPEG из Best Frame window;
- PRIVATE/YUV reprocessing на Pixel;
- object-editor gesture/memory behavior на реальном Pixel;
- remote Nextcloud/WebDAV interoperability на реальном сервере;
- production security audit;
- release signing или публикация.

## Проверки перед handoff

Обязательные команды:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
./gradlew compileDebugAndroidTestJavaWithJavac
```

Upstream lint backlog может оставаться неблокирующим, но новые DarkCat-файлы не должны добавлять необъяснённых ошибок. Точный результат команд, commit, APK path/SHA-256 и CI status фиксируются в итоговом отчёте, а не предполагаются этим документом.

Следующий gate — пройти [PIXEL7_TEST.md](PIXEL7_TEST.md) для обоих целевых устройств, приложить diagnostics JSON и только затем уточнить claims Field Mode, physical ultrawide, ZSL и Bluetooth remote.
