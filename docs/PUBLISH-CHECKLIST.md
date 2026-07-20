# PUBLISH-CHECKLIST — публикация v1.0.1

Пошаговая инструкция **для ручного выполнения** создателем проекта. Ничего из этого файла не исполняется автоматически — каждый шаг делаешь сам, руками, в указанном порядке.

## Шаг 1. ✅ ВЫПОЛНЕНО — Бэкап ключа подписи — ДО всего остального

**Потеря ключа = невозможность выпускать обновления. Навсегда.** Восстановить нельзя.

Скопируй оба файла минимум в **2 места вне репозитория**:

- `release.jks` (корень репо, gitignored)
- `keystore.properties` (корень репо, gitignored)

Рекомендуемая пара мест: пароль-менеджер (вложением) + офлайн-носитель (флешка/внешний диск). Пароли из `keystore.properties` продублируй в пароль-менеджер отдельными записями.

Сделай этот шаг, **даже если публикацию откладываешь**.

## Шаг 2. ✅ ВЫПОЛНЕНО — Создать GitHub-репозиторий

Репозиторий создан: https://github.com/dExNight/tatar-keyboard (пока **приватный** — переключить на Public перед релизом: Settings → General → Danger Zone → Change visibility).

## Шаг 3. ✅ ВЫПОЛНЕНО — Подставить реальный owner в URL-плейсхолдеры

В `app/src/main/res/values/strings-appname.xml` URL-плейсхолдеры заменены на реальные URL:

- `privacy_policy_url` → `https://github.com/dExNight/tatar-keyboard/blob/main/PRIVACY.md`
- `license_url` → `https://github.com/dExNight/tatar-keyboard/blob/main/LICENSE`

## Шаг 4. ✅ ВЫПОЛНЕНО — Push

`main` запушен в https://github.com/dExNight/tatar-keyboard.

## Шаг 5. Зелёный CI + негативный тест гейта

1. Открой вкладку **Actions** репозитория, дождись зелёного прогона workflow CI.
   - Если шаг size-gate упал с «No such file»: проверь имя unsigned-APK в
     `app/build/outputs/apk/release/` из лога шага сборки и поправь путь в
     `.github/workflows/ci.yml` (AGP-конвенция — `app-release-unsigned.apk`).
2. **Негативный тест PERF-04** (доказательство, что гейт no-INTERNET работает, — отложенный blocker фазы 1):
   ```
   git checkout -b ci-negative-test
   # добавь в app/src/main/AndroidManifest.xml строку:
   #   <uses-permission android:name="android.permission.INTERNET" />
   git commit -am "test: CI negative check (must fail)"
   git push origin ci-negative-test
   ```
   Дождись **красного** CI на этой ветке → гейт работает. Затем удали ветку:
   ```
   git checkout main
   git push origin --delete ci-negative-test
   git branch -D ci-negative-test
   ```

## Шаг 6. Финальная локальная сборка и проверка

```
./gradlew clean assembleRelease
apksigner verify app/build/outputs/apk/release/app-release.apk
stat -f%z app/build/outputs/apk/release/app-release.apk   # ≤ 3145728
```

Все три: сборка зелёная, verify без ошибок, размер ≤ 3 МБ.

## Шаг 7. Тег v1.0.1

```
git tag v1.0.1
git push origin v1.0.1
```

## Шаг 8. GitHub Release

1. Открой `https://github.com/dExNight/tatar-keyboard/releases/new`
2. Тег: **v1.0.1**. Заголовок: **Tatar Keyboard 1.0.1**.
3. Тело: скопируй раздел `[1.0.1]` из [CHANGELOG.md](../CHANGELOG.md).
4. Приложи подписанный APK: `app/build/outputs/apk/release/app-release.apk`, переименовав в **`tatar-keyboard-1.0.1.apk`**.
5. Publish release.

## Шаг 9. Заявка IzzyOnDroid

1. Открой `https://gitlab.com/IzzyOnDroid/repo/-/issues/new`
2. Шаблон **«App inclusion request»**, укажи URL репозитория `https://github.com/dExNight/tatar-keyboard`.
3. Требования Izzy к этому моменту уже выполнены: подписанный APK в Releases, лицензия Apache-2.0 в репо, privacy policy (PRIVACY.md).

## Шаг 10. После включения в IzzyOnDroid

Добавь бейдж IzzyOnDroid в README отдельным коммитом (до включения бейдж мёртвый — поэтому его нет в README сейчас).

---

После шагов 8–9: проставь чек-боксы REL-02/REL-03 в `.planning/REQUIREMENTS.md`, переведи их Traceability в Complete — v1.0 milestone закрыт.
