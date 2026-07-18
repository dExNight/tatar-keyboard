# Phase 5: Механика ввода — жесты и multi-touch — Context

**Gathered:** 2026-07-18 (autonomous run; грей-зон нет — требования детерминированы)
**Status:** Ready for planning

<domain_boundary>
Жестовая механика: двойной пробел → точка+пробел (INPUT-05), свайп по пробелу → курсор (INPUT-06), multi-touch commit-on-second-touch (INPUT-07). НЕ входят: swipe-typing (out of scope всего проекта), скин (6–7), long-press попапы (готово в 3).
</domain_boundary>

## Phase Scope

**Requirements:** INPUT-05, INPUT-06, INPUT-07. Success criteria роадмапа: SC1 двойной пробел точка, SC2 свайп-курсор, SC3 два пальца без потери букв, SC4 сборка + smoke матрица (Telegram/WebView, без ложных срабатываний в password).

## Предварительный скаутинг

- Свайп по пробелу: следы в Settings/SettingsValues/PointerTracker (cursor move / sliding input?) — ресерчу выяснить, есть ли готовая механика и включена ли по умолчанию.
- Multi-touch: PointerTrackerQueue + NonDistinctMultitouchHelper есть в базе — вероятно WORKS (AOSP-механика commit-on-second-touch).
- Двойной пробел → точка: grep по mDoubleSpacePeriod/double_space ничего не нашёл — ВЕРОЯТНЫЙ GAP (rkkr мог вырезать из AOSP). Если так — единственная кодовая работа фазы: восстановить/реализовать минимально (AOSP-паттерн: таймаут двойного тапа, отмена третьим пробелом/бэкспейсом? — минимум по требованию: двойной тап → «. »), с уважением к password/URL полям (SC4: не срабатывать ложно — уточнить условие: AOSP отключает в некоторых полях?).

## Claude's Discretion

- Вся реализация. Если double-space-period отсутствует — реализовать по AOSP-паттерну минимальным диффом (InputLogic/связка с таймером), с гейтом на тип поля, консистентным с базой. Java-правки в этой фазе допустимы (фаза механики), минимальный дифф.
- Если свайп-курсор есть, но за prefом — решить: включить по умолчанию или оставить как есть (ресерч смотрит, что делает база; требование говорит «работает», не «включён по умолчанию» — но UX-дефолт должен позволять SC2 без копания в настройках).
- Отмена точки (третий пробел/undo) — по AOSP-паттерну, если он есть; не изобретать.

## Deferred

- On-device UAT — стандартная отложенная схема (принята).
- Swipe-typing — out of scope проекта.

---
*Phase: 05-zhesty-i-multi-touch*
*Context gathered: 2026-07-18*
