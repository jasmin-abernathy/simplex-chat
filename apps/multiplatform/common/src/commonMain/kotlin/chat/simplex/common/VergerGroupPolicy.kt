package chat.simplex.common

import java.net.URI

/** Validates group invitation links, not member authorization.
 * Group membership and invitation rights remain enforced by SimpleX. */
object VergerGroupPolicy {
  fun isGroupInvitation(value: String?): Boolean = try {
    val raw = value ?: ""
    if (raw.isEmpty() || raw.length > 16384 || raw.any { it.isWhitespace() || it.code < 0x20 }) false
    else {
      val uri = URI(raw)
      val validScheme = when (uri.scheme?.lowercase()) {
        "https" -> uri.host?.lowercase() == "simplex.chat" && uri.port == -1 && uri.userInfo == null
        "simplex" -> uri.host.isNullOrEmpty() && uri.userInfo == null
        else -> false
      }
      validScheme && uri.path in listOf("/g", "/g/") && !uri.rawFragment.isNullOrBlank()
    }
  } catch (_: Exception) { false }
}
