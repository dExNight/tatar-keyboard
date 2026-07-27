package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Engine integration of edit classes #2 (geometric neighbour) and #3 (adjacent transposition). */
class TdictPrefixIndexE3bTest {
    private val table = E3bTestFixtures.tatarNeighborTable()

    private fun index(entries: List<Pair<String, Long>>, withTable: Boolean = true): TdictPrefixIndex {
        val index = EngineTestFixtures.index(entries)
        if (withTable) index.updateKeyNeighbors(table)
        return index
    }

    private fun lookup(index: TdictPrefixIndex, prefix: String): List<String> =
        index.lookup(ImmutableUtf8Prefix.copyOf(prefix.toByteArray(Charsets.UTF_8)))

    @Test
    fun geometricNeighbourTypoIsRecoveredIntoAnEmptyCell() {
        // "аит" is "кит" with к mistyped as its geometric neighbour а. The class #2 pass substitutes
        // а back to к (а's geometric neighbours are в к п с), scans "кит*" and surfaces "китап".
        val index = index(listOf("китап" to 10L))
        assertEquals(listOf("китап"), lookup(index, "аит"))
    }

    @Test
    fun transpositionTypoIsRecoveredIntoAnEmptyCell() {
        // "икт" is "кит" with к and и swapped. The class #3 pass swaps them back to "кит".
        val index = index(listOf("китап" to 10L))
        assertEquals(listOf("китап"), lookup(index, "икт"))
    }

    @Test
    fun exactCandidatesAreNeverShiftedByTheGeometricOrTranspositionPasses() {
        // "кита" has one exact continuation (китап); the fuzzy classes only fill the two cells the
        // exact pass left empty and never move the exact candidate off the front.
        val index = index(
            listOf(
                // Code-point sorted, as the tdict fixture requires.
                "кита" to 20L,    // == typed word, excluded
                "китап" to 10L,   // exact continuation of "кита"
                "кутап" to 9L,    // a fuzzy-reachable word — must stay after the exact candidate
            ),
        )
        val result = lookup(index, "кита")
        assertEquals("китап", result[0])
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun aWordReachableByTwoClassesStillOccupiesOnlyOneCell() {
        // "китап" is reachable from "кит"-family variants of several classes; de-duplication by
        // dictionary index guarantees it never fills two cells.
        val index = index(listOf("китап" to 10L))
        val result = lookup(index, "икт")
        assertEquals(result.distinct(), result)
    }

    @Test
    fun theWholeFuzzyLevelIsDroppedWhenTheVariantBudgetIsExceeded() {
        // A synthetic table whose 'к' key carries 65 long-press partners — one past the engine's
        // MAX_FUZZY_VARIANTS budget — makes class #1 alone overflow on a prefix starting with 'к'.
        // Latin + Cyrillic + Greek lowercase letters are all NFC-stable and distinct, so the built
        // partner set keeps all 65.
        val fakePartners = ((0x61..0x7A) + (0x430..0x44F) + (0x3B1..0x3C9))
            .filter { it != 'к'.code }
            .take(65)
            .toIntArray()
        assertEquals(65, fakePartners.size)
        assertTrue("target partner must be present", fakePartners.contains('з'.code))
        val overloaded = KeyNeighborTable.build(
            "tt_RU", true,
            listOf(
                KeyNeighborTable.RawKey('к'.code, 0, 0, 10, 10, fakePartners),
                E3aTestFixtures.rawKey('о'), E3aTestFixtures.rawKey('т'),
            ),
        )
        assertEquals(65, overloaded.longPressPartnersOf('к'.code)!!.size)
        val small = KeyNeighborTable.build(
            "tt_RU", true,
            listOf(
                KeyNeighborTable.RawKey('к'.code, 0, 0, 10, 10, intArrayOf('з'.code)),
                E3aTestFixtures.rawKey('о'), E3aTestFixtures.rawKey('т'),
            ),
        )
        val index = EngineTestFixtures.index(listOf("зот" to 5L))
        // With a small table, class #1 (к→з) recovers "зот".
        index.updateKeyNeighbors(small)
        assertEquals(listOf("зот"), lookup(index, "кот"))
        assertTrue(!index.lastFuzzyOverBudget)
        // With the overloaded table the budget is exceeded, so the WHOLE level is dropped — the
        // partial "зот" found mid-generation is discarded, not kept.
        index.updateKeyNeighbors(overloaded)
        assertEquals(emptyList<String>(), lookup(index, "кот"))
        assertTrue(index.lastFuzzyOverBudget)
    }

    @Test
    fun theResultIsDeterministicAcrossManyRepeats() {
        val index = index(listOf("китап" to 10L, "китаплар" to 4L, "кутап" to 9L))
        val first = lookup(index, "икт")
        repeat(1000) { assertEquals(first, lookup(index, "икт")) }
    }
}
