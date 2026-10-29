# APK de test — export manuel vers Resilience Vault

Cette branche de fabrication part du code revu dans la PR #1 au commit `2a5164a1503aa5cbcd495c33b66ad4ae844313d7`. Elle ajoute uniquement le workflow et ses scripts de compilation. Les scripts natifs proviennent du fork au commit `23543c5f4e7f295b4389d9a0a5a20d3135dfce54`, avec interdiction de modifier le fichier de verrouillage Nix pendant le build.

## Périmètre

- Android 8 minimum, arm64-v8a, variante FOSS debug.
- Application séparée : `chat.simplex.app.vaulttest`, nom « SimpleX Vault Test ». Elle ne remplace pas l’installation habituelle de SimpleX.
- Export manuel, contrôle des erreurs de copie, nettoyage après interruption. Ni intégration automatique RV ni effacement coopératif.
- Le réglage du capteur de proximité de la PR #2 et le prototype d’appels de groupe ne sont pas inclus dans cette branche.
- Les bibliothèques natives sont construites depuis les sources de cette branche et ses dépendances Nix verrouillées. Aucun APK officiel n’est utilisé comme fournisseur de bibliothèques.

L’artifact `android-app` n’est publié que si les tests, l’assemblage, la vérification de l’identifiant d’application, la présence des bibliothèques natives et la vérification de signature ont réussi. Il contient le SHA source, l’empreinte du fichier et le certificat public de signature. Cela ne constitue pas un essai fonctionnel sur Android.

## Essais physiques

Utiliser un profil de test avec des conversations et médias fictifs, et une phrase secrète connue. Ne pas importer l’unique copie d’une base personnelle.

1. Confirmer que l’application s’installe à côté de SimpleX et démarre.
2. Préparer un export complet, vérifier la reprise du chat après création du ZIP, puis annuler ou terminer la copie.
3. Tester plusieurs fournisseurs de documents, stockage saturé et révocation de permission. Aucune erreur ne doit être annoncée comme une réussite. Supprimer manuellement toute destination partielle signalée.
4. Arrêter le processus pendant la préparation du ZIP et après ouverture du sélecteur ; relancer et vérifier que le dossier de la tentative interrompue est supprimé. Ne pas publier de dump contenant des conversations ou des chemins personnels.
5. Vérifier qu’une nouvelle tentative reste possible et que les archives extérieures au stockage temporaire du coffre sont intactes.
6. Réimporter sur un second appareil de test : utilisateurs, conversations, médias et phrase secrète. Ce test valide l’archive SimpleX ; il ne valide pas un coffre RV distant.

Une signature debug générée par un nouveau runner peut changer entre builds. Une mise à jour Android peut alors être refusée : conserver les données de test nécessaires avant toute réinstallation. Aucune signature de production n’est utilisée.
