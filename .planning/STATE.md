---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_phase: 10
current_phase_name: Онбординг и настройки
status: verifying
stopped_at: Completed 10-01-PLAN.md — Phase 10 complete-local (Task 5 device UAT deferred)
last_updated: "2026-07-18T21:59:44.725Z"
last_activity: 2026-07-19
last_activity_desc: Phase 10 execution started
progress:
  total_phases: 10
  completed_phases: 10
  total_plans: 11
  completed_plans: 11
---

# State: Tatar Keyboard

## Current Position

**Milestone:** v1.0 — MVP + релиз (GitHub Releases + IzzyOnDroid)
**Phase:** 10 (Онбординг и настройки) — EXECUTING
**Plan:** 1 of 1
**Status:** Phase complete — ready for verification
**Last activity:** 2026-07-19 — Phase 10 execution started

Progress: [██████████] 100%

## Accumulated Context

### Decisions

- База — форк Simple Keyboard (rkkr), Apache-2.0; решение окончательное, «этап 0» (прототип HeliBoard) пропущен.
- Новый код — Kotlin через interop; Java-базу массово не конвертировать. UI — один кастомный View + Canvas; Compose только в Activity настроек.
- Без composing-текста в MVP: коммит сразу, удаление `deleteSurroundingTextInCodePoints`.
- Без разрешения INTERNET — CI-проверка с фазы 1.
- Раскладки — данными (XML), формат должен допускать латиницу Zamanälif позже.
- Рабочее название «Tatar Keyboard»; финальное имя и applicationId — до публикации (фаза 11), applicationId фиксируется в фазе 1.
- План Б (форк HeliBoard, цена GPL-3.0) — решение принимать до больших вложений в iOS-скин (т.е. до фазы 6).
- [01-01] applicationId = `org.tatarkeyboard.ime` (провизорный, нейтральный вариант из ресерча) + `applicationIdSuffix ".debug"` в debug — сосуществование оригинала/debug/release.
- [01-01] compileSdk/targetSdk остаются 37 (как в базе b40c70d9), вопреки «36» в CLAUDE.md — даунгрейд = лишний риск; CLAUDE.md обновить отдельным коммитом вне фазы.
- [01-01] `gradle/wrapper/gradle-wrapper.jar` не трекается (upstream игнорирует `gradle/`); локально скачан официальный jar Gradle v9.6.0 — на новой машине его нужно восстановить (`gradle wrapper` или тот же URL).
- [01-01] Kotlin — через built-in Kotlin AGP 9 (удалена строка `android.builtInKotlin=false`), плагин `org.jetbrains.kotlin.android` не подключён.
- [01-02] Подпись release: условный signingConfig через `keystore.properties` (Pattern 3); `release.jks` (RSA 4096, validity 10950, alias `tatarkeyboard`) и `keystore.properties` — только локально, оба gitignored (проверено `git check-ignore` + grep истории). Без файла assembleRelease даёт unsigned APK — CI живёт без секретов.
- [01-02] CI (`.github/workflows/ci.yml`): официальные actions на мажорных тегах, `permissions: contents: read`; вызывает `scripts/check-no-internet.sh` дважды (fast-fail до сборки + по собранному APK); gradle-wrapper.jar восстанавливается шагом workflow из тега v9.6.0 gradle/gradle с pin по sha256 (jar не в git).
- [01-02] GitHub-репозиторий НЕ создан (на машине нет `gh`, remote только `upstream`) — создание репо/push/прогоны CI отложены; точные шаги в 01-02-SUMMARY.md § Deferred.

- [02-01] Татарская раскладка: rowkeys литеральными кодпоинтами (без !text/ — обход DEFAULT-ловушки KeyboardTextsTable), высоты 5×20%p / 6×16.667%p; Java-диф ограничен реестром SubtypeLocaleUtils (case с break, tt первым в getDefaultSubtypes). Ревью F1: ё и ъ недостижимы до phase-3 long-press — включить в LAYOUT-02.

- [03-01] Long-press дубли — литеральные `latin:moreKeys` одиночными кодпоинтами (10 клавиш вкл. е→ё/ь→ъ из F1), без KeyboardTextsTable; заглавные не прописываются (MoreKeySpec auto-upcase). Русская раскладка — собственный layout set `russian` (6 XML, копия tatar-рядов без пятого ряда) — shared east_slavic (be_BY/kk/ky/uk) не тронут. Реестр: LOCALE_TATAR мигрирован на "tt_RU" (prefs-миграцию не писали — dev-строка tt:tatar самовосстанавливается), getDefaultSubtypes = детерминированная тройка tt_RU→ru→en_US (MVP-хак и мёртвый fallback F2 удалены). «Татарча» через locale_exception_keys. SWITCH-01/02 (глобус/цикл/пикер/персистентность) — ноль нового кода, штатные механизмы форка; pref_enable_ime_switch остаётся false (цикл строго внутри IME). Трактовка A3: «система видит три subtype» = виртуальный subtype-реестр форка — при простановке SWITCH-01 в REQUIREMENTS.md добавить аннотацию.

- [05-01] Double-space→period восстановлен в InputLogic по AOSP-паттерну: always-on без pref, константа 1100 мс, гейты password + буква/цифра-перед-пробелом (новый cache-only аксессор getCodePointBeforeCursor(offset) в RichInputConnection), revert по backspace; гигиена состояния — сбросы mJustDoubleSpaced/mLastSpaceDownTime в startInput() и на любом событии кроме успешного double-space (несепаратор, не-пробельный сепаратор, backspace без revert). Свайп-дефолт pref_space_swipe → true в 3 согласованных местах (Settings.java, prefs_screen_preferences.xml, app_restrictions.xml); pref_delete_swipe не тронут. INPUT-07 — zero-work, доказательства запинованы fail-capable-грепами. Boundary фазы = 5 объявленных файлов (diff-чек от 8e4693e).

- [06-01] iOS-скин маршрутом (b) 06-RESEARCH.md: тема id=7 «Tatar» поверх штатной системы тем (KeyboardTheme.KEYBOARD_THEMES, DEFAULT_THEME_ID=7), палитра ТОЛЬКО ресурсами ios_* в values/ (#D4D6DD/#FFFFFF/#B3B7C0/#40000000) + values-night/ (#2C2C2C/#6B6B6B/#474747/#B3000000) — dark mode штатным механизмом; тень — layer-list из двух roundRect 5dp со смещением 1dp (setShadowLayer отклонён); иконки shift/shift_locked/globe — собственные path'ы, тонировка ?attr/functionalTextColor сохранена. 3 PERF-фикса: индексный цикл onDrawKeyboard, HashMap-кэш клонов KeyDrawParams (инвалидация в setKeyboard), кэш строки+textScaleX языка пробела (инвалидация в setKeyboard/startDisplayLanguageOnSpacebar). A11y-каркас ExploreByTouchHelper (Kotlin) + androidx.customview:customview:1.1.0 — первая внешняя зависимость; APK-гейт пройден: release 645 830 → 700 679 байт (+54 849, ≤ 3 145 728); транзитивы только annotation/core/collection. Попутно gradle.properties: android.useAndroidX=false→true (обязательно для androidx-зависимостей).

- [04-01] Фаза 4 — zero-code верификация: вердикт ресерча 04-RESEARCH.md ALL WORKS (INPUT-01..04 работают в базе форка), все доказательства запинованы fail-capable-грепами. Принята трактовка INPUT-03 (2026-07-18): эквивалент deleteSurroundingText с подсчётом по кодпоинту + AOSP-автоповтор 400 мс → 50 мс. Бонус INPUT-01: caps lock long-press'ом shift (1200 мс). Диф кода фазы пуст (boundary-check b19ce97..HEAD по app/ = пусто).

- [07-01] Фаза 7 — zero-Java стилизация: in-layer превью/панель подтверждены (DrawingPreviewPlacerView в android.R.id.content окна IME, PopupWindow в исходниках отсутствует — перенос не понадобился); баллон/панель — 2 новых drawable (ios_key_preview_background: layer-list roundRect ios_key_normal + 1dp-тень, радиус 5dp, size/padding зеркалят старый feedback-drawable; ios_popup_panel_background: радиус 5dp, padding 5dp сохранён) + 2 item в themes-tatar.xml; sound default оставлен false по решению пользователя (тумблер — фаза 10); Java/Kotlin-дифф фазы = 0 строк (boundary-чек fbfd66a..HEAD).

- [08-01] Фаза 8 — data-only совместимость: единственная правка под app/ = 1 строка values-land/config.xml (config_use_fullscreen_mode true→false) — extract mode мёртв во всех 5 config-вариантах (true=0, false=5), onEvaluateFullscreenMode() всегда false штатным ресурсным путём (Java-override отклонён ресерчем). Все 5 COMPAT-вердиктов запинованы fail-capable-грепами: insets-линия upstream 827da4f/2885ae5 (fitsSystemWindows v28-сплит, requestApplyInsets, onComputeInsets, contrast off), directBoot (манифест + device-protected prefs, ноль обходных call-sites), no-composing (0 вхождений) + commitText/deleteSurroundingText, password (mIsPasswordField-гейт + ноль словаря). Матрица SC5 написана — 08-UAT-MATRIX.md (CLOSED-STRUCTURAL + DEFERRED, ни одного PASS до device-прогона). Java/Kotlin-дифф фазы = 0 строк (boundary-чек d2ae619..HEAD).

- [09-01] Фаза 9 — TalkBack достроен до полной A11Y-01/02: 4 гэпа ресерча закрыты — G2 описания только через KeyDescriptionMapper (когда по key.getCode(), 6 татарских кодпоинтов hex-литералами + шаблон «Заглавная %s», shift ×4 по mElementId, enter ×7 по imeAction с приоритетом custom label; строки en base values/strings-a11y.xml + values-ru/strings-a11y.xml, AOSP-имена spoken_*), G1 клик = синтез MotionEvent DOWN/UP в видимый центр → public MainKeyboardView.processMotionEvent (штатный touch-путь, AOSP-паттерн), G3 isClickable/isTextEntryKey (androidx.core транзитив — новых зависимостей нет), G4 TYPE_VIEW_CLICKED + return true. Fork-Java-дифф = 0 строк (boundary 0a280ce: ровно 2 .kt + 2 .xml). Password: собственных announceForAccessibility ноль (запиновано), описания клавиш не обскьюрены осознанно (ACCESSIBILITY_SPEAK_PASSWORD deprecated c API 26 — озвучка = зона TalkBack).

- [10-01] Фаза 10 — онбординг SetupActivity + zero-code верификация SETUP-02: новый SetupActivity.kt (Kotlin, classic View/XML, наследует android.app.Activity — ноль новых зависимостей/gradle-правок) детектит 2 статуса живьём из системы (шаг 1 imm.enabledInputMethodList.any{packageName}, шаг 2 Settings.Secure.DEFAULT_INPUT_METHOD.startsWith("$packageName/") — префикс устойчив к debug-суффиксу), кнопки стартуют только системные экраны (ACTION_INPUT_METHOD_SETTINGS + showInputMethodPicker), рефреш идемпотентен в onWindowFocusChanged(true)+onResume, done-блок → SettingsActivity; собственный флаг завершения НЕ хранится (источник истины — система). Манифест: MAIN/LAUNCHER переехал на SetupActivity (блок ПЕРЕД SettingsActivity), у SettingsActivity LAUNCHER снят но exported=true сохранён (IME→настройки цел — launchSettings класс-интент LatinIME.java:881). Legacy not-enabled AlertDialog старого бренда + private isInputMethodOfThisImeEnabled удалены из SettingsActivity.onStart (проверка зависимостей грепом: оба символа были только в SettingsActivity.java), осиротевшие импорты вычищены, ресурс setup_message оставлен нетронутым (ребрендинг = фаза 11). SETUP-02 = уже реализовано, доказано грепами (тумблеры/громкость + hasVibrator→removePreference + живой отклик Settings→loadSettings→AudioAndHapticFeedbackManager), кода 0 строк. Строки — strings-setup.xml (en base + values-ru), бренд через @string/english_ime_name, «ә»-подсказка. Boundary 13ce533 = ровно 6 файлов app/ (1 .kt + 2 strings-setup.xml + 1 layout + манифест + SettingsActivity.java), без gradle/ic_launcher/старых strings.xml. Обе сборки зелёные, check-no-internet OK, release-APK ≤ 3 МБ. Device-UAT (SC2-live/SC3/SC4) — deferred по standing-паттерну фаз 1–9.

### Cross-cutting disciplines (каждая фаза)

- Smoke-тест матрицы InputConnection (Telegram, Chrome/WebView keyCode 229, password-поля, MIUI/One UI) — в критериях каждой фазы ввода/UI; полный проход — фаза 8.
- Ноль аллокаций в горячем пути пишется в код при создании (Paint/Rect — поля, точечный invalidate); замеренная верификация PERF-01..03 — фаза 11.
- Каждая фаза завершается собирающимся и устанавливаемым APK — фаз, оставляющих проект несобираемым, не бывает.

### Open questions (не блокеры старта)

- Порядок клавиш пятого ряда (алфавитный vs частотный) — юзер-тест после MVP.
- minSdk 24 vs 26 — решить перед фазой 11.
- Финальное название приложения и applicationId — до публикации.
- app_restrictions.xml: android:title перепутаны между pref_enable_ime_switch и pref_space_swipe (pre-existing upstream, ~строки 40/45) — поправить отдельным коммитом вне фазы 5.
- Прогрессивный разгон backspace (удаление словами после N повторов, как в Gboard) — post-MVP, после юзер-теста; `repeatCount` уже пробрасывается в `onKeyRepeat` (TimerHandler.java:53).
- [09-01] moreKeys-панель вне a11y-дерева: ё/ъ (long-press е/ь) недостижимы с TalkBack — не блокер MVP (все 6 татарских букв на собственных клавишах пятого ряда, long-press для татарского ввода не нужен); отдельный делегат на MoreKeysKeyboardView — backlog.
- [09-01] values-tt (татарские названия букв в описаниях) + announce смены shift-режима (AOSP spoken_description_shiftmode_*) — post-MVP backlog.
- [10-01] ic_launcher (adaptive-иконка ic_launcher.xml + foreground/monochrome, ic_launcher_background=#ECEFF1) — наследие базы форка Simple Keyboard; замена = ребрендинг → фаза 11/backlog (open-Q2 ресерча 10-RESEARCH.md). НЕ трогать в фазе 10.
- [10-01] Полный ребрендинг ~30 локализованных setup_message (старый бренд «Simple Keyboard») — строка перестала использоваться из кода после удаления legacy-диалога, но оставлена в ресурсах; чистка/ребрендинг → фаза 11 (Pitfall 5).

### Blockers/Concerns

- ⚠️ [Phase 1] Отложенная ручная проверка (принята пользователем 2026-07-18): on-device smoke debug/release, создание GitHub-репо + зелёный CI + красный ci-negative-test (доказательство PERF-04 на Actions), бэкап release.jks. Точные шаги — 01-01/01-02-SUMMARY.md § Deferred; прогнать при первой возможности (устройство + GitHub).
- ⚠️ [Phase 2, plan 02-01] Task 5 on-device UAT deferred — устройство не подключено (adb devices пуст), по образцу фазы 1. BUILD-критерии закрыты автоматикой (assembleDebug зелёный, aapt2 видит *_tatar ресурсы, check-no-internet OK, Java-diff = только SubtypeLocaleUtils.java). Чек-лист при появлении устройства: (1) чистая установка adb uninstall org.tatarkeyboard.ime.debug → adb install app-debug.apk → выбрать «Tatar Keyboard (dev)»; (2) клавиатура открывается ТАТАРСКОЙ: пятый ряд ә ө ү җ ң һ СВЕРХУ над ЙЦУКЕН; (3) напечатать «әни өй үрдәк җир таң һава» + «щи, ыл, эш, ике» — все 37 букв тапом, щ/ы/э/и не пустые; (4) shift → Ә Ө Ү Җ Ң Һ; (5) 5 рядов + action row без обрезки, adb logcat | grep -i "too tall" пуст; (6) ?123 → #+= → АБВ туда-обратно; (7) Number row ON: цифры НАД пятым рядом, 6 рядов помещаются, выключить обратно; (8) smoke-матрица SC4: «әни өй үрдәк җир таң һава» в Telegram, Chrome (адресная строка + поле формы/WebView keyCode 229), password-поле — без потерь/дублей. 02-01-SUMMARY.md создаётся после резолва чекпойнта.
- ⚠️ [Phase 3, plan 03-01] Task 5 on-device UAT deferred — устройство не подключено (adb devices пуст), по standing-паттерну фаз 1–2. BUILD-критерии закрыты автоматикой (assembleDebug зелёный, aapt2 видит все 6 *_russian ресурсов, check-no-internet OK, Java-diff b0b4606..HEAD = только SubtypeLocaleUtils.java, diff по *east_slavic*/KeyboardTextsTable.java пуст). Чек-лист при появлении устройства (полные шаги — 03-01-SUMMARY.md § Deferred Verification): (1) чистая установка adb uninstall org.tatarkeyboard.ime.debug → adb install (ОБЯЗАТЕЛЬНО — dev-prefs tt:tatar фазы 2 маскируют новую тройку, Pitfall 4); (2) открывается татарской; Languages: ровно три — «Татарча», «Русский»/ICU, «English (US)»; (3) тап глобуса циклит tt→ru→en→tt, русская = стандартная ЙЦУКЕН без пятого ряда, английская = QWERTY; (4) long-press глобуса → пикер: три наших subtype + другие IME; (5) 10 long-press дублей на ТАТАРСКОЙ (а→ә о→ө у→ү ж→җ н→ң х→һ э→ә г→һ + е→ё ь→ъ); (6) те же 10 на РУССКОЙ; (7) shift + long-press а → Ә; (8) персистентность: переключить на ru, force-stop, открыть → русская; (9) smoke-матрица SC5: Telegram/Chrome-WebView(229)/password с переключением языков; (10) MIUI — при наличии Xiaomi. Bookkeeping: при простановке SWITCH-01 аннотировать трактовку «виртуальные subtypes форка» (A3).

- ⚠️ [Phase 4, plan 04-01] Task 3 on-device UAT deferred — устройство не подключено (adb devices пуст), по standing-паттерну фаз 1–3. BUILD-критерии закрыты автоматикой (assembleDebug зелёный, check-no-internet OK, все структурные грепы INPUT-01..04 PASS, ZERO-CODE boundary: diff b19ce97..HEAD по app/ пуст). Чек-лист при появлении устройства (полные шаги — 04-01-SUMMARY.md § Deferred Verification; всё на татарской раскладке, shift/backspace выборочно повторить на русской): (1) установка текущего app-debug.apk (uninstall не обязателен — дефолты не менялись); (2) INPUT-01: тап shift → пятый ряд Ә Ө Ү Җ Ң Һ + ЙЦУКЕН заглавные, ввод буквы → возврат в строчные; двойной тап → caps lock (sticky-иконка), серия заглавных, тап → выход; long-press shift ~1.2 с → caps lock (бонус); (3) INPUT-02: новое сообщение в Telegram → shifted; после «. » → снова shifted; адресная строка Chrome и email-поле → НЕ shifted; выключить Auto-capitalization → эффект пропадает, включить обратно; (4) INPUT-03: «әни өй үрдәк», удержание backspace → после ~0.4 с серия ~20 удалений/сек; ә/җ/ң удаляются целиком за одно нажатие; (5) INPUT-04: Enter в поиске Chrome (лупа/поиск), Telegram (send), заметках multiline (перенос), форме actionDone (галка); (6) smoke SC5: backspace+Enter в WebView/поле формы Chrome (keyCode 229) и password-поле; (7) MIUI — при наличии Xiaomi (иначе пометить как не покрыто). 04-01-SUMMARY.md § Deferred Verification обновляется после резолва.

- ⚠️ [Phase 5, plan 05-01] Task 4 on-device UAT deferred — устройство не подключено (adb devices пуст), по standing-паттерну фаз 1–4. BUILD-критерии закрыты автоматикой (assembleDebug зелёный, check-no-internet OK, все структурные грепы INPUT-05..07 PASS, boundary: diff 8e4693e..HEAD по app/ = только 5 объявленных файлов). Чек-лист при появлении устройства (полные шаги — 05-01-SUMMARY.md § Deferred Verification): (1) установка свежего app-debug.apk; uninstall ЖЕЛАТЕЛЕН — dev-prefs могли зафиксировать space_swipe=false до флипа default; (2) INPUT-05: в Telegram и заметках «әни» + двойной пробел (< 1.1 с) → «әни. » и shift поднят; backspace сразу → откат к двум пробелам; двойной пробел после точки/в начале поля → просто два пробела; медленный второй пробел (> 1.1 с) → без точки; (3) INPUT-05/password: в password-поле двойной пробел НЕ даёт точку; (4) INPUT-06: свайп по пробелу двигает курсор из коробки без настроек; поверх татарского текста с ә/җ курсор шагает по буквам; выключить pref → жест пропадает, включить обратно; (5) INPUT-07: быстрая печать двумя пальцами «әни өй үрдәк җир» — без потери букв и порядка; (6) smoke SC4: пп. 2/4/5 в Chrome WebView/поле формы (keyCode 229) + password без аномалий; (7) MIUI — при наличии Xiaomi (иначе пометить как не покрыто). Финальная простановка чек-боксов INPUT-05..07 — после UAT.

- ⚠️ [Phase 6, plan 06-01] Task 7 on-device UAT deferred — устройство не подключено (adb devices пуст), по standing-паттерну фаз 1–5. BUILD-критерии закрыты автоматикой (assembleDebug + assembleRelease зелёные, check-no-internet OK, палитра/тема/PERF/boundary запинованы грепами, APK-гейт числовой PASS). Чек-лист при появлении устройства (полные шаги — 06-01-SUMMARY.md § Deferred Verification): (1) установка свежего APK; uninstall ЖЕЛАТЕЛЕН — dev-prefs могли зафиксировать старую тему (pref_keyboard_theme_20140509, риск R6); (2) SC1 палитра: светлая — фон #D4D6DD, клавиши белые, служебные #B3B7C0, радиус ~5dp, резкая 1dp-тень; тёмная — #2C2C2C/#6B6B6B/#474747; pressed: обычная темнеет, служебная белеет; (3) SC1 смена темы light↔dark при показанной клавиатуре без перезапуска IME (Android 12+ особо — live-reload, риск R3; если не подхватилась — снять условие < S в KeyboardSwitcher.java:91-93, 1 строка); (4) SC4 ввод фаз 2–5 поверх нового рендера: пятый ряд ә ө ү җ ң һ видим/нажимаем (гапы не съели ряд, риск R4), глобус tt/ru/en, shift/caps (иконка меняется на залитую), long-press дубли, double-space→период, свайп-курсор, двупальцевая печать; (5) SC3 профайлер: минута печати — ноль GC-событий, janky ~0; (6) smoke: Telegram, Chrome WebView (229), password; MIUI при наличии; (7) TalkBack НЕ верифицируется (фаза 9) — только отсутствие touch-регрессий без TalkBack.

- ⚠️ [Phase 7, plan 07-01] Task 3 on-device UAT deferred — устройство не подключено (adb devices пуст), по standing-паттерну фаз 1–6. BUILD-критерии закрыты автоматикой (assembleDebug + assembleRelease зелёные, check-no-internet OK, все структурные грепы UI-02/03/04 PASS, пин sound-default false PASS, zero-Java boundary: diff fbfd66a..HEAD по app/ = 3 объявленных XML, ни одного .java/.kt). Чек-лист при появлении устройства (полные шаги — 07-01-SUMMARY.md § Deferred Verification): (1) установка свежего app-debug.apk; (2) SC1 баллон (UI-02): нажатие буквы → баллон МГНОВЕННО на касании (down, не up), iOS-вид: light — белый с 1dp-тенью, dark — #6B6B6B, текст читаем, исчезает при отпускании; (3) SC1/SC4 края: баллон пятого ряда (ә — рисуется НАД клавиатурой в прозрачной зоне окна IME, не обрезан) и крайних колонок (левая й, правая ъ/э/һ — клампинг сдвигает внутрь); MIUI/HyperOS особо при наличии Xiaomi; (4) SC2 панель (UI-03): long-press а → панель с ә; НЕ отрывая пальца скольжение — подсветка следует, отпускание коммитит; уход в сторону — отмена; панель в iOS-палитре (серый фон, радиус 5dp, pressed-подсветка); (5) SC3 отклик (UI-04): вибрация + подсветка в момент КАСАНИЯ; звук по умолчанию НЕ звучит (default off — решение пользователя) — включить sound_on → звук на касании → выключить; выключить vibrate_on → вибрация пропала → включить; (6) SC4 smoke: пп. 2/4/5 в Telegram, Chrome WebView (keyCode 229), password-поле; MIUI при наличии (иначе пометить как не покрыто); (7) регрессии фаз 2–6: печать, глобус, shift, double-space, свайп-курсор — без аномалий. Финальная простановка чек-боксов UI-02/03/04 — после UAT.

- ⚠️ [Phase 8, plan 08-01] Task 4 on-device UAT deferred — устройство не подключено (adb devices пуст), по standing-паттерну фаз 1–7. BUILD-критерии закрыты автоматикой (assembleDebug + assembleRelease зелёные, check-no-internet OK, все 5 COMPAT-вердиктов запинованы fail-capable-грепами, extract mode мёртв во всех 5 config-вариантах, zero-Java boundary: diff d2ae619..HEAD по app/ = ровно values-land/config.xml, ни одного .java/.kt). Исполняемый чеклист — **08-UAT-MATRIX.md** (self-contained: 12 окружений × 8 сценариев, структурные механизмы CLOSED-STRUCTURAL со ссылками, все runtime-ячейки DEFERRED; подготовка/порядок прогона/спец-блоки E8 эмулятор API 35–36, E9 ландшафт без extract mode, E10 Direct Boot PIN, E11 MIUI, E12 One UI — внутри документа). Прогнать вместе с UAT-бандлом фаз 1–7; блок E8 исполним на эмуляторе отдельно, раньше телефона (A4). Финальная простановка чек-боксов COMPAT-01..05 в REQUIREMENTS.md — только после реального прогона.

- ⚠️ [Phase 10, plan 10-01] Task 5 on-device UAT deferred — устройство не подключено (adb devices пуст), по standing-паттерну фаз 1–9. BUILD-критерии закрыты автоматикой (assembleDebug + assembleRelease зелёные, check-no-internet OK, release-APK 728 719 байт ≤ 3 145 728, все структурные грепы SETUP-01/02 PASS, boundary 13ce533..HEAD по app/ = ровно 6 объявленных файлов, без gradle/ic_launcher/старых strings.xml). Чек-лист при появлении устройства (полные шаги — 10-01-SUMMARY.md § Deferred Verification): (1) SC3 главный — чистая установка: adb uninstall org.tatarkeyboard.ime.debug → adb install app-debug.apk → тап по иконке «Tatar Keyboard (dev)» на лаунчере → открывается ОНБОРДИНГ (не настройки), 2 карточки шагов не завершены; (2) шаг 1: «Включить клавиатуру» → системный экран → включить → назад → карточка шага 1 ✓ БЕЗ ручного обновления, шаг 2 активен; (3) шаг 2: «Выбрать клавиатуру» → системный picker → выбрать → после закрытия экран сам показывает «Готово» с подсказкой «ә» (рефреш по возврату фокуса — Pitfall 2); (4) финал SC3: Telegram → напечатать «ә» (пятый ряд) и отправить — от иконки до «ә» без подсказок; (5) «Готово»-кнопка: вернуться в онбординг (иконка) → сразу «Готово»-состояние (статусы живые) → «Открыть настройки» → SettingsActivity; старый диалог «Simple Keyboard is not enabled…» НЕ появляется нигде; (6) SC2-live (SETUP-02): Настройки → «Нажатие клавиши» → включить/выключить звук клика → отклик меняется сразу; выключить/включить вибрацию → отклик меняется без перезапуска IME; (7) SC4 smoke-матрица: печать в Telegram и Chrome/WebView (буквы/пятый ряд, глобус tt-ru-en, shift/caps, backspace-удержание, double-space→точка, свайп-курсор, long-press панель, баллон) + password-поле — без деградации; IME→настройки через ⚙-путь клавиатуры работает. Финальная простановка чек-боксов SETUP-01/02 в REQUIREMENTS.md — только после прогона. Прогнать вместе с UAT-бандлом фаз 1–9.

- ⚠️ [Phase 9, plan 09-01] Task 4 on-device TalkBack UAT deferred — устройство не подключено (adb devices пуст), по standing-паттерну фаз 1–8. BUILD-критерии закрыты автоматикой (assembleDebug + assembleRelease зелёные, check-no-internet OK, release-APK 718 695 байт ≤ 3 МБ, все линии A11Y запинованы fail-capable-грепами, zero-fork-Java boundary: diff 0a280ce..HEAD по app/ = ровно 4 файла — 2 .kt + 2 .xml, ни одного .java). Чек-лист при появлении устройства (установить свежий app-debug.apk, включить TalkBack: Настройки → Спец. возможности): (1) SC1/SC2 explore-by-touch: медленно вести палец по всем рядам татарской раскладки — TalkBack называет КАЖДУЮ клавишу; пятый ряд: ә → «татарская э», ө → «татарская о», ү → «татарская у», җ → «татарская ж», ң → «татарская н», һ → «татарская х»; shift → Ә → «Заглавная татарская э»; служебные: shift/«Клавиша верхнего регистра» (после тапа — «Верхний регистр включён», после двойного — «Caps Lock включён»), delete/«Удалить», пробел/«Пробел», enter в Telegram/«Отправить», ?123/«Символы» → АБВ/«Буквы», глобус/«Сменить язык»; ни одного «shift_key»/сырого имени. (2) SC3 — главный критерий: в Telegram двойным тапом (и, если работает, lift-to-type — A2) набрать «әни» и отправить: каждая буква печатается, слышен earcon клика, слово ушло в чат; проверить и русскую раскладку (глобус двойным тапом → «привет»). (3) Динамика: тап shift → описания букв меняются на заглавные; смена раскладки → узлы новой раскладки озвучиваются. (4) Password: поле пароля — клавиши озвучиваются по именам (описания не обскьюрены — осознанное решение), набранное IME сам не произносит. (5) SC4 — не-TalkBack смоук: ВЫКЛЮЧИТЬ TalkBack → обычная печать в Telegram и Chrome/WebView (буквы, backspace-удержание, double-space→точка, свайп-курсор, long-press панель, баллон) — без деградации. Финальная простановка чек-боксов A11Y-01/02 в REQUIREMENTS.md — только после прогона.

### Research pointers

- `.planning/research/SUMMARY.md` — конденсат; детали в `research/00`–`08` в корне.
- Фаза 1: фазовый ресерч по реальным исходникам Simple Keyboard (пофайлово не покрыт).
- Фаза 6: ресерч отрисовки KeyboardView форка — что переписывать, что переиспользовать.
- Фаза 8: актуальные known issues MIUI/HyperOS для IME.
- Фазы 4–5, 10: стандартные паттерны (research/01, research/08) — research-phase можно пропустить.

## Session Continuity

**Stopped at:** Completed 10-01-PLAN.md — Phase 10 complete-local (Task 5 device UAT deferred)
**Resume file:** None

**Next step:** Phase 10 — Онбординг и настройки.

Last session: 2026-07-18T21:59:44.720Z

---
*Last updated: 2026-07-18 — Phase 1 complete*

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 1 P01 | 35 min | 4 tasks | 7 files |
| Phase 1 P02 | 20 min | 2 of 4 tasks (2 deferred) | 3 files (+2 local secrets) |
| Phase 02 P01 | 9 min | 4 tasks | 8 files |
| Phase 03 P01 | 8 min | 4 of 5 tasks (Task 5 UAT deferred) | 12 files |
| Phase 04 P01 | 10 min | 2 of 3 tasks (Task 3 UAT deferred) | 3 files (zero code) |
| Phase 05 P01 | 8 min | 3 of 4 tasks (Task 4 UAT deferred) | 5 code files + planning |
| Phase 06 P01 | 15 min | 6 of 7 tasks (Task 7 UAT deferred) | 18 files (17 app + gradle.properties) |
| Phase 07 P01 | 25 min | 2 of 3 tasks (Task 3 UAT deferred) | 3 app files (zero Java) + planning |
| Phase 08 P01 | 12 min | 3 of 4 tasks (Task 4 UAT deferred) | 1 app file (1 line, zero Java) + planning |
| Phase 09 P01 | 12 min | 4 tasks | 6 files |
| Phase 10 P01 | 10 min | 4 of 5 tasks (Task 5 UAT deferred) tasks | 6 app files (4 new + 2 edits) + planning files |
