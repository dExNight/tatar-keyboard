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

package rkr.simplekeyboard.inputmethod.latin.emoji

import java.io.InputStream

/**
 * An immutable "categories -> entries" snapshot of the emoji set.
 *
 * Both the category list and every entry list are defensive, read-only copies, so a published
 * snapshot cannot change under a reader. Indices are compact: a category that ends up empty after
 * glyph filtering is not present at all, and the entries within a category have no gaps, so a cell
 * grid can address `entryAt(category, 0 until entryCount(category))` without holes.
 */
class EmojiSetSnapshot internal constructor(
    private val names: List<String>,
    private val entries: List<List<String>>,
) {
    /** Number of non-empty categories in the snapshot. */
    val categoryCount: Int get() = names.size

    /** True when no category survived parsing and filtering. */
    val isEmpty: Boolean get() = names.isEmpty()

    /** The name (without the leading `#`) of the category at [category]. */
    fun categoryName(category: Int): String = names[category]

    /** How many entries the category at [category] holds. */
    fun entryCount(category: Int): Int = entries[category].size

    /** The entry at [index] within the category at [category]. */
    fun entryAt(category: Int, index: Int): String = entries[category][index]

    /** The read-only entry list of the category at [category], in significant order. */
    fun entriesOf(category: Int): List<String> = entries[category]

    /** Total number of entries across every category. */
    fun totalEntryCount(): Int {
        var total = 0
        for (list in entries) total += list.size
        return total
    }

    internal companion object {
        val EMPTY = EmojiSetSnapshot(emptyList(), emptyList())
    }
}

/**
 * Fail-closed parser that turns the packed emoji asset into an [EmojiSetSnapshot].
 *
 * The asset is data, not code: UTF-8, LF line endings, category sections written as `#smileys`, one
 * sequence per line, order within a section significant. This parser never touches an
 * `AssetManager` — it takes a [String] or an [InputStream] — so it stays Android-free and runs on
 * the plain JVM.
 *
 * Fail-closed throughout: a malformed line is dropped, a duplicate within a section is dropped, a
 * section that has no surviving entries is absent from the snapshot, a fully unreadable input
 * yields [EmojiSetSnapshot.EMPTY], and no exception ever escapes. The cost of glyph filtering and
 * category composition is paid exactly once here, in [build], not while drawing.
 */
object EmojiSet {

    /** Upper bound on the length (in `char`) of a single sequence line; anything longer is junk. */
    private const val MAX_SEQUENCE_CHARS = 64

    private val ACCEPT_ALL = GlyphProbe { true }

    /** Parses [text] with no glyph filtering (every well-formed entry is kept). */
    @JvmStatic
    fun parse(text: String): EmojiSetSnapshot = build(text, ACCEPT_ALL)

    /** Reads [input] as UTF-8 and parses it with no glyph filtering. */
    @JvmStatic
    fun parse(input: InputStream): EmojiSetSnapshot = build(input, ACCEPT_ALL)

    /**
     * Parses [text] and keeps only the entries for which [probe] reports a glyph, dropping any
     * category left empty. This is the single point where filtering and composition happen; the
     * result is an immutable snapshot ready to draw without further probing.
     */
    @JvmStatic
    fun build(text: String, probe: GlyphProbe): EmojiSetSnapshot =
        try {
            parseFiltered(text, probe)
        } catch (e: Exception) {
            // Fail-closed: any parsing mishap collapses to an empty set rather than propagating.
            EmojiSetSnapshot.EMPTY
        }

    /** Reads [input] as UTF-8 and delegates to [build]; an unreadable stream yields an empty set. */
    @JvmStatic
    fun build(input: InputStream, probe: GlyphProbe): EmojiSetSnapshot {
        val text = try {
            input.reader(Charsets.UTF_8).readText()
        } catch (e: Exception) {
            return EmojiSetSnapshot.EMPTY
        }
        return build(text, probe)
    }

    private fun parseFiltered(text: String, probe: GlyphProbe): EmojiSetSnapshot {
        // First pass: split into ordered categories, dropping malformed lines and intra-section
        // duplicates. Sequences that appear before any section header have no home and are dropped.
        val rawNames = ArrayList<String>()
        val rawEntries = ArrayList<ArrayList<String>>()
        var currentEntries: ArrayList<String>? = null
        var currentSeen: HashSet<String>? = null

        for (rawLine in text.split('\n')) {
            // Tolerate a stray CR from a CRLF file even though the format is LF-only.
            val line = if (rawLine.endsWith('\r')) rawLine.substring(0, rawLine.length - 1) else rawLine
            if (line.isEmpty()) continue
            if (isHeaderLine(line)) {
                val entriesForSection = ArrayList<String>()
                currentEntries = entriesForSection
                currentSeen = HashSet()
                rawNames.add(line.substring(1))
                rawEntries.add(entriesForSection)
            } else if (isValidSequence(line)) {
                val entriesForSection = currentEntries
                val seen = currentSeen
                if (entriesForSection != null && seen != null && seen.add(line)) {
                    entriesForSection.add(line)
                }
            }
            // Anything else is a malformed line and is dropped.
        }

        // Second pass: glyph-filter each category and keep only the ones with survivors.
        val outNames = ArrayList<String>()
        val outEntries = ArrayList<List<String>>()
        for (index in rawNames.indices) {
            val filtered = ArrayList<String>()
            for (entry in rawEntries[index]) {
                if (probe.hasGlyph(entry)) filtered.add(entry)
            }
            if (filtered.isNotEmpty()) {
                outNames.add(rawNames[index])
                outEntries.add(filtered.toList())
            }
        }
        if (outNames.isEmpty()) return EmojiSetSnapshot.EMPTY
        return EmojiSetSnapshot(outNames.toList(), outEntries.toList())
    }

    /**
     * A header is `#` followed by an ASCII category name. This deliberately excludes a line that
     * merely starts with `#` but continues with non-ASCII code points, such as the `#`-keycap
     * emoji sequence, which is a data line rather than a section marker.
     */
    private fun isHeaderLine(line: String): Boolean {
        if (line.length < 2 || line[0] != '#') return false
        for (index in 1 until line.length) {
            val c = line[index]
            val ok = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-'
            if (!ok) return false
        }
        return true
    }

    /** A well-formed sequence line is non-empty, within the length bound, and holds no whitespace. */
    private fun isValidSequence(line: String): Boolean {
        if (line.isEmpty() || line.length > MAX_SEQUENCE_CHARS) return false
        for (index in line.indices) {
            if (Character.isWhitespace(line[index])) return false
        }
        return true
    }
}
