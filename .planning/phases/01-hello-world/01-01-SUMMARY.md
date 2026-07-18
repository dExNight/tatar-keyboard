---
phase: 01-hello-world
plan: 01
subsystem: infra
tags: [android, gradle, agp9, kotlin-interop, ime, fork, aapt2, apache-2.0]

# Dependency graph
requires: []
provides:
  - "Форк rkkr/simple-keyboard (b40c70d9, v6.5) с полной upstream-историей, собирается в этом репозитории"
  - "applicationId org.tatarkeyboard.ime + .debug suffix — уникальный пакет, ставится рядом с оригиналом"
  - "scripts/check-no-internet.sh — двухуровневая проверка PERF-04 (grep манифеста + aapt2 dump permissions), переиспользуется CI в 01-02"
  - "Built-in Kotlin AGP 9 включён; KotlinInteropCheck доказывает Java→Kotlin вызов на уровне сборки"
affects: [01-02, phase-2-layouts, phase-6-ios-skin, phase-11-release]

# Tech tracking
tech-stack:
  added:
    - "Gradle 9.6.0 (wrapper), AGP 9.2.1, built-in Kotlin 2.3.x (KGP бандлится с AGP)"
    - "aapt2 (Android build-tools 36.0.0) для дампа permissions"
  patterns:
    - "applicationId меняется без смены namespace (минимальный diff против upstream, будущие мержи почти бесплатны)"
    - "Kotlin через built-in AGP 9 (удаление android.builtInKotlin=false), без плагина org.jetbrains.kotlin.android"
    - "Раскладки/поведение стока не трогаются — форк = база, наш код добавляется поверх"

key-files:
  created:
    - "scripts/check-no-internet.sh"
    - "app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/KotlinInteropCheck.kt"
    - "gradle/wrapper/gradle-wrapper.jar (не трекается — gitignored; восстановить через `gradle wrapper` или официальный URL v9.6.0)"
  modified:
    - "app/build.gradle (applicationId, versionCode/Name, applicationIdSuffix .debug)"
    - "app/src/main/res/values/strings-appname.xml (english_ime_name = Tatar Keyboard (dev))"
    - "gradle.properties (удалён android.builtInKotlin=false)"
    - "app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java (import + KotlinInteropCheck.log() в onCreate)"
    - ".gitignore (union наш + upstream)"

key-decisions:
  - "applicationId = org.tatarkeyboard.ime (провизорный, нейтральный вариант из ресерча); финальный — фаза 11"
  - "applicationIdSuffix .debug — сосуществование оригинал/debug/release (Pitfall 5)"
  - "compileSdk/targetSdk остаются 37 (как в базе b40c70d9), вопреки «36» в CLAUDE.md — даунгрейд = лишний риск"
  - "Kotlin через built-in AGP 9, без org.jetbrains.kotlin.android (конфликт extension)"
  - "gradle-wrapper.jar не в git (upstream игнорирует gradle/) — скачан официальный jar Gradle v9.6.0 локально"

patterns-established:
  - "Pattern: applicationId без смены namespace — сохраняет мержабельность upstream, proguard-правила и method.xml FQCN валидны"
  - "Pattern: двухуровневая PERF-04-проверка (grep источника + aapt2 по артефакту) как единый скрипт для локали и CI"

requirements-completed: []

coverage:
  - id: D1
    description: "Форк rkkr/simple-keyboard (b40c70d9) смержен с полной историей и собирается (assembleDebug) без правок кода"
    verification:
      - kind: integration
        ref: "./gradlew assembleDebug (BUILD SUCCESSFUL 1m59s baseline); git merge-base --is-ancestor b40c70d9 HEAD (exit 0)"
        status: pass
    human_judgment: false
  - id: D2
    description: "scripts/check-no-internet.sh падает (exit 1) при INTERNET в манифесте/APK и проходит (exit 0) на текущем коде (PERF-04)"
    verification:
      - kind: integration
        ref: "bash scripts/check-no-internet.sh (exit 0, только VIBRATE); негативный тест: инъекция INTERNET → exit 1, откат → exit 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "APK собирается под уникальным пакетом org.tatarkeyboard.ime.debug с именем «Tatar Keyboard (dev)»"
    verification:
      - kind: integration
        ref: "aapt2 dump badging app-debug.apk → package name='org.tatarkeyboard.ime.debug' versionCode='1' versionName='0.1.0'"
        status: pass
    human_judgment: false
  - id: D4
    description: "Kotlin-код компилируется built-in Kotlin'ом и вызывается из Java (LatinIME.onCreate → KotlinInteropCheck.log())"
    verification:
      - kind: integration
        ref: "./gradlew assembleDebug (BUILD SUCCESSFUL 57s с KotlinInteropCheck; Java-вызов Kotlin-object компилируется)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Смоук на устройстве: установка рядом с оригиналом, включение IME, печать в реальном приложении, logcat TatarKeyboard «Kotlin interop OK»"
    verification: []
    human_judgment: true
    rationale: "Task 5 checkpoint:human-verify — DEFERRED пользователем (устройство не подключено). Требует физического устройства/эмулятора и системных настроек IME; runtime-подтверждение interop через logcat не выполнено. Phase verification MUST treat as human_needed."

# Metrics
duration: 35min
completed: 2026-07-18
status: complete
---

# Phase 1 Plan 01: Форк, applicationId, Kotlin interop Summary

**Форк rkkr/simple-keyboard (b40c70d9) собирается под org.tatarkeyboard.ime.debug, INTERNET-проверка PERF-04 доказуемо падает на инъекции, Kotlin-interop подтверждён на сборке — on-device смоук отложен пользователем.**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-07-18 (сессия исполнения)
- **Completed:** 2026-07-18
- **Tasks:** 4 из 5 complete (Task 5 deferred — human verification pending)
- **Files modified:** 5 изменено, 2 создано (+ gitignored wrapper jar)

## Accomplishments
- Полная upstream-история rkkr/simple-keyboard (b40c70d9, v6.5) смержена в main (`--allow-unrelated-histories`); baseline `assembleDebug` зелёный без единого изменения кода.
- `scripts/check-no-internet.sh` — двухуровневая PERF-04-проверка; негативный тест доказан (инъекция INTERNET → exit 1, откат → exit 0).
- Рибрендинг: `org.tatarkeyboard.ime` + `.debug` suffix, versionCode 1 / versionName 0.1.0, имя «Tatar Keyboard (dev)»; namespace не тронут. `aapt2 dump badging` подтверждает `org.tatarkeyboard.ime.debug`.
- Built-in Kotlin AGP 9 включён (удалён `android.builtInKotlin=false`); `KotlinInteropCheck.log()` вызывается из `LatinIME.onCreate()` — Java→Kotlin компилируется, interop доказан на уровне сборки.

## Task Commits

Каждая задача закоммичена атомарно:

1. **Task 1: Мерж upstream b40c70d9 + baseline-сборка** — `6594bcd` (chore)
2. **Task 2: scripts/check-no-internet.sh (двухуровневая PERF-04)** — `43860bb` (feat)
3. **Task 3: applicationId org.tatarkeyboard.ime, имя, версия 0.1.0** — `852b163` (feat)
4. **Task 4: Kotlin interop — built-in Kotlin + KotlinInteropCheck** — `5bbda51` (feat)
5. **Task 5: Смоук на устройстве** — **DEFERRED (human verification pending)** — не закоммичен, критерии не отмечены пройденными

**Промежуточная фиксация состояния:** `91a275c` (docs: paused at device-verify checkpoint)
**Plan metadata:** этот SUMMARY (docs: summary — device verification deferred)

## Files Created/Modified
- `.gitignore` — union наш + upstream (сохранены `*.jks`, `local.properties`)
- `gradle/wrapper/gradle-wrapper.jar` — восстановлен локально (официальный Gradle v9.6.0), не трекается git
- `scripts/check-no-internet.sh` — двухуровневая проверка INTERNET (grep манифеста + aapt2 dump permissions по APK)
- `app/build.gradle` — applicationId, versionCode 1, versionName 0.1.0, `applicationIdSuffix ".debug"` в debug buildType
- `app/src/main/res/values/strings-appname.xml` — `english_ime_name` = «Tatar Keyboard (dev)»
- `gradle.properties` — удалена строка `android.builtInKotlin=false`
- `app/src/main/java/.../latin/utils/KotlinInteropCheck.kt` — Kotlin object, `@JvmStatic fun log()`
- `app/src/main/java/.../latin/LatinIME.java` — import + `KotlinInteropCheck.log();` в начале `onCreate()`

## Decisions Made
- **applicationId = `org.tatarkeyboard.ime`** — провизорный нейтральный вариант из ресерча (владелец GitHub-репо неизвестен); смена до публикации — одна строка (фаза 11).
- **`applicationIdSuffix ".debug"`** — рядом живут оригинал, наш debug и будущий release (Pitfall 5).
- **compileSdk/targetSdk 37** оставлены как в базе, вопреки «36» в CLAUDE.md — движение назад = лишний риск; CLAUDE.md обновить отдельным правочным коммитом вне фазы.
- **Kotlin через built-in AGP 9** — плагин `org.jetbrains.kotlin.android` не подключён (дал бы конфликт extension при включённом built-in).
- **gradle-wrapper.jar не в git** — upstream игнорирует `gradle/`; локально скачан официальный jar Gradle v9.6.0. На чистой машине восстанавливается `gradle wrapper` или тем же URL (зафиксировано в STATE.md).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Восстановлен gradle/wrapper/gradle-wrapper.jar**
- **Found during:** Task 1 (baseline-сборка)
- **Issue:** upstream `.gitignore` игнорирует `gradle/`, поэтому `gradle-wrapper.jar` не приехал мержем; `./gradlew` падал с «Unable to access jarfile … gradle-wrapper.jar». Системного `gradle` на машине нет.
- **Fix:** скачан официальный `gradle-wrapper.jar` из тега `v9.6.0` репозитория gradle/gradle (совпадает с `distributionUrl` gradle-9.6.0 в `gradle-wrapper.properties`). Файл gitignored — git status чист, tree не загрязнён.
- **Files modified:** `gradle/wrapper/gradle-wrapper.jar` (untracked)
- **Verification:** `./gradlew --version` → Gradle 9.6, Kotlin 2.3.21; `assembleDebug` зелёный.
- **Committed in:** не коммитится (gitignored) — задокументировано в STATE.md/этом SUMMARY.

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Восстановление wrapper jar необходимо для любой сборки; артефакт не входит в git, поведение стока не изменено. Скоуп не расширен.

## Issues Encountered
- **On-device верификация отложена (Task 5).** Устройство/эмулятор не подключены (`adb devices` пуст). Критерии Task 5 НЕ отмечены пройденными. Фаза verification обязана трактовать это как `human_needed`.

## User Setup Required
None для этого плана — внешние сервисы не настраиваются. Однако **отложена ручная проверка на устройстве** (см. ниже).

## Deferred Verification — Task 5 (checkpoint:human-verify, human_needed)

Отложено пользователем («Отложить проверку», устройство не подключено). Точная последовательность к выполнению при появлении устройства:

1. (опц.) установить оригинальный Simple Keyboard (F-Droid/APK) для side-by-side; иначе уникальность доказана `aapt2 dump badging` (package `org.tatarkeyboard.ime.debug`).
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. `adb shell pm list packages | grep -e simplekeyboard -e tatarkeyboard` → ожидается `org.tatarkeyboard.ime.debug` (+ `rkr.simplekeyboard.inputmethod`, если оригинал установлен)
4. Настройки → Система → Языки и ввод → Экранная клавиатура → включить «Tatar Keyboard (dev)» → выбрать текущей
5. Напечатать фразу в реальном приложении (мессенджер/заметки) — текст коммитится
6. `adb logcat -s TatarKeyboard` → ожидается строка `Kotlin interop OK` после показа клавиатуры

**Ожидаемые acceptance-критерии (Task 5), пока НЕ подтверждены:**
- `pm list packages` содержит `org.tatarkeyboard.ime.debug` — конфликтов установки нет
- «Tatar Keyboard (dev)» отображается в системном списке и включается
- Текст реально печатается в стороннем приложении
- `adb logcat -s TatarKeyboard` содержит `Kotlin interop OK`

## Next Phase Readiness
- Автоматизированная часть плана 01-01 завершена: сборка, уникальный пакет, PERF-04-скрипт, Kotlin-interop — всё зелёное.
- **Блокер к «полностью verified»:** on-device смоук Task 5 (human_needed) отложен. Phase 1 verification должна поднять его как незакрытый human-verify.
- Готово к плану 01-02 (keystore/signingConfig + CI на GitHub Actions, где `scripts/check-no-internet.sh` встаёт в workflow).

---
*Phase: 01-hello-world*
*Completed: 2026-07-18 (Task 5 deferred — human verification pending)*
