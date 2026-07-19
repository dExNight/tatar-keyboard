---
phase: 05-zhesty-i-multi-touch
goal: "Жестовая механика быстрой печати: двойной пробел — точка, свайп по пробелу — курсор, двупальцевый ввод без потери букв"
requirements: [INPUT-05, INPUT-06, INPUT-07]
status: passed
previous_status: human_needed
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18)
verified_at: 2026-07-18
verifier: gsd-verify-work (live re-check, не пересказ SUMMARY)
mechanical: pass
human_needed_reason: "Runtime-поведение (тайминг 1100 мс, revert, свайп поверх ә/җ, двупальцевая печать) проверяемо только on-device; устройство недоступно — standing deferral паттерн фаз 1–4, чек-лист в STATE.md Blockers"
---

# Phase 5 Verification — Жесты и multi-touch

**Verdict: mechanical PASS · human_needed (on-device UAT deferred, standing pattern)**

Все проверки выполнены заново на HEAD (не по записям SUMMARY). Сборка, приватность, boundary,
все структурные грепы INPUT-05..07, все 4 review-фикса и bookkeeping — зелёные. Единственный
открытый элемент — on-device UAT (Task 4), отложенный по принятой пользователем схеме фаз 1–4.

## 1. INPUT-05 — double-space → period (live-инспекция InputLogic.java)

| Проверка | Результат | Доказательство (file:line на HEAD) |
|---|---|---|
| Константа 1100 мс, не getDoubleTapTimeout | ✅ | `DOUBLE_SPACE_PERIOD_TIMEOUT = 1100` InputLogic.java:48; негативный греп getDoubleTapTimeout пуст |
| `tryDoubleSpacePeriod` с полным гейтом | ✅ | :352-369 — окно `now - mLastSpaceDownTime < TIMEOUT`, `!mIsPasswordField` (:355), пробел перед курсором (:356), `isLetterOrDigit(getCodePointBeforeCursor(1))` (:357) |
| Letter/digit-гейт fails closed | ✅ | `getCodePointBeforeCursor(int)` RichInputConnection.java:307-316: короткий/пустой кеш → `NOT_A_CODE` (-1) → `isLetterOrDigit(-1)` false → точки нет; суррогат-aware шаг (`isSupplementaryCodePoint ? 2 : 1`), cache-only, ноль IPC |
| Batch edit на триггере (F3) | ✅ | beginBatchEdit/endBatchEdit вокруг delete+commit :358-361 |
| Revert по backspace + batch edit | ✅ | handleBackspaceEvent :393-402: гейт `mJustDoubleSpaced` + текст-чек «пробел, перед ним точка» → deleteTextBeforeCursor(2) + commitText("  ") в batch edit |
| Always-on, без pref | ✅ | негативный греп `pref_double_space` по app/ пуст |

**Гигиена состояния — все сбросы присутствуют (live-грепы + чтение контекста):**

| Путь | Строка | Статус |
|---|---|---|
| `startInput()` (+ `onSubtypeChanged()` → startInput) | :80-81 | ✅ |
| `onTextInput()` — ".com"/paste (**F2**) | :112 | ✅ |
| `onUpdateSelection()` — unexpected-selection чек против getExpectedSelectionStart/End (**F1**) | :132-133 | ✅ |
| `handleConsumedEvent()` — combiner commit (**F2**) | :189 | ✅ |
| `handleNonSeparatorEvent()` | :319 | ✅ |
| Не-пробельная ветка `handleSeparatorEvent()` | :335 | ✅ |
| Backspace с selection | :390 | ✅ |
| Backspace без revert | :404 | ✅ |
| Оба выхода `tryDoubleSpacePeriod` | :362-363, :366-367 | ✅ |

## 2. INPUT-06 — свайп-курсор по умолчанию (live-грепы ×3 + негативные ×3)

| Место | pref_space_swipe | pref_delete_swipe |
|---|---|---|
| Settings.java:247/:251 | `getBoolean(PREF_SPACE_SWIPE, true)` ✅ | `false` ✅ (не тронут) |
| prefs_screen_preferences.xml | `defaultValue="true"` ✅ | `defaultValue="false"` ✅ |
| app_restrictions.xml | `defaultValue="true"` ✅ | `defaultValue="false"` ✅ |

Механика (PointerTracker/LatinIME) не изменена — подтверждено boundary-чеком (§4).

## 3. INPUT-07 — multi-touch commit (структурные доказательства, ноль правок)

- `releaseAllPointersOlderThan` — PointerTracker.java ✅ и PointerTrackerQueue.java ✅
- `onPhantomUpEvent` — PointerTracker.java ✅, PointerTrackerQueue.java (вкл. вызов) ✅
- `NonDistinctMultitouchHelper` — MainKeyboardView.java ✅ (fallback для non-distinct панелей)

## 4. Boundary — диф фазы механически ограничен

`git diff --name-only 8e4693e..HEAD` (live) = **ровно** 5 объявленных файлов кода + `.planning/*`:

```
app/.../latin/inputlogic/InputLogic.java
app/.../latin/RichInputConnection.java
app/.../latin/settings/Settings.java
app/src/main/res/xml/app_restrictions.xml
app/src/main/res/xml/prefs_screen_preferences.xml
.planning/{REQUIREMENTS,ROADMAP,STATE}.md + .planning/phases/05-*/(PLAN|SUMMARY|REVIEW|REVIEW-FIX|VALIDATION)
```

PointerTracker/PointerTrackerQueue/LatinIME/KeyboardState/InputAttributes нетронуты; новых `.kt`,
зависимостей, правок манифеста — нет. Prohibitions №1–3 плана: **verified**.

## 5. Сборка, приватность, бюджет

| Проверка | Результат |
|---|---|
| `./gradlew assembleDebug` | ✅ exit 0 (live-прогон при верификации) |
| `scripts/check-no-internet.sh` | ✅ exit 0 — Level 1 (манифест) + Level 2 (собранный APK); только `android.permission.VIBRATE` |
| APK размер | ✅ app-debug.apk = 1 923 243 байт (~1.83 МБ) ≤ 3 МБ (PERF-01 бюджет; официальный замер release — фаза 11) |

## 6. Review debt — 05-REVIEW.md закрыт (фиксы подтверждены на диске, не по отчёту)

| Finding | Severity | Фикс на HEAD | Подтверждение |
|---|---|---|---|
| F1 — flag не сбрасывался при движении курсора | major | ✅ | InputLogic.onUpdateSelection :126-135: сравнение с expected selection, сброс mJustDoubleSpaced + mLastSpaceDownTime при расхождении |
| F2 — onTextInput/handleConsumedEvent не сбрасывали flag | moderate | ✅ | :112 и :189 — `mJustDoubleSpaced = false` в обоих путях |
| F3 — триггер/revert без batch edit | minor | ✅ | beginBatchEdit/endBatchEdit в tryDoubleSpacePeriod (:358-361) и в revert (:397-400) |
| F4 — deleteTextBeforeCursor без isConnected | minor | ✅ | RichInputConnection.deleteTextBeforeCursor: `if (isConnected())` вокруг `mIC.deleteSurroundingText` |
| F5 — password-only гейт (info) | info | — | Не требовал действий по вердикту ревью (AOSP-consistent, MVP-acceptable) |

05-REVIEW-FIX.md: `status: all_fixed`, 4/4 — соответствует состоянию кода.

## 7. Bookkeeping

- REQUIREMENTS.md: аннотации под INPUT-05 (восстановление, always-on, 1100 мс, гейты, revert) и
  INPUT-06 (флип default ×3) ✅; чек-боксы INPUT-05..07 пустые до UAT ✅; Traceability —
  `Verifying (05-01: structural PASS; on-device UAT deferred)` ×3 ✅
- STATE.md: decision `[05-01]` ✅; backlog-пункт про перепутанные `android:title` в
  app_restrictions.xml (pre-existing upstream) ✅; Blockers несут отложенный UAT фазы 5 с полным
  чек-листом (7 пунктов вкл. password-гейт, ә/җ-свайп, smoke SC4, MIUI-оговорку) ✅
- ROADMAP.md: Progress фазы 5 = complete-local, UAT deferred ✅

## 8. Gaps → human_needed

Единственный gap — **on-device UAT (Task 4, checkpoint:human-verify)**: тайминг двойного пробела,
revert, password-гейт, свайп из коробки поверх ә/җ, двупальцевая печать «әни өй үрдәк җир»,
smoke SC4 (Telegram / WebView keyCode 229 / password). Автоматикой недостижимо (MotionEvent
реального тачскрина + реальные приложения; юнит-харнес отклонён решением фаз 2–4).
Отложено в STATE.md Blockers по standing-паттерну фаз 1–4, принятому пользователем.
Финальная простановка чек-боксов INPUT-05..07 — после UAT.

---
*Verified live: 2026-07-18*
