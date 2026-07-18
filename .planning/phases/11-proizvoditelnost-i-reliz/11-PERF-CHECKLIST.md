# 11-PERF-CHECKLIST — device-сессия PERF-02/03

Чек-лист замеров бюджетов производительности на устройстве. Прогоняется вручную при подключённом девайсе; числа вписываются в таблицы «факт», после чего в `.planning/REQUIREMENTS.md` проставляются чек-боксы PERF-02/PERF-03.

## Подготовка

- **APK:** строго **release** (`app/build/outputs/apk/release/app-release.apk`) — minify/shrink влияют на все метрики; debug не показателен.
- **Устройство:** бюджетное — Xiaomi/Redmi или Samsung A-серия (целевая аудитория).
- Установка: `adb install -r app/build/outputs/apk/release/app-release.apk`
- Клавиатура включена и выбрана дефолтной (онбординг из 2 шагов).
- Package: `org.tatarkeyboard.ime` (release — без суффикса).

---

## PERF-02a — PSS показанной клавиатуры ≤ 30 МБ

Открыть Telegram (или заметки), тапнуть в поле ввода — клавиатура показана. Затем:

```
adb shell dumpsys meminfo org.tatarkeyboard.ime
```

Смотреть строку `TOTAL PSS` (в KB). Бюджет: **≤ 30720 KB** (30 МБ).

Замер ×3 — все три должны быть в бюджете:

| # | Условие замера | TOTAL PSS, KB | ≤ 30720? |
|---|---|---|---|
| 1 | Сразу после открытия клавиатуры | | |
| 2 | После ротации экрана (портрет→ландшафт→портрет) | | |
| 3 | После цикла раскладок tt→ru→en→tt (глобус ×3) | | |

## PERF-02b — холодный старт до показа < 400 мс

Методика: убить процесс → тапнуть в текстовое поле → замерить от тапа до показа клавиатуры.

```
adb shell am force-stop org.tatarkeyboard.ime
adb logcat -c
adb logcat -v time | grep -iE 'latinime|inputmethod'
```

Тапнуть в поле ввода. Окно метрики в логе: от `bindInput` до `onWindowShown` (либо первое сообщение IME-процесса → показ).

Альтернатива (надёжнее для «от тапа»): скринрекорд 60 fps (`adb shell screenrecord /sdcard/cold.mp4`) → покадровый счёт от кадра касания до кадра полностью отрисованной клавиатуры; **24 кадра @ 60 fps = 400 мс**.

×5 прогонов (каждый — с force-stop), медиана < **400 мс**:

| # | Время, мс |
|---|---|
| 1 | |
| 2 | |
| 3 | |
| 4 | |
| 5 | |
| **Медиана** | |

## PERF-03a — ноль аллокаций в цикле отрисовки

Android Studio → Profiler → процесс `org.tatarkeyboard.ime` → **Memory**:

1. Начать запись аллокаций (Record native/Java allocations).
2. 30 секунд непрерывной печати (татарская раскладка, вперемешку пятый ряд/ЙЦУКЕН/shift/backspace).
3. Остановить запись, отфильтровать по `rkr.simplekeyboard`.

Критерии:

| Проверка | Порог | Факт | PASS? |
|---|---|---|---|
| Аллокации из стеков `onDraw`/`onTouchEvent` | **0** | | |
| GC-события за сессию печати (Memory timeline) | **0** | | |

## PERF-03b — janky frames ~0%

```
adb shell dumpsys gfxinfo org.tatarkeyboard.ime reset
```

60 секунд активной печати, затем:

```
adb shell dumpsys gfxinfo org.tatarkeyboard.ime
```

Смотреть `Janky frames: X (Y%)`. Порог: **Y ≤ 1%** («~0%» ROADMAP; доли процента от системных событий допустимы).

| Метрика | Порог | Факт | PASS? |
|---|---|---|---|
| Janky frames, % | ≤ 1% | | |

---

## Закрытие

Все 4 блока PASS → в `.planning/REQUIREMENTS.md`: PERF-02 → `[x]`, PERF-03 → `[x]`, Traceability → Complete (с датой замера и моделью устройства). Любой FAIL → завести issue с числами, бюджет пересматривается только осознанным решением.
