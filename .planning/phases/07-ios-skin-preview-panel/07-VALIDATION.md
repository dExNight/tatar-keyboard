---
phase: 7
slug: ios-skin-preview-panel
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 7 — Validation Strategy

> Per-phase validation contract. Фаза — zero-Java: вся кодовая работа = 2 новых drawable + 2 item в themes-tatar.xml; остальное — fail-capable-верификация вердиктов ресерча (in-layer превью/панель, отклик на ACTION_DOWN, prefs) + пин решения пользователя (sound default OFF). Автоматика = сборка debug+release + грепы + boundary-diff; мгновенность баллона, обрезка краями, slide-ощущение и хаптика — только on-device.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build (debug + release) + grep/sed + git diff — решение фаз 2–6 сохраняется: юнит-харнеса нет; визуал и тактильный отклик доказываются только на устройстве |
| **Config file** | none — Wave 0 покрыт инфраструктурой фазы 1 (`scripts/check-no-internet.sh`) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh` |
| **Estimated runtime** | ~90–180 seconds |

---

## Sampling Rate

- **After every task commit:** `./gradlew assembleDebug`
- **After Task 2:** full suite + все fail-capable-грепы + boundary-diff (`git diff --name-only fbfd66a..HEAD -- app/` ⊆ объявленные XML; ноль `.java`/`.kt`)
- **Before `/gsd-verify-work`:** full suite green + все грепы Task 1–2
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 07-01.T1 | 07-01 | 1 | UI-02 (стилизация баллона), UI-03 (косметика панели) | T1 | `ios_key_preview_background.xml` (roundRect ios_key_normal + 1dp-тень, радиус семейства 5dp) и `ios_popup_panel_background.xml` существуют и подключены к теме Tatar; общие drawable и 6 старых тем нетронуты; все цвета через ресурсы/атрибуты | build + grep + git diff | verify-команда Task 1 (файлы + wiring-грепы + diff-чек нетронутости общих drawable) | ✅ (сборка фазы 1) | ⬜ pending |
| 07-01.T2 | 07-01 | 1 | UI-02/03/04 (вердикты ресерча) + boundary + bookkeeping | T2, T3 | In-layer запинован (ноль PopupWindow, placer → android.R.id.content); панель in-layer + slide-handoff; down-цепочка отклика; EFFECT_CLICK/KEYBOARD_TAP; prefs wired; `config_default_sound_enabled=false` НЕ флипнут; zero-Java boundary; night-parity новых цветов; аннотации + Traceability ×3 + decision | build + grep/sed + git diff | verify-команда Task 2 (полный греп-пакет + boundary от fbfd66a + bookkeeping-грепы) | ✅ | ⬜ pending |
| 07-01.T3 | 07-01 | 1 | UI-02/03/04 + Phase SC1–SC4 (on-device) | T1 | Баллон мгновенно на down без обрезки (пятый ряд + края), панель со скольжением, хаптика/подсветка на касании, звук default off и появляется при pref, smoke-матрица | manual | — (checkpoint:human-verify, стандартная отложенная схема фаз 1–6) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Рабочая сборка (`./gradlew assembleDebug` / `assembleRelease`) — с фазы 1
- [x] `scripts/check-no-internet.sh` — с фазы 1
- [x] Инфраструктура превью/панели форка (KeyPreviewChoreographer, DrawingPreviewPlacerView, MoreKeysKeyboardView, AudioAndHapticFeedbackManager) — объект верификации, не создания (вердикт ресерча: WORKS)
- [x] Тема id=7 «Tatar» + палитра ios_* light/night (фаза 6) — объект расширения двумя item

*Новых Wave 0 зависимостей нет.*

---

## Manual-Only Verifications

On-device UAT (Task 3, отложенная схема при недоступном устройстве — как фазы 1–6):

1. **SC1 баллон (UI-02):** появляется мгновенно НА КАСАНИИ (down, не up); iOS-вид: light — белый с 1dp-тенью, dark — #6B6B6B; исчезает при отпускании.
2. **SC1/SC4 края:** баллон пятого ряда (ә — верхний ряд, рисуется над клавиатурой в прозрачной зоне окна IME) и крайних колонок (левая й, правая ъ/э/һ — клампинг сдвигает внутрь) НЕ обрезан; MIUI/HyperOS — особо, при наличии Xiaomi.
3. **SC2 панель (UI-03):** long-press а → панель с ә; скольжение без отрыва — подсветка следует, отпускание коммитит; уход в сторону — отмена; iOS-палитра панели.
4. **SC3 отклик (UI-04):** вибрация + подсветка в момент касания; звук по умолчанию НЕ звучит (решение: default off) — при включении pref `sound_on` появляется на касании; `vibrate_on` выключаем/включаем — эффект следует.
5. **Smoke-матрица:** пп. 1–4 в Telegram, Chrome WebView (keyCode 229), password-поле; MIUI при наличии (иначе пометить как не покрыто).
6. **Регрессии фаз 2–6:** печать, глобус, shift, double-space, свайп-курсор — без аномалий.

**Почему без автоматики:** мгновенность (латентность кадра), пиксельная обрезка у краёв реального экрана, тактильная хаптика и slide-ощущение — свойства рендер-пайплайна и вибромотора устройства; instrumented/скриншот-тесты несоразмерны соло-MVP (решение фаз 2–6 сохраняется). Структурная сторона каждого пункта (трасса кода, prefs, дефолты) запинована грепами Task 2.

---

## Boundary Contract

- База дифа: **fbfd66a** (docs-коммит ресерча фазы 7 — последний коммит до кода фазы; последующие docs-коммиты app/ не трогают).
- Разрешённые файлы под `app/`: `res/drawable/ios_key_preview_background.xml` (новый), `res/drawable/ios_popup_panel_background.xml` (новый), `res/values/themes-tatar.xml`; условно — dimens-файл при подстройке геометрии баллона (A3, с записью в SUMMARY).
- Запрещено: любые `.java`/`.kt` (zero-Java phase), PopupWindow, флип `config_default_sound_enabled`, общие drawable (`keyboard_key_feedback_background.xml`, `keyboard_popup_panel_background.xml`, `btn_keyboard_key_popup`), 6 старых тем, манифест, зависимости, ассеты Apple.
- Чеки:
  - `[ -z "$(git diff --name-only fbfd66a..HEAD -- 'app/' | grep -v -E 'ios_key_preview_background\.xml|ios_popup_panel_background\.xml|themes-tatar\.xml|dimens')" ]`
  - `[ -z "$(git diff --name-only fbfd66a..HEAD -- 'app/' | grep -E '\.(java|kt)$')" ]`
  - `! grep -rq 'PopupWindow' app/src/main/java`
  - `grep -q 'config_default_sound_enabled">false' app/src/main/res/values/config-per-form-factor.xml`
