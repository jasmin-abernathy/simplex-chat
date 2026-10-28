# Appels de groupe SimpleX — faisabilité

28 octobre 2026. Proposition de travail, sans prototype ni validation cryptographique. Base : `03ba484f118763bd2a2885dd27a0abcf3552603b`. Les chemins ci-dessous sont relatifs au dépôt officiel, à ce commit.

## Recommandation

Faire valider un appel audio ponctuel à trois participants, puis mesurer quatre et cinq participants. Le premier candidat est un mesh WebRTC avec relations authentifiées entre participants et relais TURN pour préserver les adresses IP. Ne pas étendre directement une clé d'appel individuel à tout un groupe. La vidéo vient après validation des ressources mobiles ; les salons permanents constituent un chantier distinct.

Un appel ponctuel a une invitation, une durée et une liste de participants bornées. Une visioconférence ajoute plusieurs vidéos, sélection des flux et contraintes de débit. Un salon permanent ajoute découverte, présence, modération, admissions répétées, synchronisation après longue absence et risques de corrélation temporelle. Le fait qu'un utilisateur appartienne à un groupe de discussion ne doit pas ouvrir automatiquement son microphone.

## Pile actuelle observée

| Couche | Chemins / éléments examinés | Évolution nécessaire |
|---|---|---|
| Signalisation | `src/Simplex/Chat/Library/Commands.hs` : APISendCallInvitation/Offer/Answer/ExtraInfo ; `Library/Subscriber.hs` : xCallInv, xCallOffer | Les commandes ciblent un contact. Il faut distinguer session collective et liaison individuelle. |
| Types et clés | `src/Simplex/Chat/Call.hs` : CallId, CallState, CallInvitation, callMediaKey | Contexte de session, participant, génération d'appartenance ; aucune confusion avec un appel individuel. |
| Android et desktop | `apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/views/call/CallManager.kt` ; `WebRTC.kt` | L'état actif et la file de commandes sont conçus pour un appel avec un contact. Ajouter une session et plusieurs états de pairs, pas plusieurs appels concurrents non coordonnés. |
| Média WebRTC | `packages/simplex-chat-webrtc/src/call.ts` : activeCall, initializeCall, callCryptoFunction | Plusieurs PeerConnection, capture micro partagée, lecture de plusieurs flux et nettoyage indépendant. |
| Android | `CallView.android.kt`, `CallActivity.kt`, `CallService.kt` (service décrit dans la spécification) | Intégration WebView conservée, UI participants, permissions, audio focus, notification collective, PiP et consommation. |
| iOS | `apps/ios/Shared/Views/Call/WebRTCClient.swift` : RTCFrameEncryptor/Decryptor | Plusieurs connexions et contexte de clé par flux ; intégration CallKit et cycle de vie à auditer avant mise en œuvre. |
| Desktop | `apps/multiplatform/spec/services/calls.md` | Le client courant décrit des appels dans le navigateur système via localhost. Une interface strictement intégrée nécessite de coordonner le chantier `sh/desktop-native-calls`, non audité ici. |

La signalisation passe par les canaux SimpleX. Le média dispose d'une couche de chiffrement de trames : AES-256-GCM dans le code JS consulté, avec un préfixe de trame conservé en clair. `callMediaKey` dérive une clé depuis X25519 ; la version 2 utilise HKDF avec CallId et le contexte `SimpleXCallMediaKey`, tandis que l'ancienne version a un autre chemin. Ce constat de lecture n'est ni un audit du chiffrement existant ni une preuve d'E2EE collective.

## Mesh ou SFU

Soit n participants et b le débit d'un flux émis, hors surcoûts réseau. Modèle simplifié sans simulcast ni sélection des flux :

| Critère | Mesh | SFU |
|---|---|---|
| Connexions | n(n−1)/2 liaisons entre pairs | Une relation client-serveur par participant, selon implémentation |
| Débit montant client | (n−1)b | Environ b |
| Débit descendant client | (n−1)b | Jusqu'à (n−1)b |
| Serveur | TURN peut relayer tous les échanges ; ce n'est pas gratuit | Entrée nb, sortie jusqu'à n(n−1)b |
| CPU/batterie | Plusieurs flux sortants, charge à mesurer | Encodage réduit possible, plusieurs décodages subsistent |
| Hébergement | TURN existant à dimensionner | Nouveau service, exploitation, quotas, disponibilité et abus |
| Métadonnées | TURN observe connexions et volumes ; P2P révèle les IP aux pairs | SFU observe présence, timing, topologie et sélection des flux |
| Départ | Fermer les liaisons du membre ; vérifier état collectif | Retirer l'accès serveur et renouveler les secrets média |
| Complexité initiale | Moins d'infrastructure ; refonte des états clients nécessaire | Signalisation serveur et chiffrement média supplémentaire indispensables |

À cinq participants, b=50 kbit/s donne 200 kbit/s montants par client en mesh ; b=1 Mbit/s donne 4 Mbit/s. Ce sont des hypothèses de dimensionnement, pas des mesures SimpleX. Pour un SFU à cinq flux de 1 Mbit/s, la sortie théorique de 20 Mbit/s représente environ 9 Go par heure, hors overhead. Coût = volume facturé × tarif de l'hébergeur + calcul et exploitation ; aucun prix fournisseur vérifié.

## Chiffrement et menace serveur

Le modèle de menace inclut un relais malveillant, un participant retiré, un membre qui usurpe un autre membre et un terminal compromis. Un membre légitime peut toujours enregistrer ce qu'il reçoit : aucune révocation ne récupère les médias déjà reçus.

Pour un mesh, conserver des associations média distinctes entre pairs authentifiés, sans secret collectif improvisé. Lier chaque échange à la session et aux identités de groupe vérifiées reste à spécifier. Les connexions directes déjà établies entre membres ne peuvent pas être présumées disponibles dans tous les types de groupes.

Pour un SFU, DTLS-SRTP terminé sur le serveur ne protège pas le contenu contre celui-ci. Le candidat standard est SFrame (RFC 9605) avec gestion de clés E2EE séparée : chiffrement avant envoi au SFU, déchiffrement chez les destinataires, aucune clé au serveur. SFrame ne remplace pas l'admission ni la distribution des clés. Choisir une bibliothèque éprouvée et vérifier ses licences et interfaces sur les trois plateformes avant engagement. La gestion de groupe MLS (RFC 9420) est une alternative à évaluer, pas une dépendance déjà sélectionnée.

Exigences proposées : secrets distincts par émetteur, compteurs sans réutilisation, liaison authentifiée identité/session/époque, nouvelle génération après retrait, admission avec consentement, rejet des paquets d'anciennes sessions, fenêtre anti-rejeu bornée et échec fermé si une clé manque. L'affectation des clés et compteurs doit être revue par des spécialistes. Un simple ratchet connu du membre exclu ne suffit pas à le révoquer : il faut un nouveau secret inaccessible à ce membre. Ne pas prétendre à une résistance post-compromission sans mécanisme et tests dédiés.

## Session, compatibilité et incidents

- Identité : utiliser les identités authentifiées propres au groupe, sans identifiant global ; ne jamais se fier au seul nom affiché. Décider comment vérifier les liens entre membres avant l'appel.
- Admission : invitation liée au groupe, identifiant aléatoire de session, durée de validité, capacités et rôles ; réponse explicite de chaque invité. L'autorité de la liste des participants doit être définie, notamment si l'initiateur disparaît.
- Départ/retrait : état visible partout, fermeture des flux, révocation des accès et rotation avant reprise des émissions. Un membre hors ligne doit se resynchroniser avant de rejoindre.
- Compatibilité : annoncer une capacité collective dédiée. Un ancien client doit afficher une indisponibilité ou ignorer proprement l'événement, jamais participer avec une sécurité dégradée. Champs JSON et versions de protocole à examiner selon le guide amont.
- Réseau : réutiliser les mécanismes de reconnexion individuels de #7711, mais isoler les timers par pair. Une panne de pair ne termine pas automatiquement toute la session. Expiration, déduplication et ordre des invitations sont nécessaires.
- Appels manqués : notifier une session, sans une avalanche de notifications par liaison ; ne pas rouvrir un appel expiré lors de la livraison différée.
- Appels concurrents : prévoir busy/accept/reject et transitions explicites entre session collective et appel individuel ; pas de capture micro concurrente implicite.

## Étapes et critères de sortie

1. Discussion amont : valider portée Android audio trois personnes, relations préalables entre pairs, TURN et règles de liste des membres. Estimation indicative : quelques jours de spécification, hors délai de réponse.
2. Spécification et revue sécurité : messages, états, admission, clés, compatibilité et scénarios malveillants. Prévoir plusieurs semaines de travail et de revue, pas une simple modification UI.
3. Prototype isolé Android : plusieurs semaines probables ; trois participants factices, réseau privé, capture synthétique. Pas d'usage réel tant que le chemin cryptographique n'est pas vérifié. Aucun prototype livré dans ce dossier.
4. Mesures et tests : débit, batterie, température, latence et mémoire à trois puis cinq ; départ/rejoin, perte réseau, changement Wi-Fi/mobile, extinction, deux invitations concurrentes, arrêt initiateur, refus d'admission, retrait, ancien client, mauvaise clé, rejeu et retransmission d'une ancienne invitation.
5. Interopérabilité iOS/desktop puis vidéo : chantier de plusieurs mois possible ; estimations à reprendre après prototype. SFU seulement si les mesures ou l'objectif de taille le justifient. Salons persistants ensuite.

Condition de démonstration E2EE : établir quels endpoints possèdent chaque secret, vérifier l'authentification de la signalisation, prouver qu'une mauvaise clé empêche le décodage, qu'un membre exclu ne déchiffre pas les nouveaux flux et qu'un relais ne reçoit jamais de clé. Une capture réseau chiffrée seule ne suffit pas. Aucun déploiement ni infrastructure publique requis pour cette étude.

Sources normatives : [SFrame, RFC 9605](https://www.rfc-editor.org/rfc/rfc9605.html), [MLS, RFC 9420](https://www.rfc-editor.org/rfc/rfc9420.html). Questions amont : mesh acceptable ? appels réservés aux membres déjà reliés ? autorité de session ? politique TURN ? calendrier du desktop natif ? bibliothèque cryptographique et revue de sécurité disponibles ?
