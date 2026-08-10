# Лицензионная стратегия

DarkCat Camera — намеренный GPLv3-compatible fork. Это не MIT-приложение, и его нельзя так представлять.

## Основная производная работа

- Base repository: `https://github.com/UrbanVue/linked_camera`
- Imported upstream: tag `v1.4`, commit `7a504c0329aef71adb4d191e3f28910e762b74ea`
- Upstream license: GNU GPLv3
- Origin: Linked Camera основана на Open Camera by Mark Harman
- Сохранены `LICENSE`, GPL text, upstream history, source notices и атрибуция Linked/Open Camera.

Весь APK, включая DarkCat-specific Java layers, распространяется на условиях, совместимых с GPLv3. При распространении бинарника необходимо предоставить соответствующий исходный код, build instructions и notices.

## DarkCat additions

`ru.darkcat.camera.*` добавляет capture policy, GPS Locker, Field Mode foundation, secure recovery/vault, metadata/tags/sequence, diagnostics, editor integration и provider/sync adapters. CameraX layer из PR #1 не переносился; security/recovery concepts были переосмыслены и реализованы поверх Linked/Open Camera.

## Сторонние зависимости

- `com.burhanrashid52:photoeditor:3.1.0` — MIT, остаётся объявленной/атрибутированной в `THIRD_PARTY_NOTICES.md`; новый object overlay/editor этого vertical slice реализован независимо на Android Canvas и не копирует библиотечный graphics framework.
- AndroidX AppCompat/ExifInterface/WorkManager/test libraries — Apache License 2.0, совместимы с GPLv3 distribution при сохранении соответствующих notices.
- Material/Open Camera icons и другие сохранённые upstream assets следуют их исходным notices.

Новая crop-библиотека в этом vertical slice не добавлялась. Если для интерактивного crop/object editor будет выбрана зависимость, до merge нужно проверить её точную версию, license file, transitive dependencies и добавить notice. Библиотека с несовместимой closed/proprietary лицензией недопустима.

## Reference APK

FadCam, Background Video Recorder и Timestamp Camera используются только для исследования наблюдаемого поведения, Android manifest/API architecture и совместимости lifecycle. В репозиторий нельзя переносить их proprietary bytecode, декомпилированный исходный код, UI resources, strings, icons, algorithms с творчески значимой реализацией или иные закрытые материалы.

Допустимы независимо реализованные решения на основе публичных Android API и документированных platform restrictions: foreground service types, notifications, MediaSession adapters, wake locks и lifecycle handling. Исследовательские выводы должны быть описаны как behavioral/API findings, а не как происхождение скопированного кода.

## Отдельные продукты

DarkCat CRM — отдельное приложение. Его исходный код не включён; integration ограничена публичным Intent/CaptureContext contract.

PR #1 и PR #2 нельзя merge в рамках аппаратной подготовки. Лицензионная стратегия не меняет статус PR и не даёт разрешения на публикацию release-сборки.
