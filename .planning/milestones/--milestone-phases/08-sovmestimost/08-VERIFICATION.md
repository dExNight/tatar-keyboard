---
phase: 08-sovmestimost
verified: 2026-07-18T00:00:00Z
status: passed
previous_status: human_needed
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18, паттерн фаз 1–7)
score: 9/10 must-haves verified
behavior_unverified: 1
overrides_applied: 0
behavior_unverified_items:
  - truth: "On-device: матрица исполнена построчно (ландшафт без extract mode, API 35+ без перекрытия, Direct Boot PIN, MIUI/One UI при наличии)"
    test: "Установить app-debug.apk; пройти 08-UAT-MATRIX.md построчно: E1–E7 основной прогон S1–S8; E8 эмулятор API 35–36 gesture/3-button light/dark; E9 ландшафт (extract mode НЕ появляется); E10 ребут → PIN до разблокировки; E11/E12 при наличии Xiaomi/Samsung"
    expected: "Все DEFERRED-ячейки разрешены в +/FAIL/N-A; extract mode не появляется в ландшафте; нижний ряд не перекрыт панелями на API 35+; PIN вводится нашей клавиатурой до первой разблокировки; чек-боксы COMPAT-01..05 проставляются только после прогона"
    why_human: "Extract mode, перекрытие системными панелями, Direct Boot и вендорские killer'ы — свойства реального фреймворка/лончера/вендор-прошивки; grep пинует механизм, но не runtime-поведение; adb devices пуст"
human_verification:
  - test: "On-device исполнение 08-UAT-MATRIX.md (12 окружений × 8 сценариев, self-contained чеклист)"
    expected: "Ландшафт без extract mode (проверка флипа вживую), API 35+ edge-to-edge чисто, Direct Boot PIN, WebView/password-ввод, MIUI/One UI при наличии"
    why_human: "Runtime-совместимость проверяема только на устройстве/эмуляторе — недоступна grep/сборке"
---

# Phase 8: Совместимость Verification Report

**Phase Goal:** Клавиатура корректна в проблемных окружениях — полный проход тестовой матрицы InputConnection.
**Verified:** 2026-07-18
**Status:** human_needed (on-device UAT accepted-deferred per standing pattern фаз 1–7)
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth   | Status     | Evidence       |
| --- | ------- | ---------- | -------------- |
| 1 | COMPAT-04 гэп закрыт данными: values-land `config_use_fullscreen_mode` true→false — единственная правка под app/; инвариант true=0/false=5 | ✓ VERIFIED | grep `">true` в res/ → 0 вхождений; `">false` → ровно 5 файлов (values, values-land, sw430/600/768); дифф d2ae619..HEAD по app/ = 1 строка `true</bool>` → `false</bool>` (коммит 4088f50) |
| 2 | COMPAT-03 без кода: insets-линия полностью в базе upstream'ом (827da4f, 2885ae5) | ✓ VERIFIED | `fitsSystemWindows="true"` layout-v28/input_view.xml:32; базовый layout/ — 0 вхождений (сознательный v28-сплит); `requestApplyInsets` LatinIME.java:333; `public void onComputeInsets` :535; `setNavigationBarContrastEnforced(false)` :935 |
| 3 | COMPAT-02 без кода: composing-текста ноль; commitText/deleteSurroundingText-пути на месте | ✓ VERIFIED | grep `setComposingText\|setComposingRegion\|finishComposingText` по app/src/main/java → 0 вхождений; `mConnection.commitText(...newSingleCodePointString...)` InputLogic.java:606; `mIC.deleteSurroundingText(numChars, 0)` RichInputConnection.java:356 |
| 4 | COMPAT-01 без кода: словаря/обучения нет вообще; password-гейт на месте | ✓ VERIFIED | 0 файлов `*Dictionary*`/`*UserHistory*`; 0 строк `import.*Dictionary`; 0 `personaliz`; `mIsPasswordField` InputAttributes.java:34,49; гейт `!settingsValues.mInputAttributes.mIsPasswordField` InputLogic.java:355 |
| 5 | COMPAT-05 без кода: directBootAware + все prefs через device-protected storage | ✓ VERIFIED | `android:directBootAware="true"` AndroidManifest.xml:31; `createDeviceProtectedStorageContext` PreferenceManagerCompat.java:25; `getDefaultSharedPreferences` вне compat-класса → 0 call-sites |
| 6 | SC5 имеет письменный артефакт: 08-UAT-MATRIX.md — 12×8, CLOSED-STRUCTURAL со ссылками, DEFERRED self-contained, ни одного PASS | ✓ VERIFIED | Файл существует; env-строк E1–E12 = 12; сценариев S1–S8 = 8; CS-1..CS-5 = 5 строк, каждая с грепом/файл:строкой/коммитом в колонке «Доказательство»; DEFERRED ×96 (runtime-ячейки); литеральных строк «PASS» в документе — 0 (анти-фабрикация; легенда device-прогона использует `+`) |
| 7 | Java/Kotlin-дифф фазы = 0 строк — zero-Java boundary от d2ae619 | ✓ VERIFIED | `git diff --name-only d2ae619..HEAD -- app/` = ровно `app/src/main/res/values-land/config.xml`; `.java`/`.kt` в диффе → 0; содержимое диффа = 1 строка bool-флипа |
| 8 | assembleDebug + assembleRelease зелёные; check-no-internet exit 0 | ✓ VERIFIED | BUILD SUCCESSFUL (оба таргета, exit 0); check-no-internet Level 1 (source manifest) + Level 2 (built APK) OK, exit 0; release-APK 702 КБ ≤ 3 МБ |
| 9 | Bookkeeping: 5 аннотаций COMPAT, чек-боксы пусты, Traceability ×5 = Verifying, decision [08-01], Blockers Phase 8, ROADMAP Progress | ✓ VERIFIED | REQUIREMENTS.md: `Verifying (08-01` ×5, `- [ ] **COMPAT-0[1-5]**` ×5 (checked ×0), аннотаций со ссылкой на 08-RESEARCH.md ×5; STATE.md:62 decision [08-01], :92 Blocker ⚠️[Phase 8, plan 08-01]; ROADMAP.md:146 Progress-строка 08-01 complete-local |
| 10 | On-device: матрица исполнена построчно — human-verified Task 4 ИЛИ отложено в STATE.md Blockers как фазы 1–7 | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | Механизмы всех 5 COMPAT запинованы грепами; runtime-поведение (extract mode, панели, Direct Boot PIN, MIUI) недоступно статике; adb devices пуст; деферрал записан в STATE.md Blockers ⚠️[Phase 8, plan 08-01] со ссылкой на 08-UAT-MATRIX.md — принят по standing-схеме |

**Score:** 9/10 truths verified (1 present, behavior-unverified — on-device UAT accepted-deferred)

### Required Artifacts

| Artifact | Expected    | Status | Details |
| -------- | ----------- | ------ | ------- |
| `app/src/main/res/values-land/config.xml` | config_use_fullscreen_mode=false; соседние ключи нетронуты | ✓ VERIFIED | bool=false; `config_default_keyboard_height` 176dp, `config_min_keyboard_height` 45%p, `config_key_vertical_gap_5row` 3.864%p, `config_key_bonus_height_5row` 10%p — все на месте, дифф их не касается |
| `.planning/phases/08-sovmestimost/08-UAT-MATRIX.md` | письменный деливерабл SC5: полная матрица, CLOSED-STRUCTURAL + DEFERRED, self-contained чеклист | ✓ VERIFIED | 12 окружений × 8 сценариев; легенда статусов; честная преамбула (двухуровневая честность); § Структурные закрытия CS-1..5 со ссылками; § DEFERRED-чеклист с подготовкой, порядком E1–E12, спец-блоками и пост-прогон шагами (вкл. простановку чек-боксов только после прогона) |

### Key Link Verification

| From | To  | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| onEvaluateFullscreenMode | false во всех конфигурациях | Settings.readUseFullscreenMode → R.bool.config_use_fullscreen_mode | ✓ WIRED | после флипа все 5 qualifier'ов = false; Java-override не писался |
| setInputView | framework-паддинг под systemBars | requestApplyInsets (LatinIME:333) → fitsSystemWindows (layout-v28) | ✓ WIRED | v28-сплит: API 24–27 без флага (сознательно) |
| ввод буквы / backspace | InputConnection без composing | commitText (InputLogic:606) / deleteSurroundingText (RichInputConnection:356) | ✓ WIRED | composing-API в коде не существует (0 вхождений) |
| LatinIME.onCreate | device-protected prefs | PreferenceManagerCompat.getDeviceSharedPreferences → createDeviceProtectedStorageContext | ✓ WIRED | обходных getDefaultSharedPreferences ноль |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Debug + Release сборка | `./gradlew assembleDebug assembleRelease` | BUILD SUCCESSFUL (exit 0) | ✓ PASS |
| Приватность (no INTERNET) | `bash scripts/check-no-internet.sh` | Level 1+2 OK, exit 0 | ✓ PASS |
| Fullscreen-инвариант | grep true/false по res/ | true=0, false=5 | ✓ PASS |
| Zero-Java boundary | `git diff --name-only d2ae619..HEAD -- app/` | ровно values-land/config.xml; .java/.kt = 0 | ✓ PASS |
| Анти-фабрикация матрицы | `grep -c 'PASS' 08-UAT-MATRIX.md` | 0 | ✓ PASS |
| Runtime-матрица (E1–E12) | on-device | недоступно (adb пуст) | ? SKIP → human |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| COMPAT-01 | 08-01 | Password-поля: корректный ввод, без подсказок/обучения | ? NEEDS HUMAN (структурно SATISFIED) | ноль словаря/обучения + mIsPasswordField-гейт verified; ввод в реальном password-поле — on-device (E4/E5) |
| COMPAT-02 | 08-01 | WebView/Chrome, сценарий keyCode 229 | ? NEEDS HUMAN (структурно SATISFIED) | no-composing (0 вхождений) + commitText/deleteSurroundingText verified; реальный Chrome/contenteditable — on-device (E2/E3) |
| COMPAT-03 | 08-01 | Edge-to-edge/WindowInsets API 35+ | ? NEEDS HUMAN (структурно SATISFIED) | insets-линия upstream (v28-сплит, requestApplyInsets, onComputeInsets, contrast off) verified; визуально — эмулятор API 35–36 (E8, исполним раньше бандла — A4) |
| COMPAT-04 | 08-01 | Ландшафтная ориентация | ? NEEDS HUMAN (структурно SATISFIED) | флип fullscreen=false ×5 + 5row-фракции values-land verified; «extract mode не появляется» вживую — on-device (E9) |
| COMPAT-05 | 08-01 | directBootAware: доступна до первой разблокировки | ? NEEDS HUMAN (структурно SATISFIED) | манифест + device-protected prefs без обходов verified; PIN после ребута — on-device (E10) |

Traceability: COMPAT-01..05 = `Verifying (08-01: structural PASS; on-device UAT deferred)` ×5. Чек-боксы корректно НЕ проставлены до UAT.

### Prohibitions Verified (must-NOT)

| Prohibition | Status | Evidence |
| ----------- | ------ | -------- |
| MUST NOT править Java/Kotlin (data/docs phase, дифф = 1 файл) | ✓ HELD | дифф app/ = ровно values-land/config.xml; .java/.kt = 0 |
| MUST NOT трогать работающие механизмы (layout-v28, layout/, LatinIME, InputLogic, RichInputConnection, InputAttributes, манифест) | ✓ HELD | ни один из файлов не в диффе d2ae619..HEAD; все — только объекты грепов |
| MUST NOT ставить PASS в device-ячейки / чек-боксы COMPAT до прогона | ✓ HELD | «PASS» в матрице — 0 строк; чек-боксы COMPAT-01..05 пусты ×5 |
| MUST NOT: новые зависимости / INTERNET / Apple-ассеты / правки sw-конфигов и остальных ключей values-land | ✓ HELD | дифф не касается build.gradle/манифеста/sw-конфигов; высоты и 5row-фракции values-land нетронуты; check-no-internet OK |

### Anti-Patterns Found

None. Правка фазы — один bool в ресурсе; 08-UAT-MATRIX.md — чистые данные с двухуровневой легендой честности; debt-маркеров в изменённых файлах нет.

### Human Verification Required

On-device UAT (accepted-deferred по standing-схеме фаз 1–7, записано в STATE.md Blockers ⚠️[Phase 8, plan 08-01]; исполняемый чеклист — self-contained 08-UAT-MATRIX.md):

1. **E9 ландшафт (COMPAT-04):** поворот → пятый ряд на месте, **extract mode НЕ появляется** (проверка флипа вживую) — особенно телефон < sw430dp.
2. **E8 эмулятор API 35–36 (COMPAT-03):** gesture-nav + 3-button, light/dark — нижний ряд не перекрыт, фон под баром = фон клавиатуры; исполним отдельно, раньше бандла (A4).
3. **E2/E3 WebView/keyCode 229 (COMPAT-02):** Chrome адресная строка, форма, contenteditable — полный цикл S1–S8 без потерь/дублей.
4. **E4/E5 password/PIN (COMPAT-01):** маска-точки и numberPassword — ввод корректен, double-space не срабатывает, подсказок нет.
5. **E10 Direct Boot (COMPAT-05):** ребут → PIN вводится нашей клавиатурой ДО первой разблокировки; после разблокировки prefs не потеряны.
6. **E11/E12 MIUI/One UI:** при наличии устройств; иначе честный N/A с оговоркой.
7. **После прогона:** разрешить DEFERRED-ячейки, проставить чек-боксы COMPAT-01..05, снять Blocker Phase 8, обновить Traceability.

**Why human:** extract mode, перекрытие системными панелями, Direct Boot и вендорские killer'ы — свойства реального фреймворка/лончера/вендор-прошивки, недоступные grep/сборке.

### Gaps Summary

Нет блокирующих гэпов. Вся кодовая работа фазы (1 строка values-land) на месте и запинована инвариантом true=0/false=5; все пять COMPAT-вердиктов ресерча доказаны live-грепами и защищены от регрессий; обе сборки и приватность-чек зелёные; zero-Java boundary механически чист (дифф = ровно один XML); все 4 запрета соблюдены; письменный деливерабл SC5 (08-UAT-MATRIX.md) существует в честном двухуровневом состоянии — 12×8, CS-1..5 со ссылками, 96 DEFERRED-ячеек, ноль фабрикованных PASS. Единственный незакрытый пункт — on-device runtime-прогон матрицы, недоступный статике; устройство отсутствует (adb пуст), деферрал записан и принят по устоявшейся схеме фаз 1–7. Статус human_needed отражает этот UAT-хвост, а не дефект реализации.

---

_Verified: 2026-07-18_
_Verifier: Claude (gsd-verifier)_
