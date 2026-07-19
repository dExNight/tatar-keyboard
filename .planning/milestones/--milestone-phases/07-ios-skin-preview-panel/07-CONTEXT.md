# Phase 7: iOS-скин — превью, панель, отклик — Context

**Gathered:** 2026-07-18 (autonomous run; грей-зон нет — требования и дизайн зафиксированы; главный вопрос — техническая проверка «не PopupWindow»)
**Status:** Ready for planning

<domain_boundary>
«Живость» клавиатуры: баллон-превью на ACTION_DOWN (UI-02), long-press панель альтернатив со скольжением (UI-03), хаптика KEYBOARD_TAP + звук клика + подсветка на ACTION_DOWN, отключаемые программно (UI-04). НЕ входят: UI-настроек экран (фаза 10), автокоррекция, финальные замеры (фаза 11), TalkBack (фаза 9).
</domain_boundary>

## Phase Scope

**Requirements:** UI-02, UI-03, UI-04. SC1 превью мгновенно на ACTION_DOWN в слое клавиатуры (не PopupWindow); SC2 long-press панель, выбор скольжением; SC3 подсветка+хаптика+звук на ACTION_DOWN, отключаемы; SC4 сборка + smoke матрица (WebView/password), баллоны не обрезаются краями.

## Ключевой технический вопрос (research must resolve)

**UI-02 требует: превью В СЛОЕ клавиатуры, НЕ PopupWindow.** Причина (research/02, PROJECT.md): на MIUI/HyperOS PopupWindow-превью режутся краями и лагают. Форк имеет `KeyPreviewChoreographer` + `KeyPreviewView` — РЕСЕРЧ ДОЛЖЕН УСТАНОВИТЬ: рисует ли форк превью через PopupWindow или в слое View? Если PopupWindow — это главная (и, возможно, единственная) кодовая работа фазы: перенести отрисовку баллона в слой клавиатуры (Canvas или дочерний View в InputView-контейнере). Если уже в слое — UI-02 в основном верификация.

## Предварительный скаутинг

Вся инфраструктура в базе есть:
- Превью: `KeyPreviewChoreographer.java`, `KeyPreviewView.java`, TimerHandler
- Панель альтернатив: `MoreKeysPanel.java`, `MoreKeysKeyboard.java`, `MoreKeysKeyboardView.java`, `MoreKeysDetector.java` (уже работает — фаза 3 повесила на неё long-press дубли)
- Отклик: `AudioAndHapticFeedbackManager.java`; prefs `PREF_VIBRATE_ON`, `PREF_SOUND_ON`, `PREF_KEYPRESS_SOUND_VOLUME`, config_default_sound/vibration_enabled
- MoreKeysKeyboardView уже перерисован в фазе 6? — проверить, применился ли iOS-скин к панели/превью или они остались в старом стиле

## Claude's Discretion

- Если превью = PopupWindow: маршрут переноса в слой (минимальный риск регрессий multi-touch/панели).
- Стилизация превью/панели под iOS-палитру фазы 6 (баллон #FFF/тёмный, радиус, тень) — консистентно с темой id=7.
- Хаптика/звук: убедиться, что срабатывают на ACTION_DOWN (не ACTION_UP) — требование явное; если база на UP, поправить.
- «Отключаемы программно» — prefs уже есть; UI-экран настроек — фаза 10, здесь не делать.

## Deferred

- On-device UAT — стандартная отложенная схема (принята).
- Экран настроек (тумблеры звук/вибро) — фаза 10.
- Замеры производительности анимации превью — фаза 11.

---
*Phase: 07-ios-skin-preview-panel*
*Context gathered: 2026-07-18*
