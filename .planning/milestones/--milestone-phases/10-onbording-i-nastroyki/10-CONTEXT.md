# Phase 10: Онбординг и настройки — Context

**Gathered:** 2026-07-19 (autonomous run)
**Status:** Ready for planning

<domain_boundary>
Онбординг: экран из двух шагов «Включить клавиатуру» → «Выбрать клавиатуру» со статусами (SETUP-01); настройки звука/вибрации, реально влияющие на отклик (SETUP-02). НЕ входят: замеры/CI (11), новые фичи ввода, ребрендинг строк за пределами онбординга (полный ребрендинг «Simple Keyboard» — фаза 11 / backlog).
</domain_boundary>

## Phase Scope

**Requirements:** SETUP-01, SETUP-02. SC3: чистая установка → от иконки до «ә» в мессенджере без подсказок (device). SC4: smoke не деградировал.

## Предварительный скаутинг

- **SETUP-01 = главная кодовая работа фазы**: пакета setup/ в форке НЕТ (rkkr вырезал AOSP SetupWizardActivity); сейчас лишь диалог с текстом «Simple Keyboard is not enabled…» (strings.xml:126). Нужен онбординг-экран: 2 шага со статусами (шаг 1: InputMethodManager enabled? → ACTION_INPUT_METHOD_SETTINGS; шаг 2: выбран текущим? → showInputMethodPicker), лаунчер-иконка ведёт туда (или в Settings при завершённом онбординге). AOSP SetupWizard как референс (Apache-2.0), но минимализм: один Activity, без анимаций-видео AOSP.
- **SETUP-02 почти готово**: KeyPressSettingsFragment уже управляет PREF_VIBRATE_ON (с removePreference если нет вибратора), PREF_SOUND_ON, громкостью; AudioAndHapticFeedbackManager реагирует (фаза 7 подтвердила). Работа: верификация + возможно упрощение/видимость (тумблеры доступны из SettingsActivity)?
- **Compose допустим в Activity настроек** (решение проекта) — но существующие настройки на PreferenceFragment; онбординг-экран: обычный View/XML легче и консистентнее (Compose тянет зависимости — бюджет APK!). Решение за ресерчем/планом: РЕКОМЕНДАЦИЯ — без Compose, чтобы не раздувать APK (+1.5+ МБ Compose runtime не лезет в бюджет 3 МБ!).
- **Правовое**: онбординг-тексты — свои, на русском (+en base); упоминание «Simple Keyboard» в setup_message менять на наше имя ТОЛЬКО в наших новых строках; полный ребрендинг — вне фазы.

## Claude's Discretion

- Архитектура онбординг-экрана (1 Activity, статусы шагов по onResume-проверкам, иконка приложения → онбординг пока не завершён; детект «выбран текущим» через Settings.Secure DEFAULT_INPUT_METHOD или InputMethodManager).
- Тексты онбординга (ru + en base, лаконичные).
- Судьба лаунчер-иконки после онбординга (вести в SettingsActivity — стандарт).

## Deferred

- SC3 чистая установка (device) — отложенный UAT-бандл (принято).
- Полный ребрендинг ~30 локализованных setup_message — backlog/фаза 11.
- Compose — отклонён для этой фазы по бюджету APK (жёсткий лимит 3 МБ).

---
*Phase: 10-onbording-i-nastroyki*
*Context gathered: 2026-07-19*
