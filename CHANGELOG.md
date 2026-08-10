# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
the project follows [semantic versioning](https://semver.org/).

The notes meant for users live in `fastlane/metadata/android/fr/changelogs/` and
are written for them, not for developers. This file addresses contributors and
also records what has no visible effect.

## [0.2.0-alpha]

### Added

- **Several cities in one application** (`SPEC.md` §15.1). Three networks are
  served: V'lille, Vélo'v and Vélib' Métropole.
  - A **catalogue** derived from the city configurations —
    `tools/build_catalogue.py` — carries for each of them its bounding box, its
    centre, its stations and the weight of its data. It is downloadable, so a
    new city can appear without publishing a release, and a copy ships in the
    APK as a fallback.
  - A **city screen** proposes the conurbation from the user's position, on a
    button press and never by itself. Beyond fifty kilometres from the nearest
    network, it proposes nothing.
  - The datasets are **stored per city**: two cities coexist without mixing,
    and one city's data can be deleted without touching the other.

- **Leaving from a station, or going to one.** The detail sheet offered adding
  to favourites and opening an external navigation application, but not
  preparing a journey — the only action of `SPEC.md` §7.2 that had been missing
  since the search screen existed.

### Changed

- **The station list starts with the nearest station**, and each row says how
  far it is, when the position is known and falls inside the served city.
  Elsewhere — position unknown, refused, or another conurbation — the
  alphabetical order stays.
- **"Places" became "Places libres"**, on the toggle and under the count: what
  is being counted is free docks, not places in the general sense.
- **Touching a station on the map brings it to the middle of what stays
  visible**, above the detail sheet, at the same zoom.
- **A network is named with its conurbation**: "V'lille — Lille". The city
  comes from the configuration, and the catalogue carries it.
- **One minute to return a bike**, two to take one (`SPEC.md` §6). The two
  gestures are not the same one.
- **The journey screen shows the shape of a journey**: walk, station, ride,
  station, walk.
- **The place icon used in lists and buttons is a line icon**, the size of the
  others. The map's two-tone pin stays on the map, where its size is legible.
- **The map opens on the user's position** when the system already holds one
  and it falls inside the served city. No fix is requested and no permission
  asked: what one came to see is the stations around oneself, not the middle of
  the conurbation.
- **The availability source is described like the others** in "about" — by what
  the data is and where it comes from, in GBFS, indexed on
  transport.data.gouv.fr. The producer's credit follows it, as the ODbL licence
  of the feeds requires (`SPEC.md` §4.5).
- **The application no longer assumes a default city.** It used to serve one
  compiled into the APK; it now serves the one it has been given, and says so
  until it has been.
- **The published files carry their network's name.** A GitHub release has a
  single namespace: three `tiles.mbtiles` would overwrite one another. On the
  device each file recovers its bare name — BRouter recognises its segments by
  name and would not find `vlille-E0_N50.rd5`.
- **The repository speaks English.** Comments, KDoc, documentation, commit
  messages, test names and the identifiers of the map style and of the design
  tokens. The interface stays French, and `values/` remains its source: it is
  the users' language, not the contributors' (`SPEC.md` §14).

### Fixed

- **The map reopened on the previous city's framing.** The framing survives the
  destruction of the view so that a trip to another screen loses nothing; it
  also survived a change of city, and opened Paris over Lille, outside the
  tiles, on a grey screen. It is only taken up again if it falls inside the
  served city's box.
- **One city's stations stayed on screen after changing to another.** The
  station cache did not know about cities; offline, nothing came to replace
  them, and the map of Paris showed the stations of Lille. Changing city now
  empties that cache.
- **A device that already had data installed does not find it again.** It was
  stored without a city; there is no way to guess which, and attaching it at
  random would show one city's map under another's name. It has to be installed
  again after choosing a city.
- **An installed city still offered its data "to download".** The row announced
  the weight to fetch above the line saying it was already there. Once
  installed, it shows the number of stations alone.
- **The theme chosen was forgotten as soon as one left the screen.** Applying a
  theme has the activity rebuilt, which cancelled the coroutine writing the
  choice down: it was applied but never saved. Written first, applied second.
- **Replaying the presentation left two screens on top of one another.** From
  "about", the sequence kept that screen on the back stack; the first Back drew
  it over the screen just opened. Coming out of the presentation now starts
  from a clean stack, with the map underneath.
- **The two journey fields were not aligned.** The second carried a start
  margin on top of a constraint that already positioned it, and sat sixteen
  density-independent pixels to the right of the first.

## [0.1.0-alpha]

The first installable version. It covers the whole of its subject — offline
map, live availability, address search, door-to-door journeys — but **it is not
a complete release yet**:

- the datasets are installed **by hand**, from the storage screen; downloading
  them from a manifest (§4.4) does not exist yet;
- the **settings** (§7.6), **about** (§7.7), **favourites** (§7.5), **first
  launch** (§7.9) and **what's new** (§7.10) screens are missing;
- opening from another application (§7.8) is not declared;
- the complete attribution, mandatory under §4.5, is carried by the map alone.

It is signed with a test key, never with a publication key: what goes out on
F-Droid will be rebuilt and signed there.

### Added

- **Offline dataset generation scripts** (`tools/`), with the reference bounding
  box derived from the network's stations and recomputed on every run.
  - An MBTiles base map filtered at generation time against a readable
    allowlist: **35.0 MB** for zooms 10 to 16.
  - A BRouter routing graph limited to the bounding box: **1.7 MB**, against a
    hundred or so megabytes for the standard segments.
  - A SQLite FTS4 address index, house numbers stored as deltas: **5.9 MB** for
    10,591 streets and 286,028 numbers.
  - A manifest with SHA-256 digests, so only what changed is downloaded again.
- **A GBFS layer**: parsing tolerant of versions 2.x and 3.0, Room caching, and
  the refresh policy of `SPEC.md` §4.1.
- **A city configuration**: the single source of everything specific to a
  conurbation, shared between the application and the scripts.
- **Design tokens**: the "slate" palette, two embedded type families, a single
  spacing scale and a single radius.
- **A station list screen** with the availability indicator, the bikes/docks
  toggle, pull-to-refresh and the age of the data.
- **The optimised journey algorithm** (§6): it chooses the best pair of stations
  rather than the nearest, penalises poorly stocked stations, offers three
  alternatives and reports the journeys where walking is faster.
- **The offline routing engine** (§5): BRouter integrated as a Git submodule,
  with two profiles written for this project — urban pedestrian and share bike.
  The graph is read from the installed file, the profiles are in the APK, and
  nothing goes out on the network.
- **The offline vector map** (§7.1): a base map read from the installed MBTiles
  file, a plain desaturated style driven by the project's colour tokens, and
  text glyphs embedded in the APK. Station markers carry the availability scale,
  cluster at distant zooms, and the OpenStreetMap attribution is borne by the
  map itself.
- **A storage screen** (§4.4): the three offline datasets with their size and
  date, manual import through the document chooser, and deletion. Installation
  is atomic — the file is written beside, validated, then put in place — and a
  refused file says why.
- **A filter on the list by station name**, insensitive to case and accents,
  tolerant of word order, and searching the postcode too.
- **First launch** (§7.9): three short pages — what the application is, what it
  does not do with your data, what it needs in order to work — each skippable,
  the last leading straight into the download. A screen and not a dialog,
  because it must be readable again from "about".
- **What's new after an update** (§7.10), shown once, and never on a first
  installation. If the gap spans several versions, all the intermediate notes
  are shown, from the most recent to the oldest.
  - The notes come from the **F-Droid metadata**
    (`fastlane/metadata/android/fr/changelogs/`), converted into an embedded
    resource at build time: F-Droid and the application show exactly the same
    text, without double entry. Nothing is downloaded.
- **F-Droid metadata**: short description, long description, release notes and
  six screenshots, written for the user and not for the developer.
- **Two more networks, generated and measured**: Vélib' Métropole (1,518
  stations, 994 km², **142.8 MB**) and Vélo'v Lyon (465 stations, 575 km²,
  **42.3 MB**), against Lille's 42.5 MB. Lyon's routing graph spans two BRouter
  segments, which exercises a multi-file dataset for the first time.
- **A complete English translation** in `values-en/`, the worked example §9 asks
  for. It shows a translator what a finished translation looks like, and allows
  checking that switching language breaks no layout.
- **Dataset downloading** (§4.4): reading the published manifest, comparing
  digests, and transferring what changed — and that alone. Refreshing the
  address index therefore does not force the thirty-five megabytes of tiles to
  come again.
  - **Resumption** of an interrupted transfer through a `Range` header, with a
    fallback to starting over if the server ignores it: appending the beginning
    of the file to what we already had would produce a corrupted file.
  - **The digest is re-verified** after receipt. A file that does not match the
    manifest is rejected and the previous installation stays intact: the files
    received are checked before anything at all is replaced.
  - A manifest announcing an unknown format version invites updating the
    application, rather than failing later when opening a file.
  - **Never automatic**: the check happens on a press, from the storage screen.
    A periodic request would draw a usage profile.
  - A warning when not on Wi-Fi — a warning, not an obstacle.
- **Opening from another application** (§7.8): the application appears in
  Android's chooser for the `geo:` and `google.navigation:` schemes, and for
  plain text sharing — the commonest case in practice, an address received over
  a messaging application. Every form in §7.8 is accepted, including
  `geo:0,0?q=…` whose leading point is a convention, and labels in parentheses.
  - The parsing lives in the business module, in pure Kotlin: fourteen JVM tests
    cover the spellings one actually meets.
  - **No network request** is triggered by an incoming intent: an address in
    words is resolved by the local index. A shortened link is therefore not
    recognised — following its redirect would teach a third party where the user
    is going.
  - A point outside the covered box is shown on the map, without any route being
    attempted, and the application says why.
  - Web map links are declared but **not verified automatically**: the domains
    do not belong to the project. The steps to take in Android's settings are
    explained in "about" and in the `README.md`.
- **Favourites** (§7.5): the list of stations marked as favourites, with their
  live availability, **reorderable by dragging**. The order is this screen's
  only setting, and it beats an automatic sort — the station one wants to see
  first is the one in one's own neighbourhood, not the first alphabetically.
  - Favourites move from a set to an **ordered list**: a set has no order to
    rearrange. Those saved by an earlier version are picked up rather than lost.
  - No swipe to delete: a favourite is removed through the station's star, where
    it was added. A destructive gesture on a list one handles in order to
    reorder it would fire by accident.
- **Settings** (§7.6): access to the offline data, a light / dark / system theme
  applied immediately, fixed pick-up and drop-off times, and the addresses of
  the availability feed and of the data manifest. Written by hand rather than
  with `androidx.preference`, whose visual grammar the project's tokens would
  then have had to fight.
  - The fixed times are **re-read on every route computation**: changing them
    shows on the next recompute, without a restart.
  - Emptying either address restores the city configuration's own, whose value
    the field's hint then shows.
- **"About"** (§7.7): version, privacy policy in plain words, the attributions
  of §4.5 — including the network's, read from the city configuration and not
  written into the code — the application's licence, a link to the repository,
  and the **complete texts of the embedded licences**. That last point is not a
  courtesy: §5 requires keeping BRouter's copyright notice and MIT text in the
  legal notices, and the SIL Open Font License of both typefaces asks the same.
  The licence folder is walked rather than enumerated in the code, so that
  adding a dependency and its licence does not require remembering to edit that
  screen.
- **Journey search** (§7.3): two points to designate and a button to swap them.
  The specification's four ways are all there — one's position, an address, a
  favourite station, a point picked on the map. The last is aimed at under a
  fixed crosshair, the map moving underneath, and the point returned carries the
  name of the street the index recognises rather than its coordinates.
- **Journey result** (§7.4): the track in three visually distinct legs — the
  walks in thin dots, the ride in a wide solid stroke, the shape carrying the
  information as much as the colour. Underneath, the total time, its
  distribution, the distance, the three steps with their stations and their
  availability, the other station pairs, and a recompute button: availability
  changes, and the journey chosen five minutes ago may no longer hold.
  - When no bike journey is possible, the screen says which of the five cases of
    §6 applies, rather than proposing an impossible journey.
  - When walking straight there is faster, it says so, as §6 requires.
- **Favourites** kept in DataStore and selectable as a journey point.
- **Location** (§7.1, §10): a "locate me" button on the map, which asks for the
  permission at the moment of use and never at launch. A refusal blocks nothing
  and triggers no second prompt. The position comes from the system provider —
  **never from Google's fused location services**, forbidden by constraint C2 —
  and is neither written, nor sent, nor kept from one session to the next.
  - Every available provider is queried **at once**, the first fix winning: GPS
    is the most accurate but stays silent indoors, where the network provider
    answers in a second. Proven on a device — the first version, which queried
    GPS alone, waited ten seconds to return nothing.
  - The distance from the user's position appears in a station's detail as soon
    as a position is known, without ever demanding one on that occasion.
- **Station detail** (§7.2), in a sheet sliding up from the bottom, opened by a
  touch on the map as on a row of the list: name, address, bikes, docks, docking
  points, service state and age of the data. The sheet stays alive while it is
  open — the counts follow the refreshing rather than being frozen at opening
  time.
  - **The address comes from the offline index**: the network's feed publishes
    none. Within fifty metres the address is named with its house number, beyond
    that only the street is cited as a neighbourhood — a station standing in the
    middle of a roundabout has no address. Measured on the real stations: half
    of them sit within fifteen metres of a known address.
  - **Favourites** kept in DataStore, by station identifier and nothing else
    (§8).
  - A touch on a cluster of stations zooms the map in, which eventually resolves
    it into distinct markers.
- **Offline address search** (§4.3): a SQLite index queried on the device,
  without a single network call, including while typing.
  - Two stages: an FTS4 full-text index by prefix, then a Damerau-Levenshtein
    fallback when the first returns fewer than three results. A typo, a
    forgotten letter or two transposed letters still find the street.
  - Normalisation **shared with the indexing script**: one rules file, and a
    test that replays the reference cases the script produces to prove the two
    implementations agree.
  - The house number is recognised in both writing orders, with its repetition
    mark ("12 bis rue X" as well as "rue X 12 bis"). A number absent from the
    index is **interpolated between its neighbours of the same parity**, never
    brought back to the middle of the street.
  - Ranking by match quality, with proximity deciding at equal quality.
  - A search screen with a 150 ms debounce, each keystroke cancelling the
    previous computation; the chosen address lands on the map.
  - **Absorbed municipalities**: the Base Adresse Nationale attaches Lomme and
    Hellemmes to Lille, whereas their residents type their own municipality's
    name. The index now carries that name — 450 streets concerned — and displays
    it, with the postcode to match: "Rue Danton, 59160 Lomme".

### Fixed

- **Landmarks had no municipality.** OpenStreetMap rarely tags the town of a
  metro station or a library: 2,011 of the 2,436 landmarks inside the Paris box
  carried none, and "Châtelet - Les Halles" was displayed without one. Each now
  receives the municipality of the nearest street, found through a
  kilometre-wide grid rather than by comparing every pair. No landmark is left
  without a municipality across the three cities.
- **Generation wrote every city to the same place.** Producing Paris erased
  Lille. Each city now has its own output directory, named after the network
  identifier in its configuration.
- **The normalisation reference cases were being replaced** at each generation,
  so the last city produced erased the proof brought by the previous one. They
  now accumulate, one file per network, and the test replays them all: 54 cases
  across two producers.
- **The bounding-box computation could not read GBFS 3.0**: it looked for the
  feed list under a language key, which that version removed. The tool had only
  ever seen Lille.

- **GBFS 1.0 feeds were unreadable**, including Vélib' Métropole's — fifteen
  hundred stations, the largest network in France. Those feeds publish the
  station identifier as a number where the format mandates a string. The
  conversion takes the number's raw text rather than going through an integer:
  an identifier is a label, not a quantity, and that is what guarantees the two
  feeds meet on the same key. Exercised against real captures of the Vélib'
  feed.

- **The journey search screen lost its first point.** Going through the address
  search destroys only the fragment's *view*, not the fragment; re-reading the
  state from an absent instance bundle therefore erased the fields already
  filled, and the second point overwrote the first. Found by trying the screen
  on a device, not by reading it.
- **The address search test erased the installed index** on the device running
  it. It now sets it aside and gives it back at the end.

- **Manual import of the routing graph produced an unusable file.** The file was
  renamed `routing.rd5`, whereas BRouter derives the segment's name from the
  coordinates it is looking for — `E0_N50.rd5` for Lille — and opens it
  directly. The graph therefore stayed on disk without ever being read, and the
  engine answered "no route" with nothing to point at the cause. The case was
  provided for in the code, but the branch had become unreachable when the file
  name was made non-nullable; the compiler said so, and the warning had not been
  followed.
- The imported document's name is now found even when the provider does not
  publish `DISPLAY_NAME`, which is the case for a `file:` URI.

### Changed

- **The datasets' format version was raised to 2**, the address index having
  gained the absorbed-municipality columns. A version 1 index is refused with a
  word about why, rather than failing at the first search.
- The map now **remembers its framing** when one leaves it for another screen:
  coming back used to bring the opening framing, which also undid the move to a
  found address.

### Verified

- The application launches and shows the network's real availability on an
  **AOSP emulator with no Google service at all** — zero `com.google.*` package
  installed, as acceptance criterion §11.1 requires.
- In airplane mode, the last known availability stays on screen and the
  application says so, with no blocking error.
- A complete walk → bike → walk journey is composed in **1.2 s** on the
  emulator, with the 268 real stations and the real graph — against a budget of
  3 s (§11.4). Chaining the computations sequentially took 2.4 s.
- The map displays, pans and zooms **without a single network request**: tiles
  read from disk, glyphs in the APK.
- The release build with R8 produces **2.82 MB per architecture** and works: the
  kotlinx.serialization keep rules are correct, which only shows in release.
- **Typo tolerance** (§11.11), measured on a Fairphone 5 with the real index of
  10,591 streets: 300 faulty queries generated at random — one letter removed,
  two letters transposed — over 150 streets drawn at random. **98.3 %** bring
  the requested street back in the first three results, and **100 %** when the
  municipality is typed. No query is left without a result.
- **Address search response time**, same device: first search **102 ms**, corpus
  loading included; subsequent searches **2 to 9 ms** when the full-text index
  answers; **61 ms median and 81 ms at the 95th percentile** when the fuzzy scan
  fires, for a maximum of 154 ms.
- **FTS4 and the `simple` tokenizer** work on the device, which `SPEC.md` §4.3
  asked to be verified rather than assumed.
- **The build is reproducible** (§11.15): two successive release builds, each
  preceded by a `clean`, produce an APK with an identical digest —
  `2c25d5fa38fd6715…`. Verified on one machine; reproducibility across machines
  is what F-Droid will check.
- **House-number placement accuracy** (§11.10), measured on the real index by
  cross-validation: a number is removed from its street, interpolated from its
  neighbours, then compared with the position the Base Adresse Nationale gives
  it. Over 3,933 numbers drawn at random from 8,524 streets: **median error
  3.3 m**, 95th percentile 41.4 m, **96.5 % under the 50 m** required. Falling
  back on the middle of the street, which the interpolation exists to avoid,
  would give a median of 30.7 m and 204 m at the 95th percentile. A number
  **present** in the index is returned exactly.

### Technical notes

- The `org.btools:brouter-core` Maven artifact the specification mentions **does
  not exist**: zero results on Maven Central. BRouter is therefore consumed as a
  composite build from a submodule pinned to v1.7.10.
- BRouter derives its segment file's name from the coordinates it is looking for
  — `E0_N50.rd5` for Lille. The graph therefore keeps its original name at
  installation, unlike the other two datasets.

- The GBFS feed URL was taken from the MobilityData catalogue and cross-checked
  against the French national access point, whose resource redirects to the same
  address. It was not guessed.
- The feed announces `ttl: 0`, an unusable value: the application applies its
  own freshness policy.
- The two station feeds are not in step — 268 stations on one side, 267 on the
  other on 9 August 2026. The join tolerates that by construction.
- `fontVariationSettings` requires API 28 while the `minSdk` is 26: the variable
  font was frozen into two static instances, which also brought its weight down
  from 408 to 182 kB.
- Addresses are grouped by (INSEE code, former municipality, normalised name)
  rather than by `id_fantoir`, which is empty on 24,363 rows of the box. The
  previous grouping cut 69 streets in two and sent 0.53 % of addresses more than
  50 m away; the rate fell to 0.04 %.
