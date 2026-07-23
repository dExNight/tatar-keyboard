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
) : PrefixComputer {
    override fun lookup(normalizedPrefixUtf8: ImmutableUtf8Prefix): List<String> {
        if (normalizedPrefixUtf8.byteCount == 0 ||
            normalizedPrefixUtf8.byteCount > MAX_PREFIX_BYTES ||
            !isValidUtf8Scalar(normalizedPrefixUtf8)
        ) {
            return emptyList()
        }
        return try {
            val start = lowerBound(normalizedPrefixUtf8)
            val end = upperBound(normalizedPrefixUtf8, start)
            if (start >= end) return emptyList()

            val rankedIndices = IntArray(MAX_RESULTS)
            val rankedFrequencies = LongArray(MAX_RESULTS)
            var resultCount = 0
            for (index in start until end) {
                if (wordEquals(index, normalizedPrefixUtf8)) continue
                val frequency = frequencyAt(index)
                var insertion = resultCount
                for (slot in 0 until resultCount) {
                    if (ranksBefore(index, frequency, rankedIndices[slot], rankedFrequencies[slot])) {
                        insertion = slot
                        break
                    }
                }
                if (insertion >= MAX_RESULTS) continue
                val newCount = minOf(MAX_RESULTS, resultCount + 1)
                for (slot in newCount - 1 downTo insertion + 1) {
                    rankedIndices[slot] = rankedIndices[slot - 1]
                    rankedFrequencies[slot] = rankedFrequencies[slot - 1]
                }
                rankedIndices[insertion] = index
                rankedFrequencies[insertion] = frequency
                resultCount = newCount
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

    private fun lowerBound(prefix: ImmutableUtf8Prefix): Int {
        var low = 0
        var high = entryCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareWholeWordToPrefix(middle, prefix) < 0) low = middle + 1
            else high = middle
        }
        return low
    }

    private fun upperBound(prefix: ImmutableUtf8Prefix, lowHint: Int): Int {
        var low = lowHint
        var high = entryCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareWordToPrefixBlock(middle, prefix) <= 0) low = middle + 1
            else high = middle
        }
        return low
    }

    private fun compareWholeWordToPrefix(index: Int, prefix: ImmutableUtf8Prefix): Int {
        val start = wordStart(index)
        val length = wordEnd(index) - start
        val shared = minOf(length, prefix.byteCount)
        for (offset in 0 until shared) {
            val difference = unsignedByte(start + offset) - prefix.byteAt(offset)
            if (difference != 0) return difference
        }
        return length - prefix.byteCount
    }

    /** Words beginning with prefix compare equal, which gives the exclusive range end. */
    private fun compareWordToPrefixBlock(index: Int, prefix: ImmutableUtf8Prefix): Int {
        val start = wordStart(index)
        val length = wordEnd(index) - start
        val shared = minOf(length, prefix.byteCount)
        for (offset in 0 until shared) {
            val difference = unsignedByte(start + offset) - prefix.byteAt(offset)
            if (difference != 0) return difference
        }
        return if (length < prefix.byteCount) -1 else 0
    }

    private fun wordEquals(index: Int, prefix: ImmutableUtf8Prefix): Boolean {
        val start = wordStart(index)
        if (wordEnd(index) - start != prefix.byteCount) return false
        for (offset in 0 until prefix.byteCount) {
            if (unsignedByte(start + offset) != prefix.byteAt(offset)) return false
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
