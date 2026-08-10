# Архитектура DarkCat Camera 0.3

DarkCat Camera — отдельное Android-приложение с `applicationId` `ru.darkcat.camera`. Версия 0.3 сохраняет Linked Camera v1.4/Open Camera как камеру и добавляет продуктовые слои DarkCat вокруг неё. CameraX не является основной камерой и в этот vertical slice не добавлялся.

Основная цель текущей итерации — подготовить приложение к полевой проверке на Google Pixel 7 с GrapheneOS. Это не заявление об аппаратной совместимости: Pixel 7/GrapheneOS ещё не тестировался.

## Границы слоёв

| Слой | Ответственность |
| --- | --- |
| `com.linkedcamera.app.*` | Linked/Open Camera preview, Camera1/Camera2, Camera2 physical camera ID, 3A, tap focus, фото/видео, flash и совместимость устройств |
| `ru.darkcat.camera.capture` | Максимальная скорость/приоритет резкости, Camera2 3A snapshot, гироскоп, дешёвая оценка резкости и ранжирование кандидатов |
| `ru.darkcat.camera.location` | Google-free GPS Locker на `LocationManager.GPS_PROVIDER`, возраст fix, GREEN/YELLOW/RED и strict capture gate |
| `ru.darkcat.camera.field` | Явно запущенный пользователем camera foreground service, private notification, wake lock и process-local bridge к открытой camera Activity |
| `ru.darkcat.camera.haptic` | Короткий success и отличимый fail; post-capture ошибки не переопределяют успешный кадр |
| `ru.darkcat.camera.data` | Настройки, CaptureContext, sequence allocator, SQLite media/upload state |
| `ru.darkcat.camera.tags` / `stamp` | Хранилище выбранных тегов и чистое форматирование технического блока |
| `ru.darkcat.camera.crypto` | Android Keystore, credential envelope и потоковый AES-256-GCM vault |
| `ru.darkcat.camera.vault` | App-private recovery journal, шифрование, UUID-файлы, encrypted thumbnails и защищённая галерея |
| `ru.darkcat.camera.upload` | Валидируемая state machine, WorkManager и provider adapters |
| `ru.darkcat.camera.diagnostics` | JSON capability report без media и координат |
| `ru.darkcat.camera.ui` / `editor` | Русская продуктовая оболочка, настройки, Vault, sync, diagnostics и object-based EDIT |

Advanced-функции Open Camera остаются в исходниках и доступны как compatibility/engineering surface. Они не должны дублировать обычные DarkCat-настройки.

## Критический путь фотосъёмки

Смысл «кадр сделан» ограничен двумя условиями: strict GPS policy разрешила попытку и camera callback подтвердил фактический capture.

```text
shutter / Volume+
  -> GPS gate
  -> Max Speed: сразу
     Sharp Priority: сразу либо краткое окно стабилизации <= 200 ms
  -> Linked/Open Camera Camera2 capture
  -> успешный camera callback
       -> короткий haptic НЕМЕДЛЕННО
       -> durable photo sequence reservation
       -> immutable capture ticket с capture-time LocationFix
  -> standard secure JPEG: app-private .tmp -> fsync -> atomic rename -> durable sidecar
  -> асинхронные stamp/edit, AES-GCM, vault, DB и sync queue
```

Ни JPEG encode, ни stamp, ни encryption, ни thumbnail, ни SQLite, ни upload не входят в success UX. Ошибка после camera callback сохраняется как storage/recovery problem и не даёт haptic «кадр не снят».

Photo sequence резервируется синхронным `SharedPreferences.commit()` только после успешного camera callback. До вызова shutter приложение фиксирует текущий non-stale `LocationFix`; callback повторно проверяет возраст именно этой точки и кладёт её в immutable ticket. Стандартный secure callback передаёт JPEG и ticket прямо в atomic recovery handoff до RAM-очереди ImageSaver, поэтому поздний GPS update/stamp не может подменить shutter fix. Если точка отсутствует или успела устареть, координаты честно остаются пустыми — processing-time `lastKnownLocation` не используется. Неуспешная попытка номер не увеличивает. Видео имеет отдельный счётчик.

## Camera2 и физические камеры

Camera2 — продуктовый default при наличии поддерживаемой реализации; Camera1 остаётся advanced fallback. Linked Camera уже умеет перечислять logical/physical camera relations и открывать конкретный physical ID через `cameraIdSPhysical`. Обычному пользователю должны показываться понятные варианты вроде `0.5×`, `1×`, `Фронтальная`, а сырые ID остаются в Advanced/diagnostics.

Текущий Camera2 engine:

- использует continuous-picture AF по умолчанию и latency-optimized capture вместо обязательного autofocus cycle перед каждым кадром;
- сохраняет tap-to-focus и возврат в continuous workflow средствами upstream;
- публикует последние `CONTROL_AF_STATE`, `CONTROL_AE_STATE` и `CONTROL_AWB_STATE` как advisory readiness, но не ждёт их бесконечно;
- запрашивает `CONTROL_ENABLE_ZSL=true` для обычного still capture на Android 8+ вне extension session;
- сохраняет обычный capture fallback при отсутствии ZSL/reprocessing или несовместимом режиме.

Наличие request-флага ZSL не доказывает, что HAL реально выдал zero-shutter-lag кадр. Это проверяется на Pixel по timestamps и поведению камеры.

## Best Frame: реализованная граница

Есть pure engine: bounded metadata ring, variance-of-Laplacian на уменьшенной luminance plane, gyroscope motion score, Camera2 3A score и temporal distance. Когда camera preview представлен `TextureView`, runtime `BestFrameMonitor` раз в 120 ms снимает уменьшенный `160×120` bitmap, преобразует его в luminance и хранит до 12 candidate metadata в окне около shutter. Наличие достаточно резкого/стабильного candidate позволяет Sharp Priority снимать немедленно. Для другого типа preview монитор безопасно не запускается.

Engine **ещё не заменяет JPEG, выбранный Camera2/Open Camera**. Preview bitmap служит только advisory score, а ring хранит метаданные кандидатов и не владеет YUV/PRIVATE `Image`. Поэтому нельзя заявлять, что итоговый JPEG уже автоматически выбран из нескольких кадров. Рабочий runtime fallback — обычный быстрый Camera2 capture; подробности в [FAST_SHARP_CAPTURE.md](FAST_SHARP_CAPTURE.md).

## Полевой режим и lockscreen

`FieldModeService` — foreground service типа `camera`, который пользователь запускает из видимой Activity. Он держит visible private notification, partial wake lock и MediaSession/VolumeProvider adapter. Камерой продолжает владеть Linked Camera `MainActivity`; service вызывает тот же shutter path через weak process-local `FieldCaptureBridge`.

Это фундамент удержания уже открытой session, а не независимый service-owned camera engine:

- Activity и процесс живы — bridge может использовать тёплую camera session;
- Activity/session были уничтожены системой — service не притворяется, что камера готова, а notification просит открыть камеру для восстановления;
- process recreation требует reopen через Activity;
- приложение не рисует поверх lockscreen, не показывает Vault и не разблокирует устройство;
- camera FGS не стартует автоматически после boot.

Android 14+ запрещает произвольно создавать camera/location FGS из background, когда используются while-in-use permissions. Поэтому Field Mode запускается только в допустимом foreground state после явного действия пользователя. На Android 15+ camera FGS также нельзя восстанавливать из `BOOT_COMPLETED`. См. [официальные ограничения запуска FGS](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [типы foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types) и [изменения Android 15](https://developer.android.com/about/versions/15/changes/foreground-service-types).

## GPS Locker

GPS отделён от камеры. `GpsLockerService` — foreground service типа `location`, использующий системный `LocationManager.GPS_PROVIDER` с callback-интервалом 1 секунду и нулевой минимальной дистанцией. Google Play Services не обязательны. Возраст location вычисляется только по monotonic elapsed realtime, поэтому переводы системных часов не «освежают» старый fix.

После boot можно пытаться восстановить только GPS Locker, и только если пользователь ранее включил его и выдал fine/background location. Отказ платформы безопасно оставляет восстановление до открытия приложения. Camera service на boot не запускается.

## Durable recovery и Vault

Secure Mode по умолчанию включён на API 23+. Стандартный JPEG из camera callback сразу пишется через игнорируемый `.tmp`, `fsync` и atomic rename в app-private `recovery-pending`, после чего получает sidecar-журнал. Только затем начинается editor/stamp/encryption pipeline. Очередь post-processing сериализована: это даёт bounded backpressure без хранения полного JPEG в её задачах.

Перед shutter выполняется консервативный `StatFs` preflight: при наличии заранее записанного аварийного файла требуется не менее 64 МиБ свободного рабочего пространства. В private vault поддерживается реальный (не sparse) резерв 64 МиБ. Если recovery-write неожиданно получает `ENOSPC`, резерв освобождается и атомарная запись повторяется один раз; после успешного pipeline резерв создаётся заново. Переживающий restart флаг storage-blocked не снимается, пока не пройдут новый capacity check и маленькая `fsync`-проба.

```text
RECOVERY_PENDING plaintext (app-private)
  -> optional EDIT/stamp
  -> AES-256-GCM temporary vault file
  -> atomic rename to random UUID .dcv
  -> encrypted thumbnail
  -> SQLite row ENCRYPTED
  -> удалить recovery plaintext + sidecar
  -> optional QUEUED
```

Recovery не имеет TTL и переживает restart. Необработанные EDIT-записи автоматически не flattenятся, чтобы не потерять пользовательское решение; Vault показывает их отдельно и позволяет снова открыть редактор либо явно сохранить исходник без правок. Public source удаляется только после появления durable private recovery copy и журнала.

Для уже завершённого upstream-видео до возврата из callback синхронно публикуется маленький app-private `external-pending` journal (`tmp` → `fsync` → rename), содержащий путь/MediaStore URI. Большое копирование остаётся асинхронным, но process recreation повторяет его по стабильному recovery ID; reference удаляется только после private media + recovery sidecar и подтверждённого удаления внешнего source. Если даже reference journal записать нельзя, interceptor возвращает управление upstream, чтобы тот завершил обычное сохранение вместо ложного secure-success.

Vault filename/DB identity детерминирован для конкретного recovery path. Повтор после crash между encryption, DB insert и plaintext cleanup сходится к одной записи, а ciphertext и GCM tag синхронизируются на диск до публикации. Thumbnail декодируется с sampling до 512 px. Decrypted viewer cache имеет уникальное session-имя, удаляется в `onDestroy`, а остатки умершего процесса очищаются при следующем запуске.

## Object editor

EDIT использует first-party image-space overlay на Android Canvas. Source bitmap и freehand/line/rectangle/oval/arrow/text остаются отдельными объектами до Save. Hit-test позволяет снова выбрать старый объект; один палец двигает, pinch масштабирует/вращает, context actions меняют цвет/толщину или удаляют. Undo/redo хранит bounded history (24 snapshots). Crop — интерактивная перемещаемая/масштабируемая рамка с сеткой третей и Apply/Cancel.

Save flatten-ит source resolution в JPEG quality 100. Сначала создаются fsync-ed edited recovery bytes и второй durable sidecar; только затем выполняются stamp/vault commit и удаление исходного recovery. Выход без Save оставляет исходный pending материал. Gesture UX, поворот экрана и память на максимальном Pixel resolution аппаратно не проверены.

## Sync

Upload работает независимо от capture через WorkManager. Runtime меняет статусы только через проверяемые переходы:

```text
CAPTURED -> RECOVERY_PENDING -> ENCRYPTED -> QUEUED -> UPLOADING
  -> UPLOADED -> VERIFIED -> LOCAL_DELETE_PENDING -> LOCAL_DELETED
  -> FAILED_RETRYABLE / FAILED_PERMANENT -> QUEUED
```

На практике первые два состояния существуют прежде всего в recovery journal, а SQLite media row создаётся при `ENCRYPTED`. HTTP 2xx означает `UPLOADED`, но не `VERIFIED`. Generic WebDAV подтверждает `VERIFIED` только успешным HEAD с точным `Content-Length`; отсутствие длины либо ошибка HEAD после принятого PUT оставляет `UPLOADED`. KEEP LOCAL — default, удаление возможно только после VERIFIED и явного opt-in.

## Diagnostics

Advanced export создаёт JSON в app-specific external storage. В него входят модель/Android/security patch, camera IDs, logical/physical relations, focal lengths, hardware level, flash, AF/OIS, reprocessing/ZSL capabilities, ограниченный список stream sizes, extensions, выбранная камера и состояния services/GPS/volume adapter.

Точные координаты и media в export не включаются. Diagnostics — средство аппаратной проверки, а не доказательство, что объявленная HAL capability работает корректно.

## Intent contract

Будущий CRM caller может использовать action `ru.darkcat.camera.action.CAPTURE` и передать `ru.darkcat.camera.extra.CAPTURE_CONTEXT` JSON либо отдельные extras: `CRM_OBJECT_ID`, `INSPECTION_ID`, `TASK_ID`, `USER_ID`, `CUSTOM_TAGS`. Отсутствующий контекст допустим. DarkCat CRM не входит в APK и не является зависимостью.
