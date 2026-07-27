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

package rkr.simplekeyboard.inputmethod.latin.dictionary.personal

/**
 * The frozen `.tpers` personal-dictionary binary format.
 *
 * The format is deliberately its own schema rather than a reuse of `.tdict`: the dictionary asset
 * is an immutable, name-sha256-and-count-before-build artifact activated through a lease that
 * returns null while a foreign reader is alive, which is unusable for a file that changes during a
 * session. See `docs/DICTIONARY-E4.md` for the written justification.
 *
 * Header layout (little-endian, [HEADER_SIZE] = 72 bytes), the same checksum convention as
 * `TdictValidator.calculateDigests` (SHA-256 over the whole file with the checksum field zeroed):
 *
 * | offset | size | field                                                         |
 * |-------:|-----:|---------------------------------------------------------------|
 * |      0 |    8 | [MAGIC] `TATPERS\0`                                            |
 * |      8 |    2 | schemaId u16 = [SCHEMA_ID]                                     |
 * |     10 |    2 | formatVersion u16 = [FORMAT_VERSION]                          |
 * |     12 |    2 | headerSize u16 = [HEADER_SIZE]                                 |
 * |     14 |    2 | checksumAlgorithm u16 = [CHECKSUM_ALGORITHM_SHA256]            |
 * |     16 |    4 | entryCount u32                                                |
 * |     20 |    4 | payloadSize u32                                               |
 * |     24 |   16 | subtypeTag: ASCII, NUL-padded ([SUBTYPE_TAG_SIZE] bytes)      |
 * |     40 |   32 | SHA-256 over the whole file with this field zeroed            |
 *
 * The payload is [entryCount] records in STRICTLY ASCENDING order of the NORMALIZED (NFC lowercase)
 * form of the word, with no duplicates by that same normalized form. Each record:
 *
 * | size | field                                          |
 * |-----:|------------------------------------------------|
 * |    1 | wordByteLength u8                              |
 * |    2 | usageCount u16 (>= 1)                          |
 * |    4 | lastUseSerial u32                             |
 * |    N | word bytes, UTF-8, stored in its ORIGINAL form |
 *
 * The record stores the ORIGINAL (as-entered) form of the word; sorting, deduplication, content
 * filters and search all operate on the normalized NFC lowercase form (see the "Контракт текста"
 * amendment of 2026-07-27, four points, owned by E4a-1). This is what lets the personal name
 * "Гүзәл" be shown and inserted with its capital while still being keyed, ordered and deduplicated
 * as "гүзәл".
 */
internal object TpersFormat {
    const val MAGIC = "TATPERS\u0000"

    const val SCHEMA_ID = 1
    const val FORMAT_VERSION = 1
    const val HEADER_SIZE = 72
    const val CHECKSUM_ALGORITHM_SHA256 = 1

    const val MAGIC_SIZE = 8
    const val SCHEMA_ID_OFFSET = 8
    const val FORMAT_VERSION_OFFSET = 10
    const val HEADER_SIZE_OFFSET = 12
    const val CHECKSUM_ALGORITHM_OFFSET = 14
    const val ENTRY_COUNT_OFFSET = 16
    const val PAYLOAD_SIZE_OFFSET = 20
    const val SUBTYPE_TAG_OFFSET = 24
    const val SUBTYPE_TAG_SIZE = 16
    const val CHECKSUM_OFFSET = 40
    const val CHECKSUM_SIZE = 32

    /** Fixed record overhead: wordByteLength u8 + usageCount u16 + lastUseSerial u32. */
    const val RECORD_HEADER_SIZE = 7

    /** Cap on records per subtype (E4a-2 limit; enforced fail-closed by the reader from day one). */
    const val MAX_PERSONAL_ENTRIES = 2_000L

    /** Cap on the whole file, 128 KiB (E4a-2 limit; enforced by the reader from day one). */
    const val MAX_FILE_SIZE = 131_072L

    /** Inclusive code-point length bounds for the normalized form of a personal word. */
    const val MIN_WORD_CODE_POINTS = 3
    const val MAX_WORD_CODE_POINTS = 24

    const val MAX_U16 = 0xffffL
    const val MAX_U32 = 0xffff_ffffL

    /**
     * The on-disk file name for a subtype, e.g. `personal-tt_RU-s1-f1.tpers`. The schema and format
     * version are woven into the name so a file written by an incompatible build is never even
     * opened for the wrong reader. The directory that holds it is owned by E4a-2; this phase only
     * reads.
     */
    fun personalFileName(subtypeTag: String): String =
        "personal-$subtypeTag-s$SCHEMA_ID-f$FORMAT_VERSION.tpers"
}
