# SimpleX Chat ↔ Resilience Vault — lot initial et relais de développement

Date : 28 octobre 2026  
Dépôts :
- SimpleX : https://github.com/jasmin-abernathy/simplex-chat
- Resilience Vault : https://github.com/jasmin-abernathy/resilience-vault
- RV, intégration préparatoire : https://github.com/jasmin-abernathy/resilience-vault/pull/35

## Finalité

Permettre à une personne de conserver une copie chiffrée et récupérable de ses données SimpleX hors du téléphone, puis, uniquement sur autorisation explicite, de demander à son client SimpleX modifié de supprimer ses **propres** données locales en cas de besoin.

**Ce lot ne met en place ni sauvegarde distante automatique, ni commande de suppression à distance, ni désinstallation.** Les fonctions de suppression existantes de SimpleX restent déclenchées par la personne dans l'application.

## État constaté dans le fork

Référence initiale : branche `stable`, commit `79d91e716dbb8aae80327f580aac9a6b42426466` (21 octobre 2026).

- Client Android : `apps/multiplatform/android/`, cible SDK 35 ; interface partagée dans `apps/multiplatform/common/`.
- Export/import : `DatabaseView.kt`, `exportChatArchive`, `importArchive`, `stopChatRunBlockStartChat`.
- Suppression locale : `deleteChatAsync`, `deleteChatDatabaseFilesAndState`, actions distinctes pour les fichiers et médias.
- Le client dispose d'un mécanisme de mot de passe de base et d'une clé d'autodestruction ; auditer la logique de ce dernier avant d'envisager une nouvelle commande destructrice.
- Licence du dépôt : AGPL-3.0 ; les éléments de marque et les obligations de distribution devront être vérifiés avant la publication d'une variante.

## Changements du présent lot

1. **Action « Export for Resilience Vault (manual) »** dans les paramètres de base de données Android ; réutilise l'export du client et le sélecteur de fichier existants.
2. **Blocage explicite avant export** si aucune base locale n'est disponible, si l'état du chiffrement est inconnu, si la base n'est pas chiffrée, si la phrase secrète de base est la valeur aléatoire initiale, ou si un hôte distant est connecté.
3. **Export strict** : si la création de l'archive signale des erreurs, le ZIP incomplet n'est pas proposé au sélecteur de destination ; tentative d'effacement du fichier temporaire et indication explicite en cas d'échec. Le flux d'export habituel reste inchangé.
4. **Libellés en français et en anglais** et tests unitaires pour les conditions d'admission.
5. Aucun nouvel `intent-filter`, service, récepteur, permission de stockage ou accès aux données d'une autre application.

## Chaîne de confiance à implémenter dans les lots suivants

### A. Sauvegarde réellement récupérable

- Préparer une API de transfert locale depuis le fork SimpleX vers RV, en ne donnant qu'une capacité temporaire et à usage unique sur une archive créée depuis une base à l'arrêt.
- Préférer le sélecteur documentaire Android/URI avec permission de lecture temporaire plutôt qu'un stockage clair public. Aucun provider exposant toute la base ; aucune permission globale.
- Faire chiffrer l'archive **avant** tout réseau par le pipeline cryptographique audité de RV. Le stockage distant n'a jamais de clé de déchiffrement.
- Finaliser la sauvegarde avec manifeste authentifié et contrôle d'intégrité ; tester une restauration complète sur une deuxième installation, profils et médias inclus, avec la phrase secrète de la base SimpleX.
- La copie de récupération doit **survivre à l'effacement du coffre RV** : compte, espace de stockage, clés et politique de rétention explicitement séparés du `DELETE-only` RV.
- Ne jamais afficher « sauvegarde protégée » sur la seule présence d'un ZIP, d'un acquittement réseau ou d'une signature ZIP. Il faut une preuve de restauration testée et d'identité de génération.

### B. Commande d'effacement coopérative

- Réutiliser les chemins internes de suppression de SimpleX après une revue de leur complétude (base chat/agent, WAL, archives temporaires, médias, miniatures, clés locales, notifications, tâches en cours).
- Concevoir une autorisation locale explicite, temporaire et révocable ; toute commande doit être liée à l'installation, à un identifiant aléatoire, à l'opération exacte, à une expiration fiable et à un compteur/nonce anti-rejeu conservé durablement.
- Authentifier le canal inter-applications **sans** faire confiance au seul nom de paquet, au numéro de téléphone, au texte d'un message ou à un lien URL. Vérifier identité de signature de l'application partenaire, capacité et possession de la clé attendue ; valider sur Android réel.
- Une commande ne peut pas s'exécuter si une sauvegarde complète, vérifiée et indépendamment récupérable n'est pas présente **pour la génération exacte** demandée ; l'utilisateur doit pouvoir choisir une politique sans sauvegarde à part, après avertissement et consentement explicites.
- Écrire une intention durable avant une opération destructrice ; traiter interruption, processus tué, reboot, double commande et résultat partiel, sans réinitialiser une base effacée par inadvertance.
- Ne pas promettre effacement flash physique, disparition de copies chez les correspondants, suppression sur les autres appareils ni désinstallation Android silencieuse.
- Ne pas accepter par défaut une commande d'effacement sur le réseau SimpleX comme authentification suffisante.

### C. Réinstallation et identité

- Avant de modifier `applicationId` pour installer la variante à côté du client officiel, auditer autorités des providers, schémas de liens, sélecteurs, notifications, nom de l'application, sauvegardes et configuration de signature.
- Ne jamais installer la variante par-dessus l'application officielle avec une signature différente, ni importer automatiquement la même base dans deux clients actifs.
- Définir le chemin de mise à jour de sécurité depuis l'amont (fusion régulière des correctifs), les tests de non-régression et la licence/identité visuelle de la variante.

## Vérification du lot actuel

Tests de politique attendus :
- état sécurisé admissible ;
- base absente, chiffrement inconnu/non actif, phrase secrète non récupérable, hôte distant connecté refusés ;
- zéro erreur d'archive autorisé ; toute erreur interdit le transfert ; compteur négatif rejeté.

Commandes indicatives à effectuer dans un clone avec les dépendances complètes :

```bash
cd apps/multiplatform
./gradlew :common:desktopTest
./gradlew :android:assembleFossDebug
```

Le projet utilise une chaîne Android/native complexe. Un build local et des essais sur téléphone doivent précéder la fusion. Ne pas publier d'APK de sécurité sur la seule base de tests unitaires.

## Liste de reprise priorisée

- [ ] Compiler et exécuter les tests sur la branche d'intégration, corriger les écarts de ressources/localisations.
- [ ] Tester l'export strict avec des fichiers/médias manquants et après arrêt/reprise du chat.
- [ ] Vérifier l'absence de ZIP temporaire résiduel en cas d'annulation, de manque d'espace et de crash.
- [ ] Faire accepter explicitement le ZIP par RV sans passer par un dossier public et mesurer le flux sur gros volumes.
- [ ] Finaliser et tester le coffre distant séparé, puis restaurer sur un second appareil.
- [ ] Auditer le mécanisme d'autodestruction existant et le chemin `deleteChatAsync` ; définir le protocole d'effacement inter-applications, écrire son modèle d'autorisation et ses tests adversariaux.
- [ ] Activer uniquement après revue indépendante et validations physiques.

## Limites importantes

L'export manuel est une première brique, pas une sauvegarde opérationnelle. L'archive exportée vers un emplacement choisi reste sous la responsabilité de l'utilisateur jusqu'à son chiffrement vérifié. Le dispositif ne protège pas contre la compromission du système Android ou contre les données déjà divulguées.
