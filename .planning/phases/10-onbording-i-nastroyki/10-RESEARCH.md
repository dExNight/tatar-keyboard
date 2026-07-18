# Phase 10: Онбординг и настройки — Research

**Researched:** 2026-07-19
**Domain:** Android IME onboarding UX (2-step enable/select flow) + settings completeness audit
**Confidence:** HIGH (all findings from direct reads of the fork source at HEAD; AOSP SetupWizard used only as a design reference)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Онбординг = главная кодовая работа фазы.** Пакета `setup/` в форке НЕТ (rkkr вырезал AOSP SetupWizardActivity). Сейчас — только диалог «Simple Keyboard is not enabled…» в `SettingsActivity.onStart()`. Нужен онбординг-экран: 2 шага со статусами (шаг 1: IME enabled? → `ACTION_INPUT_METHOD_SETTINGS`; шаг 2: выбран текущим? → `showInputMethodPicker`), лаунчер-иконка ведёт туда (или в Settings при завершённом онбординге). AOSP SetupWizard — референс (Apache-2.0), но минимализм: один Activity, без анимаций/видео.
- **БЕЗ Compose в этой фазе.** Онбординг-экран — обычный View/XML. Compose тянет +1.5 МБ runtime и не лезет в бюджет APK 3 МБ. (Compose в Activity настроек допустим проектом в принципе, но здесь отклонён по бюджету.)
- **Правовое:** онбординг-тексты свои (ru + en base). Упоминание «Simple Keyboard» менять на наше имя ТОЛЬКО в наших новых строках; полный ребрендинг ~30 локализованных `setup_message` — вне фазы.

### Claude's Discretion
- Архитектура онбординг-экрана (1 Activity, статусы шагов по `onResume`/`onWindowFocusChanged`-проверкам, иконка приложения → онбординг пока не завершён; детект «выбран текущим» через `Settings.Secure.DEFAULT_INPUT_METHOD` или `InputMethodManager`).
- Тексты онбординга (ru + en base, лаконичные).
- Судьба лаунчер-иконки после онбординга (вести в `SettingsActivity` — стандарт).

### Deferred Ideas (OUT OF SCOPE)
- SC3 чистая установка (device) — отложенный UAT-бандл (принято, по standing-паттерну фаз 1–9).
- Полный ребрендинг ~30 локализованных `setup_message` — backlog / фаза 11.
- Compose — отклонён для этой фазы по бюджету APK (жёсткий лимит 3 МБ).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SETUP-01 | Онбординг-экран проводит через включение IME и выбор клавиатуры (два шага со статусами) | Detection API patterns (§ Detection APIs), SetupActivity design (§ Onboarding Activity Design), manifest LAUNCHER-переброс |
| SETUP-02 | Минимальные настройки: звук клика вкл/выкл, вибрация вкл/выкл | Settings audit (§ SETUP-02 Audit) — verdict: уже реализовано и живо-реактивно; работа = верификация + видимость |
</phase_requirements>

## Summary

Онбординг — единственная реальная новая кодовая работа фазы. Форк rkkr удалил AOSP `SetupWizardActivity`, оставив вместо гайда примитивный `AlertDialog` в `SettingsActivity.onStart()`: если IME не включён в системе, диалог с текстом `R.string.setup_message` предлагает открыть `ACTION_INPUT_METHOD_SETTINGS`. Это покрывает только шаг 1 (включение) и никак не ведёт через шаг 2 (выбор текущей клавиатурой). SETUP-01 требует явный экран из двух шагов со статусами — это надо построить. [VERIFIED: fork source `SettingsActivity.java:44-75`]

Правильная архитектура: новый `SetupActivity` (Kotlin — конвенция проекта для нового кода) как `MAIN/LAUNCHER`, `SettingsActivity` теряет LAUNCHER-фильтр но сохраняет запуск из IME и как «настройки после онбординга». Два статуса детектятся штатными системными API, уже присутствующими в форке: (1) IME enabled — `InputMethodManager.getEnabledInputMethodList()` содержит наш пакет (точно тот же паттерн, что уже в `SettingsActivity.isInputMethodOfThisImeEnabled()`); (2) IME current — сравнение с `Settings.Secure.getString(DEFAULT_INPUT_METHOD)`. Обновление статусов при возврате из системных настроек — через `onWindowFocusChanged`/`onResume` (AOSP SetupWizard использует `onWindowFocusChanged`). [VERIFIED: fork source; CITED: AOSP SetupWizard]

SETUP-02 — фактически уже готово и является в основном верификацией. `KeyPressSettingsFragment` управляет `vibrate_on`, `sound_on`, громкостью; вибро-тумблер авто-удаляется на устройствах без вибратора; `Settings` слушает `SharedPreferences` через `OnSharedPreferenceChangeListener` и `LatinIME.loadSettings()` толкает свежие значения в `AudioAndHapticFeedbackManager.onSettingsChanged()` — живая реакция подтверждена фазой 7. Экран достижим из корня `SettingsActivity` («Нажатие клавиши»). Ничего не «отсутствует»; задача — доказать это грепами + добавить UAT-строки. [VERIFIED: fork source `KeyPressSettingsFragment.java`, `Settings.java:96`, `SettingsValues.java:75-76`]

**Primary recommendation:** Построить один `SetupActivity` (Kotlin) + один layout XML (system Material, БЕЗ Compose) + строки (ru + en base) + переброс `MAIN/LAUNCHER` в манифесте. Детект статусов — `getEnabledInputMethodList()` (шаг 1) и `Settings.Secure.DEFAULT_INPUT_METHOD` (шаг 2), рефреш в `onWindowFocusChanged`. SETUP-02 — zero-code-verification грепами. APK-бюджет: +1 activity + layout ≈ единицы КБ, тривиально влезает.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Онбординг-экран (2 шага, статусы) | App Activity (не-IME процесс) | — | Отдельный процесс приложения; НЕ `directBootAware` (пользователь уже разблокировал устройство при первичной настройке); открывается из лаунчера |
| Детект «IME enabled» | App Activity → System (`InputMethodManager`) | — | Системное состояние читается через framework API; собственного хранения нет |
| Детект «IME current» | App Activity → System (`Settings.Secure`) | — | `DEFAULT_INPUT_METHOD` — глобальный secure-setting; только чтение |
| Запуск системного диалога включения | App Activity → System Settings intent | — | `ACTION_INPUT_METHOD_SETTINGS` — системный экран, наш Activity лишь стартует intent |
| Выбор текущей клавиатурой | App Activity → System picker | — | `InputMethodManager.showInputMethodPicker()` — системный floating picker |
| Тумблеры звук/вибро (SETUP-02) | App Settings (`PreferenceFragment`) | IME process (`AudioAndHapticFeedbackManager`) | Prefs пишутся в device-protected storage; IME-процесс перечитывает их через listener → live |
| Применение отклика при нажатии | IME process | — | `AudioAndHapticFeedbackManager` в процессе `LatinIME`, вне зоны этой фазы |

**Ключевой tier-инсайт:** онбординг живёт в процессе приложения (Activity), а НЕ в процессе IME. Детект статусов — чтение системного состояния, не собственное хранилище. SETUP-02 пересекает границу процессов: настройки пишет Activity-процесс, читает IME-процесс через `SharedPreferences`-listener — эта связка уже существует и подтверждена.

## Standard Stack

Фаза чисто платформенная — новых внешних зависимостей нет. Используются только Android framework API и то, что уже в форке.

### Core
| API / Class | Source | Purpose | Why Standard |
|-------------|--------|---------|--------------|
| `android.app.Activity` (classic View/XML) | Android framework | Хост онбординг-экрана | Легчайший вариант; ноль новых зависимостей; консистентно с `PreferenceActivity`-настройками форка [VERIFIED: fork source] |
| `InputMethodManager.getEnabledInputMethodList()` | Android framework | Детект «IME включён» | Точный паттерн уже используется форком в `SettingsActivity.isInputMethodOfThisImeEnabled()` [VERIFIED: fork source `SettingsActivity.java:80-90`] |
| `Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)` | Android framework | Детект «IME выбран текущим» | Стандартный AOSP-способ прочитать активный IME id [CITED: developer.android.com Settings.Secure] |
| `InputMethodManager.showInputMethodPicker()` | Android framework | Шаг 2 — выбор клавиатуры | Публичный API; форк уже вызывает его из IME (`LatinIME.showInputMethodPicker`, хотя там subtype-picker) [VERIFIED: fork source `LatinIME.java:643`] |
| `Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)` | Android framework | Шаг 1 — включение IME | Уже используется форком в диалоге `SettingsActivity.onStart()` [VERIFIED: fork source `SettingsActivity.java:61`] |
| `Activity.onWindowFocusChanged(boolean)` | Android framework | Рефреш статусов при возврате из системных экранов | AOSP SetupWizard использует именно этот колбэк для перепроверки статуса [CITED: AOSP SetupWizardActivity] |

### Supporting
| Item | Source | Purpose | When to Use |
|------|--------|---------|-------------|
| `@style/platformSettingsTheme` | fork `values/platform-theme.xml` | Тема Activity (Material.Light / Material night) | Переиспользовать для визуальной консистентности с `SettingsActivity` [VERIFIED: fork source] |
| WindowInsets listener (edge-to-edge на API R+) | fork `SettingsActivity.onCreate` | Корректные отступы под системные панели на API 30+ | Скопировать паттерн из `SettingsActivity.onCreate()` (targetSdk 37 = edge-to-edge принудителен) [VERIFIED: fork source `SettingsActivity.java:96-113`] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Classic View/XML Activity | Jetpack Compose | ОТКЛОНЕНО (locked): +~1.5 МБ runtime не влезает в бюджет 3 МБ; нет прецедента Compose в проекте |
| Отдельный `SetupActivity` | Расширить `SettingsActivity` онбордингом | Смешение ролей; `SettingsActivity` — `PreferenceActivity` (list-based), встроить туда 2-step карточный UI неестественно; чистое разделение лучше |
| `Settings.Secure.DEFAULT_INPUT_METHOD` (шаг 2) | Только `InputMethodManager` | У `InputMethodManager` нет прямого «is this the current IME» до новых API; secure-setting — переносимый способ с API 24 [CITED: AOSP] |

**Installation:** Нет. Ноль новых пакетов, ноль gradle-изменений. Android SDK 37 / minSdk 24 уже настроены (`app/build.gradle`). [VERIFIED: fork source `app/build.gradle:12-14`]

## Package Legitimacy Audit

**Not applicable.** Эта фаза не устанавливает внешних пакетов — только Android framework API и существующий код форка. Ноль изменений в `app/build.gradle` dependencies. Проверять на registry нечего.

## Architecture Patterns

### System Architecture Diagram

```
[Лаунчер: тап по иконке]
          │
          ▼
   ┌──────────────────┐        onResume / onWindowFocusChanged
   │   SetupActivity   │◄──────────────────────────────┐
   │  (MAIN/LAUNCHER)  │                                │
   └──────────────────┘                                │
          │                                             │
          │ читает 2 статуса                            │
          ├──────────────► InputMethodManager           │
          │                .getEnabledInputMethodList()  │ (возврат из
          │                = шаг 1 done?                 │  системного
          ├──────────────► Settings.Secure              │  экрана)
          │                .DEFAULT_INPUT_METHOD          │
          │                = шаг 2 done?                 │
          ▼                                              │
   ┌──────────────────────────────────────┐             │
   │ оба false → показать оба шага         │             │
   │ шаг1 done → шаг1 ✓, подсветить шаг2   │             │
   │ оба done  → «Готово» state            │             │
   └──────────────────────────────────────┘             │
          │                │                │            │
   [кнопка шаг1]     [кнопка шаг2]    [оба done]         │
          │                │                │            │
          ▼                ▼                ▼            │
  ACTION_INPUT_       showInputMethod   открыть          │
  METHOD_SETTINGS     Picker()          SettingsActivity │
  (системный экран)   (floating picker) или finish()     │
          │                │                             │
          └────────────────┴─────────────────────────────┘

   ┌──────────────────┐  запуск из IME (⚙ клавиша) / из SetupActivity после онбординга
   │  SettingsActivity │  (LAUNCHER-фильтр снят; exported=true сохраняется)
   │  → SettingsFragment (prefs.xml root)
   │     ├─ PreferencesSettingsFragment
   │     ├─ KeyPressSettingsFragment ── vibrate_on / sound_on / volume  ◄── SETUP-02
   │     └─ AppearanceSettingsFragment
   └──────────────────┘
              │ пишет SharedPreferences (device-protected)
              ▼
   ┌──────────────────┐  IME-процесс: Settings (OnSharedPreferenceChangeListener)
   │     LatinIME      │  → loadSettings() → AudioAndHapticFeedbackManager.onSettingsChanged()
   └──────────────────┘  = живой отклик звук/вибро
```

### Component Responsibilities

| Component | File (new/existing) | Responsibility |
|-----------|--------------------|----------------|
| `SetupActivity` | NEW `.../latin/setup/SetupActivity.kt` | Хост онбординга; читает 2 статуса, рендерит шаги, стартует системные intents, рефреш в `onWindowFocusChanged` |
| Layout | NEW `res/layout/setup_activity.xml` (+ `layout-v28` при необходимости insets) | Иконка/заголовок + 2 «карточки» шагов с номером/статусом/кнопкой + «Готово»-блок |
| Строки | `res/values/strings.xml` (en base) + `res/values-ru/strings.xml` | Новые `setup_step1_*`, `setup_step2_*`, `setup_done_*`, заголовок |
| Манифест | `AndroidManifest.xml` | `SetupActivity` = `MAIN/LAUNCHER`; `SettingsActivity` — снять LAUNCHER-фильтр (оставить `exported=true`) |
| `SettingsActivity` | existing | Настройки после онбординга + запуск из IME; **опционально** удалить/оставить `onStart()` not-enabled диалог (см. Pitfall 3) |
| `KeyPressSettingsFragment` | existing | SETUP-02 — уже готов, только верификация |

### Recommended Project Structure
```
app/src/main/
├── java/rkr/simplekeyboard/inputmethod/latin/setup/
│   └── SetupActivity.kt          # NEW — новый код Kotlin (конвенция проекта)
├── res/layout/
│   └── setup_activity.xml        # NEW
└── res/values/ , res/values-ru/
    └── strings.xml               # +новые onboarding-строки
```
Пакет `setup/` зеркалит структуру AOSP LatinIME (там был `latin/setup/`), логично и знакомо. [CITED: AOSP LatinIME structure]

### Pattern 1: Two-status detection (уже наполовину в форке)
**What:** Прочитать оба статуса при каждом появлении экрана.
**When to use:** В `onResume()` и/или `onWindowFocusChanged(hasFocus=true)` `SetupActivity`.
**Example:**
```kotlin
// Шаг 1 — IME включён? (точный паттерн из SettingsActivity.isInputMethodOfThisImeEnabled)
// Source: fork SettingsActivity.java:80-90
private fun isImeEnabled(): Boolean {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == packageName }
}

// Шаг 2 — IME выбран текущим?
// Source: developer.android.com Settings.Secure.DEFAULT_INPUT_METHOD
private fun isImeCurrent(): Boolean {
    val current = Settings.Secure.getString(
        contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
    ) ?: return false
    // current — component id вида "org.tatarkeyboard.ime/.latin.LatinIME"
    return current.startsWith("$packageName/")
}
```
> Замечание по id: сравнение по префиксу `"$packageName/"` устойчивее точного string-equals, т.к. в debug applicationId = `org.tatarkeyboard.ime.debug`, а компонент — `<package>/rkr.simplekeyboard.inputmethod.latin.LatinIME` (класс не переименован при форке). Точный id можно взять и из `InputMethodInfo.getId()` (форк использует `imi.getId()` в `RichInputMethodManager.java:647`), но префикс по пакету — самый простой корректный вариант. [VERIFIED: fork source]

### Pattern 2: Refresh-on-return (AOSP SetupWizard паттерн)
**What:** После того как пользователь ушёл в системный экран включения / picker и вернулся, статусы могли измениться — перепроверить и перерисовать.
**When to use:** `SetupActivity`.
**Example:**
```kotlin
// Source: AOSP SetupWizardActivity uses onWindowFocusChanged for status refresh
override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) updateStepStates()   // перечитать оба статуса, обновить ✓/подсветку/«Готово»
}
```
`onResume()` тоже сработает, но picker — floating window: `onWindowFocusChanged` ловит возврат фокуса надёжнее для picker-кейса. Использовать оба безопасно (updateStepStates идемпотентен). [CITED: AOSP SetupWizardActivity]

### Pattern 3: Launcher redirect после завершения
**What:** Когда оба шага done, иконка не должна снова показывать онбординг.
**When to use:** В начале `SetupActivity` или по кнопке «Готово».
**Recommendation:** Держать `SetupActivity` LAUNCHER-точкой; при обоих done показывать «Готово»-состояние с кнопкой «Открыть настройки» (→ `SettingsActivity`) и подсказкой «напечатайте ә в любом поле». НЕ авто-редиректить сразу (иначе пользователь не поймёт, что настройка завершена). Это стандарт и проще, чем динамически включать/выключать `LAUNCHER`-alias.

### Anti-Patterns to Avoid
- **Compose ради карточек:** дисквалифицировано бюджетом (locked). Обычные `LinearLayout`/`CardView`(не нужен даже CardView — можно фон-drawable) достаточно.
- **Хранить «онбординг завершён» в своём флаге:** избыточно и рассинхронизируется с реальным системным состоянием. Всегда читать живые статусы через API — источник истины у системы.
- **`directBootAware` на `SetupActivity`:** не нужно и вредно по смыслу — первичная настройка идёт на разблокированном устройстве. Не помечать.
- **Точный string-equals по фиксированному id:** ломается на debug-суффиксе applicationId. Сравнивать по префиксу пакета.
- **Дублирование системного экрана:** не пытаться рисовать список IME внутри приложения — только стартовать системные intents/picker.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Диалог выбора клавиатуры | Свой список IME + переключение | `InputMethodManager.showInputMethodPicker()` | Системный floating picker, единственно корректный способ сменить активный IME |
| Экран включения IME | Свой toggle-экран | `Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)` | Включение IME — прерогатива системного Settings; своего API нет |
| Детект «включён ли IME» | Парсинг Settings.Secure ENABLED_INPUT_METHODS вручную | `InputMethodManager.getEnabledInputMethodList()` | Готовый распарсенный список; форк уже так делает |
| Живой отклик звук/вибро на смену pref | Свой pref-listener в IME | Существующий `Settings` (`OnSharedPreferenceChangeListener`) → `loadSettings()` → `AudioAndHapticFeedbackManager.onSettingsChanged()` | Уже реализовано и подтверждено фазой 7 |
| Тумблеры звук/вибро UI | Новый экран | Существующий `KeyPressSettingsFragment` / `prefs_screen_key_press.xml` | Уже есть; авто-скрытие вибро без вибратора тоже уже есть |

**Key insight:** SETUP-01 — это на 90% оркестрация системных intents и чтение системного состояния, а не собственный UI-движок. SETUP-02 — уже построено предыдущими фазами; хендроллить нечего, только верифицировать.

## Runtime State Inventory

> Фаза преимущественно greenfield (новый Activity) + верификация. Ребрендинга/переименования строк за границами онбординга НЕТ (locked). Инвентарь ради edge-статусов:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — онбординг не хранит собственных данных; статусы читаются из системы каждый раз | None (verified: рекомендация Pattern 3 — не хранить флаг) |
| Live service config | `SharedPreferences` (device-protected) `vibrate_on`/`sound_on`/`pref_keypress_sound_volume` — читаются IME-процессом через listener | None — связка существует и жива (verified `Settings.java:96`, `LatinIME.java:281`) |
| OS-registered state | Манифест `MAIN/LAUNCHER` сейчас на `SettingsActivity` — переезжает на `SetupActivity`. После переустановки лаунчер перечитает манифест автоматически | Правка манифеста (code); на устройстве — переустановка/обновление |
| Secrets/env vars | None | None |
| Build artifacts | `applicationId` debug-суффикс `.debug` влияет на компонент-id при детекте шага 2 — учтено (сравнение по префиксу пакета) | None (учтено в Pattern 1) |

## Common Pitfalls

### Pitfall 1: Компонент-id на debug-сборке
**What goes wrong:** Детект шага 2 через точный equals с захардкоженным `"org.tatarkeyboard.ime/.latin.LatinIME"` вернёт false на debug-сборке (там applicationId = `...ime.debug`).
**Why it happens:** `applicationIdSuffix ".debug"` (решение 01-01), а имя класса IME при форке НЕ менялось (осталось `rkr.simplekeyboard.inputmethod.latin.LatinIME`).
**How to avoid:** Сравнивать `DEFAULT_INPUT_METHOD.startsWith("$packageName/")` — `packageName` в рантайме уже с суффиксом. Не хардкодить полный id.
**Warning signs:** Шаг 2 никогда не отмечается ✓ на debug несмотря на выбранную клавиатуру.

### Pitfall 2: Picker-возврат не обновляет статус
**What goes wrong:** После `showInputMethodPicker()` пользователь выбрал клавиатуру, вернулся — экран всё ещё показывает шаг 2 незавершённым.
**Why it happens:** Picker — floating окно; `onResume` мог не вызваться привычным образом.
**How to avoid:** Рефреш в `onWindowFocusChanged(hasFocus=true)` (AOSP-паттерн), плюс дублировать в `onResume`. Метод рефреша идемпотентен.
**Warning signs:** «Готово» не появляется без ручного перезахода на экран.

### Pitfall 3: Двойной not-enabled UX
**What goes wrong:** `SettingsActivity.onStart()` показывает свой старый `AlertDialog` «Simple Keyboard is not enabled…». Если пользователь попадёт в `SettingsActivity` до включения IME, он увидит и онбординг, и legacy-диалог — рассинхрон + старый бренд «Simple Keyboard».
**Why it happens:** Диалог остался от базы форка; теперь роль «провести через включение» берёт `SetupActivity`.
**How to avoid:** Решить в плане одно из: (а) удалить `onStart()`-диалог из `SettingsActivity` целиком (онбординг теперь единая точка); (б) оставить как безопасный fallback, но текст `setup_message` в наших строках уже деребрендить. Рекомендация: **(а) удалить** — чище, устраняет старый бренд, `SettingsActivity` становится чисто настройками. Это малый диф в существующем файле.
**Warning signs:** Диалог со словами «Simple Keyboard» всплывает поверх нового экрана.

### Pitfall 4: WindowInsets на API 35+ (targetSdk 37)
**What goes wrong:** На targetSdk 37 edge-to-edge принудителен; контент `SetupActivity` уедет под статус-бар/навигацию.
**Why it happens:** Android 15+ игнорирует opt-out из edge-to-edge при targetSdk ≥ 35.
**How to avoid:** Применить тот же `setOnApplyWindowInsetsListener`-паттерн, что в `SettingsActivity.onCreate()` (insets → margins), или `fitsSystemWindows` в корне layout (как в `layout-v28/input_view.xml` форка).
**Warning signs:** Заголовок/иконка перекрыты статус-баром на API 35/36.

### Pitfall 5: Ребрендинг-переползание
**What goes wrong:** Соблазн заменить «Simple Keyboard» во всех ~30 локализованных `setup_message` сразу.
**Why it happens:** Строки бросаются в глаза при работе.
**How to avoid:** Locked: полный ребрендинг — фаза 11/backlog. В этой фазе — только НОВЫЕ онбординг-строки, и ссылаться в них на `@string/english_ime_name` (= «Tatar Keyboard (dev)») вместо хардкода бренда.
**Warning signs:** Диф трогает `values-*/strings.xml` кроме `values/` и `values-ru/`.

## Code Examples

### Онбординг: статус-модель и рендер (набросок)
```kotlin
// Source: композиция из fork SettingsActivity.java + AOSP SetupWizard паттерна
class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setup_activity)
        // insets-паттерн как в SettingsActivity.onCreate (API R+)
        findViewById<View>(R.id.setup_step1_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        findViewById<View>(R.id.setup_step2_button).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
        findViewById<View>(R.id.setup_done_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java)); finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateStepStates()
    }
    override fun onResume() { super.onResume(); updateStepStates() }

    private fun updateStepStates() {
        val enabled = isImeEnabled()
        val current = isImeCurrent()
        // step1 ✓ if enabled; step2 доступен когда enabled; done-блок когда enabled && current
    }
}
```

### Манифест: переброс LAUNCHER
```xml
<!-- SetupActivity — новая точка входа -->
<activity
    android:name=".latin.setup.SetupActivity"
    android:label="@string/english_ime_name"
    android:exported="true"
    android:theme="@style/platformSettingsTheme">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<!-- SettingsActivity — LAUNCHER-фильтр СНЯТ, exported сохранён (запуск из IME) -->
<activity
    android:name=".latin.settings.SettingsActivity"
    android:label="@string/settings_screen_title_or_english_ime_name"
    android:exported="true"
    android:theme="@style/platformSettingsTheme" />
```
> `SettingsActivity` остаётся `exported="true"`: система запускает его из системного экрана «Настройки клавиатуры» и форк открывает его из IME. Проверить: как IME открывает настройки (ключ ⚙ / settings intent) — грепнуть при планировании, чтобы гарантировать неразрыв. [VERIFIED: fork source манифест]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| AOSP `SetupWizardActivity` (многошаговый визард с видео/анимациями) | Минималистичный 1-Activity онбординг | Наше решение (locked) | Меньше кода/APK, достаточно для 2 шагов |
| rkkr `AlertDialog` в `onStart()` | Явный 2-step экран со статусами | Эта фаза | SETUP-01 выполнено; лучший UX первичной настройки |
| Compose-based settings UI | Classic View/XML | Locked (бюджет) | Ноль дополнительного APK |

**Deprecated/outdated:**
- Legacy not-enabled `AlertDialog` в `SettingsActivity.onStart()`: заменяется онбордингом (см. Pitfall 3 — рекомендация удалить).
- `ACCESSIBILITY_SPEAK_PASSWORD` и пр. — не касается этой фазы.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | AOSP SetupWizard использует `onWindowFocusChanged` для рефреша статуса | Patterns, Sources | Низкий — паттерн валиден сам по себе (picker=floating window); даже если AOSP делает иначе, `onWindowFocusChanged`+`onResume` корректны |
| A2 | Компонент-класс IME при форке НЕ переименован (`rkr.simplekeyboard...LatinIME`) — подтверждено манифестом (`.latin.LatinIME` под package `rkr.simplekeyboard.inputmethod`), но полный id зависит от `applicationId` рантайма | Pitfall 1, Pattern 1 | Низкий — сравнение по префиксу пакета устойчиво к этому |
| A3 | Удаление `onStart()`-диалога из `SettingsActivity` не сломает других путей входа | Pitfall 3 | Средний — план должен грепнуть, кто ещё зависит от того, что `SettingsActivity` показывает enable-диалог; онбординг перекрывает функцию, но проверить надо |

**Примечание:** ключевые технические факты (детект-API, живость SETUP-02, структура настроек, манифест) — VERIFIED по исходникам, не ASSUMED. Выше только реально неподтверждённое.

## Open Questions

1. **Судьба legacy not-enabled диалога в `SettingsActivity.onStart()`**
   - Что известно: он есть, показывает старый бренд, дублирует роль онбординга.
   - Что неясно: удалить полностью или оставить как fallback.
   - Рекомендация: удалить (Pitfall 3, вариант а) — план должен грепнуть зависимости перед удалением.

2. **Лаунчер-иконка (`ic_launcher`) — всё ещё дефолт Simple Keyboard?**
   - Что известно: `@drawable/ic_launcher` (+ adaptive v26 foreground/monochrome, `ic_launcher_background=#ECEFF1`). Это ресурсы базы форка.
   - Что неясно: своя ли это иконка или наследие Simple Keyboard.
   - Рекомендация: замена иконки — вне scope (ребрендинг = фаза 11/backlog). Отметить как open question, НЕ трогать в фазе 10.

3. **Как именно IME открывает `SettingsActivity` (settings-ключ / intent)?**
   - Что известно: `SettingsActivity` останется `exported=true`.
   - Что неясно: точный путь запуска из IME (грепнуть при планировании).
   - Рекомендация: план верифицирует неразрыв запуска настроек из клавиатуры после снятия LAUNCHER-фильтра.

## Environment Availability

**SKIPPED** — фаза чисто код/ресурсы (Kotlin Activity + XML + строки + манифест). Внешних инструментов/сервисов сверх уже настроенного Android SDK 37 / Gradle 9.6.0 / minSdk 24 (фаза 1) не требуется. [VERIFIED: fork source `app/build.gradle`]

## Validation Architecture

> `nyquist_validation: true` в config.json — секция включена.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Нет unit-фреймворка в проекте (фазы 1–9 верифицировались grep-структурно + сборкой + отложенным device-UAT) |
| Config file | none — паттерн проекта: `assembleDebug`/`assembleRelease` + fail-capable грепы + `scripts/check-no-internet.sh` |
| Quick run command | `./gradlew assembleDebug` |
| Full suite command | `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SETUP-01 | `SetupActivity` = MAIN/LAUNCHER; `SettingsActivity` LAUNCHER снят | structural grep | `grep -A6 'SetupActivity' app/src/main/AndroidManifest.xml \| grep LAUNCHER` + греп что у SettingsActivity фильтра нет | ❌ Wave 0 (грепы пишутся в плане) |
| SETUP-01 | Детект шага 1 через `getEnabledInputMethodList` | structural grep | `grep -rn 'getEnabledInputMethodList\|enabledInputMethodList' app/src/main/java/.../setup/` | ❌ Wave 0 |
| SETUP-01 | Детект шага 2 через `DEFAULT_INPUT_METHOD` | structural grep | `grep -rn 'DEFAULT_INPUT_METHOD' app/src/main/java/.../setup/` | ❌ Wave 0 |
| SETUP-01 | Шаги стартуют правильные intents | structural grep | `grep -rn 'ACTION_INPUT_METHOD_SETTINGS\|showInputMethodPicker' app/src/main/java/.../setup/` | ❌ Wave 0 |
| SETUP-01 | Сборка не сломана | build | `./gradlew assembleDebug` | ✅ Gradle есть |
| SETUP-02 | Тумблеры `vibrate_on`/`sound_on` в prefs | structural grep | `grep -n 'vibrate_on\|sound_on' app/src/main/res/xml/prefs_screen_key_press.xml` | ✅ exists |
| SETUP-02 | Живая реакция IME на смену pref | structural grep | `grep -n 'onSettingsChanged' app/src/main/java/.../AudioAndHapticFeedbackManager.java` + `Settings.java:96 register` | ✅ exists |
| SETUP-02 | Вибро-тумблер скрыт без вибратора | structural grep | `grep -n 'hasVibrator\|removePreference' app/src/main/java/.../KeyPressSettingsFragment.java` | ✅ exists |
| SETUP-01 SC3 | Чистая установка → от иконки до «ә» без подсказок | manual (device) | — | DEFERRED (UAT-бандл) |
| SETUP-01/02 SC4 | Smoke-матрица не деградировала | manual (device) | — | DEFERRED |

### Sampling Rate
- **Per task commit:** `./gradlew assembleDebug`
- **Per wave merge:** `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh` + структурные грепы SETUP-01/02
- **Phase gate:** обе сборки зелёные, все структурные грепы PASS, APK ≤ 3 МБ; device-UAT (SC3/SC4) deferred по standing-паттерну фаз 1–9.

### Wave 0 Gaps
- [ ] Структурные fail-capable грепы SETUP-01 (манифест LAUNCHER-переброс, detect-API, intents) — пишутся в плане как verification.
- [ ] Структурные грепы SETUP-02 (тумблеры + живость + hasVibrator) — verification-only, код уже есть.
- [ ] Нет нужды в новом test-фреймворке — консистентно с фазами 1–9 (grep + build + deferred device-UAT).

## Security Domain

> `security_enforcement` не задан явно в config → трактуется как включён. Секция минимальна: фаза не вводит сеть/auth/крипто.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Онбординг не аутентифицирует; только читает системный IME-статус |
| V3 Session Management | no | Нет сессий |
| V4 Access Control | yes (лёгкий) | `SetupActivity` `exported=true` — намеренно (LAUNCHER); не принимает чувствительных extras, только стартует системные intents. `SettingsActivity` `exported=true` сохраняется (системный вход) |
| V5 Input Validation | no | Ввода пользователя нет; читаются только системные значения |
| V6 Cryptography | no | Нет крипто |
| V-Privacy (проектная дисциплина) | yes | БЕЗ INTERNET-разрешения (CI-гейт фазы 1 остаётся); онбординг не шлёт данных, не логирует PII |

### Known Threat Patterns for Android IME onboarding

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Exported Activity принимает вредоносный intent | Elevation/Tampering | `SetupActivity` игнорирует входящие extras; логика только на чтение системного состояния + старт фиксированных системных intents |
| Утечка данных через сеть | Information Disclosure | Структурно исключено: нет INTERNET-разрешения (PERF-04 CI-гейт), онбординг не делает сетевых вызовов |
| Ложный статус «настроено» вводит в заблуждение | Spoofing | Статусы читаются из системы (`InputMethodManager`/`Settings.Secure`), не из своего хранилища — подделать нечем |

**Итог по безопасности:** фаза не расширяет attack surface значимо. Единственный новый exported-компонент (`SetupActivity`) не принимает недоверенного ввода и не хранит секретов. Приватность-инвариант (no-INTERNET) сохраняется, CI-гейт продолжает действовать.

## Plan Shape (рекомендация планнеру)

Ориентировочно 1 план, ~4–5 задач (соло-новичок, малые фазы):

1. **SetupActivity + layout + строки** — новый `SetupActivity.kt` (детект 2 статусов, старт intents/picker, рефреш `onWindowFocusChanged`+`onResume`, insets-паттерн), `setup_activity.xml` (иконка/заголовок + 2 карточки шагов со статусом ✓ + «Готово»-блок), строки `values/` + `values-ru/` (ссылка на `@string/english_ime_name`, не хардкод бренда). Тема `platformSettingsTheme`.
2. **Манифест: переброс LAUNCHER** — `SetupActivity` → MAIN/LAUNCHER; снять LAUNCHER-фильтр с `SettingsActivity` (оставить `exported=true`). Опционально: удалить legacy not-enabled диалог из `SettingsActivity.onStart()` (Pitfall 3) — грепнуть зависимости перед удалением.
3. **SETUP-02 verification** — zero-code: fail-capable грепы (тумблеры в prefs, `onSettingsChanged`-живость, `hasVibrator`-скрытие, достижимость экрана «Нажатие клавиши» из корня). Подтвердить, ничего не добавляя.
4. **Структурная верификация SETUP-01 + сборка** — грепы манифеста/detect-API/intents; `assembleDebug`+`assembleRelease` зелёные; `check-no-internet.sh` OK; APK ≤ 3 МБ (новый Activity+layout ≈ единицы КБ — тривиально).
5. **Deferred device-UAT (SC3/SC4)** — чек-лист в SUMMARY по standing-паттерну фаз 1–9 (устройство не подключено): чистая установка → иконка открывает онбординг → шаг1 включить → шаг2 выбрать → «Готово» → напечатать «ә» в мессенджере; smoke-матрица не деградировала; тумблеры звук/вибро меняют отклик вживую.

**APK-бюджет:** +1 Activity (Kotlin, компилируется в существующий dex) + 1 layout + ~10 строк × 2 локали ≈ единицы КБ. Release-APK фазы 9 = 718 695 байт (≤ 3 МБ) с огромным запасом. Риск превышения — нулевой (Compose отклонён именно ради этого).

## Sources

### Primary (HIGH confidence)
- Fork source @ HEAD — `AndroidManifest.xml` (LAUNCHER на SettingsActivity, VIBRATE-only permission, directBootAware сервис)
- `SettingsActivity.java:44-135` — legacy not-enabled диалог, `isInputMethodOfThisImeEnabled()` (`getEnabledInputMethodList` паттерн), insets-listener, fragment-роутинг
- `SettingsFragment.java` + `res/xml/prefs.xml` — корень настроек: Preferences / Key press / Appearance / privacy / license
- `KeyPressSettingsFragment.java` + `res/xml/prefs_screen_key_press.xml` — SETUP-02: `vibrate_on`/`sound_on`/volume, `hasVibrator`→`removePreference`
- `Settings.java:44,96,108` — `OnSharedPreferenceChangeListener`, register/unregister, `onSharedPreferenceChanged`→`loadSettings`
- `SettingsValues.java:41-42,75-76` — `mVibrateOn`/`mSoundOn` из prefs
- `LatinIME.java:262-281,643` — `AudioAndHapticFeedbackManager.init`+`onSettingsChanged` (живость), `showInputMethodPicker`
- `RichInputMethodManager.java:647` — `imi.getId()` прецедент
- `app/build.gradle:12-14` — compileSdk/targetSdk 37, minSdk 24
- `res/values/strings-appname.xml:22` — `english_ime_name = "Tatar Keyboard (dev)"`; `values-ru/strings.xml:58` — локализованный `setup_message` (старый бренд)
- `config-*/config-per-form-factor.xml` — `config_default_sound_enabled` (false на телефонах), `config_default_vibration_enabled=true`

### Secondary (MEDIUM confidence)
- developer.android.com — `Settings.Secure.DEFAULT_INPUT_METHOD`, `InputMethodManager.showInputMethodPicker()` (стандартные публичные API)

### Tertiary (LOW confidence)
- AOSP LatinIME `SetupWizardActivity` — референс паттерна `onWindowFocusChanged`-рефреша и структуры `latin/setup/` (по памяти, не открыт в этой сессии — см. A1)

## Metadata

**Confidence breakdown:**
- Standard stack (framework API): HIGH — все detect-API и intents уже присутствуют/используются в форке, проверены по исходникам
- Architecture (SetupActivity design): HIGH — прямая композиция существующих паттернов форка + минималистичный AOSP-референс
- SETUP-02 verdict: HIGH — код существует и живость подтверждена фазой 7 + прочитан в этой сессии
- Pitfalls: HIGH (Pitfall 1,3,4 из прочитанного кода/решений STATE) / MEDIUM (Pitfall 2 — picker-focus поведение)

**Research date:** 2026-07-19
**Valid until:** ~2026-08-18 (стабильная платформенная зона; 30 дней)
