# Аудит артефакта 1.9.10

План эмодзи-подсказок, все три миссии (`docs/EMOJI-SUGGEST-PLAN.md`): курируемый
ассет «слово → эмодзи» (миссия 1, `docs/emoji-suggest/DATA.md`), движок —
эмодзи в хвостовой ячейке NEXT_WORD-полосы, append через `commitPredictedWord`,
тоггл opt-in (миссия 2, `docs/emoji-suggest/ENGINE.md`). Ветка `main`, база —
1.9.9 (`dist/tatar-keyboard-1.9.9.apk`, versionCode 25). Файл:
`dist/tatar-keyboard-1.9.10.apk`.

## Одной таблицей

| | |
|---|---|
| versionName / versionCode | **1.9.10 / 26** (было 1.9.9 / 25) |
| Размер APK | **1 792 011 Б** при потолке 3 145 728 Б, запас **43,0 %** (было 1 775 548 Б, **+16 463 Б, +0,9 %**) |
| SHA-256 APK | `101048b8bf4b95cdd80fd7e0ed46b4044e768b08e75a959b819405f37f751fd3` |
| Подпись | тот же ключ: сертификат SHA-256 `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad`, как у 1.9.4–1.9.9; CN=Tatar Keyboard. Пайплайн `scripts/release_pack.sh` (zipalign -z → apksigner v2-only), как у 1.9.9 |
| Разрешения | одно: `android.permission.VIBRATE`. INTERNET нет |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 |
| Файлов в APK | **245** (было 244; +1 — новый ассет `assets/emoji/emoji_suggest_v1.txt`) |
| JVM-тесты | **1058**, падений 0 (гейт `release_check`, `--rerun-tasks`) |
| Python-наборы `tests/` | **346** в 12 файлах, падений 0 |
| `lintRelease` | зелёный с baseline |
| no-INTERNET gate | exit 0 на релизном APK (оба уровня: манифест + aapt2) |
| Воспроизводимость | **два независимых прогона полного пайплайна** `release_pack.sh` → **одинаковый SHA-256** (`101048b8…1fd3`), `cmp` чистый |
| Ради чего версия | эмодзи-подсказки на завершённом слове (ru+tt), opt-in; «самолет » → ✈️ в хвостовой ячейке, тап дописывает эмодзи |

## Дельта по компонентам (release_check, 1.9.9 → 1.9.10)

| Компонент | 1.9.9, Б | 1.9.10, Б | Δ |
|---|---:|---:|---:|
| `assets/` | 1 416 211 | 1 503 355 | **+87 144** |
| `classes.dex` (+ `classes2.dex`) | 421 736 | 427 128 | +5 392 |
| `res/` | 431 918 | 431 918 | 0 |
| `resources.arsc` | 92 984 | 94 020 | +1 036 (строки тоггла base/ru/tt) |
| `AndroidManifest.xml` и прочее | 4 864 | 4 864 | 0 |
| **Всего (несжатое)** | 2 367 713 | 2 461 285 | +93 572 |
| **APK (сжатый)** | 1 775 548 | **1 792 011** | **+16 463** |

Пофайловая сверка внутри APK (diff листингов `unzip -l`, несжатые размеры):

| Файл | 1.9.9, Б | 1.9.10, Б | Δ |
|---|---:|---:|---:|
| `assets/emoji/emoji_suggest_v1.txt` | — | 86 114 | **+86 114 (новый)** |
| `assets/emoji/NOTICE.txt` | 4 858 | 5 862 | +1 004 |
| `assets/dexopt/baseline.prof(m)` | 1 153 | 1 179 | +26 |
| `classes.dex` / `classes2.dex` | 322 348 / 99 388 | 322 016 / 105 112 | +5 392 суммарно (код миссии 2) |
| оба словаря, обе таблицы биграмм | — | — | **байт-в-байт прежние** |

## Сверка пинов ассетов

Все 16 значений (размер + SHA-256, сжатый и raw, ×4 ассета словарей/биграмм)
совпали с константами `DictionaryStorageContracts.kt` / `BigramStorageContracts.kt`
— гейт `artifact.asset_pins` в `scripts/release_check.sh`. Эти четыре ассета
**не менялись** — пины те же, что в аудите 1.9.9.

Новый ассет `assets/emoji/emoji_suggest_v1.txt` (86 114 Б, SHA-256
`aef39f2f833e941fd46f04f3ce31ebc35b3fbea505abb51e3086a201814a2744`) гейтом
пинов `release_check.sh` **не покрыт**: тот читает только
`*StorageContracts.kt`, а эмодзи-ассеты читаются из APK напрямую и
контрактов хранилища не имеют. Его пины вместо этого охраняют:
python-набор `tests/emoji_suggest_pack/` (CommittedAssetTest: SHA-256 + числа
записей) и JVM-контракт `EmojiSuggestAssetTest.kt` — оба прогоняются гейтами
`gates.python_tests` / `gates.gradle_test` того же `release_check.sh` и зелёные.
Покрытие не слепое, но разнесённое; свести emoji_suggest в гейт пинов
`release_check.sh` — возможное отдельное улучшение (DEV-план).

## Подпись и разрешения

```
$ apksigner verify --print-certs dist/tatar-keyboard-1.9.10.apk
V2 Signer: certificate DN: CN=Tatar Keyboard
V2 Signer: certificate SHA-256 digest: 98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad
```

Тот же сертификат `98ca6feb…42ad`, что у 1.9.4–1.9.9 и всей линейки с 2026-08-18.
Схемы — v2-only, `zipalign -c 4` на подписанном артефакте проходит (шаги 3 и 5
`release_pack.sh`).

```
$ aapt2 dump permissions dist/tatar-keyboard-1.9.10.apk
uses-permission: name='android.permission.VIBRATE'
```

## Смоук

`scripts/emulator-smoke.sh --no-boot --avd tt_suggest_a14 --apk
dist/tatar-keyboard-1.9.10.apk`: **15 PASS / 0 FAIL / 4 SKIP** — тот же профиль,
что у 1.9.6–1.9.9 (skip'ы by design: подсказки opt-in нечитаемы на
не-debuggable, у en нет словаря). Свидетельства — `build/emulator-smoke-1.9.10/`.

**Обновление на устройстве (живой прогон 1.9.9 → 1.9.10, тот же AVD
tt_suggest_a14, userdebug):** `install -r` прошёл, `firstInstallTime` не
изменился (**2026-09-01 12:34:34**, versionCode 25 → 26). Четыре пиненых ассета
**не переинфлировались** — имена файлов в device-protected storage до и после
обновления идентичны (словари `…-s2-f1-922d14f2…0130.tdict` /
`…-s2-f1-f05499a3…15b2.tdict`, таблицы `…-s3-f1-93789e53…b1c0.tatbigr` /
`…-s3-f1-20a22848…b667.tatbigr`, плюс по прежним двум файлам ретенции s1/s2).
Новый эмодзи-ассет никуда не инфлируется — читается прямо из APK лениво при
включённом тоггле.

**Фича на обновлённом релизе** (тоггл `pref_emoji_suggestions=true` записан
через `adb root`, мастер-подсказки включены, ru-раскладка; снимки
`docs/emoji-suggest/evidence/`):

| Ввод | Полоса | Тап по хвосту |
|---|---|---|
| ru `самолет ` (повторный набор — первый NEXT_WORD-запрос сессии пуст, известная гонка) | **с · в · ✈️** (`release-02-samolet-band.png`) | поле «самолет самолет ✈️ » — слова целы, эмодзи + автопробел (`release-03-samolet-committed.png`) |

Контроль регрессии полосы на том же прогоне: ru `я в ` → **этом · том ·
результате** — пословно совпадает с живой сверкой 1.9.9 и содержимым schema-3
таблицы. Полоса с выключенным тогглом (`pref_emoji_suggestions=false`,
force-stop, перенабор) — пустая/словесная без эмодзи, как до фичи.

Параллельный контроль на debug-сборке того же дерева (тот же AVD): «самолет » →
[с · в · ✈️], идентично свидетельствам миссии 2.

Замеченное на прогоне (не относящееся к фиче, воспроизводится и на 1.9.9):
первый NEXT_WORD-запрос сессии/свежего поля иногда отвечает пусто — гонка
двухступенчатого attach биграммной таблицы (E5c, зафиксирована в ENGINE.md и
оставлена открытым хвостом). Повторный набор слова в той же сессии показывает
полосу полностью.

## Сборка и воспроизводимость

Артефакт собран пайплайном `scripts/release_pack.sh`: `./gradlew clean
assembleRelease -PskipReleaseSigning` → `zipalign -f -z 4` → `zipalign -c 4` →
`apksigner sign` (ключи из `keystore.properties`, v2-only) → `apksigner verify`.
Два независимых прогона полного пайплайна дали побайтно одинаковый APK:
SHA-256 `101048b8…1fd3`, размер 1 792 011 Б (`cmp` без различий). Оговорка про
пин версии build-tools из аудита 1.9.9 в силе. В релиз вкладывать файл из
`dist/`.

## Чего в этом аудите нет

* **PSS и холодный старт не мерены** — замер на железе остаётся долгом живого
  устройства (как в 1.9.9).
* **Живого телефона нет** — все числа эмуляторные.
* **В стор ничего не ушло.** Ни `git push`, ни тега наружу, ни релиза:
  артефакт лежит только в `dist/`.
