package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import androidx.annotation.Keep
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalSubtypes
import java.io.Closeable
import java.util.Locale

/**
 * E5b: the shipped bigram-table asset, schema 2 (`TATBIGR\0`) — a SEPARATE artifact kind from
 * [DictionaryArtifactSpec]'s schema 1, per PROPOSALS.md ("E5b. Отдельный файл и отдельная
 * схема"). [fileLanguageTag] parameterized storage "with language from the start" (contract
 * wording) back when only the Tatar table shipped; the second language duly added a second spec
 * rather than a second class.
 *
 * [fileLanguageTag] and [subtypeId] are NOT the same string and must not be merged. The first is
 * the short tag baked into the on-disk file name and FROZEN at `tt` for the Tatar table, which
 * devices inflated under that name in 1.6.0; the second is the IME subtype the table serves, and
 * for Tatar that identifier is `tt_RU`. Only [subtypeId] takes part in choosing a table, and
 * [DictionaryArtifactSpec] requires it to equal the language's own.
 *
 * [family] and [storageDirectoryName] complete that parameterization and carry exactly the
 * meaning they carry on [DictionaryArtifactSpec]: literals of the spec, never derived from
 * [fileLanguageTag], because the Tatar table shipped in 1.6.0 as `tatar_bigrams-tt-…` inside
 * `<device-protected>/bigrams` and a device updating to a build with a second language must find
 * that file where it left it. Each family owns its OWN directory for the same reason the
 * dictionaries do: `ProcessBigramStorageOwner` keys its lease bookkeeping by the canonical
 * directory path, so two families in one directory would share one lease counter and the second
 * language could never be activated while the first held a lease.
 *
 * Which subtype gets which table is NOT answered here. It is answered once, for both artifact
 * kinds together, by [DictionaryArtifactSpec.forSubtype] — see the note on
 * [DictionaryArtifactSpec.bigrams].
 */
data class BigramArtifactSpec(
    val family: String,
    val generation: Int,
    val fileLanguageTag: String,
    val subtypeId: String,
    val storageDirectoryName: String,
    val assetPath: String,
    val expectedCompressedSize: Long,
    val expectedCompressedSha256: String,
    val expectedRawSize: Long,
    val expectedRawSha256: String,
    val expectedHeadCount: Long,
    val schemaId: Int = TatBigrFormat.SCHEMA_ID,
    val formatVersion: Int = TatBigrFormat.FORMAT_VERSION,
    val maxCompressedSize: Long = TatBigrFormat.MAX_COMPRESSED_SIZE,
    val maxRawSize: Long = TatBigrFormat.MAX_RAW_SIZE,
) {
    init {
        require(generation > 0)
        require(FAMILY_PATTERN.matches(family))
        require(FILE_LANGUAGE_TAG_PATTERN.matches(fileLanguageTag))
        require(subtypeId.isNotBlank())
        require(FAMILY_PATTERN.matches(storageDirectoryName.replace('-', '_')))
        require(assetPath.isNotBlank())
        require(expectedCompressedSize in 1..maxCompressedSize)
        require(expectedRawSize in TatBigrFormat.HEADER_SIZE.toLong()..maxRawSize)
        require(expectedHeadCount > 0)
        require(expectedCompressedSha256.isBigramSha256())
        require(expectedRawSha256.isBigramSha256())
    }

    val finalFileName: String
        get() = String.format(
            Locale.ROOT,
            "%s-%s-v%06d-s%d-f%d-%s.tatbigr",
            family,
            fileLanguageTag,
            generation,
            schemaId,
            formatVersion,
            expectedRawSha256.lowercase(),
        )

    /** The temp-file prefix of this family; never shared with another family's directory. */
    val temporaryFilePrefix: String
        get() = ".$family-"

    /** Matches exactly the final files this family owns, and nothing else. */
    val finalFilePattern: Regex
        get() = Regex(
            "${Regex.escape(family)}-[a-z]{2,3}-v[0-9]{6}-s[0-9]+-f[0-9]+-[0-9a-f]{64}\\.tatbigr",
        )

    companion object {
        private val FAMILY_PATTERN = Regex("[a-z][a-z0-9_]*")

        /** Exactly what [finalFilePattern] accepts in the file name's language position. */
        private val FILE_LANGUAGE_TAG_PATTERN = Regex("[a-z]{2,3}")

        /**
         * The tt table packed 2026-08-17 by `scripts/bigram_asset_pack.py pack` from the E5a-
         * chosen configuration (H=10 000, K=6 — docs/DICTIONARY-E5B.md records the choice and
         * why). 9 996 heads, not 10 000: four top-10 000-by-frequency words never occur as the
         * head of an in-vocabulary pair in mixed+web and are dropped rather than stored with an
         * empty range (docs/DICTIONARY-E5B.md, "Dropped heads").
         */
        @JvmField
        val TATAR_BIGRAMS_V1 = BigramArtifactSpec(
            family = "tatar_bigrams",
            generation = 1,
            fileLanguageTag = "tt",
            subtypeId = PersonalSubtypes.TATAR_RU,
            storageDirectoryName = "bigrams",
            assetPath = "bigrams/tatar_bigrams_v1.tatbigr.zlib",
            expectedCompressedSize = 226_428,
            expectedCompressedSha256 =
                "89eb4aa82be45a57ea94daa0379ca3d8a07f1c630e5c532960832787b1e1ab8d",
            expectedRawSize = 644_148,
            expectedRawSha256 =
                "fb686476f6252f61f9d26632ccbd228f13aa1bffca7fe9bfff5f24baf9e0b05b",
            expectedHeadCount = 9_996,
        )

        /**
         * The ru table packed 2026-08-21 by `scripts/bigram_asset_pack.py pack --language rus`
         * from the same three Leipzig corpora the Russian dictionary is built from, at the same
         * H = 10 000 / K = 6 the Tatar table uses — `docs/RUSSIAN-BIGRAMS.md` records the matrix
         * that chose it and every number below.
         *
         * 10 000 heads, none dropped: unlike Tatar, every one of the top-10 000 Russian forms
         * occurs as the head of an in-vocabulary pair in the training corpora.
         *
         * Its own family and its own directory, so the Tatar table already inflated on a device
         * updating from 1.7.0 is neither renamed, re-inflated, nor sharing this language's lease
         * counter.
         */
        @JvmField
        val RUSSIAN_BIGRAMS_V1 = BigramArtifactSpec(
            family = "russian_bigrams",
            generation = 1,
            fileLanguageTag = "ru",
            subtypeId = PersonalSubtypes.RUSSIAN,
            storageDirectoryName = "bigrams-ru",
            assetPath = "bigrams/russian_bigrams_v1.tatbigr.zlib",
            expectedCompressedSize = 207_608,
            expectedCompressedSha256 =
                "6ccb3ecc75d5c4d27d6f2b6b42a62628c3462ebd00a8e5fecffb9cdc87acb1ef",
            expectedRawSize = 635_838,
            expectedRawSha256 =
                "48f8ec6ab7bd262f847351a43633bde405d85aac82f8030b8b8f1de0a0491954",
            expectedHeadCount = 10_000,
        )
    }
}

private fun String.isBigramSha256(): Boolean =
    length == 64 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

/**
 * What a validated bigram table looks like once published — deliberately NOT [PublishedDictionary]:
 * a flat word list and a head/success table are different shapes, and E5c's own contract
 * ("владелец состояния обязан знать вид результата") is exactly why this project does not fold
 * structurally different artifacts into one type just because both are files on disk.
 */
data class PublishedBigramTable(
    val generation: Int,
    val fileLanguageTag: String,
    val file: java.io.File,
    val rawSize: Long,
    val headCount: Long,
    val pairCount: Long,
    val successVocabularyCount: Long,
    val schemaId: Int,
    val formatVersion: Int,
    val rawSha256: String,
)

sealed class BigramPreparationResult {
    data class Published(
        val table: PublishedBigramTable,
        val alreadyPresent: Boolean,
    ) : BigramPreparationResult()

    data class Unavailable(val reason: StorageFailure) : BigramPreparationResult()
}

@Keep
interface PublishedBigramTableCatalog {
    fun acquireLatestForActivation(): BigramTableLease?
    fun cleanupReleasedVersions()
}

@Keep
class BigramTableLease internal constructor(
    val table: PublishedBigramTable,
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

/**
 * One lifecycle surface for background bigram-table preparation and safe activation — the exact
 * pair [DictionaryStorageController]/[BackgroundDictionaryPreparer] form for the main dictionary,
 * mirrored here rather than generalized: the two artifact kinds already deliberately don't share
 * a spec, validator, or store type (`docs/DICTIONARY-E5B.md`), and this is the last layer of that
 * same shape.
 */
@Keep
class BigramStorageController internal constructor(
    private val preparer: BackgroundBigramPreparer,
    private val catalog: PublishedBigramTableCatalog,
) : PublishedBigramTableCatalog {
    fun prepare(callback: (BigramPreparationResult) -> Unit) = preparer.prepare(callback)

    override fun acquireLatestForActivation(): BigramTableLease? =
        catalog.acquireLatestForActivation()

    override fun cleanupReleasedVersions() = catalog.cleanupReleasedVersions()
}

@Keep
class BackgroundBigramPreparer(
    private val executor: java.util.concurrent.Executor,
    private val store: AtomicBigramStore,
    private val artifact: BigramArtifactSpec,
) {
    fun prepare(callback: (BigramPreparationResult) -> Unit) {
        val taskStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            executor.execute {
                taskStarted.set(true)
                callback(store.ensurePublished(artifact))
            }
        } catch (error: RuntimeException) {
            if (taskStarted.get()) throw error
            callback(BigramPreparationResult.Unavailable(StorageFailure.EXECUTOR_REJECTED))
        }
    }
}

internal object TatBigrFormat {
    const val MAGIC = "TATBIGR\u0000"
    const val SCHEMA_ID = 2
    const val FORMAT_VERSION = 1
    const val HEADER_SIZE = 96
    const val CHECKSUM_OFFSET = 64
    const val CHECKSUM_SIZE = 32
    const val CHECKSUM_ALGORITHM_SHA256 = 1
    const val MAX_COMPRESSED_SIZE = 250_000L
    const val MAX_RAW_SIZE = 1_048_576L
    const val MAX_U32 = 0xffff_ffffL
}
