# CLAUDE.md

## Avant toute chose

**Lis `SPEC.md` à la racine du dépôt.** C'est le cahier des charges complet et la source de vérité du projet. Ce fichier-ci n'en est qu'un rappel opérationnel : en cas de contradiction, `SPEC.md` l'emporte.

## Le projet en une phrase

**Roue Libre** — application Android libre affichant les stations de vélos en libre-service V'lille (métropole de Lille) sur une carte, et calculant un itinéraire porte-à-porte marche → vélo → marche. Tout fonctionne hors ligne sauf la disponibilité des vélos en temps réel.

## Règles absolues

1. **Aucun service Google.** Ni Play Services, ni Firebase, ni FCM, ni Maps SDK, ni Crashlytics. L'appli doit tourner sur LineageOS sans GApps.
2. **Aucune télémétrie, aucun mouchard, aucun identifiant unique.** Aucune donnée de trajet conservée.
3. **Hors ligne par défaut.** Carte, recherche d'adresses et calcul d'itinéraire s'exécutent sur l'appareil. Seul le flux GBFS sort sur le réseau.
4. **Légèreté.** APK < 15 Mo. Toute dépendance ajoutée doit être justifiée dans le `README.md`.
5. **Rien de spécifique à Lille en dur** dans le code : URL, emprise, centrage et nom de réseau vivent dans la configuration de ville. Voir `SPEC.md` §15.
6. **Zéro chaîne de caractères en dur.** Tout dans `res/values/strings.xml`, français par défaut, `plurals` pour les accords, placeholders positionnels.
7. **GPLv3.** Vérifier la compatibilité de licence avant d'ajouter une dépendance.

## Conventions de code

Voir `SPEC.md` §14 pour le détail. En résumé :

- Kotlin, vues XML + ViewBinding, **pas de Compose**. minSdk 26.
- Logique métier en Kotlin pur, sans import Android, testable sur la JVM.
- Commentaires : expliquer le **pourquoi**, pas le **quoi**. Justifier chaque coefficient de l'algorithme d'itinéraire.
- KDoc sur tout ce qui est public. Nommage explicite en anglais, sans abréviations.
- Fonctions courtes, à responsabilité unique. Pas de code mort, pas d'abstraction anticipée.
- Commits atomiques, messages décrivant l'intention.

## Commandes

```bash
./gradlew assembleDebug        # compilation
./gradlew test                 # tests unitaires JVM
./gradlew lint detekt          # analyse statique
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Compile et lance les tests après chaque changement significatif. Ne me livre pas du code qui ne compile pas.

## Ce qu'il faut me demander avant de faire

- Ajouter une dépendance qui ne figure pas dans `SPEC.md` §3.
- S'écarter d'une décision déjà tranchée dans `SPEC.md` — elles l'ont été après discussion, pas par défaut.
- Faire évoluer le schéma des données ou le format des fichiers téléchargés.
- Appliquer un style visuel à l'ensemble de l'interface : soumets d'abord les jetons de conception et **une seule vue** en capture d'écran.

## Ce qu'il ne faut jamais faire

- Contourner silencieusement une contrainte du §2 de `SPEC.md`. Si elle bloque une fonctionnalité, dis-le et propose une alternative.
- Coder en dur une URL, une coordonnée ou une clé.
- Inventer une URL de flux de données : vérifie-la par une requête réelle avant de l'inscrire dans le code.
- Ajouter une fonctionnalité listée hors périmètre (`SPEC.md` §13).

## Ordre de travail

Suis la progression du §16 de `SPEC.md`. Elle commence par les **scripts de génération des données** — tuiles, graphe de routage, index d'adresses — dont les tailles réelles conditionnent toute l'architecture. Reporte-moi ces tailles avant de construire l'interface par-dessus.

## Langue

Le code, les noms de variables et les commentaires sont en anglais. L'interface, les messages d'erreur, la documentation et nos échanges sont en français.
