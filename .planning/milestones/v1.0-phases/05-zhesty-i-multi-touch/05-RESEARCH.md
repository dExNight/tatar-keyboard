# Phase 5: Жесты и multi-touch — Research

**Researched:** 2026-07-18
**Source audited:** `app/src/main/java/rkr/simplekeyboard/inputmethod/` (fork Simple Keyboard, base b40c70d9)

## Вердикты

| Req | Вердикт | Кратко |
|-----|---------|--------|
| INPUT-05 двойной пробел → «. » | **GAP** | Механика полностью вырезана rkkr — единственная кодовая работа фазы |
| INPUT-06 свайп по пробелу → курсор | **WORKS (code) / GAP (default)** | Реализовано и функционально, но `pref_space_swipe` default **false** — рекомендую флип default на true |
| INPUT-07 multi-touch commit | **WORKS** | AOSP PointerTrackerQueue + phantom-up на месте, ноль работы |

---

## INPUT-05: Double-space → period — GAP (подтверждён)

### Доказательства отсутствия

Исчерпывающий поиск по `app/src/main/java` (`DoubleSpace`, `double_space`, `mSpaceState`, `maybeDouble`, `PERIOD`, `handleSeparator`):

- Единственные совпадения `PERIOD`: `Constants.CODE_PERIOD` (Constants.java:78) и TLD-обработка `.com` (`InputLogic.performSpecificTldProcessingOnTextInput`, InputLogic.java:467-479) — не относится.
- `mSpaceState` встречается только в **устаревшем комментарии** InputLogic.java:397-398 («Warning: this depends on mSpaceState…») — само поле удалено, рудимент AOSP.
- `MSG_DOUBLE_TAP_SHIFT_KEY` в TimerHandler.java:34 — это double-tap **shift** (caps lock, фаза 4), не пробел.
- Путь пробела: `onCodeInput` → `handleNonFunctionalEvent` → `handleNonSpecialCharacterEvent` (InputLogic.java:276) → пробел — word separator → `handleSeparatorEvent` (InputLogic.java:301-305): просто `sendKeyCodePoint` + shift update. Никакой логики двойного тапа.

### Дизайн восстановления (минимальный дифф, AOSP-паттерн)

**Точка врезки:** `InputLogic.handleSeparatorEvent` (InputLogic.java:301) — перед `sendKeyCodePoint(event.mCodePoint)` вызвать `if (codePoint == Constants.CODE_SPACE && tryDoubleSpacePeriod()) return;` (плюс shift update внутри). Альтернативно отдельный приватный метод, вызываемый из `handleSeparatorEvent` только для `CODE_SPACE`.

**Состояние в InputLogic (2 поля):**
```java
private long mLastSpaceDownTime;      // SystemClock.uptimeMillis() последнего закоммиченного пробела
private boolean mJustDoubleSpaced;    // для revert по backspace
```
`SystemClock` уже импортирован в InputLogic (строка 24).

**Псевдокод:**
```
tryDoubleSpacePeriod():
  now = SystemClock.uptimeMillis()
  ok = (now - mLastSpaceDownTime < DOUBLE_SPACE_PERIOD_TIMEOUT)
       && !settingsValues.mInputAttributes.mIsPasswordField
       && cpBefore == ' '                       // getCodePointBeforeCursor()
       && isLetterOrDigitBefore(offset=1)       // символ ПЕРЕД тем пробелом — буква/цифра
  if ok:
      mConnection.deleteTextBeforeCursor(1)     // убрать первый пробел
      mConnection.commitText(". ", 1)
      mJustDoubleSpaced = true
      mLastSpaceDownTime = 0
      return true
  mLastSpaceDownTime = now; mJustDoubleSpaced = false
  return false
```
Проверка «буква/цифра перед пробелом» требует заглянуть на 2-й кодпоинт от курсора: у `RichInputConnection` есть только `getCodePointBeforeCursor()` (RichInputConnection.java:295-299, читает приватный кеш `mTextBeforeCursor`). Добавить крошечный аксессор, напр. `getCodePointBeforeCursor(int offsetCodePoints)` или `hasLetterBeforeLastSpace()` — читает тот же кеш, ноль IPC, ноль аллокаций. AOSP использует ту же проверку (`canBeFollowedByDoubleSpacePeriod`: буква/цифра/закр. кавычка/скобка; для MVP достаточно `Character.isLetterOrDigit`, можно расширить `)»"'` по вкусу).

**Таймаут:** AOSP `config_double_space_period_timeout` = **1100 мс**. `ViewConfiguration.getDoubleTapTimeout()` (~300 мс) слишком короток для этого жеста — использовать константу `private static final long DOUBLE_SPACE_PERIOD_TIMEOUT = 1100;` в InputLogic (в fork нет config.xml с таймаутами; long-press таймаут у форка — pref, не config).

**Revert (отмена по backspace):** в `handleBackspaceEvent` (InputLogic.java:311), в самом начале ветки без selection:
```
if (mJustDoubleSpaced && getCodePointBeforeCursor() == ' ' /* текст кончается ". " */):
    mConnection.deleteTextBeforeCursor(2); mConnection.commitText("  ", 1)
    mJustDoubleSpaced = false; return
```
Это точный AOSP-паттерн (revertDoubleSpacePeriod → восстановить два пробела). Любой другой ввод сбрасывает `mJustDoubleSpaced` (сброс в `tryDoubleSpacePeriod` и в `handleNonSeparatorEvent` — одна строка). «Отмена третьим пробелом» в AOSP нет — не изобретать (совпадает с CONTEXT discretion).

**Гейты полей:**
- Password: `settingsValues.mInputAttributes.mIsPasswordField` (InputAttributes.java:34,49 — `InputTypeUtils.isPasswordInputType`). SC4 закрыт.
- URL/email: явного публичного флага в InputAttributes нет (`mInputType` приватен). Проверка «перед пробелом буква/цифра, перед курсором ровно один пробел» сама по себе исключает практически все ложные срабатывания в URL/email-полях (там пробелы не печатают). Для MVP достаточно password-гейта + letter-check; отдельный URI/EMAIL-гейт не городить.

**Pref-гейт:** рекомендую **always-on, без pref**. Конвенция форка — минимум переключателей (`prefs_screen_preferences.xml` содержит всего ~6 SwitchPreference); AOSP-пref «Double-space period» — опция полноразмерной Gboard-настройки, для MVP лишняя сущность (легко добавить позже по фидбеку: паттерн `Settings.readSpaceSwipeEnabled` копируется 1-в-1).

## INPUT-06: Свайп по пробелу → курсор — WORKS, но выключен по умолчанию

**Механика есть и полная:**
- Гейт: `PointerTracker.onMoveEvent` — `oldKey.getCode() == Constants.CODE_SPACE && …mSpaceSwipeEnabled` (PointerTracker.java:621-631): считает шаги по X, ставит `mCursorMoved = true`, зовёт `sListener.onMoveCursorPointer(steps)`.
- Реализация: `LatinIME.onMoveCursorPointer` (LatinIME.java:657-677) — при известной позиции курсора двигает через `setSelection` c `getUnicodeSteps` (surrogate/ZWJ-aware, RichInputConnection.java:483+, RTL учтён), иначе fallback на `sendDownUpKeyEvent(KEYCODE_DPAD_LEFT/RIGHT)`. Haptic tick есть.
- Подавление коммита пробела после свайпа: PointerTracker.java:704 (`mCursorMoved && CODE_SPACE` → не вводить пробел), сброс на up 718-719; `onUpWithSpacePointerActive` → `reloadTextCache` (LatinIME.java:704-706).
- Бонус: аналогичный delete-swipe (`pref_delete_swipe`, PointerTracker.java:633-638, LatinIME.onMoveDeletePointer 680-696).

**GAP только в дефолте:** `Settings.PREF_SPACE_SWIPE` (Settings.java:65), `readSpaceSwipeEnabled` → `getBoolean(…, false)` (Settings.java:246-248), и `android:defaultValue="false"` в prefs_screen_preferences.xml:48-50 (+ app_restrictions.xml:44). Требование SC2 «работает» + UX-дефолт без копания в настройках → **флипнуть default на true в трёх местах** (Settings.java, prefs XML, app_restrictions XML — держать согласованными). `pref_delete_swipe` не трогаем (вне требований). Password-риск нулевой: свайп двигает курсор, текст не вводит.

## INPUT-07: Multi-touch commit — WORKS

- Устройства с distinct multitouch идут напрямую в `PointerTracker.processMotionEvent`; `NonDistinctMultitouchHelper` — только fallback для древних панелей (MainKeyboardView.java:131-134, 472-478).
- `ACTION_POINTER_DOWN` обрабатывается как down нового трекера (PointerTracker.java:440-443), трекер добавляется в очередь (481).
- Commit-без-потерь: при up любого пальца `sPointerTrackerQueue.releaseAllPointersOlderThan(this, eventTime)` (PointerTracker.java:671) выдаёт всем более старым указателям `onPhantomUpEvent` (PointerTrackerQueue.java:91-105 → PointerTracker.java:681-687 → `onUpEventInternal`) — первая клавиша коммитится **раньше** второй, порядок печати сохраняется, буквы не теряются. Модификаторы обрабатываются отдельно (releaseAllPointers/Except, строки 479, 669).
- Кросс-чек с нашими раскладками: татарская/русская используют штатные rowkeys и штатный spacebar key style — никакой интерференции с PointerTracker/space-swipe (геометрия пробела стандартная, key style из key_styles_common.xml не менялся в фазах 2–3).

## Форма плана

Один план **05-01** (одна кодовая задача + конфиг-флип + структурная верификация):
1. **Task 1 (код, Java, минимальный дифф):** восстановить double-space→period в InputLogic по дизайну выше (2 поля + `tryDoubleSpacePeriod` + revert в handleBackspaceEvent + аксессор в RichInputConnection).
2. **Task 2 (конфиг):** default `pref_space_swipe` → true (Settings.java:247 + prefs_screen_preferences.xml + app_restrictions.xml).
3. **Верификация:** сборка APK; fail-capable-грепы (наличие tryDoubleSpacePeriod, default true); структурные проверки INPUT-07 (грепы по releaseAllPointersOlderThan/onPhantomUpEvent). On-device UAT (SC1-SC4, включая Telegram/WebView/password) — отложен по standing-паттерну.
