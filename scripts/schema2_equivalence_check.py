#!/usr/bin/env python3
"""Доказательство lossless-эквивалентности TATDICT schema 1 → schema 2 (SIZE-1).

Сравнивает два ассета одного словаря (например, поставляемый v1 и его перепаковку
``dictionary_pack.py repack --schema 2``) на трёх уровнях:

  1. ПОЛНЫЙ РАЗБОР: (слова, порядок, частоты) обоих файлов обязаны совпасть
     точно — это полное доказательство тождества данных.
  2. НЕЗАВИСИМАЯ МОДЕЛЬ ЧИТАТЕЛЯ: V2BlockIndex ниже повторяет алгоритм доступа
     Kotlin-читателя schema 2 (бинпоиск по первым словам блоков, последовательный
     декод внутри блока), намеренно НЕ пользуясь разобранным списком слов из
     валидатора. Его выдача по КАЖДОМУ distinct-префиксу длины 1..5 всех слов
     сверяется с ``prefix_candidates`` по разбору v1: слова, порядок и частоты.
  3. Сводка печатается JSON-строкой RESULT|... для протокола.

Использование:

    python3 scripts/schema2_equivalence_check.py \
        --v1 app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib \
        --v2 /tmp/v2/tat.tdict.zlib --language tat

Коды выхода: 0 — эквивалентны, 1 — расхождение, 2 — входы/окружение.
Только stdlib.
"""

from __future__ import annotations

import argparse
import bisect
import json
import struct
import sys
from pathlib import Path
from typing import Sequence

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import dictionary_coverage as coverage  # noqa: E402
import dictionary_pack as dp  # noqa: E402


class V2BlockIndex:
    """Независимая модель читателя schema 2: бинпоиск по блокам + декод блока.

    Слова хранятся как байты; сравнение — побайтовое беззнаковое, как у
    ByteBuffer-читателя (для UTF-8 оно совпадает с кодпоинтным порядком).
    """

    def __init__(self, raw: bytes) -> None:
        (
            _magic,
            schema_id,
            _version,
            _header_size,
            _checksum_alg,
            entry_count,
            block_count,
            block_index_offset,
            _blocks_offset,
            _blocks_size,
            _file_size,
            _checksum,
        ) = dp.HEADER.unpack_from(raw)
        if schema_id != dp.SCHEMA_ID_V2:
            raise SystemExit(f"ожидался schema 2, получен {schema_id}")
        self.raw = raw
        self.entry_count = entry_count
        self.block_count = block_count
        self.block_index_offset = block_index_offset
        self.block_offsets = struct.unpack_from(
            f"<{block_count}I", raw, block_index_offset
        )
        # Первые слова блоков — для бинарного поиска.
        self.first_words = [self._first_word(b) for b in range(block_count)]

    def _first_word(self, block: int) -> bytes:
        offset = self.block_offsets[block]
        length = self.raw[offset]
        return self.raw[offset + 1 : offset + 1 + length]

    def decode_block(self, block: int) -> tuple[list[bytes], list[int]]:
        start = self.block_offsets[block]
        end = (
            self.block_offsets[block + 1]
            if block + 1 < self.block_count
            else len(self.raw)
        )
        count = min(dp.BLOCK_SIZE_V2, self.entry_count - block * dp.BLOCK_SIZE_V2)
        cursor = start
        first_length = self.raw[cursor]
        cursor += 1
        first = self.raw[cursor : cursor + first_length]
        cursor += first_length
        words = [first]
        for _ in range(count - 1):
            prefix, cursor = dp._decode_varint(self.raw, cursor, end)
            suffix_length = self.raw[cursor]
            cursor += 1
            words.append(first[:prefix] + self.raw[cursor : cursor + suffix_length])
            cursor += suffix_length
        frequencies = []
        for _ in range(count):
            frequency, cursor = dp._decode_varint(self.raw, cursor, end)
            frequencies.append(frequency)
        assert cursor == end
        return words, frequencies

    def word_at(self, index: int) -> tuple[bytes, int]:
        block, position = divmod(index, dp.BLOCK_SIZE_V2)
        words, frequencies = self.decode_block(block)
        return words[position], frequencies[position]

    def lower_bound(self, prefix: bytes) -> int:
        """Индекс первого слова >= prefix — бинпоиск по блокам, затем в блоке."""
        block = bisect.bisect_right(self.first_words, prefix) - 1
        if block < 0:
            return 0
        words, _ = self.decode_block(block)
        position = bisect.bisect_left(words, prefix)
        if position < len(words):
            return block * dp.BLOCK_SIZE_V2 + position
        return min((block + 1) * dp.BLOCK_SIZE_V2, self.entry_count)

    def candidates(self, prefix: bytes, top: int) -> list[tuple[bytes, int]]:
        start = self.lower_bound(prefix)
        matches: list[tuple[bytes, int]] = []
        index = start
        block, position = divmod(index, dp.BLOCK_SIZE_V2)
        words: list[bytes] = []
        frequencies: list[int] = []
        while index < self.entry_count:
            if not words:
                words, frequencies = self.decode_block(block)
            word = words[position]
            if not word.startswith(prefix):
                break
            if word != prefix:
                matches.append((word, frequencies[position]))
            index += 1
            position += 1
            if position == len(words):
                block += 1
                position = 0
                words = []
        return sorted(matches, key=lambda item: (-item[1], item[0]))[:top]


def run(v1_path: Path, v2_path: Path, tag: str, top: int) -> int:
    language = coverage.language_for(tag)
    parsed_v1 = dp.validate_asset(v1_path.read_bytes(), language=language)
    parsed_v2 = dp.validate_asset(v2_path.read_bytes(), language=language)

    # Уровень 1: полное тождество разбора.
    if parsed_v1.words != parsed_v2.words:
        print("RESULT|FAIL|words-differ", file=sys.stderr)
        return 1
    if parsed_v1.frequencies != parsed_v2.frequencies:
        print("RESULT|FAIL|frequencies-differ", file=sys.stderr)
        return 1

    # Уровень 2: все distinct-префиксы длины 1..5 через независимую модель.
    index = V2BlockIndex(parsed_v2.raw)
    prefixes: set[str] = set()
    for word in parsed_v1.words:
        for length in range(1, min(5, len(word)) + 1):
            prefixes.add(word[:length])
    checked = 0
    for prefix in sorted(prefixes):
        expected = dp.prefix_candidates(parsed_v1, prefix, top, language)
        actual = [
            (word.decode("utf-8"), frequency)
            for word, frequency in index.candidates(prefix.encode("utf-8"), top)
        ]
        if actual != expected:
            print(
                f"RESULT|FAIL|prefix {prefix!r}: v1={expected} v2={actual}",
                file=sys.stderr,
            )
            return 1
        checked += 1

    # Случайный точечный доступ word_at против плоского разбора.
    step = max(1, parsed_v1.entry_count // 10_000)
    for i in range(0, parsed_v1.entry_count, step):
        word, frequency = index.word_at(i)
        if word.decode("utf-8") != parsed_v1.words[i]:
            print(f"RESULT|FAIL|word_at({i})", file=sys.stderr)
            return 1
        if frequency != parsed_v1.frequencies[i]:
            print(f"RESULT|FAIL|frequency_at({i})", file=sys.stderr)
            return 1

    print(
        json.dumps(
            {
                "entries": parsed_v1.entry_count,
                "prefixes_checked": checked,
                "top": top,
                "v1_compressed": v1_path.stat().st_size,
                "v2_compressed": v2_path.stat().st_size,
                "v1_raw": len(parsed_v1.raw),
                "v2_raw": len(parsed_v2.raw),
            },
            ensure_ascii=False,
            sort_keys=True,
        )
    )
    print(f"RESULT|PASS|{tag}|prefixes={checked}|entries={parsed_v1.entry_count}")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--v1", required=True, type=Path)
    parser.add_argument("--v2", required=True, type=Path)
    parser.add_argument(
        "--language", choices=sorted(coverage.LANGUAGES), required=True
    )
    parser.add_argument("--top", type=int, default=3)
    args = parser.parse_args(argv)
    for path in (args.v1, args.v2):
        if not path.is_file():
            print(f"error: нет файла {path}", file=sys.stderr)
            return 2
    return run(args.v1, args.v2, args.language, args.top)


if __name__ == "__main__":
    raise SystemExit(main())
