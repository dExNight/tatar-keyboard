# Review 07 — iOS-скин превью-баллона и панели альтернатив

**Depth:** standard · **Diff base:** `fbfd66a` · **Date:** 2026-07-18
**Scope reviewed:** `ios_key_preview_background.xml`, `ios_popup_panel_background.xml`, `themes-tatar.xml` (2 item-замены). Zero Java подтверждён: `git diff fbfd66a --name-only` под `app/` — только эти 3 файла.

## Verdict

**PASS.** Блокеров нет. Геометрический паритет с legacy-drawable подтверждён построчной трассой через Java-потребителей (не только визуальным сравнением XML). Один UX-риск унаследован из фазы 6 (см. F-1) — рекомендуется проверить на UAT, к диффу фазы 7 не блокер.

## Findings

### F-1 (medium, унаследовано из фазы 6, наблюдение) — pressed-подсветка на панели почти невидима в light mode

Панель альтернатив подсвечивает выбранную клавишу через `btn_keyboard_key_popup` → `btn_keyboard_key_pressed_border` → `?attr/keyPressedBackgroundColor` = `ios_key_normal_pressed` **#C8CDD4**. Фон панели — `?attr/popupPanelBackgroundColor` = `ios_keyboard_background_secondary` **#C7CAD2**. Разница между ними — 1–2 единицы на канал: slide-to-select-подсветка в светлой теме практически неразличима. В dark всё нормально (#7D7D7D на #3A3A3A — виден).

Причина не в диффе фазы 7 (оба атрибута заведены фазой 6), но фаза 7 формализует внешний вид панели (UI-03), поэтому фиксирую здесь. Исправление — Tatar-специфичный pressed-drawable панели или отдельный цвет пары values/values-night — отдельным изменением, общий `btn_keyboard_key_pressed_border` трогать нельзя (6 старых тем). Проверить на UAT.

### F-2 (info) — intrinsic height нового layer-list 6dp против 5dp у legacy — инертно

`LayerDrawable.getIntrinsicHeight()` = intrinsic слоя-баллона (5dp из `<size>`) + inset `android:bottom="1dp"` = 6dp; у legacy плоского shape — 5dp. Проверено, что это ни на что не влияет: единственный потребитель intrinsic-размеров — `KeyPreviewView.setTextAndScaleX()` (`KeyPreviewView.java:84`), и он читает только **ширину** (45dp — совпадает: слой-тень без `<size>` даёт −1 и не участвует в max). Высота превью берётся из атрибута `keyPreviewHeight` (`KeyPreviewDrawParams.java:66`), не из drawable.

### F-3 (info) — радиус 6dp у shared pressed-border внутри 5dp-панели

`btn_keyboard_key_pressed_border` (shared, radius `button_corner_radius_lxx` = 6dp) рисуется внутри панели с радиусом 5dp. Косметическое несоответствие семейству фазы 6, заметное только на pressed-состоянии углового элемента панели; вместе с F-1 закрывается одним будущим Tatar-drawable.

## Checks performed

### 1. Паритет геометрии превью — PASS (проверено по Java-трассе)

Потребители фона превью и что они читают:

| Потребитель | Что читает | Legacy | Новый layer-list | Равно |
|---|---|---|---|---|
| `KeyPreviewView.setTextAndScaleX:83-85` | `getPadding()` L/R + `getIntrinsicWidth()` | 0/0, 45dp | 0/0 (nested-сумма паддингов слоёв), 45dp (слой-баллон) | ✓ |
| `TextView` (view-padding из background при `setBackgroundResource`, `KeyPreviewChoreographer.java:68`) | padding (0,0,0,60dp) | (0,0,0,60dp) | (0,0,0,60dp) — единственный слой с `<padding>` | ✓ |
| `KeyPreviewDrawParams.setGeometry:86-98` (mVisibleWidth/Height/Offset → выравнивание more-keys панели) | view-паддинги + `keyPreviewHeight`/`Offset` из темы | из тех же config_*_lxx | тема не меняла dimen-атрибуты | ✓ |

Позиционирование (`KeyPreviewChoreographer.placeKeyPreview:108-132`) использует только `mPreviewHeight`/`mPreviewOffset`/`mMinPreviewWidth` из атрибутов темы — не тронуты. Оба drawable заливают одинаковые bounds (у GradientDrawable `<size>` влияет только на intrinsic, не на отрисовку). **Превью не сместится.**

### 2. Структура layer-list против конвенции фазы 6 — PASS

Побайтово тот же паттерн, что default-ветка `ios_key_normal.xml`: тень `ios_key_shadow` с `top=1dp`, тело с `bottom=1dp`, radius 5dp у обоих слоёв. Radius 5dp совпадает со всем семейством (`ios_key_functional`, `ios_key_spacebar` — везде 5dp).

### 3. State-селектор для превью — не нужен, PASS

Legacy `keyboard_key_feedback_background` — тоже статический shape без селектора. `KeyPreviewView` — обычный `TextView`, pressed-состояние на него не транслируется; `setColor()` (`KeyPreviewView.java:94-102`) с OVERLAY-фильтром неактивен: тема Tatar создана с `customColorSupport=false` (`KeyboardTheme.java:46`) → `backgroundColor=TRANSPARENT` (`MainKeyboardView.java:337`), `Color.alpha()==0`, фильтр не ставится. Комментарий в drawable это документирует.

### 4. Night mode — PASS

Баллон dark: `ios_key_normal` **#6B6B6B**, текст `keyPreviewTextColor` = `ios_key_text_color` **#FFFFFF** → контраст **≈5.3:1**, проходит WCAG AA (4.5:1); совпадает с контрастом обычной клавиши dark-темы (то же сочетание). Light: чёрный на белом. Тень `ios_key_shadow` имеет night-пару (#40000000 / #B3000000). Новых цветов дифф не заводит — parity-чек values/values-night тривиально выполнен.

### 5. Контраст панели light/dark — PASS (с оговоркой F-1)

Фон панели `ios_keyboard_background_secondary`: light #C7CAD2 на клавиатуре #D4D6DD, dark #3A3A3A на #2C2C2C — панель отличима от фона в обоих режимах. Текст клавиш панели (наследован от `KeyboardView.Tatar`): чёрный на #C7CAD2 / белый на #3A3A3A — читаемо. Pressed-подсветка light — см. F-1.

### 6. Паддинг панели — PASS

Legacy 5dp по кругу → новый 5dp по кругу, идентично; инсеты клавиш панели и measure `MoreKeysKeyboardView` не изменятся. Единственное отличие от legacy — radius 5dp вместо 6dp (`button_corner_radius_lxx`) — заявленное намерение плана (консистентность семейства).

### 7. Wiring темы — PASS

Ровно две item-замены в `themes-tatar.xml`: `MainKeyboardView.Tatar.keyPreviewBackground` → `@drawable/ios_key_preview_background` (строка 60), `MoreKeysKeyboardView.Tatar.android:background` → `@drawable/ios_popup_panel_background` (строка 79). Атрибуты корректные (те же, что в legacy-темах на тех же позициях). `keyPreviewTextColor`, dimen-атрибуты превью, `keyBackground` панели — не тронуты, как требует план.

### 8. Shared legacy-файлы — PASS

`git diff fbfd66a --name-only`: `keyboard_key_feedback_background.xml`, `keyboard_popup_panel_background.xml`, `btn_keyboard_key_popup.xml`, 6 старых тем, config.xml, colors — не изменены. 6 legacy-тем продолжают ссылаться на старые drawable (проверено grep).

### 9. XML / сборка — PASS

`xmllint --noout` на всех трёх файлах — чисто. `assembleDebug` — BUILD SUCCESSFUL (лог исполнителя + повторяемость подтверждена структурой ресурсов: все ссылки `@color/ios_*`, `?attr/popupPanelBackgroundColor` разрешимы).

## Recommendations (не блокеры)

1. **UAT:** прицельно проверить slide-to-select на панели в **светлой** теме — подсветка выбранной альтернативы, скорее всего, не видна (F-1). Если подтвердится — завести задачу на Tatar-pressed-drawable панели (заодно закроет F-3).
2. При будущем рефакторинге можно вынести повторяющийся layer-list паттерн «тень 1dp + тело» — сейчас он скопирован в 4 drawable; но данными-XML это нормально, не срочно.
