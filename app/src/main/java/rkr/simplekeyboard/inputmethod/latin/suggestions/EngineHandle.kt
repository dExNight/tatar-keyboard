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

package rkr.simplekeyboard.inputmethod.latin.suggestions

import android.content.Context
import rkr.simplekeyboard.inputmethod.latin.dictionary.engine.LookupToken
import rkr.simplekeyboard.inputmethod.latin.dictionary.engine.MappedDictionaryEngine
import rkr.simplekeyboard.inputmethod.latin.dictionary.engine.ResultHandoff
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.AndroidDictionaryStorageFactory
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.PublishedDictionaryCatalog
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Worker-thread result delivery from an [EngineHandle] to the controller.
 *
 * Invoked off the UI thread (on the engine's own worker thread). The receiver is responsible for
 * marshaling to the UI thread and for re-checking [EngineHandle.isCurrent] before applying, exactly
 * as the underlying engine's [ResultHandoff] contract requires. [token] is opaque; hand it straight
 * back to [EngineHandle.isCurrent].
 */
fun interface ResultCallback {
    fun onResult(token: Any, suggestions: List<String>)
}

/**
 * Thin, unit-testable abstraction over the dictionary engine. Keeps [SuggestionsController]
 * independent of the mmap/engine machinery so it can be driven with fakes in plain JVM tests.
 *
 * Tokens are opaque [Any] values produced by [request] and only ever interpreted by the same
 * handle via [isCurrent].
 */
interface EngineHandle {
    /** Enqueues a lookup. Returns an opaque token, or null if the request was rejected. */
    fun request(editorSessionId: Long, subtypeId: String, prefixUtf8: ByteArray): Any?

    /** True only if [token] identifies the newest still-active request on this handle. */
    fun isCurrent(token: Any): Boolean

    /** Idles the engine and invalidates any in-flight generation. */
    fun finishInput()

    /** Bounded teardown; returns true if the engine fully released within [timeoutMs]. */
    fun destroy(timeoutMs: Long): Boolean
}

/**
 * Real [EngineHandle] backed by [MappedDictionaryEngine].
 *
 * Construction performs file mapping I/O, so [start] MUST be called off the UI thread (the
 * controller submits it on its background executor). Results arrive on the engine's worker thread
 * and are forwarded verbatim to [callback], which is responsible for UI marshaling.
 */
class MappedEngineHandle private constructor(
    private val engine: MappedDictionaryEngine,
) : EngineHandle {

    override fun request(editorSessionId: Long, subtypeId: String, prefixUtf8: ByteArray): Any? =
        engine.request(editorSessionId, subtypeId, prefixUtf8)

    override fun isCurrent(token: Any): Boolean =
        token is LookupToken && engine.isCurrent(token)

    override fun finishInput() = engine.finishInput()

    override fun destroy(timeoutMs: Long): Boolean =
        engine.destroy(timeoutMs, TimeUnit.MILLISECONDS)

    companion object {
        /**
         * Acquires a catalog lease and maps the newest dictionary. Performs file I/O; call off the
         * UI thread. Returns null if no dictionary is safe to activate.
         */
        fun start(
            catalog: PublishedDictionaryCatalog,
            callback: ResultCallback,
        ): MappedEngineHandle? {
            val handoff = ResultHandoff { result ->
                callback.onResult(result.token, result.suggestions)
            }
            val engine = MappedDictionaryEngine.start(catalog, handoff) ?: return null
            return MappedEngineHandle(engine)
        }

        /**
         * Production factory used by LatinIME. Builds a catalog over the already-published asset
         * (durably written to device-protected storage by SuggestionsController.onCreate's
         * prepare step) and activates it. Performs catalog validation + file mapping I/O, so this
         * MUST be called off the UI thread; the controller invokes it on its background executor
         * only after the dictionary is ready. Returns null when no dictionary is safe to activate.
         *
         * The storage factory's executor only backs background preparation, which this path never
         * triggers, so it is shut down immediately; the returned engine owns its own lookup
         * executor and the catalog lease.
         */
        @JvmStatic
        fun create(context: Context, callback: ResultCallback): MappedEngineHandle? {
            val storageExecutor = Executors.newSingleThreadExecutor()
            return try {
                val catalog = AndroidDictionaryStorageFactory.create(context, storageExecutor)
                start(catalog, callback)
            } catch (_: Throwable) {
                null
            } finally {
                storageExecutor.shutdownNow()
            }
        }
    }
}
