# GPS Locker

## Цель и backend

GPS Locker постоянно держит свежий GNSS fix для capture gate. Батарея не является приоритетом, но subsystem работает callback-based без busy loops.

Основной backend — Android `LocationManager.GPS_PROVIDER`:

- Google Play Services не требуется;
- запрос обновлений: 1 000 ms;
- минимальная дистанция: 0 m;
- используются provider callbacks и monotonic `Location.getElapsedRealtimeNanos()`;
- age ticker переоценивает состояние каждую секунду, даже если новых fixes нет.

Таким образом, последний fix не остаётся GREEN после прекращения location delivery.

## Service lifecycle

`GpsLockerService` объявлен с `foregroundServiceType="location"`, запускает ongoing private notification и возвращает `START_STICKY`. Он логически независим от camera Activity и Field Mode: сворачивание/выключение экрана не вызывает программного прекращения updates.

Пользователь может включить GPS Locker самостоятельно или вместе с Field Mode. После boot `GpsBootReceiver` пытается восстановить только location service, если настройка была включена и выданы FINE + BACKGROUND LOCATION. Camera service на boot никогда не запускается. Платформа всё равно может отказать в старте; в таком случае пользователь восстанавливает locker из видимого приложения.

В Field notification доступны независимые действия «Остановить камеру» и «Остановить всё». Первое закрывает только camera FGS, второе сбрасывает и Field Mode, и GPS Locker; остановка камеры сама по себе не должна silently выключать GPS Locker.

Отказ typed foreground promotion либо запуска provider перехватывается внутри service: persisted running-setting сбрасывается, service останавливается, а процесс приложения не падает.

GrapheneOS-specific Google APIs не нужны. Системные location toggles, per-app permissions и sensor/privacy policy имеют приоритет.

## Состояния

Один `GpsPolicy` используется и для UI, и для capture decision. Default accuracy threshold — **7 m**.

| Индикатор | Условие |
| --- | --- |
| GREEN | provider работает, fix есть, age `0–5 000 ms`, accuracy существует и `<= threshold` |
| YELLOW | provider работает, но идёт поиск; accuracy отсутствует/хуже threshold; либо точный fix стареет (`>5 000 ms` и `<=15 000 ms`) |
| RED | locker остановлен, permission denied, location disabled, provider unavailable/error, monotonic clock mismatch или fix старше `15 000 ms` |

Dynamic label строится из реального `Location.getAccuracy()`: до 10 м он сохраняет один десятичный знак (`±4.2 м -> ±6.6 м`), далее отображается без выдуманных ступеней. Если accuracy неизвестна, показывается `±— м`, а не выдуманное значение.

## Fresh / aging / stale

Текущие conservative defaults:

- `fresh`: age `<= 5 s`;
- `aging`: age `> 5 s` и `<= 15 s`;
- `stale`: age `> 15 s` либо отрицательный/несогласованный monotonic age.

Thresholds вынесены в `GpsPolicy`/internal settings (`darkcat_location_fresh_ms`, `darkcat_location_stale_ms`) и валидируются: stale должен быть строго больше fresh. Расчёт использует elapsed realtime, а не wall clock/location text timestamp.

## Capture policy

Настройка «Запрещать снимок без точного GPS» включена по умолчанию.

При strict policy capture блокируется, если:

- GPS Locker остановлен;
- permission/provider/location unavailable;
- fix отсутствует;
- fix stale;
- accuracy отсутствует;
- `accuracy > configured threshold`.

Точный aging fix пока допускается: UI становится YELLOW, но до stale boundary capture не блокируется. Это явное текущее правило, покрытое pure tests. При strict policy OFF capture всегда разрешается со стороны GPS, но UI продолжает честно показывать RED/YELLOW; отсутствие GPS не превращается в GREEN.

GPS rejection происходит до camera call, даёт заметный fail haptic и не резервирует sequence. Network/sync status никогда не участвует в решении.

## Согласованность данных

`GpsLockerController` владеет полным `LocationFix` для state/gate. Публичный диагностический `LocationSnapshotStore.Snapshot` намеренно содержит только provider, accuracy и monotonic age — без latitude/longitude.

Непосредственно перед shutter immutable capture-attempt получает текущий non-stale `LocationFix`. На успешном camera callback возраст именно этой точки проверяется повторно, после чего `PhotoCaptureTicket` объединяет её с активными tags/CRM context, а recovery sidecar сохраняет coordinates, accuracy, provider и elapsed-realtime timestamp. Поэтому дальнейшие GPS updates не меняют привязку уже снятого кадра. Если исходная точка отсутствует или устарела, stamp/metadata сохраняют координаты пустыми; новая либо произвольная system last-known точка во время post-processing не подставляется.

## Permissions

- `ACCESS_FINE_LOCATION` — обязателен для точного GNSS;
- `ACCESS_COARSE_LOCATION` — Android permission pair/fallback, но сам GPS Locker требует fine;
- `FOREGROUND_SERVICE_LOCATION` и service type `location` — targetSdk 36;
- `POST_NOTIFICATIONS` — видимость service пользователю на Android 13+;
- `ACCESS_BACKGROUND_LOCATION` — отдельный explicit grant для постоянного/boot-restore сценария, а не скрытая массовая просьба.

Location FGS должен быть запущен из разрешённого foreground/user-visible состояния, если нет корректной background-location exemption. См. [Android location foreground service type](https://developer.android.com/develop/background-work/services/fgs/service-types#location) и [while-in-use restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions).

## Failure/recovery

- Runtime permission revoke преобразуется в RED/permission denied.
- Provider disable преобразуется в RED; старый fix не используется как GREEN.
- Service restart создаёт новый controller и может использовать last-known GPS только с его настоящим monotonic timestamp.
- Нет cold-start suppression ради батареи: после старта сразу запрашиваются GNSS updates.
- Нет busy polling, alarm loop или Google-only dependency.

Фактическое поддержание warm GNSS после screen off/real lock и поведение boot restore должны быть проверены на Pixel 7/GrapheneOS.
