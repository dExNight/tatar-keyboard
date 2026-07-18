# Phase 6: iOS-скин — Canvas-отрисовка и темы — Context

**Gathered:** 2026-07-18 (autonomous run; все дизайн-решения зафиксированы в PROJECT.md / research/04; грей-зон нет)
**Status:** Ready for planning

<domain_boundary>
Canvas-рендер: новый KeyboardView с iOS-палитрой, радиусом, тенью, light/dark theme по системной. Каркас ExploreByTouchHelper. PERF-03 дисциплина (ноль аллокаций в onDraw). НЕ входят: баллон-превью (UI-02 — фаза 7), long-press панель (UI-03 — фаза 7), хаптика/звук (UI-04 — фаза 7), автокоррекция/словари.
</domain_boundary>

## Phase Scope

**Requirements:** UI-01 только (SC1–SC5).

## Locked Design Decisions (из research/04 + PROJECT.md)

| # | Решение |
|---|---------|
| Палитра | Светлая: фон клавиатуры #D4D6DD, клавиши #FFF, служебные/shift/delete #B3B7C0; Тёмная: #2C2C2C / #6B6B6B / #474747 |
| Геометрия | Радиус ~5dp; резкая 1dp-тень (смещение 0,+1dp, цвет #00000040 approx) |
| Шрифт | Roboto — системный; никакого SF Pro |
| Иконки | Собственные VectorDrawable; никаких SF Symbols / ассетов Apple |
| ExploreByTouchHelper | Каркас при первой версии KeyboardView (ключи = виртуальные узлы); полная верификация TalkBack — фаза 9 |
| PERF-03 | Paint/Rect — поля класса, не new в onDraw; точечный invalidate(Rect); ноль аллокаций в горячем пути. Дисциплина ЗАПИСЫВАЕТСЯ В КОД сейчас, финальный замер — фаза 11 |
| Theme switch | Слушать конфигурацию системы (UiModeManager / Configuration.uiMode) без перезапуска KeyboardView — через updateConfiguration/recreateAllKeyboards или эквивалент форка |
| Canvas, не Compose | Один кастомный View + Canvas; Compose в IME-процессе запрещён |

## What the fork has (предварительный скаутинг)

- `KeyboardView.java` (базовый) + `MainKeyboardView.java` (расширенный) — оба рисуют на Canvas
- `keyboard-themes.xml` и `colors.xml` существуют — в базе уже есть своя система тем
- `ExploreByTouchHelper` — отсутствует в исходниках (пустой grep)
- Paint уже импортирован и используется (mBackgroundDimAlphaPaint, newLabelPaint и т.д.)
- `onDrawKeyTopVisuals` в MainKeyboardView — основной хук отрисовки ключей

## Claude's Discretion

- Архитектурный подход: расширить KeyboardView/MainKeyboardView (оверрайдить onDraw/onDrawKeyTopVisuals) или написать новый KeyboardView? Исследовать форк и выбрать путь с минимальным риском регрессий механики.
- Точная схема применения тени (Paint.setShadowLayer vs ручной смещённый прямоугольник)
- Как интегрировать ExploreByTouchHelper с существующим touch-handling форка
- Детали смены темы без перезапуска — по существующему механизму форка

## Deferred

- On-device UAT — стандартная схема.
- Баллон-превью, long-press панель, хаптика/звук — фаза 7.
- Финальные замеры производительности — фаза 11.
- TalkBack полная верификация — фаза 9.

---
*Phase: 06-ios-skin-canvas*
*Context gathered: 2026-07-18*
