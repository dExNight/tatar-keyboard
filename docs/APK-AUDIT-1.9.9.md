# Аудит артефакта 1.9.9

Финал кампании ужатия APK (миссии SIZE-1/2/3): словари на TATDICT schema 2,
таблицы биграмм на TATBIGR schema 3, zopfli-рекомпрессия упаковки. Ветка `main`,
база — 1.9.8 (`dist/tatar-keyboard-1.9.8.apk`, versionCode 24). Файл:
`dist/tatar-keyboard-1.9.9.apk`. Отчёты миссий — `docs/SIZE-SCHEMA2.md`,
`docs/SIZE-SCHEMA3.md`, итог кампании — `docs/SIZE-CAMPAIGN.md`.

## Одной таблицей

| | |
|---|---|
| versionName / versionCode | **1.9.9 / 25** (было 1.9.8 / 24) |
| Размер APK | **1 775 548 Б** при потолке 3 145 728 Б, запас **43,6 %** (было 2 095 592 Б, **−320 044 Б, −15,3 %**) |
| SHA-256 APK | `d08fac8aa84825caffbe76703c205f4fda9f12f1ba3f6b008c9fbd1274f2efcf` |
| Подпись | тот же ключ: сертификат SHA-256 `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad`, как у 1.9.4–1.9.8; CN=Tatar Keyboard. Подпись выполнена **apksigner v2-only после zipalign -z** (новый пайплайн `scripts/release_pack.sh`), не AGP |
| Разрешения | одно: `android.permission.VIBRATE`. INTERNET нет |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 |
| Файлов в APK | **244** (как в 1.9.8) |
| JVM-тесты | **1013**, падений 0 (`--rerun-tasks`) |
| Python-наборы `tests/` | **312** в 11 файлах, падений 0 |
| `lintRelease` | зелёный с baseline |
| no-INTERNET gate | exit 0 на исходнике (debug) и на релизном APK |
| Воспроизводимость | **три независимых прогона полного пайплайна** (gradle unsigned → zipalign -z → apksigner) → **одинаковый SHA-256** (`d08fac8a…efcf`), `cmp` чистый |
| Ради чего версия | ужатие без потерь: −15,3 % APK, идентичность выдачи доказана полными сверками (76 839 префиксов, 20 202 головы / 80 683 пары) |

## Дельта по компонентам (release_check, 1.9.8 → 1.9.9)

| Компонент | 1.9.8, Б | 1.9.9, Б | Δ |
|---|---:|---:|---:|
| `assets/` | 1 795 998 | 1 416 211 | **−379 787** |
| `classes.dex` | 418 812 | 421 736 | +2 924 (регенерированный baseline-профиль влияет на dex layout под startup-профиль) |
| `res/` | 431 918 | 431 918 | 0 |
| `resources.arsc` | 92 984 | 92 984 | 0 |
| `AndroidManifest.xml` и прочее | 4 864 | 4 864 | 0 |
| **Всего (несжатое)** | 2 744 576 | 2 367 713 | −376 863 |
| **APK (сжатый)** | 2 095 592 | **1 775 548** | **−320 044** |

Внутри `assets/` изменились ровно четыре пиненых ассета (пофайловая сверка):

| Ассет | 1.9.8 | 1.9.9 | Δ |
|---|---:|---:|---:|
| `dictionaries/tatar_top100k_v1.tdict.zlib` | 601 118 | 501 683 | −99 435 |
| `dictionaries/russian_top100k_v1.tdict.zlib` | 638 758 | 539 948 | −98 810 |
| `bigrams/tatar_bigrams_v1.tatbigr.zlib` | 176 749 | 81 028 | −95 721 |
| `bigrams/russian_bigrams_v1.tatbigr.zlib` | 149 118 | 63 312 | −85 806 |

Упаковочный уровень (zopfli, SIZE-3): unsigned APK 1 790 772 → zipalign -z
1 768 116 (**−22 856 Б**) → подпись v2 1 775 548 Б.

## Сверка пинов ассетов

Все 16 значений (размер + SHA-256, сжатый и raw, ×4 ассета) совпали с константами
`DictionaryStorageContracts.kt` / `BigramStorageContracts.kt` — гейт
`artifact.asset_pins` в `scripts/release_check.sh`. Все четыре ассета
перезакреплены кампанией:

| Ассет | Сжатый размер / SHA-256 | Raw размер / SHA-256 |
|---|---|---|
| `dictionaries/tatar_top100k_v1.tdict.zlib` | 501 683 / `cb34fe7d…8119` | 1 162 870 / `922d14f2…0130` |
| `dictionaries/russian_top100k_v1.tdict.zlib` | 539 948 / `273f1a69…5fde` | 1 151 323 / `f05499a3…15b2` |
| `bigrams/tatar_bigrams_v1.tatbigr.zlib` | 81 028 / `b1b92914…087f` | 134 664 / `93789e53…b1c0` |
| `bigrams/russian_bigrams_v1.tatbigr.zlib` | 63 312 / `aa25f629…e817` | 131 662 / `20a22848…b667` |

## Подпись и разрешения

```
$ apksigner verify --print-certs dist/tatar-keyboard-1.9.9.apk
V2 Signer: certificate DN: CN=Tatar Keyboard
V2 Signer: certificate SHA-256 digest: 98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad
```

Тот же сертификат `98ca6feb…42ad`, что у 1.9.4–1.9.8 и всей линейки с 2026-08-18.
Схемы — v2-only (`--v1-signing-enabled false --v3-signing-enabled false`), как у
AGP-сборок линейки; `zipalign -c 4` на подписанном артефакте проходит.

```
$ aapt2 dump permissions dist/tatar-keyboard-1.9.9.apk
uses-permission: name='android.permission.VIBRATE'
```

## Смоук

`scripts/emulator-smoke.sh --no-boot --avd tt_suggest_a14`:

- release (`dist/tatar-keyboard-1.9.9.apk`): **15 PASS / 0 FAIL / 4 SKIP** — тот
  же профиль, что у 1.9.6–1.9.8 (skip'ы by design: подсказки opt-in нечитаемы на
  не-debuggable, у en нет словаря). Свидетельства — `build/emulator-smoke-1.9.9/`.
- debug: **18 PASS / 0 FAIL / 1 SKIP** (en by design), полоса подсказок оживает
  на обеих словарных раскладках. Свидетельства — `build/emulator-smoke-1.9.9-debug/`.

Полоса предсказаний на живом обновлении (prefs через `adb root`, userdebug-образ),
снимки — `docs/size-campaign/evidence/`:

| Ввод | Полоса | Сверка с ассетом |
|---|---|---|
| tt `ул кил ` | **дә · әле · һәм** | `кил` → дә·әле·һәм ✓ |
| ru `я в ` | **этом · том · результате** | `в` (контекст «я») → этом·том·результате ✓ |

Обе тройки совпадают с содержимым schema-3 таблиц и со снимками 1.9.8/SIZE-2 —
выдача после смены форматов не сдвинулась.

**Обновление на устройстве (живой прогон 1.9.8 → 1.9.9, тот же AVD):**
`install -r` прошёл, `firstInstallTime` не изменился (2026-09-01 12:34:34,
versionCode 24 → 25). Имена файлов в device-protected storage несут schema-id и
raw SHA-256 — все четыре ассета переинфлировались ровно один раз при первой
активации своей раскладки:

- `dictionaries/`: рядом со старым `…-s1-f1-8f434ec7…5f76.tdict` появился
  `…-s2-f1-922d14f2…0130.tdict`;
- `dictionaries-ru/`: рядом с `…-s1-f1-60b30371…faee.tdict` появился
  `…-s2-f1-f05499a3…15b2.tdict`;
- `bigrams/`: рядом с `…-s2-f1-6db06331…c966.tatbigr` появился
  `…-s3-f1-93789e53…b1c0.tatbigr`;
- `bigrams-ru/`: рядом с `…-s2-f1-5e6c3e01…f15e.tatbigr` появился
  `…-s3-f1-20a22848…b667.tatbigr`.

Старые файлы остаются второй копией (ретенция `MAX_FINAL_ARTIFACTS` = 2 — штатный
путь, как в аудите 1.9.8). Crash-буфер после прогона пуст.

## Сборка и воспроизводимость

Артефакт собран новым пайплайном `scripts/release_pack.sh`:
`./gradlew clean assembleRelease -PskipReleaseSigning` → `zipalign -f -z 4` →
`zipalign -c 4` → `apksigner sign` (ключи из `keystore.properties`, v2-only) →
`apksigner verify`. Три независимых прогона (два скриптом + один вручную по шагам)
дали побайтно одинаковый APK: SHA-256 `d08fac8a…efcf`, размер 1 775 548 Б
(`cmp` без различий). Детерминизм zopfli обеспечен пином версии build-tools
(берётся старшая установленная; при смене build-tools байты могут измениться —
это та же оговорка, что у AGP-версии в DEV-2). CI-гейт `reproducible` (два
unsigned `clean assembleRelease`) не тронут и продолжает работать. В релиз
вкладывать файл из `dist/`.

## Чего в этом аудите нет

* **PSS и холодный старт не мерены** — latency читателей измерена JVM-тестами
  (в бюджетах с запасом два порядка, см. `SIZE-SCHEMA2/3.md`), но замер на
  железе остаётся долгом живого устройства.
* **Живого телефона нет** — все числа эмуляторные.
* **В стор ничего не ушло.** Ни `git push`, ни тега наружу, ни релиза:
  артефакт лежит только в `dist/`.
