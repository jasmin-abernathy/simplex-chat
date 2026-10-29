package chat.simplex.common.views.database

import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

/** The attempt directories are the on-disk inventory, created before any archive content. */
class VaultExportStore(cacheDir: File) {
  private val root = cacheDir.absoluteFile.toPath().normalize().resolve("vault-exports-v1")
  private val attemptName = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
  private var recoveryCompleted = false

  @Synchronized
  fun createArchive(): File {
    if (!recoverInterrupted()) throw VaultExportCleanupException()
    if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(root)
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw IOException("Invalid vault export directory")
    val attempt = Files.createDirectory(root.resolve(UUID.randomUUID().toString()))
    return attempt.resolve("simplex-chat.zip").toFile()
  }

  /** One successful startup sweep per store instance; later calls cannot remove active exports. */
  @Synchronized
  fun recoverInterrupted(): Boolean {
    if (recoveryCompleted) return true
    val success = try {
      if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
        true
      } else if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
        false
      } else {
        var complete = true
        Files.newDirectoryStream(root).use { entries ->
          for (entry in entries) {
            if (attemptName.matches(entry.fileName.toString()) && !removeAttempt(entry)) complete = false
          }
        }
        complete
      }
    } catch (_: DirectoryIteratorException) {
      false
    } catch (_: IOException) {
      false
    } catch (_: SecurityException) {
      false
    }
    recoveryCompleted = success
    return success
  }

  @Synchronized
  fun removeArchive(archive: File): Boolean {
    val path = archive.toPath().toAbsolutePath().normalize()
    if (path.fileName?.toString() != "simplex-chat.zip") return false
    return removeAttempt(path.parent)
  }

  private fun removeAttempt(attempt: Path): Boolean {
    if (attempt.parent != root || !attemptName.matches(attempt.fileName.toString())) return false
    return try {
      if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) return true
      if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return false
      if (Files.notExists(attempt, LinkOption.NOFOLLOW_LINKS)) return true
      if (!Files.isDirectory(attempt, LinkOption.NOFOLLOW_LINKS)) return false
      // No FOLLOW_LINKS: engine-created links must never lead cleanup outside this attempt.
      Files.walkFileTree(attempt, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          Files.delete(file)
          return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
          if (exc != null) throw exc
          Files.delete(dir)
          return FileVisitResult.CONTINUE
        }
      })
      true
    } catch (_: IOException) {
      false
    } catch (_: SecurityException) {
      false
    }
  }
}
