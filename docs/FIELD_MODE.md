# Полевой режим

## Назначение и статус

Полевой режим — явно включаемый пользователем режим готовности камеры и GPS при background/screen off/штатном lockscreen. Он не является скрытой камерой и не имитирует блокировку.

Текущий статус — **0.4 implementation complete, hardware validation pending**. Camera foreground service, независимая Camera2 session, notification, wake lock, GPS gate и trigger adapters реализованы. Удержание session и Bluetooth Volume+ на целевых устройствах ещё не подтверждены аппаратно.

## Пользовательский сценарий

1. Пользователь открывает DarkCat Camera в foreground.
2. Включает «Полевой режим» и подтверждает safety screen.
3. Поэтапно выдаёт CAMERA, notification и необходимые location permissions.
4. Приложение запускает camera FGS и, если выбран GPS Locker, location FGS.
5. В foreground камера использует зрелый Linked/Open Camera engine; после ухода Activity в background ownership передаётся service-owned Camera2 session.
6. Пользователь штатной кнопкой питания выключает экран/блокирует Android.
7. Приложение не снимает системную защиту; PIN/пароль/fingerprint остаются единственным lockscreen.
8. Volume+ trigger при доступном routing вызывает тот же GPS-gated shutter path.
9. Режим останавливается в приложении или private foreground notification.

Safety screen должен явно сообщать: camera/GPS продолжат работать, появятся системные notifications, расход батареи возрастёт, Android lockscreen останется активен, Vault поверх него не показывается.

## Компоненты

### `FieldModeService`

- объявлен с `foregroundServiceType="camera"`;
- стартует только вызовом `startFromVisibleActivity()` после пользовательского действия;
- вызывает `startForeground(..., FOREGROUND_SERVICE_TYPE_CAMERA)`;
- держит non-reference-counted `PARTIAL_WAKE_LOCK` до остановки режима;
- возвращает `START_NOT_STICKY`: camera FGS не должен самовольно возрождаться после process death;
- если платформа отклоняет typed `startForeground`/runtime setup, сбрасывает persisted mode, отмечает ошибку и останавливается вместо падения процесса;
- публикует private ongoing notification с безопасным GPS/queue status;
- никогда не открывает Vault и не рисует поверх lockscreen.

### `MainActivity`, `FieldModeService` и `FieldCaptureBridge`

`FieldCaptureBridge` содержит только сильную ссылку на явный service endpoint и не знает об Activity. `MainActivity` передаёт ownership-сигнал при `onResume`/`onPause`, но не является владельцем locked/background capture.

Когда Activity видима, снимки и lens/quality настройки идут через сохранённый Linked/Open Camera engine. Когда Activity уходит в background, `FieldModeService` останавливает Activity session и запускает `FieldCameraSessionOwner`: отдельный Camera2 controller с HandlerThread, JPEG/YUV readers, continuous AF/AE/AWB и повторным open после camera error/disconnect. JPEG сначала fsync-ится в app-private каталоге, затем передаётся в общий GPS-gated capture callback и recovery pipeline.

Следствия этой границы:

- bridge и camera ownership не зависят от `WeakReference<MainActivity>`;
- Activity/process живы — обычный engine сохраняет полный UI, physical-lens и advanced compatibility surface;
- Activity в background/при screen off — service-owned session продолжает готовность к capture без View/Activity;
- process death не теряет уже записанный service JPEG: при следующем service start оставшийся durable файл повторно передаётся в secure recovery;
- notification показывает «Камера готова» только когда service-owned session действительно configured, иначе просит открыть камеру;
- service-owned fallback intentionally использует Camera2 rear logical camera; выбор lens/quality остаётся за visible Linked/Open Camera UI.

### `GpsLockerService`

GPS — отдельная ответственность и может оставаться активной независимо от camera Activity. Подробности в [GPS_LOCKER.md](GPS_LOCKER.md).

### `Sync Worker`

Sync не живёт в camera service и не блокирует shutter. Notification action «Отправить очередь» только ставит допустимые записи в WorkManager.

## Lifecycle

| Событие | Ожидаемое поведение текущей архитектуры |
| --- | --- |
| Activity visible | Пользователь может законно запустить camera/location FGS; снимки идут через Linked/Open Camera engine |
| App background | Activity session закрывается, FGS запускает service-owned Camera2 session |
| Screen off | Wake lock сохраняет CPU callbacks, но не держит экран и не отменяет OEM/GrapheneOS camera policy |
| Real lock | Системный lockscreen остаётся; service-owned session принимает разрешённый trigger |
| Unlock/return | Service session останавливается, Activity возвращает Linked/Open Camera preview |
| Activity destroyed | Service endpoint и session остаются независимыми от Activity |
| Process killed | FGS `START_NOT_STICKY`; следующий явный старт восстанавливает session и durable JPEG handoff |
| Reboot | Camera FGS не стартует; возможна только осторожная попытка location-only restore |
| Camera privacy toggle/permission revoke | Android закрывает/не даёт camera; capture должен завершиться fail, без обхода системы |

Это ожидаемая модель. Каждая строка screen off/lock/unlock должна быть проверена на Pixel 7/GrapheneOS.

## Volume+ и Bluetooth HID

Есть два адаптера:

1. При видимой Activity `onKeyDown` получает `KEYCODE_VOLUME_UP`; DarkCat принимает только первое событие нажатия, а `Volume−` оставляет системной регулировке.
2. В Field Mode активная `MediaSession` с remote relative `VolumeProvider` реагирует на `AudioManager.ADJUST_RAISE` и передаёт trigger в `FieldCaptureBridge`.

Обычный Volume+ не обязан превращаться в `MEDIA_BUTTON`. `MediaSession.FLAG_HANDLES_MEDIA_BUTTONS` само по себе этого не гарантирует, а Bluetooth shutter remotes отличаются: одни посылают HID volume key, другие media/camera key. Поэтому locked-screen Bluetooth path считается **hardware-validation pending**, а не готовым фактом. См. Android [`MediaController.dispatchMediaButtonEvent`](https://developer.android.com/reference/android/media/session/MediaController#dispatchMediaButtonEvent(android.view.KeyEvent)) и [`VolumeProvider`](https://developer.android.com/reference/android/media/VolumeProvider).

Оба trigger path используют общий 300 ms anti-duplicate debounce. MediaSession активна только пока включены Field Mode и его Volume+ adapter. После отключения adapter/режима она release-ится, чтобы Volume+ снова работал как обычная регулировка громкости. Если bridge отсутствует, trigger даёт fail haptic, а не увеличивает sequence. Явная остановка Field Mode также закрывает camera session, которую Activity держала тёплой в background.

## Notification

Заголовок: `DarkCat Camera · Полевой режим`.

Разрешённые данные:

- «Камера готова» либо просьба открыть камеру для восстановления;
- GPS `±N м` или «поиск/заблокирована»;
- размер очереди.

Actions:

- открыть камеру;
- отправить очередь;
- остановить камеру;
- остановить всё (Field Mode и GPS Locker).

Notification имеет `VISIBILITY_PRIVATE`; public version сообщает только, что режим активен. Thumbnails, координаты, tags, CRM context и история съёмки не показываются.

## Современные Android restrictions

Для targetSdk 36 объявлены `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, CAMERA и service type `camera`. CAMERA — while-in-use permission: на Android 14+ создать camera FGS из произвольного background state обычно нельзя, даже если generic FGS-start exception формально применима. Запуск выполняется из видимой Activity.

Начиная с Android 12 background start FGS ограничен; Android 14 проверяет type-specific permissions при создании service; Android 15 запрещает camera FGS из `BOOT_COMPLETED`. DarkCat не обходит эти ограничения и не пытается auto-start camera after boot.

Официальные источники:

- [Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Declare foreground services and request permissions](https://developer.android.com/develop/background-work/services/fgs/declare)
- [Android 15 foreground service changes](https://developer.android.com/about/versions/15/changes/foreground-service-types)

## Permission onboarding

Рекомендуемый порядок диалогов:

1. CAMERA — при обычном первом открытии camera.
2. FINE/COARSE LOCATION — при включении GPS Locker/geotagging.
3. POST_NOTIFICATIONS — перед Field/GPS FGS notification на Android 13+.
4. ACCESS_BACKGROUND_LOCATION — отдельным шагом только для постоянного GPS/boot restore.
5. RECORD_AUDIO — только при первом видео со звуком, не для фото Field Mode.

`FOREGROUND_SERVICE*`, `WAKE_LOCK`, `VIBRATE` и `RECEIVE_BOOT_COMPLETED` — manifest permissions без runtime dialog.

## Аппаратный gate

До прохождения [PIXEL7_TEST.md](PIXEL7_TEST.md) нельзя заявлять:

- camera session действительно остаётся warm после power button/real lock;
- Bluetooth Volume+ приходит в service при заблокированном экране;
- lock/unlock не вызывает cold restart;
- GrapheneOS не ограничивает session выбранной конфигурацией;
- haptic/notification routing соответствует ожиданиям конкретного устройства.
