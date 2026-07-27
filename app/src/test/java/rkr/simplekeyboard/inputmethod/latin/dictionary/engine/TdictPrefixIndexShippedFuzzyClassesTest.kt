package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The E3b verdict as an executable contract (PROPOSALS.md, section "Контракт текста", line "Итог,
 * 2026-07-27"; docs/DICTIONARY-E3.md): the shipped fuzzy pass runs edit class #1 (long-press
 * partner) ONLY. Classes #2 (geometric neighbour) and #3 (transposition) are excluded from the live
 * path in EXACTLY ONE named place — [TdictPrefixIndex.SHIPPED_FUZZY_EDIT_CLASSES] — while their
 * generators remain in the tree as infrastructure (exercised directly by [FuzzyPrefixVariantsE3bTest]).
 *
 * This file carries the source-contract assertion (only class #1 is enabled, visible in that one
 * place) and functional assertions that, on prefixes where classes #2/#3 WOULD have produced
 * matching variants, the shipped result equals the class-#1-only result.
 */
class TdictPrefixIndexShippedFuzzyClassesTest {
    private val geometricTable = E3bTestFixtures.tatarNeighborTable()
    private val codePointScratch = IntArray(64)
    private val variantScratch = ByteArray(256)

    private fun index(entries: List<Pair<String, Long>>): TdictPrefixIndex {
        val index = EngineTestFixtures.index(entries)
        index.updateKeyNeighbors(geometricTable)
        return index
    }

    private fun lookup(index: TdictPrefixIndex, prefix: String): List<String> =
        index.lookup(ImmutableUtf8Prefix.copyOf(prefix.toByteArray(Charsets.UTF_8)))

    private fun longPressVariantsOf(prefix: String): List<String> {
        val collected = ArrayList<String>()
        val bytes = prefix.toByteArray(Charsets.UTF_8)
        FuzzyPrefixVariants.generateLongPressVariants(
            bytes, bytes.size, geometricTable, codePointScratch, variantScratch, 100,
        ) { v, len -> collected.add(String(v, 0, len, Charsets.UTF_8)) }
        return collected
    }

    private fun geometricVariantsOf(prefix: String): List<String> {
        val collected = ArrayList<String>()
        val bytes = prefix.toByteArray(Charsets.UTF_8)
        FuzzyPrefixVariants.generateGeometricVariants(
            bytes, bytes.size, geometricTable, codePointScratch, variantScratch, 100,
        ) { v, len -> collected.add(String(v, 0, len, Charsets.UTF_8)) }
        return collected
    }

    private fun transpositionVariantsOf(prefix: String): List<String> {
        val collected = ArrayList<String>()
        val bytes = prefix.toByteArray(Charsets.UTF_8)
        FuzzyPrefixVariants.generateTranspositionVariants(
            bytes, bytes.size, codePointScratch, variantScratch, 100,
        ) { v, len -> collected.add(String(v, 0, len, Charsets.UTF_8)) }
        return collected
    }

    /**
     * Source contract: the single named switch enables class #1 only. This is the one place the
     * live path consults, so classes #2 and #3 are provably absent from it.
     */
    @Test
    fun theShippedFuzzyPassEnablesOnlyEditClassOne() {
        val shipped = TdictPrefixIndex.SHIPPED_FUZZY_EDIT_CLASSES.toList()
        assertEquals(listOf(TdictPrefixIndex.EDIT_CLASS_LONG_PRESS), shipped)
        assertFalse("class #2 must not ship", shipped.contains(TdictPrefixIndex.EDIT_CLASS_GEOMETRIC))
        assertFalse("class #3 must not ship", shipped.contains(TdictPrefixIndex.EDIT_CLASS_TRANSPOSITION))
    }

    /**
     * Functional: on a prefix where class #2 WOULD produce a matching variant, the shipped result
     * equals the class-#1-only result. Premise proven directly from the generators — class #2 turns
     * "аит" into "кит" (а→к geometric), which the block of "китап" begins with, while class #1
     * produces no matching variant. The class-#1-only result is therefore empty, and so is the
     * shipped lookup.
     */
    @Test
    fun onAClass2PrefixTheShippedResultEqualsTheClass1OnlyResult() {
        assertTrue("premise: class #2 would match", geometricVariantsOf("аит").contains("кит"))
        assertFalse("premise: class #1 would not match", longPressVariantsOf("аит").contains("кит"))
        val index = index(listOf("китап" to 10L))
        assertEquals(emptyList<String>(), lookup(index, "аит"))
    }

    /**
     * Functional: on a prefix where class #3 WOULD produce a matching variant, the shipped result
     * equals the class-#1-only result. Class #3 turns "икт" into "кит" (adjacent swap), matching
     * "китап"; class #1 produces no matching variant, so both results are empty.
     */
    @Test
    fun onAClass3PrefixTheShippedResultEqualsTheClass1OnlyResult() {
        assertTrue("premise: class #3 would match", transpositionVariantsOf("икт").contains("кит"))
        assertFalse("premise: class #1 would not match", longPressVariantsOf("икт").contains("кит"))
        val index = index(listOf("китап" to 10L))
        assertEquals(emptyList<String>(), lookup(index, "икт"))
    }

    /**
     * Positive control: class #1 still recovers on the shipped path, and a class-#2 sibling that a
     * full #1+#2 pass would additionally surface does NOT appear. "кум" → class #1 (у→ү) → "күмеш";
     * class #2 (у→ө) would add "көм*", i.e. "көмеш" (far higher frequency), but it is dropped.
     */
    @Test
    fun class1StillRecoversWhileItsClass2SiblingIsDropped() {
        assertTrue("premise: class #2 would add \"көм\"", geometricVariantsOf("кум").contains("көм"))
        // Code-point sorted: ү (U+04AF) precedes ө (U+04E9) at the second position.
        val index = index(listOf("күмеш" to 5L, "көмеш" to 9_999L))
        assertEquals(listOf("күмеш"), lookup(index, "кум"))
    }
}
