# Revue de l’export manuel vers Resilience Vault

## Correctifs

- Relire les préconditions après confirmation et authentification, juste avant l’export. Un refus à ce stade permet au gestionnaire de reprendre le chat s’il l’avait arrêté.
- Donner un nom distinct à chaque export temporaire vers le coffre. Ne pas supprimer l’ancienne archive ni modifier ses préférences, y compris quand le nouvel export contient des erreurs.
- Couvrir les 24 combinaisons des préconditions dans les tests de la politique d’admission.
- Ajouter un workflow ciblé pour les tests desktop et la compilation/tests unitaires Android, avec rapports conservés.

## Limites encore ouvertes

Ces changements ne constituent pas une validation fonctionnelle ou de sécurité du parcours complet.

- Le parcours autorise maintenant la reprise du chat dès la création du ZIP complet. La reprise effective et les interactions avec le sélecteur restent à tester sur téléphone.
- Les exceptions du moteur, y compris une annulation de coroutine, déclenchent maintenant une tentative de suppression du ZIP de cette tentative. Un échec de suppression est signalé sans remplacer l’erreur initiale. Un processus tué ne passe pas par ce nettoyage : il exige toujours un inventaire durable et un nettoyage de reprise limité à cet inventaire.
- Le parcours du coffre utilise maintenant un sélecteur distinct et propage le résultat de copie et de nettoyage. Les tests de flux couvrent les erreurs d’écriture et de fermeture ; les fournisseurs SAF réels, le stockage plein et les changements de cycle de vie Android restent à tester.
- La vérification des préconditions n’est pas un verrou transactionnel contre un changement concurrent pendant l’export.
- La compilation Kotlin et les tests JVM ne produisent pas un APK exécutable. L’assemblage exige les bibliothèques natives SimpleX/support correspondant à cette révision ; aucun binaire d’une version arbitraire ne doit être substitué.
- La transmission locale à RV, le chiffrement avant réseau, le manifeste authentifié et le commit distant restent à réaliser et auditer.
- Restauration sur un second téléphone, médias inclus, avec phrase secrète disponible hors du premier téléphone : non testée.
- Indépendance du coffre de récupération face au panic RV : non démontrée.
- Aucun effacement coopératif n’est activé.

## Vérifications des autres validations du fork

Le lancement android-emu 40681650399 du 28 octobre est sur stable et exécute seulement select-feature-branch. Il ne valide aucune modification Android.

Le lancement build 40673573479 concerne la branche android/call-proximity-setting, commit 23543c5f4e7f295b4389d9a0a5a20d3135dfce54. Ses journaux confirment compilation Android et testFossDebugUnitTest réussis. Il ne contient pas d’artifact android-app, seulement un enregistrement Docker. Ces résultats ne couvrent pas la branche Resilience Vault et ne remplacent pas un essai du capteur sur téléphone.

## Reprise du 28 octobre — nettoyage des exports en échec

- L’archive précédente reste intacte ; une destination déjà existante est refusée avant l’appel au moteur.
- Une archive réussie reste disponible pour le sélecteur. Une archive créée par un moteur qui lève une exception est nettoyée ; une annulation est propagée après la tentative de nettoyage.
- Les erreurs de suppression sont attachées à l’erreur initiale et signalées à l’utilisateur. L’indicateur de progression est réinitialisé dans un `finally`.
- Les rejets d’archive partielle et les exceptions non liées à une annulation autorisent la reprise du chat si le parcours l’avait arrêté. La reprise effective sur téléphone reste à tester.
- Validation locale : compilation Kotlin 2.1.20 et exécution JUnit 4.13.2 des classes `VaultExportAttemptTest` et `VaultExportGateTest` : **10 tests réussis**, dont six cas de cycle de vie de fichier et les 24 combinaisons de préconditions.
- Le run Android précédent `40709173334` a été annulé à la limite de 35 minutes pendant `:common:compileDebugKotlinAndroid`, sans rapport de test. Il ne constitue pas une validation Android.
- Toujours ouverts : copie SAF, annulation du sélecteur, nettoyage après processus tué, reprise après export accepté, restauration physique et intégration RV complète.

## Reprise du 29 octobre — copie et mémoire de compilation

Le run `40751851575` a échoué dans les compilations Kotlin Android et desktop avec `OutOfMemoryError: GC overhead limit exceeded`, avant les tests. Le workflow limite maintenant les workers à un et compile Kotlin dans le processus Gradle, doté de 5 Gio de heap, sans augmenter les exigences des compilations locales.

Le sélecteur du coffre dispose de son propre état. La copie s’exécute hors du thread UI ; son résultat n’est positif qu’après fermeture du flux de destination. Une erreur d’ouverture, d’écriture ou de fermeture est signalée sans chemin ou exception brute. La copie temporaire est nettoyée après succès, annulation ou échec. Si ce nettoyage échoue, le chemin reste suivi et une nouvelle tentative d’export retente le nettoyage avant de créer une archive. Les autres opérations de base sont désactivées tant que ce fichier est en attente.

Huit tests supplémentaires couvrent copie complète, annulation, permission révoquée, stockage plein, erreur de fermeture du fournisseur, source disparue, suppression refusée et propagation d’une annulation. Chaque scénario préserve une ancienne archive indépendante.

Menace couverte : un fournisseur de documents peut refuser l’accès ou échouer pendant la copie. Une fermeture réussie ne prouve ni persistance distante, ni contenu authentique, ni restauration. Le fournisseur peut conserver une destination partielle en cas d’échec ; le message demande de la supprimer, sans effacer automatiquement un document externe dont la propriété ne peut pas être garantie.

La reprise du chat est demandée dès que le ZIP est prêt ; il s’agit d’un instantané indépendant de la base courante. Le parcours d’export habituel est conservé.

Toujours bloquants : validation Android du nouveau commit, interruption du processus et inventaire durable, concurrence des changements de base, assemblage avec les bibliothèques natives correspondantes et essais SAF/restauration sur deux appareils. L’intégration de chiffrement et de récupération RV reste distincte ; aucun effacement coopératif n’est activé.
