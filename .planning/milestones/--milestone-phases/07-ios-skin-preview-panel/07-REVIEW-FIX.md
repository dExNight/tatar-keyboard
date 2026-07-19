# Review-Fix 07 — iOS-скин превью-баллона и панели альтернатив

**Base review:** [07-REVIEW.md](07-REVIEW.md) · **Date:** 2026-07-18
**Fix commit:** `ff283d9` — `fix(07): visible slide-select highlight on more-keys panel (F-1)`

## Status of findings

### F-1 (medium) — pressed-подсветка на панели почти невидима в light mode — **FIXED**

Заведён Tatar-специфичный drawable `ios_popup_key_background.xml` (селектор: pressed-shape + transparent), подключён в `MoreKeysKeyboardView.Tatar.keyBackground` вместо shared `btn_keyboard_key_popup`. Подсветка использует новую пару цветов `ios_popup_key_pressed`:

- **light:** `#A2A6B0` на фоне панели `#C7CAD2` — тон из палитры фазы 6 (совпадает с `ios_key_checked`), явно темнее фона, slide-to-select виден
- **dark:** `#7D7D7D` на фоне панели `#3A3A3A` — тот же тон, что был через `ios_key_normal_pressed`, визуальный паритет с прежним dark-поведением

Shared `btn_keyboard_key_pressed_border` / `btn_keyboard_key_popup` и 6 legacy-тем не тронуты. Values/values-night parity соблюдён (новый цвет заведён в обеих папках).

### F-2 (info) — intrinsic height layer-list 6dp vs 5dp — **NO ACTION**

Инертно по трассе ревью (высота превью берётся из `keyPreviewHeight`, не из drawable). Изменений не требует; остаётся заметкой для UAT-прохода фазы 7.

### F-3 (info) — радиус 6dp у shared pressed-border внутри 5dp-панели — **FIXED (вместе с F-1)**

Новый `ios_popup_key_background` рисует pressed-shape с радиусом 5dp — консистентно с `ios_popup_panel_background` и всем семейством фазы 6. Несоответствие 6dp/5dp на панели Tatar-темы устранено; shared drawable по-прежнему 6dp для legacy-тем, как и должно быть.

## Verification

- `./gradlew assembleDebug` — BUILD SUCCESSFUL
- `scripts/check-no-internet.sh` (source manifest + built APK) — OK, no INTERNET
- Дифф затрагивает только Tatar-ресурсы: 1 новый drawable, пара цветов values/values-night, 1 item-замена в `themes-tatar.xml`; zero Java

## Remaining

- **UAT** (перенесено из рекомендаций ревью): прицельно проверить slide-to-select на панели в светлой теме на устройстве — подтвердить, что `#A2A6B0` достаточно различим; заодно F-2-заметку про превью-баллон.
