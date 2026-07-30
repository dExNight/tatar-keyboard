package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import androidx.annotation.Keep
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.util.Locale

data class DictionaryArtifactSpec(
    val generation: Int,
    val assetPath: String,
    val expectedCompressedSize: Long,
    val expectedCompressedSha256: String,
    val expectedRawSize: Long,
    val expectedRawSha256: String,
    val expectedEntryCount: Long,
    val schemaId: Int = TdictFormat.SCHEMA_ID,
    val formatVersion: Int = TdictFormat.FORMAT_VERSION,
    val maxCompressedSize: Long = TdictFormat.MAX_COMPRESSED_SIZE,
    val maxRawSize: Long = TdictFormat.MAX_RAW_SIZE,
) {
    init {
        require(generation > 0)
        require(assetPath.isNotBlank())
        require(expectedCompressedSize in 1..maxCompressedSize)
        require(expectedRawSize in TdictFormat.HEADER_SIZE.toLong()..maxRawSize)
        require(expectedEntryCount > 0)
        require(expectedCompressedSha256.isSha256())
        require(expectedRawSha256.isSha256())
    }

    val finalFileName: String
        get() = String.format(
            Locale.ROOT,
            "tatar_top100k-v%06d-s%d-f%d-%s.tdict",
            generation,
            schemaId,
            formatVersion,
            expectedRawSha256.lowercase(),
        )

    companion object {
        @JvmField
        val TATAR_TOP100K_V1 = DictionaryArtifactSpec(
            generation = 1,
            assetPath = "dictionaries/tatar_top100k_v1.tdict.zlib",
            expectedCompressedSize = 600_606,
            expectedCompressedSha256 =
                "2d98ed359aa11261a5042a13c5ca9459c6e365c6ab4bf0563d0e3604a7485cae",
            expectedRawSize = 2_542_036,
            expectedRawSha256 =
                "798d3257700c092cdf17cbe148eb0383b82eb6a2230132af417c6a1b8548f558",
            expectedEntryCount = 100_000,
        )
    }
}

private fun String.isSha256(): Boolean =
    length == 64 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

fun interface AssetInputProvider {
    fun open(spec: DictionaryArtifactSpec): InputStream
}

fun interface StorageClock {
    fun nowMillis(): Long
}

fun interface SpaceProbe {
    fun usableBytes(directory: File): Long
}

fun interface DeviceProtectedDirectoryProvider {
    fun dictionaryDirectory(): File
}

interface DurableFileOps {
    fun createNewFile(file: File): Boolean
    fun syncFile(fileDescriptor: FileDescriptor)
    fun atomicRename(source: File, destination: File)
    fun syncDirectory(directory: File)
    fun delete(file: File): Boolean

    /**
     * Atomically replaces [destination] with [source], REPLACING an existing destination.
     *
     * This is deliberately distinct from [atomicRename], which throws when the destination already
     * exists: D1b's staged-publication retention depends on that throwing behaviour, so its
     * semantics must not change. The personal store's whole-file write (E4a-2) needs the replacing
     * variant instead, because it rewrites the same file across a session.
     *
     * The production override in `AndroidDurableFileOps` uses POSIX `rename(2)`, which is atomic and
     * replaces in place. This default is only a JVM fallback for test doubles that do not override
     * it; it is never reached by the dictionary-asset store.
     */
    fun atomicReplace(source: File, destination: File) {
        if (!source.renameTo(destination)) {
            if (!(destination.delete() && source.renameTo(destination))) {
                throw IOException("atomic replace failed")
            }
        }
    }
}

data class PublishedDictionary(
    val generation: Int,
    val file: File,
    val rawSize: Long,
    val entryCount: Long,
    val schemaId: Int,
    val formatVersion: Int,
    val rawSha256: String,
)

enum class StorageFailure {
    INVALID_ASSET,
    NO_SPACE,
    IO,
    RETENTION_BLOCKED,
    EXECUTOR_REJECTED,
}

sealed class PreparationResult {
    data class Published(
        val dictionary: PublishedDictionary,
        val alreadyPresent: Boolean,
    ) : PreparationResult()

    data class Unavailable(val reason: StorageFailure) : PreparationResult()
}

/**
 * Catalog consumed by D1d. acquireLatestForActivation performs validation I/O and must run off
 * the UI thread. D1d may call it only after its executor is stopped and its reader count is zero.
 * The returned lease must remain open for the complete mapping/executor lifetime. There is no
 * live hot-swap operation: close an old lease only after that version has no readers.
 */
@Keep
interface PublishedDictionaryCatalog {
    /**
     * Returns the newest validated dictionary that is safe to activate.
     *
     * A lease represents the complete mapping/executor/reader lifetime. If a different
     * dictionary version is still leased by any store instance for this directory, this method
     * returns null. Callers must stop the old executor, wait for its readers, and close its lease
     * before retrying at the next safe lifecycle boundary.
     */
    fun acquireLatestForActivation(): DictionaryFileLease?
    fun cleanupReleasedVersions()
}

/**
 * One lifecycle surface for background preparation and safe dictionary activation.
 *
 * AndroidDictionaryStorageFactory may return multiple controller objects, for example after an
 * IME service recreation. Their stores still coordinate through process-wide state keyed by the
 * device-protected directory.
 */
@Keep
class DictionaryStorageController internal constructor(
    private val preparer: BackgroundDictionaryPreparer,
    private val catalog: PublishedDictionaryCatalog,
) : PublishedDictionaryCatalog {
    fun prepare(callback: (PreparationResult) -> Unit) = preparer.prepare(callback)

    override fun acquireLatestForActivation(): DictionaryFileLease? =
        catalog.acquireLatestForActivation()

    override fun cleanupReleasedVersions() = catalog.cleanupReleasedVersions()
}

@Keep
class DictionaryFileLease internal constructor(
    val dictionary: PublishedDictionary,
    private val release: () -> Unit,
) : Closeable {
    private var closed = false

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            release()
        }
    }
}

internal object TdictFormat {
    const val MAGIC = "TATDICT\u0000"
    const val SCHEMA_ID = 1
    const val FORMAT_VERSION = 1
    const val HEADER_SIZE = 72
    const val CHECKSUM_OFFSET = 40
    const val CHECKSUM_SIZE = 32
    const val CHECKSUM_ALGORITHM_SHA256 = 1
    const val MAX_COMPRESSED_SIZE = 700_000L
    const val MAX_RAW_SIZE = 2_936_012L
    const val MAX_U32 = 0xffff_ffffL
}
