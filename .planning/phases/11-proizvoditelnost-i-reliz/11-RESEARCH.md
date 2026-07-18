# Phase 11: Производительность и релиз — Research

**Researched:** 2026-07-19
**Method:** чтение исходников форка + **живой эксперимент**: shrinkResources=true собран, APK продампен aapt2/dexdump, эксперимент откачен (рабочее дерево чистое)

## Вердикт-конденсат

| Вопрос | Вердикт |
|---|---|
| R1 shrinkResources | **ВКЛЮЧАТЬ МОЖНО** — но строго вместе с `res/raw/keep.xml` (проверено сборкой: раскладки выживают, а `locale_name_*`-строки БЕЗ keep стрипаются — реальный баг-кандидат) |
| minifyEnabled | Уже `true` (app/build.gradle:33); все нужные классы выживают в release-dex — проверено dexdump'ом, **новых proguard-правил не нужно** |
| Ребрендинг | 2 файла: `strings-appname.xml` (снять «(dev)») + `strings.xml:126` (`setup_message` — мёртвая строка, удалить или переписать) |
| Keystore | **Уже существует и подписывает**: release.jks (RSA-4096, alias `tatarkeyboard`, до 2056) + keystore.properties, оба gitignored; release-APK подписан (apksigner verify OK, CN=Tatar Keyboard) |
| CI | `.github/workflows/ci.yml` уже есть и вызывает check-no-internet.sh дважды; **нет** только шага размера APK — добавить 3 строки |
| PERF-01 | Release APK **730 783 байта** (с shrink+keep — 681 078) при бюджете 3 145 728 — запас 4.3× |

---

## 1. R1: shrinkResources vs getIdentifier — ЭКСПЕРИМЕНТ ПРОВЕДЁН

### Текущее состояние

- `shrinkResources` в app/build.gradle **отсутствует** (= false по умолчанию) — подтверждено grep'ом.
- `keep.xml` нигде нет (`res/raw/` не существует), в proguard-rules.pro keep'ов ресурсов нет (это и невозможно — resource shrinker управляется только keep.xml).

### Все 4 call-site getIdentifier (полный аудит)

| # | Файл:строка | Что резолвит | Паттерн имени |
|---|---|---|---|
| 1 | `KeyboardLayoutSet.java:275` | XML раскладки | `keyboard_layout_set_<name>` (префикс :62) |
| 2 | `LocaleResourceUtils.java:77` | имя локали в root | `string/locale_name_in_root_locale_<locale>` |
| 3 | `LocaleResourceUtils.java:85` | имя локали | `string/locale_name_<locale>` |
| 4 | `KeyboardTextsSet.java:134` | `!string/`-ссылки из XML раскладок | `label_pause_key`, `label_wait_key` (только 2 имени во всём res/xml — проверено grep'ом) |

### Результат живой сборки с shrinkResources=true (БЕЗ keep.xml)

Сборка зелёная, APK 663 874 байта. Дамп aapt2:

- ✅ **Раскладки ВЫЖИЛИ**: `xml/keyboard_layout_set_tatar` (0x7f100074), `_russian`, `_qwerty`, `_east_slavic` + все `kbd_tatar`/`rowkeys_tatar1..3`/`rowkeys_tatar_extra`/`rows_tatar` + 153 rowkeys-ресурса на месте. Причина: AGP-шрикер видит вызовы `getIdentifier` в коде и переходит в safe mode для xml (плюс layout set'ы тянут kbd_* прямыми `@xml/`-ссылками).
- ✅ `label_pause_key`/`label_wait_key` выжили (2/2).
- ❌ **`locale_name_*`-строки СТРИПНУТЫ** (0 вхождений в дампе; в базе их 8: en_GB, en_US, es_US, hi_ZZ, sr_ZZ, **tt_RU «Татарча»** + 2 in_root_locale). Их резолвит только `getIdentifier` (call-sites 2–3), прямых ссылок нет.

**Последствие стрипа**: `LocaleResourceUtils.initLocked` (:68-88) кладёт в мапы `resId=0` для всех 6 локалей из `locale_exception_keys` (в т.ч. tt_RU), `getLocaleDisplayNameInternal` (:167-168) делает `sResources.getString(0)` → **Resources.NotFoundException, краш** при отрисовке имени языка (спейсбар «Татарча», список Languages в настройках). То есть враг не раскладки (интуиция R1), а строки имён локалей.

### Результат с keep.xml (проверено второй сборкой)

`app/src/main/res/raw/keep.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools"
    tools:keep="@xml/keyboard_layout_set_*,@xml/kbd_*,@xml/rows_*,@xml/rowkeys_*,@xml/row_*,@string/locale_name_*,@string/label_*" />
```

APK 681 078 байт; в дампе: `locale_name` = 8 ✓, layout set'ы = 49 ✓, tatar-ресурсы = 12 ✓, label_pause/wait = 2 ✓.

### Рекомендация

**Включить `shrinkResources true` + этот keep.xml** в v1.0 (закрывает I1 обзора фазы 10). Обоснование:

- Риск снят экспериментально, а не теоретически: точный список стрипаемого известен, keep-маски покрывают все 4 call-site getIdentifier с запасом (xml-маски избыточны при safe mode — оставить для страховки от смены поведения AGP).
- Выигрыш ~50 КБ сейчас — небольшой, но shrinker станет страховкой от будущего мусора ресурсов (35 locale-строковых файлов Simple Keyboard остаются в репо).
- В план обязательно: **пост-сборочный гейт** `aapt2 dump resources | grep -c locale_name` ≥ 8 и `grep keyboard_layout_set_tatar` — fail-capable проверка, что keep работает.

Примечание: эксперимент откачен (`git status` чист); в фазе накатить заново как боевой код.

## 2. minifyEnabled / proguard

- `minifyEnabled true` уже стоит — app/build.gradle:33 (+ `proguard-android-optimize.txt` :34). Наследие базы форка, работает с фазы 1.
- proguard-rules.pro:19-22 держит `R`-класс + 3 фрагмента (SettingsFragment, LanguagesSettingsFragment, SingleLanguageSettingsFragment).
- **Дамп release-dex (dexdump)**: выживают все 9 фрагментов settings/, `LatinIME`, `SetupActivity` — включая 4 фрагмента БЕЗ явных правил (AppearanceSettings, KeyPress, Preferences, ThemeSettings). Причина: AGP генерирует keep-правила из `android:fragment=` в prefs.xml/prefs_screen_appearance.xml и из манифеста (LatinIME — сервис, SetupActivity — activity).
- Kotlin-файлы: `SetupActivity.kt` (манифест-keep), `KeyboardAccessibilityDelegate.kt`/`KeyDescriptionMapper.kt` (прямые ссылки из MainKeyboardView.java — R8 не тронет), `KotlinInteropCheck.kt` (вызов из LatinIME.java:255).

**Вердикт: правил добавлять не нужно, ничего не менять.** Существующие 3 fragment-правила избыточны, но безвредны — не трогать (минимальный дифф).

## 3. Ребрендинг app name — минимальный скоуп v1.0

Текущее состояние:

- Манифест: все 4 label (application:25, service:30, SetupActivity:45, SettingsActivity:56) → `@string/english_ime_name` — единая точка, манифест **менять не нужно**.
- `values/strings-appname.xml:22`: `english_ime_name = "Tatar Keyboard (dev)"` → **убрать «(dev)»** → `Tatar Keyboard`. Там же `english_ime_settings = "Tatar Keyboard Settings"` — уже ок (используется как key в prefs.xml:18, не как видимый label). Там же `privacy_policy_url`/`license_url` — placeholder'ы `.invalid`: **заменить на реальные GitHub-URL** при известном имени репо (или оставить до публикации — решить в плане; рекомендация: прописать целевой `https://github.com/<owner>/tatar-keyboard/blob/main/PRIVACY.md` сразу, репо всё равно фиксируется в PUBLISH-CHECKLIST).
- `values/strings.xml:126`: `setup_message` со старым брендом «Simple Keyboard» — **мёртвая строка** (0 вхождений в java/kt после фазы 10 — проверено grep'ом). Минимальный фикс: **удалить из values/strings.xml** (35 локализованных копий в values-*/ можно не трогать — без базовой строки они станут orphan'ами, aapt их игнорирует/lint ворчит; полная чистка 35 файлов — backlog post-v1.0 как и записано в STATE).
- Прочие «Simple Keyboard» в базовых values/ — только эта одна строка (grep подтвердил); остальные 34 файла — локализации того же setup_message.
- `strings-setup.xml` — бренд через `@string/english_ime_name`, правок не требует.
- `versionName "0.1.0"` / `versionCode 1` (build.gradle:15-16) → для релиза поднять до **"1.0.0" / versionCode 1** (первый релиз, code можно оставить 1).
- ic_launcher — наследие форка, замена = backlog (решение фазы 10, open question в STATE). В v1.0 не трогаем.

## 4. Keystore / подпись — ГОТОВО, работ нет

- build.gradle:3-7 читает `keystore.properties` из корня; signingConfigs.release :18-27 условный; release buildType подхватывает :35-37. Секреты в git не попадают (Pattern 3 фазы 1).
- **Оба файла существуют** (keystore.properties 140 B, release.jks 4262 B, права 600), `git check-ignore` подтверждает оба (.gitignore:47-48), в истории git их нет.
- Keystore: alias `tatarkeyboard`, **RSA-4096**, SHA384withRSA, CN=Tatar Keyboard, валиден 2026-07-18 → **2056-07-10** (30 лет — сверх требования «25 лет» контекста; контекст говорил RSA-2048/SHA-256, фактически лучше — 4096/SHA-384, менять не нужно).
- Текущий `app-release.apk` **уже подписан этим ключом**: apksigner verify --print-certs OK (SHA-256 cdd8c535…).

**Вердикт: генерировать ничего не нужно; задача фазы — только подтверждающие гейты (apksigner verify) + напоминание о бэкапе jks в PUBLISH-CHECKLIST (blocker фазы 1 в STATE).**

## 5. CI no-internet — почти готово

`.github/workflows/ci.yml` существует (фаза 1) и уже делает: fast-fail check-no-internet по манифесту → setup-java 17 + gradle → восстановление gradle-wrapper.jar (pin sha256) → assembleDebug → check-no-internet по собранному APK (aapt2, level 2) → upload-artifact. `permissions: contents: read`, мажорные теги actions.

Чего нет:

1. **Гейт размера APK (PERF-01)** — добавить шаг (~3 строки): `test $(stat -c%s app.apk) -le 3145728`. Debug-APK 2.38 МБ (без minify) — в бюджет влезает, но честнее мерить release: вариант — добавить `assembleRelease` (без keystore.properties в CI он даст **unsigned** APK — build.gradle это допускает by design) и мерить его.
2. CI ни разу не гонялся — GitHub-репо не создано (STATE, blocker фазы 1). Прогон = ручная публикация (PUBLISH-CHECKLIST).

**Вердикт: не писать новый workflow — дописать в существующий ci.yml шаг assembleRelease (unsigned) + size-гейт + при включённом shrinkResources гейт `aapt2 … | grep locale_name`.**

## 6. PERF-01 размер APK — факт

| Сборка | Байт | Бюджет |
|---|---|---|
| release (текущий, minify, без shrink) | 730 783 | 3 145 728 |
| release + shrinkResources + keep.xml | 681 078 | ↑ запас 4.6× |
| debug | 2 380 971 | (не нормируется) |

PERF-01 закрывается механическим `stat` в CI и в верификации фазы. PERF-02/03 (PSS/холодный старт/аллокации) — только устройство → deferred UAT, инструмент — `11-PERF-CHECKLIST.md` с adb-командами (`adb shell dumpsys meminfo`, `am start -W`… — написать в фазе).

## 7. PERF-03 setup_message override

Контекст упоминал «оверрайд setup_message → собственный текст». Ресерч показал: строка **мертва** (использовалась только удалённым в фазе 10 legacy-диалогом) — оверрайдить нечего, правильное действие = удалить из базового strings.xml (см. § 3).

## 8. Форма плана (рекомендация)

1. **Task 1 — release-конфиг** (код): `shrinkResources true` + `res/raw/keep.xml` (текст выше) + ребрендинг (`english_ime_name` → «Tatar Keyboard», удалить `setup_message` из values/strings.xml, URL-плейсхолдеры → целевые GitHub-URL) + `versionName "1.0.0"`. Гейты: assembleDebug/Release зелёные, aapt2-дамп release: locale_name ≥ 8, keyboard_layout_set_tatar есть, label_pause/wait = 2; APK ≤ 3 МБ; check-no-internet OK; apksigner verify OK.
2. **Task 2 — CI**: дописать в ci.yml assembleRelease (unsigned) + size-гейт + shrink-гейт (grep locale_name через aapt2). Без прогона (репо нет) — верификация синтаксисом/ревью.
3. **Task 3 — документы**: переписать README.md (сейчас — README Simple Keyboard c чужими бейджами/ссылками F-Droid/Play — обязательно заменить), расширить PRIVACY.md (сейчас 1 строка), создать CHANGELOG.md (v1.0.0 по фазам), docs/PUBLISH-CHECKLIST.md (создание репо, push, зелёный CI + красный негативный тест PERF-04, тег v1.0.0, GitHub Release с APK, заявка IzzyOnDroid, бэкап release.jks), 11-PERF-CHECKLIST.md (adb-команды PERF-02/03).
4. **Task 4 — верификация/bookkeeping**: структурные грепы, простановка PERF-01/REL-01 (REL-02/03 — после ручной публикации), STATE/REQUIREMENTS.
5. **Task 5 (deferred/UAT)**: device-прогон PERF-02/03 + ручная публикация (REL-03). **Никаких push/создания репо автоматически** — только по явному действию пользователя.

Риски плана: единственный содержательный — shrink (снят экспериментом + двойной гейт aapt2); остальное — документы и однострочники.

---
*Phase: 11-proizvoditelnost-i-reliz*
*Researched: 2026-07-19 — все вердикты по живым сборкам и дампам, дерево откачено в чистое состояние*
