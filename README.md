# Roue Libre

## What it is

A free Android application that shows bike-share stations and computes a
door-to-door **walk → bike → walk** journey, choosing the best pair of stations
rather than the nearest one.

It serves **any conurbation whose network publishes its stations as open data**
in the GBFS format, and several of them can live side by side on the same
device. **306 networks in 35 countries are configured** — every one whose
stations are published and whose docks the journey algorithm can rely on, from
Vélib' to Auch's ten stations, by way of New York, Prague, Barcelona, Tokyo,
Buenos Aires and Pristina. The list, and what was set aside and why, is in
[`docs/networks.md`](docs/networks.md); three of them have their offline data
generated so far. Adding a network is a configuration file and a data
generation run, never a code change (see [Adding a city](#adding-a-city)). No
city is a default: the application proposes one from your position, and you
choose.

The interface is in English, and translations are welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md).

## The name

*Roue libre* is French for **freewheel**: the ratchet that lets a bicycle carry
on rolling while the pedals stand still. To ride *en roue libre* is to coast —
to be carried by what you have already put in, without pushing.

The phrase is doing three things at once, and all three are the project:

- **the freewheel** itself, the part that makes a bike a bike;
- ***libre-service***, French for self-service — a bike-share bike is a *vélo
  en libre-service*, which is what this application is about;
- ***logiciel libre***, free software. Not free of charge: free as in free to
  use, study, change and pass on. This one is under the GPL.

One word, and it says: a bicycle, shared, and free.

## What sets it apart

- **No Google service.** No Play Services, no Firebase, no Maps SDK. It runs on
  LineageOS without GApps.
- **No telemetry, no tracker, no unique identifier.** No journey data is kept:
  no history, no positions, no destinations.
- **Offline by default.** The vector map, the address search and the route
  computation all run on the device. Address search is the application's most
  sensitive data — it reveals where you are going — and it never leaves the
  phone.
- **Light.** Target: under 15 MB of APK. The downloaded data has no fixed
  ceiling; its weight follows the city served, and it is announced before
  downloading.

## Progress

The project follows the progression of `SPEC.md` §16.

| Stage | State |
|---|---|
| 1. Fetching and displaying GBFS data as a list | ✅ done |
| 2. Offline data generation scripts | ✅ done |
| 3. Vector map and markers | ✅ done |
| 4. Offline routing engine | ✅ done |
| 5. Optimised journey algorithm | ✅ done |
| 6. Local address search | ✅ done |
| 7. Remaining screens | ✅ done |
| 8. Finishing touches and F-Droid metadata | under way — data download, metadata and the sample translation are done |

A first installable version, **0.2.0-alpha**, exists: it shows the map and the
availability, searches an address offline, computes a door-to-door journey and
serves three networks. The datasets are still installed by hand — nothing has
been published to download yet — see the [CHANGELOG](CHANGELOG.md).

## Architecture

Two Gradle modules, and the boundary between them is enforced by the compiler
rather than by discipline.

```
┌─────────────────────────────────────────────────────────────┐
│  :app                                          Android      │
│                                                             │
│  ui/          single activity, fragments, XML views         │
│               ViewBinding, no Compose                       │
│      ↑ observed state (StateFlow)                           │
│  ui/*ViewModel                                              │
│      ↑ Outcome<T>                                           │
│  data/        StationRepository — freshness policy          │
│      ├── network/  OkHttp ──────────────────► GBFS feeds    │
│      └── local/    Room, DataStore                          │
│      ↑                                                      │
│  AppContainer  manual instantiation, no Hilt and no Koin    │
└──────────────────────────┬──────────────────────────────────┘
                           │ depends on
┌──────────────────────────▼──────────────────────────────────┐
│  :core                                    pure Kotlin       │
│                                                             │
│  gbfs/       feed parsing, tolerant of GBFS 2.x and 3.0     │
│  station/    domain model, availability scale, freshness    │
│              of the data                                    │
│  address/    street-name normalisation, edit distance,      │
│              ranking, house-number interpolation            │
│  journey/    the walk → bike → walk algorithm               │
│  geo/        coordinates, bounding box, distances           │
│  config/     reading the city configuration and catalogue   │
│  Outcome     result types, never a silent exception         │
│                                                             │
│  No Android import. Testable on the JVM, without emulator.  │
└─────────────────────────────────────────────────────────────┘
```

**The data flow.** The repository is the single source. It emits a continuous
stream of the local cache's contents, which means the interface shows something
immediately, offline included and from the first draw. The network comes on top:
a refresh writes into the cache, and the cache re-emits. No screen talks to the
network directly.

**Error handling.** No exception crosses a layer boundary. Failures are values —
`Outcome.Failure(DataError.Offline)` — and the only layer that puts them into
words is the interface. The business module is not allowed to hold a
displayable string.

## Building

The repository contains a submodule. Cloning without it would give a build that
fails on the routing engine:

```bash
git clone --recurse-submodules https://github.com/mgdx/RoueLibre.git
# or, on an already cloned repository:
git submodule update --init
```

```bash
./gradlew assembleDebug     # build
./gradlew test              # unit tests on the JVM
./gradlew lint ktlintCheck  # static analysis, no warning tolerated
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

You need a JDK 17 or later and the Android SDK (compileSdk 37). No key, no
account and no third-party service is required to build.

Release APK size, map, routing, address search and journeys included:
**7.67 MB on arm64-v8a** and 7.10 MB on armeabi-v7a, against a ceiling of 12 MB
per architecture. MapLibre's native libraries are packaged compressed — without
that, the same APK would weigh 14.87 MB.

## Generating the offline datasets

The three sets — base map, routing graph, address index — are not in the APK:
they are downloaded on first launch, or provided by hand. Their generation is
entirely scripted and versioned in [`tools/`](tools/README.md):

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
```

By hand, for a network the catalogues do not list:

1. **Find the GBFS feed URL.** Never guess it: take it from the
   [MobilityData catalogue](https://github.com/MobilityData/gbfs/blob/master/systems.csv),
   in France from [transport.data.gouv.fr](https://transport.data.gouv.fr/), or
   from the producer's own developer page — then verify it with a real request.
   An address found that way goes into
   [`config/extra-feeds.json`](config/extra-feeds.json), where the survey picks
   it up and judges it like any other.
2. **Copy a city configuration.** Start from
   [`config/cities/lille.json`](config/cities/lille.json) and adjust only the
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

**Where the addresses come from.** In France the index is built from the Base
Adresse Nationale, which is the finer source there; everywhere else it is built
from the OpenStreetMap extract the map and the routing graph are already cut
from, so a city costs one download rather than two. The configuration says
which, in `dataSources.addressSource`, and the coverage of the second varies
from one city to the next — that is the honest cost of not having a national
address base to lean on.

**Street names are normalised in their own language.** "Boulevard" abbreviates
to "bd" in French, "ulica" to "ul." in Polish, and "Straße" is typed "Strasse"
by half of Germany. One file per language holds those rules, in
[`config/address-normalization/`](config/address-normalization/); the index
records which one it was built with, and the application reads it back from
there rather than deciding for itself. A language with no file of its own falls
back on English — plain folding, which still finds a street typed in full — and
writing one is a pull request against a single JSON file.

## Dependencies, and why each one

`SPEC.md` §4 requires justifying every addition. Nothing enters without a
reason.

| Dependency | Role | Why it rather than another |
|---|---|---|
| **OkHttp** | HTTP requests | Three GET requests do not justify Retrofit. OkHttp alone suffices and weighs less. |
| **kotlinx.serialization** | reading JSON | Generation at compile time, so no reflection and no R8 rules to maintain — unlike Gson or Moshi. |
| **Room** | station cache | Required by `SPEC.md` §8. Brings reactive streams and compile-time query checking. |
| **DataStore** | settings | A few isolated values; Room would be out of proportion. |
| **Coroutines** | asynchrony | The language's standard. |
| **Material Components** | interface base | Proven accessible components. None of its default colours survives. |
| **BRouter** | offline route computation | A proven engine, cycling-oriented, with configurable profiles. Integrated as a **Git submodule** pinned to a tag: the `org.btools:brouter-core` Maven artifact one finds mentioned is published nowhere. MIT, GPLv3-compatible, licence notice kept in the application's legal notices. |
| **MapLibre Native** | offline vector map | The project's only native dependency, and its only accepted departure from the size constraint: it is the price of offline operation. Reads the MBTiles straight from disk, without a tile server. BSD-2-Clause, minSdk 23. |
| **AndroidX** *(core, appcompat, fragment, lifecycle, recyclerview, swiperefreshlayout, constraintlayout)* | interface building blocks | The base of an XML-view application. |
| **Atkinson Hyperlegible** | body typeface | Drawn by the Braille Institute for low vision: 0 is distinct from O, 1 from l. For an application read while walking, that is functional. SIL OFL. |
| **Bricolage Grotesque** | figures typeface | Bike counts are the central information; they deserve letters recognisable from a distance. Frozen into two static instances of 91 kB. SIL OFL. |

No analytics, crash reporting or advertising library, under any pretext.

**Data generation tools**, outside the APK: `osmium-tool`, `tippecanoe`,
`fontTools`, and the map creator from
[BRouter](https://github.com/abrensch/brouter) (MIT), whose version is pinned
and whose archive is verified by SHA-256 digest.

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

## Opening a place from another application

`geo:` and `google.navigation:` links, along with addresses shared as plain
text, arrive straight in Roue Libre: it is enough to choose it in Android's
chooser.

Links from mapping websites — `openstreetmap.org`, `google.com/maps` —
**cannot** be verified automatically, those domains not belonging to the
project. Since Android 12 they therefore only reach the application if you allow
it:

**Settings → Apps → Roue Libre → Open by default → Add link**, then tick the
domains you want.

A shortened link is not recognised: the place only appears after a redirect, and
following it would send a request out to a third party, teaching them where you
are going.

## Privacy

In ordinary use, **the only network request that goes out is the GBFS feed**.
Data downloads happen only on first launch or on an explicit action. Checking
for updates is never automatic: a periodic request would draw a usage profile.

No identifier is sent. The `User-Agent` names the application and its version,
nothing else. `android:allowBackup` is `false`: nothing goes to the cloud.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), which covers the translation procedure
in particular.

## Licence

[GPLv3](LICENSE). The embedded fonts are under the
[SIL Open Font License](app/src/main/assets/licences/), which is their own.
