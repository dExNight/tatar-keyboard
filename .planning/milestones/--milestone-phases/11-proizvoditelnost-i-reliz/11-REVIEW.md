# 11-REVIEW — Phase 11 (финальный релиз), diff 77e714d..HEAD

**Depth:** standard · **Date:** 2026-07-18 · **Verdict: PASS с замечаниями** — блокеров нет; 1 warning стоит закрыть до заявки в IzzyOnDroid (шаг 9 чек-листа), остальное — minor.

---

## 1. keep.xml — PASS

`app/src/main/res/raw/keep.xml:3` — синтаксис корректен: корень `<resources>` с `xmlns:tools`, атрибут `tools:keep` со списком через запятую, каждая маска с префиксом типа (`@xml/`, `@string/`). Расположение в `res/raw/` — каноническое для resource shrinker.

Полнота масок сверена со всеми call-sites `getIdentifier` в кодовой базе (grep по `app/src/main`, найдено ровно 4):

| Call-site | Резолвит | Маска |
|---|---|---|
| `KeyboardLayoutSet.java:275` | `keyboard_layout_set_<name>` (xml) — из него по ссылкам тянутся `kbd_*`/`rows_*`/`rowkeys_*`/`row_*` | `@xml/keyboard_layout_set_*` + 4 маски вложенных xml |
| `LocaleResourceUtils.java:77` | `string/locale_name_in_root_locale_<locale>` | покрыта глобом `@string/locale_name_*` |
| `LocaleResourceUtils.java:85` | `string/locale_name_<locale>` | `@string/locale_name_*` |
| `KeyboardTextsSet.java:134` | `!string/<name>` из раскладок; grep всех `!string/`-ссылок в res+java даёт только `label_pause_key`, `label_wait_key` | `@string/label_*` |

Пропущенных пользователей `getIdentifier` нет. Маски `kbd_*`/`rows_*`/`rowkeys_*`/`row_*` нужны, потому что корневой `keyboard_layout_set_*` сам держится только через keep — без него shrinker счёл бы всю цепочку недостижимой. Комплект полный.

## 2. app/build.gradle — PASS

- `shrinkResources true` только в `release` (app/build.gradle:34), рядом с уже включённым `minifyEnabled true` (без которого shrinkResources не работает) — корректно.
- `versionCode 1`, `versionName "1.0.0"` — соответствует релизу.
- Подпись: оба места защищены `keystorePropsFile.exists()` — и объявление `signingConfigs.release` (строка 19), и присвоение в buildType (строка 36). В CI, где `keystore.properties` отсутствует (gitignored, в git не отслеживается — проверено `git ls-files`), `assembleRelease` собирает unsigned APK без падения. Гейт в CI ссылается на `app-release-unsigned.apk` — совпадает с конвенцией AGP для unsigned.
- `minSdkVersion 24` не тронут (в диффе только versionName и shrinkResources).

## 3. .github/workflows/ci.yml — PASS

- Новые шаги синтаксически корректны (обычные `run:`-шаги, отступы верные).
- Size gate `test $(stat -c%s …) -le 3145728` — fail-capable: `test` возвращает ненулевой код и роняет job; `stat -c%s` — верный GNU-синтаксис для ubuntu-runner. Порог 3 145 728 = ровно 3 МБ; фактический APK 681 070 байт — запас ~4.6×.
- Секретов нет (`grep secrets` пусто), `permissions: contents: read`, автопубликации нет — единственный артефакт-аплоад это debug APK, как и раньше.
- Minor (M3 ниже): level-2 проверка INTERNET гоняется по debug APK, хотя release APK в этом же job уже собран.

## 4. Документация

### README.md — PASS
- Ссылка установки `[GitHub Releases](../../releases)` — относительная, на GitHub резолвится в `/releases` любого owner'а; мёртвых абсолютных ссылок на несуществующий репозиторий нет.
- URL-плейсхолдеры (`*.invalid`) живут не в README, а в `strings-appname.xml`, явно помечены комментарием (RFC 2606) и закрываются шагом 3 PUBLISH-CHECKLIST — до финальной сборки шага 6, порядок верный.
- «Android 7.0+ (minSdk 24)» — соответствие API↔версии верное. Инструкция восстановления gradle-wrapper.jar совпадает с CI (v9.6.0 + sha256).
- Атрибуция: форк Simple Keyboard указан со ссылкой на upstream, упомянута база AOSP LatinIME.

### PRIVACY.md — PASS
Согласована с no-INTERNET: манифест содержит единственный `uses-permission` — VIBRATE (AndroidManifest.xml:19), что ровно совпадает с текстом политики. Заявленная проверяемость (aapt2 + CI-гейт) реально существует (`scripts/check-no-internet.sh`, оба уровня).

### CHANGELOG.md — PASS
Пункты v1.0.0 соответствуют реализованному (раскладки, глобус, long-press дубли, directBoot, TalkBack, онбординг). «APK меньше 1 МБ» — верно: подписанный release 681 070 байт ≈ 665 КБ.

### docs/PUBLISH-CHECKLIST.md — PASS
- Шаг 1 — бэкап `release.jks` + `keystore.properties` **до всего остального**, с явным «потеря ключа = навсегда» — есть, первым.
- Красный негативный тест CI-гейта включён (шаг 5.2) с полной последовательностью и зачисткой ветки.
- `stat -f%z` в шаге 6 — BSD/macOS-синтаксис; корректен для машины разработчика (macOS), а в CI используется GNU-вариант. Не ошибка.
- Порядок безопасный: URL-замена (шаг 3) → push → CI → финальная локальная сборка (шаг 6) → тег → Release. Автопубликации нигде нет — всё руками, о чём файл сам предупреждает.

### 11-PERF-CHECKLIST.md — PASS
- PSS: `dumpsys meminfo`, порог 30 720 KB = 30 МБ — верно, ×3 замера.
- Холодный старт: вместо `am start` (который для IME неприменим — IME не запускается активити) используется logcat-окно `bindInput→onWindowShown` + покадровый screenrecord; «24 кадра @ 60 fps = 400 мс» — арифметика верна. Это методически правильнее, чем `am start`.
- Аллокации: Profiler по стекам `onDraw`/`onTouchEvent`, порог 0 — соответствует бюджету «ноль аллокаций в цикле отрисовки».
- Janky: `dumpsys gfxinfo <pkg> reset` → печать → `dumpsys gfxinfo <pkg>`, порог ≤1% — синтаксис и трактовка «~0%» корректны.

## 5. Apple-wording и Apache-2.0 — PASS

- Grep по `iphone|ios|apple|sf pro|sf symbols` во внешних файлах (README, PRIVACY, CHANGELOG, docs/, metadata/, strings) — только ложные срабатывания (подстрока «ios» внутри португальских/венгерских/литовских слов). Наружу Apple-формулировок нет.
- Apache-2.0: полный текст LICENSE в корне (оригинальные 202 строки), заголовки-копирайты AOSP в исходниках сохранены, README явно кредитует Simple Keyboard и AOSP LatinIME. По NOTICE: обязанность воспроизводить NOTICE (§4d) действует только если upstream его поставляет; в рабочей копии форка NOTICE-файла нет и в истории git он не удалялся — по моим данным upstream rkkr/simple-keyboard NOTICE не имеет (в сети не перепроверял — офлайн-сессия), так что нарушения нет. Формально §4(b) просит «prominent notices» в изменённых файлах — на практике это закрывается git-историей и README-атрибуцией; риска не вижу.

---

## Findings

### W1 (warning — закрыть до шага 9 чек-листа): metadata/ всё ещё брендирован «Simple Keyboard»
`metadata/en-US/full_description.txt` и `metadata/pt-BR/full_description.txt` — унаследованные от upstream описания: «Откройте “Simple Keyboard” из лаунчера», без слова про татарскую раскладку. IzzyOnDroid автоматически подхватывает Fastlane-структуру `metadata/` из репозитория для текста листинга — при заявке (PUBLISH-CHECKLIST шаг 9) наружу уйдёт чужое имя и неверные инструкции. Переписать под Tatar Keyboard (или удалить каталог до заявки, если листинг-текст подаётся иначе). Вне диффа фазы, но прямо задевает её результат — публикацию.

### M1 (minor): комментарий у setup_message занижает счёт переводов
`app/src/main/res/values/strings.xml:124` — комментарий говорит «35 legacy translations», фактически `setup_message` присутствует в 44 файлах `values-*/strings.xml`. На поведение не влияет; поправить число при бэклог-зачистке.

### M2 (minor): даты в PRIVACY.md и CHANGELOG.md — 2026-07-19
Завтрашняя дата относительно сегодняшней (2026-07-18). Если публикация не завтра — даты станут неверными; проставить фактическую дату публикации на шаге 3–4 чек-листа.

### M3 (minor): level-2 no-INTERNET проверка в CI — только debug APK
`ci.yml:50` гоняет `check-no-internet.sh` по `app-debug.apk`, хотя release APK в job уже собран. Наружу уходит именно release; добавить второй вызов с `app-release-unsigned.apk` — одна строка, закрывает теоретический разрыв (buildType-специфичный merged manifest).

---

## Сводка по ограничениям

- **no INTERNET** — подтверждено: манифест (только VIBRATE) + двухуровневый CI-гейт.
- **keystore не в git** — подтверждено: `release.jks` и `keystore.properties` под `.gitignore` (строки 47–48), `git ls-files` их не видит.
- **no auto-publish** — подтверждено: CI только собирает и проверяет; публикация целиком ручная по чек-листу.
