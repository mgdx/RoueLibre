# Specification — Roue Libre (Android application)

> **To the agent:** this document is the project's source of truth. When in doubt between this file and a "classic" Android development habit, this file wins. Every structural decision has been settled: do not revisit any of them without discussing it first. If a constraint looks impossible to meet, say so and propose an alternative rather than silently working around it.

---

## 1. Goal

**Roue Libre** — the name plays on the double meaning of *libre* in French: self-service and free software. It is **deliberately independent of any city and any network**, as required by §15.

An Android application that lets you:

1. See the stations of a bike-share network on a map with their real-time availability (bikes available, free docks).
2. Compute a combined door-to-door journey: **walk → bike → walk**, automatically choosing the best departure station and the best arrival station.

**Which network? Any that publishes its stations as open data.** The application serves every conurbation whose network publishes a GBFS feed — the international bike-share standard — and adding one is a matter of a configuration file and a data generation run, never of code (§15). Several cities live side by side in the same installation, and the user chooses which one is being served (§15.1). No city is a default, and none is privileged in the code, in the interface or in the visual identity.

The figures quoted throughout this document come from the conurbations actually generated — Lille, Lyon, Paris. They are measurements, not a scope: they say what to expect of a medium-sized city and of a capital.

The application is a personal tool, plain and fast. It is not a booking application: it never talks to a network's user account.

## 2. Non-negotiable constraints

| # | Constraint | Consequence |
|---|---|---|
| C1 | **Open source**, published on F-Droid | Free licence, reproducible build, no proprietary dependency |
| C2 | **No Google service** | Forbidden: Google Play Services, Firebase, FCM, Maps SDK, ML Kit, Crashlytics, Play Integrity. The application must run on any Android OS without Google services |
| C3 | **Privacy** | No telemetry, no tracker, no unique identifier, no account. The user's position never leaves the device |
| C4 | **Lightness** | Target APK **under 15 MB** (**under 12 MB** per architecture), excluding downloaded data. Every dependency added must be justified in the documentation, in `docs/dependencies.md` |
| C5 | **Complete offline operation** | Map, address search and route computation all work **with no network at all**. Only real-time station availability needs a connection |
| C6 | **English by default, translatable** | See §9 |

The application must pass the **F-Droid / Exodus Privacy** scan with no tracker detected.

## 3. Technical stack

- **Language:** Kotlin
- **UI:** XML views + ViewBinding + Material Components. **No Jetpack Compose** (weight, constraint C4)
- **minSdk: 26** (Android 8.0) — **targetSdk:** the latest stable version. Rationale: `java.time` available natively (no desugaring to configure), adaptive icons, and above all an up-to-date TLS stack, indispensable for downloading the datasets without running into the obsolete certificate stores of earlier versions. Check that the minSdk required by MapLibre Native is not higher.
- **Network:** OkHttp + `kotlinx.serialization`. No Retrofit, no Gson, no Moshi
- **Mapping:** MapLibre Native, fed by a **local** vector tile file (see §4.2). It is the project's only native dependency, and the only accepted departure from constraint C4: it is the price of offline operation
- **Asynchrony:** Coroutines + Flow
- **Persistence:** Room for the station cache, `DataStore` (Preferences) for settings
- **Architecture:** simple MVVM, a single activity, fragment-based navigation. No dependency injection framework (Hilt/Koin): manual instantiation through an `AppContainer`
- **Build:** Gradle Kotlin DSL, R8 enabled in release, `shrinkResources true`, **ABI splits** (MapLibre's native libraries must not ship four times in the same APK)
- **`applicationId`:** of the form `io.github.<account>.rouelibre`. Do not invent an identifier based on a domain the project does not own — it is irreversible once the application is published.
- **Licence:** **GPLv3**. Every dependency added must be compatible with that licence — to be checked before integration, in particular for the routing engine (§5)

No analytics, crash reporting or advertising library, under any pretext.

## 4. Data sources

### Reference bounding box

All the offline datasets — tiles, routing graph, address index — share **one and the same bounding box**, defined once and for all in the generation scripts.

Beware of the misreading a network's name invites: it is almost never the city it is named after. A bike-share network serves a conurbation — a group of municipalities, town centres and outskirts together. The Lille one, for instance, covers **95 municipalities over nearly 672 km²**, from Roubaix and Tourcoing to rural municipalities of the Weppes; the Paris one covers 211.

**The bounding box must not be the administrative boundary of the conurbation**, which would cover vast areas without a single station and would needlessly inflate all three datasets. It is **derived from the stations themselves**:

1. compute the rectangle enclosing every station present in `station_information.json`, **less the ones that are not really there**: a position at latitude zero, a field left empty rather than omitted, and a station standing more than 25 km from every other — Valenbisi publishes one in Madrid, three hundred kilometres from Valencia, and it stretched the network's rectangle from 150 km² to 33,645. The stations of a docked network are a few hundred metres apart; what is dropped is named in the generation log, never swallowed;
2. widen it by a **3 km margin**, to cover walking legs from or towards the edge of the network and to avoid edge effects in route computation near the boundaries of the graph.

This bounding box is **recomputed every time the data is regenerated**, which automatically follows extensions of the network. It is written into the city configuration file (§15) and shown in the "storage" screen. No bounding-box coordinate is hard-coded in the application, and every city served has its own.

A consequence to accept: outside that box, the map and route computation do not work. The application must detect this and say so clearly, never fail silently.

### 4.1 Station availability — GBFS

The networks served publish their availability in the **GBFS format** (the international bike-share standard), refreshed **every minute** as a rule. Versions 1.0, 2.x and 3.0 are all in the field and must all be read: a producer's version is its own business, not the user's.

Files used:

- `gbfs.json` — auto-discovery file listing the other feeds
- `station_information.json` — static data: identifier, name, latitude/longitude, capacity
- `station_status.json` — real-time data: bikes available, free docks, station in service or not

**Implementation rules:**

- The `gbfs.json` URL **must not be guessed**. The agent must obtain it from a public catalogue — MobilityData's `systems.csv`, the registry the GBFS standard keeps of itself and the only one covering every country; the dataset page on `transport.data.gouv.fr` for France; or the producer's own developer page — then verify it with a real request before writing it into the code. An address found on a producer's page goes into `config/extra-feeds.json` with the page it was read from, so the claim can be checked.
- Every feed URL goes through the auto-discovery file, never hard-coded: that is the principle of GBFS and it protects against URL changes on the producer's side.
- The `gbfs.json` URL comes from the **city configuration** (§15), never from a setting. A happy consequence: the application works with any GBFS network in the world without a code change — serving another one means adding its city, not retyping an address by hand. There is no URL field in the interface: an address typed wrong there breaks the availability screen with nothing to explain why, while the catalogue's entries have been verified.
- Do not use a network's legacy proprietary API, nor the third-party JSON wrappers found on GitHub, when a GBFS feed exists: they are **deprecated** and nobody maintains them. Lille's old `vlille-realtime` API is the example to hand.

**Refresh policy:**

- `station_information.json`: cached in the database, refreshed at most once a day (respect the feed's `ttl`).
- `station_status.json`: refreshed when the map screen opens, then at most every 60 s while the screen is visible, and on a pull-to-refresh gesture. **No background refresh**, no periodic `WorkManager`.
- The age of the data must be shown to the user ("12 s ago").
- Offline: show the last known state, clearly marked as stale.

### 4.2 Base map

**Offline vector rendering** through MapLibre Native. No tile request goes out to a server during use.

- Format: **MBTiles** (a SQLite database holding the tiles). MapLibre Native reads it straight from disk through the `mbtiles://` URI scheme, usable as such in the style — it is the best-supported path. PMTiles was rejected: its strength is serving tiles from static hosting through HTTP *Range* requests, which is useless here since the file is downloaded whole, and its sources handle neither offline packs nor caching on the MapLibre Native side.
- Extent: the one defined at the head of §4. Zoom **10 to 16**. Zoom 16 is amply enough to find your way in a street; going to 17 or 18 would blow up the size for no gain in this application.
- The tile file is **not in the APK**: it is downloaded on first launch (see §4.5). Expected order of magnitude for a medium-sized conurbation: **30 to 60 MB**. A dense metropolis legitimately produces more — Paris, with 1.24 million building footprints inside its box against 78,000 for Lille, produces 115 MB. **300 MB is the figure to design for, not a gate.** It applies to the base map alone, the heaviest of the three sets, and every conurbation network stays well under it — the heaviest of the three hundred measures 172 MB. A network serving a whole region does not, and it is served anyway: refusing the map would be refusing the network, and these networks are real — Vélo Fluo puts one station in each town of the Grand Est, 261 km by 327, and its map weighs 1,343 MB. What the user is owed is not a promise about the file's size but the size itself, announced before the download starts and reclaimable afterwards (§11.9), which is what lets them decide on a phone with eight gigabytes free. The rendering rules stay the same for everyone and no per-city exception is carved out: a rule that holds for one conurbation only is not a rule. The lever named below — building footprints, moved to zoom 16 or dropped — remains what to pull if the whole corpus has to be slimmed, and it was measured rather than assumed: on the six heaviest maps it returned 15%, which brought a 323 MB map under the figure and left a 932 MB one far above it. It is not the answer to a regional box, and it changes the map of every city, which is why it is not pulled for the sake of one.
- Map style: a plain style, embedded in the APK as JSON, with the fonts and icons it needs. No style downloaded from a third-party service.
- The **text glyphs travel whole**: every range of 256 characters of the Basic Multilingual Plane ships, for each font the labels use. This is not typographic zeal — a range MapLibre asks for and does not find fails the tile that needed it, and that tile is then not drawn at all, streets and rivers included. One Romanian letter emptied the map of a whole town. A range the font does not cover answers in forty-four bytes, so the whole plane costs a few hundred kilobytes: what the font cannot draw stays blank, and nothing else is lost.
- File hosting: see §4.4. The **regeneration procedure** must be documented and scripted in the repository, so the file can be updated without depending on anyone.

**Map content.** Filtering happens **when the tiles are generated**, not only in the style: what is not kept weighs nothing. It is the main lever on size after the zoom level, and it serves visual restraint as much as lightness.

Kept, because they help you find your way:

- **transport**: metro and tram stations (at every zoom), railway stations, bus stops (**from zoom 15 only**, as discreet unlabelled points — a conurbation has several thousand of them, showing them earlier would drown the map and the stations with it);
- **public facilities**: town halls, schools, secondary schools, universities and higher-education institutions, hospitals and clinics, post offices, libraries, media libraries, swimming pools, gymnasiums, cemeteries;
- **visual landmarks**: monuments, churches and religious buildings, museums, theatres, belfries, statues and notable features;
- **urban fabric**: parks and green spaces, watercourses and canals, railways, street names, municipal boundaries, names of municipalities and neighbourhoods;
- **building footprints**, but **from zoom 15 only** and as a discreet flat fill. It is often the heaviest layer of a vector tile set: if the size budget is exceeded, it is the first lever to pull.

**Excluded**, because they clutter without serving: shops, restaurants, bars, cafés, hotels, banks, cash machines, hairdressers, agencies, company offices, petrol stations, private car parks, and every commercial point of interest. This exclusion is a **deliberate design choice**, not an omission: the map is scenery, the stations are the subject (§7).

The list must live in a **readable configuration file** of the generation script, so it can be adjusted without diving back into the code.
- The user must be able to install a map that does not come from the default host: not by typing a URL, but by **importing a local file** from the "storage" screen (§4.4), the very file the generation scripts produce.

### 4.3 Address search — local index

Address search runs **entirely on the device**. No online geocoder, no third-party request: it is the most sensitive data in the application, since it reveals where the user is going.

- Source: the country's open address base. For France, the **Base Adresse Nationale**, in freely downloadable per-department extracts. **Everywhere else, OpenStreetMap** — the very extract the base map and the routing graph are already cut from, so that a city costs one download rather than two. The city configuration says which, in `dataSources.addressSource`, and the script reads the two behind the same interface: house numbers from `addr:housenumber`, streets from the named ways, municipalities from the nearest inhabited place. The coverage of the second is not the first's, and it varies from one city to the next; that is the accepted cost of serving a country that publishes no address base of its own.
- **Granularity: the house number.** A thoroughfare is often over a kilometre long: a single point per street would produce an error of several hundred metres, enough to designate the wrong station and therefore a wrong journey. House-number precision is a requirement, not a comfort.
- The index forms **a single downloaded package** together with the other datasets (§4.4); nothing is embedded in the APK:
  - **Streets** — one entry per street: name, municipality, postcode, representative point. Around 15,000 to 20,000 entries over the reference box, **1 to 3 MB**.
  - **House numbers** — one entry per address, attached to a street. Around 450,000 to 550,000 entries over the reference box, **12 to 25 MB** depending on the encoding. Those figures correspond to the dense area covered by the stations; keeping a conurbation's whole administrative area would raise them appreciably for no real use.
- Encoding: do not store text per address. A house-number entry = street reference + number (integer plus an optional `bis`, `ter`, `A` suffix) + coordinates **encoded as deltas from the street's point**, on two short integers. That is how we aim for the bottom of the range rather than the top.
- If the number typed does not exist in the index, **interpolate** between the two nearest known numbers of the same street rather than falling back on the middle of the street.
- Also add the **points of interest useful for finding your way**: railway stations, metro stations, universities, hospitals, major squares. A few thousand extra entries, extracted from OpenStreetMap, treated as streets.
- Implementation: **SQLite FTS on street names only** — that is what makes the whole thing viable. Numbers are never searched full-text: once the street is identified, the number resolves through a plain index on (street, number). Search insensitive to case and accents, tolerant of common abbreviations ("bd", "av", "st"), results ranked by proximity to the current position.
- **Normalisation is written per language**, in `config/address-normalization/<language>.json`, and the language meant is the one the **address base** is written in, never the one the interface speaks: an index built over Antwerp is searched in Dutch whatever the phone is set to. A street type is a word of a language — "rue" says nothing about a Warsaw address, where the word is *ulica* — and §15.1 requires those rules to travel with a city's data. The index records the language it was built with; the application reads it back from there rather than deciding for itself, so an index and a rule set can never be paired wrongly. A language with no file of its own falls back on English: plain folding, no street type, which still finds a street typed in full.
- **Tolerance to typing mistakes.** Search must find a street despite a typo, a missing letter or two transposed letters. Implemented in two stages:
  1. **Normalisation**, applied to the index and to the query alike: lowercase, letters folded that accent removal cannot reach (ß → ss, ł → l, ø → o, final sigma → sigma), accents and punctuation removed, abbreviations expanded ("st" → "saint", "bd" → "boulevard", "av" → "avenue", "fbg" → "faubourg", "ul." → "ulica"). The **street type is stored in a separate field** from the proper name, so that "gambetta" finds "rue Gambetta" and "rue de la gare" is not penalised by word order. Prefix matching on each word, which covers typing in progress — and **only** typing in progress: a text arriving whole from another application is matched word for word (§7.8). A word of two letters or fewer, like a stop word, weighs too little either to rule a street out or to single one out: it is matched as itself, never through a correction, "on" being one mistake away from "Or" as much as from "En".
  2. **Edit-distance fallback** when the first stage returns fewer results than expected: **Damerau-Levenshtein** distance — it handles letter transposition, the commonest mistake on a touch keyboard — computed in Kotlin over the normalised names held in memory. With a corpus of around 20,000 entries and under a megabyte, a full scan stays in the tens of milliseconds.
- Tolerance threshold **proportional to length**: one mistake allowed below eight characters, two beyond. Past that, the noise exceeds the service.
- Search **triggered with a debounce delay** (around 150 ms) and **cancellable**: each keystroke cancels the previous computation. No computation on the main thread.
- **Result ranking** by a combined score: match quality first, proximity to the current position second. At equal match quality, the nearer street comes first.
- Do not depend on SQLite's trigram tokenizer: it is absent from the versions embedded in the oldest Android releases the application targets. Fuzziness happens in Kotlin. Likewise check that the chosen FTS version is actually available on an API 26 device, and plan a fallback.
- Document the index generation script in the repository, to allow its regeneration and its extension to other conurbations.

### 4.4 Initial data download

On first launch, a screen explains clearly what is about to be downloaded, at what size, and asks for confirmation:

- the vector base map of the city served (§4.2);
- routing data (§5);
- the address index, streets and house numbers (§4.3).

The three sets form **a coherent whole, versioned and published together**. The application has no geographic data at all until they are installed: before that it is limited to the station list and their availability, and says so clearly.

Constraints: download **resumed after an interruption**, a warning if the user is not on Wi-Fi, the option to postpone (the application then remains usable in degraded mode: station list and availability, without map or journeys), and an integrity check on the downloaded file.

**Manual import is mandatory.** Every dataset must be **providable by hand** from a file present on the device, with no download at all. Someone who generates their own files, or copies them over a cable, must be able to install and use the application without it issuing a single request to a data server. Downloading is the default path, never the only path.

**Data file hosting: the GitHub repository's *releases***, as attached files. The per-file size limit there is far above our needs.

Associated rules:

- Data is published as **releases distinct from the application's** (tagged `data-2026-08`, for instance), so that updating the base map does not force an application release, and the other way round.
- Every data release is described by a **manifest file** (a few kilobytes) listing, for each of the three sets: its identifier, its version, its generation date, its URL, its size and its **SHA-256 digest**. The manifest also carries the bounding box and the format version the application is expected to read.
- **Update by digest comparison.** The application keeps the digest of every installed file, fetches the manifest, and re-downloads **only the sets whose digest changed**. Refreshing the address index alone must never force 60 MB of tiles to come down again.
- The digest is **re-verified after download**: a file that does not match the manifest is rejected, and the previous version kept. An interrupted or corrupted update must never leave the application unusable — write the new file beside the old one, validate, then replace.
- **Checking is never automatic in the background**: it happens on an explicit user action, from the "storage" screen, which shows when it last ran. A periodic request would draw a usage profile of the application, which constraint C3 rules out.
- If the manifest announces a **format version** the application cannot read, say so clearly and invite the user to update the application, rather than failing when opening a file.
- The manifest's URL comes from the **city configuration** (§15), and the application can work through manual import anyway (see above). The host must never be a single point of failure.
- The `User-Agent` of downloads identifies the application and its version, with no identifier specific to the user.

In every case, two safeguards are **mandatory**, so that the application survives the disappearance of its host: the local file import (§4.2), and the regeneration scripts versioned in the repository. The default URL must never be a single point of failure — someone whose host has vanished regenerates the files and imports them, or edits the city configuration and rebuilds.

A "storage" screen must list every dataset with its size, its date, an update button and a delete button. The user must always know what the application occupies and be able to reclaim it.

### 4.5 Attribution (mandatory)

An "about" screen must show:

- the attribution and licence of the availability feed of the network served, as its configuration declares them — they change with the city;
- "© OpenStreetMap contributors";
- the attribution of the routing engine and its data;
- the application's licence and the link to the repository.

The OpenStreetMap attribution must also be visible **on the map itself**.

## 5. Routing engine (offline)

**Recommendation: BRouter**, an offline bike routing engine, proven on Android, cycling-oriented, with configurable profiles.

- **Licence: MIT** (verified). Compatible with the application's GPLv3: MIT code can be integrated into a GPLv3 whole, provided the **MIT copyright notice and licence text are kept** in the application's legal notices. Careful: several third-party pages and old *brouter-web* repositories still describe BRouter as GPLv3 — that is obsolete, the `LICENSE` file of the `abrensch/brouter` repository is authoritative.
- Integrate **BRouter's core as a library** in the application, rather than depending on the BRouter application being installed separately (the user must have only one application to install). The module is published as a Maven artifact: `org.btools:brouter-core`. Check the current version and prefer that dependency to a copy of the sources in the repository.
- The **routing data** is **not embedded in the APK**: it is downloaded on first launch (§4.4).
- **Priority: generate a dataset limited to the reference bounding box defined at the head of §4** rather than using the 5°×5° segments distributed by BRouter, which cover a good part of northern Europe. That takes us from roughly 100–170 MB down to **15–40 MB**. It is the only place where weight is divided by five, so it is worth the integration effort. The generation script must be versioned in the repository.
- If that carve-out turns out to be impractical, report it and propose falling back on the standard segments **before** implementing it.
- Two profiles are needed: **pedestrian** (access legs) and **urban bike** (main leg).

If the agent judges BRouter unsuitable after investigation, it must **propose an alternative and wait for approval**, not decide alone.

## 6. Optimised journey algorithm

This is the application's business core. To be implemented in an isolated, testable class, with no dependency on Android.

**Inputs:** departure point, arrival point, current state of the stations.

**Principle:** never settle for "the nearest station". Optimise the **pair** departure station / arrival station.

1. Select the **N candidate departure stations** (N = 5 by default) among those nearest the departure point with `num_bikes_available ≥ 1` and in service.
2. Select the **M candidate arrival stations** (M = 5 by default) among those nearest the arrival point with `num_docks_available ≥ 1` and in service.
3. For every (departure, arrival) pair, compute: walking time to the departure station + biking time between stations + walking time to the destination.
4. **No fixed allowance for taking or returning the bike.** Until 12 August 2026 a configurable pick-up and drop-off time — 2 min and 1 min by default — was added to every journey; it was dropped on that date, setting included. Being the same constant on every pair, it never decided which pair won: it shifted every total by the same amount, and what it really did was ask the user to tune a figure they had no way of measuring. Two consequences are accepted: the announced time is now the one the routing engine traced and nothing else, and the bike wins the comparison with walking on trips slightly shorter than before, since it no longer carries three minutes of standing still.
5. Keep the pair with the smallest total time, and offer **that one alone**. The application proposes a single journey, the one it has proved best. A list of runner-up pairs asked the user to arbitrate a choice the risk penalty of the rules below has already made for them, on figures they cannot weigh better than the algorithm: it was a way of not deciding. What the interface owes them instead is the availability of the stations chosen, so they can judge the risk they are taking (see below).

**Reliability — important rules:**

- A station with a single bike can empty while you walk to it. Apply a **risk penalty** that grows as availability falls: a station with 1 bike is less attractive than a station with 8, even slightly further away. Same at the arrival end for free docks.
- Always show the number of bikes or docks of the chosen station, so the user can judge for themselves.
- **No distance disqualifies a station.** The N nearest stations that can lend a bike, and the M nearest that can take one back, are candidates however far they stand. Until 13 August 2026 a station beyond 1.2 km was struck off, and Tourcoing → Wattignies — 17 km, a station 200 m from the departure point, the nearest one to the arrival 2.4 km off — was answered with 19.7 km of walking, three hours fifty-four, in breach of §11.4. A threshold on the access walk cannot tell the trip where it is worth twenty minutes from the trip where it is worth none, because it never sees the length of the journey it is guarding; the comparison with the direct walk sees it, and decides on measured times.
- **What replaces it is the comparison with walking, made at every distance.** Below `directWalkThresholdMetres` the direct walk is traced alongside the access walks, where it costs a core rather than a wait. Beyond, it is traced after the fact, and only when the journey found fails to beat the straight line covered at 1.8 m/s — a pace no walker holds, so a journey quicker than that beats every real walk and needs none computed. The bound is optimistic in the walker's favour on purpose: were it the other way, a walk that would have won could be skipped. At most one route computation is added, and only on a journey where walking might still be the answer.
- If the bike journey is slower than walking straight there, **the walk is the journey offered** — the bike trip is not shown at all. Until 13 August 2026 the ride was announced with a note saying one would get there sooner on foot; that note handed back a comparison the algorithm had already made, and left the user to ask for the walk they had just been told to take. The summary says why the answer is a walk: sooner than going to fetch a bike.
- The number of route computations (N × M + walking legs) must stay bounded, computed off the main thread and cancellable. There is **no fixed deadline on the answer**: the earlier one of 3 seconds was dropped on 12 August 2026, because it was held by cutting every leg off at 5 seconds — and a journey right across a conurbation, whose bike leg alone takes longer than that on a phone, was then reported as having no route at all. A wrong answer in three seconds is worth less than a right one in thirty. What remains is a per-leg limit set by the slowest device we mean to serve rather than the fastest (1 min, see `OfflineRouter`), and an interface that says it is working for as long as it takes.

## 7. Screens

### Visual identity and interface principles

The application must be **carefully made, spare and alive** — not a default Material interface. But "make something beautiful" only produces a generic result: the direction below is therefore binding.

**Token system.** Before writing a single view, produce a design-token file and have it approved:

- a palette of **4 to 6 named colours** (background, ink, accent, plus the availability scale);
- **two type families**: one with character for numbers and titles — bike counts are this application's central information, they deserve memorable treatment — and one neutral and legible for everything else;
- a single **spacing scale** and corner radius, applied everywhere.

No colour or size may be hard-coded in a layout: everything goes through resources.

**Signature element.** Spend the boldness in a single place: the stations' **availability indicator**. It is what the user looks at a hundred times a week, it is what they will remember. It must read instantly, while walking, in the sun, at a glance. The rest of the interface is calm and disciplined around it.

**Explicitly to be avoided**, because they are reflexes rather than choices: the cream background with a terracotta accent, the black background with a single acid-green accent, the stack of drop-shadowed cards on grey. If a style decision could apply as-is to any other application, it was not a decision.

**The map style is a design object**, not a technical setting. The background must be **desaturated** so the markers stand out: the map is scenery, the stations are the subject.

**Quality constraints, non-negotiable:** light and dark themes both carefully made; touch targets of at least 48 dp; contrast meeting accessibility guidance; content labels on every interactive element; the system "reduce animations" preference respected; the interface usable one-handed, the main controls within thumb reach.

**Motion:** in the service of understanding only — markers appearing, the route being drawn, the transition to a station's detail. Nothing ambient, nothing decorative.

**Interface writing:** short sentences, active voice, an action bearing the same name from the button through to the confirmation. An error message says what happened and what to do, without apologising or staying vague. An empty screen is an invitation to act, not a statement of fact.

**No network's name appears in the visual identity**: not its brand colour, not its logo, not its typeface. The application has an identity of its own, the same whichever city it is serving — that is a portability requirement (§15) as much as trademark caution.

**The bike drawn says what is lent.** Where the network in service lends pedal-assist bikes, every bike glyph drawn for that city bears a small bolt: the button opening the journey search, the ride leg, and the discs standing for a station wherever they appear — map, illustration, detail. Not a brand mark and not a decoration: it is the difference between two offers, and it is the first thing one wants to know before walking to a station. Whether it applies is read from the city configuration (§15) and from nowhere else, so a mechanical fleet in one conurbation and an electric one in the next need no code change. A mixed fleet counts as electric: what the bolt answers is whether the city lends electric bikes. The application's own identity is untouched — the launcher icon and the welcome screens are the same whichever city is served, and one of them is shown before any city has been chosen at all.

### 7.0 Opening

The first thing the application shows is its own mark: the launcher icon's bicycle and pin, white on the identity's green filling the screen, with the application's name under it. It is what the user has just tapped, held a moment longer — the point is continuity, not ceremony.

**It leaves when three things are true**: the first screen is settled — the last version code seen has been read, which decides between the welcome screen (§7.9), the what's-new screen (§7.10) and the map — that screen has something drawn in it, and the opening has been on the screen for **six hundred milliseconds**.

That floor was not the intention and was put in on evidence. Without it the opening lasts exactly as long as the work it hides, which sounds right and is not: measured frame by frame on a Fairphone 5, the whole thing was over in two tenths of a second and the name appeared only in the three frames of the fade-out, over a map already showing through. The faster the phone, the less the screen exists — and a screen that exists only on slow hardware is not a screen. The six hundred milliseconds are counted from the moment the opening actually reaches the glass, not from the application starting: until Android's own splash hands over, whatever we drew was drawn underneath it.

The wait is the longest of the three conditions and not their sum: what the floor adds is only the part the start-up had not already spent once the opening was up — two tenths of a second on a Fairphone 3, half a second on the faster Fairphone 5. It is a floor, not a delay laid on top of the wait.

**The green starts before the application does.** Between the tap and the activity's first frame, Android draws a window of its own from the theme declared in the manifest; on Android 12 and later it draws its splash screen there. Both are given the identity's green and the same mark, so the sequence is one continuous image rather than a pale flash followed by a green one. The system bars are green for as long as the opening lasts, and are handed back to the theme's colours with it.

**The identity does not follow the theme.** The green and the white are fixed, in the dark theme as in the light one: they are the mark's own colours, the ones the launcher draws the icon in, and a white bicycle on the dark theme's lighter green would not be the same mark. They are held apart from the interface's tokens for that reason.

Nothing on this screen can be pressed, and nothing is announced to a screen reader twice: the mark is decorative, the name says it.

### 7.1 Map (main screen)

- Full-screen map, centred on the user's position when the system already holds one inside the city served, and on that city's configured centring otherwise.
- One marker per station, **legible at a glance**: the colour code reflects availability (no bikes / few / fine / station out of service). Colour alone must never carry the information: add the figure or a distinct shape (accessibility, colour blindness).
- **"Bikes" / "docks"** toggle: depending on whether the user wants to borrow or to return.
- Marker clustering at distant zoom levels.
- **The edge of the served area is never shown.** The camera stays over the reference bounding box of §4: it may not zoom out further than that box covering the screen, nor pan close enough to an edge for the emptiness beyond it to show. Past that edge the base map has nothing to draw, and a straight line of nothing across the screen says "the download stops here" to nobody. The consequence is accepted: on a small conurbation the widest zoom is the one framing the box, whatever the configuration's `minZoom` allows. The same limit holds on the journey's map (§7.4), where it costs the margins of a journey crossing the conurbation from end to end.
- "Locate me" button, "refresh" button, access to the settings and to the journey search.
- The **user's position follows the device in real time** while the map is on screen. The point moves, the framing does not: recentring at every fix would take the map back from under someone looking a street further on, and "locate me" is what brings it back. The subscription stops with the screen, so nothing listens to the satellites in the background.
- The location permission is **asked for when the map opens**, once per session (§10). The point that follows the device is what this screen is for, and reaching it through a button first is a detour. Refused, the map stays whole and pointless — in the literal sense — and nothing asks again: "locate me" is what remains to change one's mind.
- Data-age indicator.

### 7.2 Station detail

A sheet sliding up from the bottom: name, address, bikes available, free docks, total capacity, state, distance from the current position, timestamp. Actions: add to favourites, set as the departure or the arrival of a journey, open in an external navigation application.

### 7.3 Journey search

- Two fields: departure and arrival. Each accepts: my position, a favourite, a point picked on the map, an address.
- A field **opens the address search directly**, without a menu of ways in between: one nearly always knows the address one is going to. The three other ways head the result list, where they stay whatever is typed — **my position first**, since that is what somebody setting off means by "from here".
- Address search queries the **local index** described in §4.3. No network call, no suggestion sent to a third party, including while typing.
- **"My position" says that it is looking.** A first fix takes up to ten seconds indoors, and the field itself carries the wait meanwhile: without it the screen comes back from the address search with nothing changed, which reads as a press that was lost. Found or not, the field goes back to what it said.
- A swap button for departure and arrival.

### 7.4 Journey result

- **The two ends stay at the top**, in the fields and with the swap button of §7.3: a point is corrected here, without going back. Any change — a field refilled, the two swapped — asks for the journey again straight away.
- **No title bar on this screen**, unlike the others: the two ends and the journey under them say where one is, and a whole row spent repeating the word "journey" pushed them down for nothing. The way back stays, on the row of the first field.
- **While it is being worked out**, the screen shows the wait rather than an empty result: the bike of the stations crosses from one edge to the other, comes back along a higher line, and goes round again, under a sentence saying what is happening. A device asking for reduced animations gets the drawing still (§7). The two fields stay clear of that wait: correcting a point is precisely what one does while it lasts.
- Drawn on the map as three visually distinct legs: walk, bike, walk.
- The journey's **four points** are marked: the two stations, and the two ends of the journey. Shape carries the meaning as much as colour: a station is a filled disc bearing a bike, an end of the journey an outlined disc bearing a walking figure — the drawing of the search screen's illustration, at marker size.
- The **user's position** is shown and follows the device while the screen is open, **above every other marker**. Nothing is requested when the screen opens: without the permission, no point, and nothing says otherwise (§10).
- A **"locate me" button** at the bottom right of the map brings the framing down onto the walker, as close as the tiles allow. It is the one thing on this screen that asks for the location permission, and only when pressed — which is the moment the user has said what they want it for (§10).
- **A "show the whole journey" button sits just above it**, and undoes what it does: once the map has come down onto the walker, or a pan has gone looking at a junction, this brings the framing back onto the whole track. It was added on 13 August 2026, the screen having until then offered a way out of that framing and none back into it short of asking for the journey again. It applies the framing the screen lays by itself, to the map at the size it then has, and it is the only one of the two moves that is animated: it answers a press, and the travel is what shows the press was heard. It is absent while there is no track to frame — an impossible journey offers nothing to come back to.
- Summary: total time, of which walking and biking, distance, **the climb**, departure station (with its bike count), arrival station (with its dock count). It sits **beside the total time, from its top**, not under it: it is the same sentence either way, and a line of its own pushed the drawing and everything after it further down the screen for nothing. It wraps under itself when it is too long for what the figure leaves, and starting level with the top of the figure rather than on its baseline keeps those two lines beside the figure instead of below it.
- **The climb is named where there is one, and nowhere else.** The routing graph carries the elevation of its nodes (§5), and a bike-share bike is heavy: thirty metres of climb is what separates a ride one takes from a ride one regrets, and the figure was on the device already, unread. It is the metres gained over the whole journey, the two walks included — a hill walked up is climbed as surely as one ridden up — and each step of the detail carries its own, by the same rule. What silences it is the elevation data's own reach: the graph is built from SRTM samples some thirty metres apart, whose vertical error runs to several metres. A stretch shorter than **three hundred metres** — ten samples — is not described by them but by their error, and says nothing: forty metres of pavement announced five metres of climb, a twelve per cent grade on a street that has none. Over that length, anything under **five metres** is not named either, and what is named is written to five metres, never in kilometres: a climb is counted in metres by everyone who rides up one. A higher floor was tried first and dropped: at ten metres — the dip the engine's elevation filter forgives — the bike leg of a flat conurbation's journeys fell silent while the total, summing three legs, still named a climb. The same silence covers a city whose graph was generated without elevation, which would otherwise read a row of zeroes.
- **The summary is the way to the journey in full.** The block holding the total time, the summary and the drawing answers to a press, and opens the screen of §7.4.1. Until 13 August 2026 the steps were folded into this screen behind a "details" button set under the drawing; on 13 August 2026 they moved to a screen of their own, where the map does not take the room they need. The button went with them: it was a row spent saying in words what the block above it can say by answering to a press, on a screen where every row is taken from the map. A chevron at the end of the summary marks the way on, and goes when there is no journey to open — nothing offers a screen that would be empty.
- **The detail is laid against the bottom edge**, and the map takes every pixel it leaves: the total time sits where the thumb already is, and a short journey widens the map instead of leaving a band of nothing under the last line. A long detail scrolls rather than eating the map.
- The **shape of the journey** under the summary, in the drawing of the search screen — outlined disc bearing a walking figure at either end, filled disc bearing a bike at each station, dotted stroke for a walk and unbroken for the ride — with **how far each leg runs written above its stroke and how long it takes below**. The two lines are not interchangeable: it is the minutes one leaves the drawing with, so they take the lower side, the one the reading goes on towards, and in full ink — a journey is decided on its minutes — while the distance labels its stroke from above. The steps having moved to §7.4.1, this is the leg-by-leg reading this screen offers — the whole journey seen at once, where the steps read one line at a time. **The two lines are held close against the drawing**: they are its labels, not two rows of figures over and under it, and the gap that let them float away from it was taken back on 13 August 2026 — measured off the discs, it is read against the strokes half a disc further in, which leaves the figures air without letting them drift off the line they measure.
- **No recompute button.** The journey is worked out again whenever the question changes — a point corrected, the two ends swapped — and that is the whole of what one comes back to this screen to do. A button whose only effect is to redo the same computation against a fresher availability was a row of the detail spent on something the screen can decide for itself; leaving to the journey the room the button was taking is worth more than the press.
- **A "navigation" button, last of the reading**, hands one leg of the journey to whichever application guides along it. It stands where the "details" button stood, and it is the only thing this screen offers to do: everything the screen offers to do comes after everything it has to say. The application computes journeys and does not guide along them (§13); the applications that do are already installed, and a `geo:` URI is what they all understand (§7.2).
- **One leg at a time, because a `geo:` URI carries one point.** No standard scheme carries a route with its waypoints — the ones that exist belong to a single application each, and choosing one would tie the button to that application. So the press asks which part of the journey is being set off on — walk to the station, ride to the other, walk to the destination — and hands that leg's end over with its name. It is also how a journey is lived: one leg at a time, the question asked again at each station. A journey made on foot from end to end has one leg, and asks nothing.
- **This application is taken out of that choice.** It answers `geo:` itself (§7.8), so on a phone where it is the only one to, or the one kept as the default, handing a leg over reopened Roue Libre and started the journey again — a press that looked like it had done nothing. What is offered is the applications that guide, through Android's chooser and never a pick made for the user; where there is none, the screen says so. Seeing them requires declaring that intent in the manifest, which grants sight of the applications answering it and of nothing else, and nothing about them ever leaves the device (§2, C3).

### 7.4.1 The journey in detail

A screen of its own, opened from the summary of §7.4 and holding everything that does not fit beside a map.

- **The same total and the same summary as the screen it comes from**, word for word, from the same resources and laid out the same way — the summary beside the figure it explains and level with its top (§7.4). A total set out differently would read as a different journey. The drawing follows, since it is what the press was made on.
- Then the journey **leg by leg, in the order it is lived**, with the two stations standing between the legs that reach them: each walk and the ride with its distance, its minutes and its climb. It is one reading, from where one stands to where one is going, not a table of legs on one side and stations on the other.
- **The two ends have no row of their own.** They were given one, named "start" and "destination", and it repeated what the leg reaching it already said — "walk to the destination" — under two fields that say it again at the top of the screen one arrives from. What a row must add is what is not already on the screen; these added a label.
- A station is given **by name, by street and by count**: the street read off the offline address index (§4.3), as the station's sheet reads it (§7.2) — the availability feed publishes none — and the counts the journey was decided on, with the station's capacity to put them on a scale. Those counts are **said to be the ones the journey was worked out on**: a station empties while one walks to it (§6), and a figure read five minutes ago must not pass for a promise.
- **The ground the ride runs over, drawn**, last of the screen and only where the graph has something to say. A total of metres says how much there is to climb and never where it stands, and a hundred metres taken in one wall at the end of the ride is not the same ride as a hundred spread over ten kilometres. Only the bike leg is drawn: the walks are lived at a pace no hill changes. The vertical scale is the leg's own, stretched between its lowest and its highest reading, and both are written at the ends of the drawing — an amplified bump must not read as a mountain. What silences the drawing is what silences the climb figure (§7.4): under three hundred metres of ground, or inside five metres of height, the readings are the error of the SRTM samples rather than the ground. For the same reason the curve is **smoothed over a hundred and fifty metres** before being drawn: raw, a ride across flat country is a saw of a metre up and a metre down every fifty, none of it real, while a rise of three hundred metres comes through the smoothing whole.
- **Nothing else**: no map, no availability refreshed behind the reader's back, no action but the way back. The map, the correction of the two ends and the handover to a navigation application all live one screen back, where one returns by the way one came.
- The journey reaches this screen **in memory**, never through a saved argument: it carries its tracks point by point, and §8 wants it kept nowhere. A process killed while this screen is open therefore comes back with nothing to show, and it steps back to the result screen — the only place that can work the journey out again.

### 7.5 Favourites

A list of the stations marked as favourites, with their live availability. Reorderable.

### 7.6 Settings

City served, offline data management, light/dark/system theme, and the way to the "about" screen. **No source address is typed here**: the feeds and the manifest come from the city configuration (§4.1, §4.4), and data that does not come down from them is brought in by importing a file from the "storage" screen.

### 7.7 About

Attributions (§4.3), version, link to the repository, privacy policy in plain words.

### 7.8 Opening from another application

The application must appear in Android's chooser when a place is opened or shared from another application, and must be selectable as the default choice. The received place becomes directly the **destination** of a new journey.

**Entry points to declare:**

- `ACTION_VIEW` on the **`geo:`** scheme, with the `DEFAULT` and `BROWSABLE` categories. Every form must be accepted: `geo:<lat>,<lon>`, with a zoom parameter, `geo:0,0?q=<lat>,<lon>(<label>)`, and `geo:0,0?q=<address as text>` — the last resolved by the local index (§4.3), **with no network call**.
- `ACTION_VIEW` on the **`google.navigation:`** scheme, still emitted by many applications.
- **`ACTION_SEND`** of plain text: detect a coordinate pair or an address inside the shared text. It is the commonest case in practice — an address received over a messaging application.
- Web map links: to be handled, but knowing they **cannot be verified automatically**, since the domains involved do not belong to the project. Since Android 12 they only reach the application if the user allows it in the system settings. Document that step in the "about" screen and in `docs/sharing-links.md`, failing which the behaviour will look like a defect.

**Expected behaviour:**

- Open the journey result screen directly, destination pre-filled, departure at the current position. If location is denied or unavailable, open the search screen with only the destination filled in.
- A received text is **finished text**, and is searched for as such: its words are looked for whole, never as words begun (§4.3). The search box may read "Gambetta" into "gamb", because it shows a list its user chooses from; here the first result becomes a journey without anyone choosing it, and a fragment allowed to stand for a longer word would turn a sentence naming no address — "on se voit demain" — into a destination. The address found is otherwise resolved and written exactly as the search box resolves and writes it: one index, one answer.
- A text in which nothing is found produces **no destination**, says so, and offers the address search rather than falling silent.
- Show the received label if there is one, rather than raw coordinates.
- If the point is **outside the bounding box** (§4), say so clearly and offer to show it on the map anyway if the data allows, without attempting a route computation.
- If the datasets are not installed yet, explain it and offer the download, rather than failing.
- An incoming intent **never** triggers a network request other than the normal availability refresh.
- The application must not install itself as its own default handler: the choice belongs to the user, through Android's chooser.

No extra permission is needed for any of this.

### 7.9 First launch

On the very first start, a welcome screen — **not a dialog**: the content is too dense for a modal window, and it must be readable again from "about" — introduces the application in a few short sentences:

- a **free and open** application, no account, no advertising, no tracker;
- **no personal data leaves the device**: journeys are computed on the phone, searched destinations are sent to nobody, no history is kept;
- **offline operation**: the map, the streets, the points of interest and the route computation live on the device, which implies an **initial download**; only real-time bike availability needs a connection afterwards.

That screen leads straight into the download confirmation described in §4.4, with the size announced — **a single sequence, not two successive walls of text**. Three screens at most, each skippable, and a button to postpone the download.

Each page carries **one drawing**, under its text and above its buttons: the journey walk-bike-walk laid on the map it happens on, the phone whose boundary a single dashed thread crosses, the city's tiles becoming a map once they are downloaded. They are vector drawables built from the palette and map tokens of §7 — the same discs, the same dashes, the same ground colours as the real map — so the pages show the application rather than illustrate it. They are decorative for accessibility purposes: each repeats what its paragraph says, and a screen reader must not read it twice. The drawing takes the height the text leaves and disappears at the largest font sizes rather than pushing a sentence below the fold.

The tone is that of §7: short sentences, active voice, no jargon. We explain how something works, we are not selling anything.

The map lies under this sequence, already in place, and its own request for the location permission therefore reaches the screen over the first page — see §10, where that is stated and accepted.

### 7.10 What's new after an update

After a new version is installed, a **what's new** screen appears **once only**, listing fixes and improvements since the previously installed version.

- The application remembers the last version code seen. If the gap spans several versions, present the notes of **all** the intermediate versions, from newest to oldest.
- **Never shown on a first installation**: the §7.9 screen applies then.
- Always reachable afterwards from "about".
- The notes are **embedded in the APK**, never downloaded: no network request may be triggered by that screen.
- **Single source of truth**: the release notes of the F-Droid metadata (`fastlane/metadata/android/fr/changelogs/<versionCode>.txt`). They are converted into an embedded resource **at build time**, so that F-Droid and the application show exactly the same text without double entry.
- Write those notes **for the user, not for the developer**: "address search now tolerates typos", not "refactored the geocoding module". Translatable like everything else.

## 8. Storage and data model

- **Room**: a station table (static data) + a table of the last known state.
- **DataStore**: settings and favourites (station identifiers).
- **No journey data is kept**: no history, no positions, no destinations. Computed journeys live in memory for the duration of the session.
- No automatic cloud backup: `android:allowBackup="false"`.

## 9. Internationalisation

- **Not a single hard-coded string** in the Kotlin code or the layouts. Everything in `res/values/strings.xml`, which constitutes the **default language: English** — what Android serves when no translation matches the device, and therefore what most of the world reads. The application is not addressed to one country: it serves whatever conurbation publishes its stations as open data (§1), and the language of its interface must not say otherwise.
- **French is a translation like any other**, in `res/values-fr/`. It was the source language while the project was a Lille one, and the interface's tone was set in it; that history shows in the writing, not in the resource that has no qualifier.
- **Dates, numbers and distances follow the language actually displayed**, which is not always the system's: a device set to a language the application does not speak reads English text, and must read English figures with it. That correspondence is held in one place (`ui/Locales.kt`), where every new translation is declared.
- Use `<plurals>` for everything that agrees ("1 bike available" / "3 bikes available").
- Use **positional placeholders** (`%1$s`, `%2$d`) and never string concatenation: word order changes from one language to another.
- Add `<!-- -->` comments above ambiguous strings, for future translators.
- Provide a **started file for the most widely spoken languages, and for every language spoken where a network is served**, holding the English text until somebody translates it, so that contributing means editing a file rather than creating one. The catalogue is what settles the list: arriving in Ljubljana or Pristina with the application must mean arriving in a language somebody can finish, not in a folder somebody must create. Every language supplied must be declared in `localeFilters`, without which its folder is dropped from the APK.
- Format dates, times, distances and durations through the localisation APIs, not by hand.
- Layouts compatible with right-to-left languages (`start`/`end` rather than `left`/`right`).
- Provide a `CONTRIBUTING.md` explaining how to submit a translation.
- The **F-Droid metadata** follows the same rule: `en-US` is the default the store falls back on, other languages sit beside it. The application's "what's new" screen reads those very files (§7.10), and shows the notes of the language it is speaking.

## 10. Permissions

Permissions requested, and **no others**:

- `INTERNET` — fetching the GBFS feeds and the datasets
- `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` — requested **when the map opens**, once per session and never again once refused, and on a press of "locate me" (§7.1). Those two moments and no other: no screen that never shows a position may ask for one
- `ACCESS_NETWORK_STATE` — offline detection

**The very first launch is the exception, and it is accepted.** The map is the application's content: it is put in place as the application starts, and the welcome sequence of §7.9 is laid over it. The map has therefore opened, and it asks — so on a fresh install the system dialog appears over the first page of the welcome, before that page has been read. Two ways out were weighed on 13 August 2026 and both cost more than they save. Holding the map back until the sequence ends builds the main screen after the sequence rather than behind it, so the application arrives at its map slower at the very moment it is being judged. Moving the request to the end of the sequence asks on the same run anyway, one screen later, and buys nothing but the order. What this section actually guards is untouched: the request belongs to the map, which is where one lands the moment the sequence is over; it is made once; a refusal is never repeated; and no screen asks for a permission it makes no use of. The cost accepted is a dialog arriving before its explanation, on one launch in the life of an installation.

The application must be **fully usable if location permission is denied**: the user then designates their departure and arrival points by hand. A refusal must never block a screen nor trigger an insistent prompt.

Geolocation uses the Android system's location provider, **not** Google's fused location services.

## 11. Acceptance criteria

Every criterion must be verifiable:

1. The application installs and works on a device without Google services.
2. Stations appear on the map with availability consistent with the official site.
3. **In airplane mode**, once the data is downloaded: the map displays, address search works, a complete journey computes. Only availability is frozen at the last known state, explicitly marked as stale. No blocking error.
4. A journey between two points of the served area returns a walk → bike → walk trip — including between two opposite ends of it, where the bike leg alone runs to some thirty kilometres.
5. The proposed departure station always has at least 1 bike; the arrival station at least 1 free dock.
6. When no nearby station has a bike, the application says so explicitly instead of proposing an impossible journey.
7. In ordinary use, **the only network request that goes out is the GBFS feed** — to be verified with a firewall or a traffic capture. Data downloads happen only on first launch or on an explicit user action.
8. Exodus Privacy analysis detects no tracker.
9. The release APK weighs under 15 MB, and under 12 MB per architecture. The downloaded data follows the size and density of the network served, and a capital legitimately weighs more than a medium-sized city: there is **no fixed limit**: the 300 MB of §4.2 is the figure a conurbation is designed for, which a regional network legitimately exceeds. Below it, what is required is that the application **announce the size before downloading** and that it allow **deleting one city's data** to reclaim the space. Measured over the seventy French networks on 12 August 2026: median 10.9 MB for the three sets together, 44.1 MB for Lille, 42.3 MB for Lyon, 143.0 MB for Paris — of which 114.9 MB of base map, the highest of the seventy.
10. An address with a house number in a long thoroughfare is located within 50 m of its real position.
11. A search containing a typo or a missing letter finds the intended street in the first three results.
12. A `geo:` link opened from another application offers this application in the chooser and pre-fills the destination.
13. Switching the system between English and French breaks no layout, and a language with no translation of its own falls back on English text with English figures.
14. Every attribution is present.
15. The build is reproducible: two successive compilations produce the same APK.

## 12. Deliverables

- A Git repository with a clean, atomic commit history
- `README.md`: **entirely in English**, like the interface it describes. It is the front page and stays short — what the application is, what it does, how to build it, how to contribute — and it explains the name, which is a French phrase and does not travel by itself. The detail lives in `docs/`, one file per subject, so that the front page stays readable: `architecture.md` (layers and data flow), `offline-data.md` (dataset generation, sizes obtained, procedure for adding a city per §15, sources and attributions), `dependencies.md` (justification of every one), `sharing-links.md` (places opened from another application), `networks.md` (generated, never written by hand).
- `CONTRIBUTING.md` including the translation procedure
- A licence file
- F-Droid metadata (`fastlane/metadata/android/fr/`): short description, long description, release notes, screenshots
- Unit tests on the §6 algorithm and on GBFS feed parsing
- A signed release APK
- `CHANGELOG.md`

## 13. Out of scope for v1

Not to be implemented, even if the opportunity arises: notifications, home-screen widget, journey history, user accounts, bike booking, public transport integration, availability forecasting, statistics, social sharing, guided navigation mode with voice instructions.

**Handing a place over to an application that does guide is not guiding**: a station (§7.2) or a leg of a journey (§7.4) leaves through a `geo:` URI, and what happens next belongs to the application the user chose. What is excluded here is guiding *in* this application — the voice, the recentring, the screen kept awake.

## 14. Code quality and maintainability

This project is meant to live a long time, to be taken over by contributors and to be audited by F-Droid reviewers. Readability beats cleverness.

- **The code and everything written around it are in English**: identifiers, comments, KDoc, documentation and commit messages — as is the interface and the F-Droid metadata that goes with it (§9). Other languages arrive as translations, never as the source.
- **Explicit naming**, without obscure abbreviations. A long clear name beats a short one you have to decipher.
- **Short functions**, single responsibility. If a function needs a comment to explain what it does, it usually needs splitting.
- **Comments: explain the *why*, not the *what*.** A comment that paraphrases the code is noise that will go stale. Do document systematically, however: non-obvious choices, accepted trade-offs, workarounds for library limitations, and business formulas — in particular all of §6, where every coefficient must be justified.
- **KDoc** on every public class and function: role, parameters, return value, error cases.
- **Strict layer separation.** Business logic (§6, feed parsing, address resolution) must be in pure Kotlin, with no Android import, hence testable on the JVM without an emulator.
- **No dead code, no anticipated feature.** Do not build an abstraction "just in case": the only generalisations asked for are those of §15.
- **Explicit error handling**: result types rather than silent exceptions, and for every failure a user-facing message saying what to do, not a technical code.
- **Unit tests are mandatory** on the §6 algorithm, GBFS parsing, address resolution and house-number interpolation. Every bug fix comes with the test that would have caught it.
- **Automatic formatting** (ktlint or equivalent) and **static analysis** (Android Lint, detekt) wired into the build, with no warning tolerated in release.
- **Atomic commits**, explicit messages describing intent.

## 15. Portability to another conurbation

The application must be able to serve another city **without a code change**. This is a design requirement, not an intention.

- **No data specific to a city hard-coded** in the code: no URL, no bounding box, no centring coordinates, no network name. All of it lives in a **city configuration file**, single and documented. It carries the country as well (ISO 3166-1 alpha-2), which the catalogue groups on and the generation scripts read the address base of.
- That file describes: network name, `gbfs.json` URL, bounding box, default centre and zoom, URLs of the datasets to download, default language, and whether the fleet holds pedal-assist bikes.
- The **fleet** is read from the network's own `vehicle_types` feed by `tools/read_fleet.py`, never typed in: a bicycle whose `propulsion_type` is electric is a pedal-assist bike, and the interface then marks that city's bike glyphs with a bolt (§7). A network whose feed declares no vehicle type says nothing rather than something unverified, and the plain bike is drawn. The application itself never calls that feed: this is a fact settled when the city is added, not real-time state.
- Since GBFS is an international standard, most of the portability is won as soon as the URL is configurable (§4.1).
- The **generation scripts** for the data (tiles, routing graph, address index) take the bounding box as a parameter. Producing another city's data must be a single command.
- The vocabulary of the code and of the interface stays **generic**: "station", "bike", "network". A network's name lives in its configuration alone — never in a class name, a variable or a string resource.
- Document in `docs/offline-data.md` the complete procedure for deploying the application on a new city.
- The Base Adresse Nationale is French, and it was long the caveat here. It no longer is: the index script reads the addresses of any other country from its OpenStreetMap extract, behind the same interface, and the configuration says which source applies (§4.3).

### 15.1 Several cities in the same application

Serving one city without recompiling is not enough: a single application must be able to serve **several networks**, one after another, and download nothing of those it does not use.

- There is **one city configuration per network served**, and a **catalogue** indexing them. The catalogue is derived from the configurations, never written by hand: it carries, for each city, its bounding box, its centre, its station count and **the weight of its data**.
- The catalogue is **downloadable**, so that a new city appears without publishing a release. A copy ships in the APK as a fallback: a first launch without a network must show a list, not an empty screen.
- The application **assumes no default city**. On first launch it **proposes one from the user's position**, on a button press and never by itself (§10); beyond fifty kilometres or so from the nearest network, it proposes nothing rather than anything.
- Afterwards, and once a city is in service, the application **offers the network of the conurbation it finds itself in** when that is not the one being served: someone who travels arrives with another city's data installed, on a blank map that says nothing about the network under their feet. Strictly bounded: it reads only **a position the system already holds** — no permission requested, no fix asked for, nothing at all if location is denied or off (§10) — it compares it against the catalogue **already on the device**, so no request goes out, it **offers** and never acts, and a refusal is not repeated for the rest of the session. Nothing about the cities one passes through is written down (§2, C3).
- The catalogue is **searchable by name**, network or conurbation: past a few dozen entries a list is scrolled rather than read.
- Datasets are **stored per city**. Two cities therefore coexist on the device without mixing, and one city's data can be deleted without touching the other (§11.9).
- The street-name normalisation rules (§4.3) are language-specific, one file per language, and they travel with a city's data rather than being frozen into the application: the address index records which language it was built with, and the application applies that one. All the rule files ship, since a user installs a second city without updating the application.

## 16. Working method expected of the agent

1. Start by **verifying the real GBFS feed URL** and the exact structure of the data received before writing the data models. Hard-code nothing that has not been observed.
2. Deliver in verifiable stages, in this order: (1) fetching and displaying GBFS data as a list, (2) **offline data generation scripts** — tiles, routing graph, address index — with the real sizes obtained, to be compared against the announced budgets, (3) vector map and markers, (4) offline routing engine, (5) optimised journey algorithm, (6) local address search, (7) remaining screens, (8) finishing touches and F-Droid metadata.
   Stage (2) comes early on purpose: it is what validates or invalidates the whole offline bet. If the real sizes depart appreciably from the budgets, we need to know before the interface has been built on top.
3. Never add a dependency without justifying it in `docs/dependencies.md`.
4. Report immediately any point where a §2 constraint would prevent a feature, rather than working around the constraint.
