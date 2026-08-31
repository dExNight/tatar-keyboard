#!/usr/bin/env python3
"""Tests for scripts/rebuild_assets.py — the dictionary+bigram rebuild orchestrator.

Everything runs on synthetic fixtures in a temporary directory: a handful of Tatar words
instead of a 100k dictionary, a two-head bigram table instead of 518 KB. What is pinned
here is not the data but the CONTRACT of the tool: it reads pins from the Kotlin files
rather than duplicating them, it rewrites them byte-carefully, and `--check` is green on
a consistent set and red — with the right diagnosis — on a diverged one.
"""

from __future__ import annotations

import io
import json
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPOSITORY_ROOT / "scripts"))

import bigram_asset_pack  # noqa: E402
import dictionary_pack  # noqa: E402
import rebuild_assets  # noqa: E402

# Words of the synthetic dictionary, most frequent first. The alphabet is the Tatar one:
# every letter here is also valid for the shipped assets, so the same validators apply.
WORDS = ["мәхәббәт", "китап", "су", "әни", "әти", "өлкә"]
FREQUENCIES = [900, 800, 700, 600, 500, 400]


def build_dictionary_asset(directory: Path, words=WORDS, frequencies=FREQUENCIES) -> Path:
    words_path = directory / "words.txt"
    words_path.write_text(
        "".join(
            f"{index}\t{word}\t{frequency}\n"
            for index, (word, frequency) in enumerate(zip(words, frequencies), start=1)
        ),
        encoding="utf-8",
    )
    built = dictionary_pack.build_dictionary([words_path], len(words))
    asset_path = directory / "tatar_top100k_v1.tdict.zlib"
    asset_path.write_bytes(built.asset)
    return asset_path


def build_bigram_asset(directory: Path, heads, table, successes_per_head=2) -> Path:
    result = bigram_asset_pack.pack_bigram_table(heads, table, successes_per_head)
    asset_path = directory / "tatar_bigrams_v1.tatbigr.zlib"
    asset_path.write_bytes(result.compressed)
    return asset_path


SHA0 = "0" * 64

DICT_BLOCK = """\
        val {spec} = DictionaryArtifactSpec(
            family = "{family}",
            expectedCompressedSize = 1,
            expectedCompressedSha256 =
                "{sha}",
            expectedRawSize = 72,
            expectedRawSha256 =
                "{sha}",
            expectedEntryCount = 1,
        )"""

BIGRAM_BLOCK = """\
        val {spec} = BigramArtifactSpec(
            family = "{family}",
            expectedCompressedSize = 1,
            expectedCompressedSha256 =
                "{sha}",
            expectedRawSize = 96,
            expectedRawSha256 =
                "{sha}",
            expectedHeadCount = 1,
        )"""


def write_fake_contracts(root: Path) -> None:
    dict_contract = root / rebuild_assets.DICT_CONTRACT
    dict_contract.parent.mkdir(parents=True, exist_ok=True)
    dict_contract.write_text(
        "// шапка, которая не должна пострадать\n"
        + DICT_BLOCK.format(spec="TATAR_TOP100K_V1", family="tatar_top100k", sha=SHA0)
        + "\n\n// комментарий между блоками\n"
        + DICT_BLOCK.format(spec="RUSSIAN_TOP100K_V1", family="russian_top100k", sha=SHA0)
        + "\n",
        encoding="utf-8",
    )
    bigram_contract = root / rebuild_assets.BIGRAM_CONTRACT
    bigram_contract.parent.mkdir(parents=True, exist_ok=True)
    bigram_contract.write_text(
        BIGRAM_BLOCK.format(spec="TATAR_BIGRAMS_V1", family="tatar_bigrams", sha=SHA0)
        + "\n",
        encoding="utf-8",
    )


def pin_everything(root: Path, dictionary: rebuild_assets.DictionaryAsset,
                   bigram: rebuild_assets.BigramAsset) -> None:
    """What the rebuild's step 3 does: measure the assets, write the pins."""
    rebuild_assets.write_pins(
        root / rebuild_assets.DICT_CONTRACT,
        {dictionary.spec: rebuild_assets.measure_dictionary(root / dictionary.asset,
                                                            dictionary.tag)},
        "DictionaryArtifactSpec",
        "expectedEntryCount",
    )
    rebuild_assets.write_pins(
        root / rebuild_assets.BIGRAM_CONTRACT,
        {bigram.spec: rebuild_assets.measure_bigram(root / bigram.asset)},
        "BigramArtifactSpec",
        "expectedHeadCount",
    )


class FakeTreeTest(unittest.TestCase):
    """A fake repository root: one Tatar dictionary, one bigram table, both contracts."""

    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        write_fake_contracts(self.root)
        asset_dir = self.root / "app/src/main/assets/dictionaries"
        asset_dir.mkdir(parents=True)
        build_dictionary_asset(asset_dir)
        bigram_dir = self.root / "app/src/main/assets/bigrams"
        bigram_dir.mkdir(parents=True)
        # Heads = the top-3 by frequency, each with a pair: the consistent baseline.
        self.table = {word: [(WORDS[0], 10)] for word in WORDS[:3]}
        build_bigram_asset(bigram_dir, WORDS[:3], self.table)
        self.dictionary = rebuild_assets.DictionaryAsset(
            tag="tat", spec="TATAR_TOP100K_V1",
            asset="app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib",
        )
        self.bigram = rebuild_assets.BigramAsset(
            tag="tat", spec="TATAR_BIGRAMS_V1",
            asset="app/src/main/assets/bigrams/tatar_bigrams_v1.tatbigr.zlib",
            dictionary="tat", heads=3, successes_per_head=2,
            extra_heads=None, train=(),
        )
        self._saved = (rebuild_assets.DICTIONARIES, rebuild_assets.BIGRAMS)
        rebuild_assets.DICTIONARIES = (self.dictionary,)
        rebuild_assets.BIGRAMS = (self.bigram,)
        self.addCleanup(self._restore)

    def _restore(self):
        rebuild_assets.DICTIONARIES, rebuild_assets.BIGRAMS = self._saved

    def run_check(self, known_drift=None) -> tuple[int, dict]:
        stream = io.StringIO()
        code = rebuild_assets.run_check(self.root, known_drift, stream)
        return code, json.loads(stream.getvalue())

    def write_known_drift(self, data) -> Path:
        path = self.root / "known.json"
        path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
        return path


class PinsIoTest(FakeTreeTest):
    def test_written_pins_read_back_exactly(self):
        pins = rebuild_assets.measure_dictionary(
            self.root / self.dictionary.asset, "tat")
        pin_everything(self.root, self.dictionary, self.bigram)
        read_back = rebuild_assets.read_pins(
            self.root / rebuild_assets.DICT_CONTRACT,
            "TATAR_TOP100K_V1", "DictionaryArtifactSpec", "expectedEntryCount")
        self.assertEqual(pins, read_back)

    def test_rewrite_touches_only_the_target_block(self):
        pin_everything(self.root, self.dictionary, self.bigram)
        text = (self.root / rebuild_assets.DICT_CONTRACT).read_text(encoding="utf-8")
        self.assertIn("// шапка, которая не должна пострадать", text)
        self.assertIn("// комментарий между блоками", text)
        # The Russian block was not in the update and still holds the dummy pins.
        russian = rebuild_assets.read_pins(
            self.root / rebuild_assets.DICT_CONTRACT,
            "RUSSIAN_TOP100K_V1", "DictionaryArtifactSpec", "expectedEntryCount")
        self.assertEqual(1, russian.count)
        self.assertEqual(SHA0, russian.raw_sha256)

    def test_sizes_are_written_with_underscore_separators(self):
        contract = self.root / rebuild_assets.DICT_CONTRACT
        pins = rebuild_assets.Pins(1_234_567, "a" * 64, 2_345_678, "b" * 64, 100_000)
        rebuild_assets.write_pins(contract, {"TATAR_TOP100K_V1": pins},
                                  "DictionaryArtifactSpec", "expectedEntryCount")
        text = contract.read_text(encoding="utf-8")
        self.assertIn("expectedCompressedSize = 1_234_567,", text)
        self.assertIn("expectedEntryCount = 100_000,", text)

    def test_a_block_that_disappeared_fails_closed(self):
        contract = self.root / rebuild_assets.DICT_CONTRACT
        contract.write_text("// пусто\n", encoding="utf-8")
        with self.assertRaises(rebuild_assets.ContractError):
            rebuild_assets.read_pins(contract, "TATAR_TOP100K_V1",
                                     "DictionaryArtifactSpec", "expectedEntryCount")


class CheckTest(FakeTreeTest):
    def test_green_on_a_consistent_set(self):
        pin_everything(self.root, self.dictionary, self.bigram)
        code, report = self.run_check()
        self.assertEqual(0, code, report)
        self.assertTrue(report["ok"])
        self.assertEqual("ok", report["dictionaries"]["tat"]["verdict"])
        self.assertEqual("ok", report["bigrams"]["tat"]["verdict"])

    def test_red_when_the_asset_moved_but_the_pins_did_not(self):
        pin_everything(self.root, self.dictionary, self.bigram)
        # Rebuild the dictionary with different frequencies: valid asset, stale pins.
        build_dictionary_asset(self.root / "app/src/main/assets/dictionaries",
                               frequencies=[100 + i for i in range(len(WORDS))])
        code, report = self.run_check()
        self.assertEqual(1, code)
        self.assertEqual("mismatch", report["dictionaries"]["tat"]["verdict"])
        self.assertTrue(report["dictionaries"]["tat"]["problems"])

    def test_a_head_outside_the_dictionary_is_never_allowlisted(self):
        pin_everything(self.root, self.dictionary, self.bigram)
        table = dict(self.table)
        table["дус"] = [(WORDS[0], 5)]  # «дус» в словаре нет
        build_bigram_asset(self.root / "app/src/main/assets/bigrams",
                           WORDS[:3] + ["дус"], table)
        pin_everything(self.root, self.dictionary, self.bigram)  # пины честные
        code, report = self.run_check()
        self.assertEqual(1, code)
        drift = report["bigrams"]["tat"]["drift"]
        self.assertEqual(1, drift["heads_outside_dictionary"])
        self.assertIn("дус", report["bigrams"]["tat"]["problems"][0])
        # Даже точная запись в файле известных расхождений это не разрешает.
        known = self.write_known_drift({
            "bigrams/tatar_bigrams_v1.tatbigr.zlib": {
                "missing_top_heads": 0, "unexpected_heads": 1, "reason": "тест"}})
        code, _report = self.run_check(known)
        self.assertEqual(1, code)

    def test_drift_is_counted_in_both_directions(self):
        # Таблица упакована с головами по СТАРОМУ порядку частот: «өлкә» вместо «мәхәббәт».
        build_bigram_asset(self.root / "app/src/main/assets/bigrams",
                           [WORDS[5], WORDS[1], WORDS[2]],
                           {w: [(WORDS[1], 7)] for w in (WORDS[5], WORDS[1], WORDS[2])})
        pin_everything(self.root, self.dictionary, self.bigram)
        code, report = self.run_check()
        self.assertEqual(1, code)
        drift = report["bigrams"]["tat"]["drift"]
        self.assertEqual(1, drift["missing_top_heads"])  # нет «мәхәббәт»
        self.assertEqual(1, drift["unexpected_heads"])  # лишняя «өлкә»
        self.assertEqual(0, drift["heads_outside_dictionary"])
        self.assertEqual("drift", report["bigrams"]["tat"]["verdict"])

    def test_known_drift_is_accepted_only_on_exact_numbers(self):
        build_bigram_asset(self.root / "app/src/main/assets/bigrams",
                           [WORDS[1], WORDS[2]],  # «мәхәббәт» не упакована
                           {w: [(WORDS[1], 7)] for w in (WORDS[1], WORDS[2])})
        pin_everything(self.root, self.dictionary, self.bigram)
        key = "bigrams/tatar_bigrams_v1.tatbigr.zlib"

        exact = self.write_known_drift(
            {key: {"missing_top_heads": 1, "unexpected_heads": 0, "reason": "тест"}})
        code, report = self.run_check(exact)
        self.assertEqual(0, code, report)
        self.assertEqual("known-drift", report["bigrams"]["tat"]["verdict"])

        wrong = self.write_known_drift(
            {key: {"missing_top_heads": 2, "unexpected_heads": 0, "reason": "тест"}})
        code, report = self.run_check(wrong)
        self.assertEqual(1, code)
        self.assertEqual("drift", report["bigrams"]["tat"]["verdict"])

    def test_a_stale_known_drift_entry_fails_the_check(self):
        pin_everything(self.root, self.dictionary, self.bigram)  # расхождения нет
        stale = self.write_known_drift({
            "bigrams/tatar_bigrams_v1.tatbigr.zlib": {
                "missing_top_heads": 1, "unexpected_heads": 0, "reason": "тест"}})
        code, report = self.run_check(stale)
        self.assertEqual(1, code)
        self.assertEqual("stale-known-drift", report["bigrams"]["tat"]["verdict"])

    def test_a_known_drift_entry_for_an_unknown_asset_fails(self):
        pin_everything(self.root, self.dictionary, self.bigram)
        alien = self.write_known_drift({
            "bigrams/no_such_asset.tatbigr.zlib": {
                "missing_top_heads": 1, "unexpected_heads": 1, "reason": "тест"}})
        code, _report = self.run_check(alien)
        self.assertEqual(1, code)

    def test_missing_contract_is_an_input_error(self):
        (self.root / rebuild_assets.BIGRAM_CONTRACT).unlink()
        stream = io.StringIO()
        code = rebuild_assets.run_check(self.root, None, stream)
        self.assertEqual(2, code)


class PackArgvTest(unittest.TestCase):
    """The canned pack commands are the mission's parameters, pinned verbatim."""

    def test_tatar_command(self):
        argv = rebuild_assets.bigram_pack_argv(
            Path("/root"), Path("/corpora"), Path("/work"), rebuild_assets.BIGRAMS[0])
        text = " ".join(argv)
        self.assertIn("--heads 10132", text)
        self.assertIn("--successes-per-head 4", text)
        self.assertIn("--extra-heads /root/scripts/bigram_extra_heads_tat.txt", text)
        self.assertIn("--language tat", text)
        self.assertIn("/corpora/tat_mixed_2015_1M-sentences.txt", text)
        self.assertIn("/corpora/tat_web_2018_1M-sentences.txt", text)

    def test_russian_command(self):
        argv = rebuild_assets.bigram_pack_argv(
            Path("/root"), Path("/corpora"), Path("/work"), rebuild_assets.BIGRAMS[1])
        text = " ".join(argv)
        self.assertIn("--heads 10000", text)
        self.assertIn("--successes-per-head 4", text)
        self.assertNotIn("--extra-heads", text)
        self.assertIn("--language rus", text)
        for name in ("rus_news_2022_1M", "rus_news_2019_1M", "rus_wikipedia_2021_1M"):
            self.assertIn(f"/corpora/{name}-sentences.txt", text)


class RealTreeTest(unittest.TestCase):
    """The committed assets against the committed contracts — the CI gate, as a test.

    The known-drift file makes this green today AND red the day the drift changes in
    either direction without a conscious edit of that file.
    """

    def test_committed_assets_match_their_pins(self):
        stream = io.StringIO()
        code = rebuild_assets.run_check(
            REPOSITORY_ROOT, REPOSITORY_ROOT / "scripts/known_asset_drift.json", stream)
        report = json.loads(stream.getvalue())
        self.assertEqual(0, code, json.dumps(report, ensure_ascii=False, indent=2))

    def test_strict_check_shows_the_known_russian_drift(self):
        stream = io.StringIO()
        code = rebuild_assets.run_check(REPOSITORY_ROOT, None, stream)
        report = json.loads(stream.getvalue())
        self.assertEqual(1, code)
        self.assertEqual("drift", report["bigrams"]["rus"]["verdict"])
        # Since the 2026-08-31 repack (docs/RUSSIAN-BIGRAMS-REPACK.md) the remaining 59 are
        # not a drift but the generator rule: conversational top-10k words with no
        # in-vocabulary pair in the Leipzig training corpora are dropped, not stored empty.
        self.assertEqual(59, report["bigrams"]["rus"]["drift"]["missing_top_heads"])
        self.assertEqual(0, report["bigrams"]["rus"]["drift"]["unexpected_heads"])


if __name__ == "__main__":
    unittest.main()
