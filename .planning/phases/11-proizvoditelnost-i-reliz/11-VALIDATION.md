# Phase 11 — Validation (Nyquist coverage + source audit)

**Plan:** 11-01-PLAN.md
**Created:** 2026-07-19
**Framework:** нет unit-фреймворка (standing-паттерн фаз 1–10: `assembleDebug`/`assembleRelease` + fail-capable грепы + `scripts/check-no-internet.sh` + aapt2/apksigner/stat-гейты + отложенный device-UAT + строго ручная публикация)
**Quick run:** `./gradlew assembleRelease`
**Full suite:** `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh && stat + aapt2 + apksigner гейты (Task 4)`

## Multi-Source Coverage Audit

Каждый item одного из четырёх источников (GOAL / REQ / RESEARCH / CONTEXT) должен быть COVERED планом.

### GOAL (ROADMAP Phase 11 Success Criteria)

| # | Goal item | Covered by | Status |
|---|-----------|-----------|--------|
| SC1 | Подписанный release-APK ≤ 3 МБ — замер зафиксирован (PERF-01, REL-01) | Task 1 (shrink+stat+apksigner), Task 2 (CI-гейт), Task 4 (число в SUMMARY, чек-боксы [x]) | COVERED (mechanical PASS) |
| SC2 | PSS ≤ 30 МБ + холодный старт < 400 мс на бюджетном устройстве (PERF-02) | Task 3 (11-PERF-CHECKLIST с командами/порогами), Task 5a (замеры) | COVERED (deferred device UAT) |
| SC3 | 0 аллокаций onDraw/onTouchEvent, 0 GC, janky ~0% (PERF-03) | Task 3 (чек-лист Profiler+gfxinfo), Task 5a (замеры) | COVERED (deferred device UAT) |
| SC4 | Privacy policy опубликована и слинкована из README (REL-02) | Task 3 (PRIVACY.md + README-ссылка), Task 5c (публикация = «опубликована») | COVERED (docs PASS; publish deferred manual) |
| SC5 | GitHub Release + заявка IzzyOnDroid (REL-03) | Task 3 (PUBLISH-CHECKLIST точные команды/URL), Task 5c (строго ручное исполнение) | COVERED (prepared; execution manual-only) |

### REQ (REQUIREMENTS.md phase_req_ids)

| Req ID | Covered by | Status |
|--------|-----------|--------|
| PERF-01 | Task 1 (stat-гейт ≤ 3 145 728) + Task 2 (CI-гейт) + Task 4 (чек-бокс [x] + число) | COVERED (mechanical) |
| PERF-02 | Task 3 (чек-лист: dumpsys meminfo ≤ 30 МБ ×3, am start < 400 мс ×5) + Task 5a | COVERED (deferred device) |
| PERF-03 | Task 3 (чек-лист: Profiler 0 аллокаций/GC, gfxinfo janky ≤ 1%) + Task 5a | COVERED (deferred device) |
| REL-01 | Task 1 (apksigner verify) + Task 4 (signingConfigs-грепы, чек-бокс [x]) | COVERED (keystore существует с фазы 1 — RESEARCH §4) |
| REL-02 | Task 3 (PRIVACY.md + README-линк) + Task 5c (публикация) | COVERED (prepared; publish manual) |
| REL-03 | Task 3 (CHANGELOG + PUBLISH-CHECKLIST) + Task 5c (ручное исполнение) | COVERED (prepared; publish manual) |

### RESEARCH (11-RESEARCH.md вердикты §1–8)

| Research item | Covered by | Status |
|---------------|-----------|--------|
| §1 R1: shrinkResources ВКЛЮЧАТЬ строго с keep.xml (враг — locale_name_*, не раскладки) | Task 1 (точный keep.xml из эксперимента + aapt2-гейты locale_name ≥ 8) | COVERED (риск снят экспериментально) |
| §1 пост-сборочный гейт aapt2 (locale_name + layout set tatar) | Task 1 + Task 4 (fail-capable) | COVERED |
| §2 minifyEnabled уже true, proguard-правил НЕ добавлять | prohibition (минимальный дифф build.gradle = 2 строки), boundary-чек Task 4 | HONORED (excluded — ничего не менять) |
| §3 ребрендинг: english_ime_name минус «(dev)» (манифест не трогать — единая точка) | Task 1 + Task 4 (греп + манифест не в диффе) | COVERED |
| §3 setup_message — мёртвая строка, удалить из base (35 локализаций = backlog) | Task 1 (удаление) + A4 (orphan-фолбэк) + prohibition (35 файлов не трогать) | COVERED |
| §3 privacy_policy_url/license_url плейсхолдеры → реальные URL | Task 1 (НЕ трогать — owner неизвестен) + PUBLISH-CHECKLIST шаг 3 (замена при известном owner) | COVERED (deferred to publish — план уточнил рекомендацию ресерча: репо не создано, гадать owner нельзя) |
| §3 versionName → "1.0.0" / versionCode 1 | Task 1 | COVERED |
| §4 keystore ГОТОВО (RSA-4096, до 2056, gitignored) — только подтверждающие гейты | Task 4 (apksigner + signingConfigs-грепы; генерация НЕ планируется) | COVERED (zero-work confirmed) |
| §4 напоминание о бэкапе jks | Task 3 (PUBLISH-CHECKLIST шаг 1) + Task 4.6 (SUMMARY/вывод) + Task 5c | COVERED |
| §5 CI: дописать в существующий ci.yml (НЕ новый workflow): assembleRelease unsigned + size-гейт | Task 2 | COVERED |
| §5 CI ни разу не гонялся (репо нет) — прогон при публикации | PUBLISH-CHECKLIST шаг 5 (зелёный CI + красный негативный тест) + A2 (имя unsigned-APK) | COVERED (deferred manual) |
| §5 aapt2-гейт в CI — опционален | Task 2 (сознательно НЕ добавлен: сложность setup build-tools не оправдана при local-гейте Task 4) | RESOLVED (excluded с обоснованием) |
| §6 PERF-01 факт: 730 783 → 681 078 байт с shrink (запас 4.6×) | Task 1/4 (stat-гейты), A3 | COVERED |
| §7 setup_message оверрайдить нечего — удалить | Task 1 (совпадает с §3) | COVERED |
| §8 форма плана (5 задач) | структура плана 1:1 | COVERED |

### CONTEXT (11-CONTEXT.md locked decisions + структура)

| Locked decision / item | Covered by | Status |
|-----------------|-----------|--------|
| minSdk 24 — без изменений | prohibition №3 (ноль gradle-бампов), boundary-чек | HONORED |
| Имя «Tatar Keyboard» / appId org.tatarkeyboard.ime — финально | Task 1 (ребрендинг строки; appId уже финален — не трогается) | COVERED |
| Публикация: подготовить всё локально, выполнить вручную (репо не создано) | Task 3 (все документы) + Task 5c (ручное) + prohibition №1 (NO auto-push/publish) | COVERED |
| shrinkResources (I1 обзора фазы 10) | Task 1 | COVERED |
| minifyEnabled проверить (уже true?) | RESEARCH §2 подтвердил true → zero-work, Task 4 (не в диффе) | RESOLVED |
| CI check-no-internet уже в конфиге — убедиться | RESEARCH §5 подтвердил (дважды в ci.yml); Task 4 (PERF-04-инвариант греп) | RESOLVED |
| Keystore: сгенерировать ЕСЛИ НЕТ | RESEARCH §4: существует → генерация не планируется, только гейты (Task 4) | RESOLVED (zero-work) |
| PERF-01 механически (stat) | Task 1 + Task 2 + Task 4 | COVERED |
| PERF-02/03 device-gated → deferred UAT + 11-PERF-CHECKLIST.md с adb-командами | Task 3 (чек-лист) + Task 5a | COVERED |
| README: описание, ссылка на privacy | Task 3 (скриншот-placeholder и неактивные бейджи из CONTEXT-описания СНЯТЫ prohibition №5 — мёртвые заглушки хуже отсутствия; добавить после публикации/скриншотов) | COVERED (план уточнил CONTEXT) |
| PRIVACY.md: «данные не собираются», no-INTERNET, Apache-2.0 | Task 3 | COVERED |
| CHANGELOG.md: v1.0.0 по фазам → features | Task 3 (user-facing формулировки) | COVERED |
| docs/PUBLISH-CHECKLIST.md: repo → тег → Release → IzzyOnDroid, ручное | Task 3 | COVERED |
| Deferred: PERF-02/03 device-прогон — UAT-бандл | Task 5a + STATE Blockers | HONORED (deferred) |
| Deferred: публикация — ручная | Task 5c | HONORED (manual-only) |
| Deferred: полный ребрендинг ~30+ строк — backlog post-v1.0 | prohibition (NOT in plan), STATE backlog запись фазы 10 | HONORED (excluded) |
| Deferred: shrinkResources только после проверки keep-rules | RESEARCH §1 эксперимент = проверка выполнена → включаем | RESOLVED |
| Риск R1: shrinkResources vs getIdentifier (раскладки!) | RESEARCH §1: враг — locale_name (не раскладки); keep.xml покрывает все 4 call-site; двойной aapt2-гейт | RESOLVED (экспериментально) |

**Итог аудита:** 0 unplanned items. Все GOAL/REQ/RESEARCH/CONTEXT-items — COVERED / RESOLVED / HONORED. Два сознательных уточнения ресерча/контекста планом (оба задокументированы): (1) URL-плейсхолдеры не заменяются до известного owner (шаг PUBLISH-CHECKLIST) — рекомендация ресерча «прописать целевой URL сразу» отклонена: owner неизвестен, выдуманный URL хуже честного плейсхолдера; (2) скриншот-placeholder/неактивные бейджи в README — сняты (IzzyOnDroid-ревью). Фаза влезает в один план (5 задач, zero-code: 2 строки gradle + 1 keep.xml + 2 строковых правки + CI + документы).

## Phase Requirements → Test Map

| Req | Behavior | Test Type | Automated Command | File Exists? |
|-----|----------|-----------|-------------------|-------------|
| PERF-01 | Release APK ≤ 3 МБ | mechanical gate | `stat -c%s app-release.apk` ≤ 3145728 | ✅ APK собирается (Task 1) |
| PERF-01 | Гейт на каждом коммите | CI step | `test $(stat -c%s ...) -le 3145728` в ci.yml | ❌ Wave 0 (Task 2) |
| PERF-01/R1 | locale_name_* пережили shrink | aapt2 gate | `aapt2 dump resources APK \| grep -c locale_name` ≥ 8 | ❌ Wave 0 (Task 1: keep.xml) |
| PERF-01/R1 | Раскладки пережили shrink | aapt2 gate | `aapt2 dump resources APK \| grep keyboard_layout_set_tatar` | ❌ Wave 0 (Task 1) |
| PERF-02 | PSS ≤ 30 МБ | manual (device) | `adb shell dumpsys meminfo org.tatarkeyboard.ime` — команды в 11-PERF-CHECKLIST | DEFERRED (Task 5a) |
| PERF-02 | Холодный старт < 400 мс | manual (device) | force-stop → тап → показ, медиана ×5 — методика в чек-листе | DEFERRED (Task 5a) |
| PERF-03 | 0 аллокаций / 0 GC при печати | manual (device) | Android Studio Profiler, 30 с печати | DEFERRED (Task 5a) |
| PERF-03 | Janky ~0% | manual (device) | `dumpsys gfxinfo ... reset` → 60 с → отчёт ≤ 1% | DEFERRED (Task 5a) |
| REL-01 | Release подписан рабочим ключом | mechanical gate | `apksigner verify app-release.apk` | ✅ keystore с фазы 1 (гейт — Task 1/4) |
| REL-01 | signingConfigs-цепочка жива | structural grep | `grep 'keystore.properties' + 'signingConfig signingConfigs.release' build.gradle` | ✅ exists (:4, :36) |
| REL-02 | PRIVACY.md содержательна и слинкована | structural grep | `grep -qi 'не собира' PRIVACY.md && grep -q 'PRIVACY.md' README.md` | ❌ Wave 0 (Task 3) |
| REL-03 | PUBLISH-CHECKLIST исполним (команды/URL) | structural grep | `grep 'v1.0.0' + 'gitlab.com/IzzyOnDroid' + 'release.jks' PUBLISH-CHECKLIST.md` | ❌ Wave 0 (Task 3) |
| REL-03 | Публикация выполнена | manual (user-only) | по PUBLISH-CHECKLIST шаг за шагом | DEFERRED (Task 5c, строго ручное) |
| PERF-04 инвариант | Нет INTERNET | script | `bash scripts/check-no-internet.sh` (source + release APK) | ✅ exists (Task 4 прогон) |
| Ребрендинг | «Tatar Keyboard» без «(dev)»; setup_message удалён | structural grep | `grep '>Tatar Keyboard<' strings-appname.xml && ! grep 'name="setup_message"' values/strings.xml` | ❌ Wave 0 (Task 1) |
| Boundary | 5 файлов от 7f99505, ноль .java/.kt | git diff | `git diff --name-only 7f99505..HEAD -- app/ .github/` = 5 | ❌ Wave 0 (Task 4) |

## Sampling Rate

- **Per task commit:** `./gradlew assembleRelease` (release — главный артефакт фазы; debug вторичен)
- **Per wave merge:** `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh` + stat/aapt2/apksigner-гейты + boundary-чек (7f99505 = 5 файлов)
- **Phase gate:** обе сборки зелёные; PERF-01 числом; shrink-линия (keep.xml + aapt2 ≥ 8 locale_name); apksigner OK; документы-грепы PASS; PERF-02/03 deferred device; REL-02/03 publication deferred manual — оба честно в STATE.md Blockers.

## Wave 0 Gaps

- [ ] keep.xml + shrinkResources + aapt2-гейты — Task 1 (единственный содержательный риск фазы, снят экспериментом ресерча).
- [ ] CI-шаги assembleRelease + size-гейт — Task 2 (верификация синтаксисом/грепами; живой прогон CI невозможен до создания репо — PUBLISH-CHECKLIST шаг 5).
- [ ] 5 документов + контент-грепы — Task 3.
- [ ] Boundary-чек (7f99505..HEAD -- app/ .github/ = ровно 5 файлов, ноль .java/.kt, манифест нетронут) — Task 4.
- [ ] Нет нужды в новом test-фреймворке — консистентно с фазами 1–10 (grep + build + mechanical gates + deferred device-UAT).

## Deferred (standing-паттерн + manual-only публикация)

- **Device-UAT (Task 5a/5b):** PERF-02/03 замеры (PSS/старт/Profiler/gfxinfo — команды и пороги в 11-PERF-CHECKLIST.md) + финальный QA чистой установки release v1.0.0 — присоединяются к UAT-бандлу фаз 1–10 в STATE.md Blockers; устройство не подключено (adb devices пуст). Чек-боксы PERF-02/03 — только после реальных замеров.
- **Публикация (Task 5c, ВСЕГДА ручная):** GitHub-репо → push → CI (зелёный + красный негативный тест PERF-04) → тег v1.0.0 → GitHub Release с подписанным APK → заявка IzzyOnDroid — по docs/PUBLISH-CHECKLIST.md, исключительно руками пользователя (locked decision; executor не создаёт репо и не пушит). Чек-боксы REL-02/03 — после публикации. Бэкап release.jks (шаг 1) — сделать даже при отложенной публикации.
