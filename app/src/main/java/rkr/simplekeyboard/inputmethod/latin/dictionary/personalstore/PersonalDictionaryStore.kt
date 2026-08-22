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

import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalDictionary
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalSubtypes
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.TpersFormat
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.TpersValidator
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.DurableFileOps
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.SpaceProbe
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.StorageClock
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.Executor

/**
 * The serialized owner of one subtype's personal `.tpers` file.
 *
 * Every mutation is an event on the single background [executor], so all in-memory state lives on
 * one worker and never on the UI thread. After each SUCCESSFUL mutation a fresh immutable
 * [PersonalDictionary] snapshot is published through the `@Volatile` [snapshot] reference; the
 * engine's worker thread reads it. The UI thread does no I/O, no checksum, no read and no write.
 *
 * Whole-file write only, in the frozen sequence (E4a-2): exclusive temp in the same directory →
 * write → flush → fsync file → RE-VALIDATE the written bytes → atomic replace → fsync directory.
 * A partial temp never becomes the main file; any failure leaves the previous valid file untouched;
 * temp garbage is removed when the store next opens the directory.
 *
 * Fail-closed everywhere: a caught exception (including the `UserManager.isUserUnlocked() == false`
 * gate closing the credential-protected path before the first unlock) drops the mutation and leaves
 * the feature empty rather than throwing. Nothing here logs, and no message carries the user's word
 * or the file path.
 *
 * E4a-2 wires none of this into the live IME — it is exercised only from tests via the explicit API
 * below. The settings toggle, the merge with dictionary candidates and clean-run learning are E4b
 * and E4c.
 */
internal class PersonalDictionaryStore(
    private val subtypeId: String,
    private val directoryProvider: PersonalDirectoryProvider,
    private val fileOps: DurableFileOps,
    private val outputOpener: PersonalOutputOpener,
    private val spaceProbe: SpaceProbe,
    private val clock: StorageClock,
    private val executor: Executor,
    private val validator: TpersValidator = TpersValidator(),
    private val unlockGate: () -> Boolean = { true },
    private val maxEntries: Int = TpersFormat.MAX_PERSONAL_ENTRIES.toInt(),
    private val quarantineNotice: PersonalQuarantineNotice? = null,
) {
    private val alphabet: Set<Int>? = PersonalSubtypes.alphabetFor(subtypeId)

    // Worker-confined state (touched only on [executor]).
    private var entries: PersonalEntries = PersonalEntries.empty(maxEntries)
    private var loaded = false
    private var pendingCounterFlush = false

    // E4c: progress towards learning, as salted truncated hashes. Held in memory and written on the
    // same boundary as the usage counters — never once per completed word.
    private var pending: PendingCounters = PendingCounters.EMPTY
    private var pendingDirty = false
    private var salt: ByteArray? = null

    /** Count of physical `.tpers` writes performed; a test counter (never grows on in-memory notes). */
    @Volatile
    var writeCount: Int = 0
        private set

    /** The published immutable snapshot; read by the engine's worker thread. */
    @Volatile
    var snapshot: PersonalDictionary = PersonalDictionary.EMPTY
        private set

    /**
     * Manually adds one word (E4b's "Add word…" path); a no-op if the word is not eligible.
     *
     * [outcome] is told what actually happened, on the worker, once the whole-file write has either
     * succeeded or failed — never at the moment the event was queued. Without it the screen could
     * only report that it had asked, and a write that ran out of space or failed re-validation would
     * be invisible: no list entry, no message, no retry.
     */
    fun addManually(word: String, outcome: PersonalMutationOutcome? = null) = onWorker {
        val normalized = eligibleNormalizedForm(word)
        if (normalized == null) {
            outcome?.onFinished(false)
            return@onWorker
        }
        // Evaluated first and reported second, deliberately: inside `outcome?.onFinished(...)` a
        // null outcome would short-circuit the argument too, and the write itself would vanish.
        val saved = commitWrite(entries.upsert(word, normalized))
        outcome?.onFinished(saved)
    }

    /**
     * Records ONE clean completion of [word] (E4c). Nothing is written to the dictionary until the
     * word has survived [PendingCounters.LEARN_THRESHOLD] of them; until then only a salted
     * truncated hash exists, and only in memory between flushes.
     *
     * The threshold is 3 for a reason worth keeping written down: 1 would learn any typo, 2 would
     * learn a typo repeated twice — and the same slip is exactly what a person repeats — while 3
     * demands that the spelling survive three independent completions without a single correction.
     */
    fun noteCompletion(word: String) = onWorker {
        val normalized = eligibleNormalizedForm(word) ?: return@onWorker
        if (entries.containsNormalized(normalized)) return@onWorker
        val key = PendingCounters.keyOf(saltOrCreate() ?: return@onWorker, normalized)
        val noted = pending.note(key)
        if (noted.countOf(key) >= PendingCounters.LEARN_THRESHOLD) {
            // Graduated: it goes into the dictionary itself, and its pending trace goes away.
            val candidate = entries.upsert(word, normalized)
            if (writeWhole(candidate)) {
                entries = candidate
                snapshot = candidate.toSnapshot(subtypeId)
                pending = noted.without(key)
            } else {
                pending = noted
            }
        } else {
            pending = noted
        }
        pendingDirty = true
    }

    /**
     * Removes one word and rewrites (or deletes, when it was the last) the file.
     *
     * Two things the caller is entitled to and did not use to get:
     *
     * * [outcome] is told whether the word is really gone. A failed rewrite leaves the word both in
     *   memory and on disk, and the user had already been told the opposite — the dialog closed and
     *   the band was cleared — so the word came back on the next keystroke with nothing said.
     * * The removal is published to READERS before the write, not after it. "Erased means erased" is
     *   the whole value of this feature, and the engine reads [snapshot] from its own thread: while
     *   the write was in flight — two fsyncs, tens to hundreds of milliseconds on the cheap devices
     *   this project targets — a keystroke could still bring the erased word back onto the band. If
     *   the write then fails, the previous snapshot is restored, because at that point the word IS
     *   still saved and pretending otherwise would be the same lie in the other direction.
     */
    fun forget(word: String, outcome: PersonalMutationOutcome? = null) = onWorker {
        // B3. This was the one mutation whose body ran outside a `try`, and the exception did not
        // stay inside it: the worker is a bare single-thread executor created without an
        // `UncaughtExceptionHandler`, so `deleteFile()` throwing on the removal of the LAST saved
        // word reached the default handler — `KillApplicationHandler`. The keyboard died in the
        // middle of typing in someone else's app.
        //
        // Falling over protected nothing here. The usual argument for a loud crash assumes data is
        // being lost; a delete that did not happen loses nothing — the word simply stays saved. The
        // cost was a dead IME and there was no gain, so the refusal travels the channel instead, and
        // [outcome] hears exactly one answer whatever happens below.
        val removed = try {
            removeOnWorker(word)
        } catch (_: Exception) {
            false
        }
        outcome?.onFinished(removed)
    }

    /** The body of [forget], on the worker: returns whether the word is really gone. */
    private fun removeOnWorker(word: String): Boolean {
        if (!open()) return false
        if (alphabet == null) return false
        val normalized = PersonalWordFilter.normalize(word)
        // The pending hash goes with the word: forgetting it must not leave progress behind that
        // would re-learn it after three more completions.
        salt?.let { existing ->
            val key = PendingCounters.keyOf(existing, normalized)
            if (pending.countOf(key) > 0) {
                pending = pending.without(key)
                pendingDirty = true
            }
        }
        val candidate = entries.remove(normalized)
        if (candidate === entries) {
            // The word was not in this dictionary at all: nothing to remove, and from where the
            // user stands it is gone, which is what they asked for.
            return true
        }
        val previousSnapshot = snapshot
        snapshot = if (candidate.isEmpty) PersonalDictionary.EMPTY else candidate.toSnapshot(subtypeId)
        // B3, the part that makes the refusal true rather than merely quiet: a throw here becomes
        // `false` HERE, inside the mutation, so the restore below still runs. The word is still on
        // disk at this point, and a snapshot that hides it would turn one lie into a permanent one.
        val removed = try {
            if (candidate.isEmpty) deleteFile() else writeWhole(candidate)
        } catch (_: Exception) {
            false
        }
        if (removed) {
            entries = candidate
        } else {
            snapshot = previousSnapshot
        }
        return removed
    }

    /**
     * Erases this subtype's personal dictionary: empties memory and deletes the file, the pending
     * counters, the salt and any quarantined copy of an unreadable file. The salt goes too, so the
     * hashes of a future session cannot be compared with those of the erased one; a new one is
     * created on demand.
     */
    fun clearAll(outcome: PersonalMutationOutcome? = null) = onWorker {
        entries = PersonalEntries.empty(maxEntries)
        pendingCounterFlush = false
        pending = PendingCounters.EMPTY
        pendingDirty = false
        salt = null
        snapshot = PersonalDictionary.EMPTY
        loaded = true
        if (!unlockGate()) {
            // Memory is empty, the files are untouched and the next process start reads them all
            // back. The screen shows an empty list either way, so this is exactly the case that must
            // not pass for success.
            outcome?.onFinished(false)
            return@onWorker
        }
        // Three INDEPENDENT deletions. They used to share one try, so a failure on the first one
        // skipped the other two and left the salt and the pending counters behind: the same salt
        // means the same hashes, so words two-thirds of the way to being learned kept their
        // progress and re-appeared after three more completions, with the screen showing nothing.
        val dictionaryGone = deleted { deleteFile() }
        val directory = runCatching { directoryProvider.personalDirectory() }.getOrNull()
        val countersGone = directory != null &&
            deleted { deleteFile(directory, File(directory, pendingFileName())) }
        val saltGone = directory != null &&
            deleted { deleteFile(directory, File(directory, SALT_FILE_NAME)) }
        // B2 keeps a copy of an unreadable file (see [quarantine]), and that copy is the user's own
        // words, which no screen shows. "Erase all" is the only way they can ask for those bytes to
        // go, so a copy left behind is a failed erasure — not a detail.
        val quarantineGone = directory != null &&
            deleted { deleteFile(directory, File(directory, quarantineFileName())) }
        outcome?.onFinished(dictionaryGone && countersGone && saltGone && quarantineGone)
    }

    /**
     * Records an accepted personal suggestion as a use: bumps the counter and LRU serial IN MEMORY
     * only, publishing the updated snapshot. It never rewrites the file — flushing whole for every
     * tap would be up to 128 KiB plus two fsyncs per tap. Runs as an executor event, not inline on
     * the UI thread, so it can never race [clearAll].
     */
    fun noteAcceptedSuggestion(word: String) = onWorker {
        if (!open()) return@onWorker
        alphabet ?: return@onWorker
        val candidate = entries.noteUse(PersonalWordFilter.normalize(word)) ?: return@onWorker
        entries = candidate
        snapshot = candidate.toSnapshot(subtypeId)
        pendingCounterFlush = true
    }

    /**
     * Flushes in-memory counter/serial changes to disk once, only when something changed — the one
     * boundary (`SuggestionsController.onFinishInput`) where both the usage counters and the pending
     * hashes are written, never per keystroke and never per completed word.
     */
    fun flush() = onWorker {
        if (!open()) return@onWorker
        if (pendingCounterFlush && writeWhole(entries)) pendingCounterFlush = false
        if (pendingDirty) {
            pending = pending.prunedForFlush()
            if (writePending(pending)) pendingDirty = false
        }
    }

    /**
     * Opens the store on its worker if it is not open yet, publishing the snapshot the engine will
     * read. A no-op afterwards. Safe to call from any thread — like every other mutation it is an
     * event on the executor, so the file read never lands on the caller's thread.
     */
    fun prime() = onWorker { open() }

    /** Test hook: runs [block] on the store's executor (so tests can drive the serialized owner). */
    fun runOnWorker(block: () -> Unit) = onWorker(block)

    private fun onWorker(block: () -> Unit) = executor.execute(block)

    private fun eligibleNormalizedForm(word: String): String? {
        if (!open()) return null
        val alpha = alphabet ?: return null
        return PersonalWordFilter.acceptedNormalizedForm(word, alpha)
    }

    private fun commitWrite(candidate: PersonalEntries): Boolean {
        if (!writeWhole(candidate)) return false
        entries = candidate
        snapshot = candidate.toSnapshot(subtypeId)
        return true
    }

    /**
     * Runs ONE erasure step so that its failure can neither skip the next step nor escape: returns
     * whether it went through. Nothing is logged — not the path, not the reason — which is why the
     * boolean has to travel back to the caller instead.
     */
    private inline fun deleted(step: () -> Unit): Boolean = try {
        step()
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Opens the directory once: honours the unlock gate (before the first unlock the path is
     * physically inaccessible, so the feature stays empty and untouched), removes stale temps, reads
     * the file into the model, and sets an unreadable file ASIDE (see [quarantine]), publishing an
     * empty snapshot in the same step. Returns true once the store is loaded.
     */
    private fun open(): Boolean {
        if (loaded) return true
        if (!unlockGate()) return false
        loaded = true
        try {
            val directory = directoryProvider.personalDirectory()
            if (!directory.isDirectory) {
                snapshot = PersonalDictionary.EMPTY
                return true
            }
            cleanupTemps(directory)
            readPending(directory)
            val file = File(directory, TpersFormat.personalFileName(subtypeId))
            if (!file.isFile) {
                snapshot = PersonalDictionary.EMPTY
                return true
            }
            val validated = try {
                validator.validate(file, subtypeId)
            } catch (_: Exception) {
                null
            }
            if (validated == null) {
                quarantine(directory, file)
                entries = PersonalEntries.empty(maxEntries)
                snapshot = PersonalDictionary.EMPTY
                return true
            }
            entries = PersonalEntries.fromValidated(validated, maxEntries)
            snapshot = entries.toSnapshot(subtypeId)
            readPending(directory)
        } catch (_: Exception) {
            entries = PersonalEntries.empty(maxEntries)
            snapshot = PersonalDictionary.EMPTY
        }
        return true
    }

    /**
     * The whole-file write sequence. Returns true only when every step succeeded. On any caught
     * failure the temp is removed and false is returned, leaving the previous file untouched. An
     * uncaught [Error] (a simulated process death) leaves the temp for the next open to discard.
     */
    private fun writeWhole(candidate: PersonalEntries): Boolean {
        val directory = directoryProvider.personalDirectory()
        return try {
            ensureDirectory(directory)
            cleanupTemps(directory)
            val bytes = candidate.serialize(subtypeId)
            val required = bytes.size.toLong() + FREE_SPACE_RESERVE_BYTES
            if (spaceProbe.usableBytes(directory) < required) return false
            val temporary = createExclusiveTemp(directory)
            try {
                outputOpener.open(temporary).use { output ->
                    output.write(bytes)
                    output.flush()
                    fileOps.syncFile(output.fd)
                }
                validator.validate(temporary, subtypeId)
                val destination = File(directory, TpersFormat.personalFileName(subtypeId))
                fileOps.atomicReplace(temporary, destination)
                fileOps.syncDirectory(directory)
                writeCount++
                true
            } catch (_: Exception) {
                if (temporary.exists()) runCatching { fileOps.delete(temporary) }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * The salt for the pending hashes: 16 random bytes in `salt.bin`, created on first use and
     * destroyed by [clearAll]. Returns null when it can neither be read nor created — in which case
     * nothing is counted at all, which is the fail-closed direction (no learning rather than
     * unsalted keys).
     */
    private fun saltOrCreate(): ByteArray? {
        salt?.let { return it }
        return try {
            val directory = directoryProvider.personalDirectory()
            ensureDirectory(directory)
            val file = File(directory, SALT_FILE_NAME)
            val existing = if (file.isFile && file.length() == SALT_SIZE.toLong()) {
                file.readBytes()
            } else {
                val fresh = ByteArray(SALT_SIZE)
                SecureRandom().nextBytes(fresh)
                writeBytesDurably(directory, file, fresh)
                fresh
            }
            salt = existing
            existing
        } catch (_: Exception) {
            null
        }
    }

    /** Reads the pending counters once, alongside the dictionary itself. Fail-closed to empty. */
    private fun readPending(directory: File) {
        pending = try {
            val file = File(directory, pendingFileName())
            if (file.isFile) PendingCounters.parse(file.readBytes()) else PendingCounters.EMPTY
        } catch (_: Exception) {
            PendingCounters.EMPTY
        }
    }

    private fun writePending(counters: PendingCounters): Boolean = try {
        val directory = directoryProvider.personalDirectory()
        ensureDirectory(directory)
        writeBytesDurably(directory, File(directory, pendingFileName()), counters.serialize())
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Writes one small fixed-size file through the same temp → fsync → atomic replace → directory
     * fsync sequence the dictionary uses. The pending file and the salt are not user text, but a
     * half-written one would be read as garbage, and fail-closed parsing would then silently drop a
     * user's progress.
     */
    private fun writeBytesDurably(directory: File, destination: File, bytes: ByteArray) {
        val temporary = createExclusiveTemp(directory)
        try {
            outputOpener.open(temporary).use { output ->
                output.write(bytes)
                output.flush()
                fileOps.syncFile(output.fd)
            }
            fileOps.atomicReplace(temporary, destination)
            fileOps.syncDirectory(directory)
        } catch (exception: Exception) {
            if (temporary.exists()) runCatching { fileOps.delete(temporary) }
            throw exception
        }
    }

    /**
     * B2. Moves an unreadable file into this subtype's single quarantine slot instead of deleting
     * it, and tells [quarantineNotice] that the list the user will see is empty for a reason.
     *
     * Validation fails for an interrupted write (a power cut mid-replace), a checksum that no longer
     * matches, or a format a later version changes — and what usually survives such a failure is
     * most of the words. Deleting destroyed the only data this keyboard keeps about its user, with
     * no copy and nothing said; a repair path added later can only read those words if they still
     * exist. Between losing the data and keeping one extra file, the file wins.
     *
     * ONE slot per language, replaced by the next corruption, so the copy cannot accumulate: the
     * ceiling on disk is one file, not one per failure. Its name is not the name the reader looks
     * for, so nothing ever validates, reads or publishes it, and it is not a temp either, so
     * [cleanupTemps] leaves it alone. "Erase all" removes it — see [clearAll].
     *
     * If the move itself cannot happen the unreadable file is removed after all: leaving it where
     * the reader looks would fail validation again on every single start, and the copy is worth
     * strictly less than a feature that works.
     */
    private fun quarantine(directory: File, file: File) {
        val moved = try {
            fileOps.atomicReplace(file, File(directory, quarantineFileName()))
            fileOps.syncDirectory(directory)
            true
        } catch (_: Exception) {
            false
        }
        if (!moved) runCatching { deleteFile(directory, file) }
        // Said in both cases: what the user is told is that the list is empty and that they did not
        // do it, which is equally true whether the copy was kept or could not be made.
        quarantineNotice?.onQuarantined()
    }

    private fun quarantineFileName(): String =
        TpersFormat.personalFileName(subtypeId) + QUARANTINE_SUFFIX

    private fun pendingFileName(): String = "pending-$subtypeId-s1-f1.bin"

    private fun deleteFile(): Boolean {
        val directory = directoryProvider.personalDirectory()
        val file = File(directory, TpersFormat.personalFileName(subtypeId))
        deleteFile(directory, file)
        return true
    }

    private fun deleteFile(directory: File, file: File) {
        if (!file.exists()) return
        if (!fileOps.delete(file) && file.exists()) throw IOException("cannot remove personal file")
        fileOps.syncDirectory(directory)
    }

    private fun ensureDirectory(directory: File) {
        if (directory.isDirectory) return
        if (directory.exists() || !directory.mkdirs()) throw IOException("cannot create personal directory")
        directory.parentFile?.let(fileOps::syncDirectory)
    }

    private fun cleanupTemps(directory: File) {
        val temporaries = directory.listFiles { file ->
            file.isFile && file.name.startsWith(TEMP_PREFIX) && file.name.endsWith(TEMP_SUFFIX)
        } ?: return
        if (temporaries.isEmpty()) return
        var removedAny = false
        for (temp in temporaries) {
            if (fileOps.delete(temp) || !temp.exists()) removedAny = true
        }
        if (removedAny) fileOps.syncDirectory(directory)
    }

    private fun createExclusiveTemp(directory: File): File {
        val timestamp = clock.nowMillis()
        for (counter in 0 until MAX_TEMP_ATTEMPTS) {
            val file = File(directory, "$TEMP_PREFIX$subtypeId.$timestamp.$counter$TEMP_SUFFIX")
            if (fileOps.createNewFile(file)) return file
        }
        throw IOException("cannot create exclusive personal temp")
    }

    companion object {
        private const val TEMP_PREFIX = ".personal-"
        private const val TEMP_SUFFIX = ".tmp"
        private const val MAX_TEMP_ATTEMPTS = 100
        private const val FREE_SPACE_RESERVE_BYTES = 64L * 1024L
        private const val SALT_FILE_NAME = "salt.bin"
        private const val SALT_SIZE = 16

        /**
         * Appended to the ordinary file name for the one quarantine slot (B2). Deliberately not a
         * `.tpers` name and not a temp name: nothing reads it, nothing cleans it up by accident.
         */
        private const val QUARANTINE_SUFFIX = ".quarantine"
    }
}
