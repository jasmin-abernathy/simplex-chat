package chat.simplex.app

import chat.simplex.common.views.database.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class VaultExportCopyTest {
  private fun withArchive(test: (File, File) -> Unit) {
    val dir = Files.createTempDirectory("vault-copy-test").toFile()
    val previous = File(dir, "previous.zip").apply { writeText("previous recovery") }
    val archive = File(dir, "attempt.zip").apply { writeText("new archive") }
    try {
      test(archive, previous)
      assertEquals("previous recovery", previous.readText())
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test fun successRequiresClosingTheDestinationAndRemovesOnlyTheTemporary() = withArchive { archive, _ ->
    var closed = false
    val output = object : ByteArrayOutputStream() {
      override fun close() { closed = true; super.close() }
    }
    val result = finishVaultExportCopy(archive) { output }
    assertEquals(VaultExportCopyResult(VaultExportCopyStatus.SAVED, true), result)
    assertTrue(closed)
    assertEquals("new archive", output.toString("UTF-8"))
    assertFalse(archive.exists())
  }

  @Test fun cancelledChooserRemovesTheTemporary() = withArchive { archive, _ ->
    assertEquals(VaultExportCopyResult(VaultExportCopyStatus.CANCELLED, true), finishVaultExportCopy(archive, null))
    assertFalse(archive.exists())
  }

  @Test fun permissionFailureCannotBecomeSuccess() = withArchive { archive, _ ->
    val result = finishVaultExportCopy(archive) { throw SecurityException("permission revoked") }
    assertEquals(VaultExportCopyResult(VaultExportCopyStatus.FAILED, true), result)
    assertFalse(archive.exists())
  }

  @Test fun fullDestinationIsClosedAndReportedAsFailure() = withArchive { archive, _ ->
    var closed = false
    val output = object : OutputStream() {
      override fun write(value: Int) { throw IOException("storage full") }
      override fun close() { closed = true }
    }
    val result = finishVaultExportCopy(archive) { output }
    assertEquals(VaultExportCopyResult(VaultExportCopyStatus.FAILED, true), result)
    assertTrue(closed)
    assertFalse(archive.exists())
  }

  @Test fun closeFailureCannotBecomeSuccess() = withArchive { archive, _ ->
    val output = object : ByteArrayOutputStream() {
      override fun close() { throw IOException("provider commit failed") }
    }
    assertEquals(VaultExportCopyResult(VaultExportCopyStatus.FAILED, true), finishVaultExportCopy(archive) { output })
    assertFalse(archive.exists())
  }

  @Test fun missingSourceDoesNotCreateADestination() = withArchive { archive, _ ->
    assertTrue(archive.delete())
    var opened = false
    val result = finishVaultExportCopy(archive) { opened = true; ByteArrayOutputStream() }
    assertEquals(VaultExportCopyResult(VaultExportCopyStatus.FAILED, true), result)
    assertFalse(opened)
  }

  @Test fun cleanupFailureIsSeparateFromCopySuccess() = withArchive { archive, _ ->
    val undeletable = object : File(archive.path) { override fun delete(): Boolean = false }
    assertEquals(
      VaultExportCopyResult(VaultExportCopyStatus.SAVED, false),
      finishVaultExportCopy(undeletable) { ByteArrayOutputStream() }
    )
    assertTrue(archive.exists())
  }

  @Test fun cancellationIsPropagatedAfterCleanup() = withArchive { archive, _ ->
    val failure = CancellationException("cancelled")
    assertSame(failure, assertFailsWith<CancellationException> {
      finishVaultExportCopy(archive) { throw failure }
    })
    assertFalse(archive.exists())
  }
}
