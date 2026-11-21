package chat.simplex.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VergerUpdatesTest {
  @Test
  fun acceptsOnlyANewerPublishedVergerApk() {
    val url = "https://github.com/jasmin-abernathy/verger-associations/releases/download/android-102/verger-associations.apk"
    val release = JSONObject().put("tag_name", "android-102").put(
      "assets", JSONArray().put(JSONObject().put("browser_download_url", url))
    )
    assertEquals(url, VergerUpdates.updateUrl(release, 101))
    assertNull(VergerUpdates.updateUrl(release, 102))
    assertNull(VergerUpdates.updateUrl(release, 103))
    release.put("prerelease", true)
    assertNull(VergerUpdates.updateUrl(release, 101))
    release.put("prerelease", false).put("draft", true)
    assertNull(VergerUpdates.updateUrl(release, 101))
    release.put("draft", false).put("tag_name", "android-104")
    assertNull(VergerUpdates.updateUrl(release, 101))
  }
}
