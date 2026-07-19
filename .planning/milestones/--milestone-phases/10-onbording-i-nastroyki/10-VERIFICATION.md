---
phase: 10
status: passed
verified: 2026-07-18
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18)
requirements: [SETUP-01, SETUP-02]
---

# Phase 10 Verification — Онбординг и настройки

**Phase goal:** Новый пользователь доходит от установки до татарского ввода без инструкций; звук и вибрация управляются из настроек.
**Requirements:** SETUP-01, SETUP-02
**Verified:** 2026-07-18 (live re-verification against working tree + fresh builds)
**Verdict: ✅ PASSED** (goal achieved structurally; on-device UAT accepted-deferred по standing-паттерну фаз 1–9)

---

## Goal Achievement

### SETUP-01 — Онбординг-экран: два шага со статусами → ✅ VERIFIED (structural)

**Observable truth 1: лаунчер-иконка ведёт в онбординг, не в настройки.**
Проверено живьём в `AndroidManifest.xml`:

- `SetupActivity` (`.latin.setup.SetupActivity`) объявлен с intent-filter MAIN + LAUNCHER (`AndroidManifest.xml:43-52`), блок стоит ПЕРЕД SettingsActivity — как объявлено.
- `grep -c 'category.LAUNCHER'` по манифесту = **1** — единственный LAUNCHER в приложении, и он у SetupActivity.
- Блок SettingsActivity (`AndroidManifest.xml:54-58`): `exported="true"` сохранён, intent-filter отсутствует полностью — LAUNCHER снят.
- `directBootAware` на SetupActivity отсутствует (только сервис LatinIME его несёт) — verified.
- Разрешения INTERNET в манифесте нет (только VIBRATE).

**Observable truth 2: статусы читаются живьём из системы.**
Проверено в `SetupActivity.kt` (162 строки, живой файл):

- `isImeEnabled()` (:107-115) — `imm.enabledInputMethodList.any { it.packageName == packageName }`, обёрнут в `try/catch (Exception)` → `false` + `Log.e(TAG, …)` — **фикс L2 на месте**, с companion object TAG (:54-56).
- `isImeCurrent()` (:122-126) — `Settings.Secure.getString(contentResolver, DEFAULT_INPUT_METHOD) ?: return false`, затем `startsWith("$packageName/")` — префикс с завершающим слэшем, устойчив к debug-суффиксу и к кросс-вариантному false positive.
- `updateStepStates()` (:132-161) — идемпотентный рендер трёх состояний; done-блок `visibility` по `current`.
- Собственного флага завершения нет: греп `SharedPreferences|getPreferences` по SetupActivity.kt — **0 вхождений** — источник истины система.
- Intent-extras не читаются: греп `getStringExtra|getIntExtra|getBooleanExtra|getParcelableExtra` — **0 вхождений** (T-10-01 держится).

**Observable truth 3: рефреш по возврату из системных экранов.**
- `onResume()` (:91-94) → `updateStepStates()`; `onWindowFocusChanged(hasFocus)` (:96-99) → при `true` тот же вызов — оба колбэка на месте (picker = floating window, Pitfall 2 закрыт).

**Observable truth 4: кнопки открывают только системные экраны.**
- Шаг 1 (:71-80): `startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))` в `try/catch (ActivityNotFoundException)` → Toast `R.string.setup_error_no_settings` — **фикс M1 на месте**; `.addFlags(FLAG_ACTIVITY_NEW_TASK)` отсутствует — **фикс L3 на месте**.
- Шаг 2 (:81-84): `InputMethodManager.showInputMethodPicker()`.
- Done (:85-88): класс-интент → `SettingsActivity` + `finish()`.

**Observable truth 5: a11y статус-марок (фикс L1).**
- В `updateStepStates()` оба статус-TextView получают `contentDescription` = `setup_step_status_done` / `setup_step_status_pending` (:142-144, :151-153); alpha-затемнение помечено комментарием как декоративное — **фикс L1 на месте**.

**Observable truth 6: IME→настройки путь цел после снятия LAUNCHER.**
- `LatinIME.java:881` — `intent.setClass(LatinIME.this, SettingsActivity.class)` — класс-интент, LAUNCHER не участвует; `exported="true"` у SettingsActivity сохранён (нужен и для `settingsActivity` в method.xml).

**Observable truth 7: legacy-диалог старого бренда удалён.**
- `SettingsActivity.java` (87 строк): `onStart`, `AlertDialog`, `isInputMethodOfThisImeEnabled`, `setup_message` — **0 вхождений в файле**; `isInputMethodOfThisImeEnabled` — **0 вхождений во всём app/src/main/java** (dangling references отсутствуют).
- Живая часть цела: `onCreate` (insets API R+), `onOptionsItemSelected`, `getIntent`, `isValidFragment` — на месте.
- Ресурс `setup_message` остался в `values/strings.xml` нетронутым (чистка = фаза 11, I1 deferred).

### SETUP-02 — Звук и вибрация из настроек → ✅ VERIFIED (structural, zero-code)

- `prefs_screen_key_press.xml`: `vibrate_on` + `sound_on` + `pref_keypress_sound_volume` — 6 вхождений (ключи + зависимости) ✓
- Достижимость: `KeyPressSettingsFragment` в `res/xml/prefs.xml` (экран «Нажатие клавиши» из корня настроек) ✓
- Авто-скрытие вибро: `hasVibrator` + `removePreference` в `KeyPressSettingsFragment.java` ✓
- Живой отклик: `registerOnSharedPreferenceChangeListener` в `Settings.java` → `onSettingsChanged` в `LatinIME.java` → `onSettingsChanged` в `AudioAndHapticFeedbackManager.java` — вся цепочка на месте ✓

---

## Builds & Invariants (fresh run, this session)

| Gate | Result |
|---|---|
| `./gradlew assembleDebug assembleRelease` | ✅ BUILD SUCCESSFUL |
| `bash scripts/check-no-internet.sh` | ✅ Level 1 (source manifest) + Level 2 (built APK) OK |
| Release APK size | ✅ **730 783 байта** ≤ 3 145 728 (23% бюджета; +2 КБ к 728 719 из SUMMARY — вклад review-фиксов M1/L1: строки + try/catch) |
| Новые зависимости / gradle-правки | ✅ ноль (boundary) |

## Strings parity (en base / ru overlay)

`values/strings-setup.xml` ↔ `values-ru/strings-setup.xml`:

- 10 переводимых строк присутствуют в обеих локалях: subtitle, step1/2 title+instruction+button, done title/hint/button ✓
- Review-fix строки в паритете: `setup_step_status_done`/`setup_step_status_pending` («Step done»/«Шаг выполнен»), `setup_error_no_settings` — en + ru ✓
- ru: `translatable="false"` марки (1/2/✓) корректно только в base ✓
- «ә» в done_hint обеих локалей; старого бренда в новых файлах нет; заголовок — `@string/english_ime_name` через layout ✓

## Boundary check (diff 0083715..HEAD -- app/)

Дифф фазы (task-коммиты 85b19fe/a310509/d98bed6/d9ee3b8 + review-fix 79ac956) покрывает ровно объявленный набор:
`SetupActivity.kt` (new), `layout/setup_activity.xml` (new), `values/strings-setup.xml` (new), `values-ru/strings-setup.xml` (new), `AndroidManifest.xml` (mod), `SettingsActivity.java` (mod −58). Review-fix 79ac956 трогает только SetupActivity.kt + оба strings-setup.xml — внутри того же набора. `build.gradle`, `ic_launcher`/mipmap, `values/strings.xml`, `values-ru/strings.xml` — не в диффе ✓

## Bookkeeping

- REQUIREMENTS.md: аннотации под SETUP-01 и SETUP-02 (2026-07-19), чек-боксы **не** проставлены (`- [ ]` ×2 — корректно до device-UAT), Traceability ×2 = `Verifying (10-01: structural PASS; on-device UAT deferred)` ✓
- STATE.md: decision `[10-01]` (полная запись: детект-паттерны, LAUNCHER-переезд, удаление legacy-диалога, zero-code SETUP-02, boundary); backlog ic_launcher (open-Q2) зафиксирован; Blockers — deferred-UAT записи по standing-паттерну ✓
- ROADMAP.md: Phase 10 — `1/1 plans executed`, план отмечен ✓

## Review debt

| Finding | Status |
|---|---|
| 10-REVIEW.md verdict | ✅ Approve (1 medium, 3 low, 4 info; no blockers) |
| M1 — unguarded `startActivity` | ✅ Fixed (79ac956) — verified in source :74-79 |
| L1 — status marks a11y | ✅ Fixed — verified in source :142-144, :151-153 |
| L2 — IMM guard dropped | ✅ Fixed — verified in source :109-114 |
| L3 — `FLAG_ACTIVITY_NEW_TASK` | ✅ Fixed — verified absent from settings intent |
| I1 — dead `setup_message` ×~45 локалей | 📋 Deferred to phase 11 rebrand (documented in 10-REVIEW-FIX.md) |
| I2–I4 | Informational, no action required |

---

## Deferred (accepted)

**On-device UAT — Task 5 (checkpoint:human-verify)** — устройство недоступно (adb devices пуст); отложено по standing-паттерну фаз 1–9, принятому пользователем, self-contained чек-лист в STATE.md Blockers:

- **SC3 (главный критерий цели):** чистая установка → иконка → онбординг → шаг 1 → шаг 2 → «Готово» → «ә» в Telegram без единой инструкции.
- **SC2-live:** тумблеры звук/вибро реально меняют отклик без перезапуска IME.
- **SC4:** smoke-матрица не деградировала.
- Плюс review-точки для UAT: рефреш статуса шага 2 после floating picker (A1), отсутствие призрака старой иконки после install -r (A2), TalkBack-озвучка статус-марок (L1 — присоединено к TalkBack-бандлу фазы 9).

Эти пункты определяют **окончательное** закрытие чек-боксов SETUP-01/02; структурная часть цели фазы доказана полностью.

---

## Conclusion

**Phase 10 goal: ACHIEVED (structural) / device confirmation deferred-accepted.**

Всё, что можно доказать без устройства, доказано живой верификацией кода и свежими сборками этой сессии: единственная LAUNCHER-точка ведёт в 2-шаговый онбординг с live-детектом из системы (без собственного флага), кнопки открывают только системные экраны с guard'ами, рефреш идемпотентен по обоим колбэкам, legacy-диалог удалён без dangling references, IME→настройки цел, SETUP-02 существует и живо-реактивен, все 4 review-фикса (M1/L1/L2/L3) реально в исходниках, обе сборки + no-internet зелёные, APK 730 783 байта ≤ 3 МБ. Фаза готова к переходу на Phase 11.

---
*Verified: 2026-07-18 — live re-verification (source + builds), not document-trust*
