package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import androidx.annotation.Keep
import java.io.Closeable
import java.util.Locale

/**
 * E5b: the shipped bigram-table asset, schema 2 (`TATBIGR\0`) — a SEPARATE artifact kind from
 * [DictionaryArtifactSpec]'s schema 1, per PROPOSALS.md ("E5b. Отдельный файл и отдельная
 * схема"). [languageTag] parameterizes storage "with language from the start" (contract wording)
 * even though only the Tatar table ships today; a second language adds a second spec, not a
 * second class.
 */
data class BigramArtifactSpec(
    val generation: Int,
    val languageTag: String,
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
        require(languageTag.isNotBlank())
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
            "tatar_bigrams-%s-v%06d-s%d-f%d-%s.tatbigr",
            languageTag,
            generation,
            schemaId,
            formatVersion,
            expectedRawSha256.lowercase(),
        )

    companion object {
        /**
         * The tt table packed 2026-08-17 by `scripts/bigram_asset_pack.py pack` from the E5a-
         * chosen configuration (H=10 000, K=6 — docs/DICTIONARY-E5B.md records the choice and
         * why). 9 996 heads, not 10 000: four top-10 000-by-frequency words never occur as the
         * head of an in-vocabulary pair in mixed+web and are dropped rather than stored with an
         * empty range (docs/DICTIONARY-E5B.md, "Dropped heads").
         */
        @JvmField
        val TATAR_BIGRAMS_V1 = BigramArtifactSpec(
            generation = 1,
            languageTag = "tt",
            assetPath = "bigrams/tatar_bigrams_v1.tatbigr.zlib",
            expectedCompressedSize = 226_428,
            expectedCompressedSha256 =
                "89eb4aa82be45a57ea94daa0379ca3d8a07f1c630e5c532960832787b1e1ab8d",
            expectedRawSize = 644_148,
            expectedRawSha256 =
                "fb686476f6252f61f9d26632ccbd228f13aa1bffca7fe9bfff5f24baf9e0b05b",
            expectedHeadCount = 9_996,
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
    val languageTag: String,
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
