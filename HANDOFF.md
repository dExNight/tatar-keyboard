# HANDOFF — миссия tt-small-fixes

**Статус: в работе.** Отчёт — `docs/SMALL-FIXES.md`; маркер завершения ещё не стоит.

Ветка `codex/version-1.6.0`, база `3aa6d21`. Версия, `CHANGELOG.md`, `dist/`,
keystore не тронуты; наружу ничего не отправлялось.

## Сделано

* **Пункт 1** (`ffc6002`) — запрос из одних пробелов считается пустым.
  `EmojiSearchLayout.hasQuery` (isNotBlank) — единственный ответ на «есть ли запрос»;
  от него зависят полоса, высота, подсказка и узел TalkBack. Собственных
  `queryText.isEmpty()` у `EmojiSearchView` не осталось, и это закреплено тестом.
* **Пункт 2** (`0e7369c`) — каретка не стоит на подсказке. Подсказка отходит вправо:
  `EmojiSearchLayout.hintLeft(caretX, strokeWidth, gap)`, 3dp воздуха за правым краем
  каретки. Выбор из трёх вариантов обоснован в отчёте.
* **Отчёт и скриншоты** (`5e5ebeb`) — `docs/SMALL-FIXES.md`, десять снимков
  `docs/emoji-panel/small-fix-*`.

Тесты, краснеющие до правок, зафиксированы: `.smgr/tt-small-fixes/red-items-1-2.txt`,
`red-item-2.txt`.

## Осталось

**Пункт 3 — уборка планшетного кода** (`res/values-sw600dp/config.xml`
`config_key_hysteresis_distance` 35dp и `BogusMoveEventDetector`). Досье требует:
не потерять то, что чинила `tt-touch-slop` (коммит `0dc5e9f`), тесты на это должны
остаться зелёными; не удалять то, про что не показано, что оно мертво; отложить с
объяснением — нормальный исход.

Потом: полный JVM suite с записью числа тестов и `STATUS: done` последней строкой
`docs/SMALL-FIXES.md` (до этого момента маркер в файле не упоминать).

## Состояние эмулятора

`tatar_e5_test` запущен. Ради скриншотов изменено и **подлежит возврату в конце**:

* `persist.sys.locale` = `ru-RU` (было en-US; менялось через `adb root`,
  `setprop`, `adb shell stop && start`);
* выбранная клавиатура — `org.tatarkeyboard.ime.debug` (было
  `org.tatarkeyboard.ime`); ночной режим переключался `cmd uimode night`.

`assembleDebug` ставится **отдельным пакетом** `org.tatarkeyboard.ime.debug` и не
обновляет выбранную в системе релизную клавиатуру — на этом легко потерять час.
