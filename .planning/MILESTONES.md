# Milestones

## --milestone --milestone (Shipped: 2026-07-19)

**Phases completed:** 11 phases, 12 plans, 39 tasks

**Key accomplishments:**

- Форк rkkr/simple-keyboard (b40c70d9) собирается под org.tatarkeyboard.ime.debug, INTERNET-проверка PERF-04 доказуемо падает на инъекции, Kotlin-interop подтверждён на сборке — on-device смоук отложен пользователем.
- assembleRelease даёт реально подписанный APK (apksigner verified, CN=Tatar Keyboard), без keystore.properties собирается unsigned; ci.yml с двухуровневой INTERNET-проверкой закоммичен — но GitHub-репозитория не существует (нет gh/origin), поэтому все GitHub-прогоны и device-чекпойнт отложены, не сфабрикованы.
- Татарская раскладка как чистые XML-данные: пятый ряд ә ө ү җ ң һ сверху + полная ЙЦУКЕН литеральными кодпоинтами, слои ?123/#+= из коробки, регистрация tt в реестре и tt-дефолт на чистой установке — при Java-diff ровно в один файл-реестр
- Три языка как subtypes (tt_RU активный, ru со своей ЙЦУКЕН-раскладкой, en_US) + 10 long-press татарских дублей на обеих кириллических раскладках — при Java-diff ровно в один файл-реестр и нетронутых shared east_slavic-файлах; глобус/цикл/пикер/персистентность — ноль нового кода (штатные механизмы форка)
- INPUT-01..04 доказаны структурно без единой правки кода: вердикт ресерча ALL WORKS запинован fail-capable-грепами, zero-code boundary подтверждён пустым дифом b19ce97..HEAD по app/; принятые трактовки зафиксированы в REQUIREMENTS.md; device UAT отложен (Task 3 deferred — human verification pending).
- Double-space→period восстановлен в InputLogic по AOSP-паттерну (1100 мс, гейты, revert), pref_space_swipe default флипнут на true ×3, INPUT-07 доказан структурно — UAT отложен
- iOS-скин чистым XML-диффом: тема id=7 «Tatar» (дефолт) с LOCKED-палитрой light/night, layer-list-тень 1dp без setShadowLayer, собственные иконки, 3 хирургических PERF-фикса и ExploreByTouchHelper-каркас на androidx.customview — release-APK 701 КБ при бюджете 3 МБ
- iOS-баллон превью и панель альтернатив чистым XML (2 drawable + 2 item темы, ноль Java); in-layer архитектура и отклик на ACTION_DOWN подтверждены построчно и запинованы fail-capable-грепами — перенос с PopupWindow не понадобился, его в форке нет вообще
- Extract mode убит однострочным флипом values-land config_use_fullscreen_mode, все 5 COMPAT-вердиктов запинованы fail-capable-грепами, SC5 получил письменный артефакт 08-UAT-MATRIX.md (12×8, CLOSED-STRUCTURAL + DEFERRED) — Java-дифф фазы 0 строк
- TalkBack-ввод достроен до полной A11Y-01/02: «татарская э» для ә ресурсами en/ru через KeyDescriptionMapper, клик по виртуальному узлу = синтез MotionEvent в штатный touch-путь (fork-Java-дифф 0 строк), isTextEntryKey/TYPE_VIEW_CLICKED — все 4 гэпа ресерча закрыты
- Kotlin SetupActivity с 2-шаговым онбордингом (включить IME через ACTION_INPUT_METHOD_SETTINGS + выбрать через showInputMethodPicker), живой детект статусов из системы без собственного флага, LAUNCHER-переезд и удаление legacy-диалога; SETUP-02 доказан существующим zero-code.
- Подписанный release-APK «Tatar Keyboard» 1.0.0 — 681 070 байт (гейт 3 МБ, запас 4.6×) с shrinkResources+keep.xml, CI size-гейтом и полным комплектом релизных документов; публикация и device-замеры подготовлены для ручного исполнения

---
