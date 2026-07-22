package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Deflater

internal data class TestArtifact(
    val spec: DictionaryArtifactSpec,
    val raw: ByteArray,
    val compressed: ByteArray,
)

internal object DictionaryTestFixtures {
    fun artifact(
        generation: Int = 1,
        entries: List<Pair<String, Long>> = listOf(
            "аб" to 30,
            "аба" to 20,
            "әби" to 10,
        ),
        dictionary: ByteArray? = null,
    ): TestArtifact {
        val raw = raw(entries)
        val compressed = compress(raw, dictionary)
        return TestArtifact(spec(generation, raw, compressed), raw, compressed)
    }

    fun raw(entries: List<Pair<String, Long>>): ByteArray {
        val encoded = entries.map { it.first.toByteArray(Charsets.UTF_8) }
        val offsets = mutableListOf(0)
        encoded.forEach { offsets += offsets.last() + it.size }
        val entryCount = entries.size
        val frequenciesOffset = 72 + 4 * (entryCount + 1)
        val blobOffset = frequenciesOffset + 4 * entryCount
        val blobSize = encoded.sumOf { it.size }
        val fileSize = blobOffset + blobSize
        val bytes = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("TATDICT\u0000".toByteArray(Charsets.US_ASCII))
        bytes.putShort(1)
        bytes.putShort(1)
        bytes.putShort(72)
        bytes.putShort(1)
        bytes.putInt(entryCount)
        bytes.putInt(72)
        bytes.putInt(frequenciesOffset)
        bytes.putInt(blobOffset)
        bytes.putInt(blobSize)
        bytes.putInt(fileSize)
        bytes.put(ByteArray(32))
        offsets.forEach(bytes::putInt)
        entries.forEach { bytes.putInt(it.second.toInt()) }
        encoded.forEach(bytes::put)
        return refreshEmbeddedChecksum(bytes.array())
    }

    fun spec(
        generation: Int,
        raw: ByteArray,
        compressed: ByteArray = compress(raw),
        expectedRawSize: Long = raw.size.toLong(),
        maxCompressedSize: Long = 700_000,
        maxRawSize: Long = 2_936_012,
    ) = DictionaryArtifactSpec(
        generation = generation,
        assetPath = "fixture-$generation.zlib",
        expectedCompressedSize = minOf(compressed.size.toLong(), maxCompressedSize),
        expectedCompressedSha256 = sha256(compressed),
        expectedRawSize = expectedRawSize,
        expectedRawSha256 = sha256(raw),
        expectedEntryCount = ByteBuffer.wrap(raw, 16, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffff_ffffL,
        maxCompressedSize = maxCompressedSize,
        maxRawSize = maxRawSize,
    )

    fun compress(raw: ByteArray, dictionary: ByteArray? = null): ByteArray {
        val deflater = Deflater(9, false)
        if (dictionary != null) deflater.setDictionary(dictionary)
        deflater.setInput(raw)
        deflater.finish()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()
        return output.toByteArray()
    }

    fun refreshEmbeddedChecksum(input: ByteArray): ByteArray {
        val result = input.copyOf()
        result.fill(0, 40, 72)
        val checksum = MessageDigest.getInstance("SHA-256").digest(result)
        checksum.copyInto(result, 40)
        return result
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
}

internal open class TestDurableFileOps : DurableFileOps {
    val events = mutableListOf<String>()
    val deleted = mutableListOf<File>()

    override fun createNewFile(file: File): Boolean = file.createNewFile()

    override fun syncFile(fileDescriptor: FileDescriptor) {
        events += "file-fsync"
        fileDescriptor.sync()
    }

    override fun atomicRename(source: File, destination: File) {
        events += "rename"
        if (destination.exists() || !source.renameTo(destination)) {
            throw java.io.IOException("atomic rename failed")
        }
    }

    override fun syncDirectory(directory: File) {
        events += "directory-fsync"
    }

    override fun delete(file: File): Boolean {
        events += "delete:${file.name}"
        deleted += file
        return file.delete()
    }
}

internal class CountingAssetProvider(
    private val assets: MutableMap<Int, ByteArray>,
) : AssetInputProvider {
    var opens = 0

    override fun open(spec: DictionaryArtifactSpec): java.io.InputStream {
        opens++
        return (assets[spec.generation] ?: error("missing fixture")).inputStream()
    }
}
