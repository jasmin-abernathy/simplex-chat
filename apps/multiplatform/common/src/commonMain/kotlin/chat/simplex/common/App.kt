package chat.simplex.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import chat.simplex.common.views.usersettings.SetDeliveryReceiptsView
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.CreateFirstProfile
import chat.simplex.common.views.helpers.SimpleButton
import chat.simplex.common.views.SplashView
import chat.simplex.common.views.call.*
import chat.simplex.common.views.chat.ChatView
import chat.simplex.common.views.chatlist.*
import chat.simplex.common.views.database.DatabaseErrorView
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.helpers.ModalManager.Companion.fromEndToStartTransition
import chat.simplex.common.views.helpers.ModalManager.Companion.fromStartToEndTransition
import chat.simplex.common.views.localauth.VerticalDivider
import chat.simplex.common.views.newchat.*
import chat.simplex.common.views.onboarding.*
import chat.simplex.common.views.usersettings.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// Spec: spec/client/navigation.md#AppScreen
@Composable
fun AppScreen(
  vergerGroupOnly: Boolean = false,
  vergerGroupId: Long? = null,
  onVergerGroupSelected: (Long) -> Unit = {},
  vergerCanCreateGroup: Boolean = false,
) {
  AppBarHandler.appBarMaxHeightPx = with(LocalDensity.current) { AppBarHeight.roundToPx() }
  SimpleXTheme(vergerTheme = vergerGroupOnly) {
    Surface(color = MaterialTheme.colors.background, contentColor = LocalContentColor.current) {
      // This padding applies to landscape view only taking care of navigation bar and holes in screen in status bar area
      // (because nav bar and holes located on vertical sides of screen in landscape view)
      val direction = LocalLayoutDirection.current
      val safePadding = WindowInsets.safeDrawing.asPaddingValues()
      val cutout = WindowInsets.displayCutout.asPaddingValues()
      val cutoutStart = cutout.calculateStartPadding(direction)
      val cutoutEnd = cutout.calculateEndPadding(direction)
      val cutoutMax = maxOf(cutoutStart, cutoutEnd)
      val paddingStartUntouched = safePadding.calculateStartPadding(direction)
      val paddingStart = paddingStartUntouched - cutoutStart
      val paddingEndUntouched = safePadding.calculateEndPadding(direction)
      val paddingEnd = paddingEndUntouched - cutoutEnd
      // Such a strange layout is needed because the main content should be covered by solid color in order to hide overflow
      // of some elements that may have negative offset (so, can't use Row {}).
      // To check: go to developer settings of Android, choose Display cutout -> Punch hole, and rotate the phone to landscape, open any chat
      Box {
        val fullscreenGallery = remember { chatModel.fullscreenGalleryVisible }
        Box(Modifier.padding(start = paddingStart + cutoutMax, end = paddingEnd + cutoutMax).consumeWindowInsets(PaddingValues(start = paddingStartUntouched, end = paddingEndUntouched))) {
          Box(Modifier.drawBehind {
            if (fullscreenGallery.value) {
              drawRect(Color.Black,  topLeft = Offset(-(paddingStart + cutoutMax).toPx(), 0f), Size(size.width + (paddingStart + cutoutMax).toPx() + (paddingEnd + cutoutMax).toPx(), size.height))
            }
          }) {
            MainScreen(vergerGroupOnly, vergerGroupId, onVergerGroupSelected, vergerCanCreateGroup)
          }
        }
      }
    }
  }
}

// Spec: spec/client/navigation.md#MainScreen
@Composable
fun MainScreen(
  vergerGroupOnly: Boolean = false,
  vergerGroupId: Long? = null,
  onVergerGroupSelected: (Long) -> Unit = {},
  vergerCanCreateGroup: Boolean = false,
) {
  // Même si un menu SimpleX essaie d'ouvrir un autre fil, le Verger n'affiche
  // que le groupe explicitement associé sur cet appareil.
  LaunchedEffect(vergerGroupOnly, chatModel.sharedContent.value) {
    if (vergerGroupOnly && chatModel.sharedContent.value != null) chatModel.sharedContent.value = null
  }
  LaunchedEffect(vergerGroupOnly, vergerGroupId, chatModel.chatId.value, chatModel.chats.value) {
    if (vergerGroupOnly && chatModel.chatId.value != null) {
      val selectedChat = chatModel.chats.value.firstOrNull { it.chatInfo.id == chatModel.chatId.value }?.chatInfo as? ChatInfo.Group
      if (vergerGroupId == null || selectedChat?.groupInfo?.groupId != vergerGroupId) {
        chatModel.chatId.value = null
      }
    }
  }
  val chatModel = ChatModel
  var showChatDatabaseError by rememberSaveable {
    mutableStateOf(chatModel.chatDbStatus.value != DBMigrationResult.OK && chatModel.chatDbStatus.value != null)
  }
  LaunchedEffect(chatModel.chatDbStatus.value) {
    showChatDatabaseError = chatModel.chatDbStatus.value != DBMigrationResult.OK && chatModel.chatDbStatus.value != null
  }
  var showAdvertiseLAAlert by remember { mutableStateOf(false) }
  LaunchedEffect(showAdvertiseLAAlert) {
    if (
      !chatModel.controller.appPrefs.laNoticeShown.get()
      && !appPrefs.performLA.get()
      && showAdvertiseLAAlert
      && chatModel.controller.appPrefs.onboardingStage.get() == OnboardingStage.OnboardingComplete
      && chatModel.chats.size > 3
      && chatModel.activeCallInvitation.value == null
    ) {
      AppLock.showLANotice(ChatModel.controller.appPrefs.laNoticeShown) }
  }
  LaunchedEffect(chatModel.showAdvertiseLAUnavailableAlert.value) {
    if (chatModel.showAdvertiseLAUnavailableAlert.value) {
      laUnavailableInstructionAlert()
    }
  }
  platform.desktopShowAppUpdateNotice()
  LaunchedEffect(chatModel.clearOverlays.value) {
    if (chatModel.clearOverlays.value) {
      ModalManager.closeAllModalsEverywhere()
      chatModel.clearOverlays.value = false
    }
  }

  @Composable
  fun AuthView() {
    Surface(color = MaterialTheme.colors.background.copy(1f), contentColor = LocalContentColor.current) {
      Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        SimpleButton(
          stringResource(MR.strings.auth_unlock),
          icon = painterResource(MR.images.ic_lock),
          click = {
            AppLock.laFailed.value = false
            AppLock.runAuthenticate()
          }
        )
      }
    }
  }

  Box {
    val unauthorized = remember { derivedStateOf { AppLock.userAuthorized.value != true } }
    val onboarding by remember { chatModel.controller.appPrefs.onboardingStage.state }
    val localUserCreated = chatModel.localUserCreated.value
    var showInitializationView by remember { mutableStateOf(false) }
    when {
      onboarding == OnboardingStage.Step1_SimpleXInfo && chatModel.migrationState.value != null -> {
        // In migration process. Nothing should interrupt it, that's why it's the first branch in when()
        if (appPlatform.isDesktop) DesktopOnboarding(onboarding, chatModel)
        else SimpleXInfo(chatModel, onboarding = true)
      }
      chatModel.dbMigrationInProgress.value -> DefaultProgressView(stringResource(MR.strings.database_migration_in_progress))
      chatModel.chatDbStatus.value == null && showInitializationView -> DefaultProgressView(stringResource(MR.strings.opening_database))
      showChatDatabaseError -> {
        // Prevent showing keyboard on Android when: passcode enabled and database password not saved
        if (!unauthorized.value && chatModel.chatDbStatus.value != null) {
          DatabaseErrorView(chatModel.chatDbStatus, chatModel.controller.appPrefs)
        }
      }
      remember { chatModel.chatDbEncrypted }.value == null || localUserCreated == null -> SplashView()
      onboarding == OnboardingStage.OnboardingComplete -> {
        Box {
          showAdvertiseLAAlert = true
          val userPickerState by rememberSaveable(stateSaver = AnimatedViewState.saver()) { mutableStateOf(MutableStateFlow(if (chatModel.desktopNoUserNoRemote()) AnimatedViewState.VISIBLE else AnimatedViewState.GONE)) }
          KeyChangeEffect(chatModel.desktopNoUserNoRemote) {
            if (chatModel.desktopNoUserNoRemote() && !ModalManager.start.hasModalsOpen()) {
              userPickerState.value = AnimatedViewState.VISIBLE
            }
          }
          if (appPlatform.isAndroid) {
            AndroidWrapInCallLayout {
              AndroidScreen(userPickerState, vergerGroupOnly, vergerGroupId, onVergerGroupSelected, vergerCanCreateGroup)
            }
          } else {
            DesktopScreen(userPickerState)
          }
        }
      }
      else -> {
        if (appPlatform.isDesktop) {
          DesktopOnboarding(onboarding, chatModel)
        } else {
          AnimatedContent(targetState = onboarding,
            transitionSpec = {
              if (targetState > initialState) {
                fromEndToStartTransition()
              } else {
                fromStartToEndTransition()
              }.using(SizeTransform(clip = false))
            }
          ) { state ->
            when (state) {
              OnboardingStage.OnboardingComplete -> {}
              OnboardingStage.Step1_SimpleXInfo -> if (vergerGroupOnly) VergerMessagingWelcome {
                chatModel.controller.appPrefs.onboardingStage.set(OnboardingStage.Step2_CreateProfile)
              } else SimpleXInfo(chatModel, onboarding = true)
              OnboardingStage.Step2_CreateProfile -> CreateFirstProfile(chatModel) {}
              OnboardingStage.LinkAMobile -> LinkAMobile()
              OnboardingStage.Step2_5_SetupDatabasePassphrase -> SetupDatabasePassphrase(chatModel)
              OnboardingStage.Step3_ChooseServerOperators,
              OnboardingStage.Step3_CreateSimpleXAddress,
              OnboardingStage.Step4_SetNotificationsMode -> YourNetworkView(chatModel)
              OnboardingStage.Step4_NetworkCommitments -> OnboardingConditionsView(chatModel)
            }
          }
        }
      }
    }
    if (appPlatform.isAndroid) {
      AndroidWrapInCallLayout {
        ModalManager.fullscreen.showInView()
      }
      SwitchingUsersView()
    }

    if (unauthorized.value && !(chatModel.activeCallViewIsVisible.value && chatModel.showCallView.value)) {
      LaunchedEffect(Unit) {
        // With these constrains when user presses back button while on ChatList, activity destroys and shows auth request
        // while the screen moves to a launcher. Detect it and prevent showing the auth
        if (!(androidIsFinishingMainActivity() && chatModel.controller.appPrefs.laMode.get() == LAMode.SYSTEM)) {
          AppLock.runAuthenticate()
        }
      }
      if (chatModel.controller.appPrefs.performLA.get() && AppLock.laFailed.value) {
        AuthView()
      } else {
        SplashView(true)
        ModalManager.fullscreen.showPasscodeInView()
      }
    } else {
      if (chatModel.showCallView.value) {
        if (appPlatform.isAndroid) {
          LaunchedEffect(Unit) {
            // This if prevents running the activity in the following condition:
            // - the activity already started before and was destroyed by collapsing active call (start audio call, press back button, go to a launcher)
            if (!chatModel.activeCallViewIsCollapsed.value) {
              platform.androidStartCallActivity(false)
            }
          }
        } else {
          ActiveCallView()
        }
      }
      ModalManager.fullscreen.showOneTimePasscodeInView()
      AlertManager.privacySensitive.showInView()
      if (onboarding == OnboardingStage.OnboardingComplete) {
        LaunchedEffect(chatModel.chatRunning.value, chatModel.currentUser.value, chatModel.appOpenUrl.value) {
          val (rhId, url) = chatModel.appOpenUrl.value ?: (null to null)
          if (url != null && chatModel.chatRunning.value == true) {
            chatModel.appOpenUrl.value = null
            connectIfOpenedViaUri(rhId, url, chatModel)
          }
        }
      }
    }
    val invitation = chatModel.activeCallInvitation.value
    if (invitation != null) IncomingCallAlertView(invitation, chatModel)
    AlertManager.shared.showInView()

    LaunchedEffect(Unit) {
      delay(1000)
      if (chatModel.chatDbStatus.value == null) {
        showInitializationView = true
      }
    }
  }

  DisposableEffectOnRotate {
    // When using lock delay = 0 and screen rotates, the app will be locked which is not useful.
    // Let's prolong the unlocked period to 3 sec for screen rotation to take place
    if (chatModel.controller.appPrefs.laLockDelay.get() == 0) {
      AppLock.enteredBackground.value = AppLock.elapsedRealtime() + 3000
    }
  }
}

@Composable
private fun DesktopOnboarding(onboarding: OnboardingStage, chatModel: ChatModel) {
  if (onboarding == OnboardingStage.LinkAMobile) {
    LinkAMobile()
    ModalManager.fullscreen.showInView()
  } else {
    DesktopOnboardingShell(onboarding) {
      when (onboarding) {
        OnboardingStage.Step1_SimpleXInfo -> SimpleXInfo(chatModel, onboarding = true)
        OnboardingStage.Step2_CreateProfile -> CreateFirstProfile(chatModel) {}
        OnboardingStage.Step2_5_SetupDatabasePassphrase -> SetupDatabasePassphrase(chatModel)
        OnboardingStage.Step3_ChooseServerOperators,
        OnboardingStage.Step3_CreateSimpleXAddress,
        OnboardingStage.Step4_SetNotificationsMode -> YourNetworkView(chatModel)
        OnboardingStage.Step4_NetworkCommitments -> OnboardingConditionsView(chatModel)
        else -> {}
      }
    }
  }
}

val ANDROID_CALL_TOP_PADDING = 40.dp

@Composable
fun AndroidWrapInCallLayout(content: @Composable () -> Unit) {
  val call = remember { chatModel.activeCall}.value
  val showCallArea = call != null && call.callState != CallState.WaitCapabilities && call.callState != CallState.InvitationAccepted
  Box {
    Box(Modifier.padding(top = if (showCallArea) ANDROID_CALL_TOP_PADDING else 0.dp)) {
      content()
    }
    if (call != null && showCallArea) {
      ActiveCallInteractiveArea(call)
    }
  }
}

// Spec: spec/client/navigation.md#AndroidScreen
@Composable
fun AndroidScreen(
  userPickerState: MutableStateFlow<AnimatedViewState>,
  vergerGroupOnly: Boolean = false,
  vergerGroupId: Long? = null,
  onVergerGroupSelected: (Long) -> Unit = {},
  vergerCanCreateGroup: Boolean = false,
) {
  BoxWithConstraints {
    val currentChatId = remember { mutableStateOf(chatModel.chatId.value) }
    val offset = remember { Animatable(if (chatModel.chatId.value == null) 0f else maxWidth.value) }
    val cutout = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val direction = LocalLayoutDirection.current
    val hasCutout = cutout.calculateStartPadding(direction) + cutout.calculateEndPadding(direction) > 0.dp
    Box(
      Modifier
        // clipping only for devices with cutout currently visible on sides. It prevents showing chat list with open chat view
        // In order cases it's not needed to use clip
        .then(if (hasCutout) Modifier.clip(RectangleShape) else Modifier)
        .graphicsLayer {
          // minOf thing is needed for devices with holes in screen while the user on ChatView rotates his phone from portrait to landscape
          // because in this case (at least in emulator) maxWidth changes in two steps: big first, smaller on next frame.
          // But offset is remembered already, so this is a better way than dropping a value of offset
          translationX = -minOf(offset.value.dp, maxWidth).toPx()
        }
    ) {
      StartPartOfScreen(userPickerState, vergerGroupOnly, vergerGroupId, onVergerGroupSelected, vergerCanCreateGroup)
    }
    val scope = rememberCoroutineScope()
    val onComposed: suspend (chatId: String?) -> Unit = { chatId ->
      // coroutine, scope and join() because:
      // - it should be run from coroutine to wait until this function finishes
      // - without using scope.launch it throws CancellationException when changing user
      // - join allows to wait until completion
      scope.launch {
        offset.animateTo(
          if (chatId == null) 0f else maxWidth.value,
          chatListAnimationSpec()
        )
      }.join()
    }
    LaunchedEffect(Unit) {
      launch {
        snapshotFlow { chatModel.chatId.value }
          .distinctUntilChanged()
          .collect {
            if (it == null) onComposed(null)
            currentChatId.value = it
          }
      }
    }
    Box(Modifier
      .then(if (hasCutout) Modifier.clip(RectangleShape) else Modifier)
      .graphicsLayer { translationX = maxWidth.toPx() - minOf(offset.value.dp, maxWidth).toPx() }
    ) Box2@{
      currentChatId.value?.let {
        ChatView(chatsCtx = chatModel.chatsContext, currentChatId, onComposed = onComposed)
      }
    }
  }
}

@Composable
fun StartPartOfScreen(
  userPickerState: MutableStateFlow<AnimatedViewState>,
  vergerGroupOnly: Boolean = false,
  vergerGroupId: Long? = null,
  onVergerGroupSelected: (Long) -> Unit = {},
  vergerCanCreateGroup: Boolean = false,
) {
  if (chatModel.setDeliveryReceipts.value) {
    CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler()) {
      SetDeliveryReceiptsView(chatModel)
    }
  } else {
    val stopped = chatModel.chatRunning.value == false
    if (vergerGroupOnly || chatModel.sharedContent.value == null) {
      CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler()) {
        if (vergerGroupOnly) {
          VergerClosedGroupList(vergerGroupId, onVergerGroupSelected, vergerCanCreateGroup)
        } else {
          ChatListView(chatModel, userPickerState, AppLock::setPerformLA, stopped)
        }
      }
    } else {
      // LALAL initial load of view doesn't show blur. Focusing text field shows it
      CompositionLocalProvider(LocalAppBarHandler provides rememberAppBarHandler(keyboardCoversBar = false)) {
        ShareListView(chatModel, stopped)
      }
    }
  }
}


/** Groupe privé : pas d'annuaire public et un seul groupe associé à l'organisation.
 * La création est réservée à la présidence vérifiée par le serveur Verger. */
@Composable
private fun VergerClosedGroupList(groupId: Long?, onGroupSelected: (Long) -> Unit, canCreateGroup: Boolean) {
  val groups = chatModel.chats.value.mapNotNull { it.chatInfo as? ChatInfo.Group }
    .filter { it.groupInfo.membership.memberActive }
  val group = groups.firstOrNull { it.groupInfo.groupId == groupId }
  val invitation = rememberSaveable { mutableStateOf("") }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Text("Messagerie de l'association", style = MaterialTheme.typography.h5)
    Text("Discussions chiffrées de bout en bout. L’accès au groupe dépend d’une invitation de ses responsables.")
    if (groupId != null) {
      if (group != null) {
        Text("Groupe associé : " + group.localDisplayName)
        Button(
          onClick = { chatModel.chatId.value = group.id },
          modifier = Modifier.fillMaxWidth()
        ) { Text("Ouvrir la discussion") }
      } else {
        Text("Le groupe associé n'est plus disponible sur cet appareil. Vérifiez votre profil et votre accès avec un responsable.")
      }
    } else {
      if (canCreateGroup) {
        Text("Vous représentez la présidence de l'association. Créez son groupe privé ici : aucun lien d'invitation n'est nécessaire.")
        Button(
          onClick = {
            ModalManager.start.showCustomModal { close ->
              AddGroupView(
                chatModel,
                chatModel.currentRemoteHost.value,
                close,
                closeAll = { ModalManager.start.closeModals() },
                onGroupCreated = onGroupSelected
              )
            }
          },
          enabled = chatModel.chatRunning.value == true,
          modifier = Modifier.fillMaxWidth()
        ) { Text("Créer le groupe de l'association") }
      }
      if (groups.isNotEmpty()) {
        Text("Choisissez le groupe privé de votre association parmi ceux que vous avez déjà rejoints :")
        groups.forEach { candidate ->
          Button(onClick = {
            onGroupSelected(candidate.groupInfo.groupId)
            chatModel.chatId.value = candidate.id
          }, modifier = Modifier.fillMaxWidth()) {
            Text("Associer : " + candidate.localDisplayName)
          }
        }
      }
      if (!canCreateGroup) {
        Text("Vous n'avez pas encore rejoint votre groupe ? Demandez un lien privé aux responsables, puis collez-le ci-dessous.")
        OutlinedTextField(
        value = invitation.value,
        onValueChange = { invitation.value = it.take(16384) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Lien d'invitation au groupe") },
        singleLine = true
      )
      val validInvitation = VergerGroupPolicy.isGroupInvitation(invitation.value)
      Button(onClick = {
        if (validInvitation) {
          chatModel.appOpenUrl.value = null to invitation.value.trim()
          invitation.value = ""
        }
      }, enabled = validInvitation && chatModel.chatRunning.value == true,
         modifier = Modifier.fillMaxWidth()) { Text("Rejoindre le groupe privé") }
      if (invitation.value.isNotBlank() && !validInvitation) {
        Text("Ce lien ne correspond pas à une invitation de groupe compatible.", color = MaterialTheme.colors.error)
      }
      }
    }
    Text(if (canCreateGroup) "La présidence gère les invitations et les rôles du groupe." else "Seules les personnes habilitées peuvent créer un groupe. Demandez une invitation à votre association.")
  }
}
@Composable
fun CenterPartOfScreen() {
  val currentChatId = remember { ChatModel.chatId }
  LaunchedEffect(Unit) {
    snapshotFlow { currentChatId.value }
      .distinctUntilChanged()
      .collect {
        if (it != null) {
          ModalManager.center.closeModals()
        }
      }
  }
  when (currentChatId.value) {
    null -> {
      if (rememberUpdatedState(ModalManager.center.hasModalsOpen()).value) {
        ModalManager.center.showInView()
      } else if (shouldShowOnboarding()) {
        ConnectOnboardingView()
      } else {
        Box(
          Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
          contentAlignment = Alignment.Center
        ) {
          Text(stringResource(if (chatModel.desktopNoUserNoRemote) MR.strings.no_connected_mobile else MR.strings.no_selected_chat))
        }
      }
    }
    else -> ChatView(chatsCtx = chatModel.chatsContext, currentChatId) {}
  }
}

@Composable
fun EndPartOfScreen() {
  ModalManager.end.showInView()
}

// Spec: spec/client/navigation.md#DesktopScreen
@Composable
fun DesktopScreen(userPickerState: MutableStateFlow<AnimatedViewState>) {
  Box(Modifier.width(DEFAULT_START_MODAL_WIDTH * fontSizeSqrtMultiplier)) {
    StartPartOfScreen(userPickerState)
    tryOrShowError("UserPicker", error = {}) {
      UserPicker(chatModel, userPickerState, setPerformLA = AppLock::setPerformLA)
    }
  }
  Box(Modifier.widthIn(max = DEFAULT_START_MODAL_WIDTH * fontSizeSqrtMultiplier)) {
    ModalManager.start.showInView()
    SwitchingUsersView()
  }
  Row(Modifier.padding(start = DEFAULT_START_MODAL_WIDTH * fontSizeSqrtMultiplier).clipToBounds()) {
    Box(Modifier.widthIn(min = DEFAULT_MIN_CENTER_MODAL_WIDTH).weight(1f)) {
      CenterPartOfScreen()
    }
    if (ModalManager.end.hasModalsOpen()) {
      VerticalDivider()
    }
    Box(Modifier.widthIn(max = DEFAULT_END_MODAL_WIDTH * fontSizeSqrtMultiplier).clipToBounds()) {
      EndPartOfScreen()
    }
  }
  if (userPickerState.collectAsState().value.isVisible() || (ModalManager.start.hasModalsOpen && !ModalManager.center.hasModalsOpen)) {
    Box(
      Modifier
        .fillMaxSize()
        .padding(start = DEFAULT_START_MODAL_WIDTH * fontSizeSqrtMultiplier)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {
          if (chatModel.centerPanelBackgroundClickHandler == null || chatModel.centerPanelBackgroundClickHandler?.invoke() == false) {
            ModalManager.start.closeModals()
            userPickerState.value = AnimatedViewState.HIDING
          }
        })
    )
  }
  VerticalDivider(Modifier.padding(start = DEFAULT_START_MODAL_WIDTH * fontSizeSqrtMultiplier))
  ModalManager.fullscreen.showInView()
}

@Composable
private fun SwitchingUsersView() {
  if (remember { chatModel.switchingUsersAndHosts }.value) {
    Box(
      Modifier.fillMaxSize().clickable(enabled = false, onClick = {}),
      contentAlignment = Alignment.Center
    ) {
      ProgressIndicator()
    }
  }
}

@Composable
private fun ProgressIndicator() {
  CircularProgressIndicator(
    Modifier
      .padding(horizontal = 2.dp)
      .size(30.dp),
    color = MaterialTheme.colors.secondary,
    strokeWidth = 2.5.dp
  )
}
