# Cahier des charges — Roue Libre (application Android)

> **À l'agent :** ce document est la source de vérité du projet. En cas de doute entre ce fichier et une habitude de développement Android « classique », c'est ce fichier qui gagne. Toutes les décisions structurantes ont été tranchées : ne reviens sur aucune sans en discuter d'abord. Si une contrainte te paraît impossible à tenir, dis-le et propose une alternative plutôt que de la contourner en silence.

---

## 1. Objectif

**Roue Libre** — le nom joue sur le double sens de « libre » : libre-service et logiciel libre. Il est **volontairement indépendant de toute ville et de tout réseau**, conformément au §15.

Application Android permettant de :

1. Visualiser sur une carte les stations V'lille (vélos en libre-service de la Métropole Européenne de Lille) avec leur disponibilité en temps réel (vélos disponibles, places libres).
2. Calculer un itinéraire porte-à-porte combiné : **marche → vélo → marche**, en choisissant automatiquement la meilleure station de départ et la meilleure station d'arrivée.

L'application est un outil personnel, sobre et rapide. Ce n'est pas une application de réservation : elle ne dialogue jamais avec le compte utilisateur V'lille.

## 2. Contraintes non négociables

| # | Contrainte | Conséquence |
|---|---|---|
| C1 | **Open source**, publiée sur F-Droid | Licence libre, build reproductible, aucune dépendance propriétaire |
| C2 | **Aucun service Google** | Interdit : Google Play Services, Firebase, FCM, Maps SDK, ML Kit, Crashlytics, Play Integrity. L'appli doit fonctionner sur LineageOS sans GApps |
| C3 | **Vie privée** | Aucune télémétrie, aucun tracker, aucun identifiant unique, aucun compte. La position ne quitte jamais l'appareil |
| C4 | **Légèreté** | APK cible **< 15 Mo** (**< 12 Mo** par architecture), hors données téléchargées. Toute dépendance ajoutée doit être justifiée dans le README |
| C5 | **Fonctionnement hors ligne complet** | Carte, recherche d'adresses et calcul d'itinéraire fonctionnent **sans aucun réseau**. Seule la disponibilité temps réel des stations nécessite une connexion |
| C6 | **Français par défaut, traduisible** | Voir §9 |

L'appli doit passer le scan **F-Droid / Exodus Privacy** sans aucun tracker détecté.

## 3. Stack technique

- **Langage :** Kotlin
- **UI :** Vues XML + ViewBinding + Material Components. **Pas de Jetpack Compose** (poids, contrainte C4)
- **minSdk : 26** (Android 8.0) — **targetSdk :** la dernière version stable. Justification : `java.time` disponible nativement (pas de *desugaring* à configurer), icônes adaptatives, et surtout une pile TLS à jour, indispensable pour télécharger les jeux de données sans se heurter aux magasins de certificats obsolètes des versions antérieures. Vérifier que le minSdk requis par MapLibre Native n'est pas supérieur.
- **Réseau :** OkHttp + `kotlinx.serialization`. Pas de Retrofit, pas de Gson, pas de Moshi
- **Cartographie :** MapLibre Native, alimenté par un fichier de tuiles vectorielles **local** (voir §4.2). C'est la seule dépendance native du projet, et la seule entorse assumée à la contrainte C4 : elle est le prix du hors-ligne
- **Asynchrone :** Coroutines + Flow
- **Persistance :** Room pour le cache des stations, `DataStore` (Preferences) pour les réglages
- **Architecture :** MVVM simple, une seule activité, navigation par fragments. Pas d'injection de dépendances par framework (Hilt/Koin) : instanciation manuelle via un `AppContainer`
- **Build :** Gradle Kotlin DSL, R8 activé en release, `shrinkResources true`, **splits par ABI** (les bibliothèques natives de MapLibre ne doivent pas être livrées quatre fois dans le même APK)
- **`applicationId` :** de la forme `io.github.<compte>.rouelibre`. Ne pas inventer un identifiant fondé sur un domaine qui n'appartient pas au projet — c'est irréversible une fois l'application publiée.
- **Licence :** **GPLv3**. Toute dépendance ajoutée doit être compatible avec cette licence — à vérifier avant intégration, en particulier pour le moteur de routage (§5)

Aucune bibliothèque d'analytics, de crash reporting ou de publicité, sous aucun prétexte.

## 4. Sources de données

### Emprise géographique de référence

Tous les jeux de données hors ligne — tuiles, graphe de routage, index d'adresses — partagent **une seule et même emprise**, définie une fois pour toutes dans les scripts de génération.

Attention au contresens : la Métropole Européenne de Lille n'est pas la ville de Lille. C'est un ensemble de **95 communes sur près de 672 km²**, allant jusqu'à la frontière belge, et comprenant aussi bien Roubaix, Tourcoing, Villeneuve-d'Ascq ou Seclin que des communes rurales des Weppes et de la Pévèle.

**L'emprise ne doit pas être la limite administrative de la MEL**, qui couvrirait de vastes zones rurales sans aucune station et alourdirait inutilement les trois jeux de données. Elle est **dérivée des stations elles-mêmes** :

1. calculer le rectangle englobant l'ensemble des stations présentes dans `station_information.json` ;
2. l'élargir d'une **marge de 3 km**, pour couvrir les trajets à pied depuis ou vers la périphérie du réseau et éviter les effets de bord du calcul d'itinéraire près des limites du graphe.

Cette emprise est **recalculée à chaque régénération des données**, ce qui suit automatiquement les extensions du réseau. Elle est inscrite dans le fichier de configuration de ville (§15) et affichée dans l'écran « stockage ». Aucune coordonnée d'emprise n'est écrite en dur dans le code de l'application.

Conséquence à assumer : hors de cette emprise, la carte et le calcul d'itinéraire ne fonctionnent pas. L'application doit le détecter et le dire clairement, jamais échouer silencieusement.

### 4.1 Disponibilité des stations — GBFS

La MEL publie la disponibilité au **format GBFS** (standard international du vélo en libre-service), rafraîchie **toutes les minutes**.

Fichiers utilisés :

- `gbfs.json` — fichier d'auto-découverte listant les autres flux
- `station_information.json` — données statiques : identifiant, nom, latitude/longitude, capacité
- `station_status.json` — données temps réel : vélos disponibles, places libres, station en service ou non

**Règles d'implémentation :**

- L'URL du `gbfs.json` **ne doit pas être devinée**. L'agent doit la récupérer depuis la fiche du jeu de données sur `transport.data.gouv.fr` (Point d'Accès National) ou depuis le catalogue `systems.csv` de MobilityData, puis la vérifier par une requête réelle avant de l'inscrire dans le code.
- Toutes les URL de flux passent par le fichier d'auto-découverte, jamais en dur : c'est le principe de GBFS et cela protège des changements d'URL côté producteur.
- L'URL du `gbfs.json` doit être **modifiable dans les réglages**. Conséquence heureuse : l'appli fonctionne avec n'importe quel réseau GBFS du monde sans modification de code.
- Ne pas utiliser les anciennes API `vlille-realtime` ni les wrappers JSON tiers que l'on trouve sur GitHub : ils sont **dépréciés**.

**Politique de rafraîchissement :**

- `station_information.json` : mis en cache en base, rafraîchi au maximum une fois par jour (respecter le `ttl` du flux).
- `station_status.json` : rafraîchi à l'ouverture de l'écran carte, puis au maximum toutes les 60 s tant que l'écran est visible, et sur geste de tirer-pour-rafraîchir. **Aucun rafraîchissement en arrière-plan**, aucun `WorkManager` périodique.
- L'âge de la donnée doit être affiché à l'utilisateur (« il y a 12 s »).
- Hors ligne : afficher le dernier état connu, clairement marqué comme périmé.

### 4.2 Fond de carte

Rendu **vectoriel hors ligne** via MapLibre Native. Aucune requête de tuile ne part vers un serveur pendant l'usage.

- Format : **MBTiles** (base SQLite contenant les tuiles). MapLibre Native le lit directement depuis le disque via le schéma d'URI `mbtiles://`, utilisable tel quel dans le style — c'est le chemin le mieux supporté. PMTiles a été écarté : son atout est de servir des tuiles depuis un hébergement statique par requêtes HTTP *Range*, ce qui ne sert à rien ici puisque le fichier est téléchargé en entier, et ses sources ne gèrent ni les paquets hors ligne ni la mise en cache côté MapLibre Native.
- Emprise : celle définie en tête du §4. Zoom **10 à 16**. Le zoom 16 suffit largement pour se repérer dans une rue ; monter à 17 ou 18 ferait exploser la taille pour un gain nul dans cette application.
- Le fichier de tuiles n'est **pas dans l'APK** : il est téléchargé au premier lancement (voir §4.5). Ordre de grandeur attendu pour une agglomération de taille moyenne : **30 à 60 Mo**. Ce n'est pas un plafond : une métropole dense en produit légitimement davantage — Paris, avec 1,24 million d'empreintes de bâtiments dans son emprise contre 78 000 pour Lille, en produit 115 Mo. Les règles de rendu restent les mêmes pour toutes les villes ; on ne taille pas d'exception ville par ville.
- Style de carte : un style sobre, embarqué dans l'APK sous forme de JSON, avec les polices et icônes nécessaires. Pas de style téléchargé depuis un service tiers.
- Hébergement du fichier : voir §4.4. La **procédure de régénération** doit être documentée et scriptée dans le dépôt, pour que le fichier puisse être mis à jour sans dépendre de personne.

**Contenu de la carte.** Le filtrage se fait **à la génération** des tuiles, pas seulement dans le style : ce qui n'est pas retenu ne pèse rien. C'est le principal levier de taille après le niveau de zoom, et il sert autant la sobriété visuelle que la légèreté.

Sont retenus, parce qu'ils servent à se repérer :

- **transports** : stations de métro et de tramway (à tous les zooms), gares ferroviaires, arrêts de bus (**à partir du zoom 15 seulement**, en points discrets et sans étiquette — la métropole en compte plusieurs milliers, les afficher plus tôt noierait la carte et les stations avec) ;
- **équipements publics** : mairies, écoles, collèges, lycées, universités et grandes écoles, hôpitaux et cliniques, bureaux de poste, bibliothèques, médiathèques, piscines, gymnases, cimetières ;
- **repères visuels** : monuments, églises et édifices religieux, musées, théâtres, beffrois, statues et éléments remarquables ;
- **trame urbaine** : parcs et espaces verts, cours d'eau et canaux, voies ferrées, noms de rues, limites communales, noms de communes et de quartiers ;
- **empreintes de bâtiments**, mais **à partir du zoom 15 uniquement** et en aplat discret. C'est souvent la couche la plus lourde d'un jeu de tuiles vectorielles : si le budget de taille est dépassé, c'est le premier levier à actionner.

Sont **écartés**, parce qu'ils encombrent sans servir : commerces, restaurants, bars, cafés, hôtels, banques, distributeurs, coiffeurs, agences, bureaux d'entreprises, stations-service, parkings privés, et l'ensemble des points d'intérêt commerciaux. Cette exclusion est un **choix de conception assumé**, pas une omission : la carte est un décor, les stations sont le sujet (§7).

La liste retenue doit vivre dans un **fichier de configuration lisible** du script de génération, pour être ajustée sans replonger dans le code.
- Prévoir dans les réglages une entrée « source des données cartographiques » permettant de pointer vers une autre URL ou d'importer un fichier local.

### 4.3 Recherche d'adresses — index local

La recherche d'adresses se fait **entièrement sur l'appareil**. Aucun géocodeur en ligne, aucune requête tierce : c'est la donnée la plus sensible de l'application, puisqu'elle révèle où va l'utilisateur.

- Source : la **Base Adresse Nationale**, extraits départementaux librement téléchargeables.
- **Granularité : le numéro de voirie.** Certaines artères lilloises font plus d'un kilomètre : un point unique par rue produirait une erreur de plusieurs centaines de mètres, suffisante pour désigner la mauvaise station et donc un itinéraire faux. La précision au numéro est donc une exigence, pas un confort.
- L'index forme **un seul paquet téléchargé** avec les autres jeux de données (§4.4), rien n'est embarqué dans l'APK :
  - **Voies** — une entrée par rue : nom, commune, code postal, point représentatif. Environ 15 à 20 000 entrées sur l'emprise de référence, **1 à 3 Mo**.
  - **Numéros** — une entrée par adresse, rattachée à une voie. Environ 450 à 550 000 entrées sur l'emprise de référence, **12 à 25 Mo** selon l'encodage. Ces chiffres correspondent à la zone dense couverte par les stations ; retenir l'intégralité des 95 communes de la MEL les augmenterait sensiblement pour aucun usage réel.
- Encodage : ne pas stocker un texte par adresse. Une entrée de numéro = référence de voie + numéro (entier + éventuel indice `bis`, `ter`, `A`) + coordonnées **encodées en delta par rapport au point de la voie**, sur deux entiers courts. On vise ainsi le bas de la fourchette plutôt que le haut.
- Si le numéro saisi n'existe pas dans l'index, **interpoler** entre les deux numéros connus les plus proches de la même voie plutôt que de retomber sur le centre de la rue.
- Ajouter également les **points d'intérêt utiles au repérage** : gares, stations de métro, universités, hôpitaux, grandes places. Quelques milliers d'entrées supplémentaires, extraites d'OpenStreetMap, traitées comme des voies.
- Implémentation : **SQLite FTS uniquement sur les noms de voies** — c'est ce qui rend l'ensemble viable. Les numéros ne sont jamais recherchés en texte intégral : une fois la voie identifiée, le numéro se résout par un simple index sur (voie, numéro). Recherche insensible à la casse et aux accents, tolérante aux abréviations courantes (« bd », « av », « st »), résultats classés par proximité avec la position courante.
- **Tolérance aux fautes de saisie.** La recherche doit retrouver une rue malgré une faute de frappe, une lettre oubliée ou deux lettres interverties. Mise en œuvre en deux étages :
  1. **Normalisation**, appliquée aussi bien à l'index qu'à la saisie : minuscules, accents et ponctuation supprimés, abréviations développées (« st » → « saint », « bd » → « boulevard », « av » → « avenue », « fbg » → « faubourg »). Le **type de voie est stocké dans un champ distinct** du nom propre, pour que « gambetta » trouve « rue Gambetta » et que « rue de la gare » ne soit pas pénalisé par l'ordre des mots. Recherche par préfixe sur chaque mot, ce qui couvre la frappe en cours.
  2. **Rattrapage par distance d'édition** lorsque le premier étage donne moins de résultats qu'attendu : distance de **Damerau-Levenshtein** — elle traite les interversions de lettres, faute la plus courante au clavier tactile — calculée en Kotlin sur les noms normalisés maintenus en mémoire. Le corpus étant de l'ordre de 20 000 entrées et moins d'un mégaoctet, un parcours complet reste de l'ordre de quelques dizaines de millisecondes.
- Seuil de tolérance **proportionnel à la longueur** : une faute admise en dessous de huit caractères, deux au-delà. Au-delà, le bruit dépasse le service rendu.
- Recherche **déclenchée avec un délai anti-rebond** (environ 150 ms) et **annulable** : chaque frappe annule le calcul précédent. Aucun calcul sur le fil principal.
- **Classement des résultats** par score combiné : qualité de la correspondance d'abord, proximité avec la position courante ensuite. À égalité de correspondance, la rue la plus proche passe devant.
- Ne pas dépendre du *tokenizer* trigramme de SQLite : il est absent des versions embarquées dans les Android les plus anciens que vise l'application. Le flou se fait en Kotlin. Vérifier de même la disponibilité effective de la version de FTS retenue sur un appareil à l'API 26, et prévoir un repli.
- Documenter le script de génération de l'index dans le dépôt, pour permettre sa régénération et son extension à d'autres agglomérations.

### 4.4 Téléchargement initial des données

Au premier lancement, un écran explique clairement ce qui va être téléchargé, pour quelle taille, et demande confirmation :

- fond de carte vectoriel de la métropole (§4.2) ;
- données de routage (§5) ;
- index d'adresses, voies et numéros (§4.3).

Les trois jeux forment **un ensemble cohérent, versionné et publié ensemble**. L'application ne dispose d'aucune donnée géographique tant qu'ils ne sont pas installés : avant cela, elle se limite à la liste des stations et à leurs disponibilités, et le dit clairement.

Contraintes : téléchargement **repris en cas d'interruption**, avertissement si l'utilisateur n'est pas en Wi-Fi, possibilité de reporter (l'appli reste alors utilisable en mode dégradé : liste des stations et disponibilités, sans carte ni itinéraire), et vérification d'intégrité du fichier téléchargé.

**Import manuel obligatoire.** Chaque jeu de données doit pouvoir être **fourni à la main** depuis un fichier présent sur l'appareil, sans aucun téléchargement. L'utilisateur qui génère lui-même ses fichiers, ou les copie par câble, doit pouvoir installer et utiliser l'application sans qu'elle n'émette la moindre requête vers un serveur de données. Le téléchargement est le chemin par défaut, jamais le seul chemin.

**Hébergement des fichiers de données : les *releases* du dépôt GitHub**, en fichiers attachés. La limite de taille par fichier y est très largement supérieure à nos besoins.

Règles associées :

- Les données sont publiées comme des **releases distinctes de celles de l'application** (par exemple étiquetées `data-2026-08`), afin qu'une mise à jour du fond de carte n'oblige pas à publier une version de l'appli, et inversement.
- Chaque publication de données est décrite par un **fichier manifeste** (quelques kilooctets) listant, pour chacun des trois jeux : son identifiant, sa version, sa date de génération, son URL, sa taille et son **empreinte SHA-256**. Le manifeste porte aussi l'emprise géographique et la version de format attendue par l'application.
- **Mise à jour par comparaison d'empreintes.** L'application conserve l'empreinte de chaque fichier installé, récupère le manifeste, et ne retélécharge **que les jeux dont l'empreinte a changé**. Rafraîchir uniquement l'index d'adresses ne doit jamais imposer de reprendre les 60 Mo de tuiles.
- L'empreinte est **revérifiée après téléchargement** : un fichier qui ne correspond pas au manifeste est rejeté, et l'ancienne version conservée. Une mise à jour interrompue ou corrompue ne doit jamais laisser l'application dans un état inutilisable — écrire le nouveau fichier à côté, valider, puis remplacer.
- **La vérification n'est jamais automatique en arrière-plan** : elle a lieu sur action explicite de l'utilisateur, depuis l'écran « stockage », qui affiche la date de sa dernière exécution. Une requête périodique dessinerait un profil d'usage de l'application, ce que la contrainte C3 exclut.
- Si le manifeste annonce une **version de format** que l'application ne sait pas lire, le dire clairement et inviter à mettre à jour l'application, plutôt que d'échouer à l'ouverture d'un fichier.
- L'application ne code en dur qu'une URL par défaut, **modifiable dans les réglages**, et sait de toute façon fonctionner par import manuel (voir plus haut). L'hébergeur ne doit jamais être un point de défaillance unique.
- Le `User-Agent` des téléchargements identifie l'application et sa version, sans aucun identifiant propre à l'utilisateur.

Dans tous les cas, deux garde-fous sont **obligatoires**, pour que l'application survive à la disparition de l'hébergeur : le réglage permettant de pointer vers une autre URL ou d'importer un fichier local (§4.2), et les scripts de régénération versionnés dans le dépôt. L'URL par défaut ne doit jamais être un point de défaillance unique.

Un écran « stockage » doit lister chaque jeu de données avec sa taille, sa date, un bouton de mise à jour et un bouton de suppression. L'utilisateur doit toujours savoir ce que l'appli occupe et pouvoir le reprendre.

### 4.5 Attribution (obligatoire)

Un écran « À propos » doit afficher :

- l'attribution et la licence des données V'lille (Métropole Européenne de Lille) ;
- « © les contributeurs OpenStreetMap » ;
- l'attribution du moteur de routage et de ses données ;
- la licence de l'application et le lien vers le dépôt.

L'attribution OpenStreetMap doit également être visible **sur la carte elle-même**.

## 5. Moteur de routage (hors ligne)

**Recommandation : BRouter**, moteur de calcul d'itinéraire vélo hors ligne, éprouvé sur Android, orienté cyclisme, avec profils paramétrables.

- **Licence : MIT** (vérifié). Compatible avec la GPLv3 de l'application : le code MIT peut être intégré dans un ensemble GPLv3, à condition de **conserver l'avis de copyright et le texte de la licence MIT** dans les mentions légales de l'appli. Attention : plusieurs pages tierces et anciens dépôts *brouter-web* décrivent encore BRouter comme GPLv3 — c'est obsolète, le fichier `LICENSE` du dépôt `abrensch/brouter` fait foi.
- Intégrer le **cœur de BRouter comme bibliothèque** dans l'application, plutôt que de dépendre de l'application BRouter installée séparément (l'utilisateur ne doit avoir qu'une seule appli à installer). Le module est publié comme artefact Maven : `org.btools:brouter-core`. Vérifier la version courante et privilégier cette dépendance à une copie de sources dans le dépôt.
- Les **données de routage** ne sont **pas embarquées dans l'APK** : elles sont téléchargées au premier lancement (§4.4).
- **Priorité : générer un jeu de données limité à l'emprise de référence définie en tête du §4** plutôt que d'utiliser les segments de 5°×5° distribués par BRouter, qui couvrent une bonne partie du nord de l'Europe. On passe ainsi d'environ 100–170 Mo à **15–40 Mo**. C'est le seul poste où l'on divise le poids par cinq, donc il vaut l'effort d'intégration. Le script de génération doit être versionné dans le dépôt.
- Si cette découpe s'avère impraticable, le signaler et proposer le repli sur les segments standard **avant** de l'implémenter.
- Deux profils sont nécessaires : **piéton** (trajets d'accès) et **vélo urbain** (trajet principal).

Si l'agent estime BRouter inadapté après investigation, il doit **proposer une alternative et attendre validation**, pas décider seul.

## 6. Algorithme du trajet optimisé

C'est le cœur métier de l'application. À implémenter dans une classe isolée et testable, sans dépendance à Android.

**Entrées :** point de départ, point d'arrivée, état courant des stations.

**Principe :** ne jamais se contenter de « la station la plus proche ». Optimiser le **couple** station de départ / station d'arrivée.

1. Sélectionner les **N stations candidates au départ** (par défaut N = 5) parmi les plus proches du point de départ ayant `num_bikes_available ≥ 1` et étant en service.
2. Sélectionner les **M stations candidates à l'arrivée** (par défaut M = 5) parmi les plus proches du point d'arrivée ayant `num_docks_available ≥ 1` et étant en service.
3. Pour chaque couple (départ, arrivée), calculer : temps de marche vers la station de départ + temps de vélo entre stations + temps de marche vers la destination.
4. Ajouter un **temps forfaitaire de prise et de dépose du vélo** (par défaut 2 min de chaque côté, configurable).
5. Retenir le couple au temps total minimal, et proposer **les 3 meilleures alternatives** à l'utilisateur.

**Fiabilité — règles importantes :**

- Une station avec 1 seul vélo peut se vider pendant le trajet à pied. Appliquer une **pénalité de risque** croissante quand la disponibilité est faible : une station à 1 vélo est moins attractive qu'une station à 8 vélos, même un peu plus loin. Idem à l'arrivée pour les places libres.
- Toujours afficher le nombre de vélos ou de places de la station retenue, pour que l'utilisateur juge lui-même.
- Si le trajet à vélo est plus lent que la marche directe, **le dire** et proposer l'itinéraire piéton.
- Le nombre de calculs d'itinéraire (N × M + marches) doit rester borné : viser un résultat en **moins de 3 secondes** sur un appareil milieu de gamme, calcul lancé hors du thread principal et annulable.

## 7. Écrans

### Identité visuelle et principes d'interface

L'application doit être **soignée, épurée et vivante** — pas une interface Material par défaut. Mais « fais quelque chose de beau » ne produit qu'un résultat générique : la direction ci-dessous est donc contraignante.

**Système de jetons.** Avant d'écrire la moindre vue, produire un fichier de jetons de conception et le faire valider :

- une palette de **4 à 6 couleurs nommées** (fond, encre, accent, plus l'échelle de disponibilité) ;
- **deux familles typographiques** : une avec du caractère pour les chiffres et les titres — les nombres de vélos sont l'information centrale de cette application, ils méritent un traitement mémorable — et une neutre et lisible pour le reste ;
- une **échelle d'espacement** et un rayon d'arrondi uniques, appliqués partout.

Aucune couleur ni taille ne doit être écrite en dur dans un layout : tout passe par les ressources.

**Élément signature.** Dépenser l'audace à un seul endroit : l'**indicateur de disponibilité** des stations. C'est ce que l'utilisateur regarde cent fois par semaine, c'est ce dont il se souviendra. Il doit se lire instantanément, en marchant, au soleil, d'un coup d'œil. Tout le reste de l'interface est calme et discipliné autour de lui.

**À éviter explicitement**, parce que ce sont des réflexes et non des choix : le fond crème avec accent terre cuite, le fond noir avec un unique accent vert acide, l'empilement de cartes à ombre portée sur fond gris. Si une décision de style pourrait s'appliquer telle quelle à n'importe quelle autre application, c'est qu'elle n'a pas été prise.

**Le style de carte est un objet de design**, pas un réglage technique. Le fond doit être **désaturé** pour que les marqueurs ressortent : la carte est un décor, les stations sont le sujet.

**Contraintes de qualité, non négociables :** thèmes clair et sombre tous deux soignés ; cibles tactiles d'au moins 48 dp ; contrastes conformes aux recommandations d'accessibilité ; libellés de contenu sur tous les éléments interactifs ; préférence système « réduire les animations » respectée ; interface utilisable à une main, les commandes principales à portée du pouce.

**Mouvement :** au service de la compréhension uniquement — apparition des marqueurs, tracé de l'itinéraire, transition vers le détail d'une station. Rien d'ambiant, rien de décoratif.

**Écriture de l'interface :** phrases courtes, voix active, une action porte le même nom du bouton jusqu'à la confirmation. Un message d'erreur dit ce qui s'est passé et quoi faire, sans s'excuser ni rester vague. Un écran vide est une invitation à agir, pas un constat.

**Le nom « V'lille » n'apparaît pas dans l'identité visuelle** : ni sa couleur de marque, ni son logo, ni sa typographie. L'application a son identité propre — c'est une exigence de portabilité (§15) autant qu'une prudence sur les marques.

### 7.1 Carte (écran principal)

- Carte plein écran centrée sur la métropole lilloise, ou sur la position de l'utilisateur si la permission est accordée.
- Un marqueur par station, **lisible d'un coup d'œil** : le code couleur reflète la disponibilité (aucun vélo / peu / correct / station hors service). La couleur seule ne doit jamais porter l'information : ajouter le chiffre ou une forme distincte (accessibilité, daltonisme).
- Bascule **« vélos » / « places »** : selon que l'utilisateur cherche à emprunter ou à rendre.
- Regroupement des marqueurs aux niveaux de zoom éloignés.
- Bouton « me localiser », bouton « rafraîchir », accès aux réglages et à la recherche d'itinéraire.
- Indicateur d'âge de la donnée.

### 7.2 Détail d'une station

Feuille glissante depuis le bas : nom, adresse, vélos disponibles, places libres, capacité totale, état, distance depuis la position, horodatage. Actions : mettre en favori, définir comme départ ou comme arrivée d'un itinéraire, ouvrir dans une appli de navigation externe.

### 7.3 Recherche d'itinéraire

- Deux champs : départ et arrivée. Chacun accepte : ma position, un favori, un point choisi sur la carte, une adresse.
- La recherche d'adresses interroge **l'index local** décrit au §4.3. Aucun appel réseau, aucune suggestion envoyée à un tiers, y compris pendant la frappe.
- Bouton d'inversion départ/arrivée.

### 7.4 Résultat d'itinéraire

- Tracé sur la carte en trois segments visuellement distincts : marche, vélo, marche.
- Résumé : temps total, dont marche et vélo, distance, station de départ (avec nombre de vélos), station d'arrivée (avec nombre de places).
- Liste des étapes, et accès aux 3 alternatives.
- Bouton de recalcul (les disponibilités ont pu changer).

### 7.5 Favoris

Liste des stations mises en favori, avec leur disponibilité en direct. Réorganisable.

### 7.6 Réglages

URL du flux GBFS, serveur de tuiles, gestion des données de routage, temps forfaitaires de prise/dépose, thème clair/sombre/système, activation du géocodage.

### 7.7 À propos

Attributions (§4.3), version, lien vers le dépôt, politique de confidentialité en clair.

### 7.8 Ouverture depuis une autre application

L'application doit apparaître dans le sélecteur d'Android lorsqu'un lieu est ouvert ou partagé depuis une autre application, et pouvoir être retenue comme choix par défaut. Le lieu reçu devient directement la **destination** d'un nouvel itinéraire.

**Points d'entrée à déclarer :**

- `ACTION_VIEW` sur le schéma **`geo:`**, avec les catégories `DEFAULT` et `BROWSABLE`. Toutes les formes doivent être acceptées : `geo:<lat>,<lon>`, avec paramètre de zoom, `geo:0,0?q=<lat>,<lon>(<libellé>)`, et `geo:0,0?q=<adresse en texte>` — cette dernière étant résolue par l'index local (§4.3), **sans aucun appel réseau**.
- `ACTION_VIEW` sur le schéma **`google.navigation:`**, encore émis par de nombreuses applications.
- **`ACTION_SEND`** de texte brut : détecter dans le texte partagé un couple de coordonnées ou une adresse. C'est le cas d'usage le plus fréquent en pratique — une adresse reçue par messagerie.
- Liens web cartographiques : à traiter, mais en sachant qu'ils **ne peuvent pas être vérifiés automatiquement**, les domaines concernés n'appartenant pas au projet. Depuis Android 12, ils ne parviennent à l'application que si l'utilisateur l'autorise dans les paramètres système. Documenter cette manipulation dans l'écran « À propos » et dans le `README.md`, sans quoi le comportement passera pour un défaut.

**Comportement attendu :**

- Ouvrir directement l'écran de résultat d'itinéraire, destination pré-remplie, départ à la position courante. Si la localisation est refusée ou indisponible, ouvrir l'écran de recherche avec seulement la destination renseignée.
- Afficher le libellé reçu s'il y en a un, plutôt que des coordonnées brutes.
- Si le point est **hors de l'emprise** (§4), le dire clairement et proposer de l'afficher malgré tout sur la carte si les données le permettent, sans tenter de calcul d'itinéraire.
- Si les jeux de données ne sont pas encore installés, l'expliquer et proposer le téléchargement, plutôt que d'échouer.
- Une intention entrante ne déclenche **jamais** de requête réseau autre que le rafraîchissement normal des disponibilités.
- L'application ne doit pas s'installer comme gestionnaire par défaut d'elle-même : le choix appartient à l'utilisateur, via le sélecteur d'Android.

Aucune permission supplémentaire n'est nécessaire pour tout ceci.

### 7.9 Premier lancement

Au tout premier démarrage, un écran d'accueil — **pas une boîte de dialogue** : le contenu est trop dense pour une fenêtre modale, et il doit pouvoir être relu depuis « À propos » — présente l'application en quelques phrases courtes :

- application **libre et ouverte**, sans compte, sans publicité, sans mouchard ;
- **aucune donnée personnelle ne quitte l'appareil** : les itinéraires sont calculés sur le téléphone, les destinations recherchées ne sont envoyées à personne, aucun historique n'est conservé ;
- **fonctionnement hors ligne** : la carte, les rues, les points d'intérêt et le calcul d'itinéraire résident sur l'appareil, ce qui suppose un **téléchargement initial** ; seule la disponibilité des vélos en temps réel nécessite ensuite une connexion.

Cet écran enchaîne directement sur la confirmation de téléchargement décrite au §4.4, avec la taille annoncée — **une seule séquence, pas deux murs de texte successifs**. Trois écrans au maximum, chacun contournable, et un bouton pour reporter le téléchargement.

Le ton est celui du §7 : phrases courtes, voix active, aucun jargon. On explique un fonctionnement, on ne vend rien.

### 7.10 Nouveautés après mise à jour

Après l'installation d'une nouvelle version, un écran de **nouveautés** s'affiche **une seule fois**, listant corrections et améliorations depuis la version précédemment installée.

- L'application mémorise le dernier code de version vu. Si l'écart couvre plusieurs versions, présenter les notes de **toutes** les versions intermédiaires, de la plus récente à la plus ancienne.
- **Jamais affiché lors d'une première installation** : c'est l'écran §7.9 qui s'applique alors.
- Toujours accessible ensuite depuis « À propos ».
- Les notes sont **embarquées dans l'APK**, jamais téléchargées : aucune requête réseau ne doit être déclenchée par cet écran.
- **Source unique de vérité** : les notes de version des métadonnées F-Droid (`fastlane/metadata/android/fr/changelogs/<versionCode>.txt`). Elles sont converties en ressource embarquée **au moment du build**, pour que F-Droid et l'application affichent exactement le même texte sans double saisie.
- Rédiger ces notes **pour l'utilisateur, pas pour le développeur** : « la recherche d'adresse tolère désormais les fautes de frappe », et non « refactorisation du module de géocodage ». Chaîne traduisible comme le reste.

## 8. Stockage et modèle de données

- **Room** : table des stations (données statiques) + table du dernier état connu.
- **DataStore** : réglages et favoris (identifiants de stations).
- **Aucune donnée de trajet n'est conservée** : ni historique, ni positions, ni destinations. Les itinéraires calculés vivent en mémoire le temps de la session.
- Aucune sauvegarde automatique vers le cloud : `android:allowBackup="false"`.

## 9. Internationalisation

- **Zéro chaîne de caractères en dur** dans le code Kotlin ou les layouts. Tout dans `res/values/strings.xml`, qui constitue la **langue par défaut : le français**.
- Utiliser `<plurals>` pour tout ce qui s'accorde (« 1 vélo disponible » / « 3 vélos disponibles »).
- Utiliser des **placeholders positionnels** (`%1$s`, `%2$d`) et jamais de concaténation de chaînes : l'ordre des mots change d'une langue à l'autre.
- Ajouter des commentaires `<!-- -->` au-dessus des chaînes ambiguës, pour les futurs traducteurs.
- Prévoir `res/values-en/` vide ou traduit en exemple, pour montrer la marche à suivre.
- Formater dates, heures, distances et durées via les API de localisation, pas à la main.
- Layouts compatibles avec les langues écrites de droite à gauche (`start`/`end` plutôt que `left`/`right`).
- Prévoir un fichier `CONTRIBUTING.md` expliquant comment proposer une traduction.

## 10. Permissions

Permissions demandées, et **aucune autre** :

- `INTERNET` — récupération des flux GBFS et des tuiles
- `ACCESS_COARSE_LOCATION` et `ACCESS_FINE_LOCATION` — demandées **au moment de l'usage**, jamais au lancement
- `ACCESS_NETWORK_STATE` — détection du mode hors ligne

L'application doit être **pleinement utilisable si la permission de localisation est refusée** : l'utilisateur désigne alors ses points de départ et d'arrivée à la main. Le refus ne doit jamais bloquer un écran ni déclencher de relance insistante.

La géolocalisation utilise le fournisseur de position du système Android, **pas** les services de localisation fusionnés de Google.

## 11. Critères d'acceptation

Chaque critère doit être vérifiable :

1. L'appli s'installe et fonctionne sur un appareil sans services Google.
2. Les stations s'affichent sur la carte avec des disponibilités cohérentes avec le site officiel.
3. **En mode avion**, une fois les données téléchargées : la carte s'affiche, la recherche d'adresses fonctionne, un itinéraire complet se calcule. Seules les disponibilités sont figées sur le dernier état connu, explicitement marqué comme périmé. Aucune erreur bloquante.
4. Un itinéraire entre deux points de la métropole renvoie un trajet marche → vélo → marche en moins de 3 secondes.
5. La station de départ proposée a toujours au moins 1 vélo ; la station d'arrivée au moins 1 place.
6. Quand aucune station proche n'a de vélo, l'appli le dit explicitement au lieu de proposer un trajet impossible.
7. En usage courant, **la seule requête réseau qui part est celle du flux GBFS** — à vérifier avec un pare-feu ou une capture de trafic. Les téléchargements de données n'ont lieu qu'au premier lancement ou sur action explicite de l'utilisateur.
8. L'analyse Exodus Privacy ne détecte aucun tracker.
9. L'APK de release pèse moins de 15 Mo, et moins de 12 Mo par architecture. Les données téléchargées, elles, **n'ont pas de plafond fixe** : leur poids suit la taille et la densité du réseau servi, et une capitale pèse légitimement plus qu'une ville moyenne. Ce qui est exigé est que leur poids reste **raisonnable au regard de la ville couverte**, que l'application **annonce la taille avant de télécharger**, et qu'elle permette de **supprimer les données d'une ville** pour reprendre la place. À titre de repère : environ 40 Mo pour Lille ou Lyon, environ 140 Mo pour Paris.
10. Une adresse avec numéro dans une longue artère est localisée à moins de 50 m de sa position réelle.
11. Une recherche comportant une faute de frappe ou une lettre manquante retrouve la rue visée dans les trois premiers résultats.
12. Un lien `geo:` ouvert depuis une autre application propose l'application dans le sélecteur et pré-remplit la destination.
13. Le basculement du système en anglais ne casse aucune mise en page (avec `values-en/` de test).
14. Toutes les attributions sont présentes.
15. Le build est reproductible : deux compilations successives produisent le même APK.

## 12. Livrables

- Dépôt Git avec historique de commits propre et atomique
- `README.md` : **bilingue par en-tête**. Il s'ouvre sur un court paragraphe **en anglais** — trois ou quatre phrases — expliquant ce qu'est l'application, qu'elle vise avant tout un public français, et que la suite du document est donc rédigée en français. Le reste est **entièrement en français** : description, captures d'écran, architecture et schéma des couches, instructions de compilation, génération des jeux de données, justification de chaque dépendance, procédure de portage vers une autre ville (§15). Cet en-tête anglais rend le dépôt intelligible pour un visiteur étranger sans imposer une traduction intégrale à maintenir en double.
- `CONTRIBUTING.md` incluant la procédure de traduction
- Fichier de licence
- Métadonnées F-Droid (`fastlane/metadata/android/fr/`) : description courte, description longue, notes de version, captures d'écran
- Tests unitaires sur l'algorithme du §6 et sur l'analyse des flux GBFS
- APK de release signé
- `CHANGELOG.md`

## 13. Hors périmètre de la v1

À ne pas implémenter, même si l'occasion se présente : notifications, widget d'écran d'accueil, historique de trajets, comptes utilisateurs, réservation de vélo, intégration des transports en commun, prévisions de disponibilité, statistiques, partage social, mode navigation guidée avec instructions vocales.

## 14. Qualité et maintenabilité du code

Ce projet est destiné à vivre longtemps, à être repris par des contributeurs et à être audité par des relecteurs F-Droid. La lisibilité prime sur l'astuce.

- **Nommage explicite**, en anglais pour le code, sans abréviations obscures. Un nom long et clair vaut mieux qu'un nom court à déchiffrer.
- **Fonctions courtes**, à responsabilité unique. Si une fonction demande un commentaire pour expliquer ce qu'elle fait, c'est en général qu'il faut la découper.
- **Commentaires : expliquer le *pourquoi*, pas le *quoi*.** Un commentaire qui paraphrase le code est du bruit qui se périmera. Documenter en revanche systématiquement : les choix non évidents, les compromis acceptés, les contournements de limitations de bibliothèques, et les formules métier — en particulier tout le §6, où chaque coefficient doit être justifié.
- **KDoc** sur toutes les classes et fonctions publiques : rôle, paramètres, valeur de retour, cas d'erreur.
- **Séparation stricte des couches.** La logique métier (§6, analyse des flux, résolution d'adresses) doit être en Kotlin pur, sans aucun import Android, donc testable sur la JVM sans émulateur.
- **Pas de code mort, pas de fonctionnalité anticipée.** Ne pas construire d'abstraction « au cas où » : les seules généralisations demandées sont celles du §15.
- **Gestion d'erreurs explicite** : types de résultat plutôt qu'exceptions silencieuses, et pour chaque échec un message utilisateur en français qui dit quoi faire, pas un code technique.
- **Tests unitaires obligatoires** sur l'algorithme du §6, l'analyse GBFS, la résolution d'adresses et l'interpolation des numéros. Chaque correction de bogue s'accompagne du test qui l'aurait détectée.
- **Formatage automatique** (ktlint ou équivalent) et **analyse statique** (Android Lint, detekt) intégrés au build, sans avertissement toléré en release.
- **Commits atomiques**, messages explicites décrivant l'intention.
- Le `README.md` doit permettre à quelqu'un qui découvre le dépôt de compiler et de comprendre l'architecture en moins de trente minutes. Un schéma des couches et du flux de données y est attendu.

## 15. Portabilité vers une autre agglomération

L'application doit pouvoir servir une autre ville **sans modification du code**. C'est une exigence de conception, pas une intention.

- **Aucune donnée propre à Lille en dur** dans le code : ni URL, ni emprise géographique, ni coordonnées de centrage, ni nom de réseau. Tout cela vit dans un **fichier de configuration de ville**, unique et documenté.
- Ce fichier décrit : nom du réseau, URL du `gbfs.json`, emprise géographique, centre et zoom par défaut, URL des jeux de données à télécharger, langue par défaut.
- Le format GBFS étant un standard international, l'essentiel de la portabilité est acquis dès lors que l'URL est configurable (§4.1).
- Les **scripts de génération** des données (tuiles, graphe de routage, index d'adresses) prennent l'emprise géographique en paramètre. Produire les données d'une autre ville doit être une seule commande.
- Le vocabulaire du code et de l'interface reste **générique** : « station », « vélo », « réseau ». Le nom « V'lille » n'apparaît que dans les chaînes traduisibles et la configuration, jamais dans un nom de classe ou de variable.
- Documenter dans le `README.md` la marche à suivre complète pour déployer l'appli sur une nouvelle ville.
- Attention toutefois : la Base Adresse Nationale est française. Pour une ville étrangère, l'index d'adresses devrait être régénéré depuis OpenStreetMap. Le script doit isoler cette source derrière une interface claire pour rendre la substitution possible.

### 15.1 Plusieurs villes dans la même application

Servir une ville sans recompiler ne suffit pas : une seule application doit pouvoir servir **plusieurs réseaux**, l'un après l'autre, et ne rien télécharger de ceux qu'on n'utilise pas.

- Il existe **une configuration de ville par réseau servi**, et un **catalogue** qui les indexe. Le catalogue est dérivé des configurations, jamais écrit à la main : il porte, pour chaque ville, son emprise, son centre, le nombre de stations et **le poids de ses données**.
- Le catalogue est **téléchargeable**, pour qu'une ville nouvelle apparaisse sans publier de version. Un exemplaire est livré dans l'APK comme secours : un premier lancement sans réseau doit montrer une liste, pas un écran vide.
- L'application **ne suppose aucune ville par défaut**. Au premier lancement elle en **propose une d'après la position**, sur appui d'un bouton et jamais d'elle-même (§10) ; au-delà d'une cinquantaine de kilomètres du réseau le plus proche, elle ne propose rien plutôt que n'importe quoi.
- Les jeux de données sont **rangés par ville**. Deux villes cohabitent donc sur l'appareil sans se mélanger, et les données de l'une se suppriment sans toucher à l'autre (§11.9).
- Les règles de normalisation des noms de voies (§4.3) sont propres à un pays. Elles doivent pouvoir accompagner les données d'une ville plutôt que d'être figées dans l'application.

## 16. Méthode de travail attendue de l'agent

1. Commencer par **vérifier l'URL réelle du flux GBFS** et la structure exacte des données reçues avant d'écrire les modèles de données. Ne rien coder en dur qui n'ait été observé.
2. Livrer par étapes vérifiables, dans cet ordre : (1) récupération et affichage des données GBFS en liste, (2) **scripts de génération des données hors ligne** — tuiles, graphe de routage, index d'adresses — avec les tailles réelles obtenues, à comparer aux budgets annoncés, (3) carte vectorielle et marqueurs, (4) moteur de routage hors ligne, (5) algorithme de trajet optimisé, (6) recherche d'adresses locale, (7) écrans restants, (8) finitions et métadonnées F-Droid.
   L'étape (2) vient tôt à dessein : c'est elle qui valide ou invalide tout le pari du hors-ligne. Si les tailles réelles s'écartent nettement des budgets, il faut le savoir avant d'avoir construit l'interface par-dessus.
3. Ne jamais ajouter une dépendance sans la justifier dans le README.
4. Signaler immédiatement tout point où une contrainte du §2 empêcherait une fonctionnalité, plutôt que de contourner la contrainte.
