package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import androidx.annotation.Keep
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.DataFormatException
import java.util.zip.Inflater

class BigramValidationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

data class ValidatedBigramTable(
    val rawSize: Long,
    val headCount: Long,
    val pairCount: Long,
    val successVocabularyCount: Long,
    val schemaId: Int,
    val formatVersion: Int,
    val rawSha256: String,
)

/**
 * Strict validator for the TATBIGR schema-2 format (PROPOSALS.md, "E5b. Секции" — six sections,
 * no padding, no separators). Mirrors [TdictValidator]'s two-phase shape (inflate-with-digest,
 * then validate-the-decompressed-structure) but the section layout itself is different: three
 * sections for schema 1's flat word list, six for schema 2's per-head success ranges. The u32
 * helpers, the constant-time hex compare and the "zero the digest bytes, then re-hash" checksum
 * trick are the same technique at a different offset (64, not 40 — twice as many header u32
 * fields for six sections instead of three), duplicated rather than shared because the two
 * validators check structurally different things and a shared base would need to abstract over
 * that difference for no real savings.
 */
@Keep
class TatBigrValidator {
    fun inflateAsset(
        source: java.io.InputStream,
        destination: OutputStream,
        spec: BigramArtifactSpec,
    ): InflatedAsset {
        val inflater = Inflater(false)
        val compressedDigest = MessageDigest.getInstance("SHA-256")
        val inputBuffer = ByteArray(BUFFER_SIZE)
        val outputBuffer = ByteArray(BUFFER_SIZE)
        var compressedSize = 0L
        var rawSize = 0L
        val input = BufferedInputStream(source, BUFFER_SIZE)
        val output = BufferedOutputStream(destination, BUFFER_SIZE)
        try {
            while (!inflater.finished()) {
                if (inflater.needsDictionary()) {
                    throw BigramValidationException("zlib preset dictionary is unsupported")
                }
                if (inflater.needsInput()) {
                    val count = input.read(inputBuffer)
                    if (count < 0) {
                        throw BigramValidationException("truncated zlib stream")
                    }
                    compressedSize += count
                    if (compressedSize > spec.maxCompressedSize) {
                        throw BigramValidationException("compressed size limit exceeded")
                    }
                    compressedDigest.update(inputBuffer, 0, count)
                    inflater.setInput(inputBuffer, 0, count)
                }

                val count = try {
                    inflater.inflate(outputBuffer)
                } catch (error: DataFormatException) {
                    throw BigramValidationException("invalid zlib stream", error)
                }
                if (count > 0) {
                    rawSize += count
                    if (rawSize > spec.maxRawSize) {
                        throw BigramValidationException("raw size limit exceeded")
                    }
                    output.write(outputBuffer, 0, count)
                } else if (!inflater.finished() && !inflater.needsInput() &&
                    !inflater.needsDictionary()
                ) {
                    throw BigramValidationException("zlib inflater made no progress")
                }
            }

            if (inflater.remaining != 0 || input.read() != -1) {
                throw BigramValidationException("trailing or concatenated zlib data")
            }
            output.flush()
        } finally {
            inflater.end()
        }

        val compressedSha = compressedDigest.digest().toHex()
        if (compressedSize != spec.expectedCompressedSize) {
            throw BigramValidationException("unexpected compressed size")
        }
        if (!constantTimeHexEquals(compressedSha, spec.expectedCompressedSha256)) {
            throw BigramValidationException("unexpected compressed SHA-256")
        }
        if (rawSize != spec.expectedRawSize) {
            throw BigramValidationException("unexpected raw size")
        }
        return InflatedAsset(compressedSize, compressedSha, rawSize)
    }

    fun validateRaw(file: File, spec: BigramArtifactSpec): ValidatedBigramTable {
        val length = file.length()
        if (length > spec.maxRawSize) {
            throw BigramValidationException("raw size limit exceeded")
        }
        if (length < TatBigrFormat.HEADER_SIZE) {
            throw BigramValidationException("raw table is shorter than its header")
        }
        if (length != spec.expectedRawSize) {
            throw BigramValidationException("unexpected raw size")
        }

        val header = ByteArray(TatBigrFormat.HEADER_SIZE)
        RandomAccessFile(file, "r").use { raw -> raw.readFully(header) }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(8)
        buffer.get(magic)
        if (!magic.contentEquals(TatBigrFormat.MAGIC.toByteArray(StandardCharsets.US_ASCII))) {
            throw BigramValidationException("wrong bigram table magic")
        }
        val schemaId = buffer.short.toInt() and 0xffff
        val formatVersion = buffer.short.toInt() and 0xffff
        val headerSize = buffer.short.toInt() and 0xffff
        val checksumAlgorithm = buffer.short.toInt() and 0xffff
        val headCount = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val pairCount = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val successVocabularyCount = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val section1Offset = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val section2Offset = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val section3Offset = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val section4Offset = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val section5Offset = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val section6Offset = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val headBlobLength = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val successBlobLength = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val declaredFileSize = buffer.int.toLong() and TatBigrFormat.MAX_U32
        val storedChecksum = ByteArray(TatBigrFormat.CHECKSUM_SIZE)
        buffer.get(storedChecksum)

        if (schemaId != spec.schemaId || schemaId != TatBigrFormat.SCHEMA_ID) {
            throw BigramValidationException("unsupported schema id")
        }
        if (formatVersion != spec.formatVersion || formatVersion != TatBigrFormat.FORMAT_VERSION) {
            throw BigramValidationException("unsupported format version")
        }
        if (headerSize != TatBigrFormat.HEADER_SIZE) {
            throw BigramValidationException("unexpected header size")
        }
        if (checksumAlgorithm != TatBigrFormat.CHECKSUM_ALGORITHM_SHA256) {
            throw BigramValidationException("unsupported checksum algorithm")
        }
        if (headCount == 0L || headCount != spec.expectedHeadCount) {
            throw BigramValidationException("unexpected head count")
        }

        val expectedSection1 = TatBigrFormat.HEADER_SIZE.toLong()
        val expectedSection2 = checkedAdd(expectedSection1, checkedMultiply(4L, checkedAdd(headCount, 1L)))
        val expectedSection3 = checkedAdd(expectedSection2, headBlobLength)
        val expectedSection4 = checkedAdd(expectedSection3, checkedMultiply(4L, checkedAdd(headCount, 1L)))
        val expectedSection5 = checkedAdd(expectedSection4, checkedMultiply(4L, pairCount))
        val expectedSection6 =
            checkedAdd(expectedSection5, checkedMultiply(4L, checkedAdd(successVocabularyCount, 1L)))
        val expectedFileSize = checkedAdd(expectedSection6, successBlobLength)
        if (expectedSection2 > TatBigrFormat.MAX_U32 || expectedSection3 > TatBigrFormat.MAX_U32 ||
            expectedSection4 > TatBigrFormat.MAX_U32 || expectedSection5 > TatBigrFormat.MAX_U32 ||
            expectedSection6 > TatBigrFormat.MAX_U32 || expectedFileSize > TatBigrFormat.MAX_U32
        ) {
            throw BigramValidationException("section arithmetic exceeds u32")
        }
        if (section1Offset != expectedSection1 || section2Offset != expectedSection2 ||
            section3Offset != expectedSection3 || section4Offset != expectedSection4 ||
            section5Offset != expectedSection5 || section6Offset != expectedSection6 ||
            declaredFileSize != expectedFileSize || declaredFileSize != length
        ) {
            throw BigramValidationException("noncanonical section layout")
        }

        val digests = calculateDigests(file)
        if (!MessageDigest.isEqual(storedChecksum, digests.zeroedChecksum)) {
            throw BigramValidationException("SHA-256 checksum mismatch")
        }
        val rawSha = digests.fullChecksum.toHex()
        if (!constantTimeHexEquals(rawSha, spec.expectedRawSha256)) {
            throw BigramValidationException("unexpected raw SHA-256")
        }

        RandomAccessFile(file, "r").use { raw ->
            val headOffsets = readCanonicalOffsetArray(
                raw, section1Offset, headCount, headBlobLength, "head offset",
            )
            var previousHeadBytes: ByteArray? = null
            for (index in 0 until checkedArraySize(headCount)) {
                previousHeadBytes = validateNextWord(
                    raw, section2Offset, headOffsets, index, previousHeadBytes, "head",
                )
            }

            val successRanges = readCanonicalOffsetArray(
                raw, section3Offset, headCount, pairCount, "success range",
            )
            for (index in 0 until checkedArraySize(headCount)) {
                if (successRanges[index] >= successRanges[index + 1]) {
                    throw BigramValidationException("head $index has an empty success range")
                }
            }

            raw.seek(section4Offset)
            repeat(checkedArraySize(pairCount)) {
                val id = readU32Le(raw)
                if (id >= successVocabularyCount) {
                    throw BigramValidationException("success id is >= vocabulary size")
                }
            }

            val successOffsets = readCanonicalOffsetArray(
                raw, section5Offset, successVocabularyCount, successBlobLength, "success offset",
            )
            var previousSuccessBytes: ByteArray? = null
            for (index in 0 until checkedArraySize(successVocabularyCount)) {
                previousSuccessBytes = validateNextWord(
                    raw, section6Offset, successOffsets, index, previousSuccessBytes, "success",
                )
            }
        }

        return ValidatedBigramTable(
            rawSize = length,
            headCount = headCount,
            pairCount = pairCount,
            successVocabularyCount = successVocabularyCount,
            schemaId = schemaId,
            formatVersion = formatVersion,
            rawSha256 = rawSha,
        )
    }

    /** Reads an N+1-entry u32 offset array and checks it is non-decreasing and bounds [totalSize]. */
    private fun readCanonicalOffsetArray(
        raw: RandomAccessFile,
        sectionOffset: Long,
        count: Long,
        totalSize: Long,
        label: String,
    ): LongArray {
        raw.seek(sectionOffset)
        val offsets = LongArray(checkedArraySize(count + 1L))
        var previous = readU32Le(raw)
        if (previous != 0L) {
            throw BigramValidationException("first $label must be zero")
        }
        offsets[0] = previous
        for (index in 1 until offsets.size) {
            val offset = readU32Le(raw)
            if (offset > totalSize || offset < previous) {
                throw BigramValidationException("$label array is not non-decreasing")
            }
            offsets[index] = offset
            previous = offset
        }
        if (offsets.last() != totalSize) {
            throw BigramValidationException("terminal $label does not equal section size")
        }
        return offsets
    }

    /** Reads one word from [blobOffset] using [offsets], checks UTF-8 and strict ascending order. */
    private fun validateNextWord(
        raw: RandomAccessFile,
        blobOffset: Long,
        offsets: LongArray,
        index: Int,
        previous: ByteArray?,
        label: String,
    ): ByteArray {
        val wordSize = offsets[index + 1] - offsets[index]
        if (wordSize <= 0L) {
            throw BigramValidationException("empty $label word")
        }
        val encoded = ByteArray(wordSize.toInt())
        raw.seek(blobOffset + offsets[index])
        raw.readFully(encoded)
        decodeStrictUtf8(encoded, label)
        previous?.let {
            val order = compareUnsigned(it, encoded)
            if (order == 0) throw BigramValidationException("duplicate $label word")
            if (order > 0) throw BigramValidationException("$label words are not sorted")
        }
        return encoded
    }

    private fun calculateDigests(file: File): Digests {
        val full = MessageDigest.getInstance("SHA-256")
        val zeroed = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(BUFFER_SIZE)
        var absoluteOffset = 0L
        FileInputStream(file).use { stream ->
            while (true) {
                val count = stream.read(bytes)
                if (count < 0) break
                full.update(bytes, 0, count)
                val copy = bytes.copyOf(count)
                val zeroStart = maxOf(0L, TatBigrFormat.CHECKSUM_OFFSET - absoluteOffset).toInt()
                val zeroEnd = minOf(
                    count.toLong(),
                    TatBigrFormat.CHECKSUM_OFFSET + TatBigrFormat.CHECKSUM_SIZE - absoluteOffset,
                ).toInt()
                if (zeroStart < zeroEnd) copy.fill(0, zeroStart, zeroEnd)
                zeroed.update(copy)
                absoluteOffset += count
            }
        }
        return Digests(full.digest(), zeroed.digest())
    }

    private fun decodeStrictUtf8(bytes: ByteArray, label: String): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: Exception) {
        throw BigramValidationException("$label word is not valid UTF-8", error)
    }

    private fun readU32Le(file: RandomAccessFile): Long {
        val first = file.read()
        val second = file.read()
        val third = file.read()
        val fourth = file.read()
        if (first or second or third or fourth < 0) throw EOFException()
        return first.toLong() or (second.toLong() shl 8) or
            (third.toLong() shl 16) or (fourth.toLong() shl 24)
    }

    private fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
        val count = minOf(first.size, second.size)
        for (index in 0 until count) {
            val difference = (first[index].toInt() and 0xff) - (second[index].toInt() and 0xff)
            if (difference != 0) return difference
        }
        return first.size - second.size
    }

    private fun checkedArraySize(value: Long): Int {
        if (value < 0L || value > Int.MAX_VALUE) {
            throw BigramValidationException("section is too large")
        }
        return value.toInt()
    }

    private fun checkedAdd(first: Long, second: Long): Long = try {
        Math.addExact(first, second)
    } catch (error: ArithmeticException) {
        throw BigramValidationException("section arithmetic overflow", error)
    }

    private fun checkedMultiply(first: Long, second: Long): Long = try {
        Math.multiplyExact(first, second)
    } catch (error: ArithmeticException) {
        throw BigramValidationException("section arithmetic overflow", error)
    }

    private data class Digests(val fullChecksum: ByteArray, val zeroedChecksum: ByteArray)

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
    }
}
