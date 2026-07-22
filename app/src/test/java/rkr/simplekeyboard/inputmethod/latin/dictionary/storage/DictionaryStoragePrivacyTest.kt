package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DictionaryStoragePrivacyTest {
    @Test
    fun storageSurfaceAcceptsNoTypedTextAndSourcesContainNoLoggingNetworkOrAnalytics() {
        val publicSurface = listOf(
            AtomicDictionaryStore::class.java,
            BackgroundDictionaryPreparer::class.java,
            DictionaryStorageController::class.java,
            PublishedDictionaryCatalog::class.java,
            DictionaryFileLease::class.java,
            TdictValidator::class.java,
        ).flatMap { type -> type.declaredMethods.map { "${type.simpleName}.${it.name}" } }
        val typedTextTerms = listOf("typed", "query", "prefix", "candidate", "inputtext")
        assertFalse(publicSurface.any { method ->
            typedTextTerms.any { term -> method.lowercase().contains(term) }
        })

        val sourceDirectory = locateStorageSources()
        val source = sourceDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        listOf(
            "android.util.Log",
            "java.net.",
            "android.permission.INTERNET",
            "FirebaseAnalytics",
            "typedText",
        ).forEach { forbidden -> assertFalse("found $forbidden", source.contains(forbidden)) }
        assertTrue(source.contains("AssetManager.ACCESS_STREAMING"))
        assertTrue(source.contains("createDeviceProtectedStorageContext()"))
        assertTrue(source.contains("catch (error: ErrnoException)"))
        assertTrue(source.contains("throw IOException"))
        assertFalse(source.contains("createCredentialProtectedStorageContext()"))
    }

    private fun locateStorageSources(): File {
        val relative = "src/main/java/rkr/simplekeyboard/inputmethod/latin/dictionary/storage"
        return listOf(File(relative), File("app/$relative")).firstOrNull(File::isDirectory)
            ?: error("cannot locate storage sources")
    }
}
