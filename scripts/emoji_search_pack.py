#!/usr/bin/env python3
"""Build the Tatar Keyboard emoji-search index from CLDR annotations.

The tool uses only the Python standard library. The inputs are the locally
downloaded CLDR 44 annotation files (``common/annotations/{ru,en}.xml`` and
``common/annotationsDerived/{ru,en}.xml``) with pinned SHA-256s, plus the
already-generated emoji panel asset ``emoji_set_v1.txt``; the output is
``app/src/main/assets/emoji/emoji_search_v1.txt``, a deterministic UTF-8/LF text
asset (data, not code).

One output line per emoji sequence that has at least one keyword::

    <sequence>\\t<name>\\t<keyword> <keyword> ...

Field 1 is the sequence exactly as it appears in ``emoji_set_v1.txt`` — so the
index can never offer an emoji the panel cannot draw. Field 2 is the Russian
short name (CLDR ``type="tts"``), used for ranking and shown to nobody. Field 3
is the space-separated, lowercased, de-duplicated union of the Russian and
English keywords and names. Russian comes first, which is also the search
priority.

The generator is fail-closed. It exits with a nonzero status and writes no
partial asset when:

* an input SHA-256 does not match its pin,
* an input is not valid UTF-8 or not parseable,
* a sequence would produce a line containing a tab or a newline,
* the coverage of the panel set drops below the pinned floor, or
* a guardrail is breached (asset > 262144 bytes or > 1400 lines).

CLDR strips U+FE0F (VARIATION SELECTOR-16) from its ``cp`` attributes, so a
sequence is looked up with U+FE0F removed. Tatar is deliberately absent: CLDR 44
ships eight punctuation annotations for ``tt`` and no emoji at all, so a Tatar
emoji index cannot be derived from it (see docs/EMOJI-PANEL-TELEGRAM.md).
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

# Pinned input identity: CLDR release-44 annotation files. The SHA-256 is the
# binding pin; a change to any of them is a written decision, not a silent bump.
EXPECTED_INPUT_SHA256 = {
    "ru": "6de7170ec03f1d685b1c30d71c391d5893e366c57e9c9db37f687989893771cb",
    "en": "13db8bb0a85a1ab9c46dd70f6170d72e7938063eb04cb2ee65e46d0378cfebf6",
    "derived-ru": "c672c2fc917ea132f4a9358413e0b0dda6e3c292bc441bcf5b4b09683a7b391a",
    "derived-en": "cfcadefede165fdad566f32894d4a7975a0bce76ca3d6758a5f8f448e550e6b2",
}

CLDR_VERSION = "44"

# Guardrails. A change to any number is a written decision, not a silent bump.
MAX_ASSET_BYTES = 262144
MAX_LINES = 1400

# Coverage floor: how many of the panel's sequences must carry Russian keywords.
# The only expected gap is a sequence CLDR has no annotation for at all.
MIN_RUSSIAN_COVERAGE = 0.99

VARIATION_SELECTOR_16 = "️"

SECTION_HEADER_RE = re.compile(r"^#[a-z][a-z0-9-]*$")

ANNOTATION_RE = re.compile(
    r'<annotation cp="([^"]*)"(?:\s+type="(tts)")?\s*>([^<]*)</annotation>'
)

XML_ENTITIES = (
    ("&lt;", "<"),
    ("&gt;", ">"),
    ("&quot;", '"'),
    ("&apos;", "'"),
    ("&amp;", "&"),
)


class EmojiSearchPackError(ValueError):
    """A fail-closed generator error (exit 2)."""


class EmojiSearchGuardrailError(EmojiSearchPackError):
    """A guardrail breach: too many bytes, too many lines, or too little coverage (exit 4)."""


@dataclass(frozen=True)
class SearchIndex:
    text: str
    data: bytes
    line_count: int
    russian_coverage: float
    sequence_count: int

    @property
    def byte_size(self) -> int:
        return len(self.data)

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.data).hexdigest()


def unescape(value: str) -> str:
    """Undo the five XML entities CLDR uses. ``&amp;`` is undone last on purpose."""
    for entity, char in XML_ENTITIES:
        value = value.replace(entity, char)
    return value


def read_input_text(path: Path, *, expected_sha256: str) -> str:
    raw = path.read_bytes()
    actual_sha = hashlib.sha256(raw).hexdigest()
    if actual_sha != expected_sha256:
        raise EmojiSearchPackError(
            f"{path.name}: SHA-256 {actual_sha} does not match pinned {expected_sha256}"
        )
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise EmojiSearchPackError(f"{path.name}: input is not valid UTF-8") from error


def parse_annotations(text: str) -> tuple[dict[str, list[str]], dict[str, str]]:
    """Splits a CLDR annotation file into keyword lists and short names."""
    keywords: dict[str, list[str]] = {}
    names: dict[str, str] = {}
    for match in ANNOTATION_RE.finditer(text):
        cp = unescape(match.group(1))
        value = unescape(match.group(3)).strip()
        if not cp or not value:
            continue
        if match.group(2) == "tts":
            names[cp] = value
        else:
            keywords[cp] = [part.strip() for part in value.split("|") if part.strip()]
    if not keywords and not names:
        raise EmojiSearchPackError("no annotations found; input is not a CLDR annotation file")
    return keywords, names


def read_panel_sequences(path: Path) -> tuple[str, ...]:
    """Reads the panel asset and returns its sequences in file order."""
    text = path.read_text(encoding="utf-8")
    sequences: list[str] = []
    seen: set[str] = set()
    for raw_line in text.split("\n"):
        line = raw_line.rstrip("\r")
        if not line or SECTION_HEADER_RE.match(line):
            continue
        if line in seen:
            raise EmojiSearchPackError(f"duplicate sequence in the panel asset: {line!r}")
        seen.add(line)
        sequences.append(line)
    if not sequences:
        raise EmojiSearchPackError("the panel asset holds no sequences")
    return tuple(sequences)


def lookup_key(sequence: str) -> str:
    """CLDR strips U+FE0F from its cp attributes, so a lookup strips it too."""
    return sequence.replace(VARIATION_SELECTOR_16, "")


def build_index(
    sequences: Sequence[str],
    sources: Sequence[tuple[dict[str, list[str]], dict[str, str]]],
    *,
    max_bytes: int,
    max_lines: int,
    min_russian_coverage: float,
) -> SearchIndex:
    """Composes one index line per sequence that has at least one keyword.

    ``sources`` is ordered: Russian first (its name becomes the ranking name and
    its keywords come first), English after it.
    """
    lines: list[str] = []
    with_russian = 0
    russian_keywords, russian_names = sources[0]
    for sequence in sequences:
        key = lookup_key(sequence)
        words: list[str] = []
        for keywords, names in sources:
            for word in [names.get(key, "")] + keywords.get(key, []):
                word = " ".join(word.lower().split())
                if word and word not in words:
                    words.append(word)
        if not words:
            continue
        if key in russian_keywords or key in russian_names:
            with_russian += 1
        name = russian_names.get(key, words[0])
        line = f"{sequence}\t{name.lower()}\t{' '.join(words)}"
        if "\n" in line or line.count("\t") != 2:
            raise EmojiSearchPackError(f"malformed index line for {sequence!r}")
        lines.append(line)

    text = "\n".join(lines) + "\n"
    data = text.encode("utf-8")
    coverage = with_russian / len(sequences)
    if len(data) > max_bytes:
        raise EmojiSearchGuardrailError(f"asset is {len(data)} bytes, over the {max_bytes} guardrail")
    if len(lines) > max_lines:
        raise EmojiSearchGuardrailError(f"asset has {len(lines)} lines, over the {max_lines} guardrail")
    if coverage < min_russian_coverage:
        raise EmojiSearchGuardrailError(
            f"Russian coverage {coverage:.4f} is below the {min_russian_coverage} floor"
        )
    return SearchIndex(
        text=text,
        data=data,
        line_count=len(lines),
        russian_coverage=coverage,
        sequence_count=len(sequences),
    )


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
    build = subparsers.add_parser("build", help="build the emoji-search index asset")
    build.add_argument("--cldr-dir", type=Path, required=True,
                       help="directory holding ru.xml, en.xml, derived-ru.xml, derived-en.xml")
    build.add_argument("--panel-asset", type=Path, required=True)
    build.add_argument("--output", type=Path, required=True)
    return parser


def _print_json(payload: dict) -> None:
    print(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True))


def main(argv: Sequence[str] | None = None) -> int:
    args = create_argument_parser().parse_args(argv)
    try:
        if args.command == "build":
            parsed = {}
            for name, sha in EXPECTED_INPUT_SHA256.items():
                parsed[name] = parse_annotations(
                    read_input_text(args.cldr_dir / f"{name}.xml", expected_sha256=sha)
                )
            # Derived annotations only fill gaps (keycaps); the base file wins.
            merged = {}
            for lang in ("ru", "en"):
                keywords = dict(parsed[f"derived-{lang}"][0])
                keywords.update(parsed[lang][0])
                names = dict(parsed[f"derived-{lang}"][1])
                names.update(parsed[lang][1])
                merged[lang] = (keywords, names)
            index = build_index(
                read_panel_sequences(args.panel_asset),
                (merged["ru"], merged["en"]),
                max_bytes=MAX_ASSET_BYTES,
                max_lines=MAX_LINES,
                min_russian_coverage=MIN_RUSSIAN_COVERAGE,
            )
            write_atomic(args.output, index.data)
            _print_json(
                {
                    "asset_bytes": index.byte_size,
                    "asset_sha256": index.sha256,
                    "cldr_version": CLDR_VERSION,
                    "line_count": index.line_count,
                    "russian_coverage": round(index.russian_coverage, 4),
                    "sequence_count": index.sequence_count,
                }
            )
        else:  # pragma: no cover
            raise AssertionError(args.command)
    except EmojiSearchGuardrailError as error:
        print(f"error: {error}", file=sys.stderr)
        return 4
    except (EmojiSearchPackError, OSError, UnicodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
