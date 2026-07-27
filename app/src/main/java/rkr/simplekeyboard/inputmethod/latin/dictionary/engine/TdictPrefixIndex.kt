package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DictionaryIdentity(
    val generation: Int,
    val schemaId: Int,
    val formatVersion: Int,
    val rawSha256: String,
)

internal fun interface PrefixComputer {
    fun lookup(normalizedPrefixUtf8: ImmutableUtf8Prefix): List<String>
}

/**
 * Receives the current key-neighbor table for a computer that runs a fuzzy pass. Kept separate from
 * [PrefixComputer] so the frozen `lookup` signature never changes.
 */
internal interface KeyNeighborSink {
    fun updateKeyNeighbors(table: KeyNeighborTable?)
}

/** Strict scalar UTF-8 validation without a decoder or temporary objects. */
internal fun isValidUtf8Scalar(bytes: ByteArray): Boolean =
    isValidUtf8Scalar(bytes.size) { bytes[it].toInt() and 0xff }

private fun isValidUtf8Scalar(bytes: ImmutableUtf8Prefix): Boolean =
    isValidUtf8Scalar(bytes.byteCount) { bytes.byteAt(it) }

private inline fun isValidUtf8Scalar(size: Int, byteAt: (Int) -> Int): Boolean {
    var index = 0
    while (index < size) {
        val first = byteAt(index)
        val continuationCount: Int
        val minimumCodePoint: Int
        var codePoint: Int
        when {
            first <= 0x7f -> {
                index++
                continue
            }
            first in 0xc2..0xdf -> {
                continuationCount = 1
                minimumCodePoint = 0x80
                codePoint = first and 0x1f
            }
            first in 0xe0..0xef -> {
                continuationCount = 2
                minimumCodePoint = 0x800
                codePoint = first and 0x0f
            }
            first in 0xf0..0xf4 -> {
                continuationCount = 3
                minimumCodePoint = 0x10000
                codePoint = first and 0x07
            }
            else -> return false
        }
        if (index + continuationCount >= size) return false
        for (offset in 1..continuationCount) {
            val continuation = byteAt(index + offset)
            if (continuation !in 0x80..0xbf) return false
            codePoint = (codePoint shl 6) or (continuation and 0x3f)
        }
        if (codePoint < minimumCodePoint ||
            codePoint > 0x10ffff ||
            codePoint in 0xd800..0xdfff
        ) {
            return false
        }
        index += continuationCount + 1
    }
    return true
}

/** Immutable schema-1 reader. The supplied buffer must already have passed D1b validation. */
internal class TdictPrefixIndex private constructor(
    private val bytes: ByteBuffer,
    val identity: DictionaryIdentity,
    private val entryCount: Int,
    private val offsetsOffset: Int,
    private val frequenciesOffset: Int,
    private val blobOffset: Int,
) : PrefixComputer, KeyNeighborSink {
    // Reusable per-index scratch. The index stops being fully immutable: these buffers are touched
    // ONLY inside lookup(), whose exclusivity is guaranteed by LatestOnlyPrefixEngine serialization
    // (at most one active worker). updateKeyNeighbors() only swaps a @Volatile reference.
    private val exactScratch = ByteArray(MAX_PREFIX_BYTES)
    private val variantScratch = ByteArray(MAX_PREFIX_BYTES + VARIANT_HEADROOM)
    private val codePointScratch = IntArray(MAX_PREFIX_BYTES)
    private val rankedIndices = IntArray(MAX_RESULTS)
    private val rankedFrequencies = LongArray(MAX_RESULTS)
    private val fuzzyIndices = IntArray(MAX_RESULTS)
    private val fuzzyFrequencies = LongArray(MAX_RESULTS)

    @Volatile
    private var neighborTable: KeyNeighborTable? = null

    // Test-only observability of the last lookup's fuzzy work. These are plain ints assigned on the
    // hot path (no allocation, no logging); they let the JVM harness report measured variants and
    // visited entries and prove the fail-closed budget never trips on the typo set.
    internal var lastFuzzyVariantCount = 0
        private set
    internal var lastFuzzyVisitedCount = 0
        private set
    internal var lastFuzzyOverBudget = false
        private set

    // Fuzzy-pass accumulator, private to a single lookup() invocation and reset on each entry.
    private var fuzzyExactCount = 0
    private var fuzzyRemaining = 0
    private var fuzzyCount = 0
    private var fuzzyVisited = 0
    private var fuzzyOverBudget = false
    private var fuzzyPrefixLength = 0

    // Allocated once, so neither a lambda nor any object is created per lookup or per variant.
    private val fuzzyConsumer =
        FuzzyPrefixVariants.VariantConsumer { bytes, length -> scanVariantBlock(bytes, length) }

    override fun updateKeyNeighbors(table: KeyNeighborTable?) {
        neighborTable = table
    }

    override fun lookup(normalizedPrefixUtf8: ImmutableUtf8Prefix): List<String> {
        val prefixLength = normalizedPrefixUtf8.byteCount
        if (prefixLength == 0 ||
            prefixLength > MAX_PREFIX_BYTES ||
            !isValidUtf8Scalar(normalizedPrefixUtf8)
        ) {
            return emptyList()
        }
        return try {
            lastFuzzyVariantCount = 0
            lastFuzzyVisitedCount = 0
            lastFuzzyOverBudget = false
            for (offset in 0 until prefixLength) {
                exactScratch[offset] = normalizedPrefixUtf8.byteAt(offset).toByte()
            }
            var resultCount = collectExact(prefixLength)
            // The fuzzy level fills only cells left empty by D1, and only when the exact pass
            // returned fewer than three candidates: one check, no new state. Exact candidates are
            // never shifted or replaced.
            if (resultCount < MAX_RESULTS) {
                val table = neighborTable
                if (table != null && !table.isEmpty &&
                    countCodePointsByLeadBytes(exactScratch, prefixLength) >=
                    MIN_FUZZY_PREFIX_CODE_POINTS
                ) {
                    resultCount = collectFuzzy(prefixLength, table, resultCount)
                }
            }
            if (resultCount == 0) return emptyList()
            ArrayList<String>(resultCount).also { result ->
                for (slot in 0 until resultCount) {
                    result += decodeWord(rankedIndices[slot])
                }
            }
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    /** The frozen D1 exact pass: fills [rankedIndices] with up to [MAX_RESULTS] and returns count. */
    private fun collectExact(prefixLength: Int): Int {
        val start = lowerBound(exactScratch, prefixLength, 0)
        val end = upperBound(exactScratch, prefixLength, start)
        if (start >= end) return 0
        var resultCount = 0
        for (index in start until end) {
            if (wordEquals(index, exactScratch, prefixLength)) continue
            resultCount = insertRanked(
                rankedIndices, rankedFrequencies, resultCount, MAX_RESULTS,
                index, frequencyAt(index),
            )
        }
        return resultCount
    }

    /**
     * Fills the cells the exact pass left empty with the best fuzzy candidates, ranked among
     * themselves by the same rule (frequency descending, then code-point lexical ascending). Exact
     * candidates are never touched. Returns the total candidate count.
     *
     * The whole fuzzy level is dropped (returns [exactCount]) if variant generation or the block
     * scan trips a fixed budget: the level is discarded in full, never in part.
     */
    private fun collectFuzzy(prefixLength: Int, table: KeyNeighborTable, exactCount: Int): Int {
        fuzzyExactCount = exactCount
        fuzzyRemaining = MAX_RESULTS - exactCount
        fuzzyCount = 0
        fuzzyVisited = 0
        fuzzyOverBudget = false
        fuzzyPrefixLength = prefixLength
        // Classes #1, #2 and #3 share one variant budget: the total number of variants generated
        // across all three must stay within MAX_FUZZY_VARIANTS, and the class edit type never
        // affects ranking. Any single class returning -1 (its slice of the budget exceeded) drops
        // the whole fuzzy level, never a part of it.
        val classOne = FuzzyPrefixVariants.generateLongPressVariants(
            exactScratch, prefixLength, table, codePointScratch, variantScratch,
            MAX_FUZZY_VARIANTS, fuzzyConsumer,
        )
        if (classOne < 0 || fuzzyOverBudget) {
            lastFuzzyOverBudget = true
            lastFuzzyVisitedCount = fuzzyVisited
            return exactCount
        }
        val classTwo = FuzzyPrefixVariants.generateGeometricVariants(
            exactScratch, prefixLength, table, codePointScratch, variantScratch,
            MAX_FUZZY_VARIANTS - classOne, fuzzyConsumer,
        )
        if (classTwo < 0 || fuzzyOverBudget) {
            lastFuzzyOverBudget = true
            lastFuzzyVisitedCount = fuzzyVisited
            return exactCount
        }
        val classThree = FuzzyPrefixVariants.generateTranspositionVariants(
            exactScratch, prefixLength, codePointScratch, variantScratch,
            MAX_FUZZY_VARIANTS - classOne - classTwo, fuzzyConsumer,
        )
        if (classThree < 0 || fuzzyOverBudget) {
            lastFuzzyOverBudget = true
            lastFuzzyVisitedCount = fuzzyVisited
            return exactCount
        }
        lastFuzzyVariantCount = classOne + classTwo + classThree
        lastFuzzyVisitedCount = fuzzyVisited
        for (slot in 0 until fuzzyCount) {
            rankedIndices[exactCount + slot] = fuzzyIndices[slot]
            rankedFrequencies[exactCount + slot] = fuzzyFrequencies[slot]
        }
        return exactCount + fuzzyCount
    }

    /** Scans one variant's dictionary block, ranking its candidates into [fuzzyIndices]. */
    private fun scanVariantBlock(variantBytes: ByteArray, variantLength: Int) {
        if (fuzzyOverBudget) return
        val start = lowerBound(variantBytes, variantLength, 0)
        val end = upperBound(variantBytes, variantLength, start)
        var index = start
        while (index < end) {
            fuzzyVisited++
            if (fuzzyVisited > MAX_FUZZY_VISITED) {
                fuzzyOverBudget = true
                return
            }
            // Exact-word exclusion applies on both levels: never suggest the typed word itself.
            // A word is de-duplicated by dictionary index so it can never occupy two cells.
            if (!wordEquals(index, exactScratch, fuzzyPrefixLength) &&
                !containsIndex(rankedIndices, fuzzyExactCount, index) &&
                !containsIndex(fuzzyIndices, fuzzyCount, index)
            ) {
                fuzzyCount = insertRanked(
                    fuzzyIndices, fuzzyFrequencies, fuzzyCount, fuzzyRemaining,
                    index, frequencyAt(index),
                )
            }
            index++
        }
    }

    /** Bounded insertion sort shared by both levels; returns the new count. */
    private fun insertRanked(
        indices: IntArray,
        frequencies: LongArray,
        count: Int,
        capacity: Int,
        candidateIndex: Int,
        candidateFrequency: Long,
    ): Int {
        var insertion = count
        for (slot in 0 until count) {
            if (ranksBefore(candidateIndex, candidateFrequency, indices[slot], frequencies[slot])) {
                insertion = slot
                break
            }
        }
        if (insertion >= capacity) return count
        val newCount = minOf(capacity, count + 1)
        for (slot in newCount - 1 downTo insertion + 1) {
            indices[slot] = indices[slot - 1]
            frequencies[slot] = frequencies[slot - 1]
        }
        indices[insertion] = candidateIndex
        frequencies[insertion] = candidateFrequency
        return newCount
    }

    private fun containsIndex(indices: IntArray, count: Int, value: Int): Boolean {
        for (slot in 0 until count) {
            if (indices[slot] == value) return true
        }
        return false
    }

    private fun lowerBound(query: ByteArray, queryLength: Int, from: Int): Int {
        var low = from
        var high = entryCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareWholeWordToPrefix(middle, query, queryLength) < 0) low = middle + 1
            else high = middle
        }
        return low
    }

    private fun upperBound(query: ByteArray, queryLength: Int, lowHint: Int): Int {
        var low = lowHint
        var high = entryCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareWordToPrefixBlock(middle, query, queryLength) <= 0) low = middle + 1
            else high = middle
        }
        return low
    }

    private fun compareWholeWordToPrefix(index: Int, query: ByteArray, queryLength: Int): Int {
        val start = wordStart(index)
        val length = wordEnd(index) - start
        val shared = minOf(length, queryLength)
        for (offset in 0 until shared) {
            val difference = unsignedByte(start + offset) - (query[offset].toInt() and 0xff)
            if (difference != 0) return difference
        }
        return length - queryLength
    }

    /** Words beginning with the query compare equal, which gives the exclusive range end. */
    private fun compareWordToPrefixBlock(index: Int, query: ByteArray, queryLength: Int): Int {
        val start = wordStart(index)
        val length = wordEnd(index) - start
        val shared = minOf(length, queryLength)
        for (offset in 0 until shared) {
            val difference = unsignedByte(start + offset) - (query[offset].toInt() and 0xff)
            if (difference != 0) return difference
        }
        return if (length < queryLength) -1 else 0
    }

    private fun wordEquals(index: Int, query: ByteArray, queryLength: Int): Boolean {
        val start = wordStart(index)
        if (wordEnd(index) - start != queryLength) return false
        for (offset in 0 until queryLength) {
            if (unsignedByte(start + offset) != (query[offset].toInt() and 0xff)) return false
        }
        return true
    }

    private fun ranksBefore(
        candidateIndex: Int,
        candidateFrequency: Long,
        rankedIndex: Int,
        rankedFrequency: Long,
    ): Boolean = when {
        candidateFrequency != rankedFrequency -> candidateFrequency > rankedFrequency
        else -> compareWords(candidateIndex, rankedIndex) < 0
    }

    private fun compareWords(firstIndex: Int, secondIndex: Int): Int {
        val firstStart = wordStart(firstIndex)
        val secondStart = wordStart(secondIndex)
        val firstLength = wordEnd(firstIndex) - firstStart
        val secondLength = wordEnd(secondIndex) - secondStart
        val shared = minOf(firstLength, secondLength)
        for (offset in 0 until shared) {
            val difference = unsignedByte(firstStart + offset) - unsignedByte(secondStart + offset)
            if (difference != 0) return difference
        }
        return firstLength - secondLength
    }

    private fun decodeWord(index: Int): String {
        val start = wordStart(index)
        val length = wordEnd(index) - start
        val encoded = ByteArray(length)
        for (offset in encoded.indices) encoded[offset] = bytes.get(start + offset)
        return String(encoded, Charsets.UTF_8)
    }

    private fun wordStart(index: Int): Int = blobOffset + offsetAt(index)

    private fun wordEnd(index: Int): Int = blobOffset + offsetAt(index + 1)

    private fun offsetAt(index: Int): Int = bytes.getInt(offsetsOffset + index * U32_BYTES)

    private fun frequencyAt(index: Int): Long =
        bytes.getInt(frequenciesOffset + index * U32_BYTES).toLong() and MAX_U32

    private fun unsignedByte(offset: Int): Int = unsigned(bytes.get(offset))

    companion object {
        private const val HEADER_SIZE = 72
        private const val CHECKSUM_ALGORITHM_SHA256 = 1
        private const val U32_BYTES = 4
        private const val MAX_RESULTS = 3
        internal const val MAX_PREFIX_BYTES = 128
        private const val MAX_U32 = 0xffff_ffffL

        // Fuzzy pass. Extra headroom on the variant buffer covers a re-encode that is a few bytes
        // longer than the prefix; edit classes #1/#2 (single-letter substitution) and #3
        // (transposition) keep the code-point length identical in practice.
        private const val VARIANT_HEADROOM = 8

        // The fuzzy pass (all three E3b classes) needs at least three code points; the count is
        // taken off the UTF-8 lead bytes so a two-letter Cyrillic prefix (four bytes) is rejected.
        private const val MIN_FUZZY_PREFIX_CODE_POINTS = 3

        // Fixed budgets. Exceeding either drops the whole fuzzy level, never a part of it. The
        // variant budget bounds classes #1+#2+#3 combined; it sits above the E3b offline reference
        // (p95 33 variants, max 39) with headroom, so a correct implementation never trips it on the
        // typo set — a fact the recovery test asserts. The visited budget sits far above the E3b
        // reference (p95 133 entries, max 522).
        private const val MAX_FUZZY_VARIANTS = 64
        private const val MAX_FUZZY_VISITED = 8192
        private val MAGIC = "TATDICT\u0000".toByteArray(Charsets.US_ASCII)

        fun open(
            source: ByteBuffer,
            identity: DictionaryIdentity,
            expectedEntryCount: Long,
            expectedRawSize: Long,
        ): TdictPrefixIndex? = try {
            require(identity.generation > 0)
            require(identity.schemaId == 1)
            require(identity.formatVersion == 1)
            require(expectedEntryCount in 1..Int.MAX_VALUE.toLong())
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

            val count = u32(buffer, 16)
            require(count == expectedEntryCount)
            val offsets = u32(buffer, 20)
            val frequencies = u32(buffer, 24)
            val blob = u32(buffer, 28)
            val blobSize = u32(buffer, 32)
            val fileSize = u32(buffer, 36)
            val expectedFrequencies = HEADER_SIZE.toLong() + U32_BYTES * (count + 1L)
            val expectedBlob = expectedFrequencies + U32_BYTES * count
            val expectedFileSize = expectedBlob + blobSize
            require(offsets == HEADER_SIZE.toLong())
            require(frequencies == expectedFrequencies)
            require(blob == expectedBlob)
            require(fileSize == expectedFileSize && fileSize == expectedRawSize)
            require(expectedBlob <= Int.MAX_VALUE && expectedFileSize <= Int.MAX_VALUE)

            val entryCount = count.toInt()
            val offsetsOffset = offsets.toInt()
            val frequenciesOffset = frequencies.toInt()
            val blobOffset = blob.toInt()
            val index = TdictPrefixIndex(
                buffer,
                identity,
                entryCount,
                offsetsOffset,
                frequenciesOffset,
                blobOffset,
            )
            require(index.offsetAt(0) == 0)
            var previousOffset = 0
            for (wordIndex in 0 until entryCount) {
                val nextOffset = index.offsetAt(wordIndex + 1)
                require(nextOffset > previousOffset)
                require(nextOffset.toLong() <= blobSize)
                require(index.frequencyAt(wordIndex) != 0L)
                if (wordIndex > 0) require(index.compareWords(wordIndex - 1, wordIndex) < 0)
                previousOffset = nextOffset
            }
            require(previousOffset.toLong() == blobSize)
            index
        } catch (_: RuntimeException) {
            null
        }

        private fun u16(buffer: ByteBuffer, offset: Int): Int =
            buffer.getShort(offset).toInt() and 0xffff

        private fun u32(buffer: ByteBuffer, offset: Int): Long =
            buffer.getInt(offset).toLong() and MAX_U32

        private fun unsigned(byte: Byte): Int = byte.toInt() and 0xff
    }
}
