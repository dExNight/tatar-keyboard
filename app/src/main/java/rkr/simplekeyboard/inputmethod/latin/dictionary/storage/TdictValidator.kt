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
import java.text.Normalizer
import java.util.Locale
import java.util.zip.DataFormatException
import java.util.zip.Inflater

class DictionaryValidationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

data class ValidatedDictionary(
    val rawSize: Long,
    val entryCount: Long,
    val schemaId: Int,
    val formatVersion: Int,
    val rawSha256: String,
)

data class InflatedAsset(
    val compressedSize: Long,
    val compressedSha256: String,
    val rawSize: Long,
)

@Keep
class TdictValidator {
    fun inflateAsset(
        source: java.io.InputStream,
        destination: OutputStream,
        spec: DictionaryArtifactSpec,
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
                    throw DictionaryValidationException("zlib preset dictionary is unsupported")
                }
                if (inflater.needsInput()) {
                    val count = input.read(inputBuffer)
                    if (count < 0) {
                        throw DictionaryValidationException("truncated zlib stream")
                    }
                    compressedSize += count
                    if (compressedSize > spec.maxCompressedSize) {
                        throw DictionaryValidationException("compressed size limit exceeded")
                    }
                    compressedDigest.update(inputBuffer, 0, count)
                    inflater.setInput(inputBuffer, 0, count)
                }

                val count = try {
                    inflater.inflate(outputBuffer)
                } catch (error: DataFormatException) {
                    throw DictionaryValidationException("invalid zlib stream", error)
                }
                if (count > 0) {
                    rawSize += count
                    if (rawSize > spec.maxRawSize) {
                        throw DictionaryValidationException("raw size limit exceeded")
                    }
                    output.write(outputBuffer, 0, count)
                } else if (!inflater.finished() && !inflater.needsInput() &&
                    !inflater.needsDictionary()
                ) {
                    throw DictionaryValidationException("zlib inflater made no progress")
                }
            }

            if (inflater.remaining != 0 || input.read() != -1) {
                throw DictionaryValidationException("trailing or concatenated zlib data")
            }
            output.flush()
        } finally {
            inflater.end()
        }

        val compressedSha = compressedDigest.digest().toHex()
        if (compressedSize != spec.expectedCompressedSize) {
            throw DictionaryValidationException("unexpected compressed size")
        }
        if (!constantTimeHexEquals(compressedSha, spec.expectedCompressedSha256)) {
            throw DictionaryValidationException("unexpected compressed SHA-256")
        }
        if (rawSize != spec.expectedRawSize) {
            throw DictionaryValidationException("unexpected raw size")
        }
        return InflatedAsset(compressedSize, compressedSha, rawSize)
    }

    fun validateRaw(file: File, spec: DictionaryArtifactSpec): ValidatedDictionary {
        val length = file.length()
        if (length > spec.maxRawSize) {
            throw DictionaryValidationException("raw size limit exceeded")
        }
        if (length < TdictFormat.HEADER_SIZE) {
            throw DictionaryValidationException("raw dictionary is shorter than its header")
        }
        if (length != spec.expectedRawSize) {
            throw DictionaryValidationException("unexpected raw size")
        }

        val header = ByteArray(TdictFormat.HEADER_SIZE)
        RandomAccessFile(file, "r").use { raw ->
            raw.readFully(header)
        }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(8)
        buffer.get(magic)
        if (!magic.contentEquals(TdictFormat.MAGIC.toByteArray(StandardCharsets.US_ASCII))) {
            throw DictionaryValidationException("wrong dictionary magic")
        }
        val schemaId = buffer.short.toInt() and 0xffff
        val formatVersion = buffer.short.toInt() and 0xffff
        val headerSize = buffer.short.toInt() and 0xffff
        val checksumAlgorithm = buffer.short.toInt() and 0xffff
        val entryCount = buffer.int.toLong() and TdictFormat.MAX_U32
        val offsetsOffset = buffer.int.toLong() and TdictFormat.MAX_U32
        val frequenciesOffset = buffer.int.toLong() and TdictFormat.MAX_U32
        val blobOffset = buffer.int.toLong() and TdictFormat.MAX_U32
        val blobSize = buffer.int.toLong() and TdictFormat.MAX_U32
        val declaredFileSize = buffer.int.toLong() and TdictFormat.MAX_U32
        val storedChecksum = ByteArray(TdictFormat.CHECKSUM_SIZE)
        buffer.get(storedChecksum)

        if (schemaId != spec.schemaId || schemaId != TdictFormat.SCHEMA_ID) {
            throw DictionaryValidationException("unsupported schema id")
        }
        if (formatVersion != spec.formatVersion ||
            formatVersion != TdictFormat.FORMAT_VERSION
        ) {
            throw DictionaryValidationException("unsupported format version")
        }
        if (headerSize != TdictFormat.HEADER_SIZE) {
            throw DictionaryValidationException("unexpected header size")
        }
        if (checksumAlgorithm != TdictFormat.CHECKSUM_ALGORITHM_SHA256) {
            throw DictionaryValidationException("unsupported checksum algorithm")
        }
        if (entryCount == 0L || entryCount != spec.expectedEntryCount) {
            throw DictionaryValidationException("unexpected entry count")
        }

        val expectedOffsetsOffset = TdictFormat.HEADER_SIZE.toLong()
        val expectedFrequenciesOffset = checkedAdd(
            expectedOffsetsOffset,
            checkedMultiply(4L, checkedAdd(entryCount, 1L)),
        )
        val expectedBlobOffset = checkedAdd(
            expectedFrequenciesOffset,
            checkedMultiply(4L, entryCount),
        )
        val expectedFileSize = checkedAdd(expectedBlobOffset, blobSize)
        if (expectedFrequenciesOffset > TdictFormat.MAX_U32 ||
            expectedBlobOffset > TdictFormat.MAX_U32 ||
            expectedFileSize > TdictFormat.MAX_U32
        ) {
            throw DictionaryValidationException("section arithmetic exceeds u32")
        }
        if (offsetsOffset != expectedOffsetsOffset ||
            frequenciesOffset != expectedFrequenciesOffset ||
            blobOffset != expectedBlobOffset ||
            declaredFileSize != expectedFileSize ||
            declaredFileSize != length
        ) {
            throw DictionaryValidationException("noncanonical section layout")
        }

        val digests = calculateDigests(file)
        if (!MessageDigest.isEqual(storedChecksum, digests.zeroedChecksum)) {
            throw DictionaryValidationException("SHA-256 checksum mismatch")
        }
        val rawSha = digests.fullChecksum.toHex()
        if (!constantTimeHexEquals(rawSha, spec.expectedRawSha256)) {
            throw DictionaryValidationException("unexpected raw SHA-256")
        }

        RandomAccessFile(file, "r").use { raw ->
            raw.seek(offsetsOffset)
            var previousOffset = readU32Le(raw)
            if (previousOffset != 0L) {
                throw DictionaryValidationException("first word offset must be zero")
            }
            val offsets = LongArray(checkedArraySize(entryCount + 1L))
            offsets[0] = previousOffset
            for (index in 1 until offsets.size) {
                val offset = readU32Le(raw)
                if (offset > blobSize || offset <= previousOffset) {
                    throw DictionaryValidationException("word offsets are not strictly increasing")
                }
                offsets[index] = offset
                previousOffset = offset
            }
            if (offsets.last() != blobSize) {
                throw DictionaryValidationException("terminal word offset does not equal blob size")
            }

            raw.seek(frequenciesOffset)
            repeat(checkedArraySize(entryCount)) {
                if (readU32Le(raw) == 0L) {
                    throw DictionaryValidationException("frequency must be positive")
                }
            }

            var previousWordBytes: ByteArray? = null
            for (index in 0 until checkedArraySize(entryCount)) {
                val wordSize = offsets[index + 1] - offsets[index]
                if (wordSize <= 0L || wordSize > MAX_CANONICAL_WORD_BYTES) {
                    throw DictionaryValidationException("invalid word byte length")
                }
                val encoded = ByteArray(wordSize.toInt())
                raw.seek(blobOffset + offsets[index])
                raw.readFully(encoded)
                val word = decodeStrictUtf8(encoded)
                validateCanonicalWord(word)
                previousWordBytes?.let { previous ->
                    val order = compareUnsigned(previous, encoded)
                    if (order == 0) {
                        throw DictionaryValidationException("duplicate dictionary word")
                    }
                    if (order > 0) {
                        throw DictionaryValidationException("dictionary words are not sorted")
                    }
                }
                previousWordBytes = encoded
            }
        }

        return ValidatedDictionary(
            rawSize = length,
            entryCount = entryCount,
            schemaId = schemaId,
            formatVersion = formatVersion,
            rawSha256 = rawSha,
        )
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
                val zeroStart = maxOf(0L, TdictFormat.CHECKSUM_OFFSET - absoluteOffset).toInt()
                val zeroEnd = minOf(
                    count.toLong(),
                    TdictFormat.CHECKSUM_OFFSET + TdictFormat.CHECKSUM_SIZE - absoluteOffset,
                ).toInt()
                if (zeroStart < zeroEnd) {
                    copy.fill(0, zeroStart, zeroEnd)
                }
                zeroed.update(copy)
                absoluteOffset += count
            }
        }
        return Digests(full.digest(), zeroed.digest())
    }

    private fun validateCanonicalWord(word: String) {
        if (word.isEmpty() || word.codePointCount(0, word.length) > 64) {
            throw DictionaryValidationException("invalid word length")
        }
        var offset = 0
        while (offset < word.length) {
            val codePoint = word.codePointAt(offset)
            if (codePoint !in TATAR_ALPHABET) {
                throw DictionaryValidationException("word is outside the Tatar alphabet")
            }
            offset += Character.charCount(codePoint)
        }
        val canonical = Normalizer.normalize(word, Normalizer.Form.NFC).lowercase(Locale.ROOT)
        if (canonical != word) {
            throw DictionaryValidationException("word is not NFC lowercase")
        }
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: Exception) {
        throw DictionaryValidationException("word is not valid UTF-8", error)
    }

    private fun readU32Le(file: RandomAccessFile): Long {
        val first = file.read()
        val second = file.read()
        val third = file.read()
        val fourth = file.read()
        if (first or second or third or fourth < 0) throw EOFException()
        return first.toLong() or
            (second.toLong() shl 8) or
            (third.toLong() shl 16) or
            (fourth.toLong() shl 24)
    }

    private fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
        val count = minOf(first.size, second.size)
        for (index in 0 until count) {
            val difference = (first[index].toInt() and 0xff) -
                (second[index].toInt() and 0xff)
            if (difference != 0) return difference
        }
        return first.size - second.size
    }

    private fun checkedArraySize(value: Long): Int {
        if (value < 0L || value > Int.MAX_VALUE) {
            throw DictionaryValidationException("section is too large")
        }
        return value.toInt()
    }

    private fun checkedAdd(first: Long, second: Long): Long = try {
        Math.addExact(first, second)
    } catch (error: ArithmeticException) {
        throw DictionaryValidationException("section arithmetic overflow", error)
    }

    private fun checkedMultiply(first: Long, second: Long): Long = try {
        Math.multiplyExact(first, second)
    } catch (error: ArithmeticException) {
        throw DictionaryValidationException("section arithmetic overflow", error)
    }

    private data class Digests(
        val fullChecksum: ByteArray,
        val zeroedChecksum: ByteArray,
    )

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
        private const val MAX_CANONICAL_WORD_BYTES = 64L * 2L
        private val TATAR_ALPHABET =
            "аәбвгдеёжҗзийклмнңоөпрстуүфхһцчшщъыьэюя".codePoints().toArray().toSet()
    }
}

internal fun ByteArray.toHex(): String {
    val alphabet = "0123456789abcdef"
    return buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xff
            append(alphabet[value ushr 4])
            append(alphabet[value and 0x0f])
        }
    }
}

internal fun constantTimeHexEquals(first: String, second: String): Boolean =
    MessageDigest.isEqual(
        first.lowercase(Locale.ROOT).toByteArray(StandardCharsets.US_ASCII),
        second.lowercase(Locale.ROOT).toByteArray(StandardCharsets.US_ASCII),
    )
