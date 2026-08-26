# The offline data, and how a city is added

The three sets a city needs — base map, routing graph, address index — are not
in the APK: they are downloaded on first launch, or provided by hand. Their
generation is entirely scripted and versioned in [`tools/`](../tools/README.md):

```bash
tools/generate_all.sh
```

**All 331 networks served have their data produced: 12.8 GB in all**,
median 10.7 MB a city. Sizes actually obtained, under the same rules for all:

| Network | Stations | Area | Base map | Routing | Addresses | Total |
|---|---|---|---|---|---|---|
| Hunedoara | 12 | 65 km² | 1.0 MB | 0.1 MB | 0.1 MB | **1.2 MB** |
| V'lille | 268 | 672 km² | 35.0 MB | 1.7 MB | 6.0 MB | **42.7 MB** |
| Donkey Kiel | 203 | 2,672 km² | 50.1 MB | 1.9 MB | 2.4 MB | **54.4 MB** |
| Vélib' Paris | 1,518 | 994 km² | 114.9 MB | 7.2 MB | 20.9 MB | **143.0 MB** |
| Careem Dubai | 212 | 162,065 km² | 160.0 MB | 12.5 MB | 3.6 MB | **176.1 MB** |
| Blue-bike | 315 | 21,772 km² | 932.0 MB | 62.6 MB | 26.7 MB | **1,021 MB** |
| Vélo Fluo Grand Est | 42 | 89,038 km² | 1,343 MB | 76.5 MB | 31.6 MB | **1,451 MB** |

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
python3 tools/read_fleet.py --all     # counts the bikes each network has out
python3 tools/sample_stations.py --all   # records where its stations are
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
   It is derived from the configurations and the manifests, never written by
   hand: an entry maintained by hand would end up describing a city one cannot
   install. It must be re-derived **after** the data, never before — a catalogue
   older than the manifests beside it announces a city as unpublished in the
   list and downloads it on the very next screen.
5. **Publish the files** from `data/out/<network>/` in a repository release,
   along with the manifest and the catalogue:
   ```bash
   python3 tools/publish_data.py
   ```
   A release has a single namespace: the files carry their network's prefix
   there — `velib-tiles.mbtiles` — and recover their bare name once installed.
   The command refuses to send a catalogue that disagrees with the manifests it
   would travel with, and names the cities and the command that fixes it. It
   rewrites a manifest whose release tag or digests have gone stale, which is
   itself one of the ways the catalogue falls behind: when it says it has, run
   step 4 again and publish once more.

The application assumes no default city. It proposes the one matching your
position, on a button press, and stores each city's data apart: two cities
coexist on the device, and deleting one leaves the other untouched. **Which
city matches is measured on stations, never on the reference box** — each
configuration carries eight positions taken through the network, because the
box of a network serving a whole region is mostly empty: Vélo Fluo's passes
46 km from the middle of the Morvan while its nearest bike is 130 km away.

Since GBFS is an international standard, most of the portability is won as soon
as the URL is configurable (`SPEC.md` §4.1) — and it is, in the city's own
configuration file, which is the only place a feed address is ever written.
Adding a network takes that one JSON file and no code.

## Hosting the data yourself

The application downloads from **the releases of
[`RoueLibre-data`](https://github.com/mgdx/RoueLibre-data)**, and from nowhere
else: two addresses say so, both shipped inside the APK — `catalogueUrl` at the
head of `config/catalogue.json`, which is where the list of cities is refreshed
from, and `dataRelease.manifestUrl` in each city's configuration, which is where
that city's files are described. **Neither is typed in the settings**, and
`SPEC.md` §9 means that on purpose: no source address is entered in the
application, and the way to install data that does not come from the default
host is to **import the file itself** from the storage screen, which is the
guarantee §4.4 asks for — the host must never be a single point of failure.

Serving the files from somewhere else therefore means editing those two fields
and building, which anyone may do: everything below is a plain static file, and
the two documents that describe them are these.

### The catalogue

One document for the whole application, listing what exists. `build_catalogue.py`
derives it from the configurations and the manifests; a third party writes the
same shape:

```json
{
  "catalogueVersion": 1,
  "catalogueUrl": "https://example.org/catalogue.json",
  "generatedAt": "2026-08-16T11:29:22Z",
  "cities": [
    {
      "id": "velib",
      "displayName": "Vélib' Métropole",
      "mainCity": "Paris",
      "operator": "Smovengo / Syndicat Autolib' Vélib' Métropole",
      "country": "FR",
      "stationCount": 1518,
      "stationSamples": [[48.86598, 2.27572], [48.8549, 2.41867]],
      "boundingBox": { "south": 48.716436, "west": 2.124587,
                       "north": 48.984517, "east": 2.579197 },
      "centreLatitude": 48.8566,
      "centreLongitude": 2.3522,
      "gbfsDiscoveryUrl": "https://…/gbfs.json",
      "manifestUrl": "https://example.org/manifest-velib.json",
      "dataSizeBytes": 143626141,
      "releaseTag": "data-2026-08-fr"
    }
  ]
}
```

`id`, `displayName`, `gbfsDiscoveryUrl`, `manifestUrl` and a sound `boundingBox`
are what an entry cannot do without; the rest may be left out, and an entry
missing one of them is **dropped on its own** rather than costing the catalogue
its other three hundred. `catalogueUrl` is where the application will look next
time, so a catalogue can move itself. `stationSamples` are eight positions taken
through the network, and they are what "find my city" measures against — the
reference box of a network serving a whole region is mostly empty.

### The manifest

One document per network, describing what is published for it and what it
weighs. `build_manifest.py` writes it; the shape is:

```json
{
  "formatVersion": 2,
  "releaseTag": "data-2026-08-fr",
  "generatedAt": "2026-08-14T15:45:13Z",
  "network": "velib",
  "boundingBox": { "south": 48.716436, "west": 2.124587,
                   "north": 48.984517, "east": 2.579197 },
  "datasets": [
    {
      "id": "tiles",
      "description": "Vector base map",
      "files": [
        {
          "name": "tiles.mbtiles",
          "url": "https://example.org/velib-tiles.mbtiles",
          "sizeBytes": 114855936,
          "sha256": "56dd5fd3e10fb8f836a69c44de6c06360a57ea50d8236c0e027a58ef86e58e90"
        }
      ]
    }
  ]
}
```

The three `id` values are `tiles`, `routing` and `addresses`, and a manifest may
describe any subset of them. `name` must be a **plain file name** — no slash, no
`..` — and the routing set is the one that may hold several files, one per
BRouter segment, each keeping the name the engine derives from the coordinates
it is looking for. `sha256` is the file's own digest, and it is the whole
mechanism of a partial update: what is installed is compared against it, and only
what differs comes down again. `formatVersion` must equal the `formatVersion` of
the city's configuration, failing which the application says the format is one it
cannot read and invites an update, rather than failing later when it opens a
file. `network` must be the configuration's network identifier, and `releaseTag`
is free text shown for what it is.

Everything served is static: an MBTiles archive, one or more `rd5` graphs, a
SQLite database, and these two JSON documents. Nothing is executed, nothing is
loaded as code, and any file host that answers `GET` over HTTPS will do — the
application declares no cleartext exception, so a plain `http://` address is
rewritten to `https://` rather than followed.

## Electric or not, the drawing says so

A network lending pedal-assist bikes is not the same offer as one lending
mechanical bikes, nor as one lending both, and the bike glyphs of that city say
which — plain, bearing a bolt, or bearing a bolt and a cog — wherever a bike is
drawn: the journey button, the ride leg, the discs standing for stations.
Nothing is guessed, and nothing is taken on the operator's word either:
[`tools/read_fleet.py`](../tools/read_fleet.py) **counts the bikes standing at
the stations** and writes what it saw into the `fleet` block of the city's
configuration.

That block is the **seed**, not the last word. The application counts again from
the live feeds, on every refresh of the stations: it reads `vehicle_types` once
per session to know what each identifier is, then sorts the bikes of
`station_status` into the two kinds over the whole network. A network that gains
a kind between two releases therefore shows the right bike straight away, instead
of waiting for the next survey. **A reading only ever adds** — it can reveal a
kind the seed did not know about, never take one away, so the glyph does not
flicker on a network whose stations happen to be empty of one kind at four in the
morning. What is counted is remembered across restarts, under the name of the city
it was counted in, so a launch with no connection keeps it. The seed is what
answers on a first launch, offline, or where the feeds let nothing be counted.

Counting rather than reading the `vehicle_types` declaration is the whole point.
A third of the networks declaring a mixed fleet have not one bike of one of the
two kinds in circulation: Madrid declares a mechanical type and puts out 5872
electric bikes and no mechanical one, Berlin declares an electric type and puts
out 1989 mechanical bikes and no electric one. Vélib' Métropole declares nothing
at all — it is on GBFS 1.0, which has no `vehicle_types` feed — and lends 7836
electric bikes beside 11 687 mechanical ones. Of the networks served today, 102
lend both kinds, 93 lend electric bikes only, 138 lend mechanical ones only, and
27 let nothing be counted and keep whatever their declaration says.

Those figures are the **seeds** as of the survey of 14 August 2026, not what a
user will see: the application recounts from the live feeds, and the 27 that let
nothing be counted that day will be counted the first time one of their stations
holds a bike. Re-running the survey is therefore no longer what makes a network
show the right bike — it is what makes its *first* launch show it.

Where both kinds are lent, the station's sheet splits its count — "3 mechanical
· 1 electric". The breakdown costs no extra request: it travels in the
`station_status` feed already fetched, as `vehicle_types_available` since
GBFS 2.1 and as Vélib's `num_bikes_available_types` on GBFS 1.0. The identifiers
it counts by are the producer's own — `346` and `348` at nextbike, `mechanical`
and `electrical` at Lyon — and the `vehicleTypes` table is what translates them,
read from the network's own `vehicle_types` feed and seeded by the configuration.
A type absent from that table, or a breakdown that does not add up to the count
displayed, silences the line rather than risking a wrong split: the total alone
is always true. Keeping that table counted rather than surveyed is what stops the
line disappearing without a word the day an operator adds a kind.

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
| The GBFS feed of each network served, named in its configuration | station availability | whatever the feed states — see below |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | base map, routing, landmarks | ODbL |
| [Base Adresse Nationale](https://adresse.data.gouv.fr/) | house numbers, France | ODbL |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | house numbers, everywhere else | ODbL |
| [Geofabrik's extract index](https://download.geofabrik.de/) | which extract covers a city's box | ODbL |
| [GeoNames](https://www.geonames.org/) | the municipalities a network covers | CC BY 4.0 |
| [BRouter](https://github.com/abrensch/brouter) | routing engine and generator | MIT |
| SRTM through [terrain-tiles](https://registry.opendata.aws/terrain-tiles/) | the graph's elevation data | public domain |

**The availability feeds are not under one licence, and two thirds of them name
none.** Surveyed on 26 August 2026 over the 331 networks served: 164 CC0 1.0,
40 Licence Ouverte 2.0, 22 CDLA-Permissive-2.0, 22 ODbL, 15 Licence Ouverte,
one CC BY 4.0 — and 67 that publish no `license_id` and no `license_url`, and
whose catalogue entry names no licence either.

The licence is read from the feed rather than assumed, and a feed naming none
is credited as naming none: the attribution written into each configuration
ends in *"licence not stated by the operator"*, in French where the network is
French. That clause is a finding, not a gap — it says the feed was read and
said nothing, which is what a reader of the sources page needs to know, and
what a licence guessed from the operator's country would have hidden. A third
of the licences that ARE named are named by address alone, with no code beside
them; `tools/discover_networks.py` holds the table that turns those addresses
into names, and each entry in it was read off the document the address serves.
