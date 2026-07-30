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
) {
    private val alphabet: Set<Int>? = PersonalSubtypes.alphabetFor(subtypeId)

    // Worker-confined state (touched only on [executor]).
    private var entries: PersonalEntries = PersonalEntries.empty(maxEntries)
    private var loaded = false
    private var pendingCounterFlush = false

    /** Count of physical `.tpers` writes performed; a test counter (never grows on in-memory notes). */
    @Volatile
    var writeCount: Int = 0
        private set

    /** The published immutable snapshot; read by the engine's worker thread. */
    @Volatile
    var snapshot: PersonalDictionary = PersonalDictionary.EMPTY
        private set

    /** Manually adds one word (E4b's "Add word…" path); a no-op if the word is not eligible. */
    fun addManually(word: String) = onWorker {
        val normalized = eligibleNormalizedForm(word) ?: return@onWorker
        commitWrite(entries.upsert(word, normalized))
    }

    /** Removes one word and rewrites (or deletes, when it was the last) the file. */
    fun forget(word: String) = onWorker {
        if (!open()) return@onWorker
        alphabet ?: return@onWorker
        val normalized = PersonalWordFilter.normalize(word)
        val candidate = entries.remove(normalized)
        if (candidate === entries) return@onWorker
        if (candidate.isEmpty) {
            if (deleteFile()) {
                entries = candidate
                snapshot = PersonalDictionary.EMPTY
            }
        } else {
            commitWrite(candidate)
        }
    }

    /** Erases this subtype's personal dictionary: empties memory and deletes the file. */
    fun clearAll() = onWorker {
        entries = PersonalEntries.empty(maxEntries)
        pendingCounterFlush = false
        snapshot = PersonalDictionary.EMPTY
        loaded = true
        if (!unlockGate()) return@onWorker
        try {
            deleteFile()
        } catch (_: Exception) {
            // Fail-closed: the feature is already empty in memory.
        }
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

    /** Flushes in-memory counter/serial changes to disk once, only when something changed. */
    fun flush() = onWorker {
        if (!open()) return@onWorker
        if (!pendingCounterFlush) return@onWorker
        if (writeWhole(entries)) pendingCounterFlush = false
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

    private fun commitWrite(candidate: PersonalEntries) {
        if (writeWhole(candidate)) {
            entries = candidate
            snapshot = candidate.toSnapshot(subtypeId)
        }
    }

    /**
     * Opens the directory once: honours the unlock gate (before the first unlock the path is
     * physically inaccessible, so the feature stays empty and untouched), removes stale temps, reads
     * the file into the model, and DELETES a corrupt file (quarantine — no copy is kept), publishing
     * an empty snapshot in the same step. Returns true once the store is loaded.
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
                runCatching { deleteFile(directory, file) }
                entries = PersonalEntries.empty(maxEntries)
                snapshot = PersonalDictionary.EMPTY
                return true
            }
            entries = PersonalEntries.fromValidated(validated, maxEntries)
            snapshot = entries.toSnapshot(subtypeId)
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
    }
}
