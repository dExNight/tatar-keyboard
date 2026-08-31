package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PROPOSALS.md, "E5b. Валидатор отвергает каждый класс порчи (чужой magic, schema ≠ 2, неверный
 * checksum, неканоническая арифметика секций, невалидный UTF-8, неотсортированные или
 * дублирующиеся слова, id ≥ V, пустой диапазон успехов, хвостовые байты) отдельным зелёным тестом
 * на свободно созданной fixture" — one test per named class below, plus the zlib-layer classes
 * ([TatBigrValidator.inflateAsset]) mirrored from [TdictValidatorTest].
 */
class TatBigrValidatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val validator = TatBigrValidator()

    @Test
    fun acceptsGoldenFixture() {
        val artifact = BigramTestFixtures.artifact()
        val rawFile = inflate(artifact)

        val validated = validator.validateRaw(rawFile, artifact.spec)

        assertEquals(2, validated.headCount)
        assertEquals(3, validated.pairCount)
        assertEquals(BigramTestFixtures.sha256(artifact.raw), validated.rawSha256)
    }

    @Test
    fun acceptsCommittedTatarBigramAssetWithFrozenProvenance() {
        val validated = validateCommitted(
            "tatar_bigrams_v1.tatbigr.zlib",
            BigramArtifactSpec.TATAR_BIGRAMS_V1,
        )

        // K = 4 since 2026-08-23 (docs/archive/bigrams/BIGRAM-ADJACENCY.md), H = 10 132 since
        // 2026-08-25 (docs/archive/bigrams/IMPERATIVE-HEADS.md). Repacked 2026-08-31 with the
        // conversational admixture (docs/CORPUS-CONVERSATIONAL-TT.md, corpus-conversational
        // part B): training = two Leipzig corpora + deduplicated Tatoeba + OpenSubtitles tt
        // (unthinned, 3,7 % of the written mass), extra-heads rule extended to ranks
        // [10 000, 40 000) — 75 named words. 10 204 = 10 129 (cutoff) + 75 (named); the three
        // `-гәнчә` converbs are still dropped for zero pairs.
        assertEquals(10_204, validated.headCount)
        assertEquals(520_892, validated.rawSize)
        assertEquals(40_734, validated.pairCount)
    }

    /**
     * The ru table shipped in 1.8.x with no test against its real committed bytes while the tt
     * table had one; the K = 4 repack touched both, so both are pinned now.
     *
     * Repacked 2026-08-31 with the conversational admixture (docs/CORPUS-CONVERSATIONAL-RU.md,
     * corpus-conversational part A): training = three Leipzig corpora + deduplicated
     * 1/60-thinned Tatoeba + OpenSubtitles. 9 998 heads, not 10 000 — `окей` and `берегись`
     * have no in-vocabulary pair in the thinned input and are dropped rather than stored with
     * an empty range (the same generator rule as the Tatar `-гәнчә` converbs).
     */
    @Test
    fun acceptsCommittedRussianBigramAssetWithFrozenProvenance() {
        val validated = validateCommitted(
            "russian_bigrams_v1.tatbigr.zlib",
            BigramArtifactSpec.RUSSIAN_BIGRAMS_V1,
        )

        assertEquals(9_998, validated.headCount)
        assertEquals(465_610, validated.rawSize)
        assertEquals(39_949, validated.pairCount)
    }

    @Test
    fun rejectsMalformedTruncatedAndCorruptZlib() {
        val artifact = BigramTestFixtures.artifact()
        val variants = listOf(
            byteArrayOf(1, 2, 3),
            artifact.compressed.copyOf(artifact.compressed.size - 2),
            artifact.compressed.copyOf().also {
                it[it.lastIndex / 2] = (it[it.lastIndex / 2].toInt() xor 0x40).toByte()
            },
        )
        variants.forEachIndexed { index, bytes ->
            val spec = specForCompressed(artifact, bytes, generation = 10 + index)
            assertValidationFails { inflate(bytes, spec) }
        }
    }

    @Test
    fun rejectsTrailingAndConcatenatedZlib() {
        val artifact = BigramTestFixtures.artifact()
        val variants = listOf(
            artifact.compressed + byteArrayOf(0),
            artifact.compressed + artifact.compressed,
        )
        variants.forEachIndexed { index, bytes ->
            assertValidationFails { inflate(bytes, specForCompressed(artifact, bytes, 20 + index)) }
        }
    }

    @Test
    fun rejectsPresetDictionaryZlib() {
        val dictionary = "preset dictionary".toByteArray()
        val artifact = BigramTestFixtures.artifact()
        val presetCompressed = BigramTestFixtures.compress(artifact.raw, dictionary)

        assertValidationFails {
            inflate(presetCompressed, specForCompressed(artifact, presetCompressed, 25))
        }
    }

    @Test
    fun rejectsCompressedAndRawCaps() {
        val artifact = BigramTestFixtures.artifact()
        val oversizedCompressed = ByteArray(128) { it.toByte() }
        val compressedSpec = BigramArtifactSpec(
            family = "tatar_bigrams",
            generation = 30,
            fileLanguageTag = "tt",
            subtypeId = "tt_RU",
            storageDirectoryName = "bigrams",
            assetPath = "oversized",
            expectedCompressedSize = 64,
            expectedCompressedSha256 = BigramTestFixtures.sha256(oversizedCompressed),
            expectedRawSize = artifact.raw.size.toLong(),
            expectedRawSha256 = BigramTestFixtures.sha256(artifact.raw),
            expectedHeadCount = 2,
            maxCompressedSize = 64,
        )
        assertValidationFails { inflate(oversizedCompressed, compressedSpec) }

        val largeRaw = ByteArray(artifact.raw.size + 1)
        val compressed = BigramTestFixtures.compress(largeRaw)
        val rawSpec = BigramArtifactSpec(
            family = "tatar_bigrams",
            generation = 31,
            fileLanguageTag = "tt",
            subtypeId = "tt_RU",
            storageDirectoryName = "bigrams",
            assetPath = "raw-oversized",
            expectedCompressedSize = compressed.size.toLong(),
            expectedCompressedSha256 = BigramTestFixtures.sha256(compressed),
            expectedRawSize = artifact.raw.size.toLong(),
            expectedRawSha256 = BigramTestFixtures.sha256(artifact.raw),
            expectedHeadCount = 2,
            maxRawSize = artifact.raw.size.toLong(),
        )
        assertValidationFails { inflate(compressed, rawSpec) }
    }

    @Test
    fun rejectsWrongMagic() {
        val artifact = BigramTestFixtures.artifact()
        val corrupted = BigramTestFixtures.refreshEmbeddedChecksum(
            artifact.raw.copyOf().also { it[0] = 'X'.code.toByte() },
        )
        assertRawFails(corrupted, 40)
    }

    @Test
    fun rejectsWrongSchemaId() {
        assertRawFails(mutateU16AndRehash(BigramTestFixtures.artifact().raw, 8, 3), 41)
    }

    @Test
    fun rejectsWrongFormatVersion() {
        assertRawFails(mutateU16AndRehash(BigramTestFixtures.artifact().raw, 10, 2), 42)
    }

    @Test
    fun rejectsWrongHeaderSize() {
        assertRawFails(mutateU16AndRehash(BigramTestFixtures.artifact().raw, 12, 70), 43)
    }

    @Test
    fun rejectsWrongChecksumAlgorithm() {
        assertRawFails(mutateU16AndRehash(BigramTestFixtures.artifact().raw, 14, 2), 44)
    }

    @Test
    fun rejectsChecksumMismatch() {
        val artifact = BigramTestFixtures.artifact()
        val corrupt = artifact.raw.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() }
        assertValidationFails { validator.validateRaw(writeRaw(corrupt), artifact.spec) }
    }

    @Test
    fun rejectsIdentityMismatchAgainstSpec() {
        val artifact = BigramTestFixtures.artifact()
        val wrongIdentity = artifact.spec.copy(expectedRawSha256 = "0".repeat(64))
        assertValidationFails { validator.validateRaw(writeRaw(artifact.raw), wrongIdentity) }
    }

    @Test
    fun rejectsNoncanonicalSectionArithmetic() {
        val artifact = BigramTestFixtures.artifact()
        // Offsets, in header order: three counts at 16/20/24, six section offsets at
        // 28/32/36/40/44/48, two blob lengths at 52/56, file size at 60.
        val variants = listOf(
            mutateU32AndRehash(artifact.raw, 28, 97),
            mutateU32AndRehash(artifact.raw, 32, 999),
            mutateU32AndRehash(artifact.raw, 36, 999),
            mutateU32AndRehash(artifact.raw, 40, 999),
            mutateU32AndRehash(artifact.raw, 44, 999),
            mutateU32AndRehash(artifact.raw, 48, 999),
            mutateU32AndRehash(artifact.raw, 60, artifact.raw.size + 1),
        )
        variants.forEachIndexed { index, raw -> assertRawFails(raw, 50 + index) }
    }

    @Test
    fun rejectsZeroAndOverflowingHeadCount() {
        val artifact = BigramTestFixtures.artifact()
        val zeroCount = mutateU32AndRehash(artifact.raw, 16, 0)
        assertValidationFails {
            validator.validateRaw(
                writeRaw(zeroCount),
                artifact.spec.copy(expectedRawSha256 = BigramTestFixtures.sha256(zeroCount)),
            )
        }

        val hugeCount = mutateU32AndRehash(artifact.raw, 16, -1)
        assertValidationFails {
            validator.validateRaw(
                writeRaw(hugeCount),
                artifact.spec.copy(
                    expectedRawSha256 = BigramTestFixtures.sha256(hugeCount),
                    expectedHeadCount = 0xffff_ffffL,
                ),
            )
        }
    }

    @Test
    fun rejectsEmptySuccessRange() {
        // A head with no successes at all — the exact shape pack_bigram_table (E5b's Python
        // generator) refuses to ever produce, and the validator must refuse to ever accept.
        val raw = BigramTestFixtures.raw(listOf("аб" to listOf("аба"), "аба" to emptyList()))
        assertRawFails(raw, 65)
    }

    @Test
    fun rejectsSuccessIdAtOrBeyondVocabularySize() {
        val artifact = BigramTestFixtures.artifact()
        val section4 = ByteBuffer.wrap(artifact.raw, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val corrupted = mutateU32AndRehash(artifact.raw, section4, Int.MAX_VALUE)
        assertRawFails(corrupted, 66)
    }

    @Test
    fun rejectsInvalidUtf8UnsortedAndDuplicateWords() {
        val artifact = BigramTestFixtures.artifact()
        val section2 = ByteBuffer.wrap(artifact.raw, 32, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val malformedUtf8 = BigramTestFixtures.refreshEmbeddedChecksum(
            artifact.raw.copyOf().also { it[section2] = 0xff.toByte() },
        )
        val rawVariants = listOf(
            malformedUtf8,
            BigramTestFixtures.raw(listOf("аба" to listOf("аб"), "аб" to listOf("аб"))), // heads unsorted
            BigramTestFixtures.raw(listOf("аб" to listOf("аб"), "аб" to listOf("аб"))), // duplicate heads
        )
        rawVariants.forEachIndexed { index, raw -> assertRawFails(raw, 70 + index) }
    }

    @Test
    fun rejectsTrailingRawBytesAndTruncation() {
        val artifact = BigramTestFixtures.artifact()
        val withTrailing = BigramTestFixtures.refreshEmbeddedChecksum(artifact.raw + byteArrayOf(0))
        assertRawFails(withTrailing, 80)

        assertValidationFails {
            validator.validateRaw(
                writeRaw(artifact.raw.copyOf(artifact.raw.size - 1)),
                artifact.spec,
            )
        }
    }

    private fun inflate(artifact: TestBigramArtifact): File = inflate(artifact.compressed, artifact.spec)

    private fun inflate(bytes: ByteArray, spec: BigramArtifactSpec): File {
        val file = temporaryFolder.newFile("inflated-${System.nanoTime()}.tatbigr")
        file.outputStream().use { validator.inflateAsset(bytes.inputStream(), it, spec) }
        return file
    }

    private fun assertRawFails(raw: ByteArray, generation: Int) {
        val compressed = BigramTestFixtures.compress(raw)
        val spec = BigramTestFixtures.spec(generation, "tt", raw, compressed)
        assertValidationFails { validator.validateRaw(writeRaw(raw), spec) }
    }

    private fun specForCompressed(
        artifact: TestBigramArtifact,
        compressed: ByteArray,
        generation: Int,
    ) = artifact.spec.copy(
        generation = generation,
        expectedCompressedSize = compressed.size.toLong(),
        expectedCompressedSha256 = BigramTestFixtures.sha256(compressed),
    )

    private fun writeRaw(bytes: ByteArray): File =
        temporaryFolder.newFile("raw-${System.nanoTime()}.tatbigr").also { it.writeBytes(bytes) }

    private fun mutateU16AndRehash(raw: ByteArray, offset: Int, value: Int): ByteArray =
        BigramTestFixtures.refreshEmbeddedChecksum(
            raw.copyOf().also {
                ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, value.toShort())
            },
        )

    private fun mutateU32AndRehash(raw: ByteArray, offset: Int, value: Int): ByteArray =
        BigramTestFixtures.refreshEmbeddedChecksum(
            raw.copyOf().also {
                ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
            },
        )

    private fun validateCommitted(
        assetFileName: String,
        spec: BigramArtifactSpec,
    ): ValidatedBigramTable {
        val asset = locateCommittedAsset(assetFileName).readBytes()
        val rawFile = temporaryFolder.newFile("${spec.fileLanguageTag}.tatbigr")
        rawFile.outputStream().use { output ->
            validator.inflateAsset(asset.inputStream(), output, spec)
        }
        return validator.validateRaw(rawFile, spec)
    }

    private fun locateCommittedAsset(assetFileName: String): File {
        val candidates = listOf(
            File("src/main/assets/bigrams/$assetFileName"),
            File("app/src/main/assets/bigrams/$assetFileName"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("cannot locate $assetFileName from ${File(".").absolutePath}")
    }

    private fun assertValidationFails(block: () -> Unit) {
        try {
            block()
            fail("expected BigramValidationException")
        } catch (expected: BigramValidationException) {
            assertTrue(expected.message.orEmpty().isNotEmpty())
        }
    }
}
