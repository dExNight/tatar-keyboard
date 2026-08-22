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
import java.io.FileOutputStream

/**
 * The seam that owns ONLY the personal-dictionary directory.
 *
 * It is deliberately its own seam, not the dictionary asset's device-protected directory-provider
 * seam (whose name would become a lie for a credential-protected file) and not the emoji recents
 * provider (same root, but a different directory, format and owner — sharing one provider would put
 * "erase the personal dictionary" and "clear the recent emoji" next to each other in code with no
 * reason). Production resolves it under the base (credential-protected) `noBackupFilesDir`.
 */
fun interface PersonalDirectoryProvider {
    fun personalDirectory(): File
}

/**
 * Opens the exclusive temp for writing.
 *
 * It is a seam so the WRITE and FLUSH steps of the whole-file sequence are independently
 * fault-injectable in a plain JVM test. Production returns a plain [FileOutputStream]; the store
 * owns the fsync (via [rkr.simplekeyboard.inputmethod.latin.dictionary.storage.DurableFileOps.syncFile]),
 * the atomic replace and the directory fsync around it.
 */
fun interface PersonalOutputOpener {
    fun open(temp: File): FileOutputStream
}

/**
 * The outcome of ONE mutation the user asked for by hand, delivered AFTER the event has actually
 * run on the store's worker — never before it.
 *
 * It exists because the subsystem had no channel at all for "this did not work": logging is
 * forbidden here by the privacy policy, and every mutation is an event on a background worker whose
 * result nobody waited for, so a screen could only ever report the queueing, not the writing. This
 * carries one boolean and nothing else — the word and the path stay inside the store, exactly as
 * that same policy requires.
 *
 * Called on the store's worker thread. A caller that touches UI must marshal it itself.
 */
internal fun interface PersonalMutationOutcome {
    fun onFinished(succeeded: Boolean)
}
