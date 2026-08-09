#!/usr/bin/env bash
#
# Régénère les trois jeux de données hors ligne en une seule commande.
# SPEC.md §15 : « Produire les données d'une autre ville doit être une seule
# commande. »
#
# Usage :
#   tools/generate_all.sh [--city config/cities/lille.json]
#                         [--region europe/france/nord-pas-de-calais]
#                         [--departments 59,62]
#                         [--release-tag data-AAAA-MM]
#                         [--skip-download]
#
# Les téléchargements sources (extrait OSM, extraits BAN) sont conservés dans
# data/ et réutilisés d'une exécution à l'autre.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

CITY_CONFIG="config/cities/lille.json"
OSM_REGION="europe/france/nord-pas-de-calais"
DEPARTMENTS="59,62"
RELEASE_TAG="data-$(date -u +%Y-%m)"
SKIP_DOWNLOAD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --city)         CITY_CONFIG="$2"; shift 2 ;;
    --region)       OSM_REGION="$2"; shift 2 ;;
    --departments)  DEPARTMENTS="$2"; shift 2 ;;
    --release-tag)  RELEASE_TAG="$2"; shift 2 ;;
    --skip-download) SKIP_DOWNLOAD=1; shift ;;
    -h|--help)      sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Option inconnue : $1" >&2; exit 1 ;;
  esac
done

# Le module sqlite3 de certaines distributions Python — conda notamment — est
# compilé sans FTS4, seul moteur de recherche plein texte garanti sur les
# Android que vise l'application. On impose donc le Python système.
PYTHON="/usr/bin/python3"
if ! "$PYTHON" -c "import sqlite3, yaml
sqlite3.connect(':memory:').execute('CREATE VIRTUAL TABLE t USING fts4(x)')" 2>/dev/null; then
  echo "Erreur : $PYTHON doit disposer de FTS4 et de PyYAML." >&2
  echo "         sudo apt install python3-yaml" >&2
  exit 1
fi

for tool in osmium tippecanoe tile-join java curl; do
  command -v "$tool" >/dev/null || {
    echo "Erreur : « $tool » est absent." >&2
    echo "         sudo apt install osmium-tool tippecanoe default-jdk curl" >&2
    exit 1
  }
done

OSM_FILE="data/osm/$(basename "$OSM_REGION")-latest.osm.pbf"

echo "════════════════════════════════════════════════════════════"
echo " Roue Libre — génération des jeux de données hors ligne"
echo " ville      : $CITY_CONFIG"
echo " région OSM : $OSM_REGION"
echo " release    : $RELEASE_TAG"
echo "════════════════════════════════════════════════════════════"

if [[ "$SKIP_DOWNLOAD" -eq 0 ]]; then
  echo
  echo "── Sources ──"
  mkdir -p data/osm data/ban
  if [[ ! -f "$OSM_FILE" ]]; then
    echo "Téléchargement de l'extrait OpenStreetMap…"
    curl -fSL --retry 3 -o "$OSM_FILE" \
      "https://download.geofabrik.de/${OSM_REGION}-latest.osm.pbf"
  else
    echo "Extrait OSM déjà présent : $OSM_FILE"
  fi

  IFS=',' read -ra DEPTS <<< "$DEPARTMENTS"
  for dept in "${DEPTS[@]}"; do
    target="data/ban/adresses-${dept}.csv.gz"
    if [[ ! -f "$target" ]]; then
      echo "Téléchargement de la BAN, département $dept…"
      curl -fSL --retry 3 -o "$target" \
        "https://adresse.data.gouv.fr/data/ban/adresses/latest/csv/adresses-${dept}.csv.gz"
    else
      echo "Extrait BAN déjà présent : $target"
    fi
  done
fi

# L'emprise vient en premier : les trois jeux suivants la prennent en entrée.
echo
echo "── 1/4 · Emprise de référence ──"
"$PYTHON" tools/compute_bbox.py --config "$CITY_CONFIG"

echo
echo "── 2/4 · Fond de carte ──"
"$PYTHON" tools/build_tiles.py --config "$CITY_CONFIG" --osm-extract "$OSM_FILE"

echo
echo "── 3/4 · Graphe de routage ──"
"$PYTHON" tools/build_routing.py --config "$CITY_CONFIG" --osm-extract "$OSM_FILE"

echo
echo "── 4/4 · Index d'adresses ──"
BAN_ARGS=()
IFS=',' read -ra DEPTS <<< "$DEPARTMENTS"
for dept in "${DEPTS[@]}"; do
  BAN_ARGS+=(--ban-csv "data/ban/adresses-${dept}.csv.gz")
done
"$PYTHON" tools/build_address_index.py --config "$CITY_CONFIG" \
  "${BAN_ARGS[@]}" --osm-extract "$OSM_FILE"

echo
echo "── Manifeste ──"
"$PYTHON" tools/build_manifest.py --config "$CITY_CONFIG" \
  --release-tag "$RELEASE_TAG"

echo
echo "Terminé. Les fichiers à publier sont dans data/out/."
