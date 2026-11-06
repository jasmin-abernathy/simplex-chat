# Verger + SimpleX : prototype Android intégré

Une seule application Android (8+, arm64), identifiant `chat.simplex.app.vergertest`. Les outils Verger sont embarqués hors ligne dans une WebView locale ; les discussions utilisent les écrans et le moteur natifs SimpleX du même APK. Aucun lancement d'une autre application pour accéder au chat.

## Interface française et accueil Verger

La messagerie utilise les traductions françaises déjà présentes dans SimpleX (ressources MR/fr). Au premier lancement du chat, le réglage de langue est initialisé en français, puis tout choix utilisateur ultérieur est conservé. Verger est maintenant l’écran d’accueil Android. L’interface métier reste en français ; la messagerie est dans le même APK, pas dans une application externe.

La sélection et la sauvegarde des fichiers JSON / Markdown / ICS utilisent les sélecteurs Android. Les liens externes sont ouverts dans le navigateur, pas dans la WebView contenant le pont natif.

## Essayer

Télécharger l'artefact `verger-simplex-prototype` de l'exécution GitHub Actions, décompresser et installer `verger-simplex-prototype.apk`. Créer le profil SimpleX, utiliser « Outils de l’association · Verger », créer/ouvrir une association puis « Discussion ». « Ouvrir les discussions » revient au chat interne. Pour échanger, créer un contact ou groupe depuis SimpleX et inviter un compte de test. Aucun message n'est envoyé automatiquement.

## Ce qui reste à valider sur téléphone

Installation, premier démarrage, aller-retour Verger/chat, conservation des données après fermeture, échange aller-retour entre deux comptes, réception en arrière-plan et clavier. La compilation et les tests automatiques ne valident pas ces parcours matériels.

## Limites explicites

- Les profils/groupes SimpleX ne sont pas encore associés automatiquement aux adhérents/associations.
- Le chiffrement SimpleX concerne les conversations. Il ne chiffre pas automatiquement les données Verger ; le verrouillage SimpleX ne protège pas encore l'écran Verger.
- Import/export de fichiers et parcours externes de la PWA restent à adapter à la WebView.
- Pour les consultations distantes, autoriser explicitement `https://appassets.androidplatform.net` dans `VERGER_NATIVE_ORIGINS` du serveur ; aucun serveur n'est modifié par ce prototype.
- APK de test signé avec une clé debug, sans garantie de continuité de signature. Ne pas utiliser pour des données sensibles ou une diffusion stable.

## Construction

Workflow `.github/workflows/verger-prototype-apk.yml`, volontairement limité à la branche prototype et au déclenchement manuel. Source Verger épinglée. Le noyau natif provient du build réussi 40937013106 du même fork ; le workflow vérifie que ses sources natives sont identiques. Cet artefact expire le 12 novembre 2026 : ensuite reconstruire avec `bash scripts/ci/android/build-core.sh /tmp/android-core.tar.gz` puis mettre à jour la référence d'artefact.

Base SimpleX : 79d91e716dbb8aae80327f580aac9a6b42426466, sans les expérimentations Vault/appels de groupe. Licences amont conservées. La passerelle JavaScript expose uniquement la navigation interne, pas les messages ni les clés. Seul le document local embarqué peut naviguer dans cette WebView.
