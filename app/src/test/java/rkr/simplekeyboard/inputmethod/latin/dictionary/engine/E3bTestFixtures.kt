package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

/**
 * Shared fixture for the E3b fuzzy-suggestion tests (edit classes #2 and #3).
 *
 * Unlike [E3aTestFixtures] — whose keys all sit at the same degenerate rectangle so no geometric
 * neighbour is derived and the class #1 tests exercise long-press alone — this fixture reconstructs
 * the real Tatar layout geometry so [KeyNeighborTable] derives the edit class #2 relation.
 *
 * The reconstruction mirrors `res/xml/rows_tatar.xml` on a fixed integer grid (layout percent width
 * x1000, rounded once): the fifth row of six 16.667%p keys, two rows of eleven 9.091%p keys, and a
 * bottom row of nine 8.711%p letter keys offset by the 10.8%p shift key. Both the row/column key
 * order and the long-press `moreKeys` mirror `rowkeys_tatar*.xml`. Production still derives the same
 * relation from the live keyboard (`KeyNeighborTableBuilder.fromKeyboard`); the offline generator
 * `scripts/typo_pack.py` reconstructs the identical grid, and the equality is proven by the
 * byte-identical typo-set SHA-256 in [E3bRecoveryCalibrationTest].
 *
 * Building a fixture from explicit key descriptors is a test concern; no production source carries a
 * Cyrillic literal or a hard-coded key pair (asserted by [E3bEngineSourceContractTest]).
 */
internal object E3bTestFixtures {

    // Row geometry: (keyWidth, leftOffset) in grid units, from rows_tatar.xml percent widths x1000.
    private fun rowGeometry(row: Int): Pair<Int, Int> = when (row) {
        0 -> 16667 to 0      // fifth row: 16.667%p, full width
        1 -> 9091 to 0       // rowkeys_tatar1: 9.091%p
        2 -> 9091 to 0       // rowkeys_tatar2: 9.091%p
        3 -> 8711 to 10800   // rowkeys_tatar3: 8.711%p, offset by the 10.8%p shift key
        else -> error("unexpected row $row")
    }

    private fun geoKey(base: Char, row: Int, col: Int, vararg partners: Char): KeyNeighborTable.RawKey {
        val (width, offset) = rowGeometry(row)
        val left = offset + col * width
        return KeyNeighborTable.RawKey(
            base.code, left, row, left + width, row + 1,
            IntArray(partners.size) { partners[it].code },
        )
    }

    /** The Tatar alphabet keyboard, geometry and long-press partners mirroring the layout XML. */
    fun tatarNeighborTable(subtypeId: String = "tt_RU"): KeyNeighborTable =
        KeyNeighborTable.build(
            subtypeId,
            true,
            listOf(
                // Row 0 — fifth row: ә ө ү җ ң һ
                geoKey('ә', 0, 0), geoKey('ө', 0, 1), geoKey('ү', 0, 2),
                geoKey('җ', 0, 3), geoKey('ң', 0, 4), geoKey('һ', 0, 5),
                // Row 1: й ц у к е н г ш щ з х
                geoKey('й', 1, 0), geoKey('ц', 1, 1), geoKey('у', 1, 2, 'ү'),
                geoKey('к', 1, 3), geoKey('е', 1, 4, 'ё'), geoKey('н', 1, 5, 'ң'),
                geoKey('г', 1, 6, 'һ'), geoKey('ш', 1, 7), geoKey('щ', 1, 8),
                geoKey('з', 1, 9), geoKey('х', 1, 10, 'һ'),
                // Row 2: ф ы в а п р о л д ж э
                geoKey('ф', 2, 0), geoKey('ы', 2, 1), geoKey('в', 2, 2),
                geoKey('а', 2, 3, 'ә'), geoKey('п', 2, 4), geoKey('р', 2, 5),
                geoKey('о', 2, 6, 'ө'), geoKey('л', 2, 7), geoKey('д', 2, 8),
                geoKey('ж', 2, 9, 'җ'), geoKey('э', 2, 10, 'ә'),
                // Row 3: я ч с м и т ь б ю
                geoKey('я', 3, 0), geoKey('ч', 3, 1), geoKey('с', 3, 2),
                geoKey('м', 3, 3), geoKey('и', 3, 4), geoKey('т', 3, 5),
                geoKey('ь', 3, 6, 'ъ'), geoKey('б', 3, 7), geoKey('ю', 3, 8),
            ),
        )
}
