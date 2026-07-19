# Phase 8: Совместимость — Context

**Gathered:** 2026-07-18 (autonomous run; грей-зон нет — требования детерминированы; фаза во многом верификационная + добивание пробелов)
**Status:** Ready for planning

<domain_boundary>
Проблемные окружения: password (COMPAT-01), WebView/keyCode 229 (COMPAT-02), edge-to-edge API 35+ insets (COMPAT-03), ландшафт (COMPAT-04), directBootAware (COMPAT-05). Систематический полный проход InputConnection-матрицы + добивание. НЕ входят: доступность/TalkBack (фаза 9), онбординг/настройки (10), замеры (11).
</domain_boundary>

## Phase Scope

**Requirements:** COMPAT-01..05. SC5 требует ПИСЬМЕННО зафиксированный полный проход матрицы (Telegram, Chrome/WebView, пароли, MIUI/One UI, ландшафт).

## Честная оценка природы фазы

Эта фаза наиболее on-device по сути: полный прогон матрицы InputConnection — device-only, а SC5 явно требует письменного полного прохода. Поэтому фаза расщепляется:
- **Код-аудит (сейчас):** directBootAware (манифест — уже true), обработка insets/edge-to-edge (COMPAT-03 — единственная реальная кодовая зона; проверить, есть ли обработка WindowInsets/onComputeInsets, нужна ли доработка для API 35+), password-подавление (InputAttributes.mIsPasswordField + shouldSuppressSuggestions уже есть; в MVP словаря нет → «без обучения» структурно бесплатно), no-composing-text (структурно обходит keyCode 229 — подтвердить), ландшафтные ресурсы (values-land/xml-land существуют).
- **Матрица (deferred UAT):** реальный прогон в Telegram/Chrome/WebView/пароль/MIUI/ландшафт — присоединяется к отложенному UAT-бандлу фаз 1–7.

## Предварительный скаутинг

- directBootAware="true" уже в манифесте (COMPAT-05 — вероятно WORKS, подтвердить полноту: и сервис, и прочие компоненты).
- InputAttributes: mIsPasswordField, shouldSuppressSuggestions — password-логика есть (COMPAT-01).
- Insets/edge-to-edge: grep нашёл только LatinIME.java и SettingsActivity — РЕСЕРЧУ выяснить, как форк обрабатывает onComputeInsets и WindowInsets на API 35+ (главная кодовая зона фазы; edge-to-edge форсирован с targetSdk 35+, наш compileSdk 37).
- Ландшафт: values-land, xml-land, sw600dp варианты есть.
- no-composing-text (решение проекта) — коммит сразу, deleteSurroundingText → структурно нейтрализует WebView keyCode 229 (COMPAT-02); подтвердить, что наш ввод действительно без composing.

## Claude's Discretion

- Что из COMPAT реально требует кода vs верификации — по результату ресерча.
- COMPAT-03 (insets API 35+) — вероятная единственная кодовая доработка; маршрут по ресерчу, минимальный дифф. Java-правки допустимы (не косметическая фаза).
- Формат письменного прохода матрицы (SC5) — как отложенный UAT-документ с чеклистом, часть исполняется в коде-проверках, часть — device-deferred.

## Deferred

- Реальный прогон InputConnection-матрицы на устройствах (Telegram/Chrome/WebView/пароль/MIUI/ландшафт) — отложенный UAT-бандл (принято).
- Актуальные known issues MIUI/HyperOS — research flag фазы (ресерчу проверить свежие).

---
*Phase: 08-sovmestimost*
*Context gathered: 2026-07-18*
