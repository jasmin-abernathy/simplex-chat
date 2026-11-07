# Verger + SimpleX : prototype Android intégré

Une seule application Android (8+, arm64), identifiant `chat.simplex.app.vergertest`. Les outils Verger sont embarqués hors ligne dans une WebView locale ; les discussions utilisent les écrans et le moteur natifs SimpleX du même APK. Aucun lancement d'une autre application pour accéder au chat.

## Interface française et accueil Verger

La messagerie utilise les traductions françaises déjà présentes dans SimpleX (ressources MR/fr). Au premier lancement du chat, le réglage de langue est forcé en français. Le premier écran du parcours Verger n’est plus l’accueil natif de SimpleX : il présente « Verger Associations — La messagerie de votre association » et propose de créer un profil. Le parcours de migration conserve son écran d’origine pour éviter toute interruption. Verger est maintenant l’écran d’accueil Android. L’interface métier reste en français ; la messagerie est dans le même APK, pas dans une application externe.

La sélection et la sauvegarde des fichiers JSON / Markdown / ICS utilisent les sélecteurs Android. Les liens externes sont ouverts dans le navigateur, pas dans la WebView contenant le pont natif.

## Essayer

Télécharger l'artefact `verger-simplex-prototype` de l'exécution GitHub Actions, décompresser et installer `verger-simplex-prototype.apk`. Créer un profil de messagerie, utiliser « Retour aux outils du Verger », créer/ouvrir une association puis « Discussion ». « Ouvrir les discussions » revient au chat interne. Pour échanger, créer un contact ou groupe depuis SimpleX et inviter un compte de test. Aucun message n'est envoyé automatiquement.

## Ce qui reste à valider sur téléphone

Installation, premier démarrage, aller-retour Verger/chat, conservation des données après fermeture, échange aller-retour entre deux comptes, réception en arrière-plan et clavier. La compilation et les tests automatiques ne valident pas ces parcours matériels.

## Discussion en groupe fermé (interface dédiée)

Le Verger n'expose plus la liste classique SimpleX avec les boutons « nouveau contact », « nouveau groupe » ou annuaire.
Les membres rejoignent un groupe privé sur invitation et l'associent explicitement à ce Verger sur leur appareil.
Après association, le sélecteur de groupe et le champ de nouveaux liens disparaissent ; seule la discussion associée est affichée.
Les liens externes qui ne désignent pas une invitation à un groupe SimpleX sont refusés par l'activité Android.
Le chiffrement de bout en bout des messages reste celui de SimpleX ; les données métier Automerge ne deviennent pas chiffrées par ce changement.
Le contrôle de qui peut inviter et révoquer repose **toujours sur les rôles et les réglages du groupe SimpleX**, pas sur la simple disparition des boutons.
Le lien entre groupe et association est un paramètre local sur cet appareil ; la révocation Verger/Automerge reste un chantier séparé.
Le groupe se prépare au préalable côté responsables, via un client SimpleX standard : aucune création de groupe n'est proposée dans l'APK Verger.
Les liens d'invitation de groupe sur `simplex.chat`, `smpN.simplex.im` et `smpN.simplexonflux.com` sont reconnus ; les liens de contact individuel sont refusés.
Un lien de groupe permanent partagé publiquement ne suffit **pas** à garantir un groupe fermé : les responsables doivent préférer les invitations individuelles, limiter les droits d'invitation aux administrateurs et retirer/régénérer les liens de groupe en cas de diffusion. Le pilote ne vérifie pas automatiquement ces réglages ni l'identité des membres.
Cette interface ne constitue pas une interdiction protocolaire de créer des groupes avec un autre client SimpleX.

## Limites explicites

- Les profils/groupes SimpleX ne sont pas encore associés automatiquement aux adhérents/associations.
- Le chiffrement SimpleX concerne les conversations. Il ne chiffre pas automatiquement les données Verger ; le verrouillage SimpleX ne protège pas encore l'écran Verger.
- Import/export de fichiers et parcours externes de la PWA restent à adapter à la WebView.
- Pour les consultations distantes, autoriser explicitement `https://appassets.androidplatform.net` dans `VERGER_NATIVE_ORIGINS` du serveur ; aucun serveur n'est modifié par ce prototype.
- APK de test signé avec une clé debug, sans garantie de continuité de signature. Ne pas utiliser pour des données sensibles ou une diffusion stable.

## Construction

Workflow `.github/workflows/verger-prototype-apk.yml`, volontairement limité à la branche prototype et au déclenchement manuel. Source Verger épinglée. Le noyau natif provient du build réussi 40937013106 du même fork ; le workflow vérifie que ses sources natives sont identiques. Cet artefact expire le 12 novembre 2026 : ensuite reconstruire avec `bash scripts/ci/android/build-core.sh /tmp/android-core.tar.gz` puis mettre à jour la référence d'artefact.

Base SimpleX : 79d91e716dbb8aae80327f580aac9a6b42426466, sans les expérimentations Vault/appels de groupe. Licences amont conservées. La passerelle JavaScript expose uniquement la navigation interne, pas les messages ni les clés. Seul le document local embarqué peut naviguer dans cette WebView.

## Serveur de vérification pour le test ALDEBARAN

Cette APK de test embarque désormais l'adresse de l'API pilote `https://app.lepotager.org/verger-api/api/v1` (sans clé ni secret). Il n'est pas nécessaire de saisir cette URL lors de la configuration initiale. La création d'une association est pour l'instant autorisée uniquement pour `asso-aldebaran.fr`. Les deux méthodes de validation sont proposées : TXT DNS ou fichier `https://asso-aldebaran.fr/verger-verification.txt` à déposer à la racine publique du site. Le nom de l'association, le courriel du responsable et le mot de passe seront saisis par la personne qui réalise le test.

Ce serveur n'active **pas** encore la synchronisation Automerge multi-appareils ni le provisionnement automatique du groupe SimpleX. L'APK de développement n'est pas une version publique de production. L'URL du serveur demeure personnalisable dans les réglages locaux du pilote.

## Présentation de la messagerie

L'écran initial de configuration est propre à Verger, entièrement en français et sans la marque SimpleX. Les deux messages de l'écran de groupe fermé qui nommaient SimpleX ont été remplacés par des explications fonctionnelles. Le moteur de messagerie, les invitations compatibles, le protocole et les protections natives ne sont pas remplacés. La transparence sur les opérateurs de serveurs, les licences et les crédits reste obligatoire et ne doit pas être supprimée.

**Limite :** la création de profil et les écrans avancés de réseau, de confidentialité et de discussion sont encore des composants issus du client natif sous-jacent. Ce changement ne constitue pas une refonte visuelle complète de la messagerie ; des libellés anglais ou des mentions techniques peuvent donc subsister en dehors de l'écran d'accueil Verger. Leur correction nécessite un audit et une personnalisation de ces composants, sans supprimer les choix de sécurité nécessaires.

## Palette commune et sobre

La messagerie Verger utilise une palette identique pour toutes les associations : vert profond, gris doux, blanc cassé en clair ; vert sauge et gris anthracite en sombre. Les bulles envoyées et reçues restent distinctes. Le mode clair/sombre du téléphone est respecté, sans personnalisation selon l'association. La portée est limitée aux vues Verger, y compris les écrans de discussion et les variantes des couleurs de conversation. Les licences, crédits et informations sur les serveurs demeurent accessibles.

Le sélecteur Android accepte désormais explicitement les exports `text/plain` nécessaires à la méthode de validation du domaine par fichier (`verger-verification.txt`), tout en conservant la liste fermée des autres formats exportables.

## Fichier de vérification : destination Android

Sur Android 10 et versions supérieures, le bouton « Télécharger verger-verification.txt » utilise MediaStore et dépose le fichier directement dans **Téléchargements**, sans demander de choisir un dossier. Un message confirme l’écriture réussie. Le contenu est vérifié et doit correspondre à un code de vérification temporaire. Sur Android 8 et 9, le sélecteur « Enregistrer sous » est conservé en repli. Les autres exports de l'application continuent à utiliser le sélecteur Android. Si un fichier du même nom existe déjà, vérifier le nom effectivement attribué par Android avant de le copier sur l’hébergement.

## Entrée par responsabilité et téléchargement sans sélection de dossier

Le lancement de Verger commence par trois choix **dans cet ordre** : Bénévoles, Bureau / fondateurs, Présidence / coprésidence. Les deux premiers ouvrent la connexion, un formulaire de demande d'accès, ou l'activation d'une invitation. Le troisième permet une inscription fondée sur la preuve du domaine ou une connexion existante. Les rôles serveur, et non les choix visuels, décident des droits : membre, facilitateur, administrateur. Les demandes sont examinées dans l'espace admin et peuvent aboutir à un code d'invitation à transmettre manuellement. Pas de notification automatique. Le partage des données métier entre appareils reste à sécuriser et synchroniser avant production.

Sur Android 10 et versions ultérieures, le code de vérification de domaine est enregistré dans Téléchargements (MediaStore, sans sélecteur de dossier). Les autres exports continuent à employer le sélecteur système. L'APK contient désormais le parcours de rôles et les API de demandes d'accès, en plus de la correction Android.

## Messagerie : présidence et retour Android

L'ouverture du chat est déclenchée depuis la PWA avec un jeton de session natif déjà présent en mémoire. L'activité Android vérifie auprès de `https://app.lepotager.org/verger-api/api/v1/session` le rôle et l'association, puis transmet **une seule fois, uniquement en mémoire** une autorisation de création au moteur de messagerie. Aucun rôle ne vient des extras d'un Intent non fiable. Le groupe est désormais associé à l'identifiant de l'association (et pas à un groupe global sur tout le téléphone). Seule la présidence peut voir le bouton de création natif ; les bénévoles et membres du bureau reçoivent la méthode d'invitation.

La WebView Verger n'est plus fermée à l'ouverture de la messagerie, ce qui conserve sa session locale. Le bouton « Retour aux outils » ferme la messagerie, il ne recrée plus une nouvelle instance de la PWA. Un retour Android à la racine du chat revient à la WebView existante. Si le processus Android est détruit, une nouvelle connexion Verger peut être nécessaire : le jeton n'est volontairement pas persisté dans les préférences.

## Retour vers l'accueil connecté

Après ouverture de la messagerie, la WebView Verger est conservée dans la pile Android avec le panneau « Accueil » affiché et la session PWA en mémoire. Une pression Retour à la racine de la messagerie, ou le bouton « Retour aux outils du Verger », retrouve donc l'accueil déjà connecté (et non le choix des rôles). Le parcours de création du groupe privé s'affiche directement à la première ouverture pour une présidence authentifiée, une fois le profil chiffré créé. La couleur de l'association est également passée au thème natif ; les nuances restent sobres et contrastées.
