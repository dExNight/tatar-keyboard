# Политика конфиденциальности / Privacy Policy

**Версия / Version:** 1.0 — 2026-07-19

## English

**Tatar Keyboard does not collect, store, or transmit any data. Period.**

- The app's manifest contains **no INTERNET permission** — the keyboard is physically unable to send anything anywhere. This is verifiable: run `aapt2 dump permissions` on the APK, or check the CI gate that validates both the source manifest and the built APK on every commit.
- Everything you type is processed **on your device only** and never leaves it.
- The keyboard works **fully offline**.
- There are **no analytics, no advertising SDKs, no Firebase**, no third-party trackers of any kind.
- The only permission the app uses is VIBRATE (haptic feedback on key press).
- The source code is open under the Apache License 2.0 — anyone can audit these claims.

## По-русски

**Tatar Keyboard не собирает, не хранит и не передаёт никакие данные. Точка.**

- В манифесте приложения **нет разрешения INTERNET** — клавиатура физически не может ничего никуда отправить. Это проверяемо: команда `aapt2 dump permissions` по APK, плюс CI-гейт проверяет манифест и собранный APK на каждом коммите.
- Всё, что вы печатаете, обрабатывается **только на вашем устройстве** и никуда не отправляется.
- Клавиатура работает **полностью офлайн**.
- **Нет аналитики, нет рекламных SDK, нет Firebase** и никаких сторонних трекеров.
- Единственное используемое разрешение — VIBRATE (вибрация при нажатии клавиш).
- Исходный код открыт под лицензией Apache-2.0 — любой может убедиться в этих утверждениях сам.

## Контакт / Contact

Вопросы по приватности — через Issues репозитория проекта. / Privacy questions — via the project repository's Issues.
