# Journal des modifications

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le
projet, le [versionnage sémantique](https://semver.org/lang/fr/).

Les notes destinées aux utilisateurs vivent dans
`fastlane/metadata/android/fr/changelogs/` et sont écrites pour eux, pas pour
les développeurs. Ce fichier-ci s'adresse aux contributeurs et retient aussi ce
qui n'a aucun effet visible.

## [Non publié]

### Ajouté

- **Plusieurs villes dans la même application** (`SPEC.md` §15.1). Trois
  réseaux sont servis : V'lille, Vélo'v et Vélib' Métropole.
  - Un **catalogue** dérivé des configurations de ville — `tools/build_catalogue.py`
    — porte pour chacune son emprise, son centre, ses stations et le poids de
    ses données. Il est téléchargeable, pour qu'une ville nouvelle apparaisse
    sans publier de version, et livré dans l'APK comme secours.
  - Un **écran « ville »** propose l'agglomération d'après la position, sur
    appui d'un bouton et jamais de lui-même. Au-delà de cinquante kilomètres du
    réseau le plus proche, il ne propose rien.
  - Les jeux de données sont **rangés par ville** : deux villes cohabitent sans
    se mélanger, et les données de l'une se suppriment sans toucher à l'autre.

- **Partir d'une station, ou aller à une station.** La feuille de détail
  proposait de mettre en favori et d'ouvrir un guidage externe, mais pas de
  préparer un itinéraire — la seule action du `SPEC.md` §7.2 qui manquait
  depuis que l'écran de recherche existe.

### Modifié

- **L'application ne suppose plus de ville par défaut.** Elle en servait une,
  compilée dans l'APK ; elle sert désormais celle qu'on lui a désignée, et le
  dit tant qu'on ne l'a pas fait.
- **Les fichiers publiés portent le nom de leur réseau.** Une release GitHub
  n'a qu'un espace de noms : trois `tiles.mbtiles` s'y écraseraient. Sur
  l'appareil, chaque fichier retrouve son nom nu — BRouter reconnaît ses
  segments au nom et ne trouverait pas `vlille-E0_N50.rd5`.

### Corrigé

- **Les stations d'une ville restaient affichées après en avoir changé.**
  Le cache des stations ne connaissait pas la ville ; hors ligne, rien ne
  venait les remplacer, et la carte de Paris montrait les stations de Lille.
  Changer de ville vide ce cache.
- **Un appareil qui avait déjà des données installées ne les retrouve pas.**
  Elles étaient rangées sans ville ; il n'y a pas moyen de deviner laquelle, et
  les rattacher au hasard ferait afficher la carte d'une ville sous le nom
  d'une autre. Il faut les réinstaller après avoir choisi sa ville.

## [0.1.0-alpha]

Première version installable. Elle fait le tour de son sujet — carte hors
ligne, disponibilités en direct, recherche d'adresses, itinéraire
porte-à-porte — mais **ce n'est pas encore une version complète** :

- les jeux de données s'installent **à la main**, depuis l'écran de stockage ;
  leur téléchargement depuis un manifeste (§4.4) n'existe pas encore ;
- les écrans **réglages** (§7.6), **à propos** (§7.7), **favoris** (§7.5),
  **premier lancement** (§7.9) et **nouveautés** (§7.10) manquent ;
- l'ouverture depuis une autre application (§7.8) n'est pas déclarée ;
- l'attribution complète, obligatoire au §4.5, n'est portée que par la carte.

Elle est signée par une clé d'essai, jamais par une clé de publication : ce
qui sortira sur F-Droid sera recompilé et signé là-bas.

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
- **Algorithme de trajet optimisé** (§6) : choisit le meilleur couple de
  stations plutôt que la plus proche, pénalise les stations peu fournies,
  propose trois alternatives et signale les trajets où la marche va plus vite.
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
- **Premier lancement** (§7.9) : trois pages courtes — ce qu'est
  l'application, ce qu'elle ne fait pas de vos données, ce dont elle a besoin —
  chacune contournable, la dernière enchaînant directement sur le
  téléchargement. Un écran et non une boîte de dialogue, parce qu'il doit
  pouvoir être relu depuis « À propos ».
- **Nouveautés après mise à jour** (§7.10), affichées une seule fois, et
  jamais à une première installation. Si l'écart couvre plusieurs versions,
  toutes les notes intermédiaires sont montrées, de la plus récente à la plus
  ancienne.
  - Les notes viennent des **métadonnées F-Droid**
    (`fastlane/metadata/android/fr/changelogs/`), converties en ressource
    embarquée au moment du build : F-Droid et l'application affichent
    exactement le même texte, sans double saisie. Rien n'est téléchargé.
- **Métadonnées F-Droid** : description courte, description longue, notes de
  version et six captures d'écran, rédigées pour l'utilisateur et non pour le
  développeur.
- **Deux réseaux de plus, générés et mesurés** : Vélib' Métropole (1 518
  stations, 994 km², **142,8 Mo**) et Vélo'v Lyon (465 stations, 575 km²,
  **42,3 Mo**), à comparer aux 42,5 Mo de Lille. Le graphe de routage de Lyon
  s'étend sur deux segments BRouter, ce qui éprouve pour la première fois un
  jeu de données à plusieurs fichiers.
- **Traduction anglaise complète** dans `values-en/`, l'exemple que réclame le
  §9. Elle montre à un traducteur à quoi ressemble une traduction achevée, et
  permet de vérifier qu'un basculement de langue ne casse aucune mise en page.
- **Téléchargement des jeux de données** (§4.4) : consultation du manifeste
  publié, comparaison des empreintes, et transfert de ce qui a changé — et de
  cela seulement. Rafraîchir l'index d'adresses n'impose donc pas de reprendre
  les trente-cinq mégaoctets de tuiles.
  - **Reprise** d'un transfert interrompu par en-tête `Range`, avec retour à
    zéro si le serveur l'ignore : ajouter le début du fichier à la suite de ce
    qu'on avait produirait un fichier corrompu.
  - **Empreinte revérifiée** après réception. Un fichier qui ne correspond pas
    au manifeste est rejeté et l'installation précédente reste intacte : les
    fichiers reçus sont contrôlés avant que quoi que ce soit ne soit remplacé.
  - Un manifeste annonçant une version de format inconnue invite à mettre
    l'application à jour, plutôt que d'échouer plus tard à l'ouverture d'un
    fichier.
  - **Jamais automatique** : la vérification a lieu sur appui, depuis l'écran
    de stockage. Une requête périodique dessinerait un profil d'usage.
  - Avertissement hors Wi-Fi — un avertissement, pas un obstacle.
- **Ouverture depuis une autre application** (§7.8) : l'application apparaît
  dans le sélecteur d'Android pour les schémas `geo:` et `google.navigation:`,
  et pour le partage de texte brut — le cas le plus fréquent en pratique, une
  adresse reçue par messagerie. Toutes les formes du §7.8 sont acceptées, y
  compris `geo:0,0?q=…` dont le point de tête est une convention, et les
  libellés entre parenthèses.
  - L'analyse vit dans le module métier, en Kotlin pur : quatorze tests sur la
    JVM couvrent les écritures que l'on rencontre réellement.
  - **Aucune requête réseau** n'est déclenchée par une intention entrante : une
    adresse en toutes lettres est résolue par l'index local. Un lien raccourci
    n'est donc pas reconnu — suivre sa redirection apprendrait à un tiers où va
    l'utilisateur.
  - Un point hors de l'emprise couverte est montré sur la carte, sans qu'aucun
    itinéraire ne soit tenté, et l'application dit pourquoi.
  - Les liens web cartographiques sont déclarés mais **non vérifiés
    automatiquement** : les domaines n'appartiennent pas au projet. La
    manipulation à faire dans les paramètres d'Android est expliquée dans « À
    propos » et dans le `README.md`.
- **Favoris** (§7.5) : la liste des stations mises en favori, avec leur
  disponibilité en direct, **réorganisable par glissement**. L'ordre est le
  seul réglage de cet écran, et il vaut mieux qu'un tri automatique — la
  station qu'on veut voir en premier est celle de son quartier, pas la
  première par ordre alphabétique.
  - Les favoris passent d'un ensemble à une **liste ordonnée** : un ensemble
    n'a pas d'ordre à réorganiser. Ceux enregistrés par une version antérieure
    sont repris plutôt que perdus.
  - Pas de balayage pour supprimer : on retire un favori par l'étoile de la
    station, là où on l'a mis. Un geste destructeur sur une liste que l'on
    manipule pour la réorganiser se déclencherait par accident.
- **Réglages** (§7.6) : accès aux données hors ligne, thème clair / sombre /
  système appliqué immédiatement, temps forfaitaires de prise et de dépose du
  vélo, adresses du flux de disponibilité et du manifeste des données. Écrits à
  la main plutôt qu'avec `androidx.preference`, dont la grammaire visuelle
  aurait ensuite dû être combattue par les jetons du projet.
  - Les temps forfaitaires sont **relus à chaque calcul d'itinéraire** : les
    changer se voit au recalcul suivant, sans redémarrer.
  - Les deux adresses vidées rétablissent celles de la configuration de ville,
    dont l'invite de saisie montre alors la valeur.
- **« À propos »** (§7.7) : version, politique de confidentialité en clair,
  attributions du §4.5 — dont celle du réseau, lue dans la configuration de
  ville et non écrite dans le code — licence de l'application, lien vers le
  dépôt, et **textes complets des licences embarquées**. Ce dernier point n'est
  pas une courtoisie : le §5 impose de conserver l'avis de copyright et le
  texte MIT de BRouter dans les mentions légales, et la SIL Open Font License
  des deux polices demande la même chose. Le dossier des licences est parcouru
  plutôt qu'énuméré dans le code, pour qu'ajouter une dépendance et sa licence
  ne demande pas de penser à modifier cet écran.
- **Recherche d'itinéraire** (§7.3) : deux points à désigner et un bouton pour
  les intervertir. Les quatre façons du SPEC sont là — sa position, une
  adresse, une station favorite, un point choisi sur la carte. Ce dernier se
  vise sous une mire fixe, la carte se déplaçant dessous, et le point rendu
  porte le nom de la voie que l'index reconnaît plutôt que ses coordonnées.
- **Résultat d'itinéraire** (§7.4) : le tracé en trois segments visuellement
  distincts — les marches en pointillé fin, le vélo en trait plein large, la
  forme portant l'information autant que la couleur. Dessous, le temps total,
  sa répartition, la distance, les trois étapes avec leurs stations et leurs
  disponibilités, les autres couples de stations, et un bouton de recalcul :
  les disponibilités changent, le trajet retenu il y a cinq minutes peut ne
  plus tenir.
  - Quand aucun trajet à vélo n'est possible, l'écran dit lequel des cinq cas
    du §6 s'applique, plutôt que de proposer un trajet impossible.
  - Quand la marche directe va plus vite, il le dit, comme l'exige le §6.
- **Favoris** conservés dans DataStore et choisissables comme point de trajet.
- **Localisation** (§7.1, §10) : bouton « me localiser » sur la carte, qui
  demande la permission au moment de l'usage et jamais au lancement. Un refus
  ne bloque rien et ne déclenche aucune relance. La position vient du
  fournisseur du système — **jamais des services de localisation fusionnés de
  Google**, interdits par la contrainte C2 — et n'est ni écrite, ni envoyée, ni
  conservée d'une session à l'autre.
  - Tous les fournisseurs disponibles sont interrogés **en même temps**, le
    premier relevé l'emportant : le GPS est le plus précis mais reste muet en
    intérieur, où le réseau répond en une seconde. Éprouvé sur appareil — la
    première version, qui n'interrogeait que le GPS, attendait dix secondes
    pour ne rien rendre.
  - La distance depuis la position apparaît dans le détail d'une station dès
    qu'une position est connue, sans jamais la réclamer à cette occasion.
- **Détail d'une station** (§7.2), en feuille glissante depuis le bas, ouverte
  d'un toucher sur la carte comme sur une ligne de la liste : nom, adresse,
  vélos, places, points d'attache, état de service et âge de la donnée. La
  feuille reste vivante tant qu'elle est ouverte — les comptes suivent le
  rafraîchissement plutôt que d'être figés à l'ouverture.
  - **L'adresse vient de l'index hors ligne** : le flux du réseau n'en publie
    pas. En deçà de cinquante mètres l'adresse est nommée avec son numéro,
    au-delà seule la voie est citée comme un voisinage — une station posée au
    milieu d'un rond-point n'a pas d'adresse. Mesuré sur les stations réelles :
    la moitié sont à moins de quinze mètres d'une adresse connue.
  - **Favoris** conservés dans DataStore, par identifiant de station et rien
    d'autre (§8).
  - Un toucher sur un amas de stations rapproche la carte, ce qui finit par le
    résoudre en marqueurs distincts.
- **Recherche d'adresses hors ligne** (§4.3) : index SQLite interrogé sur
  l'appareil, sans le moindre appel réseau, y compris pendant la frappe.
  - Deux étages : index plein texte FTS4 par préfixe, puis rattrapage par
    distance de Damerau-Levenshtein quand le premier rend moins de trois
    résultats. Une faute de frappe, une lettre oubliée ou deux lettres
    interverties retrouvent la rue.
  - Normalisation **partagée avec le script d'indexation** : un seul fichier de
    règles, et un test qui rejoue les cas de référence produits par le script
    pour prouver que les deux implémentations concordent.
  - Numéro de voirie reconnu dans les deux ordres d'écriture, avec son indice
    (« 12 bis rue X » comme « rue X 12 bis »). Un numéro absent de l'index est
    **interpolé entre ses voisins de même parité**, jamais ramené au centre de
    la rue.
  - Classement par qualité de correspondance, la proximité départageant à
    correspondance égale.
  - Écran de recherche avec anti-rebond de 150 ms, chaque frappe annulant le
    calcul précédent ; l'adresse choisie se pose sur la carte.
  - **Communes absorbées** : la Base Adresse Nationale rattache Lomme et
    Hellemmes à Lille, alors que leurs habitants tapent le nom de leur commune.
    L'index porte désormais ce nom — 450 voies concernées — et l'affiche, code
    postal à l'appui : « Rue Danton, 59160 Lomme ».

### Corrigé

- **Les points de repère n'avaient pas de commune.** OpenStreetMap étiquette
  rarement la ville d'une station de métro ou d'une bibliothèque : 2 011 des
  2 436 repères de l'emprise parisienne n'en portaient aucune, et « Châtelet -
  Les Halles » s'affichait sans commune. Chacun reçoit désormais celle de la
  voie la plus proche, trouvée par une grille au kilomètre plutôt qu'en
  comparant toutes les paires. Plus aucun repère sans commune sur les trois
  villes.
- **La génération écrivait toutes les villes au même endroit.** Produire Paris
  effaçait Lille. Chaque ville a désormais son répertoire de sortie, nommé
  d'après l'identifiant de réseau de sa configuration.
- **Les cas de référence de normalisation étaient remplacés** à chaque
  génération, si bien que la dernière ville produite effaçait la preuve
  apportée par la précédente. Ils s'accumulent maintenant, un fichier par
  réseau, et le test les rejoue tous : 54 cas sur deux producteurs.
- **Le calcul d'emprise ne lisait pas le GBFS 3.0** : il cherchait la liste des
  flux sous une clé de langue, que cette version a supprimée. L'outil n'avait
  jamais vu que Lille.

- **Les flux GBFS 1.0 étaient illisibles**, dont celui de Vélib' Métropole —
  mille cinq cents stations, le plus grand réseau de France. Ces flux publient
  l'identifiant de station en nombre là où le format impose une chaîne. La
  conversion prend le texte brut du nombre plutôt que de passer par un entier :
  un identifiant est une étiquette, pas une quantité, et c'est ce qui garantit
  que les deux flux se rejoignent sur la même clé. Éprouvé sur des captures
  réelles du flux de Vélib'.

- **L'écran de recherche d'itinéraire perdait son premier point.** Passer par
  la recherche d'adresse ne détruit que la *vue* du fragment, pas le fragment ;
  relire l'état depuis un paquet d'instance absent effaçait donc les champs
  déjà remplis, et le second point venait écraser le premier. Trouvé en
  essayant l'écran sur un appareil, pas en le relisant.
- **Le test de la recherche d'adresses effaçait l'index installé** sur
  l'appareil qui l'exécutait. Il le met désormais de côté et le rend à la fin.

- **L'import manuel du graphe de routage produisait un fichier inutilisable.**
  Le fichier était renommé `routing.rd5`, alors que BRouter déduit le nom du
  segment des coordonnées cherchées — `E0_N50.rd5` pour Lille — et l'ouvre
  directement. Le graphe restait donc sur le disque sans jamais être lu, et le
  moteur répondait « aucun itinéraire » sans que rien n'indique la cause. Le
  cas était prévu dans le code, mais la branche était devenue inatteignable en
  rendant le nom de fichier non-nullable ; le compilateur le signalait, l'avis
  n'avait pas été suivi.
- Le nom du document importé est désormais retrouvé même quand le fournisseur
  ne publie pas `DISPLAY_NAME`, ce qui est le cas d'une URI `file:`.

### Modifié

- **Version de format des jeux de données portée à 2**, l'index d'adresses
  ayant gagné les colonnes des communes absorbées. Un index en version 1 est
  refusé en le disant, plutôt qu'en échouant à la première recherche.
- La carte **retient son cadrage** quand on la quitte pour un autre écran :
  revenir ramenait jusqu'ici le cadrage d'ouverture, ce qui annulait au passage
  le déplacement vers une adresse trouvée.

### Vérifié

- L'application se lance et affiche les disponibilités réelles du réseau sur
  un émulateur **AOSP sans aucun service Google** — zéro paquet `com.google.*`
  installé, ce qu'exige le critère d'acceptation §11.1.
- En mode avion, les dernières disponibilités connues restent affichées et
  l'application le dit, sans erreur bloquante.
- Un trajet complet marche → vélo → marche est composé en **1,2 s** sur
  l'émulateur, avec les 268 stations réelles et le vrai graphe — pour un budget
  de 3 s (§11.4). L'enchaînement séquentiel demandait 2,4 s.
- La carte s'affiche, se déplace et se zoome **sans aucune requête réseau** :
  tuiles lues sur le disque, glyphes dans l'APK.
- La compilation de release avec R8 produit **2,82 Mo par architecture** et
  fonctionne : les règles de conservation de kotlinx.serialization sont
  correctes, ce qui ne se voit qu'en release.
- **Tolérance aux fautes de frappe** (§11.11), mesurée sur un Fairphone 5 avec
  l'index réel de 10 591 voies : 300 saisies fautives produites au hasard — une
  lettre retirée, deux lettres interverties — sur 150 rues tirées au sort.
  **98,3 %** ramènent la rue demandée dans les trois premiers résultats, et
  **100 %** quand la commune est saisie. Aucune saisie ne reste sans résultat.
- **Temps de réponse de la recherche d'adresses**, même appareil : première
  recherche **102 ms**, chargement du corpus compris ; recherches suivantes
  **2 à 9 ms** quand l'index plein texte répond ; **61 ms de médiane et 81 ms au
  95ᵉ centile** quand le parcours flou se déclenche, pour un maximum de 154 ms.
- **FTS4 et le *tokenizer* `simple`** fonctionnent sur l'appareil, ce que le
  `SPEC.md` §4.3 demandait de vérifier plutôt que de supposer.
- **Le build est reproductible** (§11.15) : deux compilations de release
  successives, précédées d'un `clean`, produisent un APK d'empreinte
  identique — `2c25d5fa38fd6715…`. Vérifié sur une même machine ; la
  reproductibilité d'une machine à l'autre est ce que contrôlera F-Droid.
- **Précision du placement des numéros** (§11.10), mesurée sur l'index réel par
  validation croisée : un numéro est retiré de sa voie, interpolé depuis ses
  voisins, puis comparé à la position que la Base Adresse Nationale lui donne.
  Sur 3 933 numéros tirés au hasard dans 8 524 voies : **erreur médiane 3,3 m**,
  95ᵉ centile 41,4 m, **96,5 % sous les 50 m** exigés. Le repli sur le centre de
  la voie, que l'interpolation existe pour éviter, donnerait 30,7 m de médiane
  et 204 m au 95ᵉ centile. Un numéro **présent** dans l'index, lui, est rendu
  exactement.

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
