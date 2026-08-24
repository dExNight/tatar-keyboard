#!/bin/bash
# Холодный старт IME: от старта процесса (поле 22 /proc/<pid>/stat, шаг 10 мс при CLK_TCK=100)
# до первого FrameCompleted (колонка 14 первой строки ---PROFILEDATA---).
# Поле ввода остаётся в фокусе: система сама пересоздаёт процесс и окно клавиатуры.
A=$HOME/Android/Sdk/platform-tools/adb
PKG=org.tatarkeyboard.ime
N=${1:-20}
for i in $(seq 1 $N); do
  if ! $A shell dumpsys input_method 2>/dev/null | grep -q "mIsInputViewShown=true"; then
    $A shell am force-stop com.android.settings.intelligence >/dev/null 2>&1
    sleep 1
    $A shell am start -n com.android.settings.intelligence/.search.SearchActivity >/dev/null 2>&1
    sleep 4
  fi
  old=$($A shell pidof $PKG | tr -d '\r')
  [ -z "$old" ] && { echo "SKIP no pid"; continue; }
  $A shell kill -9 $old >/dev/null 2>&1
  new=""; for t in $(seq 1 60); do
    sleep 0.5
    p=$($A shell pidof $PKG | tr -d '\r')
    if [ -n "$p" ] && [ "$p" != "$old" ]; then new=$p; break; fi
  done
  [ -z "$new" ] && { echo "SKIP process did not come back"; continue; }
  shown=0; for t in $(seq 1 40); do
    sleep 0.25
    if $A shell dumpsys input_method 2>/dev/null | grep -q "mIsInputViewShown=true"; then shown=1; break; fi
  done
  [ "$shown" = 0 ] && { echo "SKIP window did not come back"; continue; }
  start=$($A shell cat /proc/$new/stat 2>/dev/null | awk '{print $22}' | tr -d '\r')
  frame=$($A shell dumpsys gfxinfo $PKG framestats 2>/dev/null \
          | awk '/---PROFILEDATA---/{f=1;next} f&&/^[0-9]/{print $0; exit}' \
          | tr -d '\r' | awk -F, '{print $14}')
  if [ -z "$start" ] || [ -z "$frame" ] || [ "$frame" = "0" ]; then echo "SKIP no data"; continue; fi
  python3 -c "import sys; print(f'{int(sys.argv[2])/1e6 - int(sys.argv[1])*10.0:.1f}')" "$start" "$frame"
done
