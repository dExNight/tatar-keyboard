# Майлстоун v2 — словарь и подсказки

Старт: 2026-07-21. План продолжает решения Фазы R из `docs/MILESTONE-v1.1.md`.
GSD не используется по прямому решению пользователя.

Статус на 2026-07-24: аналитическое ядро D0 выполнено, но отдельное независимое
fail-closed ревью D0 не записано. D1a–D1e реализованы последовательно в
`codex/d1-sequential`; D1f и внешняя публикация остаются открыты. Детальный frozen
контракт и текущие gates находятся в `PROPOSALS.md`, доказательства фаз — в
`docs/DICTIONARY-D0.md` и `docs/DICTIONARY-D1A.md`–`docs/DICTIONARY-D1E.md`.

Рамки: без INTERNET permission, NDK/JNI и новых Android-зависимостей; APK ≤ 3 МБ,
PSS клавиатуры ≤ 30 МБ, холодный старт < 400 мс; корпусные данные не коммитить.
Код и данные лицензировать/атрибутировать раздельно.

## D0 — проверка данных до Android-кода

- [x] Инвентаризировать официальные татарские downloads Leipzig и подтвердить лицензию
  первичными страницами.
- [x] Реализовать воспроизводимый анализ нескольких `*-words.txt` на Python stdlib:
  нормализация, фильтрация, сумма частот дубликатов, детерминированные tie-breaks,
  coverage и размер.
- [x] Покрыть анализатор автоматическими fixture-тестами, включая татарский Unicode,
  дубликаты, malformed input и ties.
- [x] Прогнать `tat_mixed_2015_1M`, `tat_news_2015_1M`, `tat_web_2018_1M` и записать
  фактические top-100k/150k/250k показатели.
- [x] Подготовить, но не отправлять запрос разрешения в corpus.tatar.
- [ ] Пройти независимое fail-closed ревью D0.

Гейт D0: решение о целевом размере D1 принято по фактическому отчёту покрытия:
Leipzig top-100k, 97.3318% self-coverage принятых токенов. Независимое D0-ревью выше
остаётся открытым и не представлено как выполненное.

## D1 — татарские подсказки, без автозамены

- [x] **D1a:** зафиксировать отдельную CC BY 4.0 лицензию/NOTICE словаря, источники,
  версию и воспроизводимый data pipeline; исходные корпуса не хранить в git.
- [x] **D1a:** сформировать детерминированный татарский top-100k asset в пределах
  лимитов 700 KB compressed / 2.8 MiB unpacked и проверить held-out gap.
- [x] **D1b:** реализовать атомарную fail-closed распаковку в device-protected storage,
  version activation, leases и ограниченное retention.
- [x] **D1c:** добавить ленивую 40dp Canvas-полосу из трёх подсказок, virtual a11y nodes
  и host-контракты `onComputeInsets`/touchable region.
- [x] **D1d:** реализовать mmap prefix lookup top-3 без NDK и однопоточный bounded
  latest-only worker с generation guards, lifecycle/FD race tests и stale suppression.
- [x] **D1e:** интегрировать opt-in настройку (по умолчанию OFF), татарский subtype,
  password/privacy gates, cached-prefix lookup, TalkBack и guarded delete+commit.
- [x] **D1e core fixes:** закрыть readiness/lifecycle и atomic candidate-binding
  блокеры; обязательные regression tests находятся в локальной истории через `a277283`.
- [x] **D1e hardening:** warm-engine `tt→ru→tt`, late readiness after finish и
  preparing/unavailable hidden-state покрыты зелёными JVM-тестами; это не device evidence.
- [ ] Пройти независимый re-audit текущего D1e hardening и записать verdict/evidence.
- [ ] **D1f artifact gate:** финальный clean test + `lintVitalRelease` + build,
  no-INTERNET, signed 1.2.0/vc4 version/certificate/permission/size audit и versioned APK
  с SHA-256.
- [ ] **D1f device/performance:** Samsung/целевое устройство, все touch/insets/rotation/
  moreKeys cases, TalkBack, privacy matrix, PSS, cold start, latency, allocations/jank и
  lifecycle FD measurements.
- [ ] Получить отдельно записанную вычитку новых татарских строк носителем языка.
- [ ] Выполнить push/merge/tag, анонимно проверяемый GitHub Release и заявку через
  Codeberg `IzzyOnDroid/repodata`; до факта публикации внешние requirements не закрывать.

Итог D1: функциональный и автоматизируемый scope D1a–D1e реализован; майлстоун целиком
не завершён, пока все открытые пункты D1f, native proofread и публикация не закрыты.

## D2 — русский словарь

- [ ] Повторить лицензируемый pipeline для русского корпуса с меньшим лимитом форм.
- [ ] Переключать словарь строго по активному subtype без смешения tt/ru.
- [ ] Повторить функциональные, privacy и resource gates; общий APK ≤ 3 МБ.

## D3 — автозамена с отменой

- [ ] Только после обкатки качества D1/D2 добавить отдельный opt-in тумблер.
- [ ] Заменять на пробеле через delete+commit без composing и хранить точное состояние
  одной последней замены.
- [ ] Backspace сразу после замены восстанавливает исходный ввод; любой другой ввод
  инвалидирует revert state.
- [ ] Закрыть рассинхрон кэша/поля, code-point deletion, password и editor edge cases.
