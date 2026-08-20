# HANDOFF — миссия tt-search-fixes

**Статус: закончена.** `docs/EMOJI-SEARCH-FIXES.md` заканчивается `STATUS: done`.

Ветка `codex/version-1.6.0`, три коммита поверх `b0980a8`:

- `0f15289` — дефект 1: каретка вплотную к концу набранного запроса
  (убрано лишнее слагаемое `closeCrossPx`, x каретки переехал в новый чистый
  `EmojiSearchLayout`).
- `43e1891` — дефект 2: при пустом запросе полоса результатов не измеряется и не
  рисуется; мёртвая строка `emoji_search_type_hint` и её переводы удалены, её
  строка убрана из `docs/TATAR-REVIEW-QUEUE.tsv`.
- `e5e95d5` — отчёт `docs/EMOJI-SEARCH-FIXES.md` и шесть скриншотов «после» в
  `docs/emoji-panel/search-fix-*.png`.

Дерево чистое, наружу ничего не отправлено (ни push, ни tag), версия
1.6.0 / versionCode 8 не поднималась, `dist/` и `PUBLISH-CHECKLIST.md` не тронуты.

Проверки: `./gradlew testDebugUnitTest` — 789 тестов, 0 failures (было 780);
`lintVitalRelease` — BUILD SUCCESSFUL; release APK 1 801 038 Б при потолке
3 145 728 Б; `scripts/check-no-internet.sh` — Level 1 и Level 2 OK
(нужен `ANDROID_HOME=~/Android/Sdk`).

Дальше по плану — миссия `tt-version-1.6.1`: поднять версию и собрать артефакт.
Найденное сверх двух дефектов и не чинившееся перечислено в конце
`docs/EMOJI-SEARCH-FIXES.md`.
