package rkr.simplekeyboard.inputmethod.latin.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.BigramPreparationResult
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.PublishedBigramTableCatalog
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.StorageFailure
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * PROPOSALS.md, "E5c. Готовность вычислителя двухступенчатая": bigram-table preparation and
 * attachment must never be on the path that publishes the dictionary engine — `strip.reserve()`
 * and the already-typed prefix being looked up must both have already happened by the time
 * [SuggestionsController.onStartInput] returns, whether or not bigram preparation has completed
 * (or exists, or fails) by then. [PendingBigramPreparation] models this faithfully: unlike a
 * fake that resolves synchronously, it holds its callback until the test fires it explicitly —
 * exactly the "requested now, resolves later, off the UI thread" shape the real
 * `BackgroundBigramPreparer` has, and the only way to observe a real ordering guarantee instead
 * of an accidental one produced by fully-synchronous fakes.
 */
class SuggestionsControllerBigramAttachTest {

    private class FakeStrip : StripSurface {
        var reserveCount = 0
        var hideCount = 0

        override fun showSuggestions(first: String, second: String?, third: String?) {}
        override fun reserve() {
            reserveCount++
        }

        override fun hideSuggestions() {
            hideCount++
        }

        override fun setTapListener(listener: SuggestionTapListener) {}
    }

    private class FakeEditor : EditorSurface {
        var word: String = ""

        override fun cachedWordBeforeCursor(): String = word
        override fun commitSuggestion(expectedPrefix: String, suggestion: String): Boolean = true
        override fun hasKnownCursor(): Boolean = true
        override fun hasLetterAfterCursor(): Boolean = false
    }

    /** Records call ORDER across every tracked method, so tests can assert relative sequencing. */
    private class FakeEngine : EngineHandle {
        val events = mutableListOf<String>()
        var attachCatalog: PublishedBigramTableCatalog? = null
        var attachResult: Boolean = true

        override fun request(editorSessionId: Long, subtypeId: String, prefixUtf8: ByteArray): Any? {
            events += "request"
            return Any()
        }

        override fun requestNextWord(editorSessionId: Long, subtypeId: String, contextWordUtf8: ByteArray): Any? {
            events += "requestNextWord"
            return null
        }

        override fun attachBigramSource(catalog: PublishedBigramTableCatalog): Boolean {
            events += "attachBigramSource"
            attachCatalog = catalog
            return attachResult
        }

        override fun isCurrent(token: Any): Boolean = true
        override fun finishInput() {}
        override fun destroy(timeoutMs: Long): Boolean = true
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var shutdown = false
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }

    private class FakeBigramCatalog : PublishedBigramTableCatalog {
        override fun acquireLatestForActivation() = null
        override fun cleanupReleasedVersions() {}
    }

    /** Resolves only when [completeWith] is called — models a background result arriving later. */
    private class PendingBigramPreparation : BigramPreparation {
        var prepareCalls = 0
        private var pendingCallback: ((BigramPreparationResult) -> Unit)? = null
        private val catalog = FakeBigramCatalog()

        override fun prepare(onResult: (BigramPreparationResult) -> Unit) {
            prepareCalls++
            pendingCallback = onResult
        }

        override fun catalog(): PublishedBigramTableCatalog = catalog

        fun completeWith(result: BigramPreparationResult) {
            val callback = requireNotNull(pendingCallback) { "prepare() was never called" }
            pendingCallback = null
            callback(result)
        }
    }

    /** Resolves synchronously, inline — for tests that only care about the terminal state. */
    private class ImmediateBigramPreparation(
        private val result: BigramPreparationResult,
    ) : BigramPreparation {
        private val catalog = FakeBigramCatalog()

        override fun prepare(onResult: (BigramPreparationResult) -> Unit) = onResult(result)
        override fun catalog(): PublishedBigramTableCatalog = catalog
    }

    private fun controller(
        strip: FakeStrip,
        editor: FakeEditor,
        engine: FakeEngine,
        executor: ExecutorService,
        bigramPreparationFactory: (ExecutorService) -> BigramPreparation?,
    ) = SuggestionsController(
        strip,
        editor,
        UiPoster { it.run() },
        { _, _ -> engine },
        { executor },
        { _, _ -> null },
        true,
        { backgroundExecutor, _ -> bigramPreparationFactory(backgroundExecutor) },
    )

    @Test
    fun bigramPreparationStillPendingNeverBlocksReserveOrTheCachedPrefixLookup() {
        val strip = FakeStrip()
        val editor = FakeEditor().apply { word = "аб" }
        val engine = FakeEngine()
        val bigramTable = PendingBigramPreparation()
        val controller = controller(strip, editor, engine, DirectExecutorService()) { bigramTable }

        controller.onStartInput(eligible = true)

        // Publication of the dictionary engine — the band reserved, the already-typed prefix
        // looked up — has FULLY happened, even though bigram preparation was requested and has
        // not resolved yet. This is the property the contract actually names: not "which of the
        // two happens first", but "the second can never make the first wait".
        assertEquals(listOf("request"), engine.events)
        assertEquals(1, strip.reserveCount)
        assertEquals(1, bigramTable.prepareCalls)

        bigramTable.completeWith(BigramPreparationResult.Published(fakeTable(), alreadyPresent = false))

        assertEquals(listOf("request", "attachBigramSource"), engine.events)
        assertTrue(engine.attachCatalog === bigramTable.catalog())
    }

    @Test
    fun missingBigramPreparationNeverBlocksOrDelaysPublish() {
        val strip = FakeStrip()
        val editor = FakeEditor().apply { word = "аб" }
        val engine = FakeEngine()
        val controller = controller(strip, editor, engine, DirectExecutorService()) { null }

        controller.onStartInput(eligible = true)

        assertEquals(listOf("request"), engine.events)
        assertEquals(1, strip.reserveCount)
    }

    @Test
    fun unavailableBigramPreparationLeavesDictionarySuggestionsUnaffected() {
        val strip = FakeStrip()
        val editor = FakeEditor().apply { word = "аб" }
        val engine = FakeEngine()
        val bigramTable = ImmediateBigramPreparation(BigramPreparationResult.Unavailable(StorageFailure.INVALID_ASSET))
        val controller = controller(strip, editor, engine, DirectExecutorService()) { bigramTable }

        controller.onStartInput(eligible = true)

        assertEquals(listOf("request"), engine.events)
        assertEquals(1, strip.reserveCount)
        assertNull(engine.attachCatalog)
    }

    @Test
    fun bigramPreparationFactoryThrowingNeverPropagatesToPublish() {
        val strip = FakeStrip()
        val editor = FakeEditor().apply { word = "аб" }
        val engine = FakeEngine()
        val controller = controller(strip, editor, engine, DirectExecutorService()) {
            throw IllegalStateException("boom")
        }

        controller.onStartInput(eligible = true)

        assertEquals(listOf("request"), engine.events)
        assertEquals(1, strip.reserveCount)
    }

    private fun fakeTable() = rkr.simplekeyboard.inputmethod.latin.dictionary.storage.PublishedBigramTable(
        1, "tt", java.io.File("unused"), 1, 1, 1, 1, 2, 1, "0".repeat(64),
    )
}
