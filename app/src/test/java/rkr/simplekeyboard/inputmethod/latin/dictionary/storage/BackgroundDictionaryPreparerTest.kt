package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class BackgroundDictionaryPreparerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun prepareReturnsWithoutAnyIoUntilWorkerRuns() {
        val artifact = DictionaryTestFixtures.artifact()
        val assets = CountingAssetProvider(mutableMapOf(1 to artifact.compressed))
        var probes = 0
        val executor = QueuedExecutor()
        val store = store(artifact, assets, SpaceProbe { probes++; Long.MAX_VALUE })
        var result: PreparationResult? = null

        BackgroundDictionaryPreparer(executor, store, artifact.spec).prepare { result = it }

        assertNull(result)
        assertEquals(0, assets.opens)
        assertEquals(0, probes)
        assertEquals(1, executor.tasks.size)

        executor.tasks.removeFirst().run()
        assertTrue(result is PreparationResult.Published)
        assertEquals(1, assets.opens)
        assertEquals(1, probes)
    }

    @Test
    fun workerFailureReportsUnavailableWithoutThrowingIntoCaller() {
        val artifact = DictionaryTestFixtures.artifact()
        val assets = CountingAssetProvider(mutableMapOf(1 to byteArrayOf(1, 2, 3)))
        val executor = QueuedExecutor()
        val store = store(artifact, assets, SpaceProbe { Long.MAX_VALUE })
        var result: PreparationResult? = null

        BackgroundDictionaryPreparer(executor, store, artifact.spec).prepare { result = it }
        executor.tasks.removeFirst().run()

        assertEquals(PreparationResult.Unavailable(StorageFailure.INVALID_ASSET), result)
    }

    @Test
    fun rejectedExecutorFailsWithoutStorageIo() {
        val artifact = DictionaryTestFixtures.artifact()
        val assets = CountingAssetProvider(mutableMapOf(1 to artifact.compressed))
        val store = store(artifact, assets, SpaceProbe { Long.MAX_VALUE })
        var result: PreparationResult? = null

        BackgroundDictionaryPreparer(
            Executor { throw RejectedExecutionException() },
            store,
            artifact.spec,
        ).prepare { result = it }

        assertEquals(PreparationResult.Unavailable(StorageFailure.EXECUTOR_REJECTED), result)
        assertEquals(0, assets.opens)
    }

    @Test
    fun runtimeExecutorFailureFailsWithoutStorageIo() {
        val artifact = DictionaryTestFixtures.artifact()
        val assets = CountingAssetProvider(mutableMapOf(1 to artifact.compressed))
        val store = store(artifact, assets, SpaceProbe { Long.MAX_VALUE })
        var result: PreparationResult? = null

        BackgroundDictionaryPreparer(
            Executor { throw IllegalStateException("executor is shutting down") },
            store,
            artifact.spec,
        ).prepare { result = it }

        assertEquals(PreparationResult.Unavailable(StorageFailure.EXECUTOR_REJECTED), result)
        assertEquals(0, assets.opens)
    }

    @Test
    fun inlineExecutorDoesNotMisreportOrRepeatCallbackFailureAsRejection() {
        val artifact = DictionaryTestFixtures.artifact()
        val assets = CountingAssetProvider(mutableMapOf(1 to artifact.compressed))
        val store = store(artifact, assets, SpaceProbe { Long.MAX_VALUE })
        var callbackCalls = 0

        try {
            BackgroundDictionaryPreparer(Executor(Runnable::run), store, artifact.spec).prepare {
                callbackCalls++
                throw IllegalStateException("consumer failure")
            }
            fail("expected consumer failure")
        } catch (expected: IllegalStateException) {
            assertEquals("consumer failure", expected.message)
        }

        assertEquals(1, callbackCalls)
        assertEquals(1, assets.opens)
    }

    private fun store(
        artifact: TestArtifact,
        assets: AssetInputProvider,
        spaceProbe: SpaceProbe,
    ) = AtomicDictionaryStore(
        directoryProvider = DeviceProtectedDirectoryProvider {
            File(temporaryFolder.root, "dictionary-${System.nanoTime()}")
        },
        assetInputProvider = assets,
        clock = StorageClock { 1234 },
        spaceProbe = spaceProbe,
        fileOps = TestDurableFileOps(),
        supportedArtifacts = listOf(artifact.spec),
    )

    private class QueuedExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            tasks.add(command)
        }
    }
}
