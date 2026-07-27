package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyPrefixVariantsTest {
    private val table = E3aTestFixtures.tatarNeighborTable()
    private val codePointScratch = IntArray(64)
    private val variantScratch = ByteArray(256)

    private fun variantsOf(prefix: String, maxVariants: Int = 100): Pair<Int, List<String>> {
        val collected = ArrayList<String>()
        val bytes = prefix.toByteArray(Charsets.UTF_8)
        val emitted = FuzzyPrefixVariants.generateLongPressVariants(
            bytes, bytes.size, table, codePointScratch, variantScratch, maxVariants,
        ) { variantUtf8, length ->
            collected.add(String(variantUtf8, 0, length, Charsets.UTF_8))
        }
        return emitted to collected
    }

    @Test
    fun replacesEveryPositionThatHasALongPressPartner() {
        // "ана": а→ә at position 0 and 2, н→ң at position 1.
        val (emitted, variants) = variantsOf("ана")
        assertEquals(3, emitted)
        assertEquals(listOf("әна", "аңа", "анә"), variants)
    }

    @Test
    fun eachTatarFifthRowLetterReEncodesToItsBaseOrBases() {
        // The six letters the product exists for: ә ө ү җ ң һ. Each must re-encode to valid UTF-8.
        assertEquals(listOf("ат", "эт"), variantsOf("әт").second)
        assertEquals(listOf("от"), variantsOf("өт").second)
        assertEquals(listOf("ут"), variantsOf("үт").second)
        assertEquals(listOf("жт"), variantsOf("җт").second)
        assertEquals(listOf("нт"), variantsOf("ңт").second)
        assertEquals(listOf("гт", "хт"), variantsOf("һт").second)
    }

    @Test
    fun schwaExpandsToBothDeclaredBasesInCodePointOrder() {
        // "ә" is declared on both "а" (0x430) and "э" (0x44D); symmetrization gives both back.
        assertEquals(listOf("аби", "эби"), variantsOf("әби").second)
    }

    @Test
    fun aPrefixWithoutAnyPartnerProducesNoVariant() {
        val (emitted, variants) = variantsOf("сит")
        assertEquals(0, emitted)
        assertTrue(variants.isEmpty())
    }

    @Test
    fun exceedingTheVariantBudgetFailsClosedWithMinusOne() {
        // "аоуана" would emit six variants (а×3, о, у, н); a budget of three drops the whole set.
        val (emitted, _) = variantsOf("аоуана", maxVariants = 3)
        assertEquals(-1, emitted)
    }

    @Test
    fun countCodePointsByLeadBytesHandlesCyrillicLatinAndMixed() {
        val cyrillic = "кит".toByteArray(Charsets.UTF_8)
        assertEquals(6, cyrillic.size)
        assertEquals(3, countCodePointsByLeadBytes(cyrillic, cyrillic.size))

        val latin = "abc".toByteArray(Charsets.UTF_8)
        assertEquals(3, countCodePointsByLeadBytes(latin, latin.size))

        // Mixed script + digit: "а" is two bytes, "b" and "1" one byte each: three code points.
        val mixed = "аb1".toByteArray(Charsets.UTF_8)
        assertEquals(4, mixed.size)
        assertEquals(3, countCodePointsByLeadBytes(mixed, mixed.size))

        // A two-letter Cyrillic prefix is four bytes but only two code points.
        val twoCyrillic = "мә".toByteArray(Charsets.UTF_8)
        assertEquals(4, twoCyrillic.size)
        assertEquals(2, countCodePointsByLeadBytes(twoCyrillic, twoCyrillic.size))
    }
}
