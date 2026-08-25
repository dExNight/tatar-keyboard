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
         * The tt table repacked 2026-08-25 by `scripts/bigram_asset_pack.py pack` at
         * **H = 10 132**, K = 4, with `--extra-heads scripts/bigram_extra_heads_tat.txt`.
         * K = 4 is unchanged since 2026-08-23 (docs/BIGRAM-ADJACENCY.md); the two numbers that
         * moved are the cutoff and the named-head list, and each answers a different question
         * (docs/IMPERATIVE-HEADS.md records both, with the measurement):
         *
         * * **the list** makes thirteen frequent Tatar imperatives heads regardless of rank.
         *   "кил" (come) sat at unigram rank 10 338 and predicted nothing, while "бир" (give) —
         *   the same grammatical form, rank 5 955 — predicted "бир әле". Naming thirteen words
         *   costs **223 compressed bytes**; raising the cutoff to reach "кайт" at 14 706 would
         *   have cost roughly forty times that for words nobody asked for;
         * * **the cutoff** moved from 10 000 only to keep faith with what already worked. The
         *   dictionary was rebuilt after this table was last packed (`bfb78e93`, `01f85d24`,
         *   `b3673752`), and repacking at 10 000 would have dropped 78 words that predict today.
         *   All 78 sit at ranks 10 001…10 131, so 10 132 is the smallest cutoff that loses none —
         *   derived, not chosen. It costs 2 327 compressed bytes and reaches 132 more heads.
         *
         * Verified head-by-head against the 1.9.3 artifact before the swap: **0 of the 9 996 old
         * heads is missing, and 0 of them changes the three successors the strip displays.** The
         * whole delta is additive: 10 142 heads = 9 996 + 132 (cutoff) + 13 (list) + 1 (a word
         * the rebuilt dictionary lifted into the top 10 000 on its own).
         *
         * 10 142 heads, not 10 145: three words selected by frequency never occur as the head of
         * an in-vocabulary pair in mixed+web and are dropped rather than stored with an empty
         * range (docs/DICTIONARY-E5B.md, "Dropped heads") — the same `-гәнчә` converbs as before,
         * minus `толмацкий`, which the rebuilt dictionary no longer ranks that high. Every one of
         * the thirteen named words does get pairs; none was dropped.
         */
        @JvmField
        val TATAR_BIGRAMS_V1 = BigramArtifactSpec(
            family = "tatar_bigrams",
            generation = 1,
            fileLanguageTag = "tt",
            subtypeId = PersonalSubtypes.TATAR_RU,
            storageDirectoryName = "bigrams",
            assetPath = "bigrams/tatar_bigrams_v1.tatbigr.zlib",
            expectedCompressedSize = 175_843,
            expectedCompressedSha256 =
                "f91c059937f3c9e8636b274af1f7f36bf5be887b59bc569aba26dfc1f2181893",
            expectedRawSize = 518_728,
            expectedRawSha256 =
                "d2345b4831291a678c76c5a09b5b0539f0a1ffd4055762d8d69e5fed852822a6",
            expectedHeadCount = 10_142,
        )

        /**
         * The ru table repacked 2026-08-23 by `scripts/bigram_asset_pack.py pack --language rus`
         * from the same three Leipzig corpora the Russian dictionary is built from, at
         * H = 10 000 / **K = 4** — `docs/RUSSIAN-BIGRAMS.md` records the
         * matrix that chose H and the original K = 6; docs/BIGRAM-ADJACENCY.md records the drop to
         * K = 4 and the head-by-head proof that none of the 10 000 heads changes its displayed
         * three successors. 47 098 fewer bytes at byte-identical behaviour.
         *
         * 10 000 heads, none dropped: unlike Tatar, every one of the top-10 000 Russian forms
         * occurs as the head of an in-vocabulary pair in the training corpora.
         *
         * Its own family and its own directory, so the Tatar table already inflated on a device
         * updating from 1.7.0 is neither renamed, re-inflated, nor sharing this language's lease
         * counter.
         *
         * **Not repacked since 2026-08-23, and its heads have drifted from the dictionary it is
         * paired with.** The Russian dictionary was rebuilt afterwards from whole-corpus
         * frequencies (`bfb78e93`), which reordered it far more than the Tatar one: 4 195 of
         * these 10 000 heads are no longer in the top 10 000 by today's frequencies, and 4 195
         * words that are do not appear here. Repacking would swap them all at once, which is a
         * change of a different size and a different question from the Tatar imperatives, so
         * docs/IMPERATIVE-HEADS.md records the number and leaves the table alone.
         */
        @JvmField
        val RUSSIAN_BIGRAMS_V1 = BigramArtifactSpec(
            family = "russian_bigrams",
            generation = 1,
            fileLanguageTag = "ru",
            subtypeId = PersonalSubtypes.RUSSIAN,
            storageDirectoryName = "bigrams-ru",
            assetPath = "bigrams/russian_bigrams_v1.tatbigr.zlib",
            expectedCompressedSize = 160_510,
            expectedCompressedSha256 =
                "741f55b94fc0c367833a6e2aa2f22b1332debb59fcfe19f9429a71aed56bff73",
            expectedRawSize = 505_768,
            expectedRawSha256 =
                "264b09fc6e73becf4cfe47cfecb26e6911f56d39707e038e2702b97244cbb631",
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
