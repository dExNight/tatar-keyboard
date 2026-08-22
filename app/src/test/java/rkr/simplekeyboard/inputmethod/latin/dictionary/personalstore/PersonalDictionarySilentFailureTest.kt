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
import org.junit.Assert.assertArrayEquals
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
 * Missions `tt-personal-dict` and `tt-version-1.8.2`: the store half of the silent-failure
 * register in `docs/SILENT-AUDIT.md` — A2, A3, A5, B1, and the last two, B2 and B3.
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


    // ---- B2: an unreadable file is set aside, not destroyed --------------------------------------

    /**
     * B2. Validation failure used to DELETE the file: a truncated write after a power cut, a checksum
     * that no longer matches, a format the next version changes — any of them wiped the only data
     * this keyboard keeps about its user, with no copy and no message.
     *
     * The bytes are moved aside instead. Most corruption of this file is an interrupted write, so
     * what survives is most of the words; a future version with a repair path can only read them if
     * they still exist, and after a delete they never can.
     */
    @Test
    fun anUnreadableFileIsMovedAsideInsteadOfDestroyed() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")
        val unreadableBytes = corruptTheDictionary(directory)

        val store = store(directory)
        store.prime()

        assertFalse(
            "the unreadable file must not stay where the reader looks for a valid one",
            destinationFile(directory).exists(),
        )
        assertTrue("the bytes are still on the device", quarantineFile(directory).isFile)
        assertArrayEquals(
            "byte for byte: a repair path that cannot read the original is worth nothing",
            unreadableBytes,
            quarantineFile(directory).readBytes(),
        )
        assertTrue("and fail-closed still holds — nothing unreadable is published", store.snapshot.isEmpty)
    }

    /**
     * B2, the condition that makes the copy affordable: there is ONE quarantine slot per language,
     * and the next corruption overwrites it. Growth on disk is bounded by one file, not by how many
     * times the file has ever gone bad.
     */
    @Test
    fun aSecondUnreadableFileOverwritesTheOneQuarantineSlot() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")
        corruptTheDictionary(directory)
        store(directory).prime()

        // A fresh valid file is written after the first quarantine, and then it goes bad too.
        store(directory).addManually("сүзлек")
        val secondUnreadableBytes = corruptTheDictionary(directory)
        store(directory).prime()

        assertEquals(
            "one slot, however many corruptions: the copy may not accumulate on the device",
            1,
            quarantineFiles(directory).size,
        )
        assertArrayEquals(
            "and the slot holds the latest corruption, not the first",
            secondUnreadableBytes,
            quarantineFile(directory).readBytes(),
        )
    }

    /**
     * B2, the condition without which keeping the copy would be indefensible: "Erase all" erases it.
     * These bytes are the user's own words, no screen shows them, and a privacy promise the user
     * cannot enforce is not a promise.
     */
    @Test
    fun erasingEverythingRemovesTheQuarantineCopyToo() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")
        corruptTheDictionary(directory)
        store(directory).prime()
        assertTrue("the copy exists before the erasure", quarantineFile(directory).isFile)

        val outcomes = mutableListOf<Boolean>()
        store(directory).clearAll { outcomes.add(it) }

        assertFalse(
            "the one copy of the user's words that no screen shows must still be removable",
            quarantineFile(directory).exists(),
        )
        assertEquals(listOf(true), outcomes)
    }

    /** B2. A quarantine copy that will not go is a failed erasure: the words are still on the device. */
    @Test
    fun anErasureThatCannotRemoveTheQuarantineCopyIsNotReportedAsSuccess() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")
        corruptTheDictionary(directory)
        store(directory).prime()

        val stubborn = object : PassthroughOps() {
            override fun delete(file: File): Boolean =
                if (file.name.endsWith(QUARANTINE_SUFFIX)) false else super.delete(file)
        }
        val outcomes = mutableListOf<Boolean>()
        store(directory, ops = stubborn).clearAll { outcomes.add(it) }

        assertTrue("the copy is the file that could not go", quarantineFile(directory).isFile)
        assertEquals(
            "words still on the device after 'erase everything' is not success",
            listOf(false), outcomes,
        )
    }

    /**
     * B2, the other half of the finding: the user is TOLD. An empty list that the user did not empty
     * is exactly the silence this whole register is about — they cannot tell whether they erased it
     * themselves, and the subsystem may not log, so this notice is the only thing that ever says so.
     *
     * Once, not once per open: the second `prime()` here is the ordinary second reader arriving.
     */
    @Test
    fun theUserIsToldOnceWhenAnUnreadableFileIsSetAside() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")
        corruptTheDictionary(directory)

        var notices = 0
        val store = store(directory, notice = PersonalQuarantineNotice { notices++ })
        store.prime()
        store.prime()

        assertEquals("exactly one notice per corruption", 1, notices)
    }

    /** B2. And nothing at all is said when the file reads fine — the notice is not a startup event. */
    @Test
    fun aReadableFileSaysNothing() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")

        var notices = 0
        store(directory, notice = PersonalQuarantineNotice { notices++ }).prime()

        assertEquals(0, notices)
    }

    /**
     * B2. If the move itself cannot happen, the unreadable file is removed after all — leaving it
     * where the reader looks would fail validation again on every single start — and the user is told
     * either way, because what they are told is that the list is empty and that they did not do it.
     */
    @Test
    fun theUserIsToldEvenWhenTheCopyCannotBeMade() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")
        corruptTheDictionary(directory)
        val cannotMove = object : PassthroughOps() {
            override fun atomicReplace(source: File, destination: File) =
                throw IOException("no rename")
        }

        var notices = 0
        val store = store(directory, ops = cannotMove, notice = PersonalQuarantineNotice { notices++ })
        store.prime()

        assertEquals(1, notices)
        assertFalse(
            "an unreadable file left in place would fail validation on every start",
            destinationFile(directory).exists(),
        )
        assertTrue(store.snapshot.isEmpty)
    }

    // ---- B3: a failed removal is an answer, not a dead process ----------------------------------

    /**
     * B3. `forget` was the ONE mutation without a `try`, and its exception did not stay inside it.
     * `deleteFile()` never returns false — it returns true or throws — so removing the LAST saved
     * word threw straight out of the lambda, onto a single-thread executor created without an
     * `UncaughtExceptionHandler`, where the default handler is `KillApplicationHandler`: the keyboard
     * died in the middle of typing in someone else's app.
     *
     * The executor here is that production worker in miniature — one thread per event, with a handler
     * standing exactly where the killer stands in the app. Anything it collects would have been a
     * dead IME.
     */
    @Test
    fun aRemovalThatCannotDeleteTheFileAnswersInsteadOfKillingTheWorker() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")
        val stubborn = object : PassthroughOps() {
            override fun delete(file: File): Boolean =
                if (file.name.endsWith(".tpers")) false else super.delete(file)
        }
        val uncaught = mutableListOf<Throwable>()
        val faulting = store(directory, ops = stubborn, executor = workerWithKiller(uncaught))

        val outcomes = mutableListOf<Boolean>()
        faulting.forget("абыйлар") { outcomes.add(it) }

        assertEquals(
            "nothing may reach the worker's uncaught handler: in production that handler kills the IME",
            emptyList<Throwable>(), uncaught,
        )
        assertEquals("the user hears a refusal instead", listOf(false), outcomes)
    }

    /**
     * B3, the state the refusal has to describe truthfully: a delete that did not happen leaves the
     * word saved, so the published snapshot must show it again. Falling over protected nothing here —
     * that is the whole reason the crash was the wrong answer — but neither may the store claim the
     * word is gone.
     */
    @Test
    fun aRemovalThatCannotDeleteTheFileLeavesTheWordSavedAndSaysSo() {
        val directory = newPersonalDir()
        store(directory).addManually("абыйлар")
        val stubborn = object : PassthroughOps() {
            override fun delete(file: File): Boolean =
                if (file.name.endsWith(".tpers")) false else super.delete(file)
        }
        val faulting = store(directory, ops = stubborn)

        val outcomes = mutableListOf<Boolean>()
        faulting.forget("абыйлар") { outcomes.add(it) }

        assertEquals(listOf(false), outcomes)
        assertTrue("the word is still on disk", destinationFile(directory).isFile)
        assertTrue(
            "and still published, because it is still saved",
            faulting.snapshot.indexOfNormalized("абыйлар") >= 0,
        )
    }

    // ---- helpers --------------------------------------------------------------------------------

    private val directExecutor = Executor { it.run() }

    private fun newPersonalDir(): File =
        File(temporaryFolder.newFolder(), "personal").also { assertTrue(it.mkdirs()) }

    /**
     * The production worker in miniature: one thread per event, joined so the test stays as
     * sequential as the direct executor, and an `UncaughtExceptionHandler` standing where
     * `KillApplicationHandler` stands in the app.
     */
    private fun workerWithKiller(uncaught: MutableList<Throwable>) = Executor { runnable ->
        val thread = Thread(runnable, "personal-dictionary-test")
        thread.setUncaughtExceptionHandler { _, error -> uncaught.add(error) }
        thread.start()
        thread.join()
    }

    private fun store(
        directory: File,
        ops: DurableFileOps = PassthroughOps(),
        opener: PersonalOutputOpener = PersonalOutputOpener { temp -> FileOutputStream(temp) },
        spaceProbe: SpaceProbe = SpaceProbe { Long.MAX_VALUE },
        unlockGate: () -> Boolean = { true },
        executor: Executor = directExecutor,
        notice: PersonalQuarantineNotice? = null,
    ): PersonalDictionaryStore = PersonalDictionaryStore(
        subtypeId = subtype,
        directoryProvider = { directory },
        fileOps = ops,
        outputOpener = opener,
        spaceProbe = spaceProbe,
        clock = { 1000L },
        executor = executor,
        unlockGate = unlockGate,
        quarantineNotice = notice,
    )

    private fun destinationFile(directory: File) =
        File(directory, TpersFormat.personalFileName(subtype))

    private fun pendingFile(directory: File): File? =
        directory.listFiles { file -> file.name.startsWith("pending-") }?.firstOrNull()

    private fun saltFile(directory: File) = File(directory, "salt.bin")

    /** Flips one payload byte so the checksum no longer matches; returns the unreadable bytes. */
    private fun corruptTheDictionary(directory: File): ByteArray {
        val destination = destinationFile(directory)
        val bytes = destination.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x7f).toByte()
        destination.writeBytes(bytes)
        return bytes
    }

    private fun quarantineFile(directory: File) =
        File(directory, TpersFormat.personalFileName(subtype) + QUARANTINE_SUFFIX)

    private fun quarantineFiles(directory: File): List<File> =
        directory.listFiles { file -> file.name.endsWith(QUARANTINE_SUFFIX) }?.toList() ?: emptyList()

    private companion object {
        /** The suffix the store appends for the one quarantine slot; see `PersonalDictionaryStore`. */
        const val QUARANTINE_SUFFIX = ".quarantine"
    }

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
