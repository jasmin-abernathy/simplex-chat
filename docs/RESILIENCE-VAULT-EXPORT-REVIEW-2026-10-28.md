# Revue de l’export manuel vers Resilience Vault

## Correctifs

- Relire les préconditions après confirmation et authentification, juste avant l’export. Un refus à ce stade permet au gestionnaire de reprendre le chat s’il l’avait arrêté.
- Donner un nom distinct à chaque export temporaire vers le coffre. Ne pas supprimer l’ancienne archive ni modifier ses préférences, y compris quand le nouvel export contient des erreurs.
- Couvrir les 24 combinaisons des préconditions dans les tests de la politique d’admission.
- Ajouter un workflow ciblé pour les tests desktop et la compilation/tests unitaires Android, avec rapports conservés.

## Limites encore ouvertes

Ces changements ne constituent pas une validation fonctionnelle ou de sécurité du parcours complet.

- Le chat reste arrêté après un export accepté, selon le parcours historique. Définir et tester sa reprise après copie/annulation.
- Une exception du moteur avant le retour de l’export peut laisser un fichier temporaire non suivi. Une interruption du processus exige un inventaire durable des fichiers propres à l’export, puis un nettoyage de reprise limité à cet inventaire.
- Le sélecteur partagé ignore le résultat de suppression du temporaire et ne propage pas le résultat de copie. Tester stockage plein, fournisseur SAF en erreur, annulation et échec de suppression avant de qualifier ce parcours de fiable.
- La vérification des préconditions n’est pas un verrou transactionnel contre un changement concurrent pendant l’export.
- La compilation Kotlin et les tests JVM ne produisent pas un APK exécutable. L’assemblage exige les bibliothèques natives SimpleX/support correspondant à cette révision ; aucun binaire d’une version arbitraire ne doit être substitué.
- La transmission locale à RV, le chiffrement avant réseau, le manifeste authentifié et le commit distant restent à réaliser et auditer.
- Restauration sur un second téléphone, médias inclus, avec phrase secrète disponible hors du premier téléphone : non testée.
- Indépendance du coffre de récupération face au panic RV : non démontrée.
- Aucun effacement coopératif n’est activé.

## Vérifications des autres validations du fork

Le lancement android-emu 40681650399 du 28 octobre est sur stable et exécute seulement select-feature-branch. Il ne valide aucune modification Android.

Le lancement build 40673573479 concerne la branche android/call-proximity-setting, commit 23543c5f4e7f295b4389d9a0a5a20d3135dfce54. Ses journaux confirment compilation Android et testFossDebugUnitTest réussis. Il ne contient pas d’artifact android-app, seulement un enregistrement Docker. Ces résultats ne couvrent pas la branche Resilience Vault et ne remplacent pas un essai du capteur sur téléphone.
