package chat.simplex.common.views.database

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.CancellationException

enum class VaultExportCopyStatus { SAVED, CANCELLED, FAILED }

data class VaultExportCopyResult(val status: VaultExportCopyStatus, val temporaryRemoved: Boolean)

fun removeVaultExportArchive(archive: File): Boolean = try {
  !archive.exists() || (archive.isFile && archive.delete())
} catch (_: SecurityException) {
  false
}

/** Closing the provider stream is part of the copy, not evidence of a recoverable backup. */
fun finishVaultExportCopy(
  archive: File,
  cleanup: (File) -> Boolean = ::removeVaultExportArchive,
  openDestination: (() -> OutputStream)?
): VaultExportCopyResult {
  var removed = false
  val status = try {
    if (openDestination == null) {
      VaultExportCopyStatus.CANCELLED
    } else {
      archive.inputStream().use { input ->
        BufferedOutputStream(openDestination()).use { output -> input.copyTo(output) }
      }
      VaultExportCopyStatus.SAVED
    }
  } catch (e: Exception) {
    if (e is CancellationException) throw e
    VaultExportCopyStatus.FAILED
  } finally {
    removed = cleanup(archive)
  }
  return VaultExportCopyResult(status, removed)
}
