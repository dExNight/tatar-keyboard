# HANDOFF — миссия tt-quarantine

**Статус: закончена.** Отчёт — `docs/QUARANTINE.md`, он же несёт маркер завершения последней
строкой. Фраза-маркер намеренно не повторяется больше нигде, включая этот файл.

Ветка `codex/version-1.6.0`. Наружу не уходило ничего. Версия не поднимается, релиз не
собирается (это `tt-version-1.8.3`). Keystore не трогается и не пересоздаётся.

## Карта кода (составлена, сверена по исходникам)

| Что | Где |
|---|---|
| Копия создаётся | `PersonalDictionaryStore.kt:482` `quarantine(directory, file)`, вызов из `open()` `:345` |
| Имя слота | `:496` `personalFileName(subtypeId) + ".quarantine"`, один на язык |
| Копию убирает «Стереть всё» | `:245` пятый `deleted { }` в `clearAll` |
| Флаг извещения (процессный!) | `PersonalDictionaries.kt:73` `@Volatile quarantinePending` + `:130 has…`, `:137 consume…` |
| Извещение показывает | `LatinIME.java:893` `showPersonalDictionaryUnreadableDialog()`, второй шанс `:1318` |
| Экран личного словаря | `SettingsHostActivity.kt:377` `buildPersonalDictionaryScreen()` |
| Мутации/канал отказа | `PersonalDictionaryScreenController.kt`, `SettingsHostActivity.afterPersonalMutation() :526` |
| Формат `.tpers` | `personal/TpersFormat.kt`, валидатор `personal/TpersValidator.kt` |
| Модель записей | `personalstore/PersonalEntries.kt` (`upsert` дедуплицирует по нормализованной форме) |

## Сделано (код и тесты закрыты, всё зелёное)

| Часть | Где | Тесты |
|---|---|---|
| 1. Спасение слов | `personalstore/PersonalQuarantineSalvage.kt` (новый) | `PersonalQuarantineSalvageTest` — 19 |
| 2. Экран | `SettingsHostActivity.addPersonalQuarantineCards()` + `PersonalDictionaryScreenController.quarantines/restoreQuarantine/discardQuarantine` | `PersonalQuarantineScreenSourceContractTest` — 9 |
| 3. Извещение на диске | `PersonalDictionaryStore`: `quarantineNoticeFileName()`, запись в `quarantine()`, подъём в `open()`, снятие в `noticeDelivered()`; `PersonalDictionaries.consumeQuarantineNotice()` снимает у всех живых сторов | `PersonalQuarantineRecoveryTest` — 22 |
| 4. Тесты | см. выше | — |
| 6. Дыра в трёх мутациях | `PersonalDictionaryStore.report()` — единственный выход ответа, внутри `try/catch`; мёртвая ветка убрана, `deleteFile()` стал `Unit` | `PersonalQuarantineRecoveryTest`, `PersonalQuarantineNoticeSourceContractTest` |

Полный прогон: **913 тестов, 0 падений, 1 пропуск**. `lintVitalRelease` — чисто.

Строки: 10 новых + переписанное `personal_dictionary_unreadable` в `values`, `values-ru`,
`values-tt`; строки очереди добавлены в `docs/TATAR-REVIEW-QUEUE.tsv` (`approved`, правило
оператора от 2026-08-20).

### Красное до правки

Доказано на HEAD в отдельном worktree (`git worktree add … HEAD`, свой `local.properties`
и `gradle-wrapper.jar`):

* маркер извещения не пишется — `AssertionError: and written down, because nobody may have been listening`;
* обратный вызов с исключением уходил в uncaught-обработчик воркера во **всех пяти** случаях:
  `addManually saved`, `addManually rejected`, `forget present`, `forget absent`, `clearAll`.

Один тест (`aNoticeSeamThatThrowsDoesNotKillTheWorker`) на HEAD **зелёный**: этот вызов уже
стоял внутри `try` в `open()`. Он оставлен как охранник от регрессии и так и описан в отчёте —
не выдавать его за закрытую дыру.

## Что дальше

Ничего по этой миссии. Всё, что осталось за её границами, перечислено в отчёте разделом
«Найдено по дороге, не починено» — это список оператору, а не задача.

Наружу по-прежнему не уходило ничего: ни push, ни тега, ни релиза. Изменения лежат в рабочем
дереве незакоммиченными — коммит остаётся за оператором.

## Незакрытых вопросов к оператору нет
