# The offline data, and how a city is added

The three sets a city needs — base map, routing graph, address index — are not
in the APK: they are downloaded on first launch, or provided by hand. Their
generation is entirely scripted and versioned in [`tools/`](../tools/README.md):

```bash
tools/generate_all.sh
```

Sizes actually obtained, three networks generated under the same rules:

| Network | Stations | Area | Base map | Routing | Addresses | Total |
|---|---|---|---|---|---|---|
| V'lille | 268 | 672 km² | 35.0 MB | 1.7 MB | 6.0 MB | **42.7 MB** |
| Vélo'v Lyon | 465 | 575 km² | 35.6 MB | 2.6 MB | 4.1 MB | **42.3 MB** |
| Vélib' Paris | 1,518 | 994 km² | 114.9 MB | 7.2 MB | 20.9 MB | **143.0 MB** |

The bounding box is derived from the stations themselves, which follows the
reality of the networks: "Lille" covers 68 municipalities of the metropolis,
Lyon 85, Paris 211. Paris weighs more because it is Paris — 1.24 million
building footprints against 78,000 for Lille — and the rendering rules stay
common to all.

**Area does not decide weight, and it decides nothing here.** A network is
never refused for the ground its stations cover: Kiel's reaches from Rendsburg
to Plön over 2,672 km², four times Lille's, and its base map weighs 50 MB
against Lille's 35 — countryside costs almost nothing to draw. The **300 MB of `SPEC.md` §4.2** is the
figure a conurbation is designed for, and every one of them stays under it. A
network serving a whole region legitimately exceeds it — Vélo Fluo's one
station per town of the Grand Est weighs 1,343 MB — and is served anyway, its
weight announced before the download rather than hidden behind a refusal.

## Adding a city

No data specific to a conurbation exists in the code: no URL, no bounding box,
no centring coordinate, no network name. Each city fits in one file under
`config/cities/`, and the catalogue indexes them.

**For the whole world, both steps are already done.** The survey below
re-reads the public catalogues, calls every feed on earth that claims to
publish stations — around sixteen hundred of them — applies the eligibility
rules and writes the configurations of the networks it kept:

```bash
python3 tools/discover_networks.py    # calls every feed, writes docs/networks.md
python3 tools/discover_networks.py --country PL   # or one country at a time
python3 tools/add_city.py --list      # what it would add
python3 tools/add_city.py --all       # writes config/cities/*.json
python3 tools/add_city.py --refresh-sources   # re-reads where the data is cut from
python3 tools/read_fleet.py --all     # asks each network what it lends
```

By hand, for a network the catalogues do not list:

1. **Find the GBFS feed URL.** Never guess it: take it from the
   [MobilityData catalogue](https://github.com/MobilityData/gbfs/blob/master/systems.csv),
   in France from [transport.data.gouv.fr](https://transport.data.gouv.fr/), or
   from the producer's own developer page — then verify it with a real request.
   An address found that way goes into
   [`config/extra-feeds.json`](../config/extra-feeds.json), where the survey picks
   it up and judges it like any other.
2. **Copy a city configuration.** Start from
   [`config/cities/lille.json`](../config/cities/lille.json) and adjust only the
   `network` block, the `gbfs.json` URL and the map's centring. Leave the
   `boundingBox` block alone: it is recomputed automatically.
3. **Generate the data.** The OpenStreetMap extract, and in France the
   address-base departments, come from the configuration's `dataSources` block,
   so the command carries nothing but the city:
   ```bash
   tools/generate_all.sh --city config/cities/<city>.json
   ```
   `--region` and `--departments` override them where a box reaches a sliver of
   a neighbouring department the sampling missed. The bounding box is derived
   from the network's stations, then widened by 3 km; it therefore covers the
   whole conurbation — Vélib's box reaches into eight departments, Avignon's
   into three — and follows extensions of the network by itself.
4. **Regenerate the catalogue**, which tells the application what exists and
   what it weighs:
   ```bash
   python3 tools/build_catalogue.py
   ```
   It is derived from the configurations, never written by hand: an entry
   maintained by hand would end up describing a city one cannot install.
5. **Publish the files** from `data/out/<network>/` in a repository release,
   along with the manifest and the catalogue. A release has a single namespace:
   the files carry their network's prefix there — `velib-tiles.mbtiles` — and
   recover their bare name once installed.

The application assumes no default city. It proposes the one matching your
position, on a button press, and stores each city's data apart: two cities
coexist on the device, and deleting one leaves the other untouched.

Since GBFS is an international standard, most of the portability is won as soon
as the URL is configurable — and it is also configurable from the application's
settings, without recompiling.

## Electric or not, the drawing says so

A network lending pedal-assist bikes is not the same offer as one lending
mechanical bikes, and the bike glyphs of that city carry a small bolt — the
journey button, the ride leg, the discs standing for stations. Nothing is
guessed: [`tools/read_fleet.py`](../tools/read_fleet.py) reads the network's own
GBFS `vehicle_types` feed and writes the answer into the `fleet` block of its
configuration, which is the only place the application looks. A feed declaring
no vehicle type leaves the block out, and the plain bike is drawn. Of the
networks served today, 192 lend pedal-assist bikes, 101 lend mechanical ones
and 13 say nothing.

## Where the addresses come from

In France the index is built from the Base Adresse Nationale, which is the finer
source there; everywhere else it is built from the OpenStreetMap extract the map
and the routing graph are already cut from, so a city costs one download rather
than two. The configuration says which, in `dataSources.addressSource`, and the
coverage of the second varies from one city to the next — that is the honest
cost of not having a national address base to lean on.

## Street names are normalised in their own language

"Boulevard" abbreviates to "bd" in French, "ulica" to "ul." in Polish, and
"Straße" is typed "Strasse" by half of Germany. One file per language holds
those rules, in
[`config/address-normalization/`](../config/address-normalization/); the index
records which one it was built with, and the application reads it back from
there rather than deciding for itself. A language with no file of its own falls
back on English — plain folding, which still finds a street typed in full — and
writing one is a pull request against a single JSON file.

## Data sources and attributions

| Source | Use | Licence |
|---|---|---|
| The GBFS feed of each network served, named in its configuration | station availability | ODbL, as a rule |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | base map, routing, landmarks | ODbL |
| [Base Adresse Nationale](https://adresse.data.gouv.fr/) | house numbers, France | ODbL |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | house numbers, everywhere else | ODbL |
| [Geofabrik's extract index](https://download.geofabrik.de/) | which extract covers a city's box | ODbL |
| [GeoNames](https://www.geonames.org/) | the municipalities a network covers | CC BY 4.0 |
| [BRouter](https://github.com/abrensch/brouter) | routing engine and generator | MIT |
| SRTM through [terrain-tiles](https://registry.opendata.aws/terrain-tiles/) | the graph's elevation data | public domain |
