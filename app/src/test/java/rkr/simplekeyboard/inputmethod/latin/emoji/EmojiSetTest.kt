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

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSetTest {

    /** A probe that renders everything, so parsing can be tested without any filtering. */
    private val acceptAll = GlyphProbe { true }

    private fun entriesOf(snapshot: EmojiSetSnapshot, category: Int): List<String> =
        snapshot.entriesOf(category)

    @Test
    fun normalInputKeepsCategoriesAndOrder() {
        val text = "#smileys\n😀\n😁\n#animals\n🐶\n🐱\n"
        val snapshot = EmojiSet.parse(text)

        assertEquals(2, snapshot.categoryCount)
        assertEquals("smileys", snapshot.categoryName(0))
        assertEquals("animals", snapshot.categoryName(1))
        assertEquals(listOf("😀", "😁"), entriesOf(snapshot, 0))
        assertEquals(listOf("🐶", "🐱"), entriesOf(snapshot, 1))
        assertEquals(4, snapshot.totalEntryCount())
    }

    @Test
    fun malformedLineIsDroppedButNeighboursSurvive() {
        // The middle line carries a space, so it is not a well-formed single sequence.
        val text = "#smileys\n😀\n😀 with junk\n😁\n"
        val snapshot = EmojiSet.parse(text)

        assertEquals(1, snapshot.categoryCount)
        assertEquals(listOf("😀", "😁"), entriesOf(snapshot, 0))
    }

    @Test
    fun fullyUnreadableInputYieldsEmptySnapshot() {
        // No section headers at all: every sequence line is homeless and dropped.
        val text = "😀\n😁\nnot a header\n🐶\n"
        val snapshot = EmojiSet.parse(text)

        assertTrue(snapshot.isEmpty)
        assertEquals(0, snapshot.categoryCount)
        assertEquals(0, snapshot.totalEntryCount())
    }

    @Test
    fun blankInputYieldsEmptySnapshot() {
        assertTrue(EmojiSet.parse("").isEmpty)
        assertTrue(EmojiSet.parse("\n\n\n").isEmpty)
    }

    @Test
    fun duplicateWithinSectionIsKeptOnlyOnce() {
        val text = "#smileys\n😀\n😀\n😁\n"
        val snapshot = EmojiSet.parse(text)

        assertEquals(listOf("😀", "😁"), entriesOf(snapshot, 0))
    }

    @Test
    fun categoryLeftEmptyAfterParsingIsAbsent() {
        // The "empty" section contains only a malformed line, so it never reaches the snapshot.
        val text = "#empty\nbad line here\n#smileys\n😀\n"
        val snapshot = EmojiSet.parse(text)

        assertEquals(1, snapshot.categoryCount)
        assertEquals("smileys", snapshot.categoryName(0))
    }

    @Test
    fun sequencesBeforeAnyHeaderAreDropped() {
        val text = "😀\n#smileys\n😁\n"
        val snapshot = EmojiSet.parse(text)

        assertEquals(1, snapshot.categoryCount)
        assertEquals(listOf("😁"), entriesOf(snapshot, 0))
    }

    @Test
    fun keycapLineStartingWithHashIsAnEntryNotAHeader() {
        // "#️⃣" starts with '#' but continues with non-ASCII, so it is a data line, not a section.
        val hashKeycap = StringBuilder().appendCodePoint(0x0023).appendCodePoint(0xFE0F)
            .appendCodePoint(0x20E3).toString()
        val text = "#symbols\n$hashKeycap\n"
        val snapshot = EmojiSet.parse(text)

        assertEquals(1, snapshot.categoryCount)
        assertEquals("symbols", snapshot.categoryName(0))
        assertEquals(listOf(hashKeycap), entriesOf(snapshot, 0))
    }

    @Test
    fun crlfLineEndingsAreTolerated() {
        val text = "#smileys\r\n😀\r\n😁\r\n"
        val snapshot = EmojiSet.parse(text)

        assertEquals(listOf("😀", "😁"), entriesOf(snapshot, 0))
    }

    @Test
    fun inputStreamOverloadReadsUtf8() {
        val text = "#smileys\n😀\n😁\n"
        val stream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
        val snapshot = EmojiSet.parse(stream)

        assertEquals(listOf("😀", "😁"), entriesOf(snapshot, 0))
    }

    @Test
    fun probeRejectingHalfKeepsExactlyTheAvailableEntriesCompactly() {
        val text = "#smileys\n😀\n😁\n😂\n😃\n😄\n😅\n"
        // Accept every other entry; the survivors must land at compact indices 0..2.
        val accepted = setOf("😀", "😂", "😄")
        val snapshot = EmojiSet.build(text, GlyphProbe { it in accepted })

        assertEquals(1, snapshot.categoryCount)
        assertEquals(3, snapshot.entryCount(0))
        assertEquals(listOf("😀", "😂", "😄"), entriesOf(snapshot, 0))
        // No holes: every index in range resolves to a rendered entry.
        for (index in 0 until snapshot.entryCount(0)) {
            assertTrue(snapshot.entryAt(0, index) in accepted)
        }
    }

    @Test
    fun categoryEmptyAfterProbeFilterIsDropped() {
        val text = "#animals\n🐶\n🐱\n#smileys\n😀\n😁\n"
        // The probe renders no animals, so that whole category disappears.
        val accepted = setOf("😀", "😁")
        val snapshot = EmojiSet.build(text, GlyphProbe { it in accepted })

        assertEquals(1, snapshot.categoryCount)
        assertEquals("smileys", snapshot.categoryName(0))
        assertEquals(listOf("😀", "😁"), entriesOf(snapshot, 0))
    }

    @Test
    fun probeRejectingEverythingYieldsEmptySnapshot() {
        val text = "#smileys\n😀\n😁\n"
        val snapshot = EmojiSet.build(text, GlyphProbe { false })

        assertTrue(snapshot.isEmpty)
    }

    @Test
    fun snapshotEntryOrderWithinSectionIsPreserved() {
        val text = "#smileys\n😅\n😀\n😂\n"
        val snapshot = EmojiSet.parse(text)

        assertEquals("😅", snapshot.entryAt(0, 0))
        assertEquals("😀", snapshot.entryAt(0, 1))
        assertEquals("😂", snapshot.entryAt(0, 2))
    }

    @Test
    fun headerWithNoNameIsNotTreatedAsASection() {
        // A lone '#' is not a valid header; the following sequence has no home and is dropped.
        val text = "#\n😀\n"
        val snapshot = EmojiSet.parse(text)

        assertTrue(snapshot.isEmpty)
    }

    @Test
    fun emptySnapshotConstantIsEmpty() {
        assertTrue(EmojiSetSnapshot.EMPTY.isEmpty)
        assertEquals(0, EmojiSetSnapshot.EMPTY.categoryCount)
        assertFalse(EmojiSet.parse("#smileys\n😀\n").isEmpty)
    }
}
