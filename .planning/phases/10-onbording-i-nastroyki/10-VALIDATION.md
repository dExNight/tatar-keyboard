# Phase 10 — Validation (Nyquist coverage + source audit)

**Plan:** 10-01-PLAN.md
**Created:** 2026-07-19
**Framework:** нет unit-фреймворка (standing-паттерн фаз 1–9: `assembleDebug`/`assembleRelease` + fail-capable грепы + `scripts/check-no-internet.sh` + отложенный device-UAT)
**Quick run:** `./gradlew assembleDebug`
**Full suite:** `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh`

## Multi-Source Coverage Audit

Каждый item одного из четырёх источников (GOAL / REQ / RESEARCH / CONTEXT) должен быть COVERED планом.

### GOAL (ROADMAP Phase 10 Success Criteria)

| # | Goal item | Covered by | Status |
|---|-----------|-----------|--------|
| SC1 | Онбординг-экран: 2 шага со статусами, открывает системные экраны (SETUP-01) | Task 1 (SetupActivity + детект + intents + рефреш), Task 2 (LAUNCHER), Task 4 (греп-пиновка), Task 5 (device) | COVERED |
| SC2 | В настройках вкл/выкл звук+вибрация, реально меняет отклик (SETUP-02) | Task 4 (zero-code грепы: тумблеры + живой отклик), Task 5 (live-переключение) | COVERED |
| SC3 | Чистая установка → от иконки до «ә» без подсказок | Task 5 (device-UAT, deferred по standing-паттерну) | COVERED (deferred UAT) |
| SC4 | APK собирается; smoke-матрица не деградировала | Task 4 (обе сборки), Task 5 (smoke device) | COVERED |

### REQ (REQUIREMENTS.md phase_req_ids)

| Req ID | Covered by | Status |
|--------|-----------|--------|
| SETUP-01 | Task 1 + Task 2 + Task 3 + Task 4 (structural) + Task 5 (device) | COVERED |
| SETUP-02 | Task 4 (zero-code verification) + Task 5 (live) | COVERED |

### RESEARCH (10-RESEARCH.md features/constraints/open-questions)

| Research item | Covered by | Status |
|---------------|-----------|--------|
| SetupActivity design (2 шага, детект-API, рефреш onWindowFocusChanged) | Task 1 | COVERED |
| Detection: getEnabledInputMethodList (шаг 1) | Task 1 | COVERED |
| Detection: Settings.Secure.DEFAULT_INPUT_METHOD префикс (шаг 2, Pitfall 1) | Task 1 | COVERED |
| Intents: ACTION_INPUT_METHOD_SETTINGS + showInputMethodPicker | Task 1 | COVERED |
| Манифест LAUNCHER-переброс на SetupActivity | Task 2 | COVERED |
| Open-Q1: судьба legacy not-enabled диалога → удалить (Pitfall 3) | Task 3 (с механической проверкой зависимостей) | COVERED |
| Open-Q2: ic_launcher — не трогать, backlog | Task 4 (STATE.md запись), prohibition №3 | COVERED (deferred backlog) |
| Open-Q3: IME→настройки неразрыв после снятия LAUNCHER | Task 2 (греп launchSettings класс-интент) | COVERED (резолв: путь цел) |
| SETUP-02 audit (вердикт «уже готово», живо-реактивно) | Task 4 | COVERED |
| Pitfall 2: picker-возврат не обновляет статус → onWindowFocusChanged+onResume | Task 1, A1 | COVERED |
| Pitfall 4: WindowInsets на targetSdk 37 (edge-to-edge) | Task 1 (insets-паттерн) | COVERED |
| Pitfall 5: ребрендинг-переползание (не трогать ~30 setup_message) | Task 3 (ресурс не трогаем), prohibition №3, boundary-чек | COVERED |
| Security: SetupActivity exported, игнор extras (T-10-01) | Task 4 (греп no-extras), threat_model | COVERED |

### CONTEXT (10-CONTEXT.md D-XX / locked decisions)

CONTEXT.md формулирует locked-решения прозой (без нумерованных D-XX). Каждое покрыто:

| Locked decision | Covered by | Status |
|-----------------|-----------|--------|
| Онбординг = главная работа: 2 шага со статусами, лаунчер ведёт туда | Task 1 + Task 2 | COVERED |
| БЕЗ Compose (бюджет APK) — classic View/XML | Task 1 (XML), prohibition №1, Task 4 no-gradle boundary | COVERED |
| Правовое: онбординг-тексты свои (ru+en base), бренд через @string, полный ребрендинг вне фазы | Task 1 (strings-setup), Task 3 (setup_message не трогаем) | COVERED |
| Discretion: 1 Activity, live-статусы, done→SettingsActivity | Task 1 | COVERED |
| Deferred SC3 device-UAT — отложенный бандл | Task 5 | COVERED (honored as deferred) |
| Deferred: полный ребрендинг setup_message → фаза 11 | prohibition №3 (NOT in plan) | HONORED (excluded) |
| Deferred: Compose → отклонён | prohibition №1 (NOT in plan) | HONORED (excluded) |

**Итог аудита:** 0 unplanned items. Все GOAL/REQ/RESEARCH/CONTEXT-items — COVERED (device-зависимые честно помечены deferred UAT по standing-паттерну фаз 1–9). Deferred Ideas корректно исключены. Фаза влезает в один план (~5 задач, ядро — 1 новый Kotlin Activity; ноль риска превышения контекста/APK).

## Phase Requirements → Test Map

| Req | Behavior | Test Type | Automated Command | File Exists? |
|-----|----------|-----------|-------------------|-------------|
| SETUP-01 | SetupActivity = MAIN/LAUNCHER; SettingsActivity LAUNCHER снят | structural grep | `grep -c category.LAUNCHER AndroidManifest.xml` == 1 + под SetupActivity | ❌ Wave 0 (грепы в Task 1/2/4) |
| SETUP-01 | Детект шага 1 (getEnabledInputMethodList) | structural grep | `grep enabledInputMethodList SetupActivity.kt` | ❌ Wave 0 (Task 1) |
| SETUP-01 | Детект шага 2 (DEFAULT_INPUT_METHOD префикс) | structural grep | `grep 'DEFAULT_INPUT_METHOD' + 'startsWith' SetupActivity.kt` | ❌ Wave 0 (Task 1) |
| SETUP-01 | Шаги стартуют системные intents | structural grep | `grep 'ACTION_INPUT_METHOD_SETTINGS\|showInputMethodPicker' SetupActivity.kt` | ❌ Wave 0 (Task 1) |
| SETUP-01 | Рефреш по возврату | structural grep | `grep 'onWindowFocusChanged\|onResume\|updateStepStates' SetupActivity.kt` | ❌ Wave 0 (Task 1) |
| SETUP-01 | IME→настройки неразрыв | structural grep | `grep 'setClass(...SettingsActivity.class)' LatinIME.java` | ✅ exists (:881) |
| SETUP-01 | Сборка не сломана | build | `./gradlew assembleDebug` | ✅ Gradle есть |
| SETUP-01 SC3 | Иконка → «ә» без подсказок | manual (device) | — | DEFERRED (Task 5) |
| SETUP-02 | Тумблеры vibrate_on/sound_on/громкость | structural grep | `grep 'vibrate_on\|sound_on\|pref_keypress_sound_volume' prefs_screen_key_press.xml` | ✅ exists |
| SETUP-02 | Достижимость из корня настроек | structural grep | `grep KeyPressSettingsFragment prefs.xml` | ✅ exists |
| SETUP-02 | Вибро скрыт без вибратора | structural grep | `grep 'hasVibrator\|removePreference' KeyPressSettingsFragment.java` | ✅ exists |
| SETUP-02 | Живая реакция IME | structural grep | `grep registerOnSharedPreferenceChangeListener Settings.java` + `onSettingsChanged` LatinIME/AudioAndHaptic | ✅ exists |
| SETUP-02 SC2 | Тумблеры реально меняют отклик | manual (device) | — | DEFERRED (Task 5) |
| SETUP-01/02 SC4 | Smoke-матрица не деградировала | manual (device) | — | DEFERRED (Task 5) |

## Sampling Rate

- **Per task commit:** `./gradlew assembleDebug`
- **Per wave merge:** `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh` + структурные грепы SETUP-01/02 + boundary-чек (13ce533 = 6 файлов)
- **Phase gate:** обе сборки зелёные, все структурные грепы PASS, release-APK ≤ 3 145 728 байт, ноль новых зависимостей (build.gradle не в диффе); device-UAT (SC2-live/SC3/SC4) deferred по standing-паттерну фаз 1–9.

## Wave 0 Gaps

- [ ] Структурные fail-capable грепы SETUP-01 (манифест LAUNCHER-переброс, детект-API, intents, рефреш, no-extras, строки) — пишутся в Task 1/2/4 как verification.
- [ ] Структурные грепы SETUP-02 (тумблеры + достижимость + hasVibrator + живой отклик) — verification-only, код уже есть (вердикт ресерча).
- [ ] Boundary-чек (13ce533..HEAD = ровно 6 файлов, без gradle/ic_launcher/старых strings.xml) — Task 4.
- [ ] Нет нужды в новом test-фреймворке — консистентно с фазами 1–9 (grep + build + deferred device-UAT).

## Deferred (device-UAT, standing-паттерн)

SC2-live (тумблеры реально меняют отклик), SC3 (иконка→«ә» без подсказок), SC4 (smoke-матрица) — отложены в STATE.md Blockers self-contained чек-листом (Task 5), присоединяются к UAT-бандлу фаз 1–9; устройство не подключено (adb devices пуст). Финальная простановка чек-боксов SETUP-01/02 в REQUIREMENTS.md — только после реального прогона.
