#!/usr/bin/env bash
#
# Regenerates the three offline datasets in a single command.
# SPEC.md §15: "Producing the data of another city must be a single command."
#
# Usage:
#   tools/generate_all.sh --city config/cities/rennes.json
#                         [--region europe/france/bretagne]
#                         [--departments 35]
#                         [--release-tag data-AAAA-MM]
#                         [--skip-download]
#
# The OpenStreetMap extract and, in France, the Base Adresse Nationale
# departments are read from the city configuration's "dataSources" block, which
# tools/discover_networks.py derives from the reference box. Passing --region
# or --departments overrides them, for a box that reaches a sliver of a
# neighbouring department the sampling missed.
#
# Outside France the addresses come from the OSM extract itself (SPEC §15):
# there is no national base to download, and the configuration says so with
# "addressSource": "openstreetmap".
#
# The source downloads (OSM extract, BAN extracts) are kept in data/ and
# reused from one run to the next.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

CITY_CONFIG="config/cities/lille.json"
OSM_REGION=""
DEPARTMENTS=""
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
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

# Some distributions' Python builds their sqlite3 module without FTS4, the
# only full-text search engine guaranteed on the Android versions the
# application targets. The system Python is therefore imposed.
PYTHON="/usr/bin/python3"
if ! "$PYTHON" -c "import sqlite3, yaml
sqlite3.connect(':memory:').execute('CREATE VIRTUAL TABLE t USING fts4(x)')" 2>/dev/null; then
  echo "Error: $PYTHON must provide FTS4 and PyYAML." >&2
  echo "         sudo apt install python3-yaml" >&2
  exit 1
fi

for tool in osmium tippecanoe tile-join java curl; do
  command -v "$tool" >/dev/null || {
    echo "Error: \"$tool\" is missing." >&2
    echo "         sudo apt install osmium-tool tippecanoe default-jdk curl" >&2
    exit 1
  }
done

# Where a city's data comes from is part of its configuration (§15), so that
# generating another conurbation stays a single command. The flags above win
# when they are given.
read_from_config() {
  "$PYTHON" -c "
import json, sys
sources = json.load(open(sys.argv[1])).get('dataSources') or {}
print(','.join(sources.get(sys.argv[2]) or []))" "$CITY_CONFIG" "$1"
}

[[ -n "$OSM_REGION"  ]] || OSM_REGION="$(read_from_config osmRegions)"
[[ -n "$DEPARTMENTS" ]] || DEPARTMENTS="$(read_from_config banDepartments)"

ADDRESS_SOURCE="$("$PYTHON" -c "
import json, sys
sources = json.load(open(sys.argv[1])).get('dataSources') or {}
print(sources.get('addressSource') or ('ban' if sources.get('banDepartments') else 'openstreetmap'))" "$CITY_CONFIG")"

if [[ -z "$OSM_REGION" ]]; then
  echo "Error: $CITY_CONFIG names no OpenStreetMap extract." >&2
  echo "         Pass --region, or regenerate the configuration:" >&2
  echo "         python3 tools/add_city.py --network <id>" >&2
  exit 1
fi
if [[ "$ADDRESS_SOURCE" == "ban" && -z "$DEPARTMENTS" ]]; then
  echo "Error: $CITY_CONFIG reads its addresses from the BAN but names" >&2
  echo "         no department. Pass --departments, or regenerate the" >&2
  echo "         configuration: python3 tools/add_city.py --network <id>" >&2
  exit 1
fi

# A reference box straddling two of Geofabrik's regions needs both, merged:
# Avignon's reaches into Languedoc-Roussillon, Tarbes's into Aquitaine.
IFS=',' read -ra REGIONS <<< "$OSM_REGION"
if [[ "${#REGIONS[@]}" -eq 1 ]]; then
  OSM_FILE="data/osm/$(basename "${REGIONS[0]}")-latest.osm.pbf"
else
  OSM_FILE="data/osm/$(IFS=+; echo "${REGIONS[*]##*/}")-latest.osm.pbf"
fi

echo "════════════════════════════════════════════════════════════"
echo " Roue Libre — generating the offline datasets"
echo " city       : $CITY_CONFIG"
echo " OSM region : $OSM_REGION"
echo " addresses  : $ADDRESS_SOURCE${DEPARTMENTS:+ ($DEPARTMENTS)}"
echo " release    : $RELEASE_TAG"
echo "════════════════════════════════════════════════════════════"

# Is this file whole? Asked of the format itself, not of the size, which the
# server never announced reliably enough to compare.
#
# "osmium fileinfo" reads the format from the file's extension, and the file
# being checked is still called *.part: -F says what it is, without which a
# perfectly good extract is rejected.
verify_download() {
  case "$2" in
    pbf)  osmium fileinfo -F pbf "$1" >/dev/null 2>&1 ;;
    gzip) gzip -t "$1" 2>/dev/null ;;
  esac
}

# Downloads to a temporary name, checks what arrived, and only then gives it
# the name the rest of the script looks for.
#
# Two things this guards against, both met while generating the seventy French
# conurbations. First, curl's --retry only replays what it calls transient —
# timeouts, refused connections, 429 and 5xx — and a connection dying in
# mid-body after a 200 is not on that list. It is, however, what
# adresse.data.gouv.fr does about one request in three, so --retry-all-errors
# is the flag that matters here. Second, curl writes as it goes: interrupted at
# the final name, it leaves a truncated file that the "already present" test
# below takes for a complete download, and every later run reuses it. The
# failure then surfaces three steps away, in whatever tries to read it.
download_verified() {
  local url="$1" target="$2" kind="$3" label="$4"
  shift 4
  local attempt
  mkdir -p "$(dirname "$target")"
  for attempt in 1 2 3; do
    if curl -fSL --retry 5 --retry-all-errors --retry-delay 5 \
         --remove-on-error "$@" -o "${target}.part" "$url" \
       && verify_download "${target}.part" "$kind"; then
      mv "${target}.part" "$target"
      return 0
    fi
    rm -f "${target}.part"
    echo "  $label: attempt $attempt brought nothing usable, retrying…" >&2
    sleep $((attempt * 5))
  done
  echo "Error: could not download $label from $url" >&2
  return 1
}

if [[ "$SKIP_DOWNLOAD" -eq 0 ]]; then
  echo
  echo "── Sources ──"
  mkdir -p data/osm data/ban
  if [[ ! -f "$OSM_FILE" ]]; then
    PARTS=()
    for region in "${REGIONS[@]}"; do
      part="data/osm/$(basename "$region")-latest.osm.pbf"
      if [[ ! -f "$part" ]]; then
        echo "Downloading the OpenStreetMap extract: $region…"
        download_verified \
          "https://download.geofabrik.de/${region}-latest.osm.pbf" \
          "$part" pbf "OpenStreetMap extract $region"
      fi
      PARTS+=("$part")
    done
    if [[ "${#PARTS[@]}" -gt 1 ]]; then
      # Merging two extracts cut from different daily snapshots leaves the same
      # node under two versions, which osmium merge keeps both of and every
      # later step rejects as "Node ID twice in input". Same day, same cut.
      SNAPSHOTS="$(for part in "${PARTS[@]}"; do
        osmium fileinfo -e -g header.option.timestamp "$part"
      done | sort -u | wc -l)"
      if [[ "$SNAPSHOTS" -gt 1 ]]; then
        echo "Error: the extracts to merge come from different snapshots." >&2
        echo "         Delete them from data/osm/ and let this script fetch" >&2
        echo "         them again, so that all of them are of one day." >&2
        exit 1
      fi
      echo "Merging ${#PARTS[@]} extracts…"
      osmium merge --overwrite -o "$OSM_FILE" "${PARTS[@]}"
    fi
  else
    echo "OSM extract already present: $OSM_FILE"
  fi

  IFS=',' read -ra DEPTS <<< "$DEPARTMENTS"
  [[ "$ADDRESS_SOURCE" == "ban" ]] || DEPTS=()
  for dept in "${DEPTS[@]}"; do
    target="data/ban/adresses-${dept}.csv.gz"
    if [[ ! -f "$target" ]]; then
      echo "Downloading the BAN, department $dept…"
      # --http1.1 for this host alone: it drops multiplexed streams in mid-body
      # on nearly every request, where Geofabrik served 164 extracts over HTTP/2
      # without one failure.
      download_verified \
        "https://adresse.data.gouv.fr/data/ban/adresses/latest/csv/adresses-${dept}.csv.gz" \
        "$target" gzip "BAN department $dept" --http1.1
    else
      echo "BAN extract already present: $target"
    fi
  done
fi

# Each city has its own output directory: generating Paris must not erase
# Lille. The name comes from the network identifier in the configuration, the
# only place it is written (§15).
NETWORK_ID="$("$PYTHON" -c "
import json, sys
print(json.load(open(sys.argv[1]))['network']['id'])" "$CITY_CONFIG")"
OUT_DIR="data/out/$NETWORK_ID"
mkdir -p "$OUT_DIR"
echo " output     : $OUT_DIR"

# The box comes first: the three datasets that follow take it as input.
echo
echo "── 1/4 · Reference box ──"
"$PYTHON" tools/compute_bbox.py --config "$CITY_CONFIG"

echo
echo "── 2/4 · Base map ──"
"$PYTHON" tools/build_tiles.py --config "$CITY_CONFIG" --osm-extract "$OSM_FILE" \
  --output "$OUT_DIR/tiles.mbtiles"

echo
echo "── 3/4 · Routing graph ──"
"$PYTHON" tools/build_routing.py --config "$CITY_CONFIG" --osm-extract "$OSM_FILE" \
  --output-dir "$OUT_DIR/routing"

echo
echo "── 4/4 · Address index ──"
# In France the house numbers come from the national base; everywhere else
# from the extract above, which build_address_index.py reads on its own.
BAN_ARGS=()
if [[ "$ADDRESS_SOURCE" == "ban" ]]; then
  IFS=',' read -ra DEPTS <<< "$DEPARTMENTS"
  for dept in "${DEPTS[@]}"; do
    BAN_ARGS+=(--ban-csv "data/ban/adresses-${dept}.csv.gz")
  done
fi
"$PYTHON" tools/build_address_index.py --config "$CITY_CONFIG" \
  "${BAN_ARGS[@]}" --osm-extract "$OSM_FILE" \
  --output "$OUT_DIR/addresses.sqlite"

echo
echo "── Manifest ──"
"$PYTHON" tools/build_manifest.py --config "$CITY_CONFIG" \
  --data-dir "$OUT_DIR" --output "$OUT_DIR/manifest.json" \
  --release-tag "$RELEASE_TAG"

echo
echo "Done. The files to publish are in $OUT_DIR/."
