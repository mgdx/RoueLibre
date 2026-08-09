# Génération des jeux de données hors ligne

Ces scripts produisent les trois fichiers que l'application télécharge au
premier lancement : le fond de carte, le graphe de routage et l'index
d'adresses. Ils sont versionnés ici pour que les données puissent être
régénérées sans dépendre de personne — c'est l'un des deux garde-fous exigés
par le `SPEC.md` §4.4, l'autre étant l'import manuel dans l'application.

## En une commande

```bash
tools/generate_all.sh
```

Pour une autre agglomération, il suffit de changer le fichier de configuration
de ville et la région source :

```bash
tools/generate_all.sh --city config/cities/rennes.json \
                      --region europe/france/bretagne \
                      --departments 35
```

## Prérequis

```bash
sudo apt install osmium-tool tippecanoe default-jdk python3-yaml curl
```

**Utiliser `/usr/bin/python3`, pas celui de conda.** Le module `sqlite3` de
certaines distributions Python est compilé sans FTS4, seul moteur de recherche
plein texte garanti sur les versions d'Android que vise l'application. Le
script d'index refuse de s'exécuter dans ce cas, avec le message qui va bien.

Compter environ **6 Go** d'espace disque temporaire et **5 minutes** sur une
machine à 16 cœurs, l'essentiel étant le téléchargement des sources.

## Les scripts, dans leur ordre d'exécution

| Script | Rôle |
|---|---|
| `compute_bbox.py` | Calcule l'emprise de référence à partir des stations du flux GBFS et l'inscrit dans la configuration de ville |
| `build_tiles.py` | Produit `tiles.mbtiles` à partir d'un extrait OpenStreetMap |
| `build_routing.py` | Produit le graphe BRouter `*.rd5` |
| `build_address_index.py` | Produit `addresses.sqlite` à partir de la Base Adresse Nationale et d'OpenStreetMap |
| `build_manifest.py` | Décrit la publication : tailles, empreintes SHA-256, URL |

Modules partagés : `city_config.py` (lecture de la configuration de ville et
géométrie de l'emprise) et `address_normalization.py` (normalisation des noms
de voies, appliquée aussi par l'application).

## Fichiers de configuration

| Fichier | Contenu |
|---|---|
| `config/cities/<ville>.json` | Tout ce qui est propre à une agglomération : réseau, URL du flux GBFS, emprise, centrage. **Seul endroit** où ces valeurs existent |
| `tools/map_features.yaml` | Liste blanche des objets retenus dans le fond de carte, et liste de ceux qui en sont écartés à dessein |
| `config/address_normalization.json` | Règles de normalisation des noms de voies, partagées avec l'application |

## L'emprise de référence

Les trois jeux de données partagent une seule emprise, **dérivée des stations
elles-mêmes** : rectangle englobant, élargi de 3 km. Elle n'est jamais écrite à
la main — `compute_bbox.py` la recalcule à chaque régénération et la réinscrit
dans la configuration de ville, ce qui fait suivre automatiquement les
extensions du réseau.

Ce n'est délibérément pas la limite administrative de la métropole, qui
couvrirait de vastes zones rurales sans aucune station et alourdirait les trois
jeux pour rien.

## Tailles obtenues sur l'emprise lilloise

Mesures réelles du 9 août 2026, sur une emprise de 442 km² (21,2 × 20,9 km)
dérivée de 268 stations.

| Jeu | Budget `SPEC.md` | Obtenu | |
|---|---|---|---|
| Fond de carte | 30 – 60 Mo | **35,0 Mo** | 4 052 tuiles, zooms 10 à 16 |
| Graphe de routage | 15 – 40 Mo | **1,7 Mo** | un seul fichier `E0_N50.rd5` |
| Index d'adresses | 13 – 28 Mo | **5,9 Mo** | 10 591 voies, 286 028 numéros, 490 repères |
| **Total téléchargé** | | **42,5 Mo** | plafond de 135 Mo largement tenu |

Répartition du fond de carte : les empreintes de bâtiments, présentes à partir
du zoom 15, en représentent à elles seules **21,5 Mo sur 35**. C'est le premier
levier à actionner si le budget devait être dépassé sur une autre ville —
monter leur `minZoom` à 16, ou retirer la couche.

## Sources et licences

| Source | Usage | Licence |
|---|---|---|
| [OpenStreetMap](https://www.openstreetmap.org) (extraits [Geofabrik](https://download.geofabrik.de/)) | fond de carte, graphe de routage, points de repère | ODbL |
| [Base Adresse Nationale](https://adresse.data.gouv.fr/) | numéros de voirie | ODbL |
| [BRouter](https://github.com/abrensch/brouter) 1.7.10 | générateur du graphe de routage | MIT |
| [SRTM 1″](https://registry.opendata.aws/terrain-tiles/) via *terrain-tiles* | altimétrie du graphe | domaine public |
| Flux GBFS du réseau | emprise de référence, disponibilités | voir la configuration de ville |

L'archive BRouter est vérifiée par empreinte SHA-256 avant usage : la version
du générateur est figée, ce que réclame la reproductibilité du build.

## Notes de mise en œuvre

**Découpe OSM.** Le fond de carte utilise la stratégie `smart` d'osmium, qui
conserve les relations entières : sans elle, seules 41 des communes de
l'emprise ont un contour assemblable, contre 72 avec. Le graphe de routage
utilise `complete_ways`, car `smart` y ferait entrer l'intégralité des
itinéraires cyclables longue distance qui ne font que traverser — jusqu'au
centre de la France. Les tuiles sont ensuite recoupées sur l'emprise par
tippecanoe, sinon les objets débordants dessineraient une frange de données
partielles hors de la zone couverte.

**Regroupement des adresses.** Les adresses sont regroupées par
(code INSEE, ancienne commune, nom de voie normalisé) et non par `id_fantoir` :
ce dernier est vide sur 24 363 des 286 338 lignes de l'emprise, ce qui coupait
69 voies en deux. Le code de l'ancienne commune fait partie de la clé, sinon
deux rues homonymes d'une commune fusionnée se retrouvent confondues.

**Position des numéros.** Chaque numéro est stocké en delta par rapport au
point représentatif de sa voie, en centmillièmes de degré. Erreur de
restitution mesurée sur 40 877 adresses : médiane **0,35 m**, 99ᵉ centile
0,62 m, et 17 adresses seulement au-delà de 50 m — des incohérences de la BAN
elle-même, non du codage.
