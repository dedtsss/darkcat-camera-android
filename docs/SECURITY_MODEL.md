# Модель безопасности DarkCat Camera

## Цели

Модель защищает полевые фото, thumbnails, sync credentials и чувствительный UI от случайного раскрытия на штатно заблокированном Android-устройстве. Она также стремится не потерять уже полученный камерой кадр при сбое post-processing.

Она не заявляет защиту от root-доступа, скомпрометированной ОС, извлечения данных из уже разблокированного процесса или физической атаки на устройство без актуального шифрования Android user data.

## Lockscreen

DarkCat Camera использует настоящий системный lockscreen и никогда:

- не снимает PIN/пароль/biometric lock;
- не рисует fake lockscreen;
- не применяет `showWhenLocked` для Vault/Viewer/Editor;
- не публикует thumbnails, tags, адреса или координаты в lockscreen notification;
- не делает background camera невидимой: Field Mode всегда сопровождается foreground notification.

Field/GPS notifications имеют `VISIBILITY_PRIVATE` и консервативную public version. Vault, Viewer, Editor, DarkCat Settings и Sync используют `FLAG_SECURE`; переход к чувствительным экранам из camera UI проходит через штатный keyguard challenge. Приложение сохраняет `allowBackup="false"`.

## Ключи и форматы

### Vault media

- алгоритм: AES-256-GCM;
- ключ: non-exportable Android Keystore key;
- IV: новый provider-generated 12-byte IV для каждого файла;
- header: magic/version + IV + ciphertext/GCM tag;
- имя: случайный UUID, не исходное имя файла;
- encryption потоковый, без загрузки полного media в RAM;
- decrypt сначала пишет temporary file и публикует destination только после успешной проверки GCM tag;
- encrypted file получает SHA-256 и размер для локальной целостности/sync metadata.

Encrypted thumbnails используют тот же authenticated vault cipher и отдельные случайные имена.

### Credentials

WebDAV/Nextcloud secrets используют отдельный Keystore AES-GCM key. Исправлена несовместимость `Caller-provided IV not permitted`: encryption вызывает `cipher.init(ENCRYPT_MODE, key)` без пользовательского IV, затем сохраняет `cipher.getIV()` рядом с ciphertext. Decrypt извлекает сохранённый IV и передаёт его в `GCMParameterSpec`.

Envelope остаётся `12-byte IV || ciphertext || 128-bit tag`, чтобы ранее записанные данные оставались читаемы. Повреждение ciphertext/tag вызывает authentication failure; `SecureCredentialStore` не выдаёт повреждённое значение вызывающему коду.

## Plaintext boundary и recovery

После успешного camera callback стандартный secure JPEG не отправляется как единственная копия в RAM queue: он записывается в app-private `.tmp`, `fsync`-ится, проверяется по ожидаемой длине, атомарно переименовывается и получает sidecar. `RecoveryStore` требует непустой файл внутри `files/darkcat-vault/recovery-pending` и атомарно записывает sequence, время capture, MIME, display name, CaptureContext и признак EDIT.

Plaintext recovery — сознательный компромисс между конфиденциальностью и правилом «не потерять полученный кадр»:

- каталог доступен только sandbox приложения;
- нет автоматического TTL удаления;
- process death, disk-full, ошибка stamp/encryption/DB оставляют recovery для следующего запуска;
- recovery удаляется только после успешного encrypted vault commit и durable DB row;
- исходный public/MediaStore объект удаляется только после private copy + journal;
- незавершённый EDIT остаётся recovery pending и доступен из защищённого Vault для повторного открытия либо явного сохранения без правок.

Для disk-full сценария приложение заранее выделяет в private vault настоящий аварийный резерв 64 МиБ. Capture preflight требует ещё 64 МиБ рабочего пространства; при неожиданной ошибке записи резерв удаляется и atomic recovery-write повторяется один раз. После storage failure новые capture блокируются до успешных `StatFs` check и durable `fsync` probe. Это снижает риск потери уже возвращённого камерой JPEG, но не является абсолютной гарантией при аппаратном I/O failure или когда JPEG/служебные данные превышают доступный после освобождения резерв объём.

При EDIT Save новый flattened JPEG сначала записывается как отдельный app-private temporary file, fsync-ится, atomically rename-ится и получает собственный recovery sidecar с исходными sequence/time/context. Только после успешного vault+DB commit удаляются edited plaintext и original recovery. Crash между этапами может оставить оба recoverable файла, что предпочтительнее потери кадра.

Завершённое upstream-видео сначала получает небольшой durable `external-pending` reference journal. Поэтому process death во время большого private copy не теряет ссылку на file/MediaStore source; restart повторяет перенос по стабильному ID. Внешний source и reference удаляются только после полной private copy и sidecar. Если reference создать нельзя, upstream завершает обычное сохранение, а UI отмечает storage incident — это намеренный fail-open в пользу сохранности материала, но не обещание `No DCIM plaintext` при отказе накопителя.

Vault commit использует стабильный ID recovery path: повтор после уже созданной DB-записи завершает только cleanup, не создавая дубль. Ciphertext вместе с GCM tag проходит `fsync`; pre-DB orphan artifacts очищаются, а исходный recovery остаётся. Полноразмерный decrypted viewer cache создаётся под уникальным session-именем, удаляется при закрытии и scavenged после process restart.

Открытие уже зашифрованного media для Viewer/Editor создаёт временный decrypted cache file. UI защищён `FLAG_SECURE`, но cache не является отдельным encrypted filesystem. Его жизненный цикл и очистку требуется дополнительно проверить на реальном Android/при process death.

## App-private metadata

SQLite queue/media metadata и recovery sidecars находятся в private internal storage, но сами не завернуты в per-record AES-GCM. Metadata может включать time, tags, CaptureContext и проверенные на shutter-time coordinates. Устаревшая либо полученная позже system last-known точка не подставляется. На штатно заблокированном актуальном Android этот слой дополнительно опирается на sandbox и device/user-data encryption; его нельзя представлять как отдельную криптографическую защиту от root/скомпрометированной ОС.

Diagnostics намеренно исключает media и точные координаты: экспортируются только provider, accuracy и age.

## Capture success и ошибки

Короткий success haptic отправляется непосредственно после успешного camera capture callback. Он не ждёт recovery, editor, stamp, encryption, DB или upload.

Длинный fail pattern относится только к pre-capture rejection/camera failure: нет допустимого GPS, камера недоступна, trigger отклонён или camera capture сообщил ошибку. Post-capture failure отображается как recovery/storage/sync problem и **не** вибрирует как «кадр не снят».

## Upload и retention

- Offline capture не зависит от сети/provider.
- Provider по умолчанию `Off`; auto upload по умолчанию выключен.
- KEEP LOCAL по умолчанию включён.
- PUT/HTTP 2xx означает только `UPLOADED`.
- Generic WebDAV становится `VERIFIED` только после отдельного успешного HEAD с `Content-Length`, точно равным размеру encrypted object.
- HEAD без длины, несовпадающая длина, ошибка HEAD после принятого PUT или неподтверждённый ETag не дают VERIFIED; объект остаётся UPLOADED.
- Local deletion проходит `VERIFIED -> LOCAL_DELETE_PENDING -> LOCAL_DELETED`; ошибка удаления остаётся retryable pending, а не маскируется как успех.
- Credentials не пишутся в обычные preferences/logs; URL и username не считаются секретом текущей модели, password/token — считаются.

WebDAV Basic authentication допустим только поверх доверенного HTTPS endpoint. Certificate pinning в этом vertical slice не реализован.

## Foreground services и permissions

В manifest явно объявлены `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_LOCATION`, CAMERA/location permissions, notifications, vibration и wake lock. Camera/location FGS запускаются только из разрешённого Android состояния после пользовательского действия. Это соответствует ограничениям while-in-use permissions на Android 14+; camera FGS не восстанавливается из boot receiver.

`ACCESS_BACKGROUND_LOCATION` используется только для выбранного пользователем постоянного GPS/boot-restore сценария и должен запрашиваться отдельным понятным шагом. Не следует запрашивать все разрешения одним пакетом.

Источники Android: [декларация FGS и permissions](https://developer.android.com/develop/background-work/services/fgs/declare), [camera/location service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## Известные границы

1. Secure Mode требует Android Keystore AES-GCM (API 23+). Camera core продолжает поддерживать API 21/22, но этот путь не является проверенным secure vault.
2. Pixel 7/GrapheneOS ещё не проверялся; нельзя заявлять, что камера останется открытой при screen off/lock или что Bluetooth Volume+ маршрутизируется service.
3. Process recreation уничтожает Activity-owned camera session; требуется открыть Activity и восстановить camera bridge.
4. Recovery plaintext защищён sandbox/device encryption, но не отдельным DarkCat AES-GCM до завершения pipeline.
5. Android/GrapheneOS camera and microphone privacy toggles всегда имеют приоритет; приложение их не обходит.
6. Основной стандартный JPEG path имеет прямой durable handoff, а завершённое видео — durable external-reference journal до async copy. Скрытые Advanced multi-frame/RAW пути сохраняют больше upstream lifecycle и требуют отдельной fault-injection/hardware проверки перед production claim `No DCIM plaintext` для каждой комбинации.
