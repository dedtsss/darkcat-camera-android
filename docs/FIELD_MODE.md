# Полевой режим

## Назначение и статус

Полевой режим — явно включаемый пользователем режим готовности камеры и GPS при background/screen off/штатном lockscreen. Он не является скрытой камерой и не имитирует блокировку.

Текущий статус — **foundation, hardware validation pending**. Camera foreground service, notification, wake lock и trigger bridge реализованы, но удержание camera session и Bluetooth Volume+ на Google Pixel 7/GrapheneOS ещё не подтверждены.

## Пользовательский сценарий

1. Пользователь открывает DarkCat Camera в foreground.
2. Включает «Полевой режим» и подтверждает safety screen.
3. Поэтапно выдаёт CAMERA, notification и необходимые location permissions.
4. Приложение запускает camera FGS и, если выбран GPS Locker, location FGS.
5. Камера уже открыта и прогрета в Linked Camera Activity.
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

### `MainActivity` и `FieldCaptureBridge`

CameraDevice/session остаются собственностью Linked/Open Camera Activity. Activity регистрирует weak process-local target. Service может вызвать `requestFieldCapture()`, который переходит в UI thread и использует общий capture gate.

Следствия этой границы важны:

- пока Activity/process живы, существующая session может оставаться OPEN/CONFIGURED/WARM;
- foreground service повышает легитимность/видимость долгой camera работы, но не гарантирует, что конкретная ОС не закроет Activity-owned session;
- если Activity, camera или process уничтожены, weak bridge недоступен;
- notification показывает «Откройте камеру для восстановления», а не ложное «Камера готова»;
- process recreation требует открытия Activity и быстрого reopen — service самостоятельно session не реконструирует.

Отдельный service-owned Camera2 controller в этой итерации не создавался, чтобы не дублировать и не рассинхронизировать зрелый Linked/Open Camera engine.

### `GpsLockerService`

GPS — отдельная ответственность и может оставаться активной независимо от camera Activity. Подробности в [GPS_LOCKER.md](GPS_LOCKER.md).

### `Sync Worker`

Sync не живёт в camera service и не блокирует shutter. Notification action «Отправить очередь» только ставит допустимые записи в WorkManager.

## Lifecycle

| Событие | Ожидаемое поведение текущей архитектуры |
| --- | --- |
| Activity visible | Пользователь может законно запустить camera/location FGS; camera bridge готов |
| App background | FGS/notification остаются; Activity-owned session пытается остаться тёплой |
| Screen off | Wake lock сохраняет CPU callbacks, но не держит экран и не отменяет OEM/GrapheneOS camera policy |
| Real lock | Системный lockscreen остаётся; camera bridge может работать только если Activity/session не уничтожены |
| Unlock/return | При живой session — продолжение; иначе Activity выполняет reopen |
| Activity destroyed | Bridge становится unavailable; service не сообщает ложную готовность |
| Process killed | Camera и MediaSession теряются; camera FGS `START_NOT_STICKY`, требуется явное открытие приложения |
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
- остановить режим.

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
