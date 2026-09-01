#!/usr/bin/env python3
"""Build the Tatar Keyboard emoji-suggest table from the curated TSV.

The tool uses only the Python standard library. The input is the hand-curated
``scripts/emoji_suggest_data.tsv`` — one word form per line::

    <emoji>\\t<language>\\t<word>

``language`` is ``ru`` or ``tt``; ``word`` is a single lowercase NFC word that
must pass :func:`dictionary_coverage.normalize_word` with the alphabet of its
language (Tatar words — TATAR_ALPHABET, Russian words — RUSSIAN_ALPHABET);
``emoji`` is a sequence exactly as it appears in the panel asset
``emoji_set_v1.txt`` — so the suggestion bar can never offer an emoji the
panel cannot draw. The output is
``app/src/main/assets/emoji/emoji_suggest_v1.txt``, a deterministic UTF-8/LF
text asset (data, not code), one line per word form sorted by (language,
word)::

    <language>\\t<word>\\t<emoji>

The generator is fail-closed. It exits with a nonzero status and writes no
partial asset when:

* the input is not valid UTF-8, has a malformed line, or an empty data file,
* a word is not canonical for its language (normalize_word rejects it),
* an emoji sequence is absent from the panel asset,
* the same (language, word) pair maps to two different emoji (a conflict —
  one word yields exactly one emoji per language),
* the same (emoji, language, word) row appears twice,
* a (language, word) pair is on the polysemy DENYLIST below (the word is too
  ambiguous to ever suggest an emoji for; the reasons are written next to the
  entries), or
* a guardrail is breached (asset > MAX_ASSET_BYTES, zlib-compressed asset >
  MAX_ZLIB_BYTES, or the line count leaves [MIN_LINES, MAX_LINES]).

The denylist is the project's defence against false positives measured in
``docs/EMOJI-SUGGEST-RESEARCH.md`` (CLDR raw hits like «можно» → 🚬) plus
word-form collisions found during curation. Adding a denied word to the TSV
fails the build loudly instead of shipping a misleading suggestion.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import tempfile
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))

import dictionary_coverage  # noqa: E402

# Words are validated by dictionary_coverage.normalize_word with the alphabet
# of their language — the same sets the Kotlin dictionary validators enforce.
LANG_ALPHABETS = {
    "ru": dictionary_coverage.RUSSIAN_ALPHABET,
    "tt": dictionary_coverage.TATAR_ALPHABET,
}

# Polysemy denylist: (language, word) -> reason. A word listed here must never
# appear in the curated data; the packer fails the build if it does. Sources:
# the false-positive measurements of docs/EMOJI-SUGGEST-RESEARCH.md and the
# curation passes of docs/emoji-suggest/DATA.md.
DENYLIST: dict[tuple[str, str], str] = {
    ("ru", "можно"): "модальное слово; сырой CLDR-хит давал 🚬 (замер ресерча)",
    ("ru", "работа"): "абстракция; сырой CLDR-хит давал 😫 (замер ресерча)",
    ("ru", "работаю"): "форма «работа»; абстракция",
    ("ru", "день"): "сутки частотнее «дня рождения»; сырой CLDR-хит давал 🥳",
    ("ru", "нет"): "отрицание; сырой CLDR-хит давал 😑 (замер ресерча)",
    ("ru", "пока"): "союз/прощание; сырой CLDR-хит давал 🫂",
    ("ru", "здесь"): "указательное наречие; сырой CLDR-хит давал 🈁",
    ("ru", "очень"): "наречие степени, образа нет",
    ("ru", "когда"): "вопросительное слово, образа нет",
    ("ru", "если"): "союз, образа нет",
    ("ru", "жизнь"): "абстракция, образа нет",
    ("ru", "дело"): "абстракция, образа нет",
    ("ru", "рука"): "частотнее во фразеологизмах («на руках», «рука об руку»)",
    ("ru", "кит"): "совпадает с заимствованием «kit»; зоологическое значение редкое в переписке",
    ("ru", "бар"): "заведение vs штанга/единица давления",
    ("ru", "ключ"): "инструмент vs «ключ к задаче», криптография",
    ("ru", "замок"): "строение vs механизм запирания",
    ("ru", "мир"): "планета/вселенная vs «не война»",
    ("ru", "лук"): "растение vs оружие",
    ("ru", "молния"): "разряд vs застёжка на одежде",
    ("ru", "месяц"): "луна vs календарный месяц (календарный частотнее)",
    ("ru", "земля"): "планета vs грунт",
    ("ru", "время"): "абстракция; сырой CLDR-хит давал часы",
    ("ru", "нельзя"): "модальное слово; сырой CLDR-хит давал 🈲",
    ("ru", "очки"): "зрительные vs игровые баллы",
    ("ru", "мышь"): "животное vs компьютерная мышь",
    ("ru", "ручка"): "письменная vs дверная, уменьш. от «рука»",
    ("ru", "камера"): "фото vs тюремная камера, камера хранения",
    ("ru", "труба"): "музыкальный инструмент vs трубопровод",
    ("ru", "борьба"): "спорт vs абстрактная «борьба с…»",
    ("ru", "зарядка"): "зарядное устройство vs утренняя гимнастика",
    ("ru", "приставка"): "игровая vs грамматическая приставка",
    ("ru", "карта"): "игральная vs географическая/банковская",
    ("ru", "кошелек"): "портмоне vs криптокошелёк",
    ("tt", "бар"): "«есть/имеется» — частотнейший глагол, не заведение",
    ("tt", "юк"): "«нет» — частотное служебное слово",
    ("tt", "да"): "частица «и/тоже»",
    ("tt", "көн"): "день vs солнце (оба частотны)",
    ("tt", "юл"): "дорога vs переносный «путь»",
    ("tt", "баш"): "голова vs начало/вершина",
    ("tt", "тел"): "язык-орган vs язык речи",
    ("tt", "кул"): "рука, но частотнее во фразеологизмах (паритет с ru «рука»)",
    ("tt", "сәгать"): "наручные часы vs «час» как единица времени",
    ("tt", "һава"): "воздух vs погода",
    ("tt", "яз"): "весна vs повелительное «напиши» (язу)",
    ("tt", "кара"): "чёрный vs повелительное «посмотри» (карау)",
    ("tt", "җир"): "планета vs грунт (паритет с ru «земля»)",
    ("tt", "кит"): "кит vs повелительное «уходи» (китү)",
    ("tt", "ит"): "мясо vs повелительное «делай» (итү)",
    ("tt", "ат"): "конь vs имя vs повелительное «стреляй/бросай» (ату)",
    ("tt", "эчке"): "коза vs «внутренний» (эчке як)",
    ("tt", "ак"): "белый vs повелительное «теки» (агу)",
}

# Guardrails. A change to any number is a written decision, not a silent bump.
# MAX_ZLIB_BYTES is the release budget of docs/EMOJI-SUGGEST-PLAN.md
# (≤ 32 КБ сжатого); the raw asset is allowed more because APK assets are
# compressed on packaging anyway.
MAX_ASSET_BYTES = 131072
MAX_ZLIB_BYTES = 32768
MAX_LINES = 8192
MIN_LINES = 2000  # ниже — данные явно потерялись при редактировании

SECTION_HEADER_RE = re.compile(r"^#[a-z][a-z0-9-]*$")


class EmojiSuggestPackError(ValueError):
    """A fail-closed generator error (exit 2)."""


class EmojiSuggestGuardrailError(EmojiSuggestPackError):
    """A guardrail breach: too many bytes or lines, or suspiciously few (exit 4)."""


@dataclass(frozen=True)
class SuggestTable:
    text: str
    data: bytes
    line_count: int
    ru_entries: int
    tt_entries: int
    concept_count: int  # distinct (emoji, language) pairs

    @property
    def byte_size(self) -> int:
        return len(self.data)

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.data).hexdigest()

    @property
    def zlib_size(self) -> int:
        return len(zlib.compress(self.data, 9))


def read_panel_sequences(path: Path) -> tuple[str, ...]:
    """Reads the panel asset and returns its sequences in file order."""
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError as error:
        raise EmojiSuggestPackError(f"{path.name}: input is not valid UTF-8") from error
    sequences: list[str] = []
    seen: set[str] = set()
    for raw_line in text.split("\n"):
        line = raw_line.rstrip("\r")
        if not line or SECTION_HEADER_RE.match(line):
            continue
        if line in seen:
            raise EmojiSuggestPackError(f"duplicate sequence in the panel asset: {line!r}")
        seen.add(line)
        sequences.append(line)
    if not sequences:
        raise EmojiSuggestPackError("the panel asset holds no sequences")
    return tuple(sequences)


def read_data(path: Path) -> dict[tuple[str, str], str]:
    """Reads the curated TSV into a (language, word) -> emoji map; fail-closed.

    Blank lines and ``#`` comment lines are skipped. Every data line must be
    exactly ``<emoji>\\t<language>\\t<word>`` with a panel sequence, a known
    language tag and a word that :func:`dictionary_coverage.normalize_word`
    accepts unchanged (NFC, lowercase, alphabet of the language). Duplicate
    rows, conflicting rows (same word, different emoji) and denylisted words
    fail the build.
    """
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError as error:
        raise EmojiSuggestPackError(f"{path.name}: input is not valid UTF-8") from error
    mapping: dict[tuple[str, str], str] = {}
    rows: set[tuple[str, str, str]] = set()
    for lineno, raw_line in enumerate(text.split("\n"), start=1):
        line = raw_line.rstrip("\r")
        if not line or line.startswith("#"):
            continue
        where = f"{path.name}:{lineno}"
        if line.count("\t") != 2:
            raise EmojiSuggestPackError(f"{where}: expected exactly two tabs")
        emoji, lang, word = line.split("\t")
        if not emoji or not word:
            raise EmojiSuggestPackError(f"{where}: empty emoji or word")
        alphabet = LANG_ALPHABETS.get(lang)
        if alphabet is None:
            raise EmojiSuggestPackError(
                f"{where}: unknown language {lang!r} (expected one of {sorted(LANG_ALPHABETS)})"
            )
        normalized, reason = dictionary_coverage.normalize_word(word, alphabet)
        if reason is not None or normalized != word:
            raise EmojiSuggestPackError(
                f"{where}: word {word!r} is not canonical for {lang}: {reason or 'not normalized'}"
            )
        key = (lang, word)
        if key in DENYLIST:
            raise EmojiSuggestPackError(
                f"{where}: {word!r} is on the polysemy denylist ({DENYLIST[key]})"
            )
        row = (emoji, lang, word)
        if row in rows:
            raise EmojiSuggestPackError(f"{where}: duplicate row")
        rows.add(row)
        if key in mapping and mapping[key] != emoji:
            raise EmojiSuggestPackError(
                f"{where}: {word!r} maps to both {mapping[key]} and {emoji} "
                "(one word yields exactly one emoji per language)"
            )
        mapping[key] = emoji
    if not mapping:
        raise EmojiSuggestPackError(f"{path.name}: the data file holds no rows")
    return mapping


def build_table(
    mapping: dict[tuple[str, str], str],
    panel_sequences: Sequence[str],
    *,
    max_bytes: int,
    max_zlib_bytes: int,
    max_lines: int,
    min_lines: int,
) -> SuggestTable:
    """Composes the deterministic asset: one line per word, sorted by (language, word)."""
    panel = set(panel_sequences)
    lines: list[str] = []
    for (lang, word), emoji in sorted(mapping.items()):
        if emoji not in panel:
            raise EmojiSuggestPackError(
                f"emoji {emoji!r} for {lang}:{word!r} is not in the panel asset"
            )
        line = f"{lang}\t{word}\t{emoji}"
        if "\n" in line or line.count("\t") != 2:
            raise EmojiSuggestPackError(f"malformed table line for {lang}:{word!r}")
        lines.append(line)
    text = "\n".join(lines) + "\n"
    data = text.encode("utf-8")
    table = SuggestTable(
        text=text,
        data=data,
        line_count=len(lines),
        ru_entries=sum(1 for lang, _ in mapping if lang == "ru"),
        tt_entries=sum(1 for lang, _ in mapping if lang == "tt"),
        concept_count=len({(emoji, lang) for (lang, _), emoji in mapping.items()}),
    )
    if table.byte_size > max_bytes:
        raise EmojiSuggestGuardrailError(
            f"asset is {table.byte_size} bytes, over the {max_bytes} guardrail"
        )
    if table.zlib_size > max_zlib_bytes:
        raise EmojiSuggestGuardrailError(
            f"zlib-compressed asset is {table.zlib_size} bytes, over the {max_zlib_bytes} guardrail"
        )
    if table.line_count > max_lines:
        raise EmojiSuggestGuardrailError(
            f"asset has {table.line_count} lines, over the {max_lines} guardrail"
        )
    if table.line_count < min_lines:
        raise EmojiSuggestGuardrailError(
            f"asset has {table.line_count} lines, below the {min_lines} floor"
        )
    return table


def write_atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    handle = tempfile.NamedTemporaryFile(
        dir=str(path.parent), delete=False, prefix=path.name, suffix=".tmp"
    )
    try:
        with handle:
            handle.write(data)
        Path(handle.name).replace(path)
    except BaseException:
        Path(handle.name).unlink(missing_ok=True)
        raise


def create_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    build = subparsers.add_parser("build", help="build the emoji-suggest asset")
    build.add_argument("--data", type=Path, required=True,
                       help="curated TSV: emoji<TAB>language<TAB>word")
    build.add_argument("--panel-asset", type=Path, required=True)
    build.add_argument("--output", type=Path, required=True)
    return parser


def _print_json(payload: dict) -> None:
    print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))


def main(argv: Sequence[str] | None = None) -> int:
    args = create_argument_parser().parse_args(argv)
    try:
        if args.command == "build":
            mapping = read_data(args.data)
            panel_sequences = read_panel_sequences(args.panel_asset)
            table = build_table(
                mapping,
                panel_sequences,
                max_bytes=MAX_ASSET_BYTES,
                max_zlib_bytes=MAX_ZLIB_BYTES,
                max_lines=MAX_LINES,
                min_lines=MIN_LINES,
            )
            write_atomic(args.output, table.data)
            _print_json(
                {
                    "asset_bytes": table.byte_size,
                    "asset_sha256": table.sha256,
                    "concept_count": table.concept_count,
                    "line_count": table.line_count,
                    "ru_entries": table.ru_entries,
                    "tt_entries": table.tt_entries,
                    "zlib_bytes": table.zlib_size,
                }
            )
        else:  # pragma: no cover
            raise AssertionError(args.command)
    except EmojiSuggestGuardrailError as error:
        print(f"error: {error}", file=sys.stderr)
        return 4
    except (EmojiSuggestPackError, OSError, UnicodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
