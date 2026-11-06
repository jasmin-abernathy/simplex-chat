# Erreur de préparation du lot « groupe fermé » — 6 novembre 2026

Symptôme : une tentative de transformation du code `App.kt` a échoué avec « anchor 0 » avant création d'un blob Git.
Cause : recherche d'une phrase de commentaire avec une apostrophe typographique différente de celle réellement écrite.
Récupération : relecture du HEAD distant, récupération des blobs puis modifications limitées à des signatures de code uniques, vérifiées avant sauvegarde.
Prévention : ne pas faire dépendre les modifications d'une phrase décorative ; utiliser un identifiant/symbole non ambigu et tester le nombre d'occurrences.

Le résultat Android doit être compilé avant toute annonce de disponibilité de l'APK.