package chat.simplex.app

import org.junit.Assert.*
import org.junit.Test

class VergerWebPolicyTest {
  @Test fun bundledDocumentOnly() {
    assertTrue(VergerWebPolicy.isDocument(VergerWebPolicy.HOME))
    assertTrue(VergerWebPolicy.isDocument(VergerWebPolicy.HOME + "#automerge:test"))
    for (url in listOf(null, "", "javascript:alert(1)", "file:///android_asset/verger/index.html", "https://evil.test/assets/verger/index.html", "https://appassets.androidplatform.net.evil.test/assets/verger/index.html", "https://user@appassets.androidplatform.net/assets/verger/index.html", "https://appassets.androidplatform.net:443/assets/verger/index.html", "https://appassets.androidplatform.net/assets/verger/../index.html", "https://appassets.androidplatform.net/assets/verger/other.html")) {
      assertFalse(url, VergerWebPolicy.isDocument(url))
    }
  }
}
