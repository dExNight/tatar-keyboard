# Requirements: Tatar Keyboard

**Defined:** 2026-07-18
**Core Value:** Татарский язык — первым классом: печатать по-татарски так же быстро и приятно, как по-русски в Gboard, — на самом дешёвом телефоне, без интернета.

## v1 Requirements

Requirements for initial release (майлстоун v1.0 MVP: release-ready APK + публикация GitHub Releases + заявка IzzyOnDroid). Each maps to roadmap phases.

### Раскладки

- [x] **LAYOUT-01**: Пользователь печатает на татарской раскладке: стандартная ЙЦУКЕН + отдельный видимый пятый ряд `ә ө ү җ ң һ`
- [x] **LAYOUT-02**: Long-press на родственных русских буквах (а→ә, о→ө, у→ү, ж→җ, н→ң, х→һ, э→ә, г→һ) вставляет татарскую букву — работает и на татарской, и на русской раскладке
- [x] **LAYOUT-03**: Пользователь печатает на русской (ЙЦУКЕН) и английской (QWERTY) раскладках
- [x] **LAYOUT-04**: Доступны слои цифр/символов `?123` и `#+=` с возвратом к буквам
- [x] **LAYOUT-05**: Раскладки описаны данными (XML), не кодом; формат допускает добавление новой раскладки (латиница Zamanälif позже) без изменения движка

### Переключение языков

- [x] **SWITCH-01**: Система видит три subtype: tt_RU, ru, en_US; клавиатура запоминает активный
- [x] **SWITCH-02**: Тап по глобусу циклически переключает раскладки; long-press по глобусу открывает системный пикер IME

### Механика ввода

- [x] **INPUT-01**: Shift работает в трёх состояниях: off / shift (один тап) / caps-lock (двойной тап); регистр букв на клавишах визуально меняется
  — *Аннотация (2026-07-18): бонус сверх требования — caps lock также long-press'ом shift (1200 мс). См. 04-RESEARCH.md § INPUT-01.*

- [x] **INPUT-02**: Автокапитализация первой буквы предложения по типу поля (InputType/imeOptions)
- [x] **INPUT-03**: Backspace удаляет по кодпоинтам (deleteSurroundingTextInCodePoints) и ускоряется при удержании
  — *Аннотация (2026-07-18, принято пользователем): реализовано эквивалентом — подсчёт chars по кодпоинту (`supplementary ? 2 : 1`) + `deleteSurroundingText`; семантика идентична `deleteSurroundingTextInCodePoints` (суррогатная пара удаляется целиком). «Ускоряется» = AOSP-автоповтор: 400 мс старт → серия 50 мс. Прогрессивный разгон (удаление словами) — backlog post-MVP. См. 04-RESEARCH.md § INPUT-03.*

- [x] **INPUT-04**: Клавиша Enter показывает и выполняет действие поля по imeOptions (поиск/перенос/готово/отправить)
- [x] **INPUT-05**: Двойной тап по пробелу вставляет точку с пробелом
  — *Аннотация (2026-07-18): восстановлено по AOSP-паттерну (rkkr вырезал), always-on без pref, таймаут 1100 мс, гейты password + буква/цифра, revert по backspace. См. 05-RESEARCH.md § INPUT-05.*

- [x] **INPUT-06**: Свайп по пробелу перемещает курсор
  — *Аннотация (2026-07-18): механика штатная в форке; работа фазы — флип default pref_space_swipe → true (3 места). См. 05-RESEARCH.md § INPUT-06.*

- [x] **INPUT-07**: Multi-touch: при втором касании первая клавиша коммитится (быстрая печать двумя пальцами не теряет буквы)

### iOS-скин

- [x] **UI-01**: Клавиатура отрисована на Canvas по зафиксированной геометрии и палитре (светлая: фон #D4D6DD, клавиши #FFF, служебные #B3B7C0; тёмная: #2C2C2C/#6B6B6B/#474747; радиус ~5dp, резкая 1dp-тень), тема следует системной светлой/тёмной
  - *Аннотация (2026-07-18): реализовано маршрутом (b) 06-RESEARCH.md — тема id=7 «Tatar» поверх штатной системы тем форка, layer-list-тень (setShadowLayer отклонён — на HW-acceleration работает только для текста), 3 PERF-фикса горячего пути, a11y-каркас ExploreByTouchHelper (APK-гейт пройден: 646→701 КБ ≤ 3 МБ)*
- [x] **UI-02**: При нажатии буквенной клавиши над ней мгновенно появляется баллон-превью (в слое клавиатуры, не PopupWindow)
  — *Аннотация (2026-07-18): in-layer уже в базе форка — DrawingPreviewPlacerView в content-view окна IME, PopupWindow в исходниках отсутствует; работа фазы — iOS-стилизация баллона. См. 07-RESEARCH.md §1*

- [x] **UI-03**: Long-press панель альтернатив с выбором скольжением пальца
- [x] **UI-04**: Визуальная реакция, хаптика (KEYBOARD_TAP) и системный звук клика срабатывают на ACTION_DOWN; звук и вибрация отключаемы в настройках
  — *Аннотация (2026-07-18): (а) хаптика: на API ≥ Q форк использует VibrationEffect.EFFECT_CLICK — прямой современный эквивалент KEYBOARD_TAP (KEYBOARD_TAP — фолбэк < Q); трактуется как соответствие. (б) Звук клика по умолчанию ВЫКЛЮЧЕН (config_default_sound_enabled=false) — решение пользователя (как в Gboard); требование выполняется: звук срабатывает при включённом pref sound_on, отключаемость — программно; тумблер UI — фаза 10. См. 07-RESEARCH.md §3*

### Доступность

- [x] **A11Y-01**: TalkBack озвучивает клавиши: ExploreByTouchHelper, каждая клавиша — виртуальный узел
  — *Аннотация (2026-07-18): Каркас фазы 6 достроен: клик по виртуальному узлу = синтез MotionEvent DOWN/UP в видимый центр клавиши → MainKeyboardView.processMotionEvent (AOSP-паттерн; штатный touch-путь, PointerTracker/KeyboardState не изменены — fork-Java-дифф 0 строк) + isClickable/isTextEntryKey + TYPE_VIEW_CLICKED. Ограничение: moreKeys-панель (ё/ъ long-press) вне a11y-дерева — backlog. Озвучка/набор с TalkBack — deferred UAT (SC3). См. 09-RESEARCH.md § 1, § 4.*

- [x] **A11Y-02**: Татарские буквы имеют контент-описания (напр. «татарская э» для ә)
  — *Аннотация (2026-07-18): Описания — KeyDescriptionMapper (прямой when по key.getCode(), без reflection): 6 татарских букв «татарская э/о/у/ж/н/х» ресурсами values/strings-a11y.xml (en base) + values-ru/strings-a11y.xml (AOSP-совместимые имена spoken_*); заглавные — шаблон «Заглавная %s»; 19 служебных (shift ×4 состояний по elementId, enter ×7 по imeAction, приоритет custom label); обычные буквы — label (ru-TTS). values-tt — backlog. См. 09-RESEARCH.md § 2–3.*

### Совместимость

- [x] **COMPAT-01**: В password-полях клавиатура корректно вводит текст, без подсказок и обучения
  — *Аннотация (2026-07-18): Structurally free: движка подсказок/словаря/обучения в форке нет вообще (ноль Dictionary/UserHistory-кода); mIsPasswordField гейтит double-space (InputLogic.java:355); ввод — общий commitText-путь. См. 08-RESEARCH.md § COMPAT-01.*

- [x] **COMPAT-02**: Ввод работает в WebView/Chrome (сценарий keyCode 229)
  — *Аннотация (2026-07-18): Structurally free: composing-текста в коде ноль (setComposingText/Region — 0 вхождений, решение проекта); буквы commitText (InputLogic.java:606), backspace deleteSurroundingText по кодпоинтам (RichInputConnection.java:356) — keyCode 229 не возникает. См. 08-RESEARCH.md § COMPAT-02.*

- [x] **COMPAT-03**: Edge-to-edge и WindowInsets корректны на API 35+ (клавиатура не перекрыта системными панелями)
  — *Аннотация (2026-07-18): Гэп-кандидат закрыт upstream'ом до нашей базы (827da4f, 2885ae5): fitsSystemWindows в layout-v28 + requestApplyInsets (LatinIME.java:333) + onComputeInsets (:535) + contrast off (:935); кода фазы 0 строк. Визуальный чек API 35/36 — deferred UAT. См. 08-RESEARCH.md § COMPAT-03.*

- [x] **COMPAT-04**: Клавиатура работает в ландшафтной ориентации
  — *Аннотация (2026-07-18): Раскладки в ландшафте — данными (5row-фракции values-land); единственный кодовый пробел фазы — values-land config_use_fullscreen_mode=true → флип в false (реализация решения onEvaluateFullscreenMode()=false штатным ресурсом, Java-дифф 0). См. 08-RESEARCH.md § COMPAT-04.*

- [x] **COMPAT-05**: directBootAware: клавиатура доступна до первой разблокировки устройства
  — *Аннотация (2026-07-18): directBootAware=true на сервисе + ВСЕ prefs через PreferenceManagerCompat → device-protected storage (credential-protected не трогается вообще); прочие компоненты сознательно не помечены. PIN-ввод после ребута — deferred UAT. См. 08-RESEARCH.md § COMPAT-05.*

### Онбординг и настройки

- [ ] **SETUP-01**: Онбординг-экран проводит через включение IME и выбор клавиатуры (два шага со статусами)
  — *Аннотация (2026-07-19): Онбординг — новый SetupActivity.kt (Kotlin, classic View/XML — декларативный UI отклонён по бюджету APK): 2 карточки шагов со статусами, детект живьём из системы (шаг 1 getEnabledInputMethodList-паттерн форка, шаг 2 Settings.Secure.DEFAULT_INPUT_METHOD по префиксу пакета — устойчиво к debug-суффиксу), кнопки → ACTION_INPUT_METHOD_SETTINGS / showInputMethodPicker, рефреш onWindowFocusChanged+onResume, done-блок → SettingsActivity + подсказка «печатайте ә». MAIN/LAUNCHER переехал на SetupActivity; legacy not-enabled диалог старого бренда удалён из SettingsActivity.onStart (IME→настройки цел: класс-интент launchSettings). Проход SC3 на устройстве — deferred UAT. См. 10-RESEARCH.md.*

- [ ] **SETUP-02**: Минимальные настройки: звук клика вкл/выкл, вибрация вкл/выкл
  — *Аннотация (2026-07-19): Уже реализовано базой форка и живо-реактивно (подтверждено фазой 7): vibrate_on/sound_on/громкость в prefs_screen_key_press.xml (KeyPressSettingsFragment, экран «Нажатие клавиши» из корня настроек), вибро авто-скрыт без вибратора, живой отклик Settings-listener→loadSettings→AudioAndHapticFeedbackManager.onSettingsChanged. Работа фазы = верификация грепами, кода 0 строк. Live-проверка на устройстве — deferred UAT. См. 10-RESEARCH.md § SETUP-02 Audit.*

### Производительность и приватность

- [ ] **PERF-01**: Release-APK ≤ 3 МБ
- [ ] **PERF-02**: PSS показанной клавиатуры ≤ 30 МБ; холодный старт до показа < 400 мс
- [ ] **PERF-03**: Ноль аллокаций в onDraw/onTouchEvent; 0 GC-событий во время печати
- [x] **PERF-04**: В манифесте нет разрешения INTERNET; CI-проверка это гарантирует на каждом коммите

### Релиз

- [ ] **REL-01**: Настроен keystore, релизная сборка подписана
- [ ] **REL-02**: Privacy policy («данные не собираются») опубликована
- [ ] **REL-03**: Релиз опубликован на GitHub Releases и подана заявка в IzzyOnDroid

## v2 Requirements

Deferred to future release (следующие майлстоуны). Tracked but not in current roadmap.

### Умный ввод (v1 «умная версия»)

- **SMART-01**: Автокоррекция и строка подсказок (свой Kotlin-движок: префиксный поиск + Дамерау–Левенштейн ≤2 с учётом соседних клавиш)
- **SMART-02**: Татарский словарь 150–250 тыс. словоформ (ttwiki + Leipzig, фильтр apertium-tat)
- **SMART-03**: Билингвальный ввод tt+ru (два словаря с весами), fuzzy-замены суррогатов (э→ә, у→ү)
- **SMART-04**: Заучивание пользовательских слов (уважая IME_FLAG_NO_PERSONALIZED_LEARNING и пароли)

### Расширения UX

- **UX-01**: Эмодзи-панель
- **UX-02**: История буфера обмена
- **UX-03**: Настройки: высота клавиатуры, компактный режим (без пятого ряда), «Windows-раскладка»
- **UX-04**: Латиница Zamanälif как дополнительная раскладка

### Дистрибуция

- **DIST-01**: RuStore
- **DIST-02**: Google Play (closed testing 12 тестеров × 14 дней → production)
- **DIST-03**: F-Droid (reproducible build)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Свайп-ввод | Открытой реализации нет (у HeliBoard — закрытая Google-либа); своя — годы работы |
| Голосовой ввод | Поддержка tt системным SpeechRecognizer не подтверждена |
| Собственный C++/NDK | Не нужен для MVP/v1; неподъёмная сложность для соло-новичка |
| Jetpack Compose в IME-процессе | +20–40 МБ RAM, медленный холодный старт — дисквалификация на бюджетниках |
| Порт AOSP-словарного движка | ~300 файлов C++ без прецедентов чистого выдирания (research/06) |
| Этап 0: прототип в HeliBoard | Решение о форке Simple Keyboard принято окончательно |
| Composing-текст в MVP | Минимизация багов зоопарка InputConnection — коммит символов сразу |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| LAYOUT-01 | Phase 2 | Complete |
| LAYOUT-02 | Phase 3 | Complete |
| LAYOUT-03 | Phase 3 | Complete |
| LAYOUT-04 | Phase 2 | Complete |
| LAYOUT-05 | Phase 2 | Complete |
| SWITCH-01 | Phase 3 | Complete |
| SWITCH-02 | Phase 3 | Complete |
| INPUT-01 | Phase 4 | Verifying (04-01: structural PASS; on-device UAT deferred) |
| INPUT-02 | Phase 4 | Verifying (04-01: structural PASS; on-device UAT deferred) |
| INPUT-03 | Phase 4 | Verifying (04-01: structural PASS; on-device UAT deferred) |
| INPUT-04 | Phase 4 | Verifying (04-01: structural PASS; on-device UAT deferred) |
| INPUT-05 | Phase 5 | Verifying (05-01: structural PASS; on-device UAT deferred) |
| INPUT-06 | Phase 5 | Verifying (05-01: structural PASS; on-device UAT deferred) |
| INPUT-07 | Phase 5 | Verifying (05-01: structural PASS; on-device UAT deferred) |
| UI-01 | Phase 6 | Verifying (06-01: structural PASS; on-device UAT deferred) |
| UI-02 | Phase 7 | Verifying (07-01: structural PASS; on-device UAT deferred) |
| UI-03 | Phase 7 | Verifying (07-01: structural PASS; on-device UAT deferred) |
| UI-04 | Phase 7 | Verifying (07-01: structural PASS; on-device UAT deferred) |
| A11Y-01 | Phase 9 | Verifying (09-01: structural PASS; on-device UAT deferred) |
| A11Y-02 | Phase 9 | Verifying (09-01: structural PASS; on-device UAT deferred) |
| COMPAT-01 | Phase 8 | Verifying (08-01: structural PASS; on-device UAT deferred) |
| COMPAT-02 | Phase 8 | Verifying (08-01: structural PASS; on-device UAT deferred) |
| COMPAT-03 | Phase 8 | Verifying (08-01: structural PASS; on-device UAT deferred) |
| COMPAT-04 | Phase 8 | Verifying (08-01: structural PASS; on-device UAT deferred) |
| COMPAT-05 | Phase 8 | Verifying (08-01: structural PASS; on-device UAT deferred) |
| SETUP-01 | Phase 10 | Verifying (10-01: structural PASS; on-device UAT deferred) |
| SETUP-02 | Phase 10 | Verifying (10-01: structural PASS; on-device UAT deferred) |
| PERF-01 | Phase 11 | Pending |
| PERF-02 | Phase 11 | Pending |
| PERF-03 | Phase 11 | Pending |
| PERF-04 | Phase 1 | Complete |
| REL-01 | Phase 11 | Pending |
| REL-02 | Phase 11 | Pending |
| REL-03 | Phase 11 | Pending |

**Coverage:**

- v1 requirements: 34 total *(исправлено: при определении требований в счётчике стояло 30, фактический подсчёт по списку — 34)*
- Mapped to phases: 34
- Unmapped: 0 ✓

Примечания к маппингу:

- **PERF-01..03** — сквозная дисциплина (ноль аллокаций и лёгкость закладываются при написании кода в фазах 6–7 и далее), но замеренная верификация — Phase 11.
- **PERF-04** — CI-проверка ставится и наблюдаема с Phase 1, далее действует на каждом коммите.
- **REL-01** — keystore создаётся в Phase 1 (задел), требование верифицируется подписанным release-APK в Phase 11.

---
*Requirements defined: 2026-07-18*
*Last updated: 2026-07-18 — traceability populated during roadmap creation*
