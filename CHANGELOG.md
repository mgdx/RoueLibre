# Journal des modifications

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le
projet, le [versionnage sémantique](https://semver.org/lang/fr/).

Les notes destinées aux utilisateurs vivent dans
`fastlane/metadata/android/fr/changelogs/` et sont écrites pour eux, pas pour
les développeurs. Ce fichier-ci s'adresse aux contributeurs et retient aussi ce
qui n'a aucun effet visible.

## [Non publié]

Développement initial. Rien n'est encore publié.

### Ajouté

- **Scripts de génération des jeux de données hors ligne** (`tools/`), avec
  l'emprise de référence dérivée des stations du réseau et recalculée à chaque
  exécution.
  - Fond de carte MBTiles filtré à la génération sur une liste blanche
    lisible : **35,0 Mo** pour les zooms 10 à 16.
  - Graphe de routage BRouter limité à l'emprise : **1,7 Mo**, contre une
    centaine de mégaoctets pour les segments standard.
  - Index d'adresses SQLite FTS4, numéros stockés en delta : **5,9 Mo** pour
    10 591 voies et 286 028 numéros.
  - Manifeste à empreintes SHA-256, pour ne retélécharger que ce qui a changé.
- **Couche GBFS** : analyse tolérante aux versions 2.x et 3.0, mise en cache
  Room, politique de rafraîchissement du `SPEC.md` §4.1.
- **Configuration de ville** : source unique de tout ce qui est propre à une
  agglomération, partagée entre l'application et les scripts.
- **Jetons de conception** : palette « ardoise », deux familles typographiques
  embarquées, échelle d'espacement et rayon uniques.
- **Écran de liste des stations** avec l'indicateur de disponibilité, la
  bascule vélos/places, le tirer-pour-rafraîchir et l'âge de la donnée.
- **Moteur de routage hors ligne** (§5) : BRouter intégré comme sous-module
  Git, avec deux profils écrits pour ce projet — piéton urbain et vélo de
  libre-service. Le graphe est lu depuis le fichier installé, les profils sont
  dans l'APK, rien ne sort sur le réseau.
- **Carte vectorielle hors ligne** (§7.1) : fond de carte lu depuis le fichier
  MBTiles installé, style sobre et désaturé piloté par les jetons de couleur du
  projet, glyphes de texte embarqués dans l'APK. Marqueurs de stations reprenant
  l'échelle de disponibilité, regroupés aux zooms éloignés, attribution
  OpenStreetMap portée par la carte elle-même.
- **Écran « stockage »** (§4.4) : les trois jeux de données hors ligne avec
  leur taille et leur date, import manuel par le sélecteur de documents,
  suppression. L'installation est atomique — le fichier est écrit à côté,
  validé, puis mis en place — et un fichier refusé dit pourquoi.
- **Filtre de la liste par nom de station**, insensible à la casse et aux
  accents, tolérant à l'ordre des mots, cherchant aussi le code postal.

### Vérifié

- L'application se lance et affiche les disponibilités réelles du réseau sur
  un émulateur **AOSP sans aucun service Google** — zéro paquet `com.google.*`
  installé, ce qu'exige le critère d'acceptation §11.1.
- En mode avion, les dernières disponibilités connues restent affichées et
  l'application le dit, sans erreur bloquante.
- La carte s'affiche, se déplace et se zoome **sans aucune requête réseau** :
  tuiles lues sur le disque, glyphes dans l'APK.
- La compilation de release avec R8 produit **2,82 Mo par architecture** et
  fonctionne : les règles de conservation de kotlinx.serialization sont
  correctes, ce qui ne se voit qu'en release.

### Notes techniques

- L'artefact Maven `org.btools:brouter-core` mentionné par le cahier des
  charges **n'existe pas** : zéro résultat sur Maven Central. BRouter est donc
  consommé comme build composite depuis un sous-module épinglé sur v1.7.10.
- BRouter déduit le nom de son fichier de segment des coordonnées cherchées —
  `E0_N50.rd5` pour Lille. Le graphe conserve donc son nom d'origine à
  l'installation, contrairement aux deux autres jeux de données.

- L'URL du flux GBFS a été relevée dans le catalogue MobilityData et recoupée
  avec le Point d'Accès National, dont la ressource redirige vers la même
  adresse. Elle n'a pas été devinée.
- Le flux annonce `ttl: 0`, valeur inexploitable : l'application applique sa
  propre politique de fraîcheur.
- Les deux flux de stations ne sont pas synchronisés — 268 stations d'un côté,
  267 de l'autre le 9 août 2026. La jointure le tolère par construction.
- `fontVariationSettings` exige l'API 28 alors que le `minSdk` est 26 : la
  police variable a été figée en deux instances statiques, ce qui a aussi
  ramené son poids de 408 à 182 ko.
- Les adresses sont regroupées par (code INSEE, ancienne commune, nom
  normalisé) et non par `id_fantoir`, vide sur 24 363 lignes de l'emprise.
  Le regroupement précédent coupait 69 voies en deux et envoyait 0,53 % des
  adresses à plus de 50 m ; le taux est retombé à 0,04 %.
