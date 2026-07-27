# Политика конфиденциальности / Privacy Policy

**Версия / Version:** 1.1 — 2026-07-27

## English

Tatar Keyboard keeps what you type **on your device**.

- The app's manifest contains **no INTERNET permission** — the app itself sends nothing to any server. This is verifiable: run `aapt2 dump permissions` on the APK, or check the CI gate that validates both the source manifest and the built APK on every commit. The only permission the app uses is VIBRATE (haptic feedback on key press).
- Everything you type is processed **on your device only**. The keyboard works **fully offline**.
- There are **no analytics, no advertising SDKs, no Firebase**, and no third‑party trackers of any kind.
- **Nothing is backed up off your device.** The app sets `android:allowBackup="false"`, and its backup rules exclude every app‑data domain — files, settings, databases, external storage, regular and device‑protected alike — from both the cloud backup and the transfer to a new device. This is verified by CI on the manifest **and** on the built APK, the same way the INTERNET check is. One consequence, stated plainly: your keyboard settings are **not** restored on a new device or brought back from a backup — you set them again.
- The source code is open under the Apache License 2.0 — anyone can audit these claims.

### Recently used emoji

So the emoji panel can show them first, it remembers **up to 24 recently used emoji**.

- **Where.** They are stored in an internal app folder that is **decrypted only after you enter your device's lock code** (PIN, pattern or password).
- **Before the device is unlocked** for the first time after a restart, this list is **not read and not added to** — there simply is no “Recent” tab until you unlock.
- **Excluded from backup.** This list lives in an internal “no‑backup” folder that Android does **not** include in a cloud backup or in a transfer to a new device.
- **Not everywhere.** Nothing is remembered in **password fields or other private fields** — e‑mail, URL, and any field that asks the keyboard not to show suggestions or not to personalize (for example an incognito browser tab or a banking app).
- **How to erase it.** Open the keyboard settings and tap **“Clear recent emoji.”** To remove it together with everything else, delete the app's data in the system settings — that also removes the **2,542,036‑byte** unpacked dictionary, which the app rebuilds on next use.

## По-русски

Tatar Keyboard хранит то, что вы печатаете, **на вашем устройстве**.

- В манифесте приложения **нет разрешения INTERNET** — само приложение ничего никуда не отправляет. Это проверяемо: команда `aapt2 dump permissions` по APK, плюс CI‑гейт проверяет манифест и собранный APK на каждом коммите. Единственное используемое разрешение — VIBRATE (вибрация при нажатии клавиш).
- Всё, что вы печатаете, обрабатывается **только на вашем устройстве**. Клавиатура работает **полностью офлайн**.
- **Нет аналитики, нет рекламных SDK, нет Firebase** и никаких сторонних трекеров.
- **Ничего не уходит в резервную копию.** Приложение выставляет `android:allowBackup="false"`, а его правила бэкапа исключают все домены данных приложения — файлы, настройки, базы, внешнее хранилище, и обычные, и device‑protected — и из облачной резервной копии, и из переноса на новое устройство. Это проверяет CI по манифесту **и** по собранному APK — так же, как проверку INTERNET. Прямое следствие: настройки клавиатуры на новом устройстве и из резервной копии **не** восстанавливаются — вы задаёте их заново.
- Исходный код открыт под лицензией Apache‑2.0 — любой может убедиться в этих утверждениях сам.

### Недавно использованные эмодзи

Чтобы панель эмодзи показывала их первыми, она запоминает **до 24 недавно использованных эмодзи**.

- **Где.** Они хранятся во внутренней папке приложения, которая **расшифровывается только после ввода кода блокировки устройства** (PIN, графический ключ или пароль).
- **До разблокировки** устройства после перезагрузки этот список **не читается и не пополняется** — вкладки «Недавние» до разблокировки просто нет.
- **Исключено из бэкапа.** Этот список лежит во внутренней «no‑backup» папке, которую Android **не** включает ни в облачную резервную копию, ни в перенос на новое устройство.
- **Не везде.** Ничего не запоминается в **полях пароля и других приватных полях** — e‑mail, URL и любое поле, которое просит клавиатуру не показывать подсказки или не персонализироваться (например, вкладка браузера в режиме инкогнито или банковское приложение).
- **Как стереть.** Откройте настройки клавиатуры и нажмите **«Очистить недавние эмодзи»**. Чтобы удалить вместе со всем остальным — удалите данные приложения в системных настройках; это заодно удалит распакованный словарь на **2 542 036 байт**, который приложение соберёт заново при следующем использовании.

## Контакт / Contact

Вопросы по приватности — через Issues репозитория проекта. / Privacy questions — via the project repository's Issues.
