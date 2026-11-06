package chat.simplex.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
  private var uploadCallback: ValueCallback<Array<Uri>>? = null
  private var pendingSave: ByteArray? = null

  private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    uploadCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    uploadCallback = null
  }

  private val savePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val bytes = pendingSave
    pendingSave = null
    if (result.resultCode == Activity.RESULT_OK && bytes != null && result.data?.data != null) {
      try {
        val saved = contentResolver.openOutputStream(result.data!!.data!!, "w")
        if (saved == null) throw IllegalStateException("Le fichier n’a pas pu être créé")
        saved.use { it.write(bytes) }
        Toast.makeText(this, "Fichier enregistré", Toast.LENGTH_SHORT).show()
      } catch (error: Exception) {
        Toast.makeText(this, "Échec de la sauvegarde : " + (error.message ?: "erreur inconnue"), Toast.LENGTH_LONG).show()
      }
    }
  }

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
      // L’import est explicitement autorisé uniquement après sélection via le fournisseur Android.
      settings.allowContentAccess = true
      settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      settings.javaScriptCanOpenWindowsAutomatically = false
      settings.setSupportMultipleWindows(false)
      addJavascriptInterface(NavigationBridge(), "VergerNative")
      webChromeClient = object : WebChromeClient() {
        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
          if (!VergerWebPolicy.isDocument(this@VergerActivity.webView.url)) {
            filePathCallback?.onReceiveValue(null)
            return true
          }
          uploadCallback?.onReceiveValue(null)
          uploadCallback = filePathCallback
          try {
            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
              addCategory(Intent.CATEGORY_OPENABLE)
              type = "application/json"
            }
            filePicker.launch(intent)
          } catch (error: ActivityNotFoundException) {
            uploadCallback?.onReceiveValue(null)
            uploadCallback = null
            Toast.makeText(this@VergerActivity, "Aucun sélecteur de fichiers disponible", Toast.LENGTH_LONG).show()
          }
          return true
        }
      }
      webViewClient = object : WebViewClientCompat() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
          if (VergerWebPolicy.isDocument(request.url.toString())) return false
          if (request.isForMainFrame && request.url.scheme in listOf("https", "http")) {
            try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
            catch (_: ActivityNotFoundException) { Toast.makeText(this@VergerActivity, "Navigateur indisponible", Toast.LENGTH_LONG).show() }
          }
          return true
        }

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

    @JavascriptInterface
    fun saveText(filename: String, mime: String, contents: String) = runOnUiThread {
      if (!VergerWebPolicy.isDocument(webView.url)) return@runOnUiThread
      if (mime !in setOf("application/json", "text/markdown", "text/calendar")) return@runOnUiThread
      val bytes = contents.toByteArray(Charsets.UTF_8)
      if (bytes.size > 12_000_000) {
        Toast.makeText(this@VergerActivity, "Export trop volumineux (limite 12 Mo)", Toast.LENGTH_LONG).show()
        return@runOnUiThread
      }
      if (pendingSave != null) {
        Toast.makeText(this@VergerActivity, "Terminez d’abord la sauvegarde en cours", Toast.LENGTH_LONG).show()
        return@runOnUiThread
      }
      pendingSave = bytes
      val safeName = filename.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(140).ifBlank { "verger-export" }
      try {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).apply {
          type = mime
          putExtra(Intent.EXTRA_TITLE, safeName)
        }
        savePicker.launch(intent)
      } catch (error: ActivityNotFoundException) {
        pendingSave = null
        Toast.makeText(this@VergerActivity, "Enregistrement indisponible", Toast.LENGTH_LONG).show()
      }
    }

    @JavascriptInterface
    fun openExternal(raw: String) = runOnUiThread {
      if (!VergerWebPolicy.isDocument(webView.url)) return@runOnUiThread
      val uri = Uri.parse(raw)
      if (uri.scheme in setOf("https", "http") && uri.host != null) {
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this@VergerActivity, "Navigateur indisponible", Toast.LENGTH_LONG).show() }
      }
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
