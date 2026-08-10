# Roue Libre

**Roue Libre** is a free and open-source Android app for public bike-share
networks, built for the Lille metropolitan area in northern France. It shows
station availability on a map and computes door-to-door journeys that combine
walking and cycling. Everything runs on the device — map, address search and
routing all work with no network at all; only live bike availability needs a
connection. There is no Google dependency of any kind, no account, no
telemetry. The app is aimed at a French-speaking audience, so **the rest of
this document is in French**.

---

## Ce que c'est

Une application Android libre qui affiche les stations de vélos en libre-service
de la métropole lilloise et calcule un itinéraire porte-à-porte
**marche → vélo → marche**, en choisissant le meilleur couple de stations
plutôt que la plus proche.

Le nom joue sur le double sens de « libre » : libre-service et logiciel libre.
Rien dans le code ne nomme un réseau particulier — servir une autre
agglomération se fait en remplaçant un fichier de configuration (voir
[Portage vers une autre ville](#portage-vers-une-autre-ville)).

## Ce qui la distingue

- **Aucun service Google.** Ni Play Services, ni Firebase, ni Maps SDK. Elle
  tourne sur LineageOS sans GApps.
- **Aucune télémétrie, aucun mouchard, aucun identifiant unique.** Aucune
  donnée de trajet n'est conservée : ni historique, ni positions, ni
  destinations.
- **Hors ligne par défaut.** Carte vectorielle, recherche d'adresses et calcul
  d'itinéraire s'exécutent sur l'appareil. La recherche d'adresse est la donnée
  la plus sensible de l'application — elle révèle où vous allez — et ne quitte
  jamais le téléphone.
- **Légère.** Cible : moins de 15 Mo d'APK, moins de 135 Mo une fois les
  données hors ligne installées.

## État d'avancement

Le projet suit la progression du `SPEC.md` §16.

| Étape | État |
|---|---|
| 1. Récupération et affichage des données GBFS en liste | ✅ fait |
| 2. Scripts de génération des données hors ligne | ✅ fait |
| 3. Carte vectorielle et marqueurs | ✅ fait |
| 4. Moteur de routage hors ligne | ✅ fait |
| 5. Algorithme de trajet optimisé | ✅ fait |
| 6. Recherche d'adresses locale | ✅ fait |
| 7. Écrans restants | en cours — détail d'une station, itinéraire et résultat sont faits |
| 8. Finitions et métadonnées F-Droid | à faire |

Une première version installable, **0.1.0-alpha**, existe : elle affiche la
carte et les disponibilités, cherche une adresse hors ligne et calcule un
itinéraire porte-à-porte. Les jeux de données s'y installent encore à la main,
et plusieurs écrans manquent — voir le [CHANGELOG](CHANGELOG.md).

## Architecture

Deux modules Gradle, et la frontière entre les deux est vérifiée par le
compilateur plutôt que par la discipline.

```
┌─────────────────────────────────────────────────────────────┐
│  :app                                          Android      │
│                                                             │
│  ui/          Activité unique, fragments, vues XML          │
│               ViewBinding, pas de Compose                   │
│      ↑ état observé (StateFlow)                             │
│  ui/*ViewModel                                              │
│      ↑ Outcome<T>                                           │
│  data/        StationRepository — politique de fraîcheur    │
│      ├── network/  OkHttp ──────────────────► flux GBFS     │
│      └── local/    Room, DataStore                          │
│      ↑                                                      │
│  AppContainer  instanciation manuelle, pas de Hilt ni Koin  │
└──────────────────────────┬──────────────────────────────────┘
                           │ dépend de
┌──────────────────────────▼──────────────────────────────────┐
│  :core                                    Kotlin pur        │
│                                                             │
│  gbfs/       analyse des flux, tolérante GBFS 2.x et 3.0    │
│  station/    modèle métier, échelle de disponibilité,       │
│              fraîcheur de la donnée                         │
│  address/    normalisation des noms de voies, distance      │
│              d'édition, classement, interpolation des       │
│              numéros                                        │
│  journey/    algorithme du trajet marche → vélo → marche    │
│  geo/        coordonnées, emprise, distances                │
│  config/     lecture de la configuration de ville           │
│  Outcome     types de résultat, jamais d'exception muette   │
│                                                             │
│  Aucun import Android. Testable sur la JVM, sans émulateur. │
└─────────────────────────────────────────────────────────────┘
```

**Le flux de données.** Le dépôt est la source unique. Il émet un flux continu
du contenu du cache local, ce qui fait que l'interface affiche quelque chose
immédiatement, y compris hors ligne et dès le premier dessin. Le réseau vient
par-dessus : une actualisation écrit dans le cache, et le cache réémet. Aucun
écran ne parle au réseau directement.

**La gestion d'erreurs.** Aucune exception ne traverse une frontière de couche.
Les échecs sont des valeurs — `Outcome.Failure(DataError.Offline)` — et la
seule couche qui les met en mots français est l'interface. Le module métier n'a
pas le droit de contenir une chaîne affichable.

## Compiler

Le dépôt contient un sous-module. Le cloner sans lui donnerait une
compilation qui échoue sur le moteur de routage :

```bash
git clone --recurse-submodules https://github.com/mgdx/RoueLibre.git
# ou, sur un dépôt déjà cloné :
git submodule update --init
```

```bash
./gradlew assembleDebug     # compilation
./gradlew test              # tests unitaires sur la JVM
./gradlew lint ktlintCheck  # analyse statique, aucun avertissement toléré
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

Il faut un JDK 17 ou plus et le SDK Android (compileSdk 37). Aucune clé, aucun
compte, aucun service tiers n'est nécessaire pour compiler.

Taille de l'APK de release, carte, routage, recherche d'adresses et
itinéraire compris : **7,67 Mo en arm64-v8a** et 7,10 Mo en armeabi-v7a, pour
un plafond de 12 Mo par architecture. Les bibliothèques natives de MapLibre sont empaquetées
compressées — sans quoi le même APK pèserait 14,87 Mo.

## Générer les jeux de données hors ligne

Les trois jeux — fond de carte, graphe de routage, index d'adresses — ne sont
pas dans l'APK : ils sont téléchargés au premier lancement, ou fournis à la
main. Leur génération est entièrement scriptée et versionnée dans
[`tools/`](tools/README.md) :

```bash
tools/generate_all.sh
```

Tailles réellement obtenues sur l'emprise lilloise, le 9 août 2026 :

| Jeu | Budget `SPEC.md` | Obtenu |
|---|---|---|
| Fond de carte (MBTiles, zooms 10–16) | 30 – 60 Mo | **35,0 Mo** |
| Graphe de routage (BRouter rd5) | 15 – 40 Mo | **1,7 Mo** |
| Index d'adresses (SQLite FTS4) | 13 – 28 Mo | **5,9 Mo** |
| **Total téléchargé** | | **42,5 Mo** |

## Portage vers une autre ville

Aucune donnée propre à Lille n'existe dans le code : ni URL, ni emprise, ni
coordonnée de centrage, ni nom de réseau. Tout vit dans un seul fichier.

1. **Copier la configuration de ville.** Partir de
   [`config/cities/lille.json`](config/cities/lille.json) et n'ajuster que le
   bloc `network`, l'URL du `gbfs.json` et le centrage de la carte. Ne pas
   toucher au bloc `boundingBox` : il est recalculé automatiquement.
2. **Trouver l'URL du flux GBFS.** Ne jamais la deviner : la relever dans le
   [catalogue MobilityData](https://github.com/MobilityData/gbfs/blob/master/systems.csv)
   ou, en France, sur [transport.data.gouv.fr](https://transport.data.gouv.fr/),
   puis la vérifier par une requête réelle.
3. **Générer les données** avec la région OpenStreetMap et les départements
   correspondants :
   ```bash
   tools/generate_all.sh --city config/cities/<ville>.json \
                         --region europe/france/<region> \
                         --departments 35
   ```
   L'emprise est dérivée des stations du réseau, puis élargie de 3 km ; elle
   suit donc automatiquement les extensions du réseau.
4. **Publier les fichiers** de `data/out/` dans une *release* du dépôt, avec le
   manifeste, et pointer `dataRelease.manifestUrl` dessus.

Le format GBFS étant un standard international, l'essentiel de la portabilité
est acquis dès que l'URL est configurable — elle l'est aussi depuis les
réglages de l'application, sans recompiler.

**Une limite à connaître :** l'index d'adresses s'appuie sur la Base Adresse
Nationale, qui est française. Pour une ville étrangère il faudrait le
régénérer depuis OpenStreetMap ; le script isole cette source pour rendre la
substitution possible.

## Dépendances, et pourquoi chacune

Le `SPEC.md` §4 impose de justifier chaque ajout. Rien n'entre sans raison.

| Dépendance | Rôle | Pourquoi elle plutôt qu'une autre |
|---|---|---|
| **OkHttp** | requêtes HTTP | Trois requêtes GET ne justifient pas Retrofit. OkHttp seul suffit et pèse moins. |
| **kotlinx.serialization** | lecture du JSON | Génération à la compilation, donc pas de réflexion ni de règles R8 à maintenir — contrairement à Gson ou Moshi. |
| **Room** | cache des stations | Requis par le `SPEC.md` §8. Apporte les flux réactifs et la vérification des requêtes à la compilation. |
| **DataStore** | réglages | Quelques valeurs isolées ; Room serait disproportionné. |
| **Coroutines** | asynchrone | Standard du langage. |
| **Material Components** | socle de l'interface | Composants accessibles éprouvés. Aucune de ses couleurs par défaut ne subsiste. |
| **BRouter** | calcul d'itinéraires hors ligne | Moteur éprouvé, orienté cyclisme, profils paramétrables. Intégré comme **sous-module Git** épinglé sur une étiquette : l'artefact Maven `org.btools:brouter-core` que l'on trouve mentionné n'est publié nulle part. MIT, compatible GPLv3, avis de licence conservé dans les mentions de l'application. |
| **MapLibre Native** | carte vectorielle hors ligne | Seule dépendance native du projet, et seule entorse assumée à la contrainte de taille : c'est le prix du hors-ligne. Lit le MBTiles directement sur le disque, sans serveur de tuiles. BSD-2-Clause, minSdk 23. |
| **AndroidX** *(core, appcompat, fragment, lifecycle, recyclerview, swiperefreshlayout, constraintlayout)* | briques d'interface | Base d'une application à vues XML. |
| **Atkinson Hyperlegible** | police de texte | Dessinée par le Braille Institute pour la basse vision : le 0 se distingue du O, le 1 du l. Pour une application lue en marchant, c'est fonctionnel. SIL OFL. |
| **Bricolage Grotesque** | police des chiffres | Les nombres de vélos sont l'information centrale ; ils méritent une lettre reconnaissable de loin. Figée en deux instances statiques de 91 ko. SIL OFL. |

Aucune bibliothèque d'analytics, de rapport de plantage ou de publicité, sous
aucun prétexte.

**Outils de génération des données**, hors APK : `osmium-tool`, `tippecanoe`,
`fontTools`, et le générateur de cartes de [BRouter](https://github.com/abrensch/brouter)
(MIT), dont la version est figée et l'archive vérifiée par empreinte SHA-256.

## Sources de données et attributions

| Source | Usage | Licence |
|---|---|---|
| Flux GBFS d'Ilevia / Métropole Européenne de Lille | disponibilité des stations | ODbL |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | fond de carte, routage, points de repère | ODbL |
| [Base Adresse Nationale](https://adresse.data.gouv.fr/) | numéros de voirie | ODbL |
| [BRouter](https://github.com/abrensch/brouter) | moteur et générateur de routage | MIT |
| SRTM via [terrain-tiles](https://registry.opendata.aws/terrain-tiles/) | altimétrie du graphe | domaine public |

## Ouvrir un lieu depuis une autre application

Les liens `geo:` et `google.navigation:`, ainsi que les adresses partagées en
texte brut, arrivent directement dans Roue Libre : il suffit de la choisir dans
le sélecteur d'Android.

Les liens de sites de cartographie — `openstreetmap.org`, `google.com/maps` —
ne peuvent **pas** être vérifiés automatiquement, ces domaines n'appartenant
pas au projet. Depuis Android 12, ils ne parviennent donc à l'application que
si vous l'y autorisez :

**Paramètres → Applications → Roue Libre → Ouvrir par défaut → Ajouter un
lien**, puis cochez les domaines voulus.

Un lien raccourci n'est pas reconnu : le lieu n'y apparaît qu'après
redirection, et la suivre ferait sortir une requête vers un tiers à qui l'on
apprendrait où vous allez.

## Vie privée

En usage courant, **la seule requête réseau qui part est celle du flux GBFS**.
Les téléchargements de données n'ont lieu qu'au premier lancement ou sur action
explicite. La vérification des mises à jour n'est jamais automatique : une
requête périodique dessinerait un profil d'usage.

Aucun identifiant n'est envoyé. Le `User-Agent` nomme l'application et sa
version, rien d'autre. `android:allowBackup` est à `false` : rien ne part vers
le cloud.

## Contribuer

Voir [CONTRIBUTING.md](CONTRIBUTING.md), qui décrit notamment la procédure de
traduction.

## Licence

[GPLv3](LICENSE). Les polices embarquées sont sous
[SIL Open Font License](app/src/main/assets/licences/), qui leur est propre.
