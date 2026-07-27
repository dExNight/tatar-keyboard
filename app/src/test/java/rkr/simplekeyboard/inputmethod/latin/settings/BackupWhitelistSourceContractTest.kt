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

package rkr.simplekeyboard.inputmethod.latin.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2b-3 source-contract: backup is closed as a WHITELIST, in the style of the other
 * source-contract tests (it greps the frozen source rather than exercising Android). The
 * exact XML shape is not invented here — it is fixed in docs/DICTIONARY-E2.md together
 * with the final XML; what is checked is the RESULT, in the three assertions the contract
 * names, plus a fourth on the code:
 *
 *  (1) neither section of either rule edition carries a single allowing (<include>)
 *      element;
 *  (2) no allowing element can resolve to a path under `personal/`, `dictionaries/` or the
 *      "recent emoji" medium (`recent_emoji*`);
 *  (3) the manifest carries android:allowBackup="false" and references both XML editions
 *      (and no longer carries android:fullBackupOnly);
 *  (4) not one call to BackupManager remains in the code.
 *
 * The level-2 check on the BUILT artifact (aapt2 on the APK) lives in
 * scripts/check-no-internet.sh; this test is the source level.
 *
 * Every predicate this test relies on is proven fail-capable by exercising it against a
 * deliberately-broken in-memory input as well as the real files, so a future regression
 * that reopens backup turns this test red.
 */
class BackupWhitelistSourceContractTest {

    private fun sourceRoot(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull(File::isDirectory)
            ?: error("cannot locate app/src/main from ${File(".").absolutePath}")
    }

    private val manifest by lazy { File(sourceRoot(), "AndroidManifest.xml").readText() }
    private val dataExtractionRules by lazy {
        stripXmlComments(File(sourceRoot(), "res/xml/data_extraction_rules.xml").readText())
    }
    private val fullBackupContent by lazy {
        stripXmlComments(File(sourceRoot(), "res/xml/backup_rules.xml").readText())
    }

    /** The data domains a whitelist must exclude WHOLE (regular + device-protected). */
    private val requiredDomains = setOf(
        "file", "database", "sharedpref", "external",
        "device_file", "device_database", "device_sharedpref",
    )

    /** Path fragments that must never be reachable by an allowing element. */
    private val sensitiveMarkers = listOf("personal", "dictionaries", "recent_emoji")

    // --- pure predicates (kept pure so the fail-capability tests can exercise them) -----------

    private fun stripXmlComments(xml: String): String =
        Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL).replace(xml, "")

    /** Inner text of the first <tag>...</tag> element (tags here carry no attributes). */
    private fun innerXml(xml: String, tag: String): String {
        val start = xml.indexOf("<$tag>")
        require(start >= 0) { "<$tag> not found" }
        val open = xml.indexOf('>', start) + 1
        val close = xml.indexOf("</$tag>", open)
        require(close >= 0) { "</$tag> not found" }
        return xml.substring(open, close)
    }

    /** Every <include ...> element (the only allowing element in these schemas). */
    private fun allowingElements(xml: String): List<String> =
        Regex("<include\\b[^>]*>").findAll(xml).map { it.value }.toList()

    /** Domains named by <exclude domain="..."> in the given fragment. */
    private fun excludedDomains(xml: String): Set<String> =
        Regex("<exclude\\b[^>]*\\bdomain=\"([^\"]+)\"").findAll(xml)
            .map { it.groupValues[1] }.toSet()

    /**
     * An allowing element could expose sensitive data if it opens a whole domain (no path,
     * so it covers every file including the sensitive ones) or names a sensitive path.
     */
    private fun includeCouldExposeSensitive(includeElement: String): Boolean {
        val path = Regex("\\bpath=\"([^\"]*)\"").find(includeElement)?.groupValues?.get(1)
        if (path.isNullOrBlank()) return true
        return sensitiveMarkers.any { path.contains(it) }
    }

    // --- (1) no allowing element in any section of either edition -----------------------------

    @Test
    fun neitherSectionOfEitherEditionCarriesAnAllowingElement() {
        val sections = listOf(
            "data_extraction_rules/cloud-backup" to innerXml(dataExtractionRules, "cloud-backup"),
            "data_extraction_rules/device-transfer" to innerXml(dataExtractionRules, "device-transfer"),
            "backup_rules/full-backup-content" to innerXml(fullBackupContent, "full-backup-content"),
        )
        for ((name, body) in sections) {
            assertTrue(
                "$name must contain no allowing <include> element",
                allowingElements(body).isEmpty(),
            )
        }
    }

    // --- (2) no allowing element can resolve under personal/, dictionaries/ or recents --------

    @Test
    fun noAllowingElementCanResolveUnderASensitivePath() {
        // The strongest form of (2): the allow-list is empty, so nothing can resolve anywhere.
        val allIncludes = allowingElements(dataExtractionRules) + allowingElements(fullBackupContent)
        assertTrue(
            "an empty allow-list is the only way to guarantee nothing resolves to a sensitive path",
            allIncludes.isEmpty(),
        )
        // And, defensively, any include that ever appeared must not reach a sensitive path.
        for (include in allIncludes) {
            assertFalse(
                "allowing element resolves to a sensitive path: $include",
                includeCouldExposeSensitive(include),
            )
        }
    }

    // --- whitelist completeness: every domain excluded whole, both sections ------------------

    @Test
    fun dataExtractionRulesDeclaresBothSectionsEachExcludingEveryDomain() {
        assertTrue(dataExtractionRules.contains("<data-extraction-rules>"))
        assertTrue("cloud-backup section required", dataExtractionRules.contains("<cloud-backup>"))
        assertTrue("device-transfer section required", dataExtractionRules.contains("<device-transfer>"))
        assertEquals(
            "cloud-backup must exclude every data domain whole",
            requiredDomains,
            excludedDomains(innerXml(dataExtractionRules, "cloud-backup")),
        )
        assertEquals(
            "device-transfer must exclude every data domain whole",
            requiredDomains,
            excludedDomains(innerXml(dataExtractionRules, "device-transfer")),
        )
    }

    @Test
    fun fullBackupContentExcludesEveryDomainWhole() {
        assertTrue(fullBackupContent.contains("<full-backup-content>"))
        assertEquals(
            "full-backup-content must exclude every data domain whole",
            requiredDomains,
            excludedDomains(innerXml(fullBackupContent, "full-backup-content")),
        )
    }

    // --- (3) manifest disables backup and references both editions ---------------------------

    @Test
    fun manifestDisablesBackupAndReferencesBothRuleEditions() {
        assertTrue(
            "android:allowBackup must be false",
            manifest.contains("android:allowBackup=\"false\""),
        )
        assertFalse(
            "android:allowBackup=\"true\" must be gone",
            manifest.contains("android:allowBackup=\"true\""),
        )
        assertFalse(
            "android:fullBackupOnly is meaningless with allowBackup=false and must be removed",
            manifest.contains("android:fullBackupOnly"),
        )
        assertTrue(
            "manifest must reference the API 31+ rules",
            manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""),
        )
        assertTrue(
            "manifest must reference the API 24-30 rules",
            manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""),
        )
    }

    // --- (4) no BackupManager call anywhere in the code --------------------------------------

    @Test
    fun noBackupManagerReferenceRemainsInTheCode() {
        val javaRoot = File(sourceRoot(), "java")
        val offenders = javaRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { it.readText().contains("BackupManager") }
            .map { it.name }
            .toList()
        assertTrue("BackupManager must not appear in the code: $offenders", offenders.isEmpty())
    }

    // --- fail-capability: prove each predicate turns red on a broken input --------------------

    @Test
    fun theNoAllowingElementPredicateIsFailCapable() {
        // Real sections have none; a section with an <include> is detected.
        assertTrue(allowingElements(innerXml(dataExtractionRules, "cloud-backup")).isEmpty())
        val broken = """
            <cloud-backup>
                <include domain="sharedpref" path="dictionaries/" />
                <exclude domain="file" />
            </cloud-backup>
        """.trimIndent()
        assertFalse(allowingElements(innerXml(broken, "cloud-backup")).isEmpty())
    }

    @Test
    fun theSensitivePathPredicateIsFailCapable() {
        // A whole-domain include (no path) and a dictionaries/ include are both flagged;
        // a genuinely narrow, non-sensitive include is not.
        assertTrue(includeCouldExposeSensitive("<include domain=\"sharedpref\" />"))
        assertTrue(includeCouldExposeSensitive("<include domain=\"file\" path=\"personal/word.list\" />"))
        assertTrue(includeCouldExposeSensitive("<include domain=\"file\" path=\"dictionaries/x.tdict\" />"))
        assertTrue(includeCouldExposeSensitive("<include domain=\"file\" path=\"recent_emoji_v1\" />"))
        assertFalse(includeCouldExposeSensitive("<include domain=\"file\" path=\"public/manual.txt\" />"))
    }

    @Test
    fun theExcludedDomainsPredicateIsFailCapable() {
        assertEquals(requiredDomains, excludedDomains(innerXml(dataExtractionRules, "cloud-backup")))
        // Dropping a domain from a section must be detectable.
        val missingOne = "<cloud-backup><exclude domain=\"file\" /></cloud-backup>"
        assertFalse(requiredDomains == excludedDomains(innerXml(missingOne, "cloud-backup")))
    }
}
