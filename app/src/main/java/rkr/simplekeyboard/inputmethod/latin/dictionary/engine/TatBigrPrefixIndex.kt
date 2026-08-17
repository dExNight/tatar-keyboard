package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BigramTableIdentity(
    val generation: Int,
    val languageTag: String,
    val schemaId: Int,
    val formatVersion: Int,
    val rawSha256: String,
)

/**
 * Predicts up to three successor words for an exact, already-normalized context word — the E5c
 * read side of the E5b TATBIGR schema-2 table (`docs/DICTIONARY-E5B.md`).
 *
 * Kept as a SEPARATE interface from [PrefixComputer] rather than widening its `lookup` signature:
 * PROPOSALS.md ("E5c. Вид запроса") says the owner of state must know which kind of result it
 * holds so it can choose the right commit path, and a bare `List<String>` cannot carry that on
 * its own — the same reasoning [ClassifiedPrefixComputer] and [KeyNeighborSink] already apply by
 * staying separate interfaces instead of reshaping the frozen one.
 */
internal fun interface NextWordComputer {
    fun predict(normalizedContextWordUtf8: ImmutableUtf8Prefix): List<String>
}

/**
 * Zero-allocation reader for one mapped TATBIGR schema-2 file, mirroring [TdictPrefixIndex] in
 * every way that matters: [open] re-validates the structural invariants a lookup depends on
 * (bounds, monotonicity, sort order) instead of trusting that `TatBigrValidator` was the only
 * thing ever standing between this buffer and disk; [predict] allocates nothing except the
 * decoded result strings themselves.
 *
 * The read PROPOSALS.md ("E5c. Чтение предсказаний") names is narrower than [TdictPrefixIndex]'s:
 * one binary search over the head-word block for an EXACT match (not a prefix range — there is
 * no such thing as a partial next-word context), then at most `min(3, that head's success count)`
 * u32 ids decoded into strings, in the order the E5b generator already fixed at packing time
 * (count descending, tie code-point ascending) — no re-ranking happens here.
 */
class TatBigrPrefixIndex private constructor(
    private val bytes: ByteBuffer,
    val identity: BigramTableIdentity,
    private val headCount: Int,
    private val headOffsetsOffset: Int,
    private val headBlobOffset: Int,
    private val successRangesOffset: Int,
    private val successIdsOffset: Int,
    private val successOffsetsOffset: Int,
    private val successBlobOffset: Int,
) : NextWordComputer {

    private val queryScratch = ByteArray(MAX_WORD_BYTES)

    override fun predict(normalizedContextWordUtf8: ImmutableUtf8Prefix): List<String> {
        val length = normalizedContextWordUtf8.byteCount
        if (length == 0 || length > MAX_WORD_BYTES || !isValidUtf8Scalar(normalizedContextWordUtf8)) {
            return emptyList()
        }
        return try {
            for (offset in 0 until length) {
                queryScratch[offset] = normalizedContextWordUtf8.byteAt(offset).toByte()
            }
            val head = exactHead(queryScratch, length) ?: return emptyList()
            val start = successRangeAt(head)
            val end = successRangeAt(head + 1)
            val count = minOf(MAX_RESULTS, end - start)
            if (count <= 0) return emptyList()
            ArrayList<String>(count).also { result ->
                for (slot in 0 until count) {
                    result += decodeSuccessWord(successIdAt(start + slot))
                }
            }
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    /** Exact match only — [lowerBound] then a length+byte equality check, no prefix range. */
    private fun exactHead(query: ByteArray, queryLength: Int): Int? {
        val candidate = lowerBound(query, queryLength)
        if (candidate >= headCount) return null
        return if (headWordEquals(candidate, query, queryLength)) candidate else null
    }

    private fun lowerBound(query: ByteArray, queryLength: Int): Int {
        var low = 0
        var high = headCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareHeadWordToQuery(middle, query, queryLength) < 0) low = middle + 1 else high = middle
        }
        return low
    }

    private fun compareHeadWordToQuery(index: Int, query: ByteArray, queryLength: Int): Int {
        val start = headWordStart(index)
        val length = headWordEnd(index) - start
        val shared = minOf(length, queryLength)
        for (offset in 0 until shared) {
            val difference = unsignedByte(start + offset) - (query[offset].toInt() and 0xff)
            if (difference != 0) return difference
        }
        return length - queryLength
    }

    private fun compareHeadWords(firstIndex: Int, secondIndex: Int): Int {
        val firstStart = headWordStart(firstIndex)
        val secondStart = headWordStart(secondIndex)
        val firstLength = headWordEnd(firstIndex) - firstStart
        val secondLength = headWordEnd(secondIndex) - secondStart
        val shared = minOf(firstLength, secondLength)
        for (offset in 0 until shared) {
            val difference = unsignedByte(firstStart + offset) - unsignedByte(secondStart + offset)
            if (difference != 0) return difference
        }
        return firstLength - secondLength
    }

    private fun headWordEquals(index: Int, query: ByteArray, queryLength: Int): Boolean {
        val start = headWordStart(index)
        if (headWordEnd(index) - start != queryLength) return false
        for (offset in 0 until queryLength) {
            if (unsignedByte(start + offset) != (query[offset].toInt() and 0xff)) return false
        }
        return true
    }

    private fun headWordStart(index: Int): Int = headBlobOffset + headOffsetAt(index)
    private fun headWordEnd(index: Int): Int = headBlobOffset + headOffsetAt(index + 1)
    private fun headOffsetAt(index: Int): Int = bytes.getInt(headOffsetsOffset + index * U32_BYTES)

    private fun successRangeAt(index: Int): Int = bytes.getInt(successRangesOffset + index * U32_BYTES)
    private fun successIdAt(position: Int): Int = bytes.getInt(successIdsOffset + position * U32_BYTES)

    private fun decodeSuccessWord(id: Int): String {
        val start = successBlobOffset + successOffsetAt(id)
        val end = successBlobOffset + successOffsetAt(id + 1)
        val encoded = ByteArray(end - start)
        for (offset in encoded.indices) encoded[offset] = bytes.get(start + offset)
        return String(encoded, Charsets.UTF_8)
    }

    private fun successOffsetAt(index: Int): Int = bytes.getInt(successOffsetsOffset + index * U32_BYTES)

    private fun unsignedByte(offset: Int): Int = bytes.get(offset).toInt() and 0xff

    companion object {
        private const val HEADER_SIZE = 96
        private const val CHECKSUM_ALGORITHM_SHA256 = 1
        private const val U32_BYTES = 4
        internal const val MAX_RESULTS = 3
        internal const val MAX_WORD_BYTES = 128
        private const val MAX_U32 = 0xffff_ffffL
        private val MAGIC = "TATBIGR\u0000".toByteArray(Charsets.US_ASCII)

        fun open(
            source: ByteBuffer,
            identity: BigramTableIdentity,
            expectedHeadCount: Long,
            expectedRawSize: Long,
        ): TatBigrPrefixIndex? = try {
            require(identity.generation > 0)
            require(identity.schemaId == 2)
            require(identity.formatVersion == 1)
            require(expectedHeadCount in 1..Int.MAX_VALUE.toLong())
            require(expectedRawSize in HEADER_SIZE.toLong()..Int.MAX_VALUE.toLong())
            require(source.limit().toLong() == expectedRawSize)

            val duplicate = source.asReadOnlyBuffer()
            duplicate.position(0)
            val buffer = duplicate.slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
            require(buffer.limit() >= HEADER_SIZE)
            for (index in MAGIC.indices) require(buffer.get(index) == MAGIC[index])
            require(u16(buffer, 8) == identity.schemaId)
            require(u16(buffer, 10) == identity.formatVersion)
            require(u16(buffer, 12) == HEADER_SIZE)
            require(u16(buffer, 14) == CHECKSUM_ALGORITHM_SHA256)

            val headCount = u32(buffer, 16)
            require(headCount == expectedHeadCount)
            val pairCount = u32(buffer, 20)
            val successVocabularyCount = u32(buffer, 24)
            val section1 = u32(buffer, 28)
            val section2 = u32(buffer, 32)
            val section3 = u32(buffer, 36)
            val section4 = u32(buffer, 40)
            val section5 = u32(buffer, 44)
            val section6 = u32(buffer, 48)
            val headBlobLength = u32(buffer, 52)
            val successBlobLength = u32(buffer, 56)
            val fileSize = u32(buffer, 60)

            val expectedSection1 = HEADER_SIZE.toLong()
            val expectedSection2 = expectedSection1 + U32_BYTES * (headCount + 1L)
            val expectedSection3 = expectedSection2 + headBlobLength
            val expectedSection4 = expectedSection3 + U32_BYTES * (headCount + 1L)
            val expectedSection5 = expectedSection4 + U32_BYTES * pairCount
            val expectedSection6 = expectedSection5 + U32_BYTES * (successVocabularyCount + 1L)
            val expectedFileSize = expectedSection6 + successBlobLength
            require(section1 == expectedSection1)
            require(section2 == expectedSection2)
            require(section3 == expectedSection3)
            require(section4 == expectedSection4)
            require(section5 == expectedSection5)
            require(section6 == expectedSection6)
            require(fileSize == expectedFileSize && fileSize == expectedRawSize)
            require(expectedSection6 <= Int.MAX_VALUE && expectedFileSize <= Int.MAX_VALUE)
            require(pairCount <= Int.MAX_VALUE && successVocabularyCount <= Int.MAX_VALUE)

            val index = TatBigrPrefixIndex(
                buffer,
                identity,
                headCount.toInt(),
                section1.toInt(),
                section2.toInt(),
                section3.toInt(),
                section4.toInt(),
                section5.toInt(),
                section6.toInt(),
            )

            // Re-validate every invariant the reads above depend on for correctness (not merely for
            // memory safety) — the same defensive posture as TdictPrefixIndex.open, applied to six
            // sections instead of three.
            require(index.headOffsetAt(0) == 0)
            var previousHeadOffset = 0
            for (headIndex in 0 until index.headCount) {
                val nextOffset = index.headOffsetAt(headIndex + 1)
                require(nextOffset > previousHeadOffset)
                require(nextOffset.toLong() <= headBlobLength)
                if (headIndex > 0) require(index.compareHeadWords(headIndex - 1, headIndex) < 0)
                previousHeadOffset = nextOffset
            }
            require(previousHeadOffset.toLong() == headBlobLength)

            require(index.successRangeAt(0) == 0)
            var previousRange = 0
            for (headIndex in 0 until index.headCount) {
                val nextRange = index.successRangeAt(headIndex + 1)
                require(nextRange > previousRange) // no empty range — E5b never packs one
                require(nextRange.toLong() <= pairCount)
                previousRange = nextRange
            }
            require(previousRange.toLong() == pairCount)

            for (position in 0 until pairCount.toInt()) {
                require(index.successIdAt(position).toLong() and MAX_U32 < successVocabularyCount)
            }

            require(index.successOffsetAt(0) == 0)
            var previousSuccessOffset = 0
            for (wordIndex in 0 until successVocabularyCount.toInt()) {
                val nextOffset = index.successOffsetAt(wordIndex + 1)
                require(nextOffset > previousSuccessOffset)
                require(nextOffset.toLong() <= successBlobLength)
                previousSuccessOffset = nextOffset
            }
            require(previousSuccessOffset.toLong() == successBlobLength)

            index
        } catch (_: RuntimeException) {
            null
        }

        private fun u16(buffer: ByteBuffer, offset: Int): Int =
            buffer.getShort(offset).toInt() and 0xffff

        private fun u32(buffer: ByteBuffer, offset: Int): Long =
            buffer.getInt(offset).toLong() and MAX_U32
    }
}
