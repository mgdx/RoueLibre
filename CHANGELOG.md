# Journal des modifications

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le
projet, le [versionnage sémantique](https://semver.org/lang/fr/).

Les notes destinées aux utilisateurs vivent dans
`fastlane/metadata/android/fr/changelogs/` et sont écrites pour eux, pas pour
les développeurs. Ce fichier-ci s'adresse aux contributeurs et retient aussi ce
qui n'a aucun effet visible.

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
