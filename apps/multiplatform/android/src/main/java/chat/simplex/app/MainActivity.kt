package chat.simplex.app

import android.content.Intent
import android.net.Uri
import android.os.*
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.ui.Modifier
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import chat.simplex.app.model.NtfManager
import chat.simplex.app.model.NtfManager.getUserIdFromIntent
import chat.simplex.common.*
import chat.simplex.common.helpers.*
import chat.simplex.common.model.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.chatlist.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.onboarding.*
import chat.simplex.common.platform.*
import chat.simplex.res.MR
import java.lang.ref.WeakReference

class MainActivity: FragmentActivity() {
  private var verifiedChatOrganizationId: String? = null
  private var mayCreateAssociationGroup: Boolean = false
  private fun groupPreferenceKey(): String = "groupId." + (verifiedChatOrganizationId ?: "unverified")

  companion object {
    const val OLD_ANDROID_UI_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    mainActivity = WeakReference(this)
    platform.androidSetNightModeIfSupported()
    val c = CurrentColors.value.colors
    platform.androidSetStatusAndNavigationBarAppearance(c.isLight, c.isLight)
    // Verger est une application francophone : ne pas hériter de l'ancien choix
    // anglais stocké par SimpleX ou par une installation précédente du prototype.
    // Ce réglage est réappliqué à chaque ouverture de la messagerie.
    val language = ChatModel.controller.appPrefs.appLanguage
    if (language.get() != "fr") language.set("fr")
    applyAppLocale(language)
    // This flag makes status bar and navigation bar fully transparent. But on API level < 30 it breaks insets entirely
    // https://issuetracker.google.com/issues/236862874
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }
    super.onCreate(savedInstanceState)
    val grant = if (savedInstanceState == null) VergerChatAuthorization.consume() else null
    verifiedChatOrganizationId = savedInstanceState?.getString("verifiedChatOrganizationId") ?: grant?.organizationId
    mayCreateAssociationGroup = savedInstanceState?.getBoolean("mayCreateAssociationGroup")
      ?: (grant?.canCreateGroup == true)
    // testJson()
    // When call ended and orientation changes, it re-process old intent, it's unneeded.
    // Only needed to be processed on first creation of activity
    if (savedInstanceState == null) {
      processNotificationIntent(intent)
      processVergerIntent(intent)
      // Verger ne propose pas le partage externe vers des chats privés arbitraires.
    }
    if (ChatController.appPrefs.privacyProtectScreen.get()) {
      Log.d(TAG, "onCreate: set FLAG_SECURE")
      window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
      )
    }
    enableEdgeToEdge()
    setContent {
      Column(Modifier.fillMaxSize()) {
        TextButton(
          onClick = { finish() },
          modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) { Text("Retour aux outils du Verger") }
        val groupPrefs = remember { getSharedPreferences("verger-group", MODE_PRIVATE) }
        val boundGroup = remember { mutableStateOf(groupPrefs.getLong(groupPreferenceKey(), -1).takeIf { it > 0 }) }
        Box(Modifier.weight(1f)) {
          AppScreen(
            vergerGroupOnly = true,
            vergerGroupId = boundGroup.value,
            vergerCanCreateGroup = mayCreateAssociationGroup,
            onVergerGroupSelected = { selected ->
              if (boundGroup.value == null && selected > 0) {
                groupPrefs.edit().putLong(groupPreferenceKey(), selected).apply()
                boundGroup.value = selected
              }
            }
          )
        }
      }
    }
    SimplexApp.context.schedulePeriodicServiceRestartWorker()
    SimplexApp.context.schedulePeriodicWakeUp()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putString("verifiedChatOrganizationId", verifiedChatOrganizationId)
    outState.putBoolean("mayCreateAssociationGroup", mayCreateAssociationGroup)
    super.onSaveInstanceState(outState)
  }

  /** Les liens vers des contacts ou d'autres groupes ne doivent pas ouvrir
   * un second espace de messagerie dans le Verger. */
  private fun processVergerIntent(intent: Intent?) {
    if (intent?.action == Intent.ACTION_VIEW) {
      val bound = getSharedPreferences("verger-group", MODE_PRIVATE).getLong(groupPreferenceKey(), -1) > 0
      if (bound || !VergerGroupPolicy.isGroupInvitation(intent.data?.toString())) {
        Toast.makeText(this, "Lien refusé : seul le groupe privé de l'association est accessible", Toast.LENGTH_LONG).show()
        return
      }
    }
    processIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    processVergerIntent(intent)
  }

  override fun onResume() {
    super.onResume()
    AppLock.recheckAuthState()
  }

  override fun onPause() {
    super.onPause()
    /**
     * When new activity is created after a click on notification, the old one receives onPause before
     * recreation but receives onStop after recreation. So using both (onPause and onStop) to prevent
     * unwanted multiple auth dialogs from [runAuthenticate]
     * */
    AppLock.appWasHidden()
  }

  override fun onStop() {
    super.onStop()
    VideoPlayerHolder.stopAll()
    AppLock.appWasHidden()
  }

  override fun onBackPressed() {
    // At the root of the internal messenger, return to the existing authenticated VergerActivity.
    if (chatModel.chatId.value == null &&
        !onBackPressedDispatcher.hasEnabledCallbacks() &&
        ModalManager.start.openModalCount() == 0) {
      finish()
      return
    }
    val canFinishActivity = (
        onBackPressedDispatcher.hasEnabledCallbacks() // Has something to do in a backstack
            || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R // Android 11 or above
            || isTaskRoot // there are still other tasks after we reach the main (home) activity
        ) && SimplexApp.context.chatModel.sharedContent.value !is SharedContent.Forward
    if (canFinishActivity) {
      // https://medium.com/mobile-app-development-publication/the-risk-of-android-strandhogg-security-issue-and-how-it-can-be-mitigated-80d2ddb4af06
      super.onBackPressed()
    }

    if (!onBackPressedDispatcher.hasEnabledCallbacks() && ChatController.appPrefs.performLA.get()) {
      // When pressed Back and there is no one wants to process the back event, clear auth state to force re-auth on launch
      AppLock.clearAuthState()
      AppLock.laFailed.value = true
    }
    if (!onBackPressedDispatcher.hasEnabledCallbacks()) {
      val sharedContent = chatModel.sharedContent.value
      // Drop shared content
      chatModel.sharedContent.value = null
      if (sharedContent is SharedContent.Forward) {
        chatModel.chatId.value = sharedContent.fromChatInfo.id
      }
      if (canFinishActivity) {
        finish()
      }
    }
  }
}

fun processNotificationIntent(intent: Intent?) {
  val userId = getUserIdFromIntent(intent)
  when (intent?.action) {
    NtfManager.OpenChatAction -> {
      val chatId = intent.getStringExtra("chatId")
      Log.d(TAG, "processNotificationIntent: OpenChatAction $chatId")
      if (chatId != null) {
        ntfManager.openChatAction(userId, chatId)
      }
    }
    NtfManager.ShowChatsAction -> {
      Log.d(TAG, "processNotificationIntent: ShowChatsAction")
      ntfManager.showChatsAction(userId)
    }
    NtfManager.AcceptCallAction -> {
      val chatId = intent.getStringExtra("chatId")
      if (chatId == null || chatId == "") return
      Log.d(TAG, "processNotificationIntent: AcceptCallAction $chatId")
      ntfManager.acceptCallAction(chatId)
    }
  }
}

fun processIntent(intent: Intent?) {
  when (intent?.action) {
    "android.intent.action.VIEW" -> {
      val uri = intent.data
      if (uri != null) {
        chatModel.appOpenUrl.value = null to uri.toString()
      } else {
        AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_parsing_uri_title), generalGetString(MR.strings.error_parsing_uri_desc))
      }
    }
  }
}

fun processExternalIntent(intent: Intent?) {
  when (intent?.action) {
    Intent.ACTION_SEND -> {
      // Close active chat and show a list of chats
      chatModel.chatId.value = null
      chatModel.clearOverlays.value = true
      when {
        intent.type == "text/plain" -> {
          val text = intent.getStringExtra(Intent.EXTRA_TEXT)
          val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
          if (uri != null) {
            if (uri.scheme != "content") return showWrongUriAlert()
            // Shared file that contains plain text, like `*.log` file
            chatModel.sharedContent.value = SharedContent.File(text ?: "", uri.toURI())
          } else if (text != null) {
            // Shared just a text
            chatModel.sharedContent.value = SharedContent.Text(text)
          }
        }
        isMediaIntent(intent) -> {
          val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
          if (uri != null) {
            if (uri.scheme != "content") return showWrongUriAlert()
            chatModel.sharedContent.value = SharedContent.Media(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "", listOf(uri.toURI()))
          } // All other mime types
        }
        else -> {
          val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
          if (uri != null) {
            if (uri.scheme != "content") return showWrongUriAlert()
            chatModel.sharedContent.value = SharedContent.File(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "", uri.toURI())
          }
        }
      }
    }
    Intent.ACTION_SEND_MULTIPLE -> {
      // Close active chat and show a list of chats
      chatModel.chatId.value = null
      chatModel.clearOverlays.value = true
      Log.e(TAG, "ACTION_SEND_MULTIPLE ${intent.type}")
      when {
        isMediaIntent(intent) -> {
          val uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM) as? List<Uri>
          if (uris != null) {
            if (uris.any { it.scheme != "content" }) return showWrongUriAlert()
            chatModel.sharedContent.value = SharedContent.Media(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "", uris.map { it.toURI() })
          } // All other mime types
        }
        else -> {}
      }
    }
  }
}

fun isMediaIntent(intent: Intent): Boolean =
  intent.type?.startsWith("image/") == true || intent.type?.startsWith("video/") == true

//fun testJson() {
//  val str: String = """
//  """.trimIndent()
//
//  println(json.decodeFromString<APIResult>(str))
//}
