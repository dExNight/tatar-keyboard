/*
 * Copyright (C) 2026 Tatar Keyboard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.dictionary.personalstore

import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalSubtypes
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.TpersFormat
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * What could be read out of a quarantined `.tpers` copy: the words, and whether the file ran out
 * before it said it would.
 *
 * NOT a Kotlin `data class`: it carries the user's own words, and a synthesised `toString` would
 * print them at the first interpolation. Nothing here is ever logged.
 *
 * [readToEnd] is the honesty flag. It is true only when every record the header declared was read,
 * nothing was left over and no cap cut the read short. Whenever it is false the user must be told
 * that the rest of the file is damaged and lost — a partial recovery presented as a complete one
 * would be a new defect of exactly the class this whole path exists to fix.
 */
internal class PersonalQuarantineSalvage internal constructor(
    /** Words in their ORIGINAL on-disk form, in ascending normalized order, as read. */
    val rawForms: List<String>,
    /** The parallel NFC lowercase forms. */
    val normalizedForms: List<String>,
    val readToEnd: Boolean,
) {
    val wordCount: Int
        get() = rawForms.size

    companion object {
        /**
         * Reads as much of [file] as parses, for the personal dictionary of [requestedSubtypeId].
         * Returns null when there is no copy at all; an empty salvage with [readToEnd] false when a
         * copy exists but nothing in it can be trusted.
         *
         * The stored checksum is deliberately NOT consulted. A truncated write is the ordinary way
         * this file breaks, and the checksum is the first thing truncation destroys — refusing on it
         * would refuse every copy there is, which is the hole being closed. What stands in its place
         * is the per-record contract: the header must identify this exact schema, format and
         * LANGUAGE, and every record must pass the same content and ordering checks `TpersValidator`
         * enforces. The ascending-order check earns its keep here as a resync detector: a corrupted
         * length byte lands the cursor in the middle of the payload, and the first thing that shows
         * is a word that no longer sorts after the previous one.
         *
         * Parsing stops at the FIRST record that violates anything; everything before it is kept.
         * Nothing in here throws on bad input — a broken file has no right to end a process — but
         * callers still run it inside their own `try`, because `File` I/O can fail for its own
         * reasons.
         */
        fun read(file: File, requestedSubtypeId: String): PersonalQuarantineSalvage? {
            val length = try {
                if (file.isFile) file.length() else return null
            } catch (_: Exception) {
                return null
            }
            val alphabet = PersonalSubtypes.alphabetFor(requestedSubtypeId) ?: return NOTHING
            if (length < TpersFormat.HEADER_SIZE) return NOTHING

            val cap = TpersFormat.MAX_FILE_SIZE
            val bytes = readAtMost(file, minOf(length, cap).toInt()) ?: return NOTHING
            if (bytes.size < TpersFormat.HEADER_SIZE) return NOTHING
            // A file longer than the writer could ever produce is already not whole, but its head may
            // still hold words, so it is read up to the cap rather than refused.
            var readToEnd = length <= cap && bytes.size.toLong() == length

            val header = ByteBuffer.wrap(bytes, 0, TpersFormat.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(TpersFormat.MAGIC_SIZE)
            header.get(magic)
            if (!magic.contentEquals(TpersFormat.MAGIC.toByteArray(StandardCharsets.US_ASCII))) return NOTHING
            val schemaId = header.short.toInt() and 0xffff
            val formatVersion = header.short.toInt() and 0xffff
            val headerSize = header.short.toInt() and 0xffff
            val checksumAlgorithm = header.short.toInt() and 0xffff
            val entryCount = header.int.toLong() and TpersFormat.MAX_U32
            val payloadSize = header.int.toLong() and TpersFormat.MAX_U32
            val subtypeTagBytes = ByteArray(TpersFormat.SUBTYPE_TAG_SIZE)
            header.get(subtypeTagBytes)

            if (schemaId != TpersFormat.SCHEMA_ID) return NOTHING
            if (formatVersion != TpersFormat.FORMAT_VERSION) return NOTHING
            if (headerSize != TpersFormat.HEADER_SIZE) return NOTHING
            if (checksumAlgorithm != TpersFormat.CHECKSUM_ALGORITHM_SHA256) return NOTHING
            // The language tag is not negotiable: words saved while writing another language have no
            // business appearing in this one's list, however readable they are.
            if (decodeSubtypeTag(subtypeTagBytes) != requestedSubtypeId) return NOTHING

            if (payloadSize != length - TpersFormat.HEADER_SIZE) readToEnd = false
            var declared = entryCount
            if (declared > TpersFormat.MAX_PERSONAL_ENTRIES) {
                declared = TpersFormat.MAX_PERSONAL_ENTRIES
                readToEnd = false
            }

            val rawForms = ArrayList<String>()
            val normalizedForms = ArrayList<String>()
            val cursor = ByteBuffer
                .wrap(bytes, TpersFormat.HEADER_SIZE, bytes.size - TpersFormat.HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
            var previousNormalizedBytes: ByteArray? = null
            var index = 0L
            while (index < declared) {
                if (cursor.remaining() < TpersFormat.RECORD_HEADER_SIZE) break
                val wordByteLength = cursor.get().toInt() and 0xff
                val usageCount = cursor.short.toInt() and 0xffff
                cursor.int // lastUseSerial: read to advance the cursor; a restored word starts fresh.
                if (usageCount < 1) break
                if (wordByteLength < 1) break
                if (cursor.remaining() < wordByteLength) break
                val encoded = ByteArray(wordByteLength)
                cursor.get(encoded)

                val rawWord = decodeStrictUtf8(encoded) ?: break
                val normalized = PersonalWordFilter.acceptedNormalizedForm(rawWord, alphabet) ?: break
                val normalizedBytes = normalized.toByteArray(StandardCharsets.UTF_8)
                val previous = previousNormalizedBytes
                if (previous != null && compareUnsigned(previous, normalizedBytes) >= 0) break
                previousNormalizedBytes = normalizedBytes

                rawForms.add(rawWord)
                normalizedForms.add(normalized)
                index++
            }
            if (index != declared || cursor.remaining() != 0) readToEnd = false

            return PersonalQuarantineSalvage(rawForms, normalizedForms, readToEnd)
        }

        /** A copy that exists and yields nothing: no words, and certainly not read to the end. */
        private val NOTHING = PersonalQuarantineSalvage(emptyList(), emptyList(), false)

        /**
         * Reads at most [limit] bytes. Deliberately capped rather than [File.readBytes]: a corrupt
         * length field is exactly the kind of thing that would otherwise ask for a several-hundred-
         * megabyte array on a phone that has none.
         */
        private fun readAtMost(file: File, limit: Int): ByteArray? = try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(limit)
                var filled = 0
                while (filled < limit) {
                    val step = input.read(buffer, filled, limit - filled)
                    if (step < 0) break
                    filled += step
                }
                if (filled == limit) buffer else buffer.copyOf(filled)
            }
        } catch (_: Exception) {
            null
        }

        private fun decodeSubtypeTag(tagBytes: ByteArray): String? {
            var end = tagBytes.size
            while (end > 0 && tagBytes[end - 1].toInt() == 0) end--
            for (position in 0 until end) {
                val value = tagBytes[position].toInt() and 0xff
                if (value == 0 || value > 0x7f) return null
            }
            return String(tagBytes, 0, end, StandardCharsets.US_ASCII)
        }

        private fun decodeStrictUtf8(encoded: ByteArray): String? = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(encoded))
                .toString()
        } catch (_: Exception) {
            null
        }

        private fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
            val count = minOf(first.size, second.size)
            for (position in 0 until count) {
                val difference = (first[position].toInt() and 0xff) - (second[position].toInt() and 0xff)
                if (difference != 0) return difference
            }
            return first.size - second.size
        }
    }
}
