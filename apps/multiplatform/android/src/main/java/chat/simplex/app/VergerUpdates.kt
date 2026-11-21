package chat.simplex.app

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object VergerUpdates {
  private const val releases = "https://api.github.com/repos/jasmin-abernathy/verger-associations/releases/latest"
  private const val downloadRoot = "https://github.com/jasmin-abernathy/verger-associations/releases/download"
  private var nextCheck = 0L

  fun check(activity: VergerActivity) {
    if (activity.packageName != "chat.simplex.app.verger") return
    val now = SystemClock.elapsedRealtime()
    if (now < nextCheck) return
    val prefs = activity.getSharedPreferences("verger-updates", 0)
    val lastCheck = prefs.getLong("checked", 0)
    if (System.currentTimeMillis() - lastCheck in 0 until 86_400_000L) return
    nextCheck = now + 900_000L

    Thread {
      try {
        val release = fetchLatest()
        prefs.edit().putLong("checked", System.currentTimeMillis()).apply()
        if (release == null) return@Thread
        val download = updateUrl(release, BuildConfig.VERSION_CODE) ?: return@Thread
        activity.runOnUiThread {
          if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
          AlertDialog.Builder(activity)
            .setTitle("Mise à jour disponible")
            .setMessage("Une nouvelle version de Verger Associations est disponible sur GitHub.")
            .setNegativeButton("Plus tard", null)
            .setPositiveButton("Télécharger") { _, _ ->
              try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(download)))
              } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, "Navigateur indisponible", Toast.LENGTH_LONG).show()
              }
            }
            .show()
        }
      } catch (_: Exception) {
        // A failed network check must leave the app usable offline.
      }
    }.start()
  }

  private fun fetchLatest(): JSONObject? {
    val connection = URL(releases).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 5000
      connection.readTimeout = 5000
      connection.instanceFollowRedirects = false
      connection.setRequestProperty("Accept", "application/vnd.github+json")
      return when (connection.responseCode) {
        404 -> null
        200 -> connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        else -> throw java.io.IOException("GitHub HTTP ${connection.responseCode}")
      }
    } finally {
      connection.disconnect()
    }
  }

  internal fun updateUrl(release: JSONObject, installed: Int): String? {
    if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
    val tag = release.optString("tag_name")
    val code = Regex("android-([1-9][0-9]{0,9})").matchEntire(tag)
      ?.groupValues?.get(1)?.toIntOrNull() ?: return null
    if (code <= installed || code > 2_100_000_000) return null
    val expected = "$downloadRoot/$tag/verger-associations.apk"
    val assets = release.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
      if (assets.optJSONObject(i)?.optString("browser_download_url") == expected) return expected
    }
    return null
  }
}
