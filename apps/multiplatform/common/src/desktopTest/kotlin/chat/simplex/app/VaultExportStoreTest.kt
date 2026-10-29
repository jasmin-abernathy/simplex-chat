package chat.simplex.app

import chat.simplex.common.views.database.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class VaultExportStoreTest {
  private fun inCache(test: (File) -> Unit) {
    val cache = Files.createTempDirectory("vault-store-test").toFile()
    try { test(cache) } finally { cache.deleteRecursively() }
  }

  @Test fun restartRemovesZipAndEngineWorkButPreservesOtherArchives() = inCache { cache ->
    val previous = File(cache, "simplex-chat.previous.zip").apply { writeText("recovery") }
    val archive = VaultExportStore(cache).createArchive().apply { writeText("partial") }
    File(archive.parentFile, "engine-work/media").apply { mkdirs() }
    File(archive.parentFile, "engine-work/media/photo").writeText("private")
    val restarted = VaultExportStore(cache)
    assertTrue(restarted.recoverInterrupted())
    assertFalse(archive.parentFile.exists())
    assertEquals("recovery", previous.readText())
    assertTrue(restarted.recoverInterrupted())
  }

  @Test fun restartAlsoCleansAnAttemptInterruptedBeforeWritingTheZip() = inCache { cache ->
    val archive = VaultExportStore(cache).createArchive()
    assertTrue(archive.parentFile.isDirectory)
    assertFalse(archive.exists())
    assertTrue(VaultExportStore(cache).recoverInterrupted())
    assertFalse(archive.parentFile.exists())
  }

  @Test fun cleanupOfOneAttemptDoesNotTouchAnotherActiveAttempt() = inCache { cache ->
    val store = VaultExportStore(cache)
    val first = store.createArchive().apply { writeText("first") }
    val second = store.createArchive().apply { writeText("second") }
    assertNotEquals(first.parent, second.parent)
    assertTrue(store.removeArchive(first))
    assertFalse(first.parentFile.exists())
    assertEquals("second", second.readText())
  }

  @Test fun delayedStartupCleanupCannotDeleteANewActiveExport() = inCache { cache ->
    val store = VaultExportStore(cache)
    val archive = store.createArchive().apply { writeText("active") }
    assertTrue(store.recoverInterrupted())
    assertEquals("active", archive.readText())
  }

  @Test fun successfulCopyRemovesTheWholeAttempt() = inCache { cache ->
    val store = VaultExportStore(cache)
    val archive = store.createArchive().apply { writeText("archive") }
    File(archive.parentFile, "engine-temp").writeText("temporary")
    val result = finishVaultExportCopy(archive, store::removeArchive) { ByteArrayOutputStream() }
    assertEquals(VaultExportCopyResult(VaultExportCopyStatus.SAVED, true), result)
    assertFalse(archive.parentFile.exists())
  }

  @Test fun engineFailureCleansWorkFilesAsWellAsTheZip() = inCache { cache ->
    val store = VaultExportStore(cache)
    val archive = store.createArchive()
    runBlocking {
      assertFailsWith<IOException> {
        runVaultExportAttempt(archive, store::removeArchive) {
          archive.writeText("partial")
          File(archive.parentFile, "engine-temp").writeText("temporary")
          throw IOException("engine failed")
        }
      }
    }
    assertFalse(archive.parentFile.exists())
  }

  @Test fun cleanupDoesNotFollowLinksInsideAnAttempt() = inCache { cache ->
    val outside = File(cache, "keep").apply { mkdir() }
    val recovery = File(outside, "recovery.zip").apply { writeText("recovery") }
    val store = VaultExportStore(cache)
    val archive = store.createArchive()
    Files.createSymbolicLink(File(archive.parentFile, "link").toPath(), outside.toPath())
    assertTrue(VaultExportStore(cache).recoverInterrupted())
    assertEquals("recovery", recovery.readText())
    assertFalse(archive.parentFile.exists())
  }

  @Test fun rootLinkIsRefusedAndNewExportsStayBlockedUntilRepaired() = inCache { cache ->
    val outside = File(cache, "keep").apply { mkdir() }
    val recovery = File(outside, "recovery.zip").apply { writeText("recovery") }
    val root = File(cache, "vault-exports-v1")
    Files.createSymbolicLink(root.toPath(), outside.toPath())
    val store = VaultExportStore(cache)
    assertFalse(store.recoverInterrupted())
    assertFailsWith<IOException> { store.createArchive() }
    assertEquals("recovery", recovery.readText())
    Files.delete(root.toPath())
    assertTrue(store.createArchive().parentFile.isDirectory)
  }

  @Test fun attemptLinkIsRefusedWithoutTouchingItsTarget() = inCache { cache ->
    val outside = File(cache, "keep").apply { mkdir() }
    val recovery = File(outside, "simplex-chat.zip").apply { writeText("recovery") }
    val root = File(cache, "vault-exports-v1").apply { mkdir() }
    Files.createSymbolicLink(File(root, UUID.randomUUID().toString()).toPath(), outside.toPath())
    val store = VaultExportStore(cache)
    assertFalse(store.recoverInterrupted())
    assertFailsWith<IOException> { store.createArchive() }
    assertEquals("recovery", recovery.readText())
  }

  @Test fun unexpectedNamesAndOutsidePathsAreNeverRemoved() = inCache { cache ->
    val root = File(cache, "vault-exports-v1").apply { mkdir() }
    val keep = File(root, "not-an-attempt").apply { mkdir() }
    val recovery = File(keep, "simplex-chat.zip").apply { writeText("recovery") }
    val store = VaultExportStore(cache)
    assertTrue(store.recoverInterrupted())
    assertFalse(store.removeArchive(recovery))
    assertFalse(store.removeArchive(File(cache, "simplex-chat.zip")))
    assertEquals("recovery", recovery.readText())
  }

  @Test fun invalidRootIsReportedAndNotDeleted() = inCache { cache ->
    val root = File(cache, "vault-exports-v1").apply { writeText("unexpected") }
    val store = VaultExportStore(cache)
    assertFalse(store.recoverInterrupted())
    assertFailsWith<IOException> { store.createArchive() }
    assertEquals("unexpected", root.readText())
  }
}
