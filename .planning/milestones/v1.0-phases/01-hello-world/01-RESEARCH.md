# Phase 1: Форк и hello-world - Research

**Researched:** 2026-07-18
**Domain:** Android Gradle toolchain / форк rkkr/simple-keyboard / CI / signing
**Confidence:** HIGH — реальные исходники rkkr/simple-keyboard склонированы и прочитаны (HEAD `b40c70d`, 2026-07-12, v6.5 / versionCode 146); версии тулчейна проверены по официальным release notes AGP.

## Summary

Главная новость ресерча: **база в отличном состоянии, и фаза проще, чем закладывалось в роадмапе.** Upstream rkkr/simple-keyboard на HEAD уже собирается на AGP 9.2.1 + Gradle 9.6 (wrapper) + compileSdk/targetSdk 37, minSdk 24, **ноль внешних зависимостей** (блок `dependencies {}` пуст, AndroidX не используется). Опасение из CONTEXT «сток-база на старых версиях, не превращать фазу в апгрейд-марафон» не подтвердилось — апгрейдить нечего, тулчейн новее наших ориентиров (37 vs «36» в CLAUDE.md). Никакого CI у upstream нет (`.github/` отсутствует) — наш workflow пишется с чистого листа, без конфликтов.

Kotlin-interop сводится к **одной строке**: в `gradle.properties` upstream явно выключил встроенный Kotlin (`android.builtInKotlin=false`); с AGP 9.x поддержка Kotlin встроена в сам плагин и включена по умолчанию — достаточно убрать/инвертировать этот флаг, никакой `org.jetbrains.kotlin.android` подключать не нужно (наоборот, его подключение при включённом built-in Kotlin даёт конфликт расширений). applicationId задаётся в одном месте (`app/build.gradle`), причём **namespace менять не нужно** — Java-пакеты `rkr.simplekeyboard.inputmethod` остаются как есть, что сохраняет минимальный diff против upstream и возможность мержить его будущие релизы.

CI-проверка INTERNET имеет два уровня: быстрый grep исходного манифеста недостаточен (merged manifest теоретически может получить permission от зависимостей/тулинга) — канонический способ: собрать APK и проверить фактические permissions через `aapt2 dump permissions` из build-tools, предустановленных на GitHub-раннерах. Keystore — стандартный паттерн `keystore.properties` (в .gitignore) + условный signingConfig, чтобы CI без секретов продолжал собирать unsigned release.

**Primary recommendation:** форкать через GitHub fork (полная история — это и юридическая гигиена Apache-2.0, и канал будущих мержей upstream), менять только `applicationId` + видимое имя (1 строка в `values/strings-appname.xml`), включить built-in Kotlin флагом, CI = сборка debug + `aapt2 dump permissions` с fail на INTERNET.

## User Constraints (from CONTEXT.md)

### Locked Decisions

Фаза объявлена «Claude's Discretion» целиком, но со связывающими нотами планировщику (копия из CONTEXT.md):

- **applicationId** — рабочий, провизорный (финальный — решение до публикации, фаза 11; смена до публикации — одна строка в build.gradle). Требование фазы: уникальность против оригинала `rkr.simplekeyboard.inputmethod`, чтобы ставились рядом. Разумный вариант в духе open-source на GitHub: `io.github.<owner>.tatarkeyboard` или нейтральный `org.tatarkeyboard.ime` — выбрать при планировании и зафиксировать в SUMMARY.
- **CI** — репозиторий git/GitHub → GitHub Actions; job на каждый коммит: сборка + проверка манифеста на отсутствие `android.permission.INTERNET` (fail при появлении).
- **Keystore** — release-keystore создаётся локально, НЕ коммитится (в .gitignore); signing config через локальные свойства. `assembleRelease` даёт подписанный APK.
- **Kotlin interop** — Kotlin-плагин в сборку, один тестовый Kotlin-файл, вызываемый из Java-кода базы (доказательство interop), без массовой конвертации.
- **Toolchain** — ориентир из ресерча: AGP 9.x, Gradle 8.14+/9.x, Kotlin 2.3+; minSdk как в базе (24–26, уточнение перед релизом), targetSdk/compileSdk 36 — если сток-база собирается на более старых версиях, апгрейд тулчейна делать минимально необходимым, не превращать фазу в апгрейд-марафон.

Жёсткие рамки проекта (PROJECT.md/CLAUDE.md): без разрешения INTERNET (PERF-04, CI с этой фазы), Apache-2.0 сохраняется, никаких сторонних зависимостей/аналитики.

### Claude's Discretion

Все имплементационные решения фазы — на усмотрение планировщика (см. ноты выше).

### Deferred Ideas (OUT OF SCOPE)

Финальное имя приложения и applicationId — решение фазы 11 (до публикации). Любые изменения раскладок, UI, поведения — сток-функциональность базы остаётся как есть.

## Фактическая структура rkkr/simple-keyboard (проверено по клону HEAD)

**Снимок:** commit `b40c70d9`, 2026-07-12, versionName 6.5, versionCode 146 (новее, чем 6.4 из research/06 — репозиторий живой). Клон для изучения лежит в `/tmp/sk-research`.

### Gradle-конфигурация

| Параметр | Значение | Где |
|---|---|---|
| Модули | один — `:app` | `settings.gradle` |
| AGP | **9.2.1** | `build.gradle` (корень), старый стиль `buildscript { classpath }`, не plugins DSL |
| Gradle wrapper | **9.6.0** | `gradle/wrapper/gradle-wrapper.properties` |
| Toolchain resolver | foojay-resolver-convention 1.0.0 | `settings.gradle` |
| applicationId | `rkr.simplekeyboard.inputmethod` | `app/build.gradle:5` |
| namespace | `rkr.simplekeyboard.inputmethod` | `app/build.gradle:17` |
| compileSdk / targetSdk | **37 / 37** | `app/build.gradle` |
| minSdk | **24** | `app/build.gradle` |
| Зависимости | **ноль** (`dependencies {}` пуст) | `app/build.gradle` |
| minify (release) | `minifyEnabled true` + proguard-android-optimize | `app/build.gradle` |
| Kotlin | **выключен явно**: `android.builtInKotlin=false` | `gradle.properties` |
| AndroidX | не используется: `android.useAndroidX=false` | `gradle.properties` |
| Версионный каталог / KTS | нет — Groovy DSL, без catalogs | — |
| Product flavors | нет | — |
| CI | **отсутствует** (`.github/` нет) | — |
| Тесты | **отсутствуют** (только `src/main`, нет `test/`/`androidTest/`) | — |

`gradle.properties` содержит пакет флагов `android.*` (newDsl=false, nonFinalResIds=false, uniquePackageNames=false и др.) — это осознанная фиксация legacy-поведения при миграции rkkr на AGP 9. **Не «чистить» эти флаги** — каждый снятый флаг меняет поведение сборки.

### Исходники

96 Java-файлов, пакет `rkr.simplekeyboard.inputmethod`:

```
compat/            3 файла  (EditorInfoCompatUtils, PreferenceManagerCompat, ...)
event/             2 файла  (Event, InputTransaction)
keyboard/         16 файлов (Keyboard, KeyboardView, MainKeyboardView, KeyboardSwitcher,
                             KeyboardLayoutSet, Key, KeyDetector, MoreKeysKeyboard, ...)
keyboard/internal ~20 файлов
latin/             8 файлов (LatinIME — сам сервис, 942 строки; RichInputConnection,
                             RichInputMethodManager, Subtype, InputView, ...)
latin/common       5 файлов (Constants, StringUtils, LocaleUtils, ...)
latin/define       1 файл   (DebugFlags)
latin/inputlogic   1 файл   (InputLogic)
latin/settings    17 файлов (SettingsActivity, фрагменты настроек)
latin/utils       15 файлов (SubtypeLocaleUtils, ResourceUtils, CapsModeUtils, ...)
```

### Манифест и IME-регистрация

- Единственное разрешение: `android.permission.VIBRATE`. INTERNET нет — CI-проверка стартует с «зелёного» состояния.
- Сервис `.latin.LatinIME` уже объявлен с `android:directBootAware="true"` (задел под COMPAT-05 есть из коробки), `exported="false"`, `BIND_INPUT_METHOD`, meta-data → `@xml/method`.
- `res/xml/method.xml` содержит **один generic-subtype без локали**; `android:settingsActivity` указан полным именем класса `rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity` — при смене applicationId **остаётся валидным** (это имя класса, не applicationId). Языки Simple Keyboard управляет внутри приложения (Subtype.java, RichInputMethodManager, LanguagesSettingsFragment), а не через статические subtypes — важно знать для фазы 3 (SWITCH-01 потребует решения: статические subtypes vs механизм базы).
- Видимое имя «Simple Keyboard» задано в **одном месте**: `app/src/main/res/values/strings-appname.xml`, строка `english_ime_name`, `translatable="false"` — переименование = 1 строка.
- `onEvaluateFullscreenMode()` уже переопределён (`LatinIME.java:576`, управляется настройкой).

### Раскладки (контекст для фазы 2, здесь не трогаем)

360 XML-файлов в `res/xml/`: тройки `kbd_<layout>.xml` + `keyboard_layout_set_<layout>.xml` + `rowkeys_<layout>N.xml`/`rows_<layout>.xml`. Русская ЙЦУКЕН — `*_east_slavic*`. Формат подтверждает решение «раскладки данными».

### Прочее полезное

- `.gitignore` уже игнорирует `*.jks` и `local.properties`; **не игнорирует** `keystore.properties` — добавить.
- `proguard-rules.pro` содержит два keep-правила (`R`, `SettingsFragment`) с захардкоженным пакетом — при неизменном namespace трогать не нужно.
- `metadata/` — готовая fastlane-структура (short/full description, changelogs, images) — пригодится для IzzyOnDroid в фазе 11.
- `PRIVACY.md` в корне — референс для REL-02.

## Standard Stack

### Core (унаследовано от базы, менять не нужно)

| Компонент | Версия | Назначение | Почему |
|---|---|---|---|
| AGP | 9.2.1 (как в базе) | сборка | уже в базе; требует Gradle ≥ 9.1.0, JDK ≥ 17, Build Tools 36 |
| Gradle | 9.6 (wrapper базы) | сборка | уже в базе, совместим с AGP 9.2 |
| Kotlin | **built-in в AGP 9** (KGP ≥ 2.2.10, бандлится с AGP) | новый код | включается флагом, отдельный плагин не нужен и вреден |
| JDK | 17+ (Temurin 17 в CI) | запуск сборки | минимум для AGP 9.x и Gradle 9.6 |
| compileSdk/targetSdk | 37 (как в базе) | — | **не даунгрейдить** до «36» из CLAUDE.md — база уже на 37, даунгрейд = лишний риск; зафиксировать отклонение в SUMMARY |
| minSdk | 24 (как в базе) | — | совпадает с нижней границей проекта |

### Supporting (CI)

| Компонент | Версия | Назначение |
|---|---|---|
| GitHub Actions `ubuntu-latest` | — | Android SDK + build-tools предустановлены, лицензии приняты |
| `actions/checkout` | v4/v5 | checkout |
| `actions/setup-java` (temurin, 17) | v4 | JDK для Gradle |
| `gradle/actions/setup-gradle` | v4 | кэш Gradle (опционально, но ускоряет) |
| `aapt2` из `$ANDROID_HOME/build-tools/<ver>/` | предустановлен | дамп permissions собранного APK |

### Alternatives Considered

| Вместо | Можно | Tradeoff |
|---|---|---|
| Включить built-in Kotlin флагом | Плагин `org.jetbrains.kotlin.android` + classpath KGP | С AGP 9 это legacy-путь: конфликт «extension 'kotlin' already registered» при включённом built-in; больше конфигурации, тот же результат. Не брать |
| `aapt2 dump permissions` по APK | grep исходного `AndroidManifest.xml` | grep не видит merged manifest (permissions, добавленные зависимостями/плагинами); годится только как быстрый первый уровень. Использовать оба: grep — мгновенный сигнал, aapt2 — авторитетная проверка артефакта |
| `aapt2` | `apkanalyzer manifest permissions` | apkanalyzer в cmdline-tools тоже предустановлен; aapt2 проще и стабильнее в PATH-обнаружении. Любой вариант ок |
| GitHub fork (кнопка) | clone + push в новый репозиторий | Fork даёт видимую связь с upstream и простые мержи; «отвязанный» клон чуть чище выглядит как самостоятельный проект. Обе схемы сохраняют историю; выбрать fork — дешевле в поддержке |

## Architecture Patterns

### Порядок работ (рекомендация планировщику)

Задачи фазы почти независимы, но естественный порядок такой (каждый шаг заканчивается собирающимся APK):

1. **Форк + клон + сборка стока как есть** (`./gradlew assembleDebug`, установка на устройство, включение IME, печать) — базовая линия до любых изменений.
2. **applicationId + имя**: `applicationId "<новый>"` в `app/build.gradle` (namespace не трогать), `english_ime_name` в `values/strings-appname.xml` → «Tatar Keyboard (dev)»/аналог; сброс `versionCode 1` / `versionName "0.1.0"`; проверка установки рядом с оригиналом.
3. **Kotlin interop**: флаг + тестовый файл + вызов из Java.
4. **Keystore + signingConfig**: локально, `assembleRelease` → подписанный APK.
5. **CI**: workflow на push/PR — сборка + двухуровневая проверка INTERNET.

### Pattern 1: applicationId без смены namespace

**Что:** менять только `applicationId`; `namespace 'rkr.simplekeyboard.inputmethod'` и все Java-пакеты остаются.
**Зачем:** diff против upstream — считанные строки → будущие мержи релизов rkkr почти бесплатны; proguard-rules и `method.xml` (FQCN классов) остаются валидными; Apache-2.0 не требует переименования пакетов.
**Проверка side-by-side:** `adb shell pm list packages | grep -e simplekeyboard -e <новый id>` — оба пакета одновременно.

### Pattern 2: Включение built-in Kotlin (AGP 9)

```properties
# gradle.properties — БЫЛО (upstream выключил осознанно):
android.builtInKotlin=false
# СТАЛО: строку удалить (built-in Kotlin в AGP 9 включён по умолчанию)
```

Никаких изменений в `build.gradle` не требуется: AGP 9 бандлит KGP (≥ 2.2.10), Kotlin-исходники подхватываются из `src/main/java` и `src/main/kotlin`. **Не** добавлять `org.jetbrains.kotlin.android`.

Тестовый файл — минимальный объект, вызываемый из Java, например:

```kotlin
// app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/KotlinInteropCheck.kt
package rkr.simplekeyboard.inputmethod.latin.utils

import android.util.Log

object KotlinInteropCheck {
    @JvmStatic
    fun log() {
        Log.i("TatarKeyboard", "Kotlin interop OK")
    }
}
```

Вызов из `LatinIME.onCreate()` (Java): `KotlinInteropCheck.log();` — критерий 4 доказан logcat-строкой. `@JvmStatic` — чтобы вызов из Java был идиоматичным.

### Pattern 3: Условный signingConfig через keystore.properties

```groovy
// app/build.gradle (фрагмент android { ... })
def keystoreProps = new Properties()
def keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystorePropsFile.withInputStream { keystoreProps.load(it) }
}

android {
    signingConfigs {
        if (keystorePropsFile.exists()) {
            release {
                storeFile file(keystoreProps['storeFile'])
                storePassword keystoreProps['storePassword']
                keyAlias keystoreProps['keyAlias']
                keyPassword keystoreProps['keyPassword']
            }
        }
    }
    buildTypes {
        release {
            // ...существующий minifyEnabled/proguard...
            if (keystorePropsFile.exists()) {
                signingConfig signingConfigs.release
            }
        }
    }
}
```

Создание keystore (один раз, локально; `*.jks` уже в .gitignore):

```bash
keytool -genkeypair -v -keystore release.jks -alias tatarkeyboard \
  -keyalg RSA -keysize 4096 -validity 10950
```

`keystore.properties` (добавить в .gitignore!):

```properties
storeFile=../release.jks
storePassword=...
keyAlias=tatarkeyboard
keyPassword=...
```

Без файла — `assembleRelease` собирает unsigned APK (CI живёт без секретов); с файлом — критерий 5 (подписанный APK, проверка: `apksigner verify --print-certs app-release.apk`).

### Pattern 4: CI-проверка INTERNET (двухуровневая, PERF-04)

```yaml
# .github/workflows/ci.yml
name: CI
on:
  push:
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # Уровень 1: мгновенный fail по исходному манифесту
      - name: Check source manifest for INTERNET permission
        run: |
          if grep -R "android.permission.INTERNET" app/src/main/AndroidManifest.xml; then
            echo "::error::INTERNET permission found in source manifest"
            exit 1
          fi

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4

      - name: Build debug APK
        run: ./gradlew assembleDebug

      # Уровень 2: авторитетная проверка собранного артефакта (merged manifest)
      - name: Verify APK has no INTERNET permission
        run: |
          AAPT2=$(find "$ANDROID_HOME/build-tools" -name aapt2 | sort -V | tail -1)
          "$AAPT2" dump permissions app/build/outputs/apk/debug/app-debug.apk | tee perms.txt
          if grep -q "android.permission.INTERNET" perms.txt; then
            echo "::error::INTERNET permission found in built APK"
            exit 1
          fi

      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

Заметки: Android SDK и build-tools на `ubuntu-latest` предустановлены, недостающие платформы (SDK 37) AGP докачивает сам — отдельный шаг установки SDK не нужен. `aapt2 dump permissions` печатает строки вида `uses-permission: name='android.permission.VIBRATE'` — сейчас это единственная строка, что и ожидаем.

### Anti-Patterns to Avoid

- **Переименование Java-пакетов / namespace «для чистоты»** — сотни файлов diff, ломает мержи upstream и proguard-правила, пользователю не даёт ничего. applicationId ≠ namespace, менять только первый.
- **Подключение `org.jetbrains.kotlin.android` при AGP 9** — конфликт с built-in Kotlin («Cannot add extension with name 'kotlin'»).
- **«Причёсывание» флагов `android.*` в gradle.properties** — это фиксация legacy-поведения от rkkr; снятие любого флага = незапланированная миграция.
- **Секреты в signingConfig прямо в build.gradle / коммит keystore.properties** — только локальные файлы вне git.
- **CI-проверка только grep'ом исходного манифеста** — не видит merged manifest; проверять собранный APK.
- **Даунгрейд compileSdk 37 → 36 ради буквы CLAUDE.md** — движение назад против базы; оставить 37 и зафиксировать отклонение.

## Don't Hand-Roll

| Проблема | Не строить | Использовать | Почему |
|---|---|---|---|
| IME-скелет, отрисовка, touch, subtypes | своё IME | форк как есть | решение проекта; база работает из коробки |
| Kotlin-компиляция в Java-модуле | ручная связка KGP/версий | built-in Kotlin AGP 9 | бандлится с AGP, ноль конфигурации |
| Дамп permissions APK | парсинг бинарного AXML своим скриптом | `aapt2 dump permissions` | штатный инструмент build-tools, предустановлен в CI |
| Кэш Gradle в CI | ручные actions/cache ключи | `gradle/actions/setup-gradle` | официальный action, корректные ключи из коробки |
| JDK-провижининг | ручные загрузки | `actions/setup-java` + foojay-resolver (уже в settings.gradle) | стандарт |

## Common Pitfalls

### Pitfall 1: Ожидание «старого» тулчейна в базе

**Что ломается:** план закладывает время на апгрейд AGP/Gradle/SDK.
**Реальность:** база уже на AGP 9.2.1 / Gradle 9.6 / SDK 37 — новее ориентиров проекта. Апгрейд-задач в фазе быть не должно; наоборот — ничего не трогать в версиях.
**Сигнал:** любая задача плана со словом «апгрейд тулчейна» — лишняя.

### Pitfall 2: Локальная сборка падает из-за JDK < 17

**Причина:** Gradle 9.6 и AGP 9.x требуют JDK 17+.
**Как избежать:** собирать JDK-ом из Android Studio (Quail bundled JBR — 21) или Temurin 17+; в CI — `setup-java` 17. Быстрая диагностика: `./gradlew --version`.

### Pitfall 3: Kotlin-файл «не виден» из Java

**Причина:** забыт флаг `android.builtInKotlin=false` (Kotlin-компиляция вообще не запускается — ошибка «cannot find symbol» на Kotlin-классе).
**Как избежать:** удалить флаг первым шагом Kotlin-задачи; проверять критерий 4 именно вызовом из Java + logcat, а не фактом компиляции .kt.

### Pitfall 4: Обе установки называются «Simple Keyboard»

**Что происходит:** критерий 2 (side-by-side) формально выполнен, но в системном списке IME два неразличимых «Simple Keyboard».
**Как избежать:** сменить `english_ime_name` в `values/strings-appname.xml` (одна строка, `translatable="false"` — локализации не затронуты). Это рабочее имя; финальное — фаза 11.

### Pitfall 5: Debug и release нашего приложения вытесняют друг друга

**Что происходит:** оба buildType имеют один applicationId — установка release поверх debug падает по конфликту подписи (INSTALL_FAILED_UPDATE_INCOMPATIBLE), приходится удалять.
**Как избежать (опционально, на усмотрение планировщика):** `applicationIdSuffix ".debug"` в debug buildType — тогда рядом живут три пакета: оригинал, наш debug, наш release. Дешёвая строка, сильно упрощает жизнь соло-разработчику.

### Pitfall 6: Подпись release: сгенерировали keystore, но забыли про долговечность

**Риск:** ключ подписи де-факто вечный идентификатор приложения (IzzyOnDroid, обновления у пользователей). Утеря = потеря канала обновлений.
**Как избежать:** validity ≥ 25 лет (10950 дней), пароль в менеджере паролей, резервная копия `release.jks` вне репозитория (задокументировать в README/SUMMARY, где лежит бэкап — без путей к секретам в git).

### Pitfall 7: Первый запуск CI дольше и хрупче локального

**Причины:** докачка SDK 37, прогрев Gradle. Обычно всё проходит автоматически (лицензии на раннерах приняты), но если сборка упадёт на лицензии SDK — добавить шаг `yes | sdkmanager --licenses` до Gradle.
**Сигнал:** ошибка «license not accepted» в логе job.

## Code Examples

Ключевые примеры даны в Architecture Patterns (built-in Kotlin, signingConfig, CI workflow). Дополнительно — точечный diff applicationId:

```groovy
// app/build.gradle — единственные строки, которые меняет задача applicationId
defaultConfig {
    applicationId "io.github.<owner>.tatarkeyboard"   // было: "rkr.simplekeyboard.inputmethod"
    versionCode 1                                     // было: 146
    versionName "0.1.0"                               // было: "6.5"
    // compileSdk/minSdk/targetSdk — НЕ трогать
}
// namespace 'rkr.simplekeyboard.inputmethod'  ← остаётся как есть
```

Рекомендация по выбору id: если репозиторий будет жить на GitHub у владельца `<owner>`, взять `io.github.<owner>.tatarkeyboard` — конвенция, гарантированно бесконфликтная и знакомая IzzyOnDroid; иначе `org.tatarkeyboard.ime`. Провизорность зафиксировать в SUMMARY фазы.

## State of the Art

| Старый подход | Текущий (июль 2026) | Когда сменилось | Значение для нас |
|---|---|---|---|
| Плагин `org.jetbrains.kotlin.android` + версия KGP | Built-in Kotlin в AGP 9 (флаг `android.builtInKotlin`, по умолчанию on; KGP ≥ 2.2.10 бандлится) | AGP 9.0, янв 2026 | Kotlin = удаление одной строки в gradle.properties |
| AGP 8.x / JDK 17 target Java 8 | AGP 9.x: JDK 17+ для сборки, дефолтный source/target Java 11, Gradle ≥ 9.1, Build Tools 36 | AGP 9.0 | база уже мигрирована rkkr — нам достался готовый результат |
| `apkanalyzer`/aapt для проверки манифеста | `aapt2 dump permissions` — актуальный штатный путь | — | двухуровневая CI-проверка |
| Опасение «база на старом тулчейне» (CONTEXT) | База на AGP 9.2.1/Gradle 9.6/SDK 37 — свежее некуда | проверено по HEAD 2026-07-12 | вычеркнуть апгрейд-риски из плана |

**Deprecated/устаревшее:** `android.builtInKotlin=false` перестанет работать в AGP 10 (2-я половина 2026) — ещё один довод просто удалить флаг сейчас.

## Open Questions

1. **GitHub-owner для applicationId** (`io.github.<owner>...`)
   - Известно: конвенция предпочтительна для GitHub-проекта; id провизорный.
   - Неизвестно: логин владельца будущего репозитория.
   - Рекомендация: планировщику подставить фактический логин при создании форка; если сомнение — нейтральный `org.tatarkeyboard.ime`. Блокером не является (смена — одна строка до публикации).

2. **compileSdk/targetSdk 37 vs «36» в CLAUDE.md/CONTEXT**
   - Известно: база на 37; констрейнт «36» писался до чтения исходников.
   - Рекомендация: оставить 37 (минимальное изменение = ноль изменений), зафиксировать отклонение в SUMMARY фазы; при желании обновить CLAUDE.md отдельным правочным коммитом.

3. **Fork-кнопка vs независимый репозиторий**
   - Известно: обе схемы сохраняют историю и Apache-2.0-гигиену; fork упрощает мержи upstream, но помечает репозиторий как «forked from rkkr/simple-keyboard» и слегка ограничивает GitHub-фичи (например, поиск кода в форках).
   - Рекомендация: обычный fork; если важна «самостоятельность» витрины — clone+push с remote `upstream` на rkkr. Решение эстетическое, не техническое.

## Validation Architecture

### Test Framework

| Property | Value |
|---|---|
| Framework | нет — в базе нет ни `test/`, ни `androidTest/`; фаза инфраструктурная, юнит-тесты не заводим (первый осмысленный тест-таргет появится с нашей логикой в фазах 2+) |
| Config file | none |
| Quick run command | `./gradlew assembleDebug` (компиляция = smoke) |
| Full suite command | `./gradlew build` (debug+release+lint) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|---|---|---|---|---|
| PERF-04 | В манифесте/APK нет INTERNET; CI падает при появлении | CI-проверка | `aapt2 dump permissions <apk> \| grep -v INTERNET` (шаг workflow) | ❌ Wave 0 — создать `.github/workflows/ci.yml` |
| (крит. 1) | APK собирается, IME печатает | build + manual | `./gradlew assembleDebug assembleRelease`; далее ручной smoke на устройстве | — |
| (крит. 2) | Side-by-side с оригиналом | manual | `adb shell pm list packages \| grep -c -e simplekeyboard -e <id>` → 2 | — |
| (крит. 4) | Kotlin вызывается из Java | build + logcat | `adb logcat -s TatarKeyboard` после показа клавиатуры | — |
| (крит. 5) | Подписанный release | CLI | `apksigner verify --print-certs app-release.apk` | — |

Негативная проверка CI обязательна в плане: временный коммит (или локальный тест workflow) с добавленным `<uses-permission android:name="android.permission.INTERNET"/>` должен **уронить** job — иначе критерий 3 не доказан. После проверки коммит откатить.

### Sampling Rate

- **Per task commit:** `./gradlew assembleDebug`
- **Per wave merge:** `./gradlew build` + CI зелёный
- **Phase gate:** ручной smoke на устройстве (включение IME, печать в реальном приложении) + негативный тест CI пройден

### Wave 0 Gaps

- [ ] `.github/workflows/ci.yml` — покрывает PERF-04 (создаётся в этой фазе, шаблон в Pattern 4)

## Sources

### Primary (HIGH confidence)

- Клон `rkkr/simple-keyboard` HEAD `b40c70d9` (2026-07-12), локально `/tmp/sk-research` — build.gradle (корень/app), settings.gradle, gradle-wrapper.properties, gradle.properties, AndroidManifest.xml, method.xml, strings-appname.xml, .gitignore, proguard-rules.pro, дерево `src/` (96 Java-файлов), `res/xml/` (360 файлов) — все факты таблицы «Фактическая структура» прочитаны из файлов напрямую
- [Migrate to built-in Kotlin — Android Developers](https://developer.android.com/build/migrate-to-built-in-kotlin) — поведение `android.builtInKotlin`, конфликт со старым плагином, KGP 2.2.10 бандлится
- [AGP 9.0 release notes — Android Developers](https://developer.android.com/build/releases/agp-9-0-0-release-notes) — built-in Kotlin по умолчанию, требования JDK 17 / Gradle 9.1 / Build Tools 36
- Локальные доменные ресерчи: `research/06-fork-ili-s-nulya.md` (вердикт по базе, единый источник правды), `research/01-stek-i-arhitektura-ime.md` (IME-архитектура), `.planning/research/SUMMARY.md`

### Secondary (MEDIUM confidence)

- [Update your Kotlin projects for AGP 9 — JetBrains blog](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/) — подтверждение миграционных шагов
- [AGP 9 migration skill — Android Developers](https://developer.android.com/agents/skills/build/agp/agp-9-upgrade/skill), [Kotlin version matrix](https://github.com/kotlin/kotlin-agent-skills/blob/main/skills/kotlin-tooling-agp9-migration/references/VERSION-MATRIX.md) — версии-совместимость

### Tertiary (LOW confidence)

- Предустановленность Android SDK/build-tools и принятых лицензий на GitHub `ubuntu-latest` — устойчивое знание об образах раннеров, но состав образа меняется; митигация заложена в Pitfall 7 (fallback-шаг `sdkmanager --licenses`)

## Metadata

**Confidence breakdown:**

- Структура базы: HIGH — прочитаны реальные исходники HEAD, не описания
- Toolchain/Kotlin: HIGH — официальные release notes + факт `android.builtInKotlin=false` в самом репозитории
- CI/keystore-паттерны: HIGH/MEDIUM — канонические, многократно применяемые паттерны; точный синтаксис workflow проверяется первым прогоном (негативный тест обязателен)

**Research date:** 2026-07-18
**Valid until:** ~2026-08-18 (база живая — перед стартом исполнения свериться, не уехал ли HEAD upstream; наш клон-снимок: `b40c70d9`)
