# HANDOFF — миссия tatar-apk-audit

Обновлено: 2026-08-18. Ветка: `codex/apk-audit-2026-08-18` (от `codex/e5-bigram-prediction`,
базовый коммит `9a2a3196`).

## Что просили

Пересобрать подписанный release APK `1.2.0` / `versionCode 4` с нуля из чистого checkout и
заново пройти artifact audit шагов 2 и 4 `docs/PUBLISH-CHECKLIST.md` с датой 2026-08-18,
потому что прежний аудированный артефакт локально утрачен. Досье:
`~/.supermanager/missions/tatar-apk-audit/dossier.md`.

## Что сделано

- Пересборка выполнена дословной командой чеклиста
  (`./gradlew clean test lintVitalRelease assembleDebug assembleRelease --rerun-tasks
  --console=plain`) → `BUILD SUCCESSFUL`, 83/83 задач.
- Пройдено и зафиксировано числами: 709 тестов 0/0/0; `lintVitalRelease` OK; no-INTERNET
  exit 0 на debug и release, оба уровня; versionCode 4 / versionName 1.2.0 по
  `output-metadata.json` и по `aapt2 dump badging`; permission dump — только `VIBRATE`;
  размер release 1 726 976 Б (потолок 3 145 728 Б, бюджет D1 1,7 MiB — оба pass).
- Отчёт: **`docs/APK-AUDIT-2026-08-18.md`**, в конце `STATUS: blocked-on-keystore`.
- `docs/PUBLISH-CHECKLIST.md`: раздел 2 — новая evidence-таблица с датой 2026-08-18 (старая
  сохранена как история), галочки переставлены по факту; раздел 4 — три отметки сняты в
  `[ ]`; в шапку добавлен раздел «Повторный artifact audit выполнен, подпись открыта».
- Разделы 3 и 5–7 не трогались. Статусы вычитки в `docs/TATAR-REVIEW-QUEUE.tsv` и
  `docs/DICTIONARY-E3-TYPO-REVIEW.tsv` не трогались вообще.

## Чем заблокировано

**Релизного keystore на этой машине нет.** `keystore.properties` отсутствует, `find / -name
'*.jks'` находит только системный `java-cacerts.jks`. `app/build.gradle` подключает
`signingConfigs.release` только при наличии этого файла, поэтому Gradle собрал
`app-release-unsigned.apk`; `apksigner verify` → `DOES NOT VERIFY / Missing
META-INF/MANIFEST.MF`. Прежний аудит шёл на Mac — ключ, судя по всему, остался там.

Не закрыты из-за этого: схема подписи, signer certificate и сверка с историческим
`cdd8c535…`, `apksigner verify`, финальный SHA-256 релиза, весь шаг 4.

Вопрос оператору отправлен: `.smgr/tatar-apk-audit/ask.json` (варианты: перенести ключ —
рекомендуемый; сгенерировать новый — не рекомендую, рвёт upgrade path; закрыть аудит по
неподписанному — сделано как промежуточное). Ответ придёт файлом
`.smgr/tatar-apk-audit/answer-N.md`.

## Что делать дальше, когда придёт ответ

Если ключ появился (`keystore.properties` + `.jks` в корне):

1. `export ANDROID_HOME=/home/tarchok/Android/Sdk ANDROID_SDK_ROOT=$ANDROID_HOME`
2. Повторить команду сборки из раздела 2 целиком — артефакт должен получиться
   `app-release.apk`, не `-unsigned`.
3. `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`
   — сверить signer certificate SHA-256 с `cdd8c5350ddc86f13cd89b5bfb55ca33c13efba77beb4d4ccb75d5e6b961b09e`.
4. Выполнить командный блок шага 4 (`mkdir -p dist` — каталог git-ignored и сейчас его нет).
5. Дописать в `docs/APK-AUDIT-2026-08-18.md` поля подписи и шага 4, сменить последнюю
   строку на `STATUS: done`; перенести те же числа в evidence-таблицу 2026-08-18 и
   проставить галочки в разделах 2 и 4.

Если ответ «генерируем новый ключ» — только по явному «да» оператора; в отчёте обязательно
зафиксировать, что сертификат не совпадает с историческим и upgrade path с v1.1.0 порван.

## Что ещё вынесено оператору

Дерево `9a2a3196` содержит фазы E1–E5 поверх набора, который `CHANGELOG.md` описывает как
1.2.0, при неизменных `versionCode 4` / `versionName 1.2.0`: отсюда 709 тестов вместо 186 и
1 726 976 Б вместо 1 446 111 Б. Требований это не нарушает, но changelog не описывает
реальное содержимое. Вопрос приложен вторым пунктом к `ask.json`; версию я не менял.

## Полезные пути

- Логи сборок: `/tmp/claude-1000/-home-tarchok-Projects-tatar-keyboard/c580062c-40ca-4d73-9522-3fbd43877e24/scratchpad/`
  (`gradle-build.log` — прогон с `--offline`, `gradle-build-nooffline.log` — итоговый).
- Инструменты: `/home/tarchok/Android/Sdk/build-tools/37.0.0/{aapt2,apksigner}` (в PATH их нет).
- Незакоммиченные правки оператора в `docs/TATAR-REVIEW-QUEUE.tsv` трогать нельзя.
