---
phase: 07-ios-skin-preview-panel
verified: 2026-07-18T00:00:00Z
status: passed
previous_status: human_needed
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18)
score: 9/10 must-haves verified
behavior_unverified: 1
overrides_applied: 0
behavior_unverified_items:
  - truth: "On-device: баллон мгновенно на down без обрезки (пятый ряд ә + края й/ъ), slide-select панели, хаптика/подсветка на касании, звук off по умолчанию и появляется при включении pref, smoke (Telegram/WebView 229/password)"
    test: "Установить app-debug.apk на устройство; печатать буквы (вкл. пятый ряд ә и крайние колонки й/ъ/һ), long-press → панель со скольжением, проверить хаптику/подсветку на касании, звук default off → sound_on → on, vibrate_on toggle; smoke в Telegram/Chrome WebView(229)/password; регрессии фаз 2–6"
    expected: "Баллон появляется в момент ACTION_DOWN (не отпускания), iOS-вид light/dark, не обрезан у краёв/пятого ряда/MIUI; панель со slide-select, подсветка следует за пальцем; вибрация+подсветка на касании; звук off по умолчанию, появляется при sound_on; ввод не деградировал"
    why_human: "Латентность кадра, пиксельная обрезка у краёв реального экрана, тактильная хаптика и slide-ощущение — свойства рендер-пайплайна и вибромотора устройства; grep видит трассу кода, но не runtime-поведение"
human_verification:
  - test: "On-device UAT баллона/панели/отклика (полный чек-лист SC1–SC4 + smoke + регрессии)"
    expected: "Баллон мгновенно на down без обрезки; slide-select; хаптика/подсветка на касании; звук default off; smoke Telegram/WebView229/password чисто"
    why_human: "Runtime-свойства рендера и вибромотора устройства — недоступны grep/сборке"
---

# Phase 7: iOS-скин — превью, панель, отклик Verification Report

**Phase Goal:** «Живость» iOS-клавиатуры: мгновенный баллон-превью, long-press панель альтернатив, хаптика и звук на ACTION_DOWN.
**Verified:** 2026-07-18
**Status:** human_needed (on-device UAT accepted-deferred per standing pattern фаз 1–6)
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth   | Status     | Evidence       |
| --- | ------- | ---------- | -------------- |
| 1 | Превью УЖЕ in-layer в базе форка (DrawingPreviewPlacerView в android.R.id.content окна IME); PopupWindow отсутствует в java-исходниках | ✓ VERIFIED | `grep -rn PopupWindow app/src/main/java` → NONE; MainKeyboardView.java:299 `installPreviewPlacerView`, :305 `findViewById(android.R.id.content)`, :338 `placeAndShowKeyPreview` |
| 2 | Панель — тот же in-layer placer (panel.showInParent); slide-to-select через handoff moreKeysPanel.onDownEvent | ✓ VERIFIED | MainKeyboardView.java:453 `panel.showInParent(mDrawingPreviewPlacerView)`; PointerTracker.java:781 `moreKeysPanel.onDownEvent(translatedX,...)` |
| 3 | Отклик на ACTION_DOWN: onDownEventInternal → onPressKey → hapticAndAudioFeedback И setPressedKeyGraphics | ✓ VERIFIED | onDownEventInternal содержит `callListenerOnPressAndCheckKeyboardLayoutChange` + `setPressedKeyGraphics`; LatinIME.onPressKey → `hapticAndAudioFeedback` |
| 4 | Хаптика EFFECT_CLICK (≥Q) как эквивалент KEYBOARD_TAP (<Q); аннотация в REQUIREMENTS.md UI-04 | ✓ VERIFIED | AudioAndHapticFeedbackManager.java:126 `EFFECT_CLICK`, :129 `KEYBOARD_TAP`; REQUIREMENTS.md UI-04 аннотация присутствует |
| 5 | Звук по умолчанию ВЫКЛЮЧЕН — config_default_sound_enabled=false НЕ флипнут; prefs vibrate_on/sound_on wired | ✓ VERIFIED | config-per-form-factor.xml:25 `false`; Settings.java:51-52 PREF_VIBRATE_ON/PREF_SOUND_ON, :208/:215 читаются |
| 6 | iOS-баллон + панель: 3 новых drawable (+F-1 fix) + wiring в themes-tatar.xml | ✓ VERIFIED | ios_key_preview_background (layer-list roundRect ios_key_normal + 1dp ios_key_shadow, 5dp), ios_popup_panel_background (5dp, padding 5dp), ios_popup_key_background (selector, 5dp); themes-tatar.xml:60/79/80 |
| 7 | Java/Kotlin-дифф фазы = 0; общие drawable и 6 старых тем нетронуты | ✓ VERIFIED | `git diff fbfd66a..HEAD -- app/ \| grep .java\|.kt` → NONE; legacy shared drawables 0 в диффе; 6 legacy-тем не в диффе (только themes-tatar.xml) |
| 8 | assembleDebug + assembleRelease зелёные; check-no-internet exit 0 | ✓ VERIFIED | BUILD SUCCESSFUL (оба); check-no-internet Level 1+2 OK, exit 0 |
| 9 | REQUIREMENTS.md: аннотации UI-02/UI-04, чек-боксы НЕ проставлены, Traceability ×3 = Verifying; STATE.md decision [07-01] | ✓ VERIFIED | UI-02/03/04 все `[ ]`; 2 аннотации; 3× `Verifying (07-01`; STATE.md [07-01] + Blocker Phase 7 UAT |
| 10 | On-device: мгновенность/обрезка/slide/хаптика + smoke — human-verified Task 3 ИЛИ отложено в STATE.md Blockers как фазы 1–6 | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | Код + wiring на месте (трасса запинована); runtime-поведение недоступно grep; деферрал записан в STATE.md Blockers ⚠️[Phase 7, plan 07-01] — принят по standing-схеме |

**Score:** 9/10 truths verified (1 present, behavior-unverified — on-device UAT accepted-deferred)

### Required Artifacts

| Artifact | Expected    | Status | Details |
| -------- | ----------- | ------ | ------- |
| `app/src/main/res/drawable/ios_key_preview_background.xml` | layer-list roundRect ios_key_normal + 1dp ios_key_shadow, 5dp | ✓ VERIFIED | Две shape-слоя (тень top=1dp / баллон bottom=1dp), радиус 5dp обоих; size 45×5dp + padding bottom 60dp зеркалят legacy — геометрия превью сохранена |
| `app/src/main/res/drawable/ios_popup_panel_background.xml` | roundRect ?attr/popupPanelBackgroundColor, 5dp, padding 5dp | ✓ VERIFIED | solid `?attr/popupPanelBackgroundColor`, corners 5dp, padding 5dp по кругу |
| `app/src/main/res/drawable/ios_popup_key_background.xml` (F-1 fix) | selector pressed ios_popup_key_pressed / transparent, 5dp | ✓ VERIFIED | selector: pressed-shape 5dp цвета ios_popup_key_pressed + transparent fallback |
| `app/src/main/res/values/themes-tatar.xml` | 3 item wiring (preview + panel bg + panel keyBackground) | ✓ VERIFIED | :60 keyPreviewBackground, :79 android:background (panel), :80 keyBackground (F-1) |

### Key Link Verification

| From | To  | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| MainKeyboardView.Tatar | ios_key_preview_background | keyPreviewBackground item | ✓ WIRED | themes-tatar.xml:60 |
| MoreKeysKeyboardView.Tatar | ios_popup_panel_background | android:background item | ✓ WIRED | themes-tatar.xml:79 |
| MoreKeysKeyboardView.Tatar | ios_popup_key_background | keyBackground item (F-1) | ✓ WIRED | themes-tatar.xml:80 |
| ACTION_DOWN | hapticAndAudioFeedback + setPressedKeyGraphics | onDownEventInternal → onPressKey | ✓ WIRED | обе ветки на down (PointerTracker + LatinIME) |

### Color Parity (light/night)

| Color | Light | Night | Status |
| ----- | ----- | ----- | ------ |
| ios_popup_key_pressed | #A2A6B0 | #7D7D7D | ✓ PARITY (values + values-night) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Debug + Release сборка | `./gradlew assembleDebug assembleRelease` | BUILD SUCCESSFUL | ✓ PASS |
| Приватность (no INTERNET) | `bash scripts/check-no-internet.sh` | Level 1+2 OK, exit 0 | ✓ PASS |
| Zero-Java boundary | `git diff --name-only fbfd66a..HEAD -- app/ \| grep .java\|.kt` | NONE | ✓ PASS |
| Runtime баллон/панель/хаптика | on-device | недоступно (adb пуст) | ? SKIP → human |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| UI-02 | 07-01 | Баллон-превью in-layer на нажатии | ? NEEDS HUMAN (структурно SATISFIED) | drawable+wiring+in-layer трасса verified; мгновенность на down — on-device |
| UI-03 | 07-01 | Long-press панель с выбором скольжением | ? NEEDS HUMAN (структурно SATISFIED) | panel bg + F-1 keyBackground + slide-handoff verified; ощущение — on-device |
| UI-04 | 07-01 | Реакция/хаптика/звук на ACTION_DOWN, отключаемы | ? NEEDS HUMAN (структурно SATISFIED) | down-цепочка + EFFECT_CLICK/KEYBOARD_TAP + prefs verified; тактильно — on-device |

Traceability: UI-02/03/04 = `Verifying (07-01: structural PASS; on-device UAT deferred)` ×3. Чек-боксы корректно НЕ проставлены до UAT.

### Prohibitions Verified (must-NOT)

| Prohibition | Status | Evidence |
| ----------- | ------ | -------- |
| MUST NOT править Java/Kotlin (zero-Java) | ✓ HELD | 0 .java/.kt в диффе fbfd66a..HEAD |
| MUST NOT вводить PopupWindow | ✓ HELD | grep PopupWindow → NONE |
| MUST NOT флипать config_default_sound_enabled | ✓ HELD | =false pinned |
| MUST NOT трогать общие drawable / 6 старых тем / INTERNET / Apple-ассеты | ✓ HELD | legacy 0 в диффе; check-no-internet OK; дифф = только Tatar-ресурсы |

### Anti-Patterns Found

None. Все три drawable — чистые данные с лицензионным заголовком и документирующими комментариями; никаких debt-маркеров (TODO/FIXME/XXX) в изменённых файлах фазы.

### Review Debt

- 07-REVIEW.md: **PASS**, блокеров нет.
- F-1 (medium, невидимая slide-подсветка в light): **FIXED** — ios_popup_key_background.xml + пара цветов ios_popup_key_pressed (#A2A6B0/#7D7D7D), подключён к MoreKeysKeyboardView.Tatar.keyBackground; подтверждено на диске и в теме.
- F-3 (info, 6dp radius): **CLOSED** вместе с F-1 (новый drawable рисует pressed 5dp).
- F-2 (info, intrinsic height): **NO ACTION** (инертно по трассе, заметка для UAT).
- Carry-over: прицельная проверка различимости #A2A6B0 в light на устройстве — включена в UAT-чек-лист.

### Human Verification Required

On-device UAT bundle (accepted-deferred по standing-схеме фаз 1–6, записано в STATE.md Blockers ⚠️[Phase 7, plan 07-01]):

1. **SC1 баллон (UI-02):** появляется мгновенно на ACTION_DOWN (не отпускании); light — белый + 1dp-тень, dark — #6B6B6B; исчезает при отпускании.
2. **SC1/SC4 края:** баллон пятого ряда (ә) и крайних колонок (й, ъ/э/һ) не обрезан; MIUI/HyperOS особо.
3. **SC2 панель (UI-03):** long-press → панель, slide-select без отрыва, подсветка следует, уход = отмена; различимость #A2A6B0 в light (F-1 carry-over).
4. **SC3 отклик (UI-04):** вибрация+подсветка на касании; звук default off → sound_on → on; vibrate_on toggle.
5. **SC4 smoke:** Telegram, Chrome WebView (keyCode 229), password; регрессии фаз 2–6.

**Why human:** латентность кадра, пиксельная обрезка, тактильная хаптика и slide-ощущение — свойства рендер-пайплайна и вибромотора реального устройства.

### Gaps Summary

Нет блокирующих гэпов. Вся кодовая работа фазы (zero-Java: 3 drawable + 3 item в themes-tatar.xml + пара цветов) присутствует, структурно корректна и запинована fail-capable-грепами; обе сборки и приватность-чек зелёные; boundary чист; все 4 запрета соблюдены; review-долг закрыт (F-1 fixed, F-3 closed). Единственный незакрытый пункт — on-device runtime-поведение SC1–SC4 (мгновенность, обрезка, slide-ощущение, тактильная хаптика), недоступное статическим проверкам; устройство отсутствует (adb пуст), деферрал записан и принят по устоявшейся схеме фаз 1–6. Статус human_needed отражает этот UAT-хвост, а не дефект реализации.

---

_Verified: 2026-07-18_
_Verifier: Claude (gsd-verifier)_
