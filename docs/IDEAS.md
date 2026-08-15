# DarkCat Camera — Ideas & Engineering Findings

Назначение этого файла — хранить найденные идеи, инженерные приёмы, наблюдения из чужих проектов и возможные улучшения, которые **ещё не являются обязательными требованиями**.

Перед крупным этапом, архитектурным изменением или реализацией нетривиальной функции сначала просмотреть:

1. `docs/DECISIONS.md` — что уже принято и почему;
2. этот файл — что уже исследовали и какие варианты отложены;
3. профильный документ (`ARCHITECTURE.md`, `SECURITY_MODEL.md`, `FIELD_MODE.md` и т. п.) — детали текущей реализации.

## Статусы

- `candidate` — стоит рассмотреть;
- `research` — требует проверки/прототипа;
- `accepted` — решение принято; перенести итог в `DECISIONS.md`/профильный документ/Issue;
- `rejected` — сознательно не используем; причина обязательна;
- `implemented` — уже реализовано; это не backlog, а подтверждение найденного паттерна.

Наличие записи здесь не означает, что её надо реализовывать.

---

## 2026-08-16 — аудит GeoCam 2.0 (`LoveAPK.apk`)

Источник: статический reverse engineering APK. Внутри APK идентифицировано приложение GeoCam 2.0 (`geocam.mobile.android`).

### 1. CameraX как основной camera stack

Status: `rejected` для текущей архитектуры / полезно как внешний reference.

GeoCam использует CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-video`).

DarkCat 0.5 сознательно сохраняет Linked Camera/Open Camera + Camera2 как основной camera engine. Миграция на CameraX ради самого CameraX сейчас не даёт достаточной выгоды и создаёт риск потерять уже работающие Camera2/physical-camera/compatibility возможности.

Пересматривать только при конкретной измеримой проблеме текущего engine, которую CameraX решает лучше.

### 2. Post-capture editor как отдельный слой

Status: `implemented` / подтверждено внешним примером.

GeoCam разделяет capture и последующую композицию изображения: crosshair, coordinates, watermark, text, arrows/drawing и другие overlays формируются после capture.

DarkCat уже использует отдельный object-based EDIT и WYSIWYG stamp/crosshair. Полезный вывод: не переносить editor-логику внутрь camera engine; сохранять независимость capture и editing pipeline.

### 3. Private-first storage

Status: `implemented`, в DarkCat сильнее.

GeoCam сначала хранит снимок в app-private storage, а системную Gallery рассматривает как отдельный экспорт.

DarkCat уже реализует более строгий вариант: durable private recovery + AES-256-GCM Vault, а MediaStore Gallery является отдельной destination-моделью.

### 4. MediaStore export с `IS_PENDING`

Status: `implemented` / подтверждено внешним примером.

GeoCam использует scoped-storage MediaStore flow. DarkCat уже публикует Gallery через MediaStore с `IS_PENDING` на API 29+.

### 5. Share через временный FileProvider/plaintext cache

Status: `implemented`, DarkCat сильнее.

GeoCam использует Android Share/FileProvider вместо жёсткой интеграции с одним мессенджером.

DarkCat уже использует отдельный outgoing plaintext cache и не удаляет share-файл немедленно после chooser. Сохранять эту модель: camera core не должен зависеть от Telegram/Nekogram/другого клиента.

### 6. Storage/upload adapters отдельно от камеры

Status: `implemented` / подтверждено архитектурно.

GeoCam не привязывает camera core к собственному upload backend. Для DarkCat это уже развито дальше: `ru.darkcat.camera.upload`, WorkManager и provider adapters.

Продолжать добавлять WebDAV/Nextcloud/другие назначения как providers, а не внутрь capture pipeline.

### 7. Encrypted settings storage через Keystore + AEAD/Tink

Status: `candidate`.

В GeoCam присутствуют Android Keystore, AndroidX Security Crypto/Tink и encrypted DataStore-подход для чувствительного состояния.

Перед изменениями проверить, какие DarkCat-настройки реально являются секретами и как сейчас устроен credential envelope. Не мигрировать обычные preferences без необходимости. Рассмотреть encrypted DataStore только если он уменьшит собственный crypto/settings-код без ухудшения совместимости и recovery.

### 8. EXIF после финальной композиции

Status: `research`.

GeoCam явно обновляет EXIF после обработки изображения. Для DarkCat стоит проверить отдельным тестом контракт:

`capture metadata -> EDIT/stamp/flatten -> final JPEG -> EXIF preservation/write`.

Цель — убедиться, что editor/повторный JPEG encode не теряет нужные EXIF-поля и что EXIF не конфликтует с собственным immutable CaptureContext/SQLite metadata.

Не считать EXIF единственным источником истины для доказательных metadata.

### 9. Ограничивать FileProvider минимальной директорией

Status: `candidate`.

Проверять, что share provider выдаёт URI только из выделенного outgoing/share cache, а не из широкого `filesDir`. Это уменьшает blast radius при ошибке path configuration.

### 10. Что из GeoCam не переносить

Status: `rejected`.

- CameraX migration без конкретной причины;
- обычные JPEG как основной private vault format;
- пароль по умолчанию вроде `1234`;
- жёсткую зависимость от конкретного мессенджера;
- хранение важных capture metadata только в EXIF;
- упрощение существующего durable recovery ради более простой схемы GeoCam.

## Итог аудита GeoCam

GeoCam не предлагает DarkCat новый основной стек. Основная ценность аудита — независимое подтверждение уже выбранных принципов:

`capture -> independent editor -> private-first storage -> explicit Gallery/Share -> independent transport`.

DarkCat уже расширяет эту схему за счёт durable recovery, encrypted Vault, immutable capture metadata, WorkManager/provider adapters и Field Mode. Реально новые пункты для проверки: encrypted settings storage, EXIF preservation contract и минимальный FileProvider scope.
