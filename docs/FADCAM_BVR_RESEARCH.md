# FadCam / BVR: исследование фоновой камеры

Дата исследования: 2026-08-10.

Цель этого документа — зафиксировать только наблюдаемую поведенческую и Android API-архитектуру reference APK. Закрытый код BVR не копировался и не переносился. Исследование статическое: APK/manifest/resources/disassembly, а для FadCam дополнительно публичный исходный код соответствующего release tag. Запуск на Pixel 7 / GrapheneOS, Bluetooth-пульт и поведение реального lockscreen здесь не проверялись.

## Исследованные файлы

| Reference | Идентификация | SHA-256 | Android metadata |
|---|---|---|---|
| `FadCam_v4.0.0-beta10.3.apk` | `com.fadcam.beta`, versionCode 39. Несмотря на имя файла, manifest сообщает `4.0.0-beta10.1` | `f78c464ce474a89f3313eb8eac5689101dce5a4e00e3c87a96c736c96d59e497` | minSdk 24, target/compile SDK 36 |
| `BVR+Pro+v19.5.97+(19597)+arm64-v8a.apk` | `com.arbelsolutions.BVRUltimate`, versionCode 19597, versionName `19.5.97` | `03a348fdd3f491d047e2cb1e632595d8b026d13badf2d4635728706e8427cfb8` | minSdk 24, target/compile SDK 37 |

Оба файла найдены в Google Drive, папка `!Codex`. BVR исследован из загруженного Drive-файла. FadCam из Drive имеет то же точное имя и размер 98,249,799 байт; для локального анализа был использован одноимённый официальный release asset. Это совпадение размера, но не криптографическое доказательство тождественности двух копий. Публичный FadCam source tag: `v4.0.0-beta10`, commit `569d2c5f0e4fbe4a2eeec8f240048fdcceac2e21`.

## Краткий результат

- Оба приложения выносят владение камерой/записью из Activity в foreground service. Это позволяет уже запущенной сессии продолжать работу после ухода UI в фон и выключения экрана.
- FadCam использует service-owned Camera2/encoder surfaces, `camera|microphone` FGS и `PARTIAL_WAKE_LOCK`. При уходе приложения в фон он освобождает только preview EGL/GL, а recording surface оставляет работающей.
- BVR содержит два service engine (`MainService` и `CameraXService`), foreground notification, `PARTIAL_WAKE_LOCK` и отдельные adapters для screen/media/volume событий.
- BVR перехватывает изменение аппаратной громкости через активный framework `MediaSession` с `setPlaybackToRemote(VolumeProvider)`. Именно remote-volume route, а не сам факт наличия MediaSession, является существенной частью механизма.
- Ни один APK не доказывает, что Bluetooth HID `Volume+` гарантированно доходит до приложения под secure lockscreen на Pixel 7 / GrapheneOS. Это отдельный обязательный hardware test.
- Оба APK содержат black-screen/privacy Activity. BVR дополнительно содержит старый wake/dismiss-keyguard path. Это не настоящая блокировка, не средство удержания камеры и не архитектура для DarkCat.
- `START_STICKY` не является восстановлением camera session: исследованные null-intent paths не реконструируют полноценную сессию после смерти процесса.

## Foreground Service types

### FadCam

Manifest:

- `RecordingService`: `camera|microphone`, `exported=false`;
- `DualCameraRecordingService`: `camera|microphone`, `exported=false`;
- `RecordingTileService`: Quick Settings tile с `BIND_QUICK_SETTINGS_TILE`;
- отдельные screen recording и playback services имеют соответственно `mediaProjection|microphone` и `mediaPlayback`.

При promotion в foreground основной recording service вычисляет runtime mask из реально выданных `CAMERA` и `RECORD_AUDIO`, затем вызывает трехаргументный `startForeground(...)`. Notification публикуется сразу, до тяжёлой подготовки полного notification content.

### BVR Pro

Manifest у `MainService` и `CameraXService` объявляет широкий набор:

`camera|connectedDevice|dataSync|location|mediaPlayback|microphone`.

Runtime не всегда передаёт весь набор. В частности, `CameraXService` начинает с `camera` (либо `camera|mediaPlayback` в отдельных состояниях) и условно добавляет `location`, `connectedDevice` и `microphone`; при появлении новой потребности повторно вызывает `startForeground` с объединённой маской. `MainService` также строит динамическую маску.

Полезен сам pattern динамического повышения типов. Широкую manifest-декларацию BVR копировать не следует: DarkCat должен объявлять и передавать только типы реально выполняемой работы. Bluetooth HID remote, с которым приложение напрямую не устанавливает соединение, сам по себе не обосновывает `connectedDevice`; активный MediaSession сам по себе не обосновывает фиктивный `mediaPlayback` FGS.

### Современный эквивалент для DarkCat

Для targetSdk 34+ Android требует manifest service type, соответствующее type-specific разрешение и runtime prerequisite. Для камеры это `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA` и выданный `CAMERA`; для location — `FOREGROUND_SERVICE_LOCATION` и fine/coarse location. Camera permission является while-in-use permission, поэтому camera FGS надо запускать, пока пользователь явно включает Полевой режим в видимой Activity. Уже поднятый FGS предназначен в том числе для продолжения camera access после ухода приложения в фон. См. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) и [Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

Предпочтительная декомпозиция DarkCat:

- `CameraFieldService`: `camera` и, только для видео со звуком, `microphone`;
- `GpsLockerService`: `location`;
- sync/post-processing: WorkManager или соответствующая ограниченная фоновая работа, а не постоянное добавление `dataSync` к camera FGS.

Раздельные camera/location services упрощают законный location-only restore и не заставляют пытаться восстановить запрещённый camera FGS после boot.

## Lifecycle: background, screen off, lock, unlock

### FadCam

Наблюдаемая схема:

1. `RecordingService` владеет `CameraDevice`, `CameraCaptureSession`, encoder и recording pipeline.
2. В GL path Camera2 session пишет в `getCameraInputSurface()` pipeline; UI preview surface не является обязательным camera output.
3. `ProcessLifecycleOwner.ON_STOP` отправляет service action `ACTION_APP_BACKGROUND`. Service освобождает только preview EGL/GL resources.
4. `ON_START` отправляет `ACTION_APP_FOREGROUND`; service восстанавливает preview-only path или ждёт новую UI surface. Запись при этом не должна зависеть от повторного создания Activity.
5. В legacy/fallback path при отсутствии UI preview существует служебный 1x1 `SurfaceTexture`/`Surface`, который может оставаться output capture session.
6. При фактическом старте записи service берёт `PARTIAL_WAKE_LOCK`; release есть в stop/error/`onDestroy` paths.

В camera source не найден отдельный `SCREEN_OFF`/`SCREEN_ON` receiver. Следовательно, screen-off resilience достигается не обработкой кнопки питания, а service-owned session, foreground status, encoder surface и CPU wake lock.

Quick Settings tile учитывает Android 14+: через `startActivityAndCollapse(PendingIntent)` запускается короткая translucent `RecordingStartActivity`, а она из пользовательского foreground context вызывает `startForegroundService`. Если устройство locked, tile использует `unlockAndRun`, то есть просит штатную разблокировку и не обходит keyguard.

Ограничение: `RecordingService` возвращает `START_STICKY`, но при `intent == null` только логирует событие и возвращает флаг. Полной реконструкции camera/encoder session в этом path нет.

### BVR Pro

Наблюдаемая схема:

- `MainService` и альтернативный `CameraXService` — `exported=false`, `stopWithTask=false`, сами управляют camera engine и foreground notification.
- Оба используют service-level `PARTIAL_WAKE_LOCK`. `CameraXService` берёт его без timeout и имеет release path. `MainService` в зависимости от режима берёт без timeout либо на 1,800,000 ms (30 минут), затем освобождает в cleanup.
- Опциональный `PowerButtonReceiver` динамически регистрируется на `SCREEN_ON`/`SCREEN_OFF`. В исследованной конфигурации screen-off переводится в action snapshot, screen-on — в start/record action. Это дополнительный trigger, а не фундамент удержания session.
- `MainService.onTaskRemoved()` только вызывает `super` и логирует. Отдельного немедленного reopen/restart path там нет.
- `MainService` обычно возвращает `START_STICKY`; опциональная настройка `pref_test_android11_foreground_fix` меняет результат на `START_REDELIVER_INTENT`. Null/empty intent не восстанавливает camera pipeline.
- `CameraXService.onStartCommand()` в конце возвращает результат базового `Service.onStartCommand()`, то есть не задаёт надёжную sticky-recovery политику.
- `RestartAlarmsReceiver` слушает boot/quickboot и планирует exact alarms. Это механизм расписания BVR, а не допустимый современный способ автоматически поднимать camera FGS после перезагрузки.

Статический анализ показывает ожидаемую устойчивость уже запущенной service-owned сессии, но не доказывает, что OEM/GrapheneOS никогда не отзовёт camera device после lock/screen-off. DarkCat должен иметь быстрый reopen path и диагностировать `CameraDevice.StateCallback`/session errors.

### Process recreation для DarkCat

Не полагаться на `START_STICKY`. Нужно разделить:

- durable user intent: «Полевой режим был включён»;
- volatile session: camera opened/configured, current physical ID, repeating request, GPS subscription;
- legal restart eligibility: foreground Activity/user action/system exemption и актуальные permissions.

После смерти процесса recovery originals и capture queue восстанавливаются всегда. Camera session переоткрывается только из допустимого context. Если она не может быть законно восстановлена под lockscreen, notification должен сообщить «камера требует открытия приложения», а аппаратный trigger должен дать FAIL haptic после фактического отсутствия camera readiness, не изображать успешный кадр.

## MediaSession, Volume+ и Bluetooth remote

### FadCam

Camera recording path не содержит `KEYCODE_VOLUME_UP`, volume-change listener или MediaSession callback, вызывающий capture/recording. Manifest `MediaButtonReceiver` относится к playback; публичный source прямо описывает его как no-op receiver, потому что media playback/notification manager обрабатывает media controls. Это не reference для Bluetooth shutter.

### BVR Pro

BVR имеет два различных пути:

1. Активный framework `MediaSession`:
   - устанавливаются flags, playback state и callback;
   - session активируется;
   - вызывается `MediaSession.setPlaybackToRemote(VolumeProvider)`;
   - `VolumeProvider.onAdjustVolume(direction)` передаёт направление в service;
   - service применяет debounce 2000 ms и настраиваемо отображает направление в start, stop, terminate, snapshot, zoom или camera flip;
   - MediaSession callback также разбирает `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREVIOUS`, `MEDIA_STOP`, `VOLUME_UP` и `VOLUME_DOWN` на `ACTION_UP`.
2. Legacy Bluetooth/media-button path:
   - при отдельных Bluetooth SCO состояниях вызывается старый `AudioManager.registerMediaButtonEventReceiver(ComponentName)`;
   - manifest `VolumeChangeReceiver` объявлен на `MEDIA_BUTTON`, но implementation проверяет скрытый `android.media.VOLUME_CHANGED_ACTION` и в исследованной версии не отправляет созданный service intent. Этот receiver выглядит инертным/legacy и непригоден как образец.

Android API reference прямо указывает, что `setPlaybackToRemote(VolumeProvider)` должен быть вызван, чтобы MediaSession получила volume commands; иначе система меняет громкость соответствующего stream. См. [MediaSession.setPlaybackToRemote](https://developer.android.com/reference/android/media/session/MediaSession#setPlaybackToRemote(android.media.VolumeProvider)).

Практический вывод для DarkCat:

- когда Activity видима, `dispatchKeyEvent`/`onKeyUp(KEYCODE_VOLUME_UP)` — основной простой adapter;
- в явно включённом Полевом режиме можно экспериментально активировать отдельный MediaSession + remote `VolumeProvider` adapter;
- adapter должен существовать только пока Field Mode активен, иметь debounce/anti-repeat и обязательно `setPlaybackToLocal`/`release` при остановке;
- вне Field Mode регулировка громкости не перехватывается;
- нельзя заявлять поддержку locked Bluetooth `Volume+`, пока она не проверена на Pixel 7 / GrapheneOS именно с целевым пультом;
- активная fake-media session может менять системную маршрутизацию громкости и конкурировать с реальным player. Это UX/policy trade-off, а не универсальный Android shutter API;
- если platform routing не отдаёт Bluetooth HID key в VolumeProvider, корректного общего permission-free API для перехвата любого Volume+ под secure lockscreen нет. Нельзя заменять это Accessibility Service или обходом lockscreen без отдельного явного product/security решения.

## Wake locks

Полезный общий pattern обоих APK — `PARTIAL_WAKE_LOCK` у service, а не screen wake lock у Activity. Он держит CPU/камера callbacks/encoder при погашенном экране, но не включает дисплей и не разблокирует устройство.

Для DarkCat:

- брать partial wake lock только на время активного Field Mode/camera session;
- release в idempotent stop, camera fatal error и `onDestroy`;
- использовать timeout как аварийную страховку или контролируемое продление, не busy loop;
- не использовать `FULL_WAKE_LOCK`, `SCREEN_*_WAKE_LOCK` или `ACQUIRE_CAUSES_WAKEUP` для фонового capture;
- wake lock не заменяет FGS и не гарантирует, что camera device не будет отозван системой.

## Foreground notifications и actions

### FadCam

- ongoing, silent, low-priority notification;
- action `Stop` во время записи;
- actions `Resume` и опционально `Stop` в paused state;
- default preset открывает приложение; «discreet» presets могут назначать пустой content intent и маскировать назначение notification;
- recording notification не задаёт явный `visibility`/`publicVersion`, поэтому полагается на platform default/user lockscreen policy.

Маскировку назначения приложения копировать нельзя: DarkCat Field Mode должен оставаться ясно видимым пользователю.

### BVR Pro

- actions start/stop recording, stop service и terminate формируются как immutable/update-current broadcast PendingIntent;
- у notification есть preference `chkHideFromLockScreen`: hide выбирает secret visibility, иначе builder получает public visibility;
- foreground channel создаётся с public lockscreen visibility;
- в отдельных режимах notification может получить bitmap/large style;
- action receivers в manifest экспортированы без видимого signature permission; это не следует повторять.

Для DarkCat notification:

- нейтральный текст `DarkCat Camera · Полевой режим`;
- только безопасные данные: readiness, округлённая accuracy и queue count; без thumbnails, адресов, tags и точных координат;
- `VISIBILITY_PRIVATE` с redacted public version либо `VISIBILITY_SECRET` для secure lockscreen. Android различает public/private/secret visibility; см. [Notification visibility](https://developer.android.com/reference/android/app/Notification#VISIBILITY_PRIVATE);
- actions через explicit immutable PendingIntent в `exported=false` receiver/service или permission-protected component;
- разумные actions: открыть, остановить режим, sync now. Capture action в notification не заменяет Volume remote и требует отдельного UX решения;
- пользователь всё равно может изменить channel visibility в системных настройках, поэтому sensitive content нельзя помещать даже в скрываемую notification.

## Поведение под настоящим lockscreen

FadCam не содержит camera path, который разблокирует secure keyguard. Его Quick Settings tile при locked device вызывает штатный unlock flow. При уже идущей записи camera session продолжает принадлежать FGS.

В обоих приложениях есть fullscreen black Activity:

- FadCam `PrivacyBlackActivity` рисует чёрный view, держит экран включённым и выходит по жестам;
- BVR `BlackoutActivity` рисует чёрный fullscreen view и включает immersive UI.

Это fake privacy screen, а не Android lockscreen.

BVR также содержит `LockScreenActivity` с `showWhenLocked`, `turnScreenOn`, старыми window flags (`KEEP_SCREEN_ON`, `SHOW_WHEN_LOCKED`, `TURN_SCREEN_ON`, `DISMISS_KEYGUARD`) и deprecated full/screen wake lock + `ACQUIRE_CAUSES_WAKEUP` примерно на 30 секунд. На современном secure keyguard это не является надёжным bypass, будит/раздражает пользователя и противоречит модели DarkCat.

DarkCat не должен показывать camera/vault Activity поверх lockscreen, будить дисплей или пытаться dismiss keyguard. Правильная модель: пользователь включает режим в разблокированной видимой Activity, service остаётся активным, затем пользователь сам блокирует устройство обычной кнопкой питания.

## Android 13/14/15+ — что переносимо

| Механизм | Оценка для DarkCat |
|---|---|
| Service-owned camera/encoder surfaces | Современная основа; переносить архитектурный принцип |
| Camera/location FGS, запущенный из видимой Activity | Обязательно |
| Точный runtime FGS type mask | Обязательно |
| Видимая ongoing notification с безопасным content | Обязательно |
| `PARTIAL_WAKE_LOCK` со строгим lifecycle | Допустимо и полезно |
| Activity `dispatchKeyEvent` для видимого UI | Основной Volume+ path |
| MediaSession + remote `VolumeProvider` | Публичный API, но экспериментальный Field Mode adapter; hardware/policy validation pending |
| Notification actions с explicit immutable PendingIntent | Современно; components должны быть non-exported/protected |
| Quick Settings user action через PendingIntent/Activity | Современно; locked start лучше вести через unlock |
| `START_STICKY` как recovery strategy | Недостаточно |
| Camera FGS из `BOOT_COMPLETED` | Запрещено для target Android 15+ |
| Exact alarm/boot hack для camera restart | Не переносить |
| Manifest-wide лишние FGS types | Не переносить |
| Hidden `VOLUME_CHANGED_ACTION` | Unsupported/не переносить |
| `registerMediaButtonEventReceiver` | Deprecated/не переносить как основной path |
| Black screen вместо lockscreen | Запрещено требованиями DarkCat |
| `DISMISS_KEYGUARD`, full wake lock, wake screen | Устарело/не переносить |

Android 15 запрещает приложениям с target 35+ запускать camera FGS из `BOOT_COMPLETED`; microphone FGS из boot запрещён уже для target 34+. См. [Android 15 foreground-service changes](https://developer.android.com/about/versions/15/changes/foreground-service-types). Location-only restore нужно проектировать отдельно с `ACCESS_BACKGROUND_LOCATION`, сохранённым явным согласием пользователя и проверкой актуальных platform restrictions; camera type в boot flow не добавлять.

## Решение для DarkCat Field Mode

Рекомендуемый lifecycle:

1. Пользователь открывает разблокированную DarkCat Activity и явно включает Полевой режим.
2. Последовательно проверяются/запрашиваются notification, camera и location permissions; background location — отдельным системным шагом с объяснением.
3. Пока Activity видима, запускаются `GpsLockerService` и `CameraFieldService`.
4. Services сразу публикуют conservative ongoing notifications и переходят в foreground с точными type masks.
5. Camera service владеет camera/session/ImageReader; UI preview surface может attach/detach без разрушения capture path.
6. Partial wake lock и sensor subscriptions живут только внутри активного режима.
7. После штатной блокировки Activity и Vault ничего не показывают поверх keyguard. Уже открытая camera session остаётся service-owned.
8. Volume trigger adapter выбирает visible Activity path либо активный Field Mode MediaSession/VolumeProvider path.
9. Если камера была отозвана, service делает ограниченный быстрый reopen. При невозможности — FAIL haptic; никакого ложного success.
10. Stop action прекращает capture adapters, закрывает camera, unregister sensors/MediaSession, освобождает wake lock и только затем снимает foreground state.

Старт камеры после смерти процесса/boot не должен происходить из произвольного background receiver. Пользовательский notification/tile action может привести в Activity/unlock flow. GPS Locker разрешается восстанавливать отдельно только если выбранная permission/service architecture законна на фактическом targetSdk.

## Обязательные Pixel 7 / GrapheneOS проверки

Статический анализ не закрывает следующие вопросы:

1. Сохраняется ли уже configured Camera2 session после screen off и настоящего PIN/fingerprint lock.
2. Отзывает ли GrapheneOS camera device или повторяющийся request; сколько занимает reopen.
3. Получает ли активный remote `VolumeProvider` встроенный Volume+ под lockscreen.
4. Получает ли он Bluetooth HID Volume+ от конкретного пульта; не уходит ли событие другому media player.
5. Возникает ли duplicate trigger на key down/up или auto-repeat.
6. Возвращается ли обычная громкость сразу после отключения Field Mode/release MediaSession.
7. Что происходит после process kill системой, lock/unlock и permission revocation.
8. Как отображается PRIVATE/SECRET foreground notification в GrapheneOS lockscreen settings.

До выполнения этих проверок корректная формулировка статуса: «Field Mode architecture implemented; locked capture and Bluetooth Volume+ hardware validation pending», а не «работает на Pixel 7 / GrapheneOS».

## Источники

- [FadCam repository](https://github.com/anonfaded/FadCam)
- [FadCam v4.0.0-beta10 release](https://github.com/anonfaded/FadCam/releases/tag/v4.0.0-beta10)
- [BVR Pro product page](https://www.bvr-pro.com/main)
- [BVR Pro privacy policy](https://www.bvr-pro.com/privacypolicy)
- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android foreground-service background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android 15 foreground-service changes](https://developer.android.com/about/versions/15/changes/foreground-service-types)
- [Android MediaSession API](https://developer.android.com/reference/android/media/session/MediaSession)
- [Android Notification visibility API](https://developer.android.com/reference/android/app/Notification#VISIBILITY_PRIVATE)
