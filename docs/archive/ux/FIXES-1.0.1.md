# Fixes 1.0.1 — план исправлений по итогам первого on-device UAT (2026-07-20)

Контекст: первый прогон на устройстве (Samsung, One UI) выявил краш SettingsActivity и
пикера языков во всех билдах v1.0.0 (LocaleResourceUtils: getIdentifier по namespace вместо
applicationId → getString(0) crash), а также сброс выбранного языка после смерти процесса.
Код-фиксы уже лежат в рабочей копии (незакоммичены). Итоговое ревью (7 фронтов, адверсариальная
верификация) подтвердило их корректность. v1.0.0 публиковать нельзя — релизом становится 1.0.1.

## A. Код (незакоммиченные фиксы — закоммитить; новые правки — внести)

- [x] A1. Персистентность subtype: `pref_current_subtype` (немедленная запись при выборе),
  неперсистентный hint-locale свитч, миграция legacy-prefs `tt`→`tt_RU`, `ru:east_slavic`→`ru:russian`
  (RichInputMethodManager.java, Settings.java, SubtypePreferenceUtils.java) — сделано, закоммитить
- [x] A2. SettingsActivity: санитизация `EXTRA_SHOW_FRAGMENT` через isValidFragment,
  instanceof-гарды insets-хака (SettingsActivity.java) — сделано, закоммитить
- [x] A3. LocaleResourceUtils: `getIdentifier` по `context.getPackageName()` + guard `resId != 0`
  + обновление `sResourcePackageName` в `onLocalChange` — сделано, закоммитить
- [x] A4. SubtypeLocaleUtils: пропущенный `break` в `case LOCALE_SAKHA` (fall-through в Serbian) —
  подтверждён ревью; добавить `break` (одна строка)
- [x] A5. Версия: `versionName "1.0.1"`, `versionCode 2` (app/build.gradle)

## B. Документация (корень)

- [x] B1. CHANGELOG.md: секция `[1.0.1] — 2026-07-20` с тремя фиксами (краш настроек/пикера,
  персистентность языка, миграция prefs) + пометка у `[1.0.0]`: «не публиковался — критические
  баги найдены device-UAT до публикации»
- [x] B2. README.md: проверить утверждения про настройки/онбординг — после фиксов они снова
  верны, правки только если остались неточности; версию не хардкодит — ок

## C. Planning-артефакты (устаревший каталог планирования, удалён 2026-09-03) — привести в соответствие с реальностью

- [x] C1. REQUIREMENTS.md: аннотации к SETUP-02, SWITCH-02, UI-04 — «в v1.0.0 были де-факто
  сломаны крашем SettingsActivity/пикера (LocaleResourceUtils); исправлено в 1.0.1,
  подтверждено device-UAT 2026-07-20». Чекбоксы не снимать — требования выполнены в 1.0.1
- [x] C2. STATE.md: в Blockers/Concerns добавить запись о первом on-device UAT 2026-07-20:
  что подтверждено живьём (печать, пятый ряд, все long-press дубли, глобус-цикл, онбординг
  до «Готово»), какие баги найдены и исправлены (3 фикса), что ещё deferred (TalkBack-UAT,
  PERF-02/03 замеры, UAT-матрица фазы 8, MIUI)
- [x] C3. v1.0-MILESTONE-AUDIT.md: аддендум внизу — «Post-audit device-UAT 2026-07-20 выявил
  краш SettingsActivity во всех билдах v1.0.0; вердикт PASSED (complete-local) относился к
  структурной верификации; релизный артефакт — 1.0.1»

## D. Верификация (после A–C)

- [x] D1. `./gradlew assembleDebug assembleRelease` — обе зелёные
- [x] D2. `bash scripts/check-no-internet.sh` — оба уровня OK (по обоим APK)
- [x] D3. Размер release-APK ≤ 3 145 728 байт
- [x] D4. aapt2: `locale_name` в release ≥ 8, layout set tatar жив, versionCode=2 versionName=1.0.1

## E. Коммиты (после D, логическими группами, стиль git log проекта)

- [x] E1. `fix: персистентность выбранного subtype — pref_current_subtype, неперсистентный
  hint-свитч, миграция legacy-prefs` (RichInputMethodManager, Settings, SubtypePreferenceUtils)
- [x] E2. `fix: краш SettingsActivity/пикера языков — getIdentifier по applicationId, guard resId=0`
  (LocaleResourceUtils)
- [x] E3. `fix: устойчивость SettingsActivity к внешним интентам + insets-гарды` (SettingsActivity)
- [x] E4. `fix: пропущенный break в case Sakha реестра раскладок` (SubtypeLocaleUtils)
- [x] E5. `chore(release): версия 1.0.1 (versionCode 2)` (build.gradle)
- [x] E6. `docs: CHANGELOG 1.0.1, аннотации REQUIREMENTS/STATE/AUDIT по итогам device-UAT`
  (CHANGELOG, README при необходимости, артефакты планирования, docs/FIXES-1.0.1.md)

## Вне скоупа 1.0.1 (backlog, зафиксировано ревью)

- values-tt (татарская локализация UI) — кандидат в следующий майлстоун
- TalkBack: ACTION_LONG_CLICK для moreKeys-панели, озвучка языка на пробеле, live-region в SetupActivity
- Унаследованные minor отрисовки (DrawingPreviewPlacerView при смене темы, гонка dismiss-анимации,
  touch-noise фильтр PointerTracker)
- keep.xml: wildcard не защищает неиспользуемые locale_name_* — проверять при добавлении локалей
