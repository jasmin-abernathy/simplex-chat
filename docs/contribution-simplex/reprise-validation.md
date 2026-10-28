# Reprise de validation — 28 octobre 2026

## Résultat vérifié

Le run [40672950256](https://github.com/jasmin-abernathy/simplex-chat/actions/runs/40672950256), vert, utilise `stable` au commit `ad04cc122ea8d204462ea7e1b6a75f2acb54a54c`. Son unique job est `select-feature-branch`. Ses logs demandent de choisir la branche de développement. Il ne compile rien, n'exécute pas `Android app in the emulator` et ne contient aucun artifact, notamment aucun `android-app`. Aucun avertissement de compilation ou résultat de test Android n'existe dans ce run.

## Contribution Android

[PR brouillon #2](https://github.com/jasmin-abernathy/simplex-chat/pull/2), exclusivement dans le fork. Head : `android/call-proximity-setting`, commit `23543c5f4e7f295b4389d9a0a5a20d3135dfce54`. Base de revue : `review/base-7507`, copie exacte du master amont initial `03ba484f118763bd2a2885dd27a0abcf3552603b`. Deux commits, cinq fichiers ; aucun document d'architecture ni changement de workflow dans cette PR.

La seconde correction cible le maintien de l'écran sur la vue réelle de l'appel via LocalView, et restaure sa valeur initiale à la fermeture ou réduction. Elle rectifie une hypothèse du premier compte rendu : `keepScreenOn()` vise MainActivity, tandis que les flags de CallActivity cités précédemment sont utilisés uniquement dans un chemin ancien de gestion du verrouillage. Ils ne suffisent donc pas à justifier le maintien de l'écran dans tous les appels. La nouvelle option contrôle désormais la vue d'appel explicitement. Le comportement par défaut reste inchangé.

Le contrôle de proximité ne dépend pas du périphérique audio : option active, aucune acquisition du verrou, que la route soit écouteur, haut-parleur ou Bluetooth. Le routage Android 12+ et le chemin SCO des versions antérieures ont été lus et ne sont pas modifiés. Cette lecture ne prouve pas le comportement matériel.

Contrôles statiques : `git diff --check` réussi ; XML base/fr valides, une seule occurrence de chaque nouvelle ressource. Aucun test fonctionnel du capteur exécuté.

## CI déclenchée par la PR

Le [workflow build 40673573479](https://github.com/jasmin-abernathy/simplex-chat/actions/runs/40673573479) a démarré sur le bon head. Il était en cours lors de cette note ; ne pas interpréter son démarrage comme un succès. Il est distinct de `android-emu` et n'apporte pas à lui seul un APK ni des essais du capteur.

Le [contrôle CLA 40673573451](https://github.com/jasmin-abernathy/simplex-chat/actions/runs/40673573451) a échoué : jeton requis par la configuration héritée pour écrire les signatures dans `simplex-chat/cla`, absent du fork. Avertissements de cet outil : API Node `punycode`/`url.parse` dépréciées et action ciblant Node 20 exécutée sous Node 24. Ces avertissements ne concernent pas le code Android. Aucune signature effectuée, aucun secret ajouté, aucun contrôle supprimé.

## Lancer la compilation avec émulateur

Dans [Actions / android-emu](https://github.com/jasmin-abernathy/simplex-chat/actions/workflows/android-emu.yml), choisir **Run workflow**, puis **Use workflow from: android/call-proximity-setting**. Laisser les trois options supplémentaires désactivées pour le premier passage. Une relance du run sur stable reprendrait le stub.

Après lancement : vérifier le head SHA, le job `Android app in the emulator`, les étapes `Build app`, `Upload app` et `Run smoke test`, puis télécharger `android-app`. Conserver le SHA-256 du fichier extrait et son commit source. Vérifier aussi `android-smoke-test` : checks.txt, steps.log et app.log. Un APK peut être disponible même si le smoke test échoue ; les deux résultats doivent être rapportés séparément.

Le script smoke actuel contrôle l'onboarding, un groupe local, les dispositions de la liste de chats et les crashes. Il n'appelle pas un correspondant, n'active pas notre réglage et ne valide ni proximité ni Bluetooth. Aucun téléphone physique ni périphérique Bluetooth n'est accessible dans cet environnement.

## Essais physiques indispensables

Avec deux comptes fictifs et l'APK de debug identifié :

1. Régler un délai de veille court. Option désactivée : vérifier le comportement de référence. Activer l'option et redémarrer pour contrôler sa persistance.
2. Pendant un appel audio, couvrir puis découvrir le capteur, attendre au-delà du délai de veille : écran visible avec l'option active, contrôles utilisables. Répéter sur écouteur, haut-parleur et casque Bluetooth ; vérifier voix entrante et sortante.
3. Basculer entre routes, déconnecter/reconnecter le casque, passer audio/vidéo. Refaire au moins sur Android 12+ ; ajouter Android 11 ou antérieur pour le chemin SCO si disponible.
4. Réduire/PiP, revenir, verrouiller manuellement, interrompre, terminer depuis notification, terminer avant une seconde et lancer un nouvel appel. Contrôler la libération du tag `:proximityLock` avec dumpsys power, sans publier le dump brut.
5. Tester TalkBack et police agrandie. Enregistrer appareil, version Android/WebView, casque, commit/APK et résultat pour chaque scénario. Un essai impossible est « non exécuté », jamais « réussi ».

## Premier lot réaliste pour les appels collectifs

La [note d'architecture](../appels-groupe-faisabilite.md) reste une proposition, non un prototype. Premier lot à faire approuver dans #5138 : **appel audio ponctuel à trois participants sur Android**, avec comptes de test déjà reliés, invitation explicite et UI minimale de participants. Pas de salon permanent, vidéo, SFU ou interopérabilité annoncée dans ce lot.

Travaux nécessaires : spécifier session collective et états par pair ; définir qui peut inviter et retirer, comment la liste des participants est authentifiée et resynchronisée ; négocier les capacités sans faire rejoindre silencieusement les anciens clients ; préparer un mesh de connexions avec capture micro partagée et relais selon la politique de confidentialité. Conserver des associations média authentifiées distinctes, sans réutiliser arbitrairement une clé individuelle pour tout le groupe. Revue de sécurité préalable à toute utilisation réelle.

Critères de sortie : trois participants s'entendent dans le client SimpleX ; refus d'invitation, départ, rejoin, perte/changement de réseau, fin de l'initiateur et appel concurrent gérés ; participants visibles et cohérents ; vérification du chemin des clés et du chiffrement, rejet d'une mauvaise clé et d'un membre non admis ; mesures débit, latence, mémoire et consommation. Les flux doivent rester fictifs jusqu'à cette validation.

Ensuite : interopérabilité iOS/desktop, audio à quatre/cinq, puis vidéo avec mesures mobiles. Évaluer un SFU seulement si ces mesures le justifient, avec chiffrement des médias réellement de bout en bout et gestion de clés revue. #2398, salons vocaux persistants, vient après : admission durable, présence, modération, rotation et resynchronisation constituent un autre lot. Aucun protocole collectif implémenté ni publication amont effectuée.
