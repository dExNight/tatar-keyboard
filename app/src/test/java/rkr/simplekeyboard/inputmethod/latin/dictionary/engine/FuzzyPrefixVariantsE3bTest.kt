package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edit classes #2 (geometric neighbour) and #3 (adjacent transposition) variant generation. */
class FuzzyPrefixVariantsE3bTest {
    private val table = E3bTestFixtures.tatarNeighborTable()
    private val codePointScratch = IntArray(64)
    private val variantScratch = ByteArray(256)

    private fun geometricVariantsOf(prefix: String, maxVariants: Int = 100): Pair<Int, List<String>> {
        val collected = ArrayList<String>()
        val bytes = prefix.toByteArray(Charsets.UTF_8)
        val emitted = FuzzyPrefixVariants.generateGeometricVariants(
            bytes, bytes.size, table, codePointScratch, variantScratch, maxVariants,
        ) { variantUtf8, length -> collected.add(String(variantUtf8, 0, length, Charsets.UTF_8)) }
        return emitted to collected
    }

    private fun transpositionVariantsOf(prefix: String, maxVariants: Int = 100): Pair<Int, List<String>> {
        val collected = ArrayList<String>()
        val bytes = prefix.toByteArray(Charsets.UTF_8)
        val emitted = FuzzyPrefixVariants.generateTranspositionVariants(
            bytes, bytes.size, codePointScratch, variantScratch, maxVariants,
        ) { variantUtf8, length -> collected.add(String(variantUtf8, 0, length, Charsets.UTF_8)) }
        return emitted to collected
    }

    @Test
    fun geometricPassReplacesEveryPositionWithEachNeighbourInOrder() {
        // "кит": к→[а,е,у,ө], и→[м,р,т], т→[и,о,ь] (sorted by code point).
        val (emitted, variants) = geometricVariantsOf("кит")
        assertEquals(10, emitted)
        assertEquals(
            listOf("аит", "еит", "уит", "өит", "кмт", "крт", "ктт", "кии", "кио", "киь"),
            variants,
        )
    }

    @Test
    fun geometricVariantsReEncodeToValidUtf8ForFifthRowLetters() {
        // Replacing into/around the two-byte fifth-row letters must produce valid UTF-8, not a
        // truncated byte edit. "әни": ә→[й,ц,ө]; every variant round-trips through UTF-8.
        val (_, variants) = geometricVariantsOf("әни")
        assertTrue(variants.all { it.length == "әни".length })
        assertTrue(variants.contains("йни"))
        assertTrue(variants.contains("өни"))
    }

    @Test
    fun geometricPassFailsClosedWhenTheBudgetIsExceeded() {
        val (emitted, _) = geometricVariantsOf("кит", maxVariants = 5)
        assertEquals(-1, emitted)
    }

    @Test
    fun transpositionPassSwapsEveryDistinctAdjacentPair() {
        val (emitted, variants) = transpositionVariantsOf("кит")
        assertEquals(2, emitted)
        assertEquals(listOf("икт", "кти"), variants)
    }

    @Test
    fun transpositionPassSkipsSwapsOfIdenticalAdjacentLetters() {
        // "аал": the (а,а) pair reproduces the prefix, so only the (а,л) swap is emitted.
        val (emitted, variants) = transpositionVariantsOf("аал")
        assertEquals(1, emitted)
        assertEquals(listOf("ала"), variants)
    }

    @Test
    fun transpositionPassFailsClosedWhenTheBudgetIsExceeded() {
        val (emitted, _) = transpositionVariantsOf("китап", maxVariants = 2)
        assertEquals(-1, emitted)
    }
}
