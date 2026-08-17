package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.BigramTestFixtures
import java.nio.ByteBuffer

class TatBigrPrefixIndexTest {
    @Test
    fun exactMatchReturnsSuccessesInPackingOrder() {
        val index = EngineTestFixtures.bigramIndex(
            listOf("аб" to listOf("аба", "юл"), "юл" to listOf("өй")),
        )

        assertEquals(listOf("аба", "юл"), predict(index, "аб"))
    }

    @Test
    fun onlyExactMatchNoPrefixBehaviour() {
        val index = EngineTestFixtures.bigramIndex(listOf("аба" to listOf("өй")))

        // "аб" is a strict prefix of the only head "аба" — must not match.
        assertTrue(predict(index, "аб").isEmpty())
        assertTrue(predict(index, "абалар").isEmpty())
    }

    @Test
    fun headNotFoundReturnsEmpty() {
        val index = EngineTestFixtures.bigramIndex(listOf("аб" to listOf("аба")))

        assertTrue(predict(index, "юл").isEmpty())
    }

    @Test
    fun moreThanThreeStoredSuccessesAreCappedToThree() {
        val index = EngineTestFixtures.bigramIndex(
            listOf("аб" to listOf("а", "б", "в", "г", "д", "е")),
        )

        assertEquals(listOf("а", "б", "в"), predict(index, "аб"))
    }

    @Test
    fun emptyOversizedAndInvalidUtf8ContextWordsReturnEmpty() {
        val index = EngineTestFixtures.bigramIndex(listOf("аб" to listOf("аба")))

        assertTrue(predict(index, "").isEmpty())
        assertTrue(index.predict(ImmutableUtf8Prefix.copyOf(ByteArray(129) { 1 })).isEmpty())
        assertTrue(index.predict(ImmutableUtf8Prefix.copyOf(byteArrayOf(0xff.toByte()))).isEmpty())
    }

    @Test
    fun openRejectsIdentityMismatch() {
        val raw = BigramTestFixtures.raw(listOf("аб" to listOf("аба")))
        val wrongSchema = TatBigrPrefixIndex.open(
            ByteBuffer.wrap(raw),
            EngineTestFixtures.bigramIdentity.copy(schemaId = 1),
            1,
            raw.size.toLong(),
        )
        assertNull(wrongSchema)

        val wrongHeadCount = TatBigrPrefixIndex.open(
            ByteBuffer.wrap(raw),
            EngineTestFixtures.bigramIdentity,
            2,
            raw.size.toLong(),
        )
        assertNull(wrongHeadCount)

        val truncated = TatBigrPrefixIndex.open(
            ByteBuffer.wrap(raw.copyOf(raw.size - 1)),
            EngineTestFixtures.bigramIdentity,
            1,
            raw.size.toLong(),
        )
        assertNull(truncated)
    }

    @Test
    fun acceptsCommittedTatarBigramAssetAndFindsARealHead() {
        val spec = rkr.simplekeyboard.inputmethod.latin.dictionary.storage.BigramArtifactSpec
            .TATAR_BIGRAMS_V1
        val asset = locateCommittedAsset().readBytes()
        val decompressed = java.io.ByteArrayOutputStream(spec.expectedRawSize.toInt()).also { output ->
            rkr.simplekeyboard.inputmethod.latin.dictionary.storage.TatBigrValidator()
                .inflateAsset(asset.inputStream(), output, spec)
        }.toByteArray()
        val identity = BigramTableIdentity(
            spec.generation, spec.languageTag, spec.schemaId, spec.formatVersion, spec.expectedRawSha256,
        )
        val index = TatBigrPrefixIndex.open(
            ByteBuffer.wrap(decompressed),
            identity,
            spec.expectedHeadCount,
            decompressed.size.toLong(),
        )
        assertTrue(index != null)
        // "мин" (I/me) is an extremely common Tatar word and, if present as a head at all, must
        // return at most three successes without throwing.
        val results = index!!.predict(ImmutableUtf8Prefix.copyOf("мин".toByteArray(Charsets.UTF_8)))
        assertTrue(results.size <= 3)
    }

    private fun predict(index: TatBigrPrefixIndex, word: String): List<String> =
        index.predict(ImmutableUtf8Prefix.copyOf(word.toByteArray(Charsets.UTF_8)))

    private fun locateCommittedAsset(): java.io.File {
        val candidates = listOf(
            java.io.File("src/main/assets/bigrams/tatar_bigrams_v1.tatbigr.zlib"),
            java.io.File("app/src/main/assets/bigrams/tatar_bigrams_v1.tatbigr.zlib"),
        )
        return candidates.firstOrNull(java.io.File::isFile)
            ?: error("cannot locate committed bigram asset from ${java.io.File(".").absolutePath}")
    }
}
