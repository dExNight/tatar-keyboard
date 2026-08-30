#!/bin/bash
set -u
cd "$(dirname "$0")"
dl(){ n="$2"; [ -s "$n" ] && { echo "skip $n"; return; }; curl -sS -L --retry 3 -o "$n.part" "$1" && mv "$n.part" "$n" && echo "ok $n $(stat -c%s "$n")"; }
dl https://object.pouta.csc.fi/OPUS-OpenSubtitles/v2024/mono/tt.txt.gz  OpenSubtitles-v2024.tt.txt.gz
dl https://object.pouta.csc.fi/OPUS-Tatoeba/v2026-07-08/mono/tt.txt.gz  Tatoeba-v2026-07-08.tt.txt.gz
dl https://object.pouta.csc.fi/OPUS-QED/v2.0a/mono/tt.txt.gz            QED-v2.0a.tt.txt.gz
dl https://object.pouta.csc.fi/OPUS-TED2020/v1/mono/tt.txt.gz           TED2020-v1.tt.txt.gz
dl https://object.pouta.csc.fi/OPUS-Tatoeba/v2026-07-08/mono/ru.txt.gz  Tatoeba-v2026-07-08.ru.txt.gz
dl https://object.pouta.csc.fi/OPUS-QED/v2.0a/mono/ru.txt.gz            QED-v2.0a.ru.txt.gz
dl https://object.pouta.csc.fi/OPUS-TED2020/v1/mono/ru.txt.gz           TED2020-v1.ru.txt.gz
dl https://object.pouta.csc.fi/OPUS-OpenSubtitles/v2024/mono/ru.txt.gz  OpenSubtitles-v2024.ru.txt.gz
echo "ALL DONE"
