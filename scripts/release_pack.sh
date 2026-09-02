#!/usr/bin/env bash
# SIZE-3: релизная упаковка с zopfli-рекомпрессией (docs/SIZE-OPTIMIZATION-RESEARCH.md,
# упаковочный уровень: zipalign -z даёт ~40 КБ). Порядок обязателен: zipalign ДО
# подписи (подпись v2 покрывает байты zip-записей — перепаковка подписанного APK
# её инвалидирует).
#
# Пайплайн одной командой:
#   1. ./gradlew clean assembleRelease -PskipReleaseSigning  → unsigned APK
#   2. zipalign -f -z 4                                      → zopfli-рекомпрессия
#   3. zipalign -c                                           → выравнивание сохранено
#   4. apksigner sign (ключи из keystore.properties, v2-only — как у AGP-сборки)
#   5. apksigner verify --print-certs                        → подпись валидна
#
# Запуск из корня репозитория:
#   bash scripts/release_pack.sh [путь-результата.apk]
# По умолчанию результат — app/build/outputs/apk/release/app-release-zopfli.apk.
#
# Воспроизводимость (DEV-2) сохраняется: и AGP-сборка unsigned, и zopfli (при
# пиннованной версии build-tools — resolve_tool берёт старшую установленную), и
# apksigner v2 детерминированы; два прогона одного дерева дают одинаковый SHA-256.
# Скрипт ничего не меняет в репозитории, кроме build/ и app/build/.
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "$SCRIPT_DIR/.." && pwd)
cd "$REPO_ROOT"

LOG_DIR="build/release_pack"
mkdir -p "$LOG_DIR"

OUT="${1:-app/build/outputs/apk/release/app-release-zopfli.apk}"

# --- инструменты SDK (zipalign, apksigner) ---------------------------------------------------

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [ ! -d "$SDK_ROOT/build-tools" ]; then
    echo "ERROR: Android SDK build-tools не найдены ($SDK_ROOT/build-tools);" >&2
    echo "       задайте ANDROID_HOME или ANDROID_SDK_ROOT" >&2
    exit 1
fi

resolve_tool() { # <имя>
    local found
    found=$(find "$SDK_ROOT/build-tools" -maxdepth 2 -name "$1" -type f | sort -V | tail -1)
    if [ -z "$found" ]; then
        echo "ERROR: $1 не найден под $SDK_ROOT/build-tools" >&2
        exit 1
    fi
    printf '%s' "$found"
}

ZIPALIGN=$(resolve_tool zipalign)
APKSIGNER=$(resolve_tool apksigner)
echo "zipalign:  $ZIPALIGN"
echo "apksigner: $APKSIGNER"

# --- ключи из keystore.properties (та же конвенция, что app/build.gradle) --------------------

KS_PROPS="keystore.properties"
if [ ! -f "$KS_PROPS" ]; then
    echo "ERROR: нет keystore.properties — подписывать нечем (unsigned-сборка и так доступна через gradle)" >&2
    exit 1
fi

ks_prop() { # <ключ>
    grep -E "^$1=" "$KS_PROPS" | head -1 | cut -d= -f2-
}
KS_FILE=$(ks_prop storeFile)
KS_ALIAS=$(ks_prop keyAlias)
KS_STORE_PASS=$(ks_prop storePassword)
KS_KEY_PASS=$(ks_prop keyPassword)
if [ -z "$KS_FILE" ] || [ -z "$KS_ALIAS" ]; then
    echo "ERROR: в keystore.properties нет storeFile/keyAlias" >&2
    exit 1
fi
# storeFile относительный — резолвится от app/ (как file() в app/build.gradle).
case "$KS_FILE" in
    /*) ;;
    *)  KS_FILE="app/$KS_FILE" ;;
esac
if [ ! -f "$KS_FILE" ]; then
    echo "ERROR: keystore не найден: $KS_FILE" >&2
    exit 1
fi

# --- 1. unsigned release ---------------------------------------------------------------------

echo "== 1/5 clean assembleRelease -PskipReleaseSigning =="
mkdir -p "$LOG_DIR"
./gradlew clean assembleRelease -PskipReleaseSigning --console=plain \
    >"$LOG_DIR/assemble.log" 2>&1 || {
        echo "ERROR: сборка упала, лог $LOG_DIR/assemble.log" >&2
        tail -20 "$LOG_DIR/assemble.log" >&2 || true
        exit 1
    }
# gradle clean стирает корневой build/ вместе с LOG_DIR — создаём заново.
mkdir -p "$LOG_DIR"

UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
if [ ! -f "$UNSIGNED" ]; then
    echo "ERROR: $UNSIGNED не появился (skipReleaseSigning не сработал?)" >&2
    exit 1
fi

# --- 2. zipalign -z (zopfli) -----------------------------------------------------------------

echo "== 2/5 zipalign -z (zopfli) =="
ALIGNED="$LOG_DIR/app-release-zopfli-aligned.apk"
"$ZIPALIGN" -f -z 4 "$UNSIGNED" "$ALIGNED"

# --- 3. проверка выравнивания -----------------------------------------------------------------

echo "== 3/5 zipalign -c =="
if "$ZIPALIGN" -c 4 "$ALIGNED" >"$LOG_DIR/zipalign-check.log" 2>&1; then
    echo "  выравнивание OK"
else
    echo "ERROR: выравнивание сломано, лог $LOG_DIR/zipalign-check.log" >&2
    exit 1
fi

# --- 4. подпись (v2-only — как у AGP-сборки линейки с 2026-08-18) ------------------------------

echo "== 4/5 apksigner sign =="
# Пароли — через env:-форму, а не pass: в argv: командная строка процесса видна
# любому пользователю хоста через ps (C1 аудита 2026-09-02), окружение — нет.
KS_STORE_PASS="$KS_STORE_PASS" KS_KEY_PASS="$KS_KEY_PASS" \
"$APKSIGNER" sign \
    --ks "$KS_FILE" --ks-key-alias "$KS_ALIAS" \
    --ks-pass env:KS_STORE_PASS --key-pass env:KS_KEY_PASS \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled false \
    --out "$OUT" "$ALIGNED"

# --- 5. верификация ----------------------------------------------------------------------------

echo "== 5/5 apksigner verify =="
if ! "$APKSIGNER" verify --print-certs "$OUT" | tee "$LOG_DIR/verify.log"; then
    echo "ERROR: подпись не верифицируется" >&2
    exit 1
fi

SIZE_UNSIGNED=$(stat -c %s "$UNSIGNED")
SIZE_OUT=$(stat -c %s "$OUT")
SHA_OUT=$(sha256sum "$OUT" | awk '{print $1}')
echo
echo "RESULT|unsigned|$SIZE_UNSIGNED"
echo "RESULT|zopfli+signed|$SIZE_OUT"
printf 'RESULT|delta|%+d Б\n' "$((SIZE_OUT - SIZE_UNSIGNED))"
echo "RESULT|sha256|$SHA_OUT"
echo "RESULT|out|$OUT"
