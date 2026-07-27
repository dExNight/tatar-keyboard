package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.DictionaryFileLease
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.PublishedDictionaryCatalog
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun interface DictionaryMapper {
    fun mapReadOnly(file: File, size: Long): ByteBuffer
}

class ExecutorServiceEngineExecutor private constructor(
    private val delegate: ExecutorService,
) : EngineExecutor {
    override fun execute(command: Runnable) = delegate.execute(command)

    override fun shutdown() = delegate.shutdown()

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
        delegate.awaitTermination(timeout, unit)

    companion object {
        fun singleThread(): EngineExecutor =
            ExecutorServiceEngineExecutor(Executors.newSingleThreadExecutor())
    }
}

class MappedDictionaryEngine private constructor(
    val identity: DictionaryIdentity,
    private val engine: LatestOnlyPrefixEngine,
) {
    fun request(
        editorSessionId: Long,
        subtypeId: String,
        normalizedPrefixUtf8: ByteArray,
    ): LookupToken? = engine.request(editorSessionId, subtypeId, normalizedPrefixUtf8)

    fun finishInput() = engine.finishInput()

    fun updateKeyNeighbors(table: KeyNeighborTable?) = engine.updateKeyNeighbors(table)

    fun isCurrent(token: LookupToken): Boolean = engine.isCurrent(token)

    fun destroy(timeout: Long, unit: TimeUnit): Boolean = engine.destroy(timeout, unit)

    val suppressedStaleResultCount: Long
        get() = engine.suppressedStaleResultCount

    private class Resources(
        private var lease: DictionaryFileLease?,
        private val catalog: PublishedDictionaryCatalog,
        var mappedBuffer: ByteBuffer?,
        var index: TdictPrefixIndex?,
    ) {
        fun release() {
            index = null
            mappedBuffer = null
            val heldLease = lease
            lease = null
            try {
                heldLease?.close()
            } catch (_: Throwable) {
                // The engine has already dropped all mapping references.
            } finally {
                try {
                    catalog.cleanupReleasedVersions()
                } catch (_: Throwable) {
                    // Cleanup is best-effort and cannot affect ordinary input.
                }
            }
        }
    }

    companion object {
        internal val FILE_MAPPER = DictionaryMapper { file, size ->
            FileInputStream(file).use { stream ->
                stream.channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
                }
            }
        }

        /**
         * Acquires and consumes a catalog lease. Call off the UI thread: catalog validation and
         * mmap both perform file I/O. On success the lease is owned exclusively by the returned
         * engine until destroy; on every failure it is closed here exactly once.
         */
        fun start(
            catalog: PublishedDictionaryCatalog,
            resultHandoff: ResultHandoff,
            executorFactory: () -> EngineExecutor =
                ExecutorServiceEngineExecutor::singleThread,
            mapper: DictionaryMapper = FILE_MAPPER,
        ): MappedDictionaryEngine? {
            val lease = try {
                catalog.acquireLatestForActivation()
            } catch (_: Throwable) {
                null
            } ?: return null
            return startOwnedLease(lease, catalog, resultHandoff, executorFactory, mapper)
        }

        private fun startOwnedLease(
            lease: DictionaryFileLease,
            catalog: PublishedDictionaryCatalog,
            resultHandoff: ResultHandoff,
            executorFactory: () -> EngineExecutor,
            mapper: DictionaryMapper,
        ): MappedDictionaryEngine? {
            val dictionary = lease.dictionary
            val identity = DictionaryIdentity(
                dictionary.generation,
                dictionary.schemaId,
                dictionary.formatVersion,
                dictionary.rawSha256,
            )
            var mapped: ByteBuffer? = null
            var createdExecutor: EngineExecutor? = null
            try {
                mapped = mapper.mapReadOnly(dictionary.file, dictionary.rawSize)
                val index = TdictPrefixIndex.open(
                    mapped,
                    identity,
                    dictionary.entryCount,
                    dictionary.rawSize,
                ) ?: throw IllegalArgumentException("validated dictionary layout mismatch")
                val executor = executorFactory()
                createdExecutor = executor
                val resources = Resources(lease, catalog, mapped, index)
                val engine = LatestOnlyPrefixEngine(
                    identity,
                    index,
                    executor,
                    resultHandoff,
                    resources::release,
                )
                return MappedDictionaryEngine(identity, engine)
            } catch (_: Throwable) {
                mapped = null
                try {
                    createdExecutor?.shutdown()
                } catch (_: Throwable) {
                    // Startup already failed; executor cleanup is best-effort.
                }
                try {
                    lease.close()
                } catch (_: Throwable) {
                    // Ownership was consumed; a failing release still must not escape startup.
                } finally {
                    try {
                        catalog.cleanupReleasedVersions()
                    } catch (_: Throwable) {
                        // Fail closed without logging dictionary paths or typed text.
                    }
                }
                return null
            }
        }
    }
}
