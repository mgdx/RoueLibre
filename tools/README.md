# Generating the offline datasets

These scripts produce the three files the application downloads on first
launch: the base map, the routing graph and the address index. They are
versioned here so the data can be regenerated without depending on anyone —
that is one of the two safeguards `SPEC.md` §4.4 requires, the other being
manual import in the application.

## In one command

```bash
tools/generate_all.sh --city config/cities/rennes.json
```

That is the whole command for any of the configured conurbations. The
OpenStreetMap extract and the address-base departments come from the
configuration's `dataSources` block, derived from the reference box;
`--region` and `--departments` override them where a box reaches a sliver of a
neighbouring department the sampling missed.

## Finding the networks in the first place

`config/cities/` holds one file per network served, and those files are not
written by hand:

```bash
python3 tools/discover_networks.py    # calls every feed on earth, writes the list
python3 tools/add_city.py --list      # what it would add, and under what name
python3 tools/add_city.py --all       # writes the configurations
python3 tools/build_catalogue.py      # re-derives the catalogue from them
```

`discover_networks.py` reads the catalogues `SPEC.md` §4.1 accepts — the GBFS
registry `systems.csv`, France's national access point, and the hand-checked
addresses of `config/extra-feeds.json` — calls every address they publish,
and keeps only what this application can actually serve: stations with real
docks, a fleet holding bicycles and no car, at least ten stations, a box that
still describes a conurbation, a feed needing no key. It then places what it
kept: Geofabrik's extract index says which OpenStreetMap extract covers the
box, the GeoNames gazetteer names the municipalities the stations stand in, and
France's geographic API says which Base Adresse Nationale extracts to download.
The reasoned list it produces, rejections included, is
[`docs/networks.md`](../docs/networks.md); the same survey in machine-readable
form lands in `data/networks.json`.

Calling sixteen hundred feeds takes the better part of an hour. `--country FR`
restricts it to one country, and `--offline` re-renders the report from the
last survey without calling anything.

`add_city.py` turns each surveyed network into a configuration: verified feed
address, network and authority names, licence, reference box recomputed against
the live feed, opening framing, and the sources a generation run needs. It
never touches a configuration that already exists unless told to.

## Publishing what was generated

```bash
python3 tools/publish_data.py --dry-run   # what it would do
python3 tools/publish_data.py             # does it
```

Needs `gh` and a `gh auth login`. Re-runnable: an asset already online is left
alone, so an interrupted upload is finished by running it again.

**One release per country, and a last one holding the index.** GitHub allows
**1,000 assets per release** — the message is `file_count limited to 1000
assets per release` — and 332 conurbations come to some 1,160 files. The heavy
files therefore go to `data-<tag>-fr`, `data-<tag>-de`, `data-<tag>-jp`; the
largest of those is France, at 240 assets, so the ceiling is far off even as
networks grow.

The last release, `data-<tag>`, holds nothing but the catalogue and the 332
manifests. It exists because the application asks for
`releases/latest/download/manifest-<network>.json`, and *latest* names the
newest release of the repository, whichever it is: every manifest must sit in
one release, and that release must be the newest. Hence the rule the script
enforces — **the index is deleted and re-created after everything else**, so
that it takes that place back. Publish data any other way and the manifests
become unreachable without a single URL changing.

A city's files are found from its country, which its configuration already
declares. Nothing records where a file went, so a partial upload never leaves a
registry to reconcile: run it again and it resumes.

## Keeping the figures honest

```bash
python3 tools/update_readme_figures.py           # rewrites what is stale
python3 tools/update_readme_figures.py --check   # exit 1 if anything is stale
```

`README.md` and `docs/offline-data.md` quote the same handful of figures several
times each — how many networks are served, in how many countries, how many
stations they hold, what a median city weighs, what the release APK weighs. Each
appears in a badge, in a feature and in a sentence, and written by hand the first
network added would leave some of those places lying with nothing to say which
one is right.

So none of them is written by hand. The counts and the median size are read from
`config/catalogue.json`, which `build_catalogue.py` derives from the
configurations and the manifests; the APK sizes are read from the release APKs
themselves, under `app/build/outputs/apk/release/`, so the figure is the file's
own size and not a memory of it. **Run it after `build_catalogue.py`, and after
`./gradlew assembleRelease` when the application's own weight is what changed** —
with no release build on disk it leaves those two figures alone and says so,
rather than inventing them. It refuses to pass silently over a sentence it cannot
find: a figure whose place has been reworded is an error, not a no-op. Only the
figures move; the hand wrapping around them is the author's.

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
| `discover_networks.py` | Surveys every GBFS feed of every country, keeps the docked bike networks, writes `docs/networks.md` |
| `add_city.py` | Turns a surveyed network into a `config/cities/*.json` |
| `compute_bbox.py` | Computes the reference bounding box from the GBFS feed's stations and writes it into the city configuration |
| `build_tiles.py` | Produces `tiles.mbtiles` from an OpenStreetMap extract |
| `build_routing.py` | Produces the BRouter `*.rd5` graph |
| `build_address_index.py` | Produces `addresses.sqlite`, from the Base Adresse Nationale in France and from the OpenStreetMap extract everywhere else |
| `build_manifest.py` | Describes the release: sizes, SHA-256 digests, URLs |
| `build_catalogue.py` | Derives the catalogue of served cities from their configurations |
| `refresh_normalization_fixtures.py` | Recomputes the normalisation reference cases after the shared rules change |
| `publish_data.py` | Uploads the generated sets to the `RoueLibre-data` releases, at the addresses the manifests name |
| `update_readme_figures.py` | Copies the counts, the median dataset size and the APK sizes into `README.md` and `docs/offline-data.md` |

Shared modules: `city_config.py` (reading the city configuration and the box's
geometry) and `address_normalization.py` (street-name normalisation, applied by
the application too).

## Configuration files

| File | Contents |
|---|---|
| `config/cities/<city>.json` | Everything specific to a conurbation: network, GBFS feed URL, bounding box, centring, the extracts its data is cut from. **The only place** these values exist |
| `config/catalogue.json` | The index of those configurations, derived from them |
| `tools/map_features.yaml` | The allowlist of objects kept in the base map, and the list of those deliberately excluded |
| `config/address-normalization/<language>.json` | Street-name normalisation rules, one file per language, shared with the application |
| `config/extra-feeds.json` | Auto-discovery addresses found outside the public catalogues, each with the page it was read from |

## Street-name normalisation, one file per language

`config/address-normalization/<language>.json` is what makes a typed street
meet an indexed one: it lowercases, folds the letters accent removal cannot
reach, strips accents and punctuation, expands abbreviations, and splits a
leading way type off the proper name so that "gambetta" finds "rue Gambetta".

**There is one file per language because a street type is a word of a
language.** "Rue" and "boulevard" say nothing about a Warsaw address, where the
word is *ulica* and the abbreviation *ul.*; "Straße" is typed *Strasse* by half
of Germany, and no accent removal folds a ß. The language meant is the one the
**address base** is written in, not the one the interface speaks: an index
built over Antwerp is searched in Dutch whatever the phone is set to. The index
records which file it was built with, and the application reads it back from
there — the two ends therefore cannot be paired wrongly.

Each file covers a whole language, not one city. Two reasons:

* an address base carries its country's abbreviations — the DGFiP's way-type
  codes `ALL`, `CHE`, `MTE`, `RLE`, `LD`, `TRA`, `PRV` in France, `ul.`, `al.`,
  `pl.` in Poland, `Str.`, `Cd.`, `Sk.` elsewhere — and a code left unexpanded
  is a street nobody finds by typing its name in full;
* a conurbation names its ways in its own words. *Traverse* and *vallon* are
  Marseille, *montée* and *traboule* Lyon, *courée* and *drève* the North,
  *venelle* and *hent* Brittany, *cavée* Normandy, *carriera* and *cami* the
  Occitan south, *ravine*, *morne* and *habitation* Guadeloupe and Réunion;
  *calle*, *calzada* and *cerrada* are Spanish America, *calle* and
  *fondamenta* Venice. Leaving a region's vocabulary out costs its inhabitants
  the type/name split.

A language with no file of its own falls back on English — plain folding, no
street type, which still finds a street typed in full. Writing one is a pull
request against a single JSON file, and `reference-<language>.json` in the
test fixtures proves it the day it lands rather than the day a city speaking
it has its data generated.

After editing the rules, recompute the reference cases the Kotlin test replays:

```bash
python3 tools/refresh_normalization_fixtures.py   # --check to see without writing
```

Read its diff. A case that moves is a street whose split has changed, and
whether that is an improvement is a judgement no script can make.

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

Real measurements from 12 August 2026, over a box of 442 km² (21.2 × 20.9 km)
derived from 268 stations.

| Set | `SPEC.md` budget | Obtained | |
|---|---|---|---|
| Base map | 30 – 60 MB, ceiling 200 | **36.4 MB** | 4,195 tiles, zooms 10 to 16 |
| Routing graph | 15 – 40 MB | **1.7 MB** | a single `E0_N50.rd5` file |
| Address index | 13 – 28 MB | **6.0 MB** | 11,090 streets, 286,028 numbers |
| **Total downloaded** | | **44.1 MB** | |

Breakdown of the base map: the building footprints, present from zoom 15 on,
account for over half of it on their own. That is the first lever to pull if
the ceiling were reached on another city — raising their `minZoom` to 16, or
dropping the layer.

## Sizes obtained over the seventy French networks

Generated in one run on 12 August 2026, 2 h 15 on a sixteen-core machine, the
sources already downloaded.

| | |
|---|---|
| Total | **1.08 GB** for 70 conurbations |
| Median | **10.9 MB** per conurbation, all three sets together |
| Lightest | Auray, **3.2 MB** |
| Heaviest | Paris, **143.0 MB** — 114.9 of base map, 20.9 of addresses, 7.2 of routing |

Paris is the only one over 60 MB, and it stays well under the 300 MB
`SPEC.md` §4.2 sets as the figure to design for. Since a network is no longer
refused for the ground its stations cover, regional feeds have joined the list
and some exceed that figure — their weight is announced before the download,
never worked around. Its box holds 1.24 million building
footprints against 78,000 for Lille, which is the whole of the difference.

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

The map's own glyphs are cut from those fonts by `node tools/build_glyphs.js`,
and it produces **every range of the Basic Multilingual Plane**, 354 kB in all.
That is not thoroughness for its own sake: a range MapLibre asks for and does
not find fails the tile whose label needed it, and nothing of that tile is
drawn — one Romanian letter emptied Hunedoara's map of its streets, its river
and its parks. A range the font does not cover answers in forty-four bytes, so
the whole plane costs little and no place name can blank a map again.

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

**Downloading the sources.** Every source is fetched under a temporary name,
checked — `gzip -t` for the address base, `osmium fileinfo` for an extract —
and only then given its final name. Two reasons, both met in practice.
`adresse.data.gouv.fr` drops its stream in mid-body on about one request in
three, and curl's `--retry` does not replay that: it covers timeouts, refused
connections, 429 and 5xx, not a transfer that dies after a 200, which is why
`--retry-all-errors` is passed. And curl writes as it goes, so a transfer
interrupted at the final name leaves a truncated file that the "already
present" test takes for a complete download, every later run reusing it and
failing three steps away. That host is also asked in HTTP/1.1, which it
survives; Geofabrik served 164 extracts over HTTP/2 without one failure and is
left alone.

**Merging two extracts.** Geofabrik cuts all its regions from the same daily
snapshot, and two extracts downloaded on different days hold the same node
under two versions. `osmium merge` keeps both, and everything downstream stops
at "Node ID twice in input". The script compares the extracts' snapshot
timestamps before merging and refuses rather than produce that file: delete
them from `data/osm/` and let it fetch them again.

**Reusing the cut.** `build_tiles.py` keeps the box's cut between runs — it
costs minutes on a large region — but reuses it only when the file beside it,
`area.provenance.json`, says it was made of the same extract for the same box.
Existence alone used to be the test, and since the working files are removed
only when a run succeeds, a run interrupted after the cut handed its own to the
next city. Each network also works in `data/work/tiles/<network>/` rather than
in one shared directory. To re-cut on purpose, delete the directory.

**OSM extraction.** The base map uses osmium's `smart` strategy, which keeps
relations whole: without it, only 41 of the box's municipalities have an
assemblable outline, against 72 with. The routing graph uses `complete_ways`,
because `smart` would pull in the entirety of the long-distance cycle routes
that merely pass through — as far as the middle of France. The tiles are then
clipped to the box by tippecanoe, otherwise overhanging objects would draw a
fringe of partial data outside the covered area.

**Address grouping, from the BAN.** Addresses are grouped by (INSEE code,
former municipality, normalised street name) rather than by `id_fantoir`: the
latter is empty on 24,363 of the box's 286,338 rows, which cut 69 streets in
two. The former municipality's code is part of the key, otherwise two homonymous
streets of a merged municipality end up conflated.

**Address grouping, from OpenStreetMap.** There is no national identifier to
group on, so the key is (normalised municipality, normalised street name). A
house number naming a municipality its street does not is attached to the
street all the same rather than opening a second one beside it: OpenStreetMap
tags `addr:city` on the numbers far more often than on the ways. Streets left
without a municipality — most of them — take the name of the nearest inhabited
place, which is an approximation of a boundary by a distance, and the
alternative was a blank in the results list.

**House-number positions.** Each number is stored as a delta from its street's
representative point, in hundred-thousandths of a degree. Round-trip error
measured over 40,877 addresses: median **0.35 m**, 99th percentile 0.62 m, and
only 17 addresses beyond 50 m — inconsistencies in the address base itself, not
in the encoding.
