/*
 * Copyright (C) 2026 Tatar Keyboard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import android.test.InstrumentationTestCase
import android.util.Log
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.DictionaryArtifactSpec
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.TdictValidator
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * E3b device-side compute harness. This is the ONLY instrumental test in the project: it exists so
 * that the E3b fuzzy compute latency can be measured on real hardware, which the host JVM
 * (`RealDictionaryPrefixIndexTest`) cannot stand in for.
 *
 * It runs the SAME prefix sample as `RealDictionaryPrefixIndexTest` (the 22 D1A query-review
 * prefixes) through the full fuzzy engine (edit classes #1 + #2 + #3, with a layout-derived
 * neighbour table) and prints p50 / p95 / maximum `compute` in milliseconds.
 *
 * It lives in `app/src/androidTest` and is packaged only into the debug androidTest APK — never the
 * release APK. No device is connected in this environment, so the measurement itself is NOT_COVERED
 * (see docs/DICTIONARY-E3.md); this class is required to COMPILE and to be assembled into the
 * androidTest APK (`:app:assembleDebugAndroidTest`).
 *
 * JUnit3 / legacy-runner style deliberately: the SDK's `android.test.InstrumentationTestRunner` and
 * `android.test.InstrumentationTestCase` resolve offline, so the harness needs no downloaded
 * androidx.test artifact.
 */
class E3bComputeInstrumentationTest : InstrumentationTestCase() {

    fun testComputeP50P95MaxOverReviewPrefixes() {
        val context = instrumentation.targetContext
        val spec = DictionaryArtifactSpec.TATAR_TOP100K_V1
        val rawFile = File.createTempFile("e3b-compute-", ".tdict", context.cacheDir)
        try {
            context.assets.open(spec.assetPath).use { input ->
                rawFile.outputStream().use { output -> TdictValidator().inflateAsset(input, output, spec) }
            }
            val validated = TdictValidator().validateRaw(rawFile, spec)
            val raw = rawFile.readBytes()
            val identity = DictionaryIdentity(
                spec.generation, validated.schemaId, validated.formatVersion, validated.rawSha256,
            )
            val index = TdictPrefixIndex.open(
                ByteBuffer.wrap(raw), identity, validated.entryCount, validated.rawSize,
            ) ?: error("index failed to open")
            index.updateKeyNeighbors(tatarNeighborTable())

            val prefixes = REVIEW_PREFIXES.map {
                ImmutableUtf8Prefix.copyOf(it.toByteArray(Charsets.UTF_8))
            }
            repeat(500) { index.lookup(prefixes[it % prefixes.size]) }

            val timings = LongArray(2_000)
            var consumed = 0
            for (sample in timings.indices) {
                val prefix = prefixes[sample % prefixes.size]
                val started = System.nanoTime()
                val results = index.lookup(prefix)
                timings[sample] = System.nanoTime() - started
                consumed = consumed xor results.size
            }
            timings.sort()
            val p50 = timings[timings.size / 2] / 1_000_000.0
            val p95 = timings[ceil(timings.size * 0.95).toInt() - 1] / 1_000_000.0
            val max = timings.last() / 1_000_000.0
            Log.i(
                TAG,
                "E3b device compute p50=${fmt(p50)} ms p95=${fmt(p95)} ms max=${fmt(max)} ms " +
                    "samples=${timings.size} consumed=$consumed",
            )
        } finally {
            rawFile.delete()
        }
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.ROOT, "%.3f", value)

    // Neighbour table reconstructed from rows_tatar.xml geometry, mirroring E3bTestFixtures. On a
    // real device the production path derives the same relation from the live keyboard via
    // KeyNeighborTableBuilder; either way the fuzzy compute measured here includes classes #2 & #3.
    private fun rowGeometry(row: Int): Pair<Int, Int> = when (row) {
        0 -> 16667 to 0
        1 -> 9091 to 0
        2 -> 9091 to 0
        3 -> 8711 to 10800
        else -> error("row $row")
    }

    private fun geoKey(base: Char, row: Int, col: Int, vararg partners: Char): KeyNeighborTable.RawKey {
        val (width, offset) = rowGeometry(row)
        val left = offset + col * width
        return KeyNeighborTable.RawKey(
            base.code, left, row, left + width, row + 1, IntArray(partners.size) { partners[it].code },
        )
    }

    private fun tatarNeighborTable(): KeyNeighborTable =
        KeyNeighborTable.build(
            "tt_RU", true,
            listOf(
                geoKey('ә', 0, 0), geoKey('ө', 0, 1), geoKey('ү', 0, 2),
                geoKey('җ', 0, 3), geoKey('ң', 0, 4), geoKey('һ', 0, 5),
                geoKey('й', 1, 0), geoKey('ц', 1, 1), geoKey('у', 1, 2, 'ү'),
                geoKey('к', 1, 3), geoKey('е', 1, 4, 'ё'), geoKey('н', 1, 5, 'ң'),
                geoKey('г', 1, 6, 'һ'), geoKey('ш', 1, 7), geoKey('щ', 1, 8),
                geoKey('з', 1, 9), geoKey('х', 1, 10, 'һ'),
                geoKey('ф', 2, 0), geoKey('ы', 2, 1), geoKey('в', 2, 2),
                geoKey('а', 2, 3, 'ә'), geoKey('п', 2, 4), geoKey('р', 2, 5),
                geoKey('о', 2, 6, 'ө'), geoKey('л', 2, 7), geoKey('д', 2, 8),
                geoKey('ж', 2, 9, 'җ'), geoKey('э', 2, 10, 'ә'),
                geoKey('я', 3, 0), geoKey('ч', 3, 1), geoKey('с', 3, 2),
                geoKey('м', 3, 3), geoKey('и', 3, 4), geoKey('т', 3, 5),
                geoKey('ь', 3, 6, 'ъ'), geoKey('б', 3, 7), geoKey('ю', 3, 8),
            ),
        )

    companion object {
        private const val TAG = "E3bCompute"

        // The same 22 prefixes as docs/DICTIONARY-D1A-QUERY-REVIEW.tsv (RealDictionaryPrefixIndexTest
        // reads them from disk on the host; on device they are inlined query data, not layout).
        private val REVIEW_PREFIXES = listOf(
            "сә", "рәх", "исәнм", "хәерл", "безн", "татарч", "кеш", "бал", "мәкт", "китап", "эшл",
            "йорт", "авыл", "шәһ", "вак", "көн", "тел", "гаил", "әни", "әти", "дус", "яң",
        )
    }
}
