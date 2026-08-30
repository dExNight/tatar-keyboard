# RESTRUCTURE — журнал кампании аудита, реструктуризации, очистки и оптимизации

Кампания по плану `docs/RESTRUCTURE-PLAN.md` (утверждён оператором 2026-08-30).
База: ветка `codex/version-1.6.0`, HEAD `30387472`, версия 1.9.4 (versionCode 20).

## Фаза 0. Подготовка и сеть безопасности

Сделано:

- Страховочная ветка `backup/pre-restructure` → `30387472` (локально).
- `.gitignore`: добавлены `*.aab` и `.pytest_cache/` (ранее покрывался только
  автогенерированным `.gitignore` внутри каталога — хрупко); зафиксировано
  датированное уточнение политики по `docs/*.generated.json`: три JSON в
  `docs/lang-priority/` закоммичены осознанно как пины-свидетельства (паттерн
  верхнего уровня их никогда не покрывал; три верхнеуровневых JSON
  `DICTIONARY-E5A`/`RUSSIAN-BIGRAMS.*` на диске игнорируются корректно).
- План кампании зафиксирован в `docs/RESTRUCTURE-PLAN.md`.

### Замеры «до» (базовая линия)

| Метрика | Значение | Источник |
|---|---|---|
| Release APK (1.9.4, подписан) | 2 538 949 Б (запас до 3 МиБ 19,29 %) | `dist/tatar-keyboard-1.9.4.apk` |
| `classes.dex` (несжатый, внутри APK) | 406 768 Б | `unzip -l` |
| `resources.arsc` | 309 272 Б | `unzip -l` |
| Файлов в APK | 548 | `unzip -l` |
| JVM-тесты (`./gradlew test`, --rerun-tasks) | 974 / 0 failures / 0 errors | `app/build/test-results/testDebugUnitTest/` |
| Python-тесты (7 файлов, `unittest`) | 181 / 0 failures | прогон 2026-08-30 |
| Холодный старт, медиана (эмулятор) | 126,3 мс при инварианте 400 мс | `docs/APK-AUDIT-1.9.4.md` (перемер — в фазе 4; adb-устройств сейчас нет, AVD в наличии: `tatar_e5_test`, `tt_prefix3`, `tt_suggest_a14`) |
| git pack | 29,3 МиБ, 17 502 объекта, garbage 0 | `git count-objects -vH` |

### Примечание по инструментарию

`pytest` в системе не установлен; тесты `tests/` — классы `unittest.TestCase`,
запускаются напрямую: `python3 tests/<каталог>/test_<имя>.py` (все 7 файлов
зелёные). Установка pytest не требуется.
