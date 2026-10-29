package chat.simplex.app

import chat.simplex.common.views.database.VaultExportBlock
import chat.simplex.common.views.database.VaultExportGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class VaultExportGateTest {
  @Test fun permitsOnlyAnEncryptedLocalDatabaseWithRecoverablePassphraseAndNoRemoteHost() {
    assertNull(VaultExportGate.check(true, true, false, false))
  }

  @Test fun rejectsEveryMissingPrecondition() {
    assertEquals(VaultExportBlock.NO_LOCAL_DATABASE, VaultExportGate.check(false, true, false, false))
    assertEquals(VaultExportBlock.DATABASE_ENCRYPTION_UNKNOWN, VaultExportGate.check(true, null, false, false))
    assertEquals(VaultExportBlock.DATABASE_NOT_ENCRYPTED, VaultExportGate.check(true, false, false, false))
    assertEquals(VaultExportBlock.PASSPHRASE_NOT_RECOVERABLE, VaultExportGate.check(true, true, true, false))
    assertEquals(VaultExportBlock.REMOTE_HOST_CONNECTED, VaultExportGate.check(true, true, false, true))
  }

  @Test fun onlyOneCombinationOfPreconditionsPermitsExport() {
    for (local in listOf(false, true)) {
      for (encrypted in listOf(null, false, true)) {
        for (initialPassphrase in listOf(false, true)) {
          for (remoteConnected in listOf(false, true)) {
            assertEquals(
              local && encrypted == true && !initialPassphrase && !remoteConnected,
              VaultExportGate.check(local, encrypted, initialPassphrase, remoteConnected) == null,
              "local=$local encrypted=$encrypted initialPassphrase=$initialPassphrase remoteConnected=$remoteConnected"
            )
          }
        }
      }
    }
  }

  @Test fun refusesPartialArchiveAndInvalidErrorCounts() {
    assertTrue(VaultExportGate.canOfferArchive(0))
    assertFalse(VaultExportGate.canOfferArchive(1))
    assertFalse(VaultExportGate.canOfferArchive(100))
    assertFailsWith<IllegalArgumentException> { VaultExportGate.canOfferArchive(-1) }
  }
}
