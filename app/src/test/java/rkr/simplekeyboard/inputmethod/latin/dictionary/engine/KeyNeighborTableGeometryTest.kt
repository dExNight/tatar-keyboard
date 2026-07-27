package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edit class #2 source: geometric neighbours derived by [KeyNeighborTable] from the raw key
 * geometry, exercised through the reconstructed live Tatar layout ([E3bTestFixtures]).
 */
class KeyNeighborTableGeometryTest {
    private val table = E3bTestFixtures.tatarNeighborTable()

    @Test
    fun degenerateGeometryProducesNoGeometricNeighbour() {
        // The E3a fixture puts every key at the same 0..10 rectangle: coincident rectangles do not
        // "touch" (no shared edge) and share one row, so no geometric neighbour is derived. This is
        // exactly what keeps the E3a (class #1) tests unaffected by the E3b geometric pass.
        val degenerate = E3aTestFixtures.tatarNeighborTable()
        for (letter in listOf('а', 'к', 'ә', 'о', 'т')) {
            assertNull(degenerate.geometricNeighborsOf(letter.code))
        }
    }

    @Test
    fun geometricNeighboursAreSymmetricAndSortedByCodePoint() {
        // "к" sits in the second row; its horizontal neighbours (у, е) and the vertically
        // overlapping keys above/below (а, ө) are its geometric neighbours, sorted by code point.
        assertArrayEquals(
            intArrayOf('а'.code, 'е'.code, 'у'.code, 'ө'.code),
            table.geometricNeighborsOf('к'.code),
        )
        // Symmetry: у lists к, and к lists у.
        assertTrue(table.geometricNeighborsOf('у'.code)!!.contains('к'.code))
        assertTrue(table.geometricNeighborsOf('к'.code)!!.contains('у'.code))
    }

    @Test
    fun fifthRowLettersAreGeometricallyConnectedToTheAlphabet() {
        // The whole point of the 35%-overlap rule: the wide fifth-row keys (ә ө ү җ ң һ) overlap the
        // narrower first-row keys and so keep a geometric link to the rest of the alphabet, instead
        // of being neighbours only of one another.
        for (fifth in listOf('ә', 'ө', 'ү', 'җ', 'ң', 'һ')) {
            val neighbours = table.geometricNeighborsOf(fifth.code)
            assertTrue("no geometric neighbour for $fifth", neighbours != null && neighbours.isNotEmpty())
            val hasNonFifthRow = neighbours!!.any { it !in "әөүҗңһ".map(Char::code) }
            assertTrue("$fifth is only connected to the fifth row", hasNonFifthRow)
        }
    }

    @Test
    fun theRelationHasThirtySevenKeysAndSixtyFiveUndirectedPairs() {
        // 37 = 6 + 11 + 11 + 9 letter keys (rowkeys_tatar*.xml); the node set also carries the two
        // more-key-only letters ё and ъ (no geometry of their own) for a total of 39 nodes. The
        // geometric relation has avg fan-out 3.51 and max 5 over the 37 geometry-bearing keys — the
        // numbers recorded for the geometric map in docs/DICTIONARY-E3.md.
        assertEquals(37, table.letterKeyCount)
        assertEquals(39, table.nodes.size)
        val undirected = HashSet<Set<Int>>()
        var totalFanout = 0
        var maxFanout = 0
        for (node in table.nodes) {
            val neighbours = table.geometricNeighborsOf(node) ?: IntArray(0)
            totalFanout += neighbours.size
            maxFanout = maxOf(maxFanout, neighbours.size)
            for (partner in neighbours) undirected.add(setOf(node, partner))
        }
        assertEquals(65, undirected.size)
        assertEquals(5, maxFanout)
        assertEquals(3.51, totalFanout.toDouble() / table.letterKeyCount, 0.01)
    }
}
