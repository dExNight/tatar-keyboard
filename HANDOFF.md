# HANDOFF — миссия tatar-apk-audit

Обновлено: 2026-08-18. Ветка: `codex/apk-audit-2026-08-18` (от `codex/e5-bigram-prediction`,
базовый коммит `9a2a3196`). Состояние миссии: **выполнена**.

## Что просили

Пересобрать подписанный release APK `1.2.0` / `versionCode 4` с нуля из чистого checkout и
заново пройти artifact audit шагов 2 и 4 `docs/PUBLISH-CHECKLIST.md` с датой 2026-08-18,
потому что прежний аудированный артефакт локально утрачен. Досье:
`~/.supermanager/missions/tatar-apk-audit/dossier.md`.

## Что сделано

Пересборка дословной командой чеклиста (`./gradlew clean test lintVitalRelease assembleDebug
assembleRelease --rerun-tasks --console=plain`) → `BUILD SUCCESSFUL`, 84/84 задач.

| Проверка | Результат |
|---|---|
| JVM tests | 709, 0 failures / 0 errors / 0 skipped |
| `lintVitalRelease` | `BUILD SUCCESSFUL` |
| no-INTERNET | exit 0 на debug и release, оба уровня |
| Версия | versionCode 4 / versionName 1.2.0 |
| Permissions | только `VIBRATE`, по одной строке в дампе |
| Размер release | 1 731 072 Б (потолок 3 145 728, бюджет D1 1,7 MiB — оба pass) |
| Подпись | `Verifies`, v2 only, 1 signer, RSA 4096, `CN=Tatar Keyboard` |
| Final SHA-256 | `18fc03695ec6421c745e536abda32c7cdf2d8779acecaae095d5a5e561a77256` |
| Шаг 4 | `dist/tatar-keyboard-1.2.0.apk`, `cmp` без различий, verify копии → `Verifies` |

Отчёт: **`docs/APK-AUDIT-2026-08-18.md`**, в конце `STATUS: done`.
`docs/PUBLISH-CHECKLIST.md`: раздел 2 — новая evidence-таблица с датой 2026-08-18 (старая
сохранена как история), галочки переставлены по факту; раздел 4 — закрыт целиком; в шапку
добавлен раздел «Повторный artifact audit выполнен, подпись — новым ключом».

Разделы 3 и 5–7 не трогались. Статусы вычитки в `docs/TATAR-REVIEW-QUEUE.tsv` и
`docs/DICTIONARY-E3-TYPO-REVIEW.tsv` не трогались вообще.

## Главное, что нужно знать дальше

**Подписано новым ключом.** Исторического keystore на машине не было; оператор ответом на
`.smgr/tatar-apk-audit/ask.json` выбрал вариант `new_keystore`, и ключ сгенерирован заново:
`tatar-keyboard-release.jks` в корне (PKCS12, alias `tatar-keyboard`, RSA 4096, годен до
2054-01-03), конфиг — `keystore.properties` в корне, оба git-ignored, права 600.

Сертификат `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad` **не совпадает**
с историческим `cdd8c5350ddc86f13cd89b5bfb55ca33c13efba77beb4d4ccb75d5e6b961b09e`. Отсюда:

1. Установка поверх старой сборки на тестовом Samsung не пройдёт — приложение надо удалить,
   локальные данные уйдут.
2. **Бэкап ключа обязателен до первой публикации.** Он в одном экземпляре, на одной машине,
   вне git. Потеря = приложение нельзя обновить никогда.
3. Упоминания старого сертификата как действующего остались в `PROPOSALS.md` и в разделе
   «Историческая справка v1.1.0» чеклиста — стоит поправить перед выпуском.
4. Вернуться к историческому ключу можно только пока ничего не опубликовано: заменить
   `keystore.properties`/`.jks`, пересобрать, переснять весь artifact audit.

**Открытый вопрос по версии.** Дерево `9a2a3196` содержит фазы E1–E5 поверх набора, который
`CHANGELOG.md` описывает как 1.2.0, при неизменных `versionCode 4` / `versionName 1.2.0`:
709 тестов вместо 186, 1 731 072 Б вместо 1 446 111 Б. Вопрос был задан оператору вместе с
вопросом про ключ, ответ пришёл только по ключу. Решение — выпускать расширенный набор под
1.2.0/4 с переписанным changelog или отделять E1–E5 в следующую версию — остаётся за
оператором. Версию я не менял.

## Что осталось до релиза (вне этой миссии)

Раздел 1 (freeze, вычитка носителем, release commit), раздел 3 (Device-UAT — снят как
блокер, но не пройден), разделы 5–7 (push, CI, tag/Release, IzzyOnDroid). Ничего наружу не
отправлялось: push не делался, tag и Release не создавались.

## Полезные пути

- Логи сборок: `/tmp/claude-1000/-home-tarchok-Projects-tatar-keyboard/c580062c-40ca-4d73-9522-3fbd43877e24/scratchpad/`
  (`gradle-build-signed.log` — итоговый подписанный прогон; `gradle-build.log` и
  `gradle-build-nooffline.log` — более ранние, до генерации ключа).
- Инструменты: `/home/tarchok/Android/Sdk/build-tools/37.0.0/{aapt2,apksigner}` (в PATH их нет).
  Сборке нужен `export ANDROID_HOME=/home/tarchok/Android/Sdk`.
- `storeFile` в `keystore.properties` резолвится относительно `app/`, а не корня — поэтому
  там `../tatar-keyboard-release.jks`.
- Незакоммиченная правка оператора в `docs/TATAR-REVIEW-QUEUE.tsv` — не трогать.
