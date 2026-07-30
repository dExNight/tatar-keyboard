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
 * A [PrefixComputer] that also reports how many of the results it just returned were EXACT
 * dictionary candidates; the rest are fuzzy (E3).
 *
 * The three-class merge of E4b has to insert one personal word BETWEEN the exact and the fuzzy
 * candidates, so it must know where the boundary is — and the frozen `lookup` signature returns a
 * bare `List<String>` that cannot carry it. The count is exposed as state rather than as a richer
 * return type on purpose: `lookup` stays frozen, and no object is allocated per lookup to carry two
 * numbers. Reading it is safe under exactly the guarantee the index's scratch buffers already rely
 * on — at most one active worker, serialized by `LatestOnlyPrefixEngine`.
 */
internal interface ClassifiedPrefixComputer : PrefixComputer {
    /** Number of LEADING results of the last [lookup] that are exact candidates. */
    val lastExactCount: Int

    /**
     * The D3 autocorrect verdict of the last [lookup], or null when the typed word must not be
     * replaced. Read from the UI thread, hence the implementations publish it through a `@Volatile`
     * reference; a computer that does not run the class #1 pass simply never advises anything.
     */
    val lastAutocorrectAdvice: AutocorrectAdvice?
        get() = null
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
) : ClassifiedPrefixComputer, KeyNeighborSink {
    // Reusable per-index scratch. The index stops being fully immutable: these buffers are touched
    // ONLY inside lookup(), whose exclusivity is guaranteed by LatestOnlyPrefixEngine serialization
    // (at most one active worker). updateKeyNeighbors() only swaps a @Volatile reference.
    private val exactScratch = ByteArray(MAX_PREFIX_BYTES)
    private val variantScratch = ByteArray(MAX_PREFIX_BYTES + VARIANT_HEADROOM)
    private val codePointScratch = IntArray(MAX_PREFIX_BYTES)
    private val rankedIndices = IntArray(MAX_RESULTS)
    private val rankedFrequencies = LongArray(MAX_RESULTS)
    // Edit class carried alongside every ranked slot as a plain primitive int — no boxing, no
    // collection. Exact candidates all carry EDIT_CLASS_EXACT, so the class key is a no-op tie on
    // the exact level and its frozen order is unchanged; fuzzy candidates carry their generating
    // class (#1/#2/#3) so the fuzzy level orders by class first (see [ranksBefore]).
    private val rankedClasses = IntArray(MAX_RESULTS)
    private val fuzzyIndices = IntArray(MAX_RESULTS)
    private val fuzzyFrequencies = LongArray(MAX_RESULTS)
    private val fuzzyClasses = IntArray(MAX_RESULTS)
    // Scratch of the D3 pass. Separate buffers rather than a reuse of the two above, because the
    // autocorrect pass must stay independent of whether the display fuzzy level ran at all: it is
    // decided by rules of its own (word length, word absent from the dictionary), never by how many
    // cells the exact pass happened to leave empty.
    private val autocorrectCodePointScratch = IntArray(MAX_PREFIX_BYTES)
    private val autocorrectVariantScratch = ByteArray(MAX_PREFIX_BYTES + VARIANT_HEADROOM)

    @Volatile
    private var neighborTable: KeyNeighborTable? = null

    /**
     * How many leading results of the last [lookup] are exact. Worker-confined exactly like the
     * scratch buffers above; reset at the top of every lookup so a failed or rejected one cannot
     * leave a stale boundary behind for the merge to trust.
     */
    override var lastExactCount = 0
        private set

    /**
     * The D3 verdict of the last [lookup]. Written by the serialized worker, read on the UI thread
     * when a word separator is pressed, hence `@Volatile`: the object itself is immutable, so
     * publishing the reference publishes everything the reader needs.
     *
     * Reset at the top of every lookup, exactly like [lastExactCount], so a rejected or failed
     * lookup can never leave an older word's verdict behind for the next separator to act on.
     */
    @Volatile
    override var lastAutocorrectAdvice: AutocorrectAdvice? = null
        private set

    /** Drops the current verdict; called when the engine idles or is torn down. */
    fun clearAutocorrectAdvice() {
        lastAutocorrectAdvice = null
    }

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

    // The edit class of the variant pass currently running (EDIT_CLASS_LONG_PRESS/GEOMETRIC/
    // TRANSPOSITION). A plain int, set before each class's generator runs and read by
    // scanVariantBlock so every fuzzy candidate is tagged with the class that produced it.
    private var fuzzyCurrentClass = EDIT_CLASS_LONG_PRESS

    // D3 accumulator, private to a single computeAutocorrectAdvice() invocation. The pass counts
    // WHOLE-WORD matches: how many class #1 variants of the typed word are themselves dictionary
    // entries, and which one. Anything but exactly one means no replacement.
    private var autocorrectMatchCount = 0
    private var autocorrectMatchIndex = NO_ENTRY

    // Allocated once, so neither a lambda nor any object is created per lookup or per variant.
    private val fuzzyConsumer =
        FuzzyPrefixVariants.VariantConsumer { bytes, length -> scanVariantBlock(bytes, length) }

    private val autocorrectConsumer =
        FuzzyPrefixVariants.VariantConsumer { bytes, length -> matchWholeWord(bytes, length) }

    override fun updateKeyNeighbors(table: KeyNeighborTable?) {
        neighborTable = table
    }

    override fun lookup(normalizedPrefixUtf8: ImmutableUtf8Prefix): List<String> {
        val prefixLength = normalizedPrefixUtf8.byteCount
        lastExactCount = 0
        lastAutocorrectAdvice = null
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
            // Recorded before the fuzzy pass appends to the same ranked arrays: everything after
            // this many slots is fuzzy, which is exactly what the E4b merge needs to know.
            lastExactCount = resultCount
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
            // Deliberately outside the `resultCount < MAX_RESULTS` guard above: the D3 verdict is
            // about the typed word itself and must not depend on how full the band happens to be.
            computeAutocorrectAdvice(normalizedPrefixUtf8, prefixLength)
            if (resultCount == 0) return emptyList()
            ArrayList<String>(resultCount).also { result ->
                for (slot in 0 until resultCount) {
                    result += decodeWord(rankedIndices[slot])
                }
            }
        } catch (_: RuntimeException) {
            lastExactCount = 0
            lastAutocorrectAdvice = null
            emptyList()
        }
    }

    /**
     * The D3 pass: decides whether the word that was just looked up may be autocorrected, and to
     * what. Runs on the same worker, right after the display passes, and touches the same mmap'd
     * buffer they do — so no new thread, no new request and no new token exist anywhere.
     *
     * Every condition of the contract is checked here, in the contract's own order:
     *  - the word is at least [AutocorrectPolicy.MIN_WORD_CODE_POINTS] code points long;
     *  - the word is ABSENT from the dictionary (a word people write is never "corrected");
     *  - EXACTLY ONE class #1 (long-press partner) variant of it is itself a dictionary word —
     *    counted before any frequency filter, so an ambiguous typo is left alone rather than
     *    resolved by frequency;
     *  - that one candidate's frequency is at least [AutocorrectPolicy.MIN_CANDIDATE_FREQUENCY].
     *
     * Two properties are worth naming. The match is WHOLE-WORD, not prefix-block: the contract
     * replaces a word by a word one edit away from it, and a prefix scan would offer continuations
     * instead. And the class is pinned to #1 directly rather than through
     * [SHIPPED_FUZZY_EDIT_CLASSES]: D3 excludes classes #2/#3 by its own contract, so re-enabling
     * them for the band must not make autocorrect follow.
     *
     * The pass costs one binary search per variant and scans no block at all; it runs only for words
     * long enough to qualify, so short prefixes — the bulk of the keystrokes — pay nothing.
     */
    private fun computeAutocorrectAdvice(
        normalizedPrefixUtf8: ImmutableUtf8Prefix,
        prefixLength: Int,
    ) {
        val table = neighborTable ?: return
        if (table.isEmpty) return
        if (countCodePointsByLeadBytes(exactScratch, prefixLength) <
            AutocorrectPolicy.MIN_WORD_CODE_POINTS
        ) {
            return
        }
        val typedEntry = lowerBound(exactScratch, prefixLength, 0)
        if (typedEntry < entryCount && wordEquals(typedEntry, exactScratch, prefixLength)) return
        autocorrectMatchCount = 0
        autocorrectMatchIndex = NO_ENTRY
        val emitted = FuzzyPrefixVariants.generateLongPressVariants(
            exactScratch, prefixLength, table, autocorrectCodePointScratch,
            autocorrectVariantScratch, MAX_FUZZY_VARIANTS, autocorrectConsumer,
        )
        // Fail closed on a budget overrun or malformed input, exactly like the display level: a
        // partially generated variant set could hide the second candidate that makes a typo
        // ambiguous, and acting on it would replace text on incomplete evidence.
        if (emitted < 0) return
        if (autocorrectMatchCount != 1) return
        val candidate = autocorrectMatchIndex
        val frequency = frequencyAt(candidate)
        if (frequency < AutocorrectPolicy.MIN_CANDIDATE_FREQUENCY) return
        lastAutocorrectAdvice = AutocorrectAdvice(
            normalizedPrefixUtf8.decodeUtf8(),
            decodeWord(candidate),
            frequency,
        )
    }

    /** Counts one class #1 variant that is itself a dictionary entry; stops caring past two. */
    private fun matchWholeWord(variantBytes: ByteArray, variantLength: Int) {
        if (autocorrectMatchCount > 1) return
        val entry = lowerBound(variantBytes, variantLength, 0)
        if (entry >= entryCount) return
        if (!wordEquals(entry, variantBytes, variantLength)) return
        // Distinct variants are distinct byte strings, so this can only fire on a defensive re-entry;
        // counting the same entry twice would turn one candidate into a false ambiguity.
        if (entry == autocorrectMatchIndex) return
        autocorrectMatchCount++
        autocorrectMatchIndex = entry
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
                rankedIndices, rankedFrequencies, rankedClasses, resultCount, MAX_RESULTS,
                index, frequencyAt(index), EDIT_CLASS_EXACT,
            )
        }
        return resultCount
    }

    /**
     * Fills the cells the exact pass left empty with the best fuzzy candidates. Within the fuzzy
     * level the order is edit class first (class #1 long-press partner, then #2 geometric
     * neighbour, then #3 transposition), and only inside one class the frozen tie-break
     * (frequency descending, then code-point lexical ascending). Exact candidates are never
     * touched and always outrank any fuzzy candidate. Returns the total candidate count.
     *
     * WHICH edit classes run on the shipped live path is decided in EXACTLY ONE named place —
     * [SHIPPED_FUZZY_EDIT_CLASSES]. E3b measured both acceptance conditions unmet (PROPOSALS.md,
     * section "Контракт текста", line "Итог, 2026-07-27"; docs/DICTIONARY-E3.md), so only class #1
     * (long-press partner) ships. Classes #2 (geometric neighbour) and #3 (transposition) are
     * excluded from this live path and are therefore unreachable through lookup(); their generators
     * below stay in the tree as infrastructure and remain covered by direct-generator tests. A
     * class runs here only if its EDIT_CLASS_* value is in the shipped set — there is no per-request
     * state and no user-facing toggle.
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
        // The enabled classes share one variant budget: the total number of variants generated
        // across all of them must stay within MAX_FUZZY_VARIANTS. The edit class DOES affect ranking
        // (class #1 before #2 before #3, then frequency inside a class); each candidate is tagged
        // with fuzzyCurrentClass, set below before its class runs. Any single class returning -1
        // (its slice of the budget exceeded) drops the whole fuzzy level, never a part of it.
        var variantsUsed = 0

        if (EDIT_CLASS_LONG_PRESS in SHIPPED_FUZZY_EDIT_CLASSES) {
            fuzzyCurrentClass = EDIT_CLASS_LONG_PRESS
            val emitted = FuzzyPrefixVariants.generateLongPressVariants(
                exactScratch, prefixLength, table, codePointScratch, variantScratch,
                MAX_FUZZY_VARIANTS - variantsUsed, fuzzyConsumer,
            )
            if (emitted < 0 || fuzzyOverBudget) {
                lastFuzzyOverBudget = true
                lastFuzzyVisitedCount = fuzzyVisited
                return exactCount
            }
            variantsUsed += emitted
        }

        if (EDIT_CLASS_GEOMETRIC in SHIPPED_FUZZY_EDIT_CLASSES) {
            fuzzyCurrentClass = EDIT_CLASS_GEOMETRIC
            val emitted = FuzzyPrefixVariants.generateGeometricVariants(
                exactScratch, prefixLength, table, codePointScratch, variantScratch,
                MAX_FUZZY_VARIANTS - variantsUsed, fuzzyConsumer,
            )
            if (emitted < 0 || fuzzyOverBudget) {
                lastFuzzyOverBudget = true
                lastFuzzyVisitedCount = fuzzyVisited
                return exactCount
            }
            variantsUsed += emitted
        }

        if (EDIT_CLASS_TRANSPOSITION in SHIPPED_FUZZY_EDIT_CLASSES) {
            fuzzyCurrentClass = EDIT_CLASS_TRANSPOSITION
            val emitted = FuzzyPrefixVariants.generateTranspositionVariants(
                exactScratch, prefixLength, codePointScratch, variantScratch,
                MAX_FUZZY_VARIANTS - variantsUsed, fuzzyConsumer,
            )
            if (emitted < 0 || fuzzyOverBudget) {
                lastFuzzyOverBudget = true
                lastFuzzyVisitedCount = fuzzyVisited
                return exactCount
            }
            variantsUsed += emitted
        }

        lastFuzzyVariantCount = variantsUsed
        lastFuzzyVisitedCount = fuzzyVisited
        for (slot in 0 until fuzzyCount) {
            rankedIndices[exactCount + slot] = fuzzyIndices[slot]
            rankedFrequencies[exactCount + slot] = fuzzyFrequencies[slot]
            rankedClasses[exactCount + slot] = fuzzyClasses[slot]
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
            // A word is de-duplicated by dictionary index so it can never occupy two cells. Because
            // classes run in order (#1, then #2, then #3), the first class to reach a word keeps it,
            // which is also its best (lowest) class — consistent with the class-first ranking.
            if (!wordEquals(index, exactScratch, fuzzyPrefixLength) &&
                !containsIndex(rankedIndices, fuzzyExactCount, index) &&
                !containsIndex(fuzzyIndices, fuzzyCount, index)
            ) {
                fuzzyCount = insertRanked(
                    fuzzyIndices, fuzzyFrequencies, fuzzyClasses, fuzzyCount, fuzzyRemaining,
                    index, frequencyAt(index), fuzzyCurrentClass,
                )
            }
            index++
        }
    }

    /** Bounded insertion sort shared by both levels; returns the new count. */
    private fun insertRanked(
        indices: IntArray,
        frequencies: LongArray,
        classes: IntArray,
        count: Int,
        capacity: Int,
        candidateIndex: Int,
        candidateFrequency: Long,
        candidateClass: Int,
    ): Int {
        var insertion = count
        for (slot in 0 until count) {
            if (ranksBefore(
                    candidateClass, candidateIndex, candidateFrequency,
                    classes[slot], indices[slot], frequencies[slot],
                )
            ) {
                insertion = slot
                break
            }
        }
        if (insertion >= capacity) return count
        val newCount = minOf(capacity, count + 1)
        for (slot in newCount - 1 downTo insertion + 1) {
            indices[slot] = indices[slot - 1]
            frequencies[slot] = frequencies[slot - 1]
            classes[slot] = classes[slot - 1]
        }
        indices[insertion] = candidateIndex
        frequencies[insertion] = candidateFrequency
        classes[insertion] = candidateClass
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
        candidateClass: Int,
        candidateIndex: Int,
        candidateFrequency: Long,
        rankedClass: Int,
        rankedIndex: Int,
        rankedFrequency: Long,
    ): Boolean = when {
        // Edit class first (ascending): #1 long-press < #2 geometric < #3 transposition. Exact
        // candidates all share EDIT_CLASS_EXACT, so this key is a tie among them and their frozen
        // order is untouched; only the fuzzy level, whose candidates carry distinct class values,
        // is reordered by it.
        candidateClass != rankedClass -> candidateClass < rankedClass
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

        /** "No dictionary entry"; entry indices are non-negative. */
        private const val NO_ENTRY = -1

        // Edit-class ranking keys, carried as plain ints. Exact candidates sort as EDIT_CLASS_EXACT
        // (a tie on the exact level, whose order is unchanged); within the fuzzy level the ascending
        // order is #1 long-press partner < #2 geometric neighbour < #3 transposition, applied ahead
        // of frequency by [ranksBefore]. The exact level always outranks the fuzzy level regardless
        // of these values, because exact and fuzzy candidates live in separate arrays and the exact
        // ones are merged first.
        private const val EDIT_CLASS_EXACT = 0
        internal const val EDIT_CLASS_LONG_PRESS = 1
        internal const val EDIT_CLASS_GEOMETRIC = 2
        internal const val EDIT_CLASS_TRANSPOSITION = 3

        // THE single switch that decides which edit classes reach the shipped fuzzy pass. E3b
        // measured both of its acceptance conditions unmet — the combined recovery@3 of classes
        // #1–#3 fell far below the 2.4x threshold — so classes #2 (geometric neighbour) and #3
        // (transposition) are excluded from the shipped live path and only class #1 (long-press
        // partner) ships. See PROPOSALS.md, section "Контракт текста", line "Итог, 2026-07-27", and
        // docs/DICTIONARY-E3.md. The #2/#3 generators, geometry map and instrumentation harness stay
        // in the tree as infrastructure and keep their direct tests; making them reachable again is
        // a one-line change to this set (add EDIT_CLASS_GEOMETRIC / EDIT_CLASS_TRANSPOSITION) — there
        // is deliberately no runtime state and no user-facing toggle. The ranking-by-edit-class order
        // in [ranksBefore] is retained unchanged; with a single shipped class it is a constant tie,
        // which is exactly why class #1 recovery is invariant to whether #2/#3 are present.
        internal val SHIPPED_FUZZY_EDIT_CLASSES = intArrayOf(EDIT_CLASS_LONG_PRESS)

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
