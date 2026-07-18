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

- [ ] **INPUT-01**: Shift работает в трёх состояниях: off / shift (один тап) / caps-lock (двойной тап); регистр букв на клавишах визуально меняется
  — *Аннотация (2026-07-18): бонус сверх требования — caps lock также long-press'ом shift (1200 мс). См. 04-RESEARCH.md § INPUT-01.*
- [ ] **INPUT-02**: Автокапитализация первой буквы предложения по типу поля (InputType/imeOptions)
- [ ] **INPUT-03**: Backspace удаляет по кодпоинтам (deleteSurroundingTextInCodePoints) и ускоряется при удержании
  — *Аннотация (2026-07-18, принято пользователем): реализовано эквивалентом — подсчёт chars по кодпоинту (`supplementary ? 2 : 1`) + `deleteSurroundingText`; семантика идентична `deleteSurroundingTextInCodePoints` (суррогатная пара удаляется целиком). «Ускоряется» = AOSP-автоповтор: 400 мс старт → серия 50 мс. Прогрессивный разгон (удаление словами) — backlog post-MVP. См. 04-RESEARCH.md § INPUT-03.*
- [ ] **INPUT-04**: Клавиша Enter показывает и выполняет действие поля по imeOptions (поиск/перенос/готово/отправить)
- [ ] **INPUT-05**: Двойной тап по пробелу вставляет точку с пробелом
- [ ] **INPUT-06**: Свайп по пробелу перемещает курсор
- [ ] **INPUT-07**: Multi-touch: при втором касании первая клавиша коммитится (быстрая печать двумя пальцами не теряет буквы)

### iOS-скин

- [ ] **UI-01**: Клавиатура отрисована на Canvas по зафиксированной геометрии и палитре (светлая: фон #D4D6DD, клавиши #FFF, служебные #B3B7C0; тёмная: #2C2C2C/#6B6B6B/#474747; радиус ~5dp, резкая 1dp-тень), тема следует системной светлой/тёмной
- [ ] **UI-02**: При нажатии буквенной клавиши над ней мгновенно появляется баллон-превью (в слое клавиатуры, не PopupWindow)
- [ ] **UI-03**: Long-press панель альтернатив с выбором скольжением пальца
- [ ] **UI-04**: Визуальная реакция, хаптика (KEYBOARD_TAP) и системный звук клика срабатывают на ACTION_DOWN; звук и вибрация отключаемы в настройках

### Доступность

- [ ] **A11Y-01**: TalkBack озвучивает клавиши: ExploreByTouchHelper, каждая клавиша — виртуальный узел
- [ ] **A11Y-02**: Татарские буквы имеют контент-описания (напр. «татарская э» для ә)

### Совместимость

- [ ] **COMPAT-01**: В password-полях клавиатура корректно вводит текст, без подсказок и обучения
- [ ] **COMPAT-02**: Ввод работает в WebView/Chrome (сценарий keyCode 229)
- [ ] **COMPAT-03**: Edge-to-edge и WindowInsets корректны на API 35+ (клавиатура не перекрыта системными панелями)
- [ ] **COMPAT-04**: Клавиатура работает в ландшафтной ориентации
- [ ] **COMPAT-05**: directBootAware: клавиатура доступна до первой разблокировки устройства

### Онбординг и настройки

- [ ] **SETUP-01**: Онбординг-экран проводит через включение IME и выбор клавиатуры (два шага со статусами)
- [ ] **SETUP-02**: Минимальные настройки: звук клика вкл/выкл, вибрация вкл/выкл

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
| INPUT-05 | Phase 5 | Pending |
| INPUT-06 | Phase 5 | Pending |
| INPUT-07 | Phase 5 | Pending |
| UI-01 | Phase 6 | Pending |
| UI-02 | Phase 7 | Pending |
| UI-03 | Phase 7 | Pending |
| UI-04 | Phase 7 | Pending |
| A11Y-01 | Phase 9 | Pending |
| A11Y-02 | Phase 9 | Pending |
| COMPAT-01 | Phase 8 | Pending |
| COMPAT-02 | Phase 8 | Pending |
| COMPAT-03 | Phase 8 | Pending |
| COMPAT-04 | Phase 8 | Pending |
| COMPAT-05 | Phase 8 | Pending |
| SETUP-01 | Phase 10 | Pending |
| SETUP-02 | Phase 10 | Pending |
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
