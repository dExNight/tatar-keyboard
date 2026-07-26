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

package rkr.simplekeyboard.inputmethod.keyboard

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * E2b-1: the space-bar layouts gain the emoji key without changing the disabled-toggle layout.
 *
 * The test re-implements the first-match &lt;switch&gt;/&lt;case&gt; evaluation of
 * [rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardBuilder] purely on the XML, so it does
 * not need Android. For each file it checks:
 *  - exactly eight &lt;case&gt; are present (zwnj × globe × showEmojiKey);
 *  - with showEmojiKey=false the parsed keys are byte-for-byte the pre-E2 layout across the four
 *    (globe × zwnj) combinations;
 *  - with showEmojiKey=true the emoji key appears left of the space bar and takes exactly the row's
 *    default width out of the space bar, so the row arithmetic is preserved.
 */
class SpaceKeyLayoutTest {

    private data class Key(val style: String, val width: Double?)

    private companion object {
        // A representative layout set from the zwnj list, and one outside it.
        private const val ZWNJ = "farsi"
        private const val NON_ZWNJ = "qwerty"
        private const val LANG = "languageSwitchKeyStyle"
        private const val EMOJI = "emojiKeyStyle"
        private const val SPACE = "spaceKeyStyle"
        private const val ZWNJ_KEY = "zwnjKeyStyle"
    }

    private fun spaceFile(name: String): File {
        val candidates = listOf(File("src/main/res/xml"), File("app/src/main/res/xml"))
        val dir = candidates.firstOrNull(File::isDirectory)
            ?: error("cannot locate res/xml from ${File(".").absolutePath}")
        return File(dir, name)
    }

    private fun switchElement(file: File): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder().parse(file)
        val switches = doc.getElementsByTagName("switch")
        assertTrue("no <switch> in ${file.name}", switches.length == 1)
        return switches.item(0) as Element
    }

    private fun childElements(parent: Element): List<Element> {
        val result = ArrayList<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            (nodes.item(i) as? Element)?.let { result.add(it) }
        }
        return result
    }

    private fun attrOrNull(el: Element, name: String): String? {
        val value = el.getAttribute(name)
        return if (value.isEmpty()) null else value
    }

    private fun keysOf(caseEl: Element): List<Key> =
        childElements(caseEl).filter { it.tagName == "Key" }.map { key ->
            val widthAttr = attrOrNull(key, "latin:keyWidth")
            Key(
                key.getAttribute("latin:keyStyle"),
                widthAttr?.removeSuffix("%p")?.toDouble(),
            )
        }

    /** First-match evaluation, exactly like KeyboardBuilder.parseCaseCondition. */
    private fun evaluate(
        switchEl: Element,
        layoutSet: String,
        globe: Boolean,
        emoji: Boolean,
    ): List<Key>? {
        for (child in childElements(switchEl)) {
            when (child.tagName) {
                "case" -> {
                    val ls = attrOrNull(child, "latin:keyboardLayoutSet")
                    val lsMatch = ls == null || ls.split("|").contains(layoutSet)
                    val g = attrOrNull(child, "latin:languageSwitchKeyEnabled")
                    val gMatch = g == null || g.toBoolean() == globe
                    val e = attrOrNull(child, "latin:showEmojiKey")
                    val eMatch = e == null || e.toBoolean() == emoji
                    if (lsMatch && gMatch && eMatch) return keysOf(child)
                }
                "default" -> return keysOf(child)
            }
        }
        return null
    }

    private fun caseCount(switchEl: Element): Int =
        childElements(switchEl).count { it.tagName == "case" }

    @Test
    fun eachFileHasExactlyEightCases() {
        assertEquals(8, caseCount(switchElement(spaceFile("key_space_5kw.xml"))))
        assertEquals(8, caseCount(switchElement(spaceFile("key_space_7kw.xml"))))
    }

    @Test
    fun phoneDisabledToggleIsIdenticalToPreE2() {
        val s = switchElement(spaceFile("key_space_5kw.xml"))
        assertEquals(
            listOf(Key(LANG, null), Key(SPACE, 30.0), Key(ZWNJ_KEY, null)),
            evaluate(s, ZWNJ, globe = true, emoji = false),
        )
        assertEquals(
            listOf(Key(SPACE, 40.0), Key(ZWNJ_KEY, null)),
            evaluate(s, ZWNJ, globe = false, emoji = false),
        )
        assertEquals(
            listOf(Key(LANG, null), Key(SPACE, 40.0)),
            evaluate(s, NON_ZWNJ, globe = true, emoji = false),
        )
        assertEquals(
            listOf(Key(SPACE, 50.0)),
            evaluate(s, NON_ZWNJ, globe = false, emoji = false),
        )
    }

    @Test
    fun tabletDisabledToggleIsIdenticalToPreE2() {
        val s = switchElement(spaceFile("key_space_7kw.xml"))
        assertEquals(
            listOf(Key(LANG, null), Key(SPACE, 45.0), Key(ZWNJ_KEY, null)),
            evaluate(s, ZWNJ, globe = true, emoji = false),
        )
        assertEquals(
            listOf(Key(SPACE, 54.0), Key(ZWNJ_KEY, null)),
            evaluate(s, ZWNJ, globe = false, emoji = false),
        )
        assertEquals(
            listOf(Key(LANG, null), Key(SPACE, 54.0)),
            evaluate(s, NON_ZWNJ, globe = true, emoji = false),
        )
        assertEquals(
            listOf(Key(SPACE, 63.0)),
            evaluate(s, NON_ZWNJ, globe = false, emoji = false),
        )
    }

    @Test
    fun phoneEnabledToggleAddsTenPercentEmojiKeyLeftOfSpace() {
        val s = switchElement(spaceFile("key_space_5kw.xml"))
        // 10%p emoji key; space bar loses exactly 10%p in every case.
        assertEquals(
            listOf(Key(LANG, null), Key(EMOJI, null), Key(SPACE, 20.0), Key(ZWNJ_KEY, null)),
            evaluate(s, ZWNJ, globe = true, emoji = true),
        )
        assertEquals(
            listOf(Key(EMOJI, null), Key(SPACE, 30.0), Key(ZWNJ_KEY, null)),
            evaluate(s, ZWNJ, globe = false, emoji = true),
        )
        assertEquals(
            listOf(Key(LANG, null), Key(EMOJI, null), Key(SPACE, 30.0)),
            evaluate(s, NON_ZWNJ, globe = true, emoji = true),
        )
        assertEquals(
            listOf(Key(EMOJI, null), Key(SPACE, 40.0)),
            evaluate(s, NON_ZWNJ, globe = false, emoji = true),
        )
    }

    @Test
    fun tabletEnabledToggleAddsNinePercentEmojiKeyLeftOfSpace() {
        val s = switchElement(spaceFile("key_space_7kw.xml"))
        // 9%p emoji key; space bar loses exactly 9%p in every case.
        assertEquals(
            listOf(Key(LANG, null), Key(EMOJI, null), Key(SPACE, 36.0), Key(ZWNJ_KEY, null)),
            evaluate(s, ZWNJ, globe = true, emoji = true),
        )
        assertEquals(
            listOf(Key(EMOJI, null), Key(SPACE, 45.0), Key(ZWNJ_KEY, null)),
            evaluate(s, ZWNJ, globe = false, emoji = true),
        )
        assertEquals(
            listOf(Key(LANG, null), Key(EMOJI, null), Key(SPACE, 45.0)),
            evaluate(s, NON_ZWNJ, globe = true, emoji = true),
        )
        assertEquals(
            listOf(Key(EMOJI, null), Key(SPACE, 54.0)),
            evaluate(s, NON_ZWNJ, globe = false, emoji = true),
        )
    }

    @Test
    fun everyCombinationResolvesToACase() {
        for (file in listOf("key_space_5kw.xml", "key_space_7kw.xml")) {
            val s = switchElement(spaceFile(file))
            for (layout in listOf(ZWNJ, NON_ZWNJ)) {
                for (globe in listOf(true, false)) {
                    for (emoji in listOf(true, false)) {
                        assertNotNull(
                            "$file [$layout globe=$globe emoji=$emoji] matched no case",
                            evaluate(s, layout, globe, emoji),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun emojiKeyStyleIsUsedOnlyBySpaceBarLayouts() {
        val xmlDir = spaceFile("key_space_5kw.xml").parentFile
            ?: error("res/xml has no parent directory")
        val users = xmlDir.listFiles { f -> f.extension == "xml" }.orEmpty()
            .filter { it.readText().contains("keyStyle=\"emojiKeyStyle\"") }
            .map { it.name }
            .sorted()
        assertEquals(listOf("key_space_5kw.xml", "key_space_7kw.xml"), users)
    }
}
