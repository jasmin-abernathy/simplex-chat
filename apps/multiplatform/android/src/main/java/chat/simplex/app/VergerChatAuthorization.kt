package chat.simplex.app

import android.os.SystemClock

/**
 * A short-lived authorization handoff from the trusted Verger WebView.
 * Issued only after the Verger server confirms the account role for the organization.
 * Never persisted, never accepts role flags from external Intents.
 */
object VergerChatAuthorization {
  data class Grant(val organizationId: String, val canCreateGroup: Boolean, val accentColor: String?)
  private var pending: Pair<Grant, Long>? = null

  @Synchronized
  fun grant(organizationId: String, canCreateGroup: Boolean, accentColor: String?) {
    pending = Grant(organizationId, canCreateGroup, accentColor) to SystemClock.elapsedRealtime()
  }

  @Synchronized
  fun consume(): Grant? {
    val value = pending
    pending = null
    return if (value != null && SystemClock.elapsedRealtime() - value.second <= 30_000L) value.first else null
  }
}
