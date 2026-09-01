#!/usr/bin/env python3
"""Доказательство lossless-эквивалентности TATBIGR schema 2 → schema 3 (SIZE-2).

Зеркало ``schema2_equivalence_check.py`` для биграммных таблиц. Сравнивает два
ассета одной таблицы (поставляемый schema 2 и его перепаковку
``bigram_asset_pack.py repack``) на двух уровнях:

  1. ПОЛНЫЙ РАЗБОР: для КАЖДОЙ головы (10 204 tat + 9 998 rus) список преемников
     обязан совпасть пословно и по порядку; наборы голов — точно.
  2. НЕЗАВИСИМАЯ МОДЕЛЬ ЧИТАТЕЛЯ: V3Index ниже повторяет алгоритм доступа
     Kotlin-читателя schema 3 (точечный поиск слова в словаре → бинпоиск блока
     по словарному индексу → потоковый декод дельт и varint-преемников),
     намеренно НЕ пользуясь результатом разбора из валидатора. Его выдача по
     каждой голове (и по выборке слов-не-голов, где читатель обязан молчать)
     сверяется с разбором v2.

Использование:

    python3 scripts/schema3_equivalence_check.py \
        --v2 app/src/main/assets/bigrams/tatar_bigrams_v1.tatbigr.zlib \
        --v3 /tmp/v3/tatar.tatbigr.zlib \
        --dictionary app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib \
        --language tat

Коды выхода: 0 — эквивалентны, 1 — расхождение, 2 — входы/окружение.
Только stdlib.
"""

from __future__ import annotations

import argparse
import bisect
import hashlib
import json
import sys
from pathlib import Path
from typing import Sequence

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import bigram_asset_pack as bp  # noqa: E402
import dictionary_coverage as coverage  # noqa: E402
import dictionary_pack as dp  # noqa: E402


class V3Index:
    """Независимая модель читателя schema 3: бинпоиск по блокам голов + потоковый
    декод, словарь — плоский список слов со своим бинпоиском (модель
    TdictPrefixIndex.indexOfWord/wordAt)."""

    def __init__(self, raw: bytes, dictionary_words: list[str]) -> None:
        (
            _magic,
            schema_id,
            _version,
            _header_size,
            _checksum_alg,
            head_count,
            _pair_count,
            block_count,
            block_index_offset,
            head_deltas_offset,
            head_deltas_size,
            counts_offset,
            success_ids_offset,
            success_ids_size,
            _file_size,
            _dict_sha,
            _reserved,
            _checksum,
        ) = bp.HEADER_V3.unpack_from(raw)
        if schema_id != bp.SCHEMA_ID_V3:
            raise SystemExit(f"ожидался schema 3, получен {schema_id}")
        self.raw = raw
        self.head_count = head_count
        self.block_count = block_count
        self.head_deltas_offset = head_deltas_offset
        self.head_deltas_size = head_deltas_size
        self.counts_offset = counts_offset
        self.success_ids_offset = success_ids_offset
        self.success_ids_size = success_ids_size
        self.records = [
            bp.BLOCK_RECORD_V3.unpack_from(
                raw, block_index_offset + bp.BLOCK_RECORD_V3.size * block
            )
            for block in range(block_count)
        ]
        self.first_indices = [record[0] for record in self.records]
        self.dictionary_words = dictionary_words
        self.dictionary_bytes = [word.encode("utf-8") for word in dictionary_words]

    def _index_of_word(self, query: bytes) -> int:
        position = bisect.bisect_left(self.dictionary_bytes, query)
        if position < len(self.dictionary_bytes) and self.dictionary_bytes[position] == query:
            return position
        return -1

    def predict(self, context: str, top: int = 3) -> list[str]:
        query_index = self._index_of_word(context.encode("utf-8"))
        if query_index < 0:
            return []
        # Бинпоиск блока: последний блок с firstDictIndex <= query_index.
        block = bisect.bisect_right(self.first_indices, query_index) - 1
        if block < 0:
            return []
        first_index, delta_offset, success_offset = self.records[block]
        first = block * bp.HEAD_BLOCK_V3
        last = min(first + bp.HEAD_BLOCK_V3, self.head_count)
        cursor = self.head_deltas_offset + delta_offset
        index = first_index
        position_in_block = 0
        found = -1
        for position_in_block, _head in enumerate(range(first, last)):
            if position_in_block > 0:
                delta, cursor = bp._decode_varint_v3(self.raw, cursor, len(self.raw))
                index += delta
            if index == query_index:
                found = position_in_block
                break
            if index > query_index:
                return []
        if found < 0:
            return []
        head = first + found
        # Пропустить преемников предыдущих голов блока.
        cursor = self.success_ids_offset + success_offset
        for previous in range(first, head):
            for _ in range(self.raw[self.counts_offset + previous]):
                _value, cursor = bp._decode_varint_v3(self.raw, cursor, len(self.raw))
        result = []
        for _ in range(min(top, self.raw[self.counts_offset + head])):
            success_index, cursor = bp._decode_varint_v3(self.raw, cursor, len(self.raw))
            result.append(self.dictionary_words[success_index])
        return result


def run(v2_path: Path, v3_path: Path, dictionary_path: Path, tag: str, top: int) -> int:
    language = coverage.language_for(tag)
    parsed_v2 = bp.validate_raw(bp.decompress(v2_path.read_bytes()))
    parsed_dictionary = dp.validate_asset(
        dictionary_path.read_bytes(), language=language
    )
    dictionary_words = list(parsed_dictionary.words)
    dictionary_sha = hashlib.sha256(parsed_dictionary.raw).digest()
    parsed_v3 = bp.validate_raw_v3(
        bp.decompress(v3_path.read_bytes()), dictionary_words, dictionary_sha
    )

    # Уровень 1: полное тождество разбора.
    if parsed_v3.head_words != parsed_v2.head_words:
        print("RESULT|FAIL|heads-differ", file=sys.stderr)
        return 1
    for head in parsed_v2.head_words:
        if parsed_v3.successes_by_head[head] != parsed_v2.successes_by_head[head]:
            print(f"RESULT|FAIL|successes-differ|{head!r}", file=sys.stderr)
            return 1

    # Уровень 2: независимая модель читателя — каждая голова + выборка не-голов.
    index = V3Index(bp.decompress(v3_path.read_bytes()), dictionary_words)
    for head in parsed_v2.head_words:
        expected = parsed_v2.successes_by_head[head][:top]
        actual = index.predict(head, top)
        if actual != expected:
            print(
                f"RESULT|FAIL|predict {head!r}: v2={expected} v3={actual}",
                file=sys.stderr,
            )
            return 1
    head_set = set(parsed_v2.head_words)
    non_heads = [word for word in dictionary_words if word not in head_set]
    step = max(1, len(non_heads) // 5_000)
    misses = 0
    for word in non_heads[::step]:
        if index.predict(word, top):
            print(f"RESULT|FAIL|non-head {word!r} predicted", file=sys.stderr)
            return 1
        misses += 1

    print(
        json.dumps(
            {
                "heads": len(parsed_v2.head_words),
                "non_head_misses_checked": misses,
                "pairs": sum(map(len, parsed_v2.successes_by_head.values())),
                "top": top,
                "v2_compressed": v2_path.stat().st_size,
                "v3_compressed": v3_path.stat().st_size,
                "v2_raw": len(bp.decompress(v2_path.read_bytes())),
                "v3_raw": len(bp.decompress(v3_path.read_bytes())),
            },
            ensure_ascii=False,
            sort_keys=True,
        )
    )
    print(f"RESULT|PASS|{tag}|heads={len(parsed_v2.head_words)}|misses={misses}")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--v2", required=True, type=Path)
    parser.add_argument("--v3", required=True, type=Path)
    parser.add_argument("--dictionary", required=True, type=Path)
    parser.add_argument(
        "--language", choices=sorted(coverage.LANGUAGES), required=True
    )
    parser.add_argument("--top", type=int, default=3)
    args = parser.parse_args(argv)
    for path in (args.v2, args.v3, args.dictionary):
        if not path.is_file():
            print(f"error: нет файла {path}", file=sys.stderr)
            return 2
    return run(args.v2, args.v3, args.dictionary, args.language, args.top)


if __name__ == "__main__":
    raise SystemExit(main())
