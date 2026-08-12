# DarkCat Camera 0.4 — ручная hardware-проверка

Статус документа: **NOT RUN**. Этот checklist не является подтверждением совместимости. Поля `PASS` заполняются только после реального прогона на устройстве.

## Паспорт прогона

| Поле | Значение |
| --- | --- |
| Устройство | Google Pixel 7 / GrapheneOS **или** Xiaomi 12 Lite / Android 14 HyperOS 1 |
| OEM/build |  |
| Android version / API |  |
| Security patch |  |
| DarkCat version | `0.4.0-field` |
| APK SHA-256 |  |
| Commit |  |
| Debug/release signature |  |
| Bluetooth remote model |  |
| Nextcloud/WebDAV server/version |  |
| Тестировщик |  |
| Дата/время |  |

Использовать отдельные тестовые credentials/server path и несекретные tags. Не прикладывать к issue/PR media, координаты или credentials.

## Подготовка

1. Сохранить APK SHA-256 и установить поверх предыдущей debug-сборки той же подписи.
2. Проверить в Android Settings, что CAMERA, FINE LOCATION и notifications выданы; BACKGROUND LOCATION выдавать только для соответствующего сценария.
3. Не снимать PIN/biometric и не ослаблять системный lockscreen.
4. Включить Camera/Microphone privacy access; затем отдельно проверить ожидаемый отказ при выключенном Camera access.
5. Запустить «Экспорт диагностики камеры» до теста и сохранить JSON.
6. При наличии ADB записать sanitized logcat, не публикуя чувствительные значения.

Для каждого пункта отмечать `PASS`, `FAIL`, `PARTIAL` или `N/A`, фактическое время/latency и короткую заметку. Crash/ANR требует stack trace и точных действий воспроизведения.

## Обязательный checklist (41 пункт)

1. **Запуск.** Статус: `____`. Cold start проходит без crash/ANR; preview появляется, основной DarkCat UI читаем и не налезает на upstream controls.

2. **Camera2 реально используется.** Статус: `____`. В diagnostics/log подтверждён Camera2, а не Camera1 compatibility fallback; записать selected logical/physical ID и hardware level.

3. **Main camera.** Статус: `____`. `1×` открывает основную заднюю камеру, preview и JPEG имеют ожидаемое поле зрения/orientation.

4. **Ultrawide.** Статус: `____`. `0.5×` действительно меняет physical camera/field of view, а не только digital zoom; записать physical ID и focal length.

5. **Front.** Статус: `____`. Фронтальная камера открывается, сохраняет правильную orientation/mirroring policy и возвращается на rear.

6. **Camera switching speed.** Статус: `____`. Измерить `1× -> 0.5× -> Front -> 1×`, отметить black-frame/freeze/cold reopen и latency каждого перехода.

7. **Continuous AF.** Статус: `____`. Перевести камеру между близким и дальним объектом без shutter; preview самостоятельно перефокусируется, AF states видны в диагностике/log.

8. **Tap focus.** Статус: `____`. Tap меняет точку фокуса; после завершения камера возвращается в пригодный continuous-picture workflow и следующий кадр не ждёт секунды.

9. **Max Speed capture.** Статус: `____`. При valid GPS shutter запускается сразу; измерить press-to-camera-callback/haptic, повторить серией не менее 10 кадров.

10. **Sharp Priority capture.** Статус: `____`. При неподвижном телефоне immediate; при движении дополнительное ожидание не превышает 200 ms и не запускает многосекундный AF.

11. **Slight hand movement.** Статус: `____`. Снять читаемый мелкий текст с короткой остановкой руки; сравнить Max Speed/Sharp Priority и отметить резкость/motion blur.

12. **Indoor.** Статус: `____`. Проверить экспозицию, AF, noise, shutter latency и читаемость технических деталей при обычном искусственном освещении.

13. **Twilight.** Статус: `____`. Проверить, что default не уходит в многосекундный Night capture; сравнить noise с motion blur и сохранить A/B заметки без публикации чувствительного media.

14. **Flash.** Статус: `____`. Проверить Auto/On/Off/Torch на main camera; отсутствие freeze, корректный fallback при несовместимом ZSL/physical lens.

15. **GPS acquisition.** Статус: `____`. На открытом небе включить GPS Locker, измерить время до первого fix и до GREEN `<=7 m`; затем проверить повторный warm acquisition.

16. **Accuracy live updates.** Статус: `____`. Значение рядом с GPS меняется по фактическим fixes (`±N м`), notification обновляется и не показывает фиксированную заглушку.

17. **Strict 7 m capture blocking.** Статус: `____`. При отсутствии fix, stale fix и accuracy `>7 m` shutter не вызывает camera capture, sequence не растёт, идёт fail haptic; при `<=7 m` capture разрешён.

18. **GREEN/YELLOW/RED states.** Статус: `____`. Проверить GREEN для fresh/точного fix, YELLOW для поиска/poor accuracy/aging, RED для disabled/denied/stale; старый fix не остаётся GREEN.

19. **Sequence index.** Статус: `____`. Задать произвольный номер, сделать успешный photo, проверить increment сразу после camera callback; rejected/failed capture не меняет номер; restart сохраняет next value.

20. **Tags.** Статус: `____`. Создать текст/несколько слов/символ/emoji, выбрать несколько, toggle повторным tap, очистить; active set остаётся для следующих кадров и переживает restart.

21. **Stamp.** Статус: `____`. Проверить black technical block, выбранные coordinates/accuracy/sequence/tags/custom text, белый читаемый текст и JPEG quality 100. Сопоставить с fix в момент shutter и убедиться, что более поздний GPS update до post-processing не меняет координаты уже снятого кадра.

22. **Crosshair preview.** Статус: `____`. PREVIEW виден в геометрическом центре итогового crop, меняет Белый/Зелёный/Красный, размер/толщину и не попадает в JPEG.

23. **Crosshair stamp.** Статус: `____`. STAMP виден и в preview, и в final JPEG, совпадает с геометрическим центром; orientation/crop не смещают его.

24. **FAST workflow.** Статус: `____`. После camera callback сразу success haptic/готовность следующего кадра; recovery, stamp, encryption, DB и upload идут независимо.

25. **EDIT workflow.** Статус: `____`. После callback сразу success haptic, recovery уже durable, открывается editor, Save создаёт vault item; закрытие/ошибка editor не теряет original recovery.

26. **Object editor.** Статус: `____`. Проверить crop, drawing, line, rectangle, ellipse, arrow, text, undo/redo; старую shape/text можно переизбрать, move/scale/rotate/recolor/restroke/delete. В текущем известном partial editor этот acceptance criterion ожидаемо не выполнен — фиксировать точные отсутствующие операции.

27. **Secure Vault.** Статус: `____`. Secure Mode не оставляет plaintext в DCIM/MediaStore; Vault/thumbnail открываются после unlock, screenshot/recents скрыты, повреждённый encrypted file не decryptится.

28. **Screen OFF.** Статус: `____`. Включить Field Mode из видимой Activity, нажать power, подождать 1/5/15 min; notification остаётся, нет crash, камера/GPS состояние записать.

29. **Real Android lock.** Статус: `____`. Устройство действительно требует PIN/biometric; DarkCat не рисует поверх lockscreen, не показывает Vault/thumbnails/tags/coordinates и не обходит unlock.

30. **Bluetooth Volume+ while locked.** Статус: `____`. Сопрячь remote, включить Field Mode, реально заблокировать телефон, нажать Volume+; проверить camera callback, ровно один кадр/sequence и success haptic. Этот путь до теста считается неподтверждённым.

31. **Short success vibration.** Статус: `____`. После фактического camera success чувствуется один короткий усиленный импульс, до завершения encryption/DB; не дублируется в burst/single shot.

32. **Long GPS-fail vibration.** Статус: `____`. При strict GPS rejection ощущается заметно более длинный двухимпульсный pattern; он отличается от success и не раздражающе длинный.

33. **Lock/unlock without camera cold restart.** Статус: `____`. Повторить lock/unlock 10 раз и проверить service-owned session/camera ID, visible preview handoff и capture latency. Если ОС закрывает service session, измерить reopen и отметить `PARTIAL/FAIL`, не скрывать cold restart.

34. **GPS remains warm while locked.** Статус: `____`. До lock получить GREEN, под lock подождать и переместиться на открытом месте; после unlock accuracy/age показывают непрерывные updates, а не старый cached fix/cold start.

35. **Offline captures.** Статус: `____`. Airplane mode без Wi-Fi: серия фото/EDIT сохраняется в recovery/vault, capture не блокируется сетью, очередь имеет правильный count.

36. **Sync after network appears.** Статус: `____`. После offline серии вернуть разрешённую сеть; QUEUED переходит UPLOADING -> UPLOADED/VERIFIED без участия camera path.

37. **Manual Sync Now.** Статус: `____`. «Отправить сейчас» ставит допустимые записи в worker; при Wi-Fi-only и metered network требуется явное подтверждение, policy не обходится молча.

38. **WebDAV/Nextcloud.** Статус: `____`. Сохранить/reopen credentials без Keystore crash; проверить Public Share и Generic WebDAV, точный remote encrypted size, UPLOADED при HEAD без length и VERIFIED только при достаточной проверке.

39. **Restart app.** Статус: `____`. Force-stop/обычный restart сохраняет settings, tags, next sequence, vault и queue; camera/permissions восстанавливаются без crash. Camera FGS не должен притворяться восстановленным после process death.

40. **Recovery pending.** Статус: `____`. На тестовом кадре остановить процесс после success haptic/до vault commit; restart обнаруживает sidecar/media, не TTL-delete original и возобновляет non-EDIT processing либо показывает unresolved EDIT.

41. **Diagnostics export.** Статус: `____`. JSON создаётся и содержит device/Android, all camera IDs, hardware level, physical IDs, focal lengths, AF/OIS, reprocessing/ZSL, streams/extensions, selected camera, service/GPS/volume state; media и точных координат нет.

## Дополнительные доказательства

Для каждого camera ID записать:

| UI lens | Logical ID | Physical ID | Focal length | Sensor size | HW level | AF | OIS | ZSL request | PRIVATE/YUV reprocess | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1× |  |  |  |  |  |  |  |  |  |  |
| 0.5× |  |  |  |  |  |  |  |  |  |  |
| Front |  |  |  |  |  |  |  |  |  |  |

Latency table:

| Scenario | Samples | Median press→callback | P95 | Max | Notes |
| --- | ---: | ---: | ---: | ---: | --- |
| Max Speed, screen on |  |  |  |  |  |
| Sharp Priority, still |  |  |  |  |  |
| Sharp Priority, moving |  |  |  |  |  |
| Field Mode, locked BT remote |  |  |  |  |  |

## Завершение прогона

- Экспортировать diagnostics ещё раз после теста.
- Убедиться, что Field Mode/GPS Locker можно остановить из notification/app.
- Проверить отсутствие чувствительных данных в public notifications/log/export.
- Записать known limitations и приложить sanitized diagnostics/logs к PR #2.
- Оставить PR #2 OPEN/DRAFT; успешный checklist сам по себе не является разрешением на merge.

## Второй обязательный профиль: Xiaomi 12 Lite / Android 14 HyperOS 1

Повторить пункты 1–41 на Xiaomi 12 Lite с Android 14 / HyperOS 1. Отдельно зафиксировать:

1. Разрешение CAMERA/location/notifications и поведение HyperOS autostart/battery policy для обоих foreground services.
2. Переживание screen off и настоящей блокировки; не считать открытый экран или только выключенный дисплей эквивалентом lockscreen.
3. Физический Bluetooth Volume+ / camera shutter routing и отсутствие duplicate capture.
4. Reopen после camera disconnect, выбранные Camera2 IDs, logical/physical lenses и фактический JPEG resolution.
5. Green/yellow/red GPS transitions, strict `7 m` gate, success/fail haptics и отсутствие потери кадров в серии.

Если OEM policy требует вручную отключить battery optimization/autostart, записать это как prerequisite и не переносить результат на обычную пользовательскую конфигурацию.
