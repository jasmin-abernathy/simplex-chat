package chat.simplex.common.views.database

/**
 * Safety checks for a user-initiated archive handed to an external vault.
 * A successful check only permits starting the normal export flow.
 * It cannot attest that an external copy was uploaded or can be restored.
 */
enum class VaultExportBlock {
  NO_LOCAL_DATABASE,
  DATABASE_ENCRYPTION_UNKNOWN,
  DATABASE_NOT_ENCRYPTED,
  PASSPHRASE_NOT_RECOVERABLE,
  REMOTE_HOST_CONNECTED
}

object VaultExportGate {
  fun check(
    localDatabaseAvailable: Boolean,
    encrypted: Boolean?,
    initialRandomPassphrase: Boolean,
    remoteHostConnected: Boolean
  ): VaultExportBlock? = when {
    !localDatabaseAvailable -> VaultExportBlock.NO_LOCAL_DATABASE
    encrypted == null -> VaultExportBlock.DATABASE_ENCRYPTION_UNKNOWN
    !encrypted -> VaultExportBlock.DATABASE_NOT_ENCRYPTED
    initialRandomPassphrase -> VaultExportBlock.PASSPHRASE_NOT_RECOVERABLE
    remoteHostConnected -> VaultExportBlock.REMOTE_HOST_CONNECTED
    else -> null
  }

  /** Partial archives must never be proposed as completed external-vault exports. */
  fun canOfferArchive(archiveErrorCount: Int): Boolean {
    require(archiveErrorCount >= 0)
    return archiveErrorCount == 0
  }
}
