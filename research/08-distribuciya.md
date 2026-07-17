# Дистрибуция и приватность татарской клавиатуры (IME) — июль 2026

Тема: требования Google Play к IME-приложениям, privacy policy и Data Safety, «клавиатура без INTERNET» как фича, альтернативные каналы (RuStore, F-Droid), подпись, targetSdk, стратегия обновлений.

---

## 1. Специфика IME: что видит клавиатура и как на это смотрит Google Play

- IME получает **весь вводимый текст во всех приложениях**: пароли, номера карт, сообщения. Android сам предупреждает пользователя при включении сторонней клавиатуры системным диалогом: «может собирать весь текст, который вы вводите, включая личные данные, пароли и номера кредитных карт». Это предупреждение показывается всегда, убрать его нельзя — учитывай его в онбординге (объясни пользователю, почему клавиатуре можно доверять).
- Отдельной «политики для клавиатур» у Google Play нет — IME регулируется общей **User Data policy** (Play Console Help, answer/10144311). Ключевые следствия для клавиатуры:
  - Сбор и передача вводимого текста (keystroke logging) без явного раскрытия и согласия = нарушение политики персональных и конфиденциальных данных. Скрытая передача набранного текста — прямой путь к бану аккаунта.
  - Любая передача пользовательских данных с устройства должна быть: (а) раскрыта в privacy policy, (б) задекларирована в Data Safety, (в) для чувствительных данных — с prominent disclosure и runtime-согласием.
  - Данные можно использовать только для целей, ради которых пользователь их предоставил (ограничение purpose limitation).
- Практический вывод: для простой татарской клавиатуры **не собирай и не передавай ничего** — тогда вся политика сводится к «мы ничего не собираем», что легко декларировать и легко проверить.

## 2. Privacy policy — обязательна

- С июля 2024 privacy policy обязательна для **всех** приложений в Google Play (независимо от того, собирают ли они данные) — без неё нельзя заполнить Data Safety и пройти ревью.
- Требования: постоянный публичный URL (не PDF в загрузках, не редактируемый Google Doc; GitHub Pages подходит), ссылка указывается в Play Console (App content → Privacy policy) **и** должна быть доступна из самого приложения (обычно экран «О приложении»/настройки).
- Для клавиатуры без сбора данных текст политики — 10–15 строк: кто разработчик, что приложение обрабатывает текст только на устройстве, не имеет доступа в интернет, не передаёт и не хранит вводимый текст, контакт для связи. Сделай версии на русском/татарском/английском.
- RuStore при модерации тоже требует прозрачную политику конфиденциальности и достоверные контакты — один и тот же URL закрывает оба стора.

## 3. Data Safety форма (Play Console → App content → Data safety)

- Обязательна для всех приложений; заполняется до публикации, отображается на карточке в сторе.
- Ключевое определение: **«collect» = передача данных с устройства** (off-device transmission), включая передачу библиотеками/SDK. Обработка текста только на устройстве (словарь, автодополнение, пользовательский словарь в локальном файле) — это **не** «сбор» по определению формы.
- Для клавиатуры без INTERNET-permission и без сторонних SDK честный ответ: **«No data collected, no data shared»**. Это сильный маркетинговый сигнал — карточка в Play показывает «Данные не собираются».
- Что моментально ломает эту декларацию (не добавляй без необходимости):
  - Firebase Analytics / Crashlytics — это «сбор» (device identifiers, crash logs) и требует декларации;
  - любые рекламные SDK;
  - «облачные» подсказки/спеллчек.
  - Даже эфемерная обработка на сервере подлежит декларации (с особым режимом раскрытия).
- Ответственность за точность формы на разработчике; расхождение поведения приложения и декларации — enforcement (reject/removal). Google периодически сканирует APK на сетевые вызовы и SDK.
- Если когда-нибудь добавишь сетевые фичи, для клавиатуры релевантные категории формы: «Personal info», «Messages or other in-app text», App activity; вводимый текст, уходящий на сервер, — почти всегда «required for core functionality» + шифрование in transit + механизм удаления.

## 4. «Без разрешения INTERNET» как фича приватности

Это устоявшийся паттерн в нише privacy-клавиатур:

| Клавиатура | INTERNET | Где распространяется | Как подаётся |
|---|---|---|---|
| Simple Keyboard (rkkr) | нет (только VIBRATE) | Google Play + F-Droid, <1 МБ | «минимальные permissions» |
| HeliBoard (форк OpenBoard/AOSP) | нет | F-Droid, IzzyOnDroid, GitHub (в Play нет, issue #619 открыт) | «100% offline», словари подключаются локальными файлами |
| FlorisBoard | нет | F-Droid | «privacy by design», incognito-режим |
| OpenBoard | нет | F-Droid, Play (заброшен, поэтому появился HeliBoard) | AOSP без Google-зависимостей |

- Механика: просто **не объявляй** `<uses-permission android:name="android.permission.INTERNET"/>` в манифесте. Тогда любой socket-вызов процесса падает с `SecurityException` — это проверяемая ОС гарантия, а не обещание.
- Подводный камень: manifest merger. Транзитивные зависимости могут сами добавить INTERNET. Контроль:

```xml
<!-- AndroidManifest.xml: жёстко запретить, даже если библиотека попросит -->
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
```

и проверка в CI: `aapt2 dump permissions app-release.apk` (или `apkanalyzer manifest permissions`) — assert, что INTERNET отсутствует. Смотри merged manifest в Android Studio (Manifest → Merged Manifest).
- Как подавать: в описании стора и онбординге — «клавиатура не имеет доступа в интернет на уровне системы; технически не может отправить то, что вы печатаете». Это же снимает тревогу от системного предупреждения при включении IME. Privacy Guides / privacytools.io рекомендуют клавиатуры именно по этому критерию — отсутствие INTERNET даёт шанс на попадание в такие подборки.
- Цена: без сети нет облачных подсказок, синхронизации словаря, автообновления словарей изнутри приложения. Для татарской клавиатуры словари шипь в APK/через обновление приложения — это нормальная практика (HeliBoard так и делает с загружаемыми пользователем локальными словарями).

## 5. Google Play: targetSdk, аккаунт, тестирование

### targetSdk (июль 2026)
- Сейчас: новые приложения и обновления — **targetSdk ≥ 35 (Android 15)**.
- Дедлайн **31 августа 2026**: существующие приложения обязаны перейти на targetSdk ≥ 35; по годовому циклу планка для новых приложений/обновлений поднимается до **36 (Android 16)** — перед релизом сверься с актуальной таблицей (support.google.com/googleplay/android-developer/answer/11926878). Продление возможно до 1 ноября 2026 через форму в Play Console.
- Практика: сразу ставь `targetSdk = 36`, `compileSdk = 36`. Для IME значимых breaking-изменений в 35/36 немного (edge-to-edge по умолчанию в 35 — проверь высоту клавиатуры и insets; predictive back). `minSdk` для охвата бюджетных устройств — 24–26 (Android 7–8): покрывает практически весь живой парк дешёвых устройств.

### Аккаунт и допуск в production
- Регистрация: личный аккаунт $25 (разово). **Новые личные аккаунты (после 13.11.2023) обязаны провести closed testing: минимум 12 тестеров, непрерывно opted-in 14 дней**, затем подать заявку на production access (ревью до ~7 дней). Организационные аккаунты освобождены. Планируй +3–4 недели на этот этап; тестеров ищи в татарском сообществе — заодно получишь реальный фидбек по раскладке.
- Верификация личности (документы, адрес, для личных аккаунтов — видимость имени в сторе) обязательна.
- Формат загрузки: только **AAB** для новых приложений.

## 6. Подпись приложения

- **Google Play**: для новых приложений обязателен **Play App Signing** — ты загружаешь AAB, подписанный *upload key*, Google хранит *app signing key* и подписывает APK сам. Рекомендация: пусть Google сгенерирует app signing key, а свой keystore используй как upload key (его при утере можно сбросить через поддержку).
- **RuStore / F-Droid / GitHub releases**: подписываешь APK своим ключом через `apksigner` (V2+V3 scheme). 
- Важный конфликт: APK из Play (подпись Google) и APK, подписанный твоим ключом, — **разные подписи**. Пользователь не сможет обновить одно поверх другого (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), придётся переустанавливать с потерей данных. Варианты:
  1. Смириться (обычная практика: у HeliBoard подписи F-Droid и GitHub тоже различались до перехода на reproducible).
  2. Сделать **reproducible build** для F-Droid (см. §8) — тогда F-Droid раздаёт APK с твоей подписью, и сборки GitHub/RuStore/F-Droid совместимы между собой (Play всё равно отдельно из-за Play App Signing).
- Keystore: сгенерируй один раз (`keytool -genkeypair -keyalg RSA -keysize 4096 -validity 10000`), храни оффлайн + в менеджере секретов; потеря ключа = потеря канала обновлений в RuStore/F-Droid.

## 7. RuStore (приоритетный канал для аудитории в Татарстане)

- Для аудитории в РФ важен: предустановлен на большинстве новых устройств, продаваемых в России; Google Play на новых аккаунтах из РФ платить/регистрироваться сложнее.
- Регистрация разработчика (console.rustore.ru): бесплатна.
  - Физлицо: аккаунт VK ID/Госуслуги, верификация личности (паспорт + селфи). Есть регистрация для нерезидентов.
  - Юрлицо/ИП: подписание формы **УКЭП** (усиленная квалифицированная электронная подпись, выдаёт ФНС; проверка через КриптоПро-плагин). Для сольного разработчика проще публиковаться как физлицо.
  - Доступ в консоль — обычно в течение суток.
- Технические требования:
  - принимаются **APK и AAB**, обязательно подписанные; для AAB ключ подписи загружается в консоль при загрузке сборки;
  - **targetSdk ≥ 28** (сильно мягче Play — но собирай один артефакт под правила Play);
  - при нативном коде обязательны 64-битные библиотеки (arm64-v8a);
  - политика конфиденциальности и контакты — обязательны.
- Модерация: ручная проверка всего пользовательского пути, обычно **до 24 часов** (обновления быстрее). Режимы публикации: вручную / автоматически после модерации / по расписанию / поэтапная раскатка (staged rollout на процент пользователей).
- Бесплатные приложения — без комиссий и платежей вообще (RuStore Pay нужен только для монетизации).
- С 1 марта 2026 действуют усиленные требования к русскому языку в карточке и пользовательских материалах — карточку и скриншоты делай на русском (татарский — как дополнение; официального требования татарской локали нет, но для твоей аудитории она и так нужна).
- IME-специфики в правилах RuStore нет (клавиатуры публикуются как обычные приложения); подтверждённых публичных кейсов отказа IME по категории не найдено — практический риск модерации низкий, если приложение работает и политика на месте.

## 8. F-Droid (канал доверия для privacy-аудитории)

Имеет смысл, только если проект **open-source** (для клавиатуры это и есть главный аргумент доверия — рекомендую).

- Требования (Inclusion Policy):
  - весь код и зависимости — FLOSS (GPL/Apache/MIT и т.п., лицензии по стандартам FSF/OSI/Debian); **никаких** Google Play Services, Firebase, Crashlytics, проприетарных SDK — для «клавиатуры без интернета» это выполняется автоматически;
  - публичный репозиторий с исходниками и файлом лицензии;
  - собственный уникальный `applicationId` (если форкаешь — обязан отличаться от оригинала);
  - зависимости — из доверенных Maven-репозиториев (Maven Central, Google Maven, JitPack и др.), но тоже свободные;
  - никакой загрузки исполняемого кода в рантайме (у HeliBoard именно опциональная внешняя glide-библиотека — главный блокер их Play-релиза и повод для Anti-Feature флага).
- Процесс включения — два пути:
  1. Тикет в **Submission Queue** (gitlab.com/fdroid/rfp) — медленно;
  2. Рекомендуемый: самому написать metadata-файл и открыть **merge request в fdroiddata** (gitlab.com/fdroid/fdroiddata) — быстрее, CI форка прогоняет сборку автоматически. После merge сборочный сервер собирает из исходников; публикация через ~24–48 ч.
- Автообновления: в metadata задаётся `UpdateCheckMode: Tags` — новый git-тег с поднятым `versionCode` автоматически собирается и публикуется, отдельных действий не нужно.
- **Reproducible builds — не обязательны**, но best practice: если сборка воспроизводима, F-Droid раздаёт APK с **твоей** подписью (директивы `Binaries:` + `AllowedAPKSigningKeys` в metadata; подписывай `apksigner`, не jarsigner). Решение о reproducible надо принять **до первой публикации** — сменить ключ подписи потом нельзя. Требования к воспроизводимости: фиксированные версии toolchain, отключить зависящие от времени артефакты (у AGP по умолчанию уже детерминированная сборка; проверь `versionCode`/`versionName` без даты, без BuildConfig-таймстампов).
- Быстрый ранний канал без бюрократии: **IzzyOnDroid** (repo подключается в F-Droid-клиент) — берёт готовые APK прямо из GitHub Releases, включение занимает дни; хорош на период, пока MR в fdroiddata на ревью.

## 9. Стратегия обновлений

- Единая версия кода, три канала:
  - **Google Play**: AAB, staged rollout (начни с 10–20%), Play Console vitals для крэшей (без Crashlytics: ANR/crash-статистика Play Console работает без SDK в приложении и не ломает «no data collected»).
  - **RuStore**: тот же код, подпись своим ключом, поэтапная публикация; обновления через консоль или RuStore API (есть публичный API загрузки сборок для CI).
  - **F-Droid**: git-тег → автосборка. Учитывай лаг конвейера F-Droid (обычно 1–4 дня после тега).
- `versionCode` монотонно растёт и одинаков во всех каналах — иначе пользователи при миграции между сторами получат конфликт версий.
- Словари/раскладки шить в ресурсы приложения; их обновление = обновление приложения. Никаких «скачиваний словаря с сервера» — это потребовало бы INTERNET и убило бы главную фичу.
- Самообновление (in-app updater c GitHub) не делай: в Play это запрещено (загрузка исполняемого кода), в F-Droid — Anti-Feature, и требует INTERNET.
- Частота: для IME редкие стабильные релизы лучше частых — пользователь не должен думать о клавиатуре. Держи closed-testing/open-testing трек в Play для татарского сообщества тестеров.

## 10. Рекомендованный план действий

1. Open-source репозиторий (GPL-3.0 или Apache-2.0), уникальный `applicationId` (например `org.<...>.tatarkeyboard`), без каких-либо сетевых/аналитических SDK, `tools:node="remove"` на INTERNET + CI-проверка permissions.
2. Privacy policy «данные не покидают устройство» на GitHub Pages (ru/tt/en), ссылка в приложении.
3. Keystore: один ключ для RuStore/F-Droid/GitHub; в Play — Play App Signing с этим ключом как upload key.
4. `targetSdk 36`, `minSdk 24–26`, AAB для Play, APK для остальных.
5. Публикация в порядке: GitHub Releases + IzzyOnDroid (сразу) → RuStore (модерация ~сутки) → Play (личный аккаунт: 12 тестеров × 14 дней, закладывай месяц) → MR в fdroiddata, по возможности с reproducible build.
6. Data Safety: «No data collected / No data shared»; в описании стора явно: «нет доступа в интернет — не может передавать введённый текст».

## Неясности / что проверить перед релизом

- Точная планка targetSdk для новых приложений после 31.08.2026 (35 или уже 36) — смотреть официальную таблицу в Play Console на момент сабмита; сторонние источники называют 36, официальная страница на момент ресерча фиксирует 35 для новых приложений.
- Актуальные условия регистрации физлица-нерезидента/резидента в RuStore и вывод на публикацию без УКЭП — проверить в console.rustore.ru при регистрации (процедуры для физлиц менялись).
- Требования закона о русском языке (с 01.03.2026) к интерфейсу приложения в RuStore — уточнить объём обязательной русской локализации UI (карточка стора точно обязана быть на русском).
- Практика модерации именно IME в RuStore — публичных кейсов мало; риск оценён по общим правилам.

---

## Источники

- Google Play — Target API level requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Android Developers — Meet Google Play's target API level requirement: https://developer.android.com/google/play/requirements/target-sdk
- Google Play — Data safety section: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play — User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play — App testing requirements for new personal developer accounts (12 testers / 14 days): https://support.google.com/googleplay/android-developer/answer/14151465
- Android Developers — Create an input method (IME): https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- RuStore — Требования к приложениям: https://www.rustore.ru/help/en/developers/publishing-and-verifying-apps/requirement-apps
- RuStore — Публикация и проверка приложений: https://www.rustore.ru/help/en/developers/publishing-and-verifying-apps
- RuStore — Управление ключами подписи APK/AAB: https://www.rustore.ru/help/en/developers/publishing-and-verifying-apps/app-publication/apk-signature
- RuStore — Поэтапная публикация: https://www.rustore.ru/help/en/developers/publishing-and-verifying-apps/app-publication/setting-up-publication/step-by-step-publication
- Хабр (VK) — Как опубликовать приложение в RuStore: https://habr.com/ru/companies/vk/articles/718062/
- F-Droid — Inclusion Policy: https://f-droid.org/en/docs/Inclusion_Policy/
- F-Droid — Inclusion How-To: https://f-droid.org/en/docs/Inclusion_How-To/
- F-Droid — Reproducible Builds: https://f-droid.org/en/docs/Reproducible_Builds/
- F-Droid — Submitting Quick Start Guide: https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- HeliBoard (GitHub): https://github.com/Helium314/HeliBoard — Play Store issue: https://github.com/Helium314/HeliBoard/issues/619
- HeliBoard в F-Droid: https://f-droid.org/packages/helium314.keyboard/
- MakeUseOf — HeliBoard privacy review: https://www.makeuseof.com/heliboard-is-best-android-keyboard-for-privacy/
- Android Police — Why I switched to HeliBoard: https://www.androidpolice.com/spent-years-switching-android-keyboards-this-one-changed-everything/
- PrivacyTools — Best Private Android Keyboard 2026: https://privacytools.io/android-keyboards
- PTKD Journal — Android custom keyboard and input method security: https://ptkd.com/journal/android-custom-keyboard-input-method-security
- Testers Community — Google Play 12 testers policy: https://www.testerscommunity.com/blog/google-play-12-testers-policy
