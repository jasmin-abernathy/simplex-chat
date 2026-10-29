package chat.simplex.common.views.database

import java.io.File
import java.io.IOException

/** Owns only the new archive of this attempt, never a previous recovery copy. */
class VaultExportCleanupException : IOException("Temporary vault export could not be removed")

suspend fun <T> runVaultExportAttempt(
  archive: File,
  cleanup: (File) -> Boolean = ::removeVaultExportArchive,
  export: suspend () -> T
): T {
  check(!archive.exists()) { "Vault export destination already exists" }
  try {
    return export()
  } catch (failure: Throwable) {
    try {
      if (!cleanup(archive)) failure.addSuppressed(VaultExportCleanupException())
    } catch (cleanupFailure: Throwable) {
      failure.addSuppressed(VaultExportCleanupException().apply { initCause(cleanupFailure) })
    }
    throw failure
  }
}
