# 08-UAT-MATRIX — Матрица InputConnection (деливерабл SC5, Phase 8)

**Дата:** 2026-07-18
**Назначение:** письменная фиксация полного прохода тестовой матрицы InputConnection (ROADMAP Phase 8, SC5: «полный проход матрицы … зафиксирован письменно»). Документ self-contained: человек с устройством выполняет прогон, не читая других файлов.

## Честная преамбула

На момент написания устройство недоступно (`adb devices` пуст — standing-паттерн фаз 1–7, принят пользователем). Поэтому:

- **Механизмы**, доказанные грепами/коммитами (Task 2 плана 08-01), закрыты статусом `CLOSED-STRUCTURAL` со ссылкой — см. § Структурные закрытия.
- **Все runtime-ячейки матрицы — `DEFERRED`**: их прогон = Task 4 плана 08-01 / общий отложенный UAT-бандл фаз 1–7 (STATE.md § Blockers).
- **Двухуровневая честность:** `CLOSED-STRUCTURAL` закрывает МЕХАНИЗМ (код существует и защищён от регрессий), но строка окружения целиком получает положительный статус только после прогона на устройстве. Ни одна ячейка не помечена пройденной до реального прогона.

## Легенда статусов

- **`CLOSED-STRUCTURAL`** — механика доказана грепом/файлом:строкой/коммитом; ссылка обязательна. НЕ заменяет device-прогон.
- **`DEFERRED`** — runtime-проверка отложена в UAT-бандл фаз 1–7 (устройства нет).
- **Статусы device-прогона** (проставляются ТОЛЬКО при исполнении на устройстве/эмуляторе): `+` (пройдено), `FAIL` (провал, обязательна заметка), `N/A` (неприменимо/устройства класса нет, обязательна оговорка).

## Сценарии (колонки)

| # | Сценарий |
|---|---|
| S1 | Татарские буквы, вкл. пятый ряд: набрать «әни өй үрдәк җир таң һава» — все ә ө ү җ ң һ вводятся тапом, без потерь/дублей |
| S2 | Backspace: одиночный (ә/җ/ң удаляются целиком за одно нажатие — кодпоинты) + удержание (~0.4 с → серия ~20/сек, автоповтор) |
| S3 | Курсор в середину текста тапом → ввод/удаление в середине корректны (позиция не прыгает) |
| S4 | Enter-действие поля по imeOptions (поиск/отправить/перенос/готово) — иконка соответствует и действие выполняется |
| S5 | Long-press панель альтернатив (а→ә, о→ө, у→ү, ж→җ, н→ң, х→һ, е→ё, ь→ъ); выбор скольжением без отрыва |
| S6 | Жесты: двойной пробел → «. » (и НЕ в password); свайп по пробелу двигает курсор |
| S7 | Переключение глобусом tt→ru→en→tt; long-press глобуса → системный пикер |
| S8 | Светлая/тёмная тема: палитра корректна, смена темы подхватывается без перезапуска IME |

## Матрица: окружения × сценарии

| # | Окружение | S1 | S2 | S3 | S4 | S5 | S6 | S7 | S8 |
|---|---|---|---|---|---|---|---|---|---|
| E1 | Telegram (обычное поле сообщения) | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED |
| E2 | Chrome — адресная строка | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED |
| E3 | Chrome форма / WebView-contenteditable — сценарий keyCode 229 (механизм: CS-2) | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED |
| E4 | Password-поле (маска-точки; механизм: CS-1) | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED |
| E5 | Поле банка / PIN-поле (numberPassword) | DEFERRED | DEFERRED | DEFERRED | DEFERRED | N/A (только цифры) | DEFERRED | DEFERRED | DEFERRED |
| E6 | Multi-line заметки (Keep/встроенные заметки) | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED |
| E7 | Поля actionSearch / actionDone / actionNext | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED |
| E8 | Эмулятор API 35–36: gesture-nav + 3-button, edge-to-edge визуально (механизм: CS-3) | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED |
| E9 | Ландшафт на телефоне, вкл. «extract mode НЕ появляется» (механизм: CS-4) | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED | DEFERRED |
| E10 | Direct Boot: ребут → PIN-ввод нашей клавиатурой ДО первой разблокировки (механизм: CS-5) | DEFERRED | DEFERRED | N/A | DEFERRED | N/A | N/A | DEFERRED | DEFERRED |
| E11 | MIUI/HyperOS-устройство, вкл. «клавиатура не исчезает при наборе» | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* |
| E12 | One UI-устройство (Samsung) | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* | DEFERRED* |

`*` E11/E12 — «при наличии устройства данного класса»; если Xiaomi/Samsung недоступны при прогоне бандла — проставить `N/A` с оговоркой «устройство класса недоступно», НЕ выдавая за пройденное.

Примечания к N/A: E5·S5 — в цифровом поле панель альтернатив букв неприменима; E10 — на lock screen нет полноценного текста/жестов (S3/S5/S6), проверяется сам факт доступности клавиатуры и ввод PIN.

## Структурные закрытия (проставлены 2026-07-18, Task 2 плана 08-01)

Каждый пункт закрывает МЕХАНИЗМ; соответствующие строки матрицы остаются DEFERRED до device-прогона.

| ID | Механизм | Статус | Доказательство (греп/файл:строка/коммит) |
|---|---|---|---|
| CS-1 | Password: «без подсказок и обучения» — словаря/движка подсказок в форке нет вообще; double-space гейтится в password | CLOSED-STRUCTURAL | ноль файлов `*Dictionary*`/`*UserHistory*`, ноль `import.*Dictionary`, ноль `personaliz` (единственное совпадение слова — upstream TODO-комментарий LatinIME.java:266, кода за ним нет); `mIsPasswordField` InputAttributes.java:34,49; гейт `!settingsValues.mInputAttributes.mIsPasswordField` InputLogic.java:355. 08-RESEARCH.md § COMPAT-01 |
| CS-2 | WebView/keyCode 229: composing-текста в коде ноль — ввод неотличим от обычного commitText, 229 не возникает структурно | CLOSED-STRUCTURAL | греп `setComposingText\|setComposingRegion\|finishComposingText` = 0 вхождений во всём app/src/main/java; буква — `commitText` InputLogic.java:606; backspace — подсчёт по кодпоинту → `deleteSurroundingText` RichInputConnection.java:356. 08-RESEARCH.md § COMPAT-02 |
| CS-3 | Edge-to-edge API 35+: insets-линия полностью в базе (закрыта upstream'ом до нашего форка) | CLOSED-STRUCTURAL | upstream-коммиты `827da4f` «fitsSystemWindows now works (#584)», `2885ae5` «Android 17 SDK (#629)» — предки HEAD; `fitsSystemWindows="true"` layout-v28/input_view.xml:32 (базовый layout/ для API 24–27 сознательно без флага); `requestApplyInsets` LatinIME.java:333; `onComputeInsets` LatinIME.java:535; `setNavigationBarContrastEnforced(false)` LatinIME.java:935. 08-RESEARCH.md § COMPAT-03 |
| CS-4 | Ландшафт: extract/fullscreen mode мёртв во всех конфигурациях; пятый ряд в ландшафте предусмотрен данными | CLOSED-STRUCTURAL | флип `config_use_fullscreen_mode` values-land → false — коммит `4088f50` (Task 1 плана 08-01); греп-инвариант: `">true` = 0, `">false` = 5 файлов (values, values-land, sw430/600/768); 5row-фракции values-land/config.xml:61,66. 08-RESEARCH.md § COMPAT-04 |
| CS-5 | Direct Boot: сервис directBootAware, ВСЕ prefs — device-protected storage (credential-protected не трогается до разблокировки) | CLOSED-STRUCTURAL | `android:directBootAware="true"` AndroidManifest.xml:31; `createDeviceProtectedStorageContext` PreferenceManagerCompat.java:25; `getDefaultSharedPreferences` вне PreferenceManagerCompat — ноль call-sites. 08-RESEARCH.md § COMPAT-05 |

MIUI/HyperOS дополнительно (кодовых действий нет): превью/панель рисуются in-layer с фазы 7 (PopupWindow в исходниках отсутствует — классический MIUI-класс проблем неприменим); киллер процесса → метрика холодного старта фазы 11; «клавиатура исчезает при наборе» — системный баг, только device-чек (E11).

## DEFERRED: self-contained чеклист device-прогона

Присоединяется к отложенному UAT-бандлу фаз 1–7 (STATE.md § Blockers). Порядок прогона:

**Подготовка:**
1. Свежая сборка: `./gradlew assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk` (uninstall не обязателен для этой фазы — дефолты prefs не менялись).
2. Включить «Tatar Keyboard (dev)» в системных настройках, выбрать текущей клавиатурой.

**Основной прогон (E1–E7):** в каждом окружении выполнить S1–S8 по описаниям таблицы «Сценарии»; каждую ячейку матрицы разрешить в `+` / `FAIL` / `N/A` с заметкой прямо в этом файле.

**E8 — эмулятор API 35–36 (исполним отдельно, ДО появления физического устройства — см. примечание ниже):**
1. AVD API 35 и/или 36, gesture-nav: клавиатура показана — нижний ряд НЕ перекрыт жестовой полосой; фон под полосой = фон клавиатуры (светлая #D4D6DD / тёмная #2C2C2C).
2. Переключить на 3-button nav: нижний ряд не перекрыт кнопками, contrast-подложка не появляется.
3. Оба пункта — в светлой И тёмной теме.
4. Ландшафт на эмуляторе: боковые insets gesture-nav не режут крайние колонки.

**E9 — ландшафт (телефон < sw430dp особенно важен):**
1. Повернуть телефон в любом текстовом поле → клавиатура перерисовывается в ландшафт, пятый ряд ә ө ү җ ң һ на месте.
2. **Extract mode НЕ появляется**: над клавиатурой НЕТ всплывающего белого редактора на весь экран — поле приложения остаётся видимым (проверка флипа CS-4 вживую).
3. Набор/жесты/переключение языков в ландшафте работают.

**E10 — Direct Boot:**
1. Установить экранный PIN (не биометрию), перезагрузить устройство.
2. ДО первой разблокировки: на lock screen поле PIN — наша клавиатура доступна и вводит цифры (если поле PIN системное числовое — проверить любое доступное текстовое поле до разблокировки, напр. экстренную информацию).
3. После разблокировки клавиатура работает как обычно (prefs не потеряны).

**E11 — MIUI/HyperOS (при наличии Xiaomi/Redmi):** основной прогон S1–S8 + спец-чек: длинный набор (100+ символов) — клавиатура НЕ исчезает посреди набора; баллоны-превью не режутся.

**E12 — One UI (при наличии Samsung):** основной прогон S1–S8.

**После прогона:**
- Обновить ячейки матрицы и колонку «Статус» структурных строк в этом файле (дата + устройство/API в заметке).
- Проставить чек-боксы COMPAT-01..05 в `.planning/REQUIREMENTS.md` — ТОЛЬКО после реального прогона.
- Обновить STATE.md § Blockers (снять запись фазы 8) и Traceability.

**Примечание (A4 плана):** блок E8 исполним на эмуляторе без физического устройства — если эмулятор появится раньше телефона, E8 можно прогнать и зафиксировать отдельно, не дожидаясь всего бандла.

---
*Phase: 08-sovmestimost · Plan: 08-01 · Task 3*
*Составлено: 2026-07-18 — структурная часть закрыта, runtime-часть отложена честно*
