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

import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalSubtypes
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.TpersFormat
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.DurableFileOps
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.SpaceProbe

/**
 * Mission `tt-personal-dict`: the store half of the silent-failure register in
 * `docs/SILENT-AUDIT.md` — A2, A3, A5 and B1.
 *
 * The whole subsystem chose fail-closed and forbade itself logging, and then never built a channel
 * for "this did not work". Every test here is about that missing channel, and each one names the
 * finding it closes. The harness is deliberately the one
 * [PersonalDictionaryStoreWriteTest] already uses — a real directory, a direct executor and
 * injectable durable ops — because the defects live in the write sequence and nowhere else.
 */
class PersonalDictionarySilentFailureTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val subtype = PersonalSubtypes.TATAR_RU

    // ---- A2: the outcome of a hand-added word ---------------------------------------------------

    /**
     * A2. `addManually` answered "the word passed the content filter" and the screen printed that as
     * "saved". A write that never lands — no space, failed re-validation, no directory — has to
     * arrive at the caller, because it is the only thing the user can be told: the package may not
     * log, and nothing else waits for the result.
     */
    @Test
    fun addingAWordThatCannotBeWrittenReportsFailure() {
        val directory = newPersonalDir()
        val store = store(directory, spaceProbe = SpaceProbe { 0L })

        val outcomes = mutableListOf<Boolean>()
        store.addManually("абыйлар") { outcomes.add(it) }

        assertEquals("exactly one answer per mutation", 1, outcomes.size)
        assertFalse("the word never reached the disk and the caller must hear so", outcomes[0])
        assertTrue("and nothing was published either", store.snapshot.isEmpty)
        assertFalse(destinationFile(directory).exists())
    }

    /** A2, the other direction: a write that did land reports success, and only once. */
    @Test
    fun addingAWordThatIsWrittenReportsSuccessAfterThePublish() {
        val directory = newPersonalDir()
        val store = store(directory)

        val seenAtCallback = mutableListOf<Int>()
        val outcomes = mutableListOf<Boolean>()
        store.addManually("абыйлар") {
            outcomes.add(it)
            // The report is not allowed to run BEFORE the snapshot exists: the screen repaints from
            // this callback, and repainting from an older snapshot is exactly the defect.
            seenAtCallback.add(store.snapshot.size)
        }

        assertEquals(listOf(true), outcomes)
        assertEquals("the published snapshot already carries the word", listOf(1), seenAtCallback)
    }

    /** A2. A word the content filter rejects is a failure too — the screen must not repaint blind. */
    @Test
    fun aRejectedWordAlsoProducesExactlyOneAnswer() {
        val store = store(newPersonalDir())

        val outcomes = mutableListOf<Boolean>()
        store.addManually("ab1") { outcomes.add(it) }

        assertEquals(listOf(false), outcomes)
    }

    // ---- A3: "Forget word" that did not forget --------------------------------------------------

    /**
     * A3. The rewrite fails, so the word stays both in memory and on disk — but the user had already
     * been told the opposite: the dialog closed and the band was cleared. The word came back on the
     * next keystroke with nothing said anywhere.
     */
    @Test
    fun forgettingAWordReportsFailureWhenTheRewriteDoesNotLand() {
        val directory = newPersonalDir()
        store(directory).apply {
            addManually("абыйлар")
            addManually("сүзлек")
        }
        // A second store over the same directory, this one unable to write anything at all.
        val faulting = store(directory, spaceProbe = SpaceProbe { 0L })

        val outcomes = mutableListOf<Boolean>()
        faulting.forget("сүзлек") { outcomes.add(it) }

        assertEquals(listOf(false), outcomes)
        assertTrue(
            "the word is still saved, which is precisely why the caller has to be told",
            faulting.snapshot.indexOfNormalized("сүзлек") >= 0,
        )
    }

    /** A3, the other direction: a rewrite that lands reports success and the word is gone. */
    @Test
    fun forgettingAWordReportsSuccessWhenTheRewriteLands() {
        val directory = newPersonalDir()
        val store = store(directory)
        store.addManually("абыйлар")
        store.addManually("сүзлек")

        val outcomes = mutableListOf<Boolean>()
        store.forget("сүзлек") { outcomes.add(it) }

        assertEquals(listOf(true), outcomes)
        assertTrue(store.snapshot.indexOfNormalized("сүзлек") < 0)
    }

    /** A3. A word that was never there is not a failure: from where the user stands it is gone. */
    @Test
    fun forgettingAWordThatIsNotSavedIsNotReportedAsAFailure() {
        val directory = newPersonalDir()
        val store = store(directory)
        store.addManually("абыйлар")

        val outcomes = mutableListOf<Boolean>()
        store.forget("сүзлек") { outcomes.add(it) }

        assertEquals(listOf(true), outcomes)
    }

    // ---- A5: "erased means erased" is a guarantee, not a race -----------------------------------

    /**
     * A5. The band unbinds the instant the dialog is confirmed, but the SOURCE the engine reads used
     * to lag by one whole-file write — serialize, fsync, re-validate, replace, fsync again, tens to
     * hundreds of milliseconds on the cheap devices this project targets. A keystroke inside that
     * window read the old snapshot and put the erased word back on the band, where a tap committed
     * it through the ordinary path.
     *
     * The probe below reads the published snapshot from inside the write itself — that is the window,
     * observed from the middle of it.
     */
    @Test
    fun theErasedWordIsGoneFromThePublishedSnapshotBeforeTheWriteBegins() {
        val directory = newPersonalDir()
        var underTest: PersonalDictionaryStore? = null
        val visibleDuringWrite = mutableListOf<Boolean>()
        val probingOpener = PersonalOutputOpener { temp ->
            object : FileOutputStream(temp) {
                override fun write(bytes: ByteArray) {
                    val store = underTest
                    if (store != null) {
                        visibleDuringWrite.add(store.snapshot.indexOfNormalized("сүзлек") >= 0)
                    }
                    super.write(bytes)
                }
            }
        }
        val store = store(directory, opener = probingOpener)
        underTest = store
        store.addManually("абыйлар")
        store.addManually("сүзлек")
        visibleDuringWrite.clear()

        store.forget("сүзлек")

        assertEquals("the rewrite happened", 1, visibleDuringWrite.size)
        assertFalse(
            "a reader that looks while the erasure is being written must not still see the word",
            visibleDuringWrite[0],
        )
    }

    /**
     * A5's other half, and the reason the publish cannot simply be moved and forgotten: when the
     * write fails the word IS still saved, so the snapshot has to come back. Claiming it is gone
     * would be the same lie in the other direction, and this time a permanent one.
     */
    @Test
    fun aFailedErasureRestoresThePublishedSnapshot() {
        val directory = newPersonalDir()
        store(directory).apply {
            addManually("абыйлар")
            addManually("сүзлек")
        }
        val faulting = store(directory, spaceProbe = SpaceProbe { 0L })
        faulting.prime()

        faulting.forget("сүзлек")

        assertEquals("both words are still saved", 2, faulting.snapshot.size)
        assertTrue(faulting.snapshot.indexOfNormalized("сүзлек") >= 0)
    }

    // ---- B1: "Erase all" must not stop at the first failure -------------------------------------

    /**
     * B1. The three deletions shared one `try`, so a failure on the dictionary file skipped the
     * pending counters and the salt. What that leaves behind is not harmless leftovers: the salt is
     * the same, so the hashes match, and words that were two thirds of the way to being learned keep
     * their progress and come back after three more completions — while the screen shows an empty
     * list, because memory was wiped first.
     */
    @Test
    fun erasingEverythingStillRemovesTheSaltAndTheCountersWhenTheDictionaryCannotBeDeleted() {
        val directory = newPersonalDir()
        val store = store(directory)
        store.addManually("абыйлар")
        store.noteCompletion("сүзләр")
        store.flush()
        assertTrue(destinationFile(directory).isFile)
        assertNotNull("the counters file exists before the erasure", pendingFile(directory))
        assertTrue("the salt exists before the erasure", saltFile(directory).isFile)

        // Only the dictionary file refuses to go: deleteFile() throws on it, as it did in production.
        val stubborn = object : PassthroughOps() {
            override fun delete(file: File): Boolean =
                if (file.name.endsWith(".tpers")) false else super.delete(file)
        }
        val outcomes = mutableListOf<Boolean>()
        store(directory, ops = stubborn).clearAll { outcomes.add(it) }

        assertTrue("the dictionary file is the one that could not go", destinationFile(directory).isFile)
        assertEquals("the counters must go anyway", null, pendingFile(directory))
        assertFalse("and so must the salt, or the same hashes match again", saltFile(directory).isFile)
        assertEquals(
            "an erasure that left the dictionary behind is not a successful erasure",
            listOf(false), outcomes,
        )
    }

    /** B1. Nothing on disk was touched at all because the user is locked out — also a failure. */
    @Test
    fun erasingEverythingBehindTheUnlockGateIsReportedAsAFailure() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")

        val outcomes = mutableListOf<Boolean>()
        store(directory, unlockGate = { false }).clearAll { outcomes.add(it) }

        assertEquals(listOf(false), outcomes)
        assertTrue("the file is untouched, which is exactly the case that must not pass",
            destinationFile(directory).isFile)
    }

    /** B1, the ordinary path: everything goes, and that is reported as success. */
    @Test
    fun erasingEverythingReportsSuccessWhenEveryFileIsGone() {
        val directory = newPersonalDir()
        val store = store(directory)
        store.addManually("абыйлар")
        store.noteCompletion("сүзләр")
        store.flush()

        val outcomes = mutableListOf<Boolean>()
        store.clearAll { outcomes.add(it) }

        assertEquals(listOf(true), outcomes)
        assertFalse(destinationFile(directory).exists())
        assertEquals(null, pendingFile(directory))
        assertFalse(saltFile(directory).exists())
    }


    // ---- helpers --------------------------------------------------------------------------------

    private val directExecutor = Executor { it.run() }

    private fun newPersonalDir(): File =
        File(temporaryFolder.newFolder(), "personal").also { assertTrue(it.mkdirs()) }

    private fun store(
        directory: File,
        ops: DurableFileOps = PassthroughOps(),
        opener: PersonalOutputOpener = PersonalOutputOpener { temp -> FileOutputStream(temp) },
        spaceProbe: SpaceProbe = SpaceProbe { Long.MAX_VALUE },
        unlockGate: () -> Boolean = { true },
    ): PersonalDictionaryStore = PersonalDictionaryStore(
        subtypeId = subtype,
        directoryProvider = { directory },
        fileOps = ops,
        outputOpener = opener,
        spaceProbe = spaceProbe,
        clock = { 1000L },
        executor = directExecutor,
        unlockGate = unlockGate,
    )

    private fun destinationFile(directory: File) =
        File(directory, TpersFormat.personalFileName(subtype))

    private fun pendingFile(directory: File): File? =
        directory.listFiles { file -> file.name.startsWith("pending-") }?.firstOrNull()

    private fun saltFile(directory: File) = File(directory, "salt.bin")

    /** Performs every durable op for real; a test overrides the one it wants to fail. */
    private open class PassthroughOps : DurableFileOps {
        override fun createNewFile(file: File): Boolean = file.createNewFile()

        override fun syncFile(fileDescriptor: FileDescriptor) = fileDescriptor.sync()

        override fun atomicRename(source: File, destination: File) {
            if (destination.exists() || !source.renameTo(destination)) throw IOException("rename failed")
        }

        override fun atomicReplace(source: File, destination: File) {
            if (!source.renameTo(destination)) {
                destination.delete()
                if (!source.renameTo(destination)) throw IOException("replace failed")
            }
        }

        override fun syncDirectory(directory: File) = Unit

        override fun delete(file: File): Boolean = file.delete()
    }
}
