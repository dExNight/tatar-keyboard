#!/bin/bash
# Холодный старт IME: от старта процесса (поле 22 /proc/<pid>/stat, шаг 10 мс при CLK_TCK=100)
# до первого FrameCompleted (колонка 14 первой строки ---PROFILEDATA---).
#
# То же измерение, что docs/dict-accept/evidence/coldstart.sh, с одной поправкой на среду:
# поле в фокусе держит диалог Google Messages, а не поиск настроек — на образе Android 14
# пакет поиска называется иначе, и полагаться на него незачем.
A=$HOME/Android/Sdk/platform-tools/adb
DEV=${DEV:-emulator-5558}
PKG=org.tatarkeyboard.ime
N=${1:-20}
raise_field() {
  $A -s "$DEV" shell monkey -p com.google.android.apps.messaging -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 4
  $A -s "$DEV" shell input tap 540 2125 >/dev/null 2>&1   # поле сообщения открытого диалога
  sleep 3
}
for i in $(seq 1 "$N"); do
  if ! $A -s "$DEV" shell dumpsys input_method 2>/dev/null | grep -q "mIsInputViewShown=true"; then
    raise_field
  fi
  old=$($A -s "$DEV" shell pidof $PKG | tr -d '\r')
  [ -z "$old" ] && { echo "SKIP no pid"; continue; }
  $A -s "$DEV" shell kill -9 "$old" >/dev/null 2>&1
  new=""; for t in $(seq 1 60); do
    sleep 0.5
    p=$($A -s "$DEV" shell pidof $PKG | tr -d '\r')
    if [ -n "$p" ] && [ "$p" != "$old" ]; then new=$p; break; fi
  done
  [ -z "$new" ] && { echo "SKIP process did not come back"; continue; }
  shown=0; for t in $(seq 1 40); do
    sleep 0.25
    if $A -s "$DEV" shell dumpsys input_method 2>/dev/null | grep -q "mIsInputViewShown=true"; then shown=1; break; fi
  done
  [ "$shown" = 0 ] && { echo "SKIP window did not come back"; continue; }
  start=$($A -s "$DEV" shell cat /proc/"$new"/stat 2>/dev/null | awk '{print $22}' | tr -d '\r')
  frame=$($A -s "$DEV" shell dumpsys gfxinfo $PKG framestats 2>/dev/null \
          | awk '/---PROFILEDATA---/{f=1;next} f&&/^[0-9]/{print $0; exit}' \
          | tr -d '\r' | awk -F, '{print $14}')
  if [ -z "$start" ] || [ -z "$frame" ] || [ "$frame" = "0" ]; then echo "SKIP no data"; continue; fi
  python3 -c "import sys; print(f'{int(sys.argv[2])/1e6 - int(sys.argv[1])*10.0:.1f}')" "$start" "$frame"
done
