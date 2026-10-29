package chat.simplex.app

import chat.simplex.common.views.database.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

class VaultExportAttemptTest {
  private fun inDirectory(test: suspend (File) -> Unit) = runBlocking {
    val dir = Files.createTempDirectory("vault-attempt-test").toFile()
    try { test(dir) } finally { dir.deleteRecursively() }
  }

  @Test fun successfulArchiveRemainsAvailableForTheChooser() = inDirectory { dir ->
    val archive = File(dir, "new.zip")
    val result = runVaultExportAttempt(archive) { archive.writeText("complete"); 42 }
    assertEquals(42, result)
    assertEquals("complete", archive.readText())
  }

  @Test fun engineFailureRemovesOnlyThisAttempt() = inDirectory { dir ->
    val previous = File(dir, "previous.zip").apply { writeText("recovery") }
    val archive = File(dir, "new.zip")
    val failure = IOException("engine failed")
    val caught = assertFailsWith<IOException> {
      runVaultExportAttempt(archive) { archive.writeText("partial"); throw failure }
    }
    assertSame(failure, caught)
    assertFalse(archive.exists())
    assertEquals("recovery", previous.readText())
  }

  @Test fun cancellationIsRethrownAfterCleanup() = inDirectory { dir ->
    val archive = File(dir, "new.zip")
    val failure = CancellationException("cancelled")
    val caught = assertFailsWith<CancellationException> {
      runVaultExportAttempt(archive) { archive.writeText("partial"); throw failure }
    }
    assertSame(failure, caught)
    assertFalse(archive.exists())
  }

  @Test fun failureBeforeFileCreationDoesNotInventACleanupFailure() = inDirectory { dir ->
    val failure = IOException("settings failed")
    assertSame(failure, assertFailsWith<IOException> {
      runVaultExportAttempt(File(dir, "new.zip")) { throw failure }
    })
    assertTrue(failure.suppressed.isEmpty())
  }

  @Test fun existingDestinationIsNeverModified() = inDirectory { dir ->
    val archive = File(dir, "existing.zip").apply { writeText("recovery") }
    var invoked = false
    assertFailsWith<IllegalStateException> {
      runVaultExportAttempt(archive) { invoked = true }
    }
    assertFalse(invoked)
    assertEquals("recovery", archive.readText())
  }

  @Test fun failedDeletionIsReportedWithoutHidingTheOriginalError() = inDirectory { dir ->
    val archive = object : File(dir, "new.zip") { override fun delete(): Boolean = false }
    val failure = IOException("engine failed")
    assertSame(failure, assertFailsWith<IOException> {
      runVaultExportAttempt(archive) { archive.writeText("partial"); throw failure }
    })
    assertTrue(archive.exists())
    assertTrue(failure.suppressed.single() is VaultExportCleanupException)
  }
}
