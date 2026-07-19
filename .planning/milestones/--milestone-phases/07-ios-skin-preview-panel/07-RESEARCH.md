# Phase 7: iOS-скин — превью, панель, отклик — Research (аудит слоя рендеринга)

**Date:** 2026-07-18
**Scope:** UI-02 (баллон-превью), UI-03 (long-press панель), UI-04 (отклик на ACTION_DOWN). Аудит форка по исходникам.

---

## TL;DR / Вердикты

| Req | Вердикт | Суть |
|-----|---------|------|
| **UI-02** | **WORKS (архитектура) + GAP (стилизация)** | Превью УЖЕ в слое окна IME, **PopupWindow не используется нигде** (`grep -rln PopupWindow app/src/main/java` → пусто). Хост — `DrawingPreviewPlacerView` (RelativeLayout), добавляемый в `android.R.id.content` собственного окна IME. Главный риск фазы снят. Осталась стилизация баллона под iOS. |
| **UI-03** | **WORKS (механика+слой) + GAP (косметика)** | Панель тоже показывается в `mDrawingPreviewPlacerView` (тот же in-layer слой, не PopupWindow). Slide-to-select реализован полностью. Тема id=7 уже задаёт стили панели (фаза 6) — остаётся только проверка вида/радиусов. |
| **UI-04** | **WORKS почти полностью** | Хаптика+звук+подсветка срабатывают на ACTION_DOWN (цепочка подтверждена построчно). Prefs `vibrate_on`/`sound_on` полностью wired. Нюансы: (а) на API ≥ Q используется `VibrationEffect.EFFECT_CLICK`, `KEYBOARD_TAP` — только фолбэк < Q; (б) звук **по умолчанию выключен** (`config_default_sound_enabled=false`) — решить, флипать ли. |

**Кодовая работа фазы — маленькая:** ~2 новых drawable + 1–3 правки строк в themes-tatar.xml + (опц.) 1 config-флип. Переносить ничего не нужно.

---

## 1. UI-02: превью — определение «PopupWindow vs слой» (решающий вопрос)

### Трасса показа превью (все пути от `app/src/main/java/rkr/simplekeyboard/inputmethod/`)

1. `PointerTracker.onDownEventInternal` (`keyboard/PointerTracker.java:496-523`) — вызывается из `processMotionEvent` по `MotionEvent.ACTION_DOWN`/`ACTION_POINTER_DOWN` (:440-442) → `setPressedKeyGraphics(key)` (:518) → `sDrawingProxy.onKeyPressed(key, true)` (:355).
2. `MainKeyboardView.onKeyPressed` (`keyboard/MainKeyboardView.java:316-322`) → `key.onPressed()` + `invalidateKey(key)` (подсветка) + `showKeyPreview(key)`.
3. `MainKeyboardView.showKeyPreview` (:324-340) → `mKeyPreviewChoreographer.placeAndShowKeyPreview(..., mDrawingPreviewPlacerView, ...)`.
4. `KeyPreviewChoreographer.getKeyPreviewView` (`keyboard/internal/KeyPreviewChoreographer.java:53-71`) — пул переиспользуемых `KeyPreviewView` (extends TextView); новый view добавляется **`placerView.addView(...)`** (:69) — обычный child view, никакого WindowManager.
5. Хост: `MainKeyboardView.installPreviewPlacerView` (:299-312), вызывается из `onAttachedToWindow` (:371-374): `rootView.findViewById(android.R.id.content).addView(mDrawingPreviewPlacerView)` — **placer добавляется в content-view СОБСТВЕННОГО окна IME**, поверх InputView. `DrawingPreviewPlacerView` (`keyboard/internal/DrawingPreviewPlacerView.java:27-47`) — простой RelativeLayout.

### Вердикт

**Требование «в слое клавиатуры, не PopupWindow» УЖЕ выполнено базой форка.** Это AOSP-паттерн DrawingPreviewPlacerView (тот самый «in-layer» из research/02 §2B): ноль IPC к WindowManager, превью — child view в иерархии окна IME, показывается тем же кадром, что и подсветка. MIUI-риск обрезки чужих окон не применим — окно одно. Никакого «переноса» делать не нужно; UI-02 = верификация + стилизация.

Позиционирование: `placeKeyPreview` (`KeyPreviewChoreographer.java:108-132`) — баллон центрируется над клавишей, `previewY = key.y − previewHeight + offset` в координатах окна. Окно IME выше видимой клавиатуры, «прозрачная» зона над ней отдаётся приложению через `LatinIME.onComputeInsets` (`latin/LatinIME.java:535-567`: `contentTopInsets`/`visibleTopInsets` = visibleTopY, `touchableRegion`) — штатный механизм, баллоны верхнего (пятого) ряда рисуются над клавиатурой внутри окна. **SC4-проверка «не обрезаются краями» остаётся UAT-пунктом** (особенно пятый ряд + крайние колонки; горизонтальный клампинг в :121 сдвигает баллон внутрь).

### Дефолт и pref

- Превью **включено по умолчанию**: `config_default_key_preview_popup=true` (`values/config-per-form-factor.xml:24`), pref `popup_on` → `Settings.readKeyPreviewPopupEnabled` (`latin/settings/Settings.java:219-224`) → `SettingsValues.mKeyPreviewPopupOn` (:43,77) → `KeyboardSwitcher.java:150-151` → `MainKeyboardView.setKeyPreviewPopupEnabled` (:290-292). Отключаемость — уже есть.
- При выключенном превью `showKeyPreview` (:330-333) корректно выставляет `visibleOffset` для панели — трогать нельзя.

### Стилизация (GAP — единственная работа по UI-02)

`KeyPreviewView.setPreviewVisual` (`keyboard/internal/KeyPreviewView.java:52-69`) берёт цвет текста/размер из `KeyDrawParams` (тема: `keyPreviewTextColor`, `keyPreviewTextRatio`), фон — `mParams.mPreviewBackgroundResId` = атрибут `keyPreviewBackground`. Фаза 6 уже прописала в `MainKeyboardView.Tatar` (`values/themes-tatar.xml:60-64`): `keyPreviewTextColor=@color/ios_key_text_color` и `keyPreviewBackground=@drawable/keyboard_key_feedback_background`. Но общий drawable `keyboard_key_feedback_background.xml` — плоский shape с `?attr/popupPanelBackgroundColor` (в теме Tatar = `ios_keyboard_background_secondary` #C7CAD2 — серый, не iOS-баллон) и странной геометрией (`size 45×5dp`, `padding bottom 60dp`, радиус 6dp).

**Фикс:** новый `res/drawable/ios_key_preview_background.xml` — roundRect цвета клавиши (`@color/ios_key_normal`: #FFF light / #6B6B6B dark — night-оверрайд бесплатно), радиус ~8–10dp (iOS-баллон крупнее ключа), при желании 1dp-тень layer-list'ом по паттерну фазы 6; сослаться из `MainKeyboardView.Tatar`. Геометрию (`keyPreviewOffset=55dp`, `keyPreviewHeight=122dp`, `config.xml:47-49`) подстроить при необходимости — данные, не код. `setColor` (`KeyPreviewView.java:94-102`) применяет OVERLAY-фильтр только при `mCustomColorSupport` — у темы Tatar его нет (backgroundColor=TRANSPARENT, `MainKeyboardView.java:337`) — не мешает.

### Аллокации (полугорячий путь — каждое нажатие)

- Пул view работает: `mFreeKeyPreviewViews`/`mShowingKeyPreviewViews` (`KeyPreviewChoreographer.java:42-45`); `new KeyPreviewView` — только пока пул пуст (первые ~N одновременных пальцев).
- Мелочь: `HashMap<Key,KeyPreviewView>.put/remove` на каждое нажатие → entry-аллокации; `createDismissAnimator` при HW-анимации — Animator+Listener на каждый dismiss (`:143-158`). Upstream-поведение, объектов мало и они короткоживущие; для PERF-03 приемлемо — **не трогаем** (замер — фаза 11; если профайлер покажет GC — известная точка).

---

## 2. UI-03: long-press панель альтернатив

### Механика (WORKS)

- Показ: `PointerTracker.onLongPressed` (`PointerTracker.java:743-784`) → `sDrawingProxy.showMoreKeysKeyboard` (`MainKeyboardView.java:384-432`) → `moreKeysKeyboardView.showMoreKeysPanel(...)` → `onShowMoreKeysPanel` (:446-455) → **`panel.showInParent(mDrawingPreviewPlacerView)`** (:453).
- `MoreKeysKeyboardView.showInParent` (`keyboard/MoreKeysKeyboardView.java:232-235`) = `parentView.addView(getContainerView())` — **панель тоже child view в том же in-layer placer'е, не PopupWindow**. MIUI-риск отсутствует по той же причине.
- **Slide-to-select работает без отрыва пальца:** после показа `PointerTracker.onLongPressed` сразу пробрасывает текущую точку в панель — `moreKeysPanel.onDownEvent(translatedX, translatedY, mPointerId)` (`PointerTracker.java:779-781`); дальнейшие `ACTION_MOVE` того же пальца идут в `mMoreKeysPanel.onMoveEvent` (`PointerTracker.java:548`), выбор подсвечивается `detectKey`→`updatePressKeyGraphics` (`MoreKeysKeyboardView.java:119-129,158-174`), коммит на `ACTION_UP` → `onUpEvent`→`onKeyInput` (:132-156). `MoreKeysDetector` с `config_more_keys_keyboard_slide_allowance` — допуск скольжения (:57-58). Уход пальца с панели в «никуда» — отмена (:125-128). Фаза 3 уже эксплуатирует это (дубли long-press) — регрессий не заводить.
- Insets: при показанной панели `touchableRegion` расширяется до всего окна (`LatinIME.onComputeInsets`, `latin/LatinIME.java:557` — `touchTop = isShowingMoreKeysPanel() ? 0 : visibleTopY`) — панель над клавиатурой кликабельна. ✓

### Стилизация после фазы 6 (малый GAP)

Тема id=7 уже содержит `MoreKeysKeyboardView.Tatar` (`values/themes-tatar.xml:76-85`): фон панели `keyboard_popup_panel_background` → `?attr/popupPanelBackgroundColor` → `ios_keyboard_background_secondary` (light #C7CAD2 / night #3A3A3A — `values/colors.xml:58`, `values-night/colors.xml:35`), pressed-клавиша панели `btn_keyboard_key_popup` → `btn_keyboard_key_pressed_border` → `?attr/keyPressedBackgroundColor` = `ios_key_normal_pressed`. То есть **панель уже перекрашена в iOS-палитру фазы 6 через атрибуты**. Остаток на усмотрение: радиус панели (`button_corner_radius_lxx` 6dp → можно поднять/оставить), возможно лёгкая тень layer-list'ом. UI-03 = верификация + микрокосметика; Java-дифф = 0.

---

## 3. UI-04: подсветка, хаптика, звук на ACTION_DOWN

### Тайминг — подтверждено: ACTION_DOWN

Цепочка: `MotionEvent.ACTION_DOWN` → `PointerTracker.onDownEvent` (:454-483) → `onDownEventInternal` (:496-523) → **две ветки на down**:
- `callListenerOnPressAndCheckKeyboardLayoutChange` (:512 → :200-221) → `sListener.onPressKey(...)` (:216) → `LatinIME.onPressKey` (`latin/LatinIME.java:848-853`) → `hapticAndAudioFeedback` (:815-838) → `AudioAndHapticFeedbackManager.performHapticFeedback` + `performAudioFeedback`. **Отклик — на нажатии, не на отпускании.** Требование выполнено базой.
- `setPressedKeyGraphics(key)` (:518 → :347-373) → `onKeyPressed` → `key.onPressed()` + `invalidateKey(key)` — **подсветка на down** тем же событием (state-list drawable `state_pressed` из фазы 6 меняет цвет). ✓

### Хаптика/звук — детали (`latin/AudioAndHapticFeedbackManager.java`)

- Хаптика (:120-133): API ≥ Q → `Vibrator.vibrate(VibrationEffect.EFFECT_CLICK)`; < Q → `performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, FLAG_IGNORE_GLOBAL_SETTING)`. Формально требование называет KEYBOARD_TAP — EFFECT_CLICK на Q+ это его прямой современный эквивалент (KEYBOARD_TAP внутри системы мапится на click-эффект); **трактовать как соответствие**, зафиксировать аннотацией при простановке чек-бокса. Вызовы уходят в single-thread executor (:66,115,124) — с UI-потока ничего не блокируется, аллокаций в горячем пути нет (лямбды — capture, мелочь; upstream).
- Звук (:84-108): `AudioManager.playSoundEffect(FX_KEYPRESS_*)` с вариациями delete/enter/space; глушится при ringer ≠ NORMAL (:77-82).
- Prefs полностью wired: `PREF_VIBRATE_ON`/`PREF_SOUND_ON` (`Settings.java:51-52`), дефолты `config_default_vibration_enabled=true` (`config-common.xml:30`), **`config_default_sound_enabled=false`** (`config-per-form-factor.xml:25`); чтение — `Settings.java:206-216`; live-обновление через `onSettingsChanged` (:150-153). «Отключаемы программно» — выполнено (UI-тумблеры — фаза 10).
- Гигиена: нет вибры при драге (:817-819), прореживание на автоповторе backspace (:821-831).

### Открытое решение (Claude's discretion → план)

Звук клика по умолчанию OFF. У iOS звук по умолчанию ON. Требование говорит лишь «срабатывает + отключаем» — формально выполнено и при OFF-дефолте. **Рекомендация: флипнуть `config_default_sound_enabled` → true** (1 строка, консистентно с iOS-референсом и «звук срабатывает» проверяем из коробки); решить при планировании.

---

## 4. Кросс-каттинг: наследует ли превью/панель тему id=7

Да. `KeyPreviewView` создаётся из `placerView.getContext()` (`KeyPreviewChoreographer.java:66-67`) — это контекст `MainKeyboardView`, т.е. `ContextThemeWrapper` темы Tatar; `MoreKeysKeyboardView` инфлейтится из `mMoreKeysKeyboardContainer` (`MainKeyboardView.java:172-173`, layout `more_keys_keyboard.xml`) в том же контексте. Все цвета идут через атрибуты темы → `ios_*` ресурсы → night-квалификатор — dark mode обоих слоёв бесплатный. Единственное «не-iOS» место — сам drawable баллона (см. §1).

Причуда (не блокер): `ViewLayoutUtils.placeViewAt` (`latin/utils/ViewLayoutUtils.java:48-57`) ставит `rightMargin = −50` захардкоженно — upstream-хак; не трогать в этой фазе.

---

## 5. Итоговые GAP'ы и план-шейп

**GAP-список (весь дифф фазы):**

1. **iOS-баллон превью** — новый `res/drawable/ios_key_preview_background.xml` (roundRect цвета `ios_key_normal`, радиус ~8–10dp, опц. layer-list-тень 1dp) + замена `keyPreviewBackground` в `MainKeyboardView.Tatar` (`themes-tatar.xml:60`); при необходимости подстройка `config_key_preview_{offset,height}_lxx` (можно продублировать своими dimen в стиле Tatar, не трогая другие темы).
2. **(Опц.) косметика панели** — радиус/тень `keyboard_popup_panel_background` для темы Tatar (лучше отдельным `ios_popup_panel_background.xml`, чтобы не менять 6 старых тем).
3. **(Решение) `config_default_sound_enabled` false→true** — 1 строка.
4. **Верификация** (грепы + UAT-чеклист): PopupWindow отсутствует; превью on-by-default; отклик на DOWN (цепочка §3); slide-to-select; баллоны пятого ряда/краёв не обрезаются; матрица WebView/password.

**Java-дифф: 0 строк** (вся фаза — XML-данные + верификация), что идеально по конституции проекта. Риски: только визуальные (геометрия баллона на 5-рядной раскладке — проверить, что баллон верхнего ряда помещается в окно; окно IME выше клавиатуры — должен, UAT-пункт).

**Ожидаемый файловый список:**
- новые: `res/drawable/ios_key_preview_background.xml` (+ опц. `ios_popup_panel_background.xml`)
- правки: `res/values/themes-tatar.xml` (1–3 item'а), опц. `res/values/config-per-form-factor.xml` (sound default), опц. dimen'ы
- ноль правок Java; ноль правок остальных 6 тем.

---

## Ответы на research questions (кратко)

1. **PopupWindow vs слой:** слой. `DrawingPreviewPlacerView` в `android.R.id.content` окна IME; PopupWindow в java-исходниках отсутствует вообще. UI-02 архитектурно satisfied; перенос не нужен.
2. **Дефолт/стилизация:** превью ON (`config_default_key_preview_popup=true`); текст уже темизирован фазой 6, фон-drawable общий и не-iOS — единственный реальный фикс.
3. **UI-03:** in-layer (тот же placer), slide-to-select полный (handoff в `onLongPressed:779-781`), тема id=7 уже применяется через атрибуты; остаётся верификация + микрокосметика.
4. **UI-04:** отклик на ACTION_DOWN подтверждён построчно; KEYBOARD_TAP — фолбэк < Q, на Q+ EFFECT_CLICK (эквивалент); prefs wired; подсветка на down. Звук default OFF — предложен флип.
5. **Тема:** оба слоя наследуют ContextThemeWrapper темы Tatar; night бесплатно. Аллокации превью — пул view, мелкие HashMap/Animator-объекты upstream-уровня, приемлемо.
6. **План:** 1–2 drawable + 1–3 строки темы + опц. 1 config-строка + верификация; Java-дифф пуст.

---
*Phase: 07-ios-skin-preview-panel | Research complete: 2026-07-18*
