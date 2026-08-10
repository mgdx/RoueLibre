# Generating the offline datasets

These scripts produce the three files the application downloads on first
launch: the base map, the routing graph and the address index. They are
versioned here so the data can be regenerated without depending on anyone —
that is one of the two safeguards `SPEC.md` §4.4 requires, the other being
manual import in the application.

## In one command

```bash
tools/generate_all.sh
```

For another conurbation, it is enough to change the city configuration file and
the source region:

```bash
tools/generate_all.sh --city config/cities/rennes.json \
                      --region europe/france/bretagne \
                      --departments 35
```

## Prerequisites

```bash
sudo apt install osmium-tool tippecanoe default-jdk python3-yaml curl
```

**Use `/usr/bin/python3`, not conda's.** Some distributions' Python builds their
`sqlite3` module without FTS4, the only full-text search engine guaranteed on
the Android versions the application targets. The index script refuses to run in
that case, with a message saying so.

Expect around **6 GB** of temporary disk space and **5 minutes** on a 16-core
machine, most of which is downloading the sources.

## The scripts, in the order they run

| Script | Role |
|---|---|
| `compute_bbox.py` | Computes the reference bounding box from the GBFS feed's stations and writes it into the city configuration |
| `build_tiles.py` | Produces `tiles.mbtiles` from an OpenStreetMap extract |
| `build_routing.py` | Produces the BRouter `*.rd5` graph |
| `build_address_index.py` | Produces `addresses.sqlite` from the Base Adresse Nationale and OpenStreetMap |
| `build_manifest.py` | Describes the release: sizes, SHA-256 digests, URLs |
| `build_catalogue.py` | Derives the catalogue of served cities from their configurations |

Shared modules: `city_config.py` (reading the city configuration and the box's
geometry) and `address_normalization.py` (street-name normalisation, applied by
the application too).

## Configuration files

| File | Contents |
|---|---|
| `config/cities/<city>.json` | Everything specific to a conurbation: network, GBFS feed URL, bounding box, centring. **The only place** these values exist |
| `tools/map_features.yaml` | The allowlist of objects kept in the base map, and the list of those deliberately excluded |
| `config/address_normalization.json` | Street-name normalisation rules, shared with the application |

## The reference bounding box

The three datasets share a single box, **derived from the stations
themselves**: their enclosing rectangle, widened by 3 km. It is never written by
hand — `compute_bbox.py` recomputes it on every regeneration and writes it back
into the city configuration, which makes it follow extensions of the network by
itself.

It is deliberately not the metropolis's administrative boundary, which would
cover vast rural areas without a single station and would inflate all three sets
for nothing.

## Sizes obtained over the Lille box

Real measurements from 9 August 2026, over a box of 442 km² (21.2 × 20.9 km)
derived from 268 stations.

| Set | `SPEC.md` budget | Obtained | |
|---|---|---|---|
| Base map | 30 – 60 MB | **35.0 MB** | 4,052 tiles, zooms 10 to 16 |
| Routing graph | 15 – 40 MB | **1.7 MB** | a single `E0_N50.rd5` file |
| Address index | 13 – 28 MB | **5.9 MB** | 10,591 streets, 286,028 numbers, 490 landmarks |
| **Total downloaded** | | **42.5 MB** | |

Breakdown of the base map: the building footprints, present from zoom 15 on,
account for **21.5 MB out of 35** on their own. That is the first lever to pull
if the budget were exceeded on another city — raising their `minZoom` to 16, or
dropping the layer.

## Regenerating the embedded fonts

Bricolage Grotesque is distributed as a variable font, but
`fontVariationSettings` only exists from API 28 while the application targets
API 26: on an Android 8 the requested weights would be ignored and the titles
would render thin. Two static instances are therefore frozen at the only weights
used.

```bash
pip install fonttools
python3 -m fontTools.varLib.instancer BricolageGrotesque.ttf \
    wght=700 wdth=100 opsz=24 -o bricolage_bold.ttf
python3 -m fontTools.varLib.instancer BricolageGrotesque.ttf \
    wght=600 wdth=100 opsz=24 -o bricolage_semibold.ttf
```

The two files weigh 182 kB in total, against 408 kB for the complete variable
font: the compatibility constraint also lightened the APK. Atkinson Hyperlegible
is already static and is embedded as it is.

## Sources and licences

| Source | Use | Licence |
|---|---|---|
| [OpenStreetMap](https://www.openstreetmap.org) ([Geofabrik](https://download.geofabrik.de/) extracts) | base map, routing graph, landmarks | ODbL |
| [Base Adresse Nationale](https://adresse.data.gouv.fr/) | house numbers | ODbL |
| [BRouter](https://github.com/abrensch/brouter) 1.7.10 | routing graph generator | MIT |
| [SRTM 1″](https://registry.opendata.aws/terrain-tiles/) through *terrain-tiles* | the graph's elevation data | public domain |
| The network's GBFS feed | reference bounding box, availability | see the city configuration |

The BRouter archive is verified by SHA-256 digest before use: the generator's
version is pinned, as the build's reproducibility requires.

## Implementation notes

**OSM extraction.** The base map uses osmium's `smart` strategy, which keeps
relations whole: without it, only 41 of the box's municipalities have an
assemblable outline, against 72 with. The routing graph uses `complete_ways`,
because `smart` would pull in the entirety of the long-distance cycle routes
that merely pass through — as far as the middle of France. The tiles are then
clipped to the box by tippecanoe, otherwise overhanging objects would draw a
fringe of partial data outside the covered area.

**Address grouping.** Addresses are grouped by (INSEE code, former municipality,
normalised street name) rather than by `id_fantoir`: the latter is empty on
24,363 of the box's 286,338 rows, which cut 69 streets in two. The former
municipality's code is part of the key, otherwise two homonymous streets of a
merged municipality end up conflated.

**House-number positions.** Each number is stored as a delta from its street's
representative point, in hundred-thousandths of a degree. Round-trip error
measured over 40,877 addresses: median **0.35 m**, 99th percentile 0.62 m, and
only 17 addresses beyond 50 m — inconsistencies in the address base itself, not
in the encoding.
