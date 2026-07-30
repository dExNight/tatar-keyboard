# Политика конфиденциальности / Privacy Policy

**Версия / Version:** 1.3 — 2026-07-31

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

### Personal dictionary

If you turn the **personal dictionary** on (it is **off** unless you turn it on), the keyboard saves words you type so it can suggest them later.

- **What is saved.** The word itself, spelled the way you typed it, plus two numbers: how often it has been used and when it was last used, as a counter — not a clock time. **Nothing else**: not the sentence around it, not the app you typed it in, not the field, not the date.
- **Where.** In an internal app folder that is **decrypted only after you enter your device's lock code** (PIN, pattern or password), separately for each keyboard language. **Before the first unlock** after a restart, nothing there is read or written.
- **It never leaves your device**, and it is excluded from cloud backup and from the transfer to a new device — there is no export, no import and no sync, in this version or any planned one.
- **Not everywhere.** Nothing is saved in **password fields or other private fields** — e-mail, URL, postal address, and any field that asks the keyboard not to show suggestions or not to personalize (an incognito tab, a banking app).
- **How to see and erase it.** Keyboard settings → **“Saved words”**: every saved word of every language, with **“Delete”** on each one and **“Erase all saved words.”** Turning the personal dictionary off does **not** erase what was already saved — erase it here. Deleting the app, or clearing its data in the system settings, destroys everything saved, and no backup brings it back.
- **A limit worth knowing, on Android 7 only.** The standard signal an app uses to say "do not learn from this field" (`IME_FLAG_NO_PERSONALIZED_LEARNING`) exists from Android 8 onwards. On Android 7.0 and 7.1 no app sets it, so on those two versions the keyboard cannot tell such a field apart, and only the other gates above (password and private field types, postal addresses, the off-by-default switch itself) protect it. Every other guarantee on this page holds on all supported versions.
- **The screen is not behind a separate password.** Anyone holding your unlocked phone can read the list of saved words. `FLAG_SECURE` keeps it out of screenshots and out of the recent-apps thumbnail, but it cannot keep out a person standing next to you. This is a deliberate trade-off, stated rather than left unsaid.

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

### Личный словарь

Если вы включите **личный словарь** (по умолчанию он **выключен**), клавиатура сохраняет набранные вами слова, чтобы предлагать их позже.

- **Что сохраняется.** Само слово в том написании, в котором вы его набрали, и два числа: сколько раз оно использовалось и когда использовалось в последний раз — счётчиком, а не временем по часам. **Больше ничего**: ни окружающего предложения, ни приложения, в котором вы печатали, ни поля, ни даты.
- **Где.** Во внутренней папке приложения, которая **расшифровывается только после ввода кода блокировки устройства** (PIN, графический ключ или пароль), отдельно для каждого языка клавиатуры. **До первой разблокировки** после перезагрузки там ничего не читается и не пишется.
- **Это никогда не покидает ваше устройство** и исключено из облачной резервной копии и переноса на новое устройство — ни экспорта, ни импорта, ни синхронизации нет ни в этой версии, ни в планах.
- **Не везде.** Ничего не сохраняется в **полях пароля и других приватных полях** — e-mail, URL, почтовый адрес и любое поле, которое просит клавиатуру не показывать подсказки или не персонализироваться (вкладка инкогнито, банковское приложение).
- **Как посмотреть и стереть.** Настройки клавиатуры → **«Сохранённые слова»**: все сохранённые слова всех языков, у каждого — **«Удалить»**, и отдельно **«Стереть все сохранённые слова»**. Выключение личного словаря **не** удаляет накопленное — стирать нужно здесь. Удаление приложения или «стереть данные» в системных настройках уничтожает всё накопленное безвозвратно, и восстановление из резервной копии его не вернёт.
- **Ограничение, о котором стоит знать, — только для Android 7.** Стандартный сигнал, которым приложение просит клавиатуру не запоминать набранное в поле (`IME_FLAG_NO_PERSONALIZED_LEARNING`), существует начиная с Android 8. На Android 7.0 и 7.1 его не выставляет ни одно приложение, поэтому на этих двух версиях клавиатура не может отличить такое поле, и его защищают только остальные перечисленные выше условия (поля пароля и другие приватные типы полей, почтовые адреса и сам выключенный по умолчанию тумблер). Все прочие гарантии этой страницы действуют на всех поддерживаемых версиях.
- **Экран не защищён отдельным паролем.** Любой, у кого в руках ваш разблокированный телефон, увидит список сохранённых слов. `FLAG_SECURE` закрывает его от скриншота и от миниатюры «недавних приложений», но не от человека рядом. Это осознанный компромисс, и он назван, а не умолчан.

## Контакт / Contact

Вопросы по приватности — через Issues репозитория проекта. / Privacy questions — via the project repository's Issues.
