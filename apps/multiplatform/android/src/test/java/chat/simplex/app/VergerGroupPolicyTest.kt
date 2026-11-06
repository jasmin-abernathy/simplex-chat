package chat.simplex.app

import chat.simplex.common.VergerGroupPolicy
import org.junit.Assert.*
import org.junit.Test

class VergerGroupPolicyTest {
  @Test fun acceptsPrivateGroupInvitationsOnly() {
    assertTrue(VergerGroupPolicy.isGroupInvitation("https://simplex.chat/g#private-test"))
    assertTrue(VergerGroupPolicy.isGroupInvitation("https://smp4.simplex.im/g#private-test"))
    assertTrue(VergerGroupPolicy.isGroupInvitation("https://smp1.simplexonflux.com/g#private-test"))
    assertTrue(VergerGroupPolicy.isGroupInvitation("simplex:/g#private-test"))
    for (value in listOf(null, "", "https://simplex.chat/contact#x", "https://simplex.chat/a#x",
      "https://simplex.chat/g", "http://simplex.chat/g#x", "https://evil.test/g#x",
      "https://smpabc.simplex.im/g#x", "https://smp4.simplex.im/contact#x",
      "https://simplex.chat.evil.test/g#x", "https://user@simplex.chat/g#x",
      "javascript:alert(1)", "https://simplex.chat/g#x\n")) {
      assertFalse(value, VergerGroupPolicy.isGroupInvitation(value))
    }
  }
}
