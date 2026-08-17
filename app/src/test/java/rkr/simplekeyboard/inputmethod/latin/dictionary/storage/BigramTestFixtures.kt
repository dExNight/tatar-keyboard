package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Deflater

internal data class TestBigramArtifact(
    val spec: BigramArtifactSpec,
    val raw: ByteArray,
    val compressed: ByteArray,
)

/**
 * Builds fixture TATBIGR (schema 2) files directly from a head -> ordered-successes list, the
 * same role [DictionaryTestFixtures] plays for schema 1. Callers are responsible for listing
 * heads in code-point ascending order themselves (the validator enforces it, this builder does
 * not re-sort) — the same discipline [DictionaryTestFixtures.raw] expects of its entries.
 */
internal object BigramTestFixtures {
    fun artifact(
        generation: Int = 1,
        languageTag: String = "tt",
        headsToSuccesses: List<Pair<String, List<String>>> = listOf(
            "аб" to listOf("аба", "әби"),
            "аба" to listOf("әби"),
        ),
    ): TestBigramArtifact {
        val raw = raw(headsToSuccesses)
        val compressed = compress(raw)
        return TestBigramArtifact(spec(generation, languageTag, raw, compressed), raw, compressed)
    }

    fun raw(headsToSuccesses: List<Pair<String, List<String>>>): ByteArray {
        val heads = headsToSuccesses.map { it.first }
        val headEncoded = heads.map { it.toByteArray(Charsets.UTF_8) }
        val headOffsets = mutableListOf(0)
        headEncoded.forEach { headOffsets += headOffsets.last() + it.size }
        val headBlobLength = headOffsets.last()

        // The success vocabulary is deduplicated AND sorted code-point ascending in storage —
        // insertion order from the caller's per-head lists is irrelevant to the file's shape.
        val vocabularyList = headsToSuccesses.flatMap { it.second }.toSortedSet().toList()
        val vocabularyIndex = vocabularyList.withIndex().associate { (index, word) -> word to index }

        val successRanges = mutableListOf(0)
        val successIds = mutableListOf<Int>()
        headsToSuccesses.forEach { (_, successes) ->
            successes.forEach { successIds += vocabularyIndex.getValue(it) }
            successRanges += successIds.size
        }

        val successEncoded = vocabularyList.map { it.toByteArray(Charsets.UTF_8) }
        val successOffsets = mutableListOf(0)
        successEncoded.forEach { successOffsets += successOffsets.last() + it.size }
        val successBlobLength = successOffsets.last()

        val headCount = heads.size
        val pairCount = successIds.size
        val successVocabularyCount = vocabularyList.size

        val section1 = 96
        val section2 = section1 + 4 * (headCount + 1)
        val section3 = section2 + headBlobLength
        val section4 = section3 + 4 * (headCount + 1)
        val section5 = section4 + 4 * pairCount
        val section6 = section5 + 4 * (successVocabularyCount + 1)
        val fileSize = section6 + successBlobLength

        val bytes = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("TATBIGR\u0000".toByteArray(Charsets.US_ASCII))
        bytes.putShort(2) // schemaId
        bytes.putShort(1) // formatVersion
        bytes.putShort(96) // headerSize
        bytes.putShort(1) // checksumAlgorithm
        bytes.putInt(headCount)
        bytes.putInt(pairCount)
        bytes.putInt(successVocabularyCount)
        bytes.putInt(section1)
        bytes.putInt(section2)
        bytes.putInt(section3)
        bytes.putInt(section4)
        bytes.putInt(section5)
        bytes.putInt(section6)
        bytes.putInt(headBlobLength)
        bytes.putInt(successBlobLength)
        bytes.putInt(fileSize)
        bytes.put(ByteArray(32))
        headOffsets.forEach(bytes::putInt)
        headEncoded.forEach(bytes::put)
        successRanges.forEach(bytes::putInt)
        successIds.forEach(bytes::putInt)
        successOffsets.forEach(bytes::putInt)
        successEncoded.forEach(bytes::put)
        return refreshEmbeddedChecksum(bytes.array())
    }

    fun spec(
        generation: Int,
        languageTag: String,
        raw: ByteArray,
        compressed: ByteArray = compress(raw),
        expectedRawSize: Long = raw.size.toLong(),
        maxCompressedSize: Long = 250_000,
        maxRawSize: Long = 1_048_576,
    ) = BigramArtifactSpec(
        generation = generation,
        languageTag = languageTag,
        assetPath = "fixture-$languageTag-$generation.zlib",
        expectedCompressedSize = minOf(compressed.size.toLong(), maxCompressedSize),
        expectedCompressedSha256 = sha256(compressed),
        expectedRawSize = expectedRawSize,
        expectedRawSha256 = sha256(raw),
        expectedHeadCount = ByteBuffer.wrap(raw, 16, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffff_ffffL,
        maxCompressedSize = maxCompressedSize,
        maxRawSize = maxRawSize,
    )

    fun compress(raw: ByteArray, dictionary: ByteArray? = null): ByteArray {
        val deflater = Deflater(9, false)
        if (dictionary != null) deflater.setDictionary(dictionary)
        deflater.setInput(raw)
        deflater.finish()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()
        return output.toByteArray()
    }

    fun refreshEmbeddedChecksum(input: ByteArray): ByteArray {
        val result = input.copyOf()
        result.fill(0, 64, 96)
        val checksum = MessageDigest.getInstance("SHA-256").digest(result)
        checksum.copyInto(result, 64)
        return result
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
}

internal class CountingBigramAssetProvider(
    private val assets: MutableMap<Int, ByteArray>,
) : BigramAssetInputProvider {
    var opens = 0

    override fun open(spec: BigramArtifactSpec): java.io.InputStream {
        opens++
        return (assets[spec.generation] ?: error("missing fixture")).inputStream()
    }
}
