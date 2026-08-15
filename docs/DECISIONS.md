# DarkCat Camera — Decision Register

Этот файл — краткий реестр уже принятых решений. Он не заменяет подробные документы (`ARCHITECTURE.md`, `SECURITY_MODEL.md`, `FIELD_MODE.md` и т. п.), а помогает быстро восстановить контекст перед крупной работой и не принимать заново уже решённые вопросы.

Если решение меняется, старую запись не удалять: отметить `superseded`, добавить новое решение и причину изменения.

---

## D-001 — Основной camera engine: Linked Camera/Open Camera + Camera2

Status: `accepted`

DarkCat 0.5 сохраняет Linked Camera/Open Camera как основу видимой камеры. Camera2 используется как продуктовый default при поддержке устройства; Camera1 остаётся advanced fallback.

CameraX не является основной камерой текущего vertical slice. Миграция возможна только при конкретной измеримой выгоде, а не ради унификации стека.

Подробности: `ARCHITECTURE.md`, `UPSTREAM_LINKED_CAMERA.md`.

## D-002 — Camera core и DarkCat-логика разделены

Status: `accepted`

Upstream camera engine отвечает за preview, Camera1/Camera2, 3A, tap focus, physical cameras, flash, фото/видео и device compatibility.

DarkCat-слои отдельно отвечают за GPS, Field Mode, capture policy, crypto, Vault, metadata, editor, upload и diagnostics.

Причина: не переписывать зрелый camera engine и не смешивать compatibility-код с продуктовой логикой DarkCat.

## D-003 — Capture success не зависит от post-processing

Status: `accepted`

Пользовательский смысл «кадр сделан» наступает после разрешённого GPS gate и подтверждённого camera callback.

Stamp, editor, encryption, thumbnail, SQLite и upload выполняются после этого и не должны задерживать shutter-success UX.

Post-capture ошибка является storage/recovery problem, а не ложным «камера не сняла».

Подробности: `ARCHITECTURE.md`.

## D-004 — Immutable capture-time metadata

Status: `accepted`

Sequence и capture ticket фиксируются после успешного camera callback. LocationFix относится к моменту shutter/capture и не должен подменяться более поздним GPS update.

Processing-time `lastKnownLocation` не используется как замена отсутствующей capture-time точки.

## D-005 — Две явные destination-модели: Vault и MediaStore Gallery

Status: `accepted`

Vault и Gallery — разные назначения хранения.

Vault: private recovery -> encryption -> private encrypted media.

Gallery: scoped MediaStore (`Pictures/DarkCat`, `IS_PENDING` на API 29+) + локальный индекс metadata.

Gallery не является неявной копией Vault, а Vault не должен автоматически публиковаться наружу.

Подробности: `ARCHITECTURE.md`, `GALLERY_STORAGE.md`.

## D-006 — Vault: durable recovery + AES-256-GCM + Android Keystore

Status: `accepted`

Основной защищённый путь строится через app-private durable recovery, затем потоковое AES-256-GCM шифрование, Android Keystore, random UUID `.dcv`, encrypted thumbnails и SQLite state.

Recovery переживает restart и не имеет TTL. Plaintext удаляется только после подтверждённого durable encrypted commit.

Подробности: `SECURITY_MODEL.md`, `ARCHITECTURE.md`.

## D-007 — Editor независим от camera preview

Status: `accepted`

EDIT — object-based image-space editor. Crosshair/stamp/watermark и другие элементы, если должны попасть в итоговый файл, формируются в post-capture pipeline, а не существуют только как preview overlay.

Выход без Save не должен уничтожать исходный pending material.

## D-008 — Upload/sync вынесены из capture pipeline

Status: `accepted`

Upload выполняется независимо через WorkManager и provider adapters.

Новые назначения (WebDAV/Nextcloud/другие backend) должны подключаться как providers/adapters, а не через изменения camera core.

Capture не должен ждать сеть.

## D-009 — Share использует отдельный временный plaintext cache

Status: `accepted`

Для Viewer/Vault share используется выделенный outgoing plaintext cache. Файл не удаляется немедленно после chooser; просроченные остатки очищаются безопасно позже.

Camera/Vault не должны зависеть от конкретного Telegram-клиента или другого consumer app.

## D-010 — GPS Locker отделён от camera engine

Status: `accepted`

GPS Locker использует системный `LocationManager.GPS_PROVIDER`, не требует Google Play Services и имеет собственную lifecycle/ownership-модель.

Field Mode и постоянный GPS Locker имеют раздельное владение запросами.

Подробности: `GPS_LOCKER.md`, `FIELD_MODE.md`.

## D-011 — Field Mode не обходит ограничения Android

Status: `accepted`

Camera foreground service запускается только из допустимого foreground state после явного действия пользователя. Не пытаться обходить Android 14/15 while-in-use/FGS restrictions, автоматически стартовать camera FGS после boot или рисовать поверх lockscreen.

## D-012 — Перед собственным workaround сначала изучать prior art

Status: `accepted`

При нетривиальной проблеме сначала проверять upstream/похожие проекты: Issues, PR, Discussions, commit history, changelog, docs, forks и известные workarounds. Затем принимать собственное решение с учётом актуальности Android/API/OEM, безопасности, лицензии и архитектурных различий.

Цель — использовать накопленный инженерный опыт и не повторять уже пройденные ошибки.

---

## Как добавлять новые решения

Для каждого существенного решения фиксировать:

- ID;
- статус;
- что принято;
- почему;
- что отвергнуто/какая альтернатива рассматривалась, если это важно;
- ссылки на профильный документ, Issue/PR/commit.

`IDEAS.md` хранит возможные идеи. Этот файл хранит только то, что уже принято как направление проекта.
