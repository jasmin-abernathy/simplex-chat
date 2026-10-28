# État amont — 28 octobre 2026

Base consultée : `master`, commit `03ba484f118763bd2a2885dd27a0abcf3552603b` du dépôt [simplex-chat/simplex-chat](https://github.com/simplex-chat/simplex-chat/tree/03ba484f118763bd2a2885dd27a0abcf3552603b). La branche par défaut est `stable`, mais le guide demande de cibler `master` pour les contributions.

| Sujet | Faits vérifiés | Conclusion |
|---|---|---|
| [#7507](https://github.com/simplex-chat/simplex-chat/issues/7507) | Ouvert, non assigné, aucun commentaire ; recherches de PR « proximity » et « 7507 » sans résultat. Le verrou de proximité est activé dans `ActiveCallView` sans préférence. | Contribution pertinente ; patch local préparé, pas encore compilé. |
| [#5138](https://github.com/simplex-chat/simplex-chat/issues/5138) | Ouvert, non assigné ; description et cinq commentaires lus. Discussion sur la différence avec #2398 et suggestion de Mumble Web par un participant. | Aucun accord architectural des mainteneurs dans ce fil. |
| [#2398](https://github.com/simplex-chat/simplex-chat/issues/2398) | Ouvert, non assigné ; corps vide, un commentaire lu proposant Mumble Web. | Demande de salons vocaux ; aucune spécification validée dans ce fil. |
| [#6595](https://github.com/simplex-chat/simplex-chat/issues/6595) | Ouvert ; description et deux commentaires lus. | Le statut du ticket seul ne reflète pas l'avancement du code. |
| [#7711](https://github.com/simplex-chat/simplex-chat/pull/7711) | Fermée et fusionnée ; commit de fusion `2ea1ec0d50eb6ead8188348ca773ee7ad2c76b8f`. Aucune discussion retournée. Le code courant contient l'état Reconnecting et la signalisation de redémarrage ICE. | Ne pas refaire la reconnexion. Présence dans master vérifiée ; diffusion dans une version stable non établie. |

La recherche des branches contenant « call » retourne `av/multiplatform-call-logs` et `sh/desktop-native-calls`. Leur contenu n'a pas été audité : la seconde est une dépendance à examiner avant une proposition desktop. Les recherches ciblées de PR n'établissent pas l'absence exhaustive d'un chantier d'appels de groupe.

## Conventions et fichiers consultés

- [Guide de contribution](https://github.com/simplex-chat/simplex-chat/blob/03ba484f118763bd2a2885dd27a0abcf3552603b/docs/CONTRIBUTING.md) : discuter le plan tôt, PR brouillon vers master, titre avec périmètre, modifications limitées.
- `docs/contributing/CODE.md`, `PROJECT.md`, `scripts/ci/README.md`, `apps/multiplatform/README.md` : structure, sécurité, compilation et tests. Licence racine : AGPL-3.0. Aucune dépendance ajoutée par le patch.
- [CallView.android.kt](https://github.com/simplex-chat/simplex-chat/blob/03ba484f118763bd2a2885dd27a0abcf3552603b/apps/multiplatform/common/src/androidMain/kotlin/chat/simplex/common/views/call/CallView.android.kt) : ActiveCallState, effet de proximité, pont WebView et fin d'appel.
- `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/usersettings/CallSettings.kt` et `model/SimpleXAPI.kt` : affichage et stockage réactif des préférences.
- `apps/multiplatform/android/src/main/java/chat/simplex/app/views/call/CallActivity.kt` : fenêtre d'appel, PiP, verrouillage ; `common/src/commonMain/kotlin/chat/simplex/common/views/call/CallManager.kt` : acceptation et fermeture.
- `apps/multiplatform/spec/services/calls.md`, `src/Simplex/Chat/Call.hs`, portions appels de `Library/Commands.hs` et `Library/Subscriber.hs`, portions initialisation et chiffrement de `packages/simplex-chat-webrtc/src/call.ts`, points d'entrée du chiffrement de `apps/ios/Shared/Views/Call/WebRTCClient.swift`.

## Résultat local et limites

Branche : [android/call-proximity-setting](https://github.com/jasmin-abernathy/simplex-chat/tree/android/call-proximity-setting), commit [3faf747e36259734610ea53a857220a398cd7d62](https://github.com/jasmin-abernathy/simplex-chat/commit/3faf747e36259734610ea53a857220a398cd7d62). Le fork personnel a été vérifié comme fork du dépôt officiel et le correctif y est enregistré. Arbre Git distant identique à l'arbre local vérifié (`623c462d2cdbb44ad89613376e9124500c78ee0d`). Aucun autre dépôt personnel modifié. Aucune PR ni commentaire amont publiés.

Les documents sont conservés séparément sur `docs/contribution-review-2026-10-28`. Aucun workflow lancé : la connexion disponible n'expose pas de démarrage de workflow. La compilation distante reste à effectuer.

Le patch ajoute une préférence persistante désactivée par défaut et une ligne Android dans les réglages d'appels. Quand elle est activée, aucun verrou de proximité n'est acquis. Le maintien de l'écran visible repose sur les flags déjà présents dans CallActivity ; le verrouillage manuel et les protections Android restent sous le contrôle du système. La coroutine libère le verrou à l'annulation ; l'état fermé interdit une acquisition tardive. Aucun changement du protocole ni du routage audio.

Contrôles réussis : `git diff --check`, lecture XML des ressources base/fr. Ce ne sont pas des tests Android. Compilation et tests Gradle bloqués avant exécution par le téléchargement de Gradle 8.12 (`java.net.SocketException: Network is unreachable`). JDK 17.0.20, Linux ; adb absent du PATH. Aucun APK produit. Voir `contribution-simplex/validation.md` et `build.log`.

Les tickets secondaires #6263, #5186 et #6561 n'ont pas été investigués. Aucun prototype collectif n'a été commencé : l'admission des membres et la gestion cryptographique demandent une validation amont.
