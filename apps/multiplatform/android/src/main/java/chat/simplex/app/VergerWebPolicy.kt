package chat.simplex.app

import java.net.URI

/** Only the bundled Verger document may own the native navigation bridge. */
object VergerWebPolicy {
  const val HOME = "https://appassets.androidplatform.net/assets/verger/index.html"
  fun isDocument(value: String?): Boolean = try {
    val uri = URI(value ?: "")
    uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
      uri.port == -1 && uri.userInfo == null && uri.rawPath == "/assets/verger/index.html"
  } catch (_: Exception) { false }
}
