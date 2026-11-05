package chat.simplex.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayInputStream

/** Business tools and SimpleX share one application, process and task. */
class VergerActivity : FragmentActivity() {
  private lateinit var webView: WebView
  private val preferences by lazy { getSharedPreferences("verger-web", MODE_PRIVATE) }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.WHITE)
    }
    ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }
    val chatButton = Button(this).apply {
      text = "Discussions · SimpleX"
      minHeight = (48 * resources.displayMetrics.density).toInt()
      setOnClickListener { openChat() }
    }
    layout.addView(chatButton, LinearLayout.LayoutParams(-1, -2))
    val loader = WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
      .build()
    webView = WebView(this).apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.allowFileAccess = false
      settings.allowContentAccess = false
      settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      settings.javaScriptCanOpenWindowsAutomatically = false
      settings.setSupportMultipleWindows(false)
      addJavascriptInterface(NavigationBridge(), "VergerNative")
      webViewClient = object : WebViewClientCompat() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
          !VergerWebPolicy.isDocument(request.url.toString())

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
          val response = loader.shouldInterceptRequest(request.url)
          if (response != null) {
            response.responseHeaders = mapOf(
              "Content-Security-Policy" to "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' https: wss:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'",
              "Referrer-Policy" to "no-referrer",
              "X-Content-Type-Options" to "nosniff"
            )
            return response
          }
          // Never fall back to the network for an absent packaged asset.
          if (request.url.host == "appassets.androidplatform.net") {
            return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
          }
          return null
        }

        override fun onPageFinished(view: WebView, url: String) {
          if (VergerWebPolicy.isDocument(url)) preferences.edit().putString("document", url).apply()
        }
      }
    }
    layout.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
    setContentView(layout)
    val saved = preferences.getString("document", null)
    webView.loadUrl(if (VergerWebPolicy.isDocument(saved)) saved!! else VergerWebPolicy.HOME)
  }

  private fun openChat() {
    if (isFinishing || isDestroyed) return
    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    finish()
  }

  private inner class NavigationBridge {
    @JavascriptInterface
    fun openChat() = runOnUiThread {
      if (VergerWebPolicy.isDocument(webView.url)) this@VergerActivity.openChat()
    }
  }

  override fun onPause() {
    if (::webView.isInitialized && VergerWebPolicy.isDocument(webView.url)) {
      preferences.edit().putString("document", webView.url).apply()
      webView.onPause()
    }
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    if (::webView.isInitialized) webView.onResume()
  }

  override fun onDestroy() {
    if (::webView.isInitialized) {
      webView.removeJavascriptInterface("VergerNative")
      webView.stopLoading()
      (webView.parent as? LinearLayout)?.removeView(webView)
      webView.destroy()
    }
    super.onDestroy()
  }
}
