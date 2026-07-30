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

import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalCandidateSource
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalDictionary
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.SnapshotPersonalCandidateSource

/** Reads the live value of the personal-dictionary setting. A seam so tests need no preferences. */
fun interface PersonalDictionaryGate {
    fun isOn(): Boolean
}

/**
 * The ONE process-wide owner of the personal dictionaries — one store per subtype, one background
 * executor for all of them.
 *
 * There is exactly one process to own: `SettingsHostActivity` is declared without
 * `android:process`, so the settings screen and the IME share it. That is what lets the screen add,
 * remove and erase words through the same serialized owner the engine reads from, with no IPC and
 * no second writer.
 *
 * Reading is gated live rather than by restarting the engine: [PersonalDictionaryGate] is consulted
 * on every lookup, so turning the setting off stops personal candidates on the very next keystroke
 * while the engine, its lease and its mapping stay exactly as they were.
 */
object PersonalDictionaries {

    private val lock = Any()
    private val stores = HashMap<String, PersonalDictionaryStore>()
    private var sharedExecutor: ExecutorService? = null

    /**
     * Notified after words are erased ("Erase all" / "Forget"), so the IME can unbind whatever is
     * still displayed. Erasure must not merely change the NEXT lookup: without this the user who
     * just confirmed the dialog would keep seeing the erased word in the band and could insert it
     * with a tap.
     */
    @Volatile
    private var erasureListener: Runnable? = null

    /** The store for [subtypeId], created on first use. Safe to call from any thread. */
    internal fun storeFor(context: Context, subtypeId: String): PersonalDictionaryStore =
        synchronized(lock) {
            stores.getOrPut(subtypeId) {
                AndroidPersonalDictionaryStorage.create(context, subtypeId, executorLocked())
            }
        }

    /**
     * The engine's read side for [subtypeId]. Returns [PersonalCandidateSource.EMPTY] semantics
     * whenever the setting is off, so a disabled personal dictionary costs a lookup nothing beyond
     * one boolean read.
     *
     * Called from the controller's background executor at engine start (the store's first open
     * reads a file), never from the UI thread.
     */
    @JvmStatic
    fun sourceFor(
        context: Context,
        subtypeId: String,
        gate: PersonalDictionaryGate,
    ): PersonalCandidateSource {
        val store = storeFor(context, subtypeId)
        if (gate.isOn()) store.prime()
        // The source itself is built in the `personal` package, which owns the read model: this
        // package hands it nothing but a supplier of the published snapshot. That is also what keeps
        // the frozen privacy rule of the store package true — no method name here names typed text.
        return SnapshotPersonalCandidateSource {
            if (gate.isOn()) store.snapshot else PersonalDictionary.EMPTY
        }
    }

    /** The current snapshot of [subtypeId], for the "Personal dictionary" screen. */
    internal fun snapshotFor(context: Context, subtypeId: String): PersonalDictionary =
        storeFor(context, subtypeId).also { it.prime() }.snapshot

    @JvmStatic
    fun setErasureListener(listener: Runnable?) {
        erasureListener = listener
    }

    /** Called by the screen once an erasure event has been queued on the store's worker. */
    internal fun notifyErased() {
        erasureListener?.run()
    }

    private fun executorLocked(): ExecutorService =
        sharedExecutor ?: Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "personal-dictionary").apply { isDaemon = true }
        }.also { sharedExecutor = it }

    /** Test hook: drops every cached store so an isolated test starts from nothing. */
    internal fun resetForTest() {
        synchronized(lock) {
            stores.clear()
            sharedExecutor?.shutdown()
            sharedExecutor = null
        }
        erasureListener = null
    }
}
