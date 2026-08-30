#!/usr/bin/env python3
"""Набор татарских слов тапами и замер «чернил» в полосе подсказок.

Полоса рисуется на Canvas, поэтому `uiautomator dump` её не видит — свидетельство только
пиксельное, как и во всех прошлых миссиях этого проекта. «Чернила» — число пикселей в
области полосы, отличающихся от фона строго темнее порога; пустая полоса даёт 0 (два
серых разделителя в счёт не идут, они светлее порога).

Ловушка, записанная миссией tt-bigram-adjacency: `screencap` отдаёт УСТАРЕВШУЮ поверхность
IME, пока клавиатура не перерисуется. Поэтому перед каждым снимком Shift переключается
дважды — это заставляет перерисовку и не меняет ни текста, ни состояния регистра.
"""
import subprocess, sys, time
from pathlib import Path
from PIL import Image

ADB = str(Path.home() / "Android/Sdk/platform-tools/adb")
DEV = "emulator-5558"
SCRATCH = Path(__file__).resolve().parent

# Экран 1080x2280, татарская раскладка с пятым рядом. Центры клавиш сняты со снимка kb2.png.
ROW_EXTRA_Y, ROW1_Y, ROW2_Y, ROW3_Y, ROW4_Y = 1497, 1640, 1783, 1926, 2069
ROW1 = "йцукенгшщзх"
ROW2 = "фывапролджэ"
ROW3 = "ячсмитьбю"
EXTRA = "әөүҗңһ"

KEYS: dict[str, tuple[int, int]] = {}
for index, letter in enumerate(ROW1):
    KEYS[letter] = (49 + 98 * index, ROW1_Y)
for index, letter in enumerate(ROW2):
    KEYS[letter] = (49 + 98 * index, ROW2_Y)
for index, letter in enumerate(ROW3):
    KEYS[letter] = (163 + 94 * index, ROW3_Y)
for index, letter in enumerate(EXTRA):
    KEYS[letter] = (89 + 180 * index, ROW_EXTRA_Y)
KEYS[" "] = (594, ROW4_Y)
SHIFT = (57, ROW3_Y)
DELETE = (1021, ROW3_Y)

# Область полосы подсказок: от низа поля ввода до верхнего ряда клавиш, с полями по краям.
BAND = (40, 1310, 1040, 1410)
INK_THRESHOLD = 140  # разделители полосы светлее (около 200), буквы темнее


def adb(*args: str) -> str:
    return subprocess.run([ADB, "-s", DEV, *args], capture_output=True, text=True).stdout


def tap(x: int, y: int, pause: float = 0.18) -> None:
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(pause)


def clear_field(count: int = 60) -> None:
    for _ in range(count):
        tap(*DELETE, pause=0.05)
    time.sleep(0.4)


def type_text(text: str) -> None:
    for character in text:
        if character not in KEYS:
            raise SystemExit(f"нет клавиши для {character!r}")
        tap(*KEYS[character])


def band_ink(name: str) -> tuple[int, Path]:
    """Переключить Shift дважды (снять стоп-кадр IME), снять экран, посчитать чернила."""
    tap(*SHIFT, pause=0.25)
    tap(*SHIFT, pause=0.45)
    adb("shell", "screencap", "-p", "/sdcard/band.png")
    local = SCRATCH / f"{name}.png"
    subprocess.run([ADB, "-s", DEV, "pull", "/sdcard/band.png", str(local)],
                   capture_output=True)
    image = Image.open(local).convert("L").crop(BAND)
    ink = sum(1 for pixel in image.getdata() if pixel < INK_THRESHOLD)
    return ink, local


def main() -> None:
    label = sys.argv[1]
    cases = sys.argv[2:]
    for index, phrase in enumerate(cases):
        clear_field()
        type_text(phrase)
        ink, path = band_ink(f"{label}-{index:02d}")
        print(f"{label}\t{phrase!r}\tink={ink}\t{path.name}", flush=True)


main()
