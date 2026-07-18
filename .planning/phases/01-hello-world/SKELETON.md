# Walking Skeleton — Tatar Keyboard

**Phase:** 1
**Generated:** 2026-07-17

## Capability Proven End-to-End

Пользователь устанавливает debug-APK нашего форка на устройство рядом с оригинальным Simple Keyboard, включает клавиатуру «Tatar Keyboard (dev)» в системных настройках и печатает ею в реальном приложении — при этом Kotlin-код участвует в сборке, release-APK подписывается нашим ключом, а CI на каждом коммите доказуемо падает при появлении разрешения INTERNET.

**User story:** As a татароязычный пользователь, I want установить форк-клавиатуру и печатать ею в реальном приложении, so that фундамент IME (сборка → установка → системная регистрация → ввод) доказан на моём устройстве.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| База | Форк rkkr/simple-keyboard, merge коммита `b40c70d9` (v6.5) в этот репозиторий через remote `upstream` с сохранением полной истории | Решение проекта (research/06); история = юридическая гигиена Apache-2.0 + канал будущих мержей upstream |
| Toolchain | Как в базе: AGP 9.2.1, Gradle 9.6 (wrapper), compileSdk/targetSdk **37**, minSdk 24, JDK 17+ | База уже на свежем тулчейне; ничего не апгрейдить и не даунгрейдить. **Отклонение от CLAUDE.md («36»)**: база на 37, даунгрейд = лишний риск — фиксируем 37 |
| Kotlin | Built-in Kotlin AGP 9 (удаление `android.builtInKotlin=false` из gradle.properties), БЕЗ плагина `org.jetbrains.kotlin.android` | KGP бандлится с AGP 9; отдельный плагин конфликтует с built-in («extension 'kotlin' already registered») |
| applicationId | **`org.tatarkeyboard.ime`** (провизорный, финальный — фаза 11) + `applicationIdSuffix ".debug"` в debug | Нейтральный, не зависит от GitHub-логина, уникален против `rkr.simplekeyboard.inputmethod`; suffix даёт сосуществование debug/release/оригинала. Смена — одна строка |
| namespace / Java-пакеты | `rkr.simplekeyboard.inputmethod` — НЕ менять | Минимальный diff против upstream, валидные proguard-rules и method.xml, дешёвые мержи |
| Видимое имя | «Tatar Keyboard (dev)» в `values/strings-appname.xml` (`english_ime_name`) | Одна строка, различимость в системном списке IME; финальное имя — фаза 11 |
| Версия | `versionCode 1`, `versionName "0.1.0"` | Своя линия версий, независимая от upstream (146/6.5) |
| Signing | `release.jks` (RSA 4096, validity 10950 дней) + `keystore.properties` вне git; условный signingConfig — без файла release собирается unsigned | CI живёт без секретов; ключ — вечный идентификатор приложения (задел REL-01) |
| CI | GitHub Actions (`.github/workflows/ci.yml`) на push/PR: сборка debug + двухуровневая проверка INTERNET (grep исходного манифеста + `aapt2 dump permissions` собранного APK) | PERF-04 с проверяемой гарантией; aapt2 видит merged manifest, grep — мгновенный сигнал |
| Локальная валидация | `scripts/check-no-internet.sh` — тот же двухуровневый чек локально и в CI | Один источник правды для проверки, вызывается и разработчиком, и workflow |

## Stack Touched in Phase 1

- [ ] Project scaffold = форк: upstream `b40c70d9` смержен в репозиторий, `./gradlew assembleDebug` зелёный
- [ ] End-to-end slice: debug-APK ставится через `adb install` рядом с оригиналом, IME включается и печатает в реальном приложении
- [ ] Kotlin interop: `KotlinInteropCheck.kt` собирается и вызывается из Java (`LatinIME.onCreate`), виден в logcat
- [ ] Signing: `assembleRelease` даёт APK, проходящий `apksigner verify`
- [ ] Deployment/CI: GitHub-репозиторий + workflow зелёный на main; негативный тест (добавленный INTERNET роняет job) продемонстрирован

## Out of Scope (Deferred to Later Slices)

- Любые изменения раскладок, UI, поведения — сток-функциональность базы остаётся как есть (татарская раскладка — фаза 2)
- Переименование Java-пакетов/namespace — никогда (анти-паттерн)
- Апгрейд/даунгрейд тулчейна, «причёсывание» флагов `android.*` в gradle.properties
- Финальное имя приложения и applicationId — фаза 11
- Юнит-тесты — первый осмысленный тест-таргет появится с нашей логикой в фазах 2+

## Subsequent Slice Plan

Каждая следующая фаза добавляет вертикальный слайс поверх этого скелета, не меняя его архитектурных решений:

- Phase 2: татарская раскладка (ЙЦУКЕН + пятый ряд) как XML-данные
- Phase 3: три subtype и переключение языков
- Phase 4–5: механика ввода (регистр, служебные клавиши, жесты)
- Phase 6–7: iOS-скин (Canvas-рендер, превью, отклик)
- Phase 8–10: совместимость, доступность, онбординг
- Phase 11: замеры бюджетов, финальное имя, релиз
