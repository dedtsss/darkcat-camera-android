# Быстрая и резкая съёмка

## Product profile

В обычном DarkCat workflow camera automation остаётся технической и предсказуемой:

- Camera2 primary, Camera1 только compatibility fallback;
- AF continuous-picture;
- AE/ISO/shutter AUTO;
- AWB AUTO либо короткий понятный preset;
- scene AUTO;
- neutral EV по умолчанию;
- JPEG quality 100 для DarkCat re-encode;
- без обязательного Night/long multiframe режима;
- без полного autofocus cycle перед каждым Volume+.

Low-light policy отдаёт приоритет читаемости и уменьшению motion blur. OEM extensions/night могут оставаться optional Advanced, но не входят в default capture latency.

## Два режима

### Максимальная скорость

После успешного GPS gate shutter запускается сразу. Уже доступный ZSL/HAL path может быть использован без дополнительного ожидания; если его нет, выполняется обычный immediate Camera2 capture.

### Приоритет резкости

Если в preview ring уже есть candidate с sharpness `>=40`, движением `<0.32 rad/s` и приемлемым последним AF state, либо текущая угловая скорость ниже `0.32 rad/s`, capture immediate. При заметном движении decision engine даёт краткое окно стабилизации:

```text
delay = 100 ms + proportional excess motion up to 100 ms
hard maximum = 200 ms
```

После hard maximum камера всё равно снимает. Этот режим не превращается в autofocus wait. Если гироскоп отсутствует/нет свежего sample, безопасный fallback — immediate capture.

Максимальная дополнительная задержка DarkCat decision engine: **200 ms**.

## Continuous AF и 3A

Linked/Open Camera использует `focus_mode_continuous_picture`. `optimiseFocusForLatency()` выбирает прямой `takePhotoWhenFocused()` path вместо quality-focus path, который мог запускать отдельный AF и ждать секунды.

Tap-to-focus остаётся: upstream временно переводит continuous workflow в touch focus и затем возвращает подходящий continuous mode. Если в момент shutter уже выполняется touch AF, upstream может завершить текущую операцию; DarkCat не добавляет второй обязательный cycle.

Camera2 capture results публикуют последние:

- `CONTROL_AF_STATE`;
- `CONTROL_AE_STATE`;
- `CONTROL_AWB_STATE`.

Они используются как metadata/readiness signals. Плохой/неизвестный 3A state влияет на candidate score, но не создаёт неограниченного ожидания.

## Sharpness score

`SharpnessScorer` считает variance of a four-neighbour Laplacian на Y plane. Для ограничения стоимости берётся stride, зависящий от меньшей стороны кадра, с ориентиром около 160 samples по стороне.

При доступном camera `TextureView` runtime `BestFrameMonitor` раз в 120 ms получает `160×120` bitmap, переводит RGB в luminance и передаёт байтовую plane scorer-у. Для SurfaceView/недоступной texture он не блокирует capture и не создаёт кандидатов. Свойства:

- анализируется уменьшенная preview luminance, не full-resolution JPEG;
- flat image даёт около нуля;
- резкие edges дают большую variance;
- нет ML/model/download;
- pure implementation покрыта JVM sanity tests.

Прямой `ImageReader` YUV stream не добавлен: preview copy является advisory analysis и не может быть reprocessed в итоговый JPEG.

## Motion score

`MotionSampler` использует `TYPE_GYROSCOPE` с `SENSOR_DELAY_GAME`. Для sample `(x,y,z)`:

```text
angularSpeed = sqrt(x² + y² + z²)
stability = 1 / (1 + 4 * angularSpeed²)
```

Samples сглаживаются экспоненциально (`0.68 previous + 0.32 new`). Датчик используется callback-based; busy loop отсутствует.

## Best Frame score

Для каждого `FrameCandidate` предусмотрены timestamp, sharpness, angular speed и AF/AE/AWB states. Score:

```text
0.46 * log-sharpness
+ 0.27 * stability
+ 0.14 * temporal proximity (до 350 ms)
+ 0.08 * AF quality
+ 0.03 * AE quality
+ 0.02 * AWB quality
```

Фокус/экспозиция/баланс белого дают бонус converged/locked state; неизвестное состояние получает нейтрально-пониженный score, явно плохое — ноль. Кандидат выбирается по максимальному score, а не только ближайшему timestamp.

`BestFrameRingBuffer` хранит до 12 metadata records. Runtime выбирает кандидатов в окне `±450 ms` вокруг shutter timestamp. Ownership camera images остаётся у camera engine, что предотвращает накопление больших YUV buffers.

## ZSL и reprocessing

Существующий Camera2 still path на Android 8+ вне extension session ставит `CONTROL_ENABLE_ZSL=true`. Diagnostics отдельно экспортирует:

- `PRIVATE_REPROCESSING` capability;
- `YUV_REPROCESSING` capability;
- logical/physical camera relation;
- YUV/PRIVATE/JPEG stream configurations;
- extensions.

Fallback rules:

- нет Camera2/ZSL capability — обычный fast capture;
- extension session — upstream не навязывает ZSL request;
- flash/mode/driver несовместимы — обычный capture path должен остаться рабочим;
- отсутствие candidate никогда не блокирует shutter;
- Camera1 compatibility mode не обещает Best Frame/ZSL.

## Честная граница текущей реализации

Scoring, preview sampler, motion, metadata ring, 3A tracking, ZSL request и capability diagnostics существуют. Но в текущем vertical slice нет continuously fed YUV/PRIVATE image ring и нет reprocessing operation, заменяющей итоговый Open Camera JPEG выбранным candidate.

Поэтому Best Frame сейчас означает **engine/capability/fallback foundation**, а не завершённый automatic best-JPEG selection. Аппаратная проверка должна сначала установить, какие Pixel 7 camera IDs/configurations реально поддерживают ZSL/reprocessing без ухудшения latency и physical-lens selection.

## Pixel A/B

Нужно сравнить для main/ultrawide/front:

- shutter-to-callback latency Max Speed vs Sharp Priority;
- sharpness при коротком движении руки;
- continuous AF/tap focus recovery;
- indoor/twilight noise vs motion blur;
- ZSL on/off timestamps;
- flash/torch interaction;
- physical camera ID stability;
- дополнительная задержка не более 200 ms.

Результаты фиксируются в [PIXEL7_TEST.md](PIXEL7_TEST.md) и diagnostics export. До этого hardware success не заявляется.
