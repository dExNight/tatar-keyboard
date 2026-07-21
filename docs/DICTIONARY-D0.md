# D0 — покрытие татарского словаря по Leipzig Corpora

Дата исследования и доступа к источникам: 2026-07-21. Это анализ данных до Android-кода;
корпусы и производные словники в репозиторий не добавлены.

## Первичные источники и лицензия

- [Каталог татарских корпусов Leipzig](https://wortschatz.uni-leipzig.de/en/download/tat)
  перечисляет семейства `tat_community_2017`, `tat_mixed_2015`,
  `tat_news_2005-2011`, `tat_news_2015`, `tat_web_2018`,
  `tat_wikipedia_2016` и `tat_wikipedia_2021`.
- [Terms of Usage](https://wortschatz.uni-leipzig.de/en/usage) отделяют downloadable text
  corpora от остальных данных сайта и прямо дают корпусам лицензию
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Упомянутая там же CC BY-NC
  относится к другим данным/приложениям, не к скачиваемым текстовым корпусам.
- [Описание формата](https://wortschatz.uni-leipzig.de/documents/Format_Download_File-eng.pdf)
  и фактические файлы подтверждают формат `id<TAB>word<TAB>frequency`.
- Рекомендованная Leipzig ссылка: D. Goldhahn, T. Eckart, U. Quasthoff,
  “Building Large Monolingual Dictionaries at the Leipzig Corpora Collection:
  From 100 to 200 Languages”, LREC 2012.

CC BY 4.0 совместима с распространением приложения, но данные не становятся
Apache-2.0: если словарь войдёт в D1, он должен поставляться как отдельный CC BY 4.0
data-компонент с атрибуцией, ссылкой на лицензию и отметкой преобразований.

Для baseline выбраны максимальные 1M-срезы трёх высокообъёмных семейств, указанных в
Фазе R. Меньшие размеры тех же семейств не объединялись, чтобы не считать вложенные
выборки повторно. `community`, старый `news` и `wikipedia` не скачивались: это другие
или меньшие срезы, не входившие в согласованный baseline; полный официальный каталог
при этом инвентаризирован.

Максимальные доступные архивы остальных веток каталога: [community 2017 — all](https://downloads.wortschatz-leipzig.de/corpora/tat_community_2017.tar.gz),
[news 2005–2011 — 300K](https://downloads.wortschatz-leipzig.de/corpora/tat_news_2005-2011_300K.tar.gz),
[Wikipedia 2016 — 100K](https://downloads.wortschatz-leipzig.de/corpora/tat_wikipedia_2016_100K.tar.gz)
и [Wikipedia 2021 — 100K](https://downloads.wortschatz-leipzig.de/corpora/tat_wikipedia_2021_100K.tar.gz).
Это инвентарь URL, а не утверждение о независимости содержащихся текстов.

| Корпус | Прямой URL | Размер архива | Last-Modified | SHA-256 архива |
|---|---|---:|---|---|
| `tat_mixed_2015_1M` | [tar.gz](https://downloads.wortschatz-leipzig.de/corpora/tat_mixed_2015_1M.tar.gz) | 200,822,852 B | 2020-11-21 | `c5a27c731116c2540a1053b8b9d6cb3a16134f519f0bf7535bca274173d01fc7` |
| `tat_news_2015_1M` | [tar.gz](https://downloads.wortschatz-leipzig.de/corpora/tat_news_2015_1M.tar.gz) | 201,497,714 B | 2020-11-20 | `e7421c8d036bfaf6ce5dec6b2a121b2c85a55ae1a26004e51b71202f6765b2d7` |
| `tat_web_2018_1M` | [tar.gz](https://downloads.wortschatz-leipzig.de/corpora/tat_web_2018_1M.tar.gz) | 180,057,807 B | 2020-11-20 | `de7816dbd8334ad9cd516be43ddca76e157316db9a53576dc3e813005d7b3f87` |

SHA-256 выше измерены локально, а не опубликованы провайдером. Архивы прошли `gzip -t`.
Из них локально извлекались только `*-words.txt`; после анализа они не нужны проекту.

## Воспроизведение

Нужен только Python 3.10+ и стандартная библиотека. Новых runtime/build/Android-зависимостей
нет.

```bash
analysis_dir=$(mktemp -d /tmp/tatar-leipzig-d0.XXXXXX)
mkdir -p "$analysis_dir/words"

for corpus in tat_mixed_2015_1M tat_news_2015_1M tat_web_2018_1M; do
  curl -L --fail --show-error --retry 5 \
    --output "$analysis_dir/$corpus.tar.gz" \
    "https://downloads.wortschatz-leipzig.de/corpora/$corpus.tar.gz"
  gzip -t "$analysis_dir/$corpus.tar.gz"
  tar -xzf "$analysis_dir/$corpus.tar.gz" -C "$analysis_dir/words" \
    "$corpus/$corpus-words.txt"
done

python3 scripts/dictionary_coverage.py --pretty \
  "$analysis_dir/words/tat_mixed_2015_1M/tat_mixed_2015_1M-words.txt" \
  "$analysis_dir/words/tat_news_2015_1M/tat_news_2015_1M-words.txt" \
  "$analysis_dir/words/tat_web_2018_1M/tat_web_2018_1M-words.txt" \
  > "$analysis_dir/report.json"
```

Тесты:

```bash
python3 -m unittest discover -s tests/dictionary_coverage -p 'test_*.py' -v
```

Фактический прогон: Python 3.14.5, macOS arm64, 3.87 s. JSON-отчёт и локальные данные
оставлены только во временной директории. Опциональный `--output-words` также предназначен
только для локального файла и прямо помечен в help как лицензируемые данные, которые нельзя
коммитить.

## Правила анализа

1. Принимаются canonical Leipzig 3-column и сокращённый 2-column
   `word<TAB>frequency`; id и frequency должны быть положительными целыми.
2. Malformed-строка по умолчанию аварийно останавливает расчёт. Явный
   `--skip-malformed` пропускает и считает такие строки. В production-прогоне malformed = 0.
3. Слово обрезается по краям, приводится к Unicode NFC и lowercase.
4. Остаются непустые последовательности только из букв современного татарского
   кириллического алфавита длиной до 64 code points. Латиница, цифры, пунктуация,
   дефисы/апострофы и смешанные токены отбрасываются.
5. Частоты одинаковых нормализованных форм внутри и между корпусами суммируются.
6. Top-N сортируется по убыванию суммарной частоты; ties — по возрастанию Unicode-строки.
   Поэтому срез частотного tie детерминирован.
7. Token coverage считается относительно суммы **принятых** токенов. Доля принятых от
   всех распарсенных токенов показана отдельно, чтобы фильтрация не была скрыта.

`serialized_tsv_bytes` — точный UTF-8 размер `word<TAB>frequency<LF>`.
`gzip_tsv_bytes` — gzip level 9 этого представления с `mtime=0`; конкретный размер может
незначительно отличаться между версиями Python/zlib.
`packed_nul_u32_bytes` — оценка `UTF-8 word + NUL + uint32 frequency` без индекса;
`packed_nul_u32_plus_offsets_bytes` добавляет один uint32 offset на форму. Это ориентиры,
а не утверждённый формат D1.

## Результаты

После фильтрации объединены 1,087,462 принятые строки в 533,641 уникальную словоформу;
553,821 дубликат объединён. Принято 38,743,747 токенов из 48,505,629 распарсенных
(79.8747%). Отфильтровано 335,979 строк; 6 из них длиннее 64, остальные содержали символы
вне татарского алфавита. Форм с частотой 1 — 214,361 (40.17% словника).

Формы с хотя бы одной специфичной татарской буквой `ә ө ү җ ң һ`: 211,878
(39.70% уникальных форм) и 16,139,234 токена (41.66% принятой массы). Отсутствие такой
буквы само по себе не означает русский текст, поэтому показатель диагностический, не
language-ID.

| Срез | Coverage принятых токенов | Покрыто токенов | Частота на границе | UTF-8 TSV | gzip TSV | packed без индекса | packed + offsets |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 100,000 | 97.3318% | 37,709,998 | 10 | 2,173,527 B | 582,497 B | 2,241,960 B | 2,641,960 B |
| 150,000 | 98.2499% | 38,065,688 | 5 | 3,249,418 B | 835,421 B | 3,413,818 B | 4,013,818 B |
| 250,000 | 99.0891% | 38,390,826 | 2 | 5,444,516 B | 1,304,959 B | 5,808,916 B | 6,808,916 B |

Полный отфильтрованный набор: TSV 11,820,933 B; packed без индекса 12,752,615 B;
packed + offsets 14,887,179 B.

## Вывод D0

- Покрытие уже на 100k высокое. Дополнительные 50k дают только +0.9181 п.п.; переход
  150k → 250k — ещё +0.8392 п.п., причём граница 250k имеет частоту всего 2 и сильнее
  подвержена опечаткам/шуму.
- Предположение Фазы R о несжатых 1.2–1.5 МБ для 150k не подтверждено. Даже простой TSV
  занимает 3.25 МБ, а packed-оценка с offsets — 4.01 МБ до APK приложения. Несжатый mmap
  top-150k несовместим с общим APK-бюджетом 3 МБ.
- Сжатый top-150k TSV занимает около 0.84 МБ. Поэтому практический D1-вариант — хранить
  версионированный словарь deflate-сжатым в APK и атомарно распаковывать в `filesDir` для
  mmap, как запасной ход Фазы R. Это требует отдельного design/review гейта, проверки
  свободного места и invalidation при обновлении.
- Рекомендуемый старт D1 — **100k**, затем качество проверить на отдельном held-out наборе
  и ручном татарском query-set. Этот отчёт измеряет self-coverage исходных корпусов, а не
  точность подсказок на реальном вводе; высокая цифра не доказывает отсутствие русских,
  имён, OCR/орфографического шума.
- Разрешение corpus.tatar не блокирует Leipzig-only прототип, но остаётся потенциально
  главным улучшением качества. Подготовлен отдельный неотправленный черновик письма.
