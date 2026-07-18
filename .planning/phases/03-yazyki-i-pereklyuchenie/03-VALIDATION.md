---
phase: 3
slug: yazyki-i-pereklyuchenie
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build + aapt2 checks + grep (no unit-test framework — решение фазы 2 сохраняется: харнес для XML-раскладок и записей-в-реестр несоразмерен; runtime-поведение popup/цикла — только on-device) |
| **Config file** | none — Wave 0 покрыт инфраструктурой фазы 1 (`scripts/check-no-internet.sh` существует) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug && bash scripts/check-no-internet.sh` |
| **Estimated runtime** | ~60–120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew assembleDebug` (aapt2 = базовая валидация XML: merge, атрибуты moreKeys, ссылки @xml)
- **After every plan wave:** Run `./gradlew assembleDebug && bash scripts/check-no-internet.sh`
- **Before `/gsd-verify-work`:** Full suite green + aapt2 dump resources по APK содержит `*_russian` записи + diff-границы (Java = 1 файл, east_slavic нетронут)
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01.T1 | 03-01 | 1 | LAYOUT-02 (+F1, татарская) | 03-01/T1 | 10 литеральных moreKeys (5/4/1), ноль `!text/`, extra-ряд нетронут | build + grep | `./gradlew assembleDebug && grep -c 'latin:moreKeys' rowkeys_tatar{1,2,3}.xml` → 5/4/1 | ✅ (сборка фазы 1) | ⬜ pending |
| 03-01.T2 | 03-01 | 1 | LAYOUT-03 (ru), LAYOUT-02 (ru) | 03-01/T2 | Свой layout set russian без пятого ряда, moreKeys-паритет 5/4/1; shared east_slavic нетронут | build + aapt2 + grep | `./gradlew assembleDebug && aapt2 dump resources app-debug.apk \| grep russian && git diff --name-only -- '*east_slavic*'` пуст | ✅ | ⬜ pending |
| 03-01.T3 | 03-01 | 1 | SWITCH-01 (tt_RU, тройка), LAYOUT-03 (регистрация ru/en) | 03-01/T3 | tt_RU-константа; case ru→russian с break; east_slavic-группа = 4 case; тройка без MVP-хака/fallback | build + grep | `./gradlew assembleDebug && grep 'LOCALE_TATAR = "tt_RU"' … && ! grep 'subtypes.add(0,' SubtypeLocaleUtils.java` | ✅ | ⬜ pending |
| 03-01.T4 | 03-01 | 1 | SWITCH-01 (display name) | — | `tt_RU` в locale_exception_keys + locale_name_tt_RU «Татарча» | build + grep | `./gradlew assembleDebug && grep '<item>tt_RU</item>' donottranslate.xml && grep 'locale_name_tt_RU' strings.xml` | ✅ | ⬜ pending |
| 03-01.T5 | 03-01 | 1 | LAYOUT-02/03, SWITCH-01/02, Phase SC5 (on-device) | — | Три subtype, глобус-цикл, пикер, 10 дублей × 2 раскладки, персистентность, smoke-матрица | manual | — (checkpoint:human-verify) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Рабочая сборка (`./gradlew assembleDebug`) — есть с фазы 1; XML-валидация едет на ресурс-компиляции
- [x] `scripts/check-no-internet.sh` — есть с фазы 1; входит в verify каждой задачи
- [x] Татарская раскладка + реестровая запись tt (фаза 2) — база для Task 1 (moreKeys поверх существующих rowkeys) и Task 3 (смена значения константы)

*Новых Wave 0 зависимостей фаза не создаёт. SWITCH-01/02 не требуют кода вообще (штатные механизмы форка) — их «инфраструктура» уже в базе.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Три subtype в Languages: «Татарча», ru, «English (US)»; свежая установка открывается татарской | SWITCH-01 | Дефолты применяются при пустых prefs — только на чистой установке; display names рендерит LocaleResourceUtils в рантайме | `adb uninstall` → install → Languages (03-01 Task 5, пп. 1–2; Pitfall 4 — без uninstall дефолты не применятся) |
| Тап глобуса циклит tt→ru→en→tt; русская — ЙЦУКЕН БЕЗ пятого ряда; английская — QWERTY | LAYOUT-03, SWITCH-02 | Цикл и резолвинг layout set — runtime | Тапы по глобусу из текстового поля (Task 5, п. 3) |
| Long-press глобуса → пикер: три наших subtype + другие IME; выбор переключает | SWITCH-02 | AlertDialog форка — runtime UI | Long-press глобуса, выбрать пункт (Task 5, п. 4) |
| 10 long-press дублей (а→ә о→ө у→ү ж→җ н→ң х→һ э→ә г→һ + е→ё ь→ъ) на ОБЕИХ кириллических раскладках; shift → заглавные в popup | LAYOUT-02 (+F1, A2) | Невалидный moreKeys-спек падает при билде раскладки только в рантайме; popup/upcase — PointerTracker/MoreKeySpec runtime | Long-press каждой из 10 клавиш на tatar и russian; shift + long-press а → Ә (Task 5, пп. 5–7) |
| Активный subtype восстанавливается после force-stop | SWITCH-01 | resetSubtypeCycleOrder → prefs → reload — цепочка процессов | Переключить на ru → закрыть → `adb shell am force-stop` → открыть поле: русская (Task 5, п. 8) |
| Smoke-матрица SC5: переключение + ввод на трёх раскладках в Telegram, Chrome/WebView (keyCode 229), password-поле | Phase SC5 (cross-cutting STATE.md) | Зоопарк InputConnection — только в реальных редакторах | По фразе на каждой раскладке в трёх окружениях, ё/ъ через long-press; без потерь/дублей (Task 5, п. 9; MIUI — п. 10 при наличии Xiaomi) |

*Если устройство недоступно (как в фазах 1–2) — чекпойнт Task 5 откладывается в STATE.md Blockers с этим чек-листом; BUILD-критерии фазы закрыты автоматикой, но LAYOUT-02/03 и SWITCH-01/02 не считаются полностью верифицированными до прогона.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
