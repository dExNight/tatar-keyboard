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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-contract for the personal store's placement (E4a-2): the directory is resolved from the
 * base (credential-protected) `noBackupFilesDir`, through its OWN `PersonalDirectoryProvider` seam,
 * and NEVER through the dictionary asset's `DeviceProtectedDirectoryProvider` nor a device-protected
 * context. Backup exclusion is already proven WHOLE by `BackupWhitelistSourceContractTest`
 * (`personal/` is a sensitive marker there and no allowing element resolves under it), so this test
 * does not re-enumerate it — a rule that names `personal/` under `no_backup` would be forbidden.
 */
class PersonalStoragePathSourceContractTest {
    private val factory by lazy {
        File(sourceRoot(), "java/rkr/simplekeyboard/inputmethod/latin/dictionary/personalstore/AndroidPersonalDictionaryStorage.kt").readText()
    }
    private val packageSource by lazy {
        packageDir().walkTopDown().filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
    }

    @Test
    fun theProductionSeamResolvesTheCredentialProtectedNoBackupFilesDir() {
        assertTrue("the personal directory lives in noBackupFilesDir", factory.contains("noBackupFilesDir"))
        assertTrue(factory.contains("PERSONAL_DIRECTORY_NAME"))
        assertFalse(
            "the personal store must never create a device-protected context",
            factory.contains("createDeviceProtectedStorageContext"),
        )
    }

    @Test
    fun theSeamIsItsOwnAndReusesNeitherTheDictionaryNorTheEmojiProvider() {
        assertTrue(
            "the personal store owns a PersonalDirectoryProvider",
            packageSource.contains("interface PersonalDirectoryProvider"),
        )
        assertFalse(
            "must not reuse the dictionary asset's device-protected seam",
            packageSource.contains("DeviceProtectedDirectoryProvider"),
        )
        assertFalse(
            "must not reuse the emoji recents seam",
            packageSource.contains("RecentEmojiFileProvider"),
        )
    }

    @Test
    fun theUnlockGateIsPresentSoDirectBootIsSurvivedWithoutException() {
        assertTrue("the credential-protected path requires the unlock gate", factory.contains("isUserUnlocked"))
    }

    private fun sourceRoot(): File =
        listOf(File("src/main"), File("app/src/main")).firstOrNull(File::isDirectory)
            ?: error("cannot locate app/src/main from ${File(".").absolutePath}")

    private fun packageDir(): File =
        File(sourceRoot(), "java/rkr/simplekeyboard/inputmethod/latin/dictionary/personalstore")
}
