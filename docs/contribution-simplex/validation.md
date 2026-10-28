# Validation de la contribution Android

État au 28 octobre 2026 : patch non compilé, non exécuté sur Android.

## Vérifications effectuées

- Linux, OpenJDK 17.0.20.
- `git diff --check` : code de sortie 0.
- `xml.etree.ElementTree.parse` des fichiers `base/strings.xml` et `fr/strings.xml` modifiés : succès.
- Depuis `apps/multiplatform`, `./gradlew :android:assembleFossDebug :android:testFossDebugUnitTest` : code de sortie 1 avant compilation. Gradle 8.12 ne peut pas être téléchargé : `java.net.SocketException: Network is unreachable`. Détail dans `build.log`.
- `adb` absent du PATH. Aucun test sur capteur, émulateur ou téléphone effectué. Aucune preuve de non-régression d'appel.

## Reproduire

Sur une machine disposant du SDK/NDK Android et des dépendances natives documentées dans le README amont :

```sh
git clone --branch android/call-proximity-setting https://github.com/jasmin-abernathy/simplex-chat.git
cd simplex-chat
git rev-parse HEAD
# Commit attendu : 3faf747e36259734610ea53a857220a398cd7d62
cd apps/multiplatform
./gradlew :android:assembleFossDebug :android:testFossDebugUnitTest
./gradlew connectedFossDebugAndroidTest
```

Configurer `local.properties` selon le README avec `application_id.suffix=.debug` et `app.name=SimpleX Debug` pour éviter de remplacer l'installation habituelle. La branche ne fournit pas de nouvel APK ni de bibliothèque native compilée pour ce changement. Les tests existants ne valident pas à eux seuls le comportement matériel du capteur.

## Matrice manuelle — tout reste à exécuter

Pour chaque cas, tester option désactivée puis activée, appels audio et vidéo, Android 11 ou antérieur puis Android 12+, si appareils disponibles.

| Cas | Résultat attendu |
|---|---|
| Installation sans préférence | Option désactivée, comportement actuel du capteur conservé. |
| Option activée puis redémarrage | Valeur conservée ; couvrir le capteur n'éteint plus la fenêtre d'appel. |
| Écouteur, haut-parleur, Bluetooth, casque filaire | Option respectée ; son et changement de route inchangés. |
| Réduction/PiP, retour, fermeture de vue | Verrou relâché à la réduction/annulation ; réacquisition uniquement si option désactivée et vue dépliée. |
| Appuyer sur le bouton verrouillage | Aucun déverrouillage ni réveil forcé ajouté par l'option. |
| Fin avant le délai d'une seconde | Aucune acquisition tardive après close(). |
| Fin depuis notification, interruption, nouvel appel | Aucun verrou conservé de l'appel précédent. |
| Appareil sans capteur | Pas d'exception, réglage sans effet matériel. |
| TalkBack, grande police | Libellé et état du switch accessibles, activation possible. |

Inspecter `adb shell dumpsys power` pendant et après les appels, chercher le tag `:proximityLock`. Vérifier l'absence du verrou après fin et lorsque l'option est active. Ne pas publier le dump brut : il peut contenir des informations privées.

La préférence est locale à l'appareil ; l'export/import des paramètres n'a pas été étendu. Le patch ne change ni la politique Bluetooth ni les flags de verrouillage. Une revue des transitions de cycle de vie reste nécessaire avant publication.

## Validation distante

Le correctif est enregistré sur le fork. Aucun workflow distant n'a été démarré et aucun résultat de CI ne peut être annoncé. Le workflow amont `android-emu.yml` décrit la compilation et un test de démarrage ; ceux-ci ne remplacent pas la matrice matérielle ci-dessus. Pour un premier contrôle de compilation sans produire ni diffuser d'APK, utiliser les tâches Kotlin et unitaires décrites dans `scripts/ci/README.md` sur une machine équipée. Ne pas activer les options supplémentaires de test distant sans en avoir vérifié les besoins.
