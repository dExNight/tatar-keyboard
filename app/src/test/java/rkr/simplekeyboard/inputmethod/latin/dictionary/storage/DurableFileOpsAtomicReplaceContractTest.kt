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

package rkr.simplekeyboard.inputmethod.latin.dictionary.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileDescriptor
import java.io.IOException

/**
 * The new `DurableFileOps.atomicReplace` (E4a-2) must REPLACE an existing destination, while
 * `atomicRename` must keep its deliberate throw-on-existing-destination behaviour — D1b's staged
 * publication retention depends on it, so its semantics are unchanged. Exercised through the
 * interface default (JVM); the production `Os.rename`-based override is device-only. The 16
 * `AtomicDictionaryStoreTest` cases prove D1b end to end and remain green without edits.
 */
class DurableFileOpsAtomicReplaceContractTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** A minimal ops using real java.io, inheriting the default [DurableFileOps.atomicReplace]. */
    private class RealishOps : DurableFileOps {
        override fun createNewFile(file: File): Boolean = file.createNewFile()
        override fun syncFile(fileDescriptor: FileDescriptor) = fileDescriptor.sync()
        override fun atomicRename(source: File, destination: File) {
            if (destination.exists()) throw IOException("versioned destination already exists")
            if (!source.renameTo(destination)) throw IOException("rename failed")
        }
        override fun syncDirectory(directory: File) {}
        override fun delete(file: File): Boolean = file.delete()
    }

    @Test
    fun atomicReplaceReplacesAnExistingDestinationInPlace() {
        val ops = RealishOps()
        val destination = temporaryFolder.newFile("dest").also { it.writeBytes("old".toByteArray()) }
        val source = temporaryFolder.newFile("src").also { it.writeBytes("new".toByteArray()) }

        ops.atomicReplace(source, destination)

        assertArrayEquals("new".toByteArray(), destination.readBytes())
        assertFalse("source is consumed by the replace", source.exists())
    }

    @Test
    fun atomicReplaceAlsoWorksWhenTheDestinationIsAbsent() {
        val ops = RealishOps()
        val destination = File(temporaryFolder.root, "fresh-dest")
        val source = temporaryFolder.newFile("src2").also { it.writeBytes("data".toByteArray()) }

        ops.atomicReplace(source, destination)

        assertArrayEquals("data".toByteArray(), destination.readBytes())
    }

    @Test
    fun atomicRenameStillThrowsWhenTheDestinationExists() {
        val ops = RealishOps()
        val destination = temporaryFolder.newFile("dest3").also { it.writeBytes("old".toByteArray()) }
        val source = temporaryFolder.newFile("src3").also { it.writeBytes("new".toByteArray()) }

        try {
            ops.atomicRename(source, destination)
            fail("atomicRename must reject an existing destination (D1b semantics)")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("already exists"))
        }
        assertArrayEquals("old".toByteArray(), destination.readBytes())
    }
}
