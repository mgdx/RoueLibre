# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
the project follows [semantic versioning](https://semver.org/).

The notes meant for users live in `fastlane/metadata/android/fr/changelogs/` and
are written for them, not for developers. This file addresses contributors and
also records what has no visible effect.

## [Unreleased]

### Fixed

- **The publisher decides what to send on the digest, and no manifest goes out
  ahead of the files it names** (`tools/publish_data.py`, SPEC §4.4). An asset
  already online was left alone when it weighed what the local file weighed,
  while a manifest was rewritten whenever that file was newer than it. The two
  rules parted company on 23 August 2026: keeping the house-number mark as its
  own country writes it changed the content of 245 address indexes and the size
  of not one of them, so none were sent, and the index release went out
  carrying the manifests that described the new files. 253 of the 337
  conurbations — Limoges and Konya among them — then downloaded an address
  index, and 72 of them a routing graph, whose digest the application
  recomputed, refused and deleted, which is exactly what it must do: the fault
  was never on the phone. What the publisher compares is now the SHA-256 GitHub
  reports for each asset, which is the very question the phone asks;
  `--network` no longer stamps the manifests of the cities it does not name;
  and `check_published` refuses, between the country releases and the index, to
  publish a manifest whose files are not online under the announced digests —
  the last moment anything can be stopped, since that release is deleted and
  re-created rather than updated. `verify` compares every published manifest to
  what is online instead of reading the single smallest city whole, which is
  the blind spot that let the run report success. The 317 files were published
  again on 31 August 2026; no application release is involved, the manifest
  being re-read at every attempt and a rejected file leaving the installation
  it failed to replace untouched.

## [1.2.4]

Six conurbations added, and the two ways the catalogue and the application can
fall out of step over them.

### Added

- **Six cities: Wrocław, Bogotá, Offenburg, Sibiu, Buzău and Slobozia.** They
  were rejected by `tools/discover_networks.py` over a form factor GBFS has no
  name for. The standard describes a handbike, a tricycle and a bike trailer as
  `other`, and `other` was read here as a motor vehicle: Wrocław's WRM and
  Sibiu's BikeCity were turned away over a handbike, Bogotá's over a
  MANOCLETA, Ortenaukreis's over a `Radanhänger`. A form nobody named is now
  judged on its propulsion — `human` and `electric_assist` mean the rider turns
  the pedals — while `car` and `moped` disqualify as before. The survey also
  records `motorVehicleTypes`, the names of the vehicles read as motorised, so
  a rejection can be checked against the feed instead of being taken on trust.
  Bogotá is the catalogue's first city in South America.
- **A city the catalogue names and this build cannot serve is shown, dimmed and
  refused** (SPEC §15.1). The catalogue is refreshed over the network and each
  city's configuration ships in the APK, so a catalogue published after a
  release names cities that release has nothing for; choosing one used to leave
  `activeCity()` with no configuration and the screen with nothing to say. The
  row now carries one sentence — "Not available in this version of the
  application" — answers no tap, and loses its touch feedback, a ripple leading
  nowhere reading as a screen that failed to open. "Find my city" says the same
  rather than proposing a city it cannot install. An asset directory that
  cannot be listed refuses nobody: that failure must not become an application
  turning away every one of its own cities.

### Changed

- **The catalogue is published before the application release that serves its
  new cities — except for this one** (SPEC §15.1). Dimming is what makes a
  catalogue ahead of the application harmless, and 1.2.3 does not have it: it
  would list the six, let one be chosen, find no configuration for it and read
  as though no city had been chosen at all. So this release goes out before its
  catalogue, and the ordinary order resumes afterwards. The six cities' heavy
  files were published on 31 August 2026 ahead of both: nothing names them
  until the catalogue does. The downloaded catalogue replaces the shipped one,
  so a catalogue lagging behind an APK silently hides cities that APK carries —
  six of them, on a phone that held all six, on 31 August 2026. The replacement
  is kept, since removing a city from the published catalogue is the only lever
  that retires a dead network without an application release; what changes is
  the order of the two publications.

## [1.2.3]

The point that stands for the user on the map, taken seriously. It used to be
a disc redrawn wherever the last fix landed, saying the same thing whether the
fix was a second or an hour old and never saying which way its owner was
facing. It now moves, ages and points. Two fixes beside them keep the following
alive where it used to need the screen rebuilt.

### Added

- **The point glides from fix to fix instead of jumping.** Fixes arrive a
  couple of seconds and a few metres apart, and a disc redrawn where each one
  lands teleports — an eight-metre jump every two seconds reads as a glitch,
  where a point that walks reads as "me". `UserPositionDisplay` animates the
  point from the position drawn to the fix received, over one second of the two
  separating fixes, the circle of uncertainty travelling with it. The
  interpolation is pure Kotlin in `core` and tested on the JVM; the glide runs
  through `ValueAnimator`, so the system's remove-animations setting collapses
  it back to the plain jump. Both maps — the main screen and the journey result
  — draw through it.

- **The point greys once the fixes have deserted it.** A disc still drawn full
  a minute after the last fix asserts a position nobody is measuring any more:
  at a walking pace the device is some eighty metres away by then, often a
  street over. The ink drains to the soft grey at that age — "last seen here"
  rather than "you are here" — and the point is not withdrawn, where the device
  was last seen still being worth showing. The age is measured on the fix's own
  forward-only clock, so a point restored after a rebuild of the view comes back
  already grey when it deserves to.

- **The point says which way it is going, while that is measured.** A cone
  peeks out from under the disc, pointing along the direction of travel the
  satellites deduce from the movement itself. It exists only in motion: a walker
  who stops loses it and the disc goes back to bare — the uncertainty circle's
  rule that a thing nobody measured is not one to draw. The bearing rides on the
  fixes already listened to, so no sensor is added and nothing new is asked of
  the system, and the glide turns it by the shorter way round the compass. A
  stale point loses its cone with its ink.

- **The wait for a first fix is visible on the locate-me button.** Indoors that
  wait can run to ten seconds, and for that long the button was merely disabled
  — a button dead for ten seconds reads as broken, not as searching. It now
  breathes while the fix is waited for, and the screen reader is told the same
  thing in words. Where the system's remove-animations setting is on, the button
  holds the faded state instead of breathing it.

### Fixed

- **The followed point survives location being switched on mid-screen.**
  `positions()` enumerated the enabled providers once and completed when the
  list was empty, so a map opened with location off never showed a point until
  its screen was rebuilt, however quickly the user switched location back on.
  The subscription now covers every provider the device has, enabled or not: a
  disabled provider costs nothing to listen to, and its fixes simply start when
  the user enables it. One-shot requests keep the enabled-only list — a button's
  answer should not wait ten seconds on a radio that is off.

- **The journey result screen starts following after a permission granted away
  from the screen.** It checked the permission once, when its view was built, so
  a permission granted later from the Android settings started nothing on
  return. The map screen already re-checked on resume; the journey screen now
  does the same.

### Technical notes

- **The journey screen's following opens on a permission gate, as the map's
  does.** Calling `repeatOnLifecycle` from `onResume` is what lint rightly
  refuses: one subscription per resume, guarded only by a job reference. The
  screen now follows the map screen's own pattern — a single subscription for
  the life of the view, opened and closed by a `StateFlow` re-reading the
  permission on every resume and set by the button's answer.

## [1.2.2]

A patch answering the F-Droid review of 1.2.1, and a reviewer's count of the
credits the city configurations carry. Four fixes, nothing added: three of them
are about giving each source what its licence asks for, and the fourth about
taking a refusal for an answer.

### Fixed

- **A refused location permission is never asked for unprompted again.** The
  map put the request on every new session, so a "no" pronounced on the city
  screen's find-my-city button was answered by a second dialog as soon as the
  map opened — two dialogs for one refusal, which an F-Droid Permissions review
  reads as insistence. A refusal, wherever it is pronounced, is now written
  down in the settings and outlives the session: only the buttons that need a
  position still put the question. The decision lives in
  `AutomaticLocationRequest`, pure Kotlin and tested on the JVM.

- **Every attribution names the licence its data is published under.** A
  reviewer counted 104 configurations out of 331 crediting an operator for data
  under no licence anybody could read. Re-reading those feeds, 37 do publish
  one — through `license_url` alone, with no `license_id` beside it, which the
  generator dropped on the floor — and 67 name nothing at all. The generator
  now reads a licence out of its address against a table whose every entry was
  read off the document that address serves, never guessed from the address;
  an address the table does not hold still names nothing, a licence guessed
  wrong being worse than one left unnamed. A feed that publishes none is now
  credited as publishing none, which says somebody read it, where a blank said
  only that nobody had looked. The credit is also written the way the
  producer's country writes it, as an address is (`SPEC.md` §15.1).

- **GeoNames is credited among the data sources.** The city catalogue's
  municipality names come from the GeoNames gazetteer, under CC BY 4.0 — a
  licence that requires attribution — and the About screen credited OSM, BAN,
  GBFS, BRouter and MapLibre but not it. The line is added in every locale.

- **The licences screen carries two notices it was missing.** MapLibre Native's
  BSD-2-Clause licence asks for its notice to be reproduced in binary
  distributions, and the Public Suffix List that OkHttp embeds is under
  MPL-2.0; both were only named in a sentence of the About screen. Their exact
  upstream texts now sit in `assets/licences/`, which the screen already reads
  in alphabetical order.

### Technical notes

- **The fdroiddata recipe quoted in `docs/release.md` is the one submitted.**
  It had drifted a version behind, showing the 1.2.0 entries while the merge
  request carried 1.2.1; the quoted block is now verified byte for byte against
  the file at the merge request's head commit.

## [1.2.1]

A patch answering the F-Droid review of 1.2.0: one fix in the application,
the rest in what the stores read. Nothing a working installation notices —
this version exists for the phones 1.2.0 refused.

### Fixed

- **MapLibre ships as its OpenGL ES artefact** (`android-sdk-opengl`), not the
  default `android-sdk`. Since MapLibre 13 the default renders through Vulkan
  only and its manifest marks Vulkan 1.0 as a required feature — merged into
  the APK, it refused installation on API 26+ devices without Vulkan, against
  the promise of minSdk 26. The OpenGL ES artefact is the same engine at the
  same version without that requirement, and its native library is about two
  megabytes lighter per ABI.

### Technical notes

- **The release notes also carry the codes F-Droid serves.** F-Droid publishes
  one APK per architecture under its own version code — 71 to 74 for base 7 —
  and reads the notes under that exact code, falling back on nothing. The
  hand-written base file is now expanded into per-architecture copies by
  `tools/expand_changelogs.py`, and the copies for every published version
  were backfilled.

## [1.2.0]

A version whose centre of gravity is the routing: the bike profiles now read
the cycling provision a road carries, hold the speed a share bike actually
holds, and the planner's own arithmetic loses three defects found by reading
it. The rest closes a second QA campaign run on the device — what an
application does when a file is wrong, a connection dies, a phone turns over
or is held sideways, and a text arrives with a sentence around its address.

### Added

- **The bike profiles read the provision a road carries** (§6). A road with a
  protected track, a shared bus lane, a painted lane or a pictogram was priced
  as the bare road, and the router detoured around boulevards whose provision
  is real. Each discount is a remission on the road's own class ladder — a
  track rides like a calm street (×0.40), a bus corridor drops the road two
  classes (×0.50), a lane one (×0.65), a pictogram under half (×0.85) — and
  nothing beats the calm street's 1.2: provision corrects a road, it does not
  outdo the absence of traffic. The side the tag serves is read too, and which
  side is which belongs to the country: the profiles carry a `leftHandTraffic`
  parameter, injected through the engine's `keyValues` — folded into its
  profile-cache key — from the country the city configuration already names,
  resolved by a `DrivingSide` table in `:core`. Measured over twelve legs of
  the Lille graph, reading the tags returns 136 s over the set and moves 3.8
  more points of the linear onto equipped ways. **No dataset changes**: the
  published graphs carry the tags already, verified on Lille and Canterbury.
- **A text shared with a sentence around its address resolves** (§4.3, §7.8).
  "Rendez-vous ici : 12 rue Nationale, Lille" resolved to nothing, every word
  of a query having to match. Where the finished text finds nothing it is now
  read a second time as a sentence: a word the index knows nothing about stops
  being fatal but still weighs in the score, so the street answering the most
  words of the query comes first and a landmark answering the one word
  "lille" no longer beats the street the text names. What remains must still
  name a street by a word of its proper name written in full — "coucou" still
  answers nothing. That reading never chooses: at most five candidates are put
  up as a list, and each row carries what tells it apart — municipality,
  postcode, distance — through the `incoming_address_choice` resource, in the
  thirty-one files, because five rows reading "12 Rue Nationale" are a draw,
  not a choice. The message that says nothing was found now carries the
  received text to the address search field.

### Changed

- **Both bike profiles stop at 25 km/h** — the figure the assistance law names
  and the one a city rider brakes at. Left alone the engine coasted any bike
  downhill to its default 45 km/h, which nobody reaches on twenty kilos of
  upright share bike, and the minutes announced came out 2 to 3 % too few
  even across flat Lille. The cap enlarges what the profile accounts for, so
  the assistance factor gives back exactly that much: ×0.95 becomes ×0.92,
  measured to land the whole at the observed ×0.80. The ceiling also
  underwrites the pruning bound, which rides a straight line at 25.2 km/h.
- **Three corrections to the planner's arithmetic**, none touching what a
  journey is: the pruning bound rides the bike the journey is asked for — a
  bound left at the mechanical pace could overtake the quickest assisted ride
  and discard the optimum unseen; the direct walk prunes pairs before their
  legs are computed rather than after; and when the pairs come up empty on a
  short trip, the walk already in hand is the one offered instead of being
  traced a second time.

### Fixed

- **A refused import names what the file really is.** What a file is is
  decided from its first bytes, in pure Kotlin, before anything opens it, and
  the two cases the user can act on are told apart: a file that is none of the
  three datasets, and a dataset offered on the wrong line. A SQLite file is
  named by its tables; the routing graph stops accepting anything that merely
  is not SQLite — a screenshot offered as the graph was accepted and
  installed. A refused import also stops leaving its staging directory behind.
- **A connection lost mid-transfer is not an unreadable file.** A socket dying
  halfway through a body fell into the same catch as a response whose shape
  makes no sense, so turning the Wi-Fi off two seconds into a 44 MB download
  answered "Unreadable file received". Only the read of the socket answers
  Offline; a digest that does not match keeps `MalformedResponse`. The
  manifest read follows the same rule, so "check for updates" on a dying
  connection stops blaming the host too.
- **A question survives the phone turning over.** All nine dialogs were built
  where they were asked and belonged to no fragment manager, so a rotation
  wiped "Delete this data?" with neither a deletion nor a cancellation to show
  for it. `ConfirmationDialogFragment` takes the seven questions with two
  answers, `ChoiceDialogFragment` the two that offer a list; the answer comes
  back as a fragment result, and what a caller needs in order to act on it
  travels in a payload rather than being read again from a list refreshed
  meanwhile.
- **The offline banner lifts the controls it used to cover.** It stood exactly
  over the map's "station list" button and the list's "nearest station
  first". The activity now reports the room the banner takes, counted above
  the system bars; the map raises the attribution its bottom cluster is
  constrained to, the list raises the button and the room kept under its last
  row.
- **Offline says so differently when nothing was ever received.** "The last
  known availability stays on screen" was said over a map that had never
  shown one. `DataError.toUserMessage` takes `hasKnownAvailability`, read from
  the very value the freshness pill is written from, and a test holds the two
  sentences apart in every started file.
- **The map stops repeating, on a banner, what its own panel says.** With no
  city chosen the map raised the panel that asks for one and then laid a
  banner under it saying the same thing with an inert "Try again" on it. The
  station list, which has no panel to lean on, carries the chooser on its
  banner instead of "Try again"; and with no conurbation in service its empty
  state now asks "Which city?" with the button that opens the chooser, rather
  than inviting a pull that fetches nothing.
- **Held sideways, the screens read once give their height back.** The welcome
  page showed two lines and a half of the paragraph everybody reads once; a
  `layout-land` arrangement has the paragraph and the drawing share the width,
  and the two buttons share the bottom row. The journey result gave the track
  a band of 109 px; its landscape arrangement puts the map and the detail side
  by side, half the width each, and the detail moves to
  `view_journey_result_detail.xml`, included by both orientations so the two
  cannot drift apart.
- **The four search fields carry `flagNoExtractUi`**: in landscape the soft
  keyboard took the whole window for its own copy of the field, and results
  updated at every keystroke were invisible until it was folded away.
- **A run of facts is a name, not a paragraph.** The journey detail and the
  result screen's summary opt out of the theme's justification through
  `Widget.RoueLibre.Name`, so "Ride to Theatre Sebastopol" wraps instead of
  spreading its first line bank to bank; `JourneyDetailNamesTest` holds every
  text view that can wrap on those layouts to the style.
- **A figure and its unit stay on one line.** The space between a number and
  the symbol naming it becomes a non-breaking one in the twenty-six translated
  folders — lengths, durations, file sizes, bikes, docking points, stations
  and the age of a reading — and `UnitTypographyTest` holds every language to
  it. The rest of each sentence keeps ordinary spaces, which is what leaves
  the line somewhere to break.
- **The labelled map controls trade their fixed height for a floor.** The mode
  toggle capped its height at the 48 dp of a touch target while its label is
  written in sp, so the largest text sizes cut the letters on the button's own
  outline; the figure moves to `minHeight` on the four labelled buttons.
- **The map speaks the application's languages.** MapLibre sets its own
  `contentDescription` inside the MapView constructor, after the layout's
  attributes are read, so under an Arabic interface the map spoke English.
  `DescribedMapView` sets ours once the superclass is built.
- **No layout is asked for in the middle of one.** The room the banner takes
  is read from its own layout pass, and the margins applied from it now wait
  for the pass to end — Android was throwing the pass away and running it
  again.

### Technical notes

- `Window.statusBarColor` and `Window.navigationBarColor`, deprecated from API
  35 and ignored under this target, are gone from the opening — the view
  already paints the green they painted. They survive under a single version
  test for Android 8 to 14, where the window still lays its own opaque bands
  over that place, and go the day minSdk reaches 35.
- The glossaries live in `docs/glossary/`, one file per language code, with an
  index naming the register each language settled; CONTRIBUTING points at it
  from its register rule.
- The announced climb's definition — BRouter's filtered ascend, descents left
  out, a ten-metre buffer against the elevation samples' own error — is
  written down where the figure enters the model. A tester could not make
  "10 m of climb" agree with a profile drawn between 20 m and 31 m; they do
  agree, and now the code says why.
- The F-Droid recipe quoted in `docs/release.md` is the one submitted for
  1.1.0, codes 51 to 54 and the hash read off the tag.

## [1.1.0]

A version made of what the application was found doing on a real phone rather
than of what it was meant to do. Most of it comes from a test campaign whose
anomalies are named in the commits, and from the F-Droid review of 1.0.0. The
station list is what changes most: it is now the screen that answers "where is
the nearest bike?" without being asked twice.

### Added

- **The station list orders itself by proximity on arrival** (§7.6), wherever
  one lands on it from, and each row says how far its station is instead of its
  postcode. It does so in two steps: what the system already holds comes first
  and costs nothing — arriving from the map, that is the very point the map was
  drawing, so the list is in order before the eye has settled on it, measured at
  one second on a Fairphone 3 — then a fix is asked for, because on a phone
  where nothing has asked for a position in a while the first step answers
  nothing at all. A fix that comes to nothing leaves the first step's order
  standing rather than falling back to the alphabet. **Nothing is asked of the
  user for any of it**: no permission prompt and no message, and where the
  permission is missing, location is off or the position falls outside the city
  served, the alphabet simply stays.

- **A "nearest station first" button**, floating over the bottom corner of the
  list where the map keeps its own controls and where the thumb is, in the
  signal green rather than the map's pale disc — a pale disc on a white list
  would be a white circle. The list leaves seventy-two dip under its last row so
  the last station of the network is never hidden under it. It is the one place
  this screen ever asks for the location permission (§10), it shows the wait
  with a ring on the button itself for exactly as long as the wait lasts, and it
  answers a refusal, a position outside the conurbation and a failed fix each in
  one line.

- **How long a closed station has been silent** (§4.1, §7.2). The GBFS
  `last_reported` field crossed the whole application and no screen ever looked
  at it: what was shown was the moment the feed was downloaded, so a station
  could read "updated just now" with a five-month-old measurement underneath. A
  station out of service now reads "Out of service · last reported 5 months
  ago". The silence qualifies a closure and never decides one — the service
  state stays settled by `is_installed`, `is_renting` and `is_returning` alone.
  The threshold is a day and not the five minutes that mark a feed frozen, GBFS
  obliging no producer to restamp a station whose count has not moved. `Days`
  and `Months` take the place of `Freshness.LongAgo`, with their plurals in the
  thirty translations.

- **The settings name the network in service** (§7.6) — network and main city
  both — where the city section spent its only line saying what pressing it
  does. Before a city is chosen the row invites the choice instead, in the words
  the welcome sequence uses.

### Fixed

- **The application no longer lands on a map it has no tiles for.** Somebody who
  had deleted their offline data met the full-screen "tiles are missing" panel at
  every launch, on a setting nobody had chosen. The station list stands in until
  there is a map to draw, needing nothing installed but the availability feed,
  and the stored choice is left untouched.
- **The first launch no longer dies on its first screen.** The station list is
  what a fresh install lands on, it asks for the stations of a city nobody has
  chosen and is covered a frame later by the welcome sequence — but its snackbar
  stayed up over that sequence, hid the "continue" button, and pressing it asked
  a fragment with no manager left for a transaction. That one message is now
  held back while the welcome is still due, and every other message is dismissed
  with the view that raised it.
- **A list handed back by a search is shown from its first row**, as the button's
  is: typing a letter showed the matches from the row the reader happened to be
  anchored on, and clearing the field brought the whole list back still shown
  from it. The scroll is armed by the two gestures that hand back another set of
  stations and by neither of the two that hand back the same one.
- **The second press of "nearest station first" comes back to the top too.** The
  same position answers the same order, a `StateFlow` emits nothing for a state
  equal to the one it holds, and the callback carrying the scroll never ran.
- **A station's sheet owes no distance from outside the city consulted.** A phone
  in Lille reading Dubai's network was told "5,236.1 km" under the station's
  name, while the list behind it refused any distance. The model is handed a
  position already filtered by the coverage of the active city.
- **A failed update check stops promising a download it never started.**
  "No connection. The download picks up where it stopped" answered a check that
  transfers nothing; checking now has a register of its own, held apart from the
  download's and the refresh's by a test across the three languages.
- **The answer to a gesture outlives the refresh that failed beside it.** Two
  snackbars replace one another rather than stack, and nothing weighed them: the
  answer to what the user had just done was wiped within a fraction of a second
  by the ten-second refresh loop. The screen's single banner belongs to the
  activity, and a rule in `:core` settles which message gets it — an answer
  outranks the state of a background refresh.
- **The reordering hint on the favourites screen waits for two rows to reorder**,
  instead of standing over the message inviting a first favourite.
- **The back gesture draws where it leads on Android 13 and 14**, the framework
  leaving `enableOnBackInvokedCallback` false there whatever the target is. The
  Fairphone 3 on Android 15 logged the same warning at every launch under this
  target, so the flag is declared rather than inferred.
- **The state banner is a label, so it is no longer justified**: now that a
  closure names its age it wraps, and justified it came out with its separator
  adrift in the middle of a line spread bank to bank.

### Removed

- **`ACCESS_WIFI_STATE`**, which MapLibre's manifest added to the merged one
  while nothing here — nor MapLibre itself, neither its classes nor its four
  native libraries — ever read the Wi-Fi state. §10 says "no others" and the
  list a user reads is the merged manifest, so the permission is taken back out
  with `tools:node="remove"`.

### Technical notes

- The F-Droid recipe carries no `scanignore` any more. It switches the scanner
  off for a whole file rather than accounting for what it found, and for every
  version the entry is copied into; the reviewer of the first submission refused
  it. The two JDK downloads it hid left the repository — the `foojay-resolver`
  plugin and the `toolchainUrl.*` entries of `gradle-daemon-jvm.properties` —
  and BRouter's `publishing` block is cut by a `prebuild` before the scan, so
  what is scanned is what is compiled. `fdroid scanner` runs clean over the
  source.
- `fdroid rewritemeta`'s wrapping depends on the `ruamel.yaml` version and not
  on `fdroidserver`'s: 0.18.12 and below reproduce the formatting their CI
  accepted, 0.18.13 and above fold every long value. Measured, and recorded in
  `docs/release.md`.

## [1.0.0]

The first version published. What it adds over the alphas is written below; what
makes it 1.0 is that it is signed by the project's own key, so that whoever
installs it can be handed the next one. The three versions before it were tagged
and never released.

### Added

- **A walking pace, and journeys worked out for it.** The algorithm of §6
  optimises a **pair** of stations by comparing times, and two of the three legs
  it compares are walked — so a walking speed is not a matter of presentation:
  it decides which pair wins. Until now everybody got a fit walker's journey.
  Somebody who walks slowly is owed a nearer departure station even at the price
  of pedalling further, and that is arithmetic the application cannot guess: it
  is a question of accuracy as much as of accessibility, for an older rider, for
  somebody with a suitcase or a child, for somebody who limps. **Three levels
  named in words** — slow, normal, brisk — in the new "Journey" section of the
  settings, written the moment they are pressed and read again at the next
  journey, so a pace changed applies without a restart. **No slider, no speed
  typed in by hand, and no figure in km/h anywhere in the interface**: one knows
  one walks slowly, which is a fact about oneself and not a measurement, and a
  speed shown on a button would be read as a promise about the minutes
  announced.
  **"Normal" is a factor of exactly one**, at installation, after a reset and
  for any stored value that cannot be read — so a journey comes back identical
  to the one the previous version computed, which is the test this whole change
  is held to. The other two are **factors on the duration of a walking leg**,
  not absolute speeds: what is stable from one version to the next is the ratio
  to the engine's pace, not a value that would have to be chased whenever
  BRouter's model changed. Slow multiplies by 1.40 and brisk by 0.85.
  **The reference pace was measured rather than assumed**, and the profile
  comment that described it was wrong. `urban-walk.brf` claimed the application
  applied a uniform walking speed; no code ever did, and the speed is not
  uniform either — BRouter times a foot profile with Tobler's hiking function
  capped at its default 6 km/h, which our profile does not override, so every
  segment is timed on its own slope. Over six legs of the Lille graph, 314 m to
  10.2 km, it traces **1.39 to 1.44 m/s — 5.0 to 5.2 km/h**, the spread coming
  from the descents where the function reaches its ceiling. That puts slow at
  about 3.6 km/h, the pace pedestrian crossing times are designed around, and
  brisk at about 6 km/h. The comment now says what the code does.
  **The factor is applied in `:core`, in plain Kotlin, before any pair is
  compared** — on the duration alone, the track and its distance untouched,
  since the same streets are walked whether one dawdles or hurries. There is no
  BRouter profile per pace and nothing is recomputed. The ride is never touched;
  the direct walk follows the same pace, without which a slow walker would be
  sent walking more often than they should be; and the minutes shown follow too,
  being the very figures the pairs were weighed on. The optimistic bound that
  decides whether a long direct walk is worth computing at all is scaled with
  the pace, so it stays exactly as optimistic for a slow walker as for a brisk
  one. `SPEC.md` §6 now says why this setting is not the pick-up delay dropped
  on 12 August 2026 for being unmeasurable and for shifting every pair alike:
  this one multiplies rather than adds, it multiplies the walking legs alone,
  and it asks for a fact rather than an estimate. §7.6 says the setting itself,
  and "Journey" is no longer an empty section.

- **Distances in the units the reader's region uses, and a setting to say
  otherwise.** The catalogue serves 332 networks, American and British ones
  among them, and the application showed kilometres in Boston. That was the
  defect a hard-coded address would be — an assumption about a country, written
  into the code — in an application whose interface is English by default
  precisely because it serves whatever city publishes its data, not one country.
  The region is now asked of ICU, which ships with Android and needs no
  dependency and no Google service: `LocaleData.getMeasurementSystem` on the
  **formatting** locale, since somebody reading English in Lyon wants
  kilometres. **Three systems, because the imperial world is not one place**:
  metres then kilometres, feet then miles, yards then miles — folding the last
  two together would write "820 ft" to a reader whose road signs count in yards.
  The setting in "Settings" has **four states**, "System" being one of its own
  rather than a synonym for metric, and it names units rather than countries —
  `m · km`, `ft · mi`, `yd · mi` — because what one gets is what is written and
  no system of measurement belongs to a nation. It is written the moment it is
  pressed and applied at once, on every screen, the interface being rebuilt on
  it as it is on a theme.
  **The rule the whole thing rests on is now written into `SPEC.md` §14: the
  application computes in metres and converts only at the last moment, to write
  a piece of text.** The routing engine, the algorithm of §6, the bounding
  boxes, the manifests, the GBFS feeds and the elevation profile are all in
  metres and none of them knows this setting exists; the conversion happens in
  one function, on the value just before it becomes a string. A rider in miles
  and a rider in kilometres therefore get the **same journey**, the same
  departure station and the same announced time — only the writing differs, and
  two tests in `:core` fail the day that stops being true. The conversion and
  the rounding moved into `:core` in the same breath, in plain Kotlin with no
  Android import, which is what makes them testable on the JVM at all;
  `ui/Distances.kt` keeps only what needs a `Context`, fetching the unit's
  symbol from the resources.
  **Every imperial step is at least as coarse as its metric counterpart** — 50
  ft or 25 yd where metric writes 10 m, 20 ft where it writes 5 m — so changing
  units never claims to have measured better than the metre did: an address is
  known to a few metres and the reader's position to a good deal less. The unit
  changes at a thousand of the smaller one in all three systems, the metric
  threshold applied to another unit, past which the figure needs four digits and
  stops being read at a glance. **The two silences of a climb do not move**:
  three hundred metres of ground and five metres of height are facts about the
  SRTM samples rather than about the reader, and a climb too small to be real is
  just as unsayable in feet. Height follows the system chosen, in feet for both
  imperial ones, the elevation profile's axis and its spoken description
  included: a sentence does not mix two systems.
  Read on a Fairphone 3 rather than assumed: ICU answers `UK` for `en-GB`, `US`
  for `en-US`, `SI` for `en-FR` — and `US` for a bare `en` or for the
  undetermined locale, which is a default standing in for an answer, so a locale
  carrying no region is read as metric here. `getMeasurementSystem` arrived in
  Android 9 and this application serves Android 8, so the two regions it names
  in feet and the two it names in yards are copied from ICU's own reading of all
  253 regions it knows, for those two releases alone.

- **Choosing the bike a journey is worked out for.** Where the network lends
  both kinds, the journey screen offers "any bike", "mechanical" or "electric",
  beside the switch for one's own bike and remembered like it: what somebody
  wants to ride is a fact about them, not about one trip. Asking for nothing is
  the default, at installation and after any reset — the application presumes no
  kind, as it presumes no city. Asking for one is a **strict filter and not a
  weighting**: a station that does not hold that kind is not a candidate at all,
  and when none is left the answer says so and names the kind — "no station
  nearby has an electric bike right now" — rather than walking somebody towards
  a bike that is not there. No penalty coefficient was invented for the wrong
  kind, because there was nothing to measure. A station whose breakdown cannot
  be read is left out too: one cannot promise a bike one has not managed to
  count — and asked for nothing, that same station is a candidate like any
  other. **The reliability penalty then weighs the bikes of that kind alone**:
  one electric bike among eight at six minutes' walk loses to four electric
  bikes a minute further, where counting the whole rack would have sent the
  rider to the first — the seven mechanical bikes beside the lone electric one
  serve nobody who asked for an electric one. No coefficient is added or
  changed, only the base the turnover rate divides. Two things stay as they
  were: the arrival end, a free dock being a free dock whatever is returned to
  it, risk included; and the time announced, an assisted bike being traced over
  the same graph with the same profile. The counts the journey carries and shows
  are still the station's whole stock — the penalty ranks, it is never
  displayed.
  The selector **does not exist** in a conurbation lending one kind, nor while
  the own-bike switch is on — absent rather than greyed out, since a choice
  nobody can collect is a promise not to make — and it appears or goes with the
  reading of the fleet, without the screen being rebuilt. A choice remembered in
  a mixed city and read back in a mechanical one is ignored and not erased, so
  coming back finds it. The map gets a filter of its own beside its "bikes /
  free docks" toggle, counting one kind in the markers, and the two controls
  command nothing of each other. **Neither reaches a station's sheet or a
  journey's detail**, which show both counts whatever was asked: they answer
  "what is waiting there", and a filter is a question put elsewhere — a
  requirement negative enough to deserve a test that fails the day somebody
  wires a kind into either. `SPEC.md` §6, §7.1, §7.2, §7.3, §7.4 and §7.4.1 say
  it; the choice is one word in the preferences, "mechanical", "electric" or
  nothing at all (`SPEC.md` §2, C3).

- **A journey on one's own bike.** A switch under the two points of the journey
  screen, off by default and remembered from one journey to the next: what
  somebody rides is a fact about them, not about one trip, and asking again
  every time would ask them the same answer every time. Turned on, the journey
  is a single ride from door to door — no station is chosen, no walk is
  measured, and the station feed is not consulted at all, so somebody who has
  never refreshed it still gets their route. The direct walk is neither computed
  nor compared: it guards, in the station algorithm, against a journey where
  fetching a bike costs more than it saves, and here nothing is fetched. The
  result reads in one leg — the ride's time, "on your own bike, over 5.2 km and
  15 m of climb", one unbroken stroke between the two ends — and its detail
  carries the ride's elevation profile with no station row and no availability
  note. The sentence and the drawing of the search screen follow the switch, the
  drawing losing its dashes and its station discs, and its two ends becoming
  the filled bike disc already drawn elsewhere instead of the outlined walking
  figure — the figure says how that point is lived, and nothing here is walked.
  The same ends are drawn on the result screen and on its map. They never take
  the cog, and they take the bolt from the rider rather than from the network —
  see the setting further down this list: what the network lends says nothing
  about a bike that is not the network's, and what the rider declares says
  everything about it. `JourneyPlan` gains an `OwnBike` case and `JourneyPlanner` a
  `planWithOwnBike`; the choice is a boolean in the preferences and nothing
  else — no point, no time, no destination (`SPEC.md` §2, C3). `SPEC.md` §7.3,
  §7.4 and §7.4.1 say it.

- **A journey says what is waiting at the station it starts from**, in the
  conurbations that lend both kinds of bike. The summary ends on the two counts,
  in the wording of the station's own sheet — "45 m of climb · 5 mechanical ·
  3 electric at the departure station" — on the result screen and on its detail
  alike, which repeat each other word for word. It is what the decision to walk
  to a station rests on and what the rest of the line cannot supply: the total
  time promises a bike, and which bike is what turns a hilly ride into one worth
  taking. The counts are the frozen ones
  the journey was worked out on, like the minutes beside them, and the
  breakdown travels in the availability already read — no extra request. It
  goes silent under the rules that silence a station's own split: a
  conurbation lending one kind, a breakdown that does not add up to the count,
  a vehicle type the network never declared. `JourneyOption` carries the
  departure station's breakdown in the producer's own identifiers, the
  algorithm having no business knowing which identifier is which (`SPEC.md`
  §15); the interface reads it through the network's table.

- **The first-launch sequence gains a page explaining the bike drawn.** Third
  of four now: the three station discs side by side — plain, bolted, bolted and
  cogged — over three sentences saying which is which and that it is read from
  the network's own feed rather than guessed. A mark met on every screen
  afterwards deserves to be introduced once; `SPEC.md` §7.9 said three pages
  and now says four, which is its ceiling.

- **A city lending both kinds draws a cog beside the bolt.** The bike glyph had
  two states, plain and bolted, and a mixed fleet counted as electric; it now
  has three, and the third says the truth of 102 of the networks served: both
  offers, side by side. The cog sits under the bolt, offset a little towards the
  edge so the two read as one pair, at the glyph's own scale, so it rides down
  to marker size with it — the journey
  button, the ride leg, the station discs on the map, in the search screen's
  illustration and in the bike that crosses the screen while a journey is being
  worked out. Three new vector drawables, no new asset weight worth measuring.

- **A station says how many of its bikes are mechanical and how many are
  electric**, under the count, in the conurbations that lend both — "3
  mechanical · 1 electric". It is the moment the question is asked: one is
  deciding whether to walk over, and a climb makes the difference between the
  two. It costs no request, the breakdown travelling in the `station_status`
  feed already fetched, as `vehicle_types_available` since GBFS 2.1 and as
  Vélib's `num_bikes_available_types` on GBFS 1.0. The map and list markers are
  unchanged and keep counting whole stations: two numbers in a disc are
  unreadable at fifty stations on screen. The line disappears rather than lie —
  a vehicle type the network never declared, or a breakdown that does not add up
  to the count displayed, leaves the total standing alone, which is always true.

- **A city lending pedal-assist bikes draws them with a bolt.** Whether the
  fleet is electric is now part of a city's configuration, in a `fleet` block,
  and every bike the interface draws for that city carries a small bolt: the
  button opening the journey search, the ride leg of the detail, and the discs
  standing for a station on the map, in the search screen's illustration, in
  the shape of the computed journey and in the bike that crosses the screen
  while it is being worked out. It is the difference between two offers, and
  the first thing one wants to know before walking to a station. The value is
  never typed in: `tools/read_fleet.py` reads the network's own GBFS
  `vehicle_types` feed, where a bicycle whose `propulsion_type` is electric
  settles the question — 192 of the networks served lend pedal-assist bikes,
  101 lend mechanical ones, and 13 declare no vehicle type at all and keep the
  plain bike. A mixed fleet counts as electric: the question is whether the
  city lends electric bikes. The application's own identity is untouched — the
  launcher icon and the welcome screens are the same whichever city is served,
  one of them being shown before any city has been chosen.

- **The result screen has a button bringing the map back onto the whole
  journey.** It sits above "locate me", which is the button that takes the map
  away from it: coming down onto the walker, or panning to look at a junction,
  left no way back to the shape of the journey short of computing it again. The
  framing is the one the screen lays by itself, applied to the map as it stands
  — but animated, since a press deserves to be seen answered, and the camera's
  limits are stood down for the whole flight rather than laid again mid-move,
  which would jump the camera inside its box and kill the animation. The button
  is absent while there is nothing to frame.

- **The journey's detail draws the ground the ride runs over.** The routing
  graph carries the elevation of every node it returns, and until now only
  their sum was read. A total says how much there is to climb, never where: a
  hundred metres taken in one wall at the end of the ride is not the ride a
  hundred metres spread over ten kilometres is. The curve is the bike leg's
  alone, on its own vertical scale, with its lowest and highest written at the
  ends so an amplified bump cannot read as a mountain. It is smoothed over a
  hundred and fifty metres first: raw, the SRTM samples draw a saw of a metre
  up and a metre down every fifty across flat country, which is their error and
  not the ground — the same error that keeps a climb from being named under
  three hundred metres, and the same silence when there is less than five
  metres of height to show.

- **The journey opens in full.** The block holding the total time, the summary
  and the drawing is now itself the way to a screen of its own, pressed where
  it is read: it names each station, the street it stands in — read off the
  offline address index, since the availability feed publishes none — what it
  held when the journey was worked out, and every leg with its distance, its
  minutes and its climb. The two ends of the journey have no row there: the leg
  reaching each of them names it, and the fields of the screen one comes from
  name it again. The journey travels there in memory, never through a
  saved argument: it carries its tracks point by point, and `SPEC.md` §8 wants
  it kept nowhere.

- **A "navigation" button hands a leg of the journey to an application that
  guides along it**, in the place the "details" button held. A `geo:` URI
  carries one point and no standard scheme carries a route, so the press asks
  which part of the journey is being set off on and hands that leg's end over,
  named. This application is taken out of the choice: it answers `geo:` itself,
  and on a phone where it is the only one to, handing a leg over reopened Roue
  Libre and started the journey again. Where no other application answers, the
  screen says so.

- **The journey says what it climbs.** The summary names the metres gained over
  the whole trip, walks included, and each step of the detail names its own —
  five metres on the walk out, seventy on a ride from Lille to Roubaix. The
  figure was already in the graph: the routing data is built with SRTM
  elevation, and BRouter has been returning its filtered ascent since the first
  route was traced. Nothing read it. On a heavy share bike it is what separates
  a ride one takes from a ride one regrets, and no city among the 306 is
  promised to be as flat as Lille.
- A climb is named over three hundred metres of ground and not under it, and
  from five metres up. The graph's elevation is SRTM sampled every thirty
  metres or so: a shorter stretch is described by the error between two
  readings rather than by the ground, which is how forty metres of pavement
  came to announce five metres of climb in testing. A plain ten-metre floor was
  tried instead and dropped — it silenced the bike leg of a flat conurbation's
  journeys while the total, summing three legs, still named a climb. The same
  silence covers a graph generated with `--no-elevation`, which would otherwise
  read a row of zeroes.

- **Every configured conurbation now has its data, and it is published.** The
  three sets exist for all **306** networks, 5.60 GB in total — median 10.2 MB
  a city, 2.0 MB for Moravská Třebová, 165.8 MB for Rotterdam, which is the
  heaviest and still under the 200 MB ceiling of `SPEC.md` §4.2. Together they
  hold 923,859 tiles, 1,115,241 streets and 20,706,107 house numbers. Each was
  checked against its manifest's digests, both its SQLite files opened and
  queried, and its routing graph counted.

- **The map can leave out the stations that answer nothing, and it is asked in
  the settings.** Two switches at the end of the display section: **hide the
  stations out of service**, and **hide the ones with nothing to offer**. In a
  dense conurbation the map carries several hundred discs and a good part of them
  are of no use to somebody looking for a bike now — a station out of service
  will lend none, a station at zero will lend none either. "Nothing" reads with
  the map's own toggle: no bike while bikes are counted, no free dock while docks
  are, so the same station disappears under one and comes back under the other.
  The switch therefore names neither, the Bikes / Free docks button being a state
  of a screen the settings know nothing about, and a line under it says what
  "nothing" is read against. **Both are off by default** — and off again for a
  stored value that is not a yes or a no. No adjustable threshold and no "at
  least three bikes": the marker already says how many there are.
  **What the feed is silent about is never hidden.** `ServiceState.Unknown`
  survives "out of service", and a count nobody could read — a station absent
  from `station_status`, a breakdown by kind that does not add up — survives
  "empty". Hiding either would assert on the strength of a silence something the
  application has not read, which is the same rule that silences a station's
  split rather than guessing at it. Three of the eight tests in `:core` exist for
  that case alone, and they fail the moment an absent count is read as a nought.
  The rule is written under the two switches, which is now the only place it can
  be read.
  **The map obeys and says nothing.** The switches were first put on the map
  itself — a pill beside the mode toggle, opening a sheet, and naming how many
  stations were missing while a filter was on — and that pill, that sheet and its
  funnel are gone. Seen on the screen rather than on paper, the question turned
  out to be one put once and left alone, which does not belong among the controls
  pressed while walking; keeping it there cost answering it again at every
  launch, since nothing was written down. So the filters are **kept from one
  session to the next**, like the theme and the units, and the map carries no
  control, no badge and no count.
  **The compromise is named in `SPEC.md` §7.1 rather than left to be found.** A
  filter that outlives the session with nothing on screen to recall it means
  somebody can reopen the application weeks later on a neighbourhood emptied of
  its stations, and the settings are the only place that explains it. The
  requirement that stood in §7.1 until then — a map that hides stations says so —
  is withdrawn with the control it was written for, and the paragraph replacing it
  says why, so the absence reads as the decision it is. It is said again where the
  preference is read, in `AppPreferences`.
  The sifting is still done in `:core` in plain Kotlin, on what the marker
  **shows** rather than on the raw feed, which is what makes it agree with the
  kind filter for free; and it happens before the features are built, not by
  redrawing several hundred markers and hiding some. It lost the parameter that
  spared the stations of a journey drawn over the map: no caller ever filled it,
  the map screen carrying no journey and the result screen having a map of its
  own.
- **The language of the interface is chosen in the application** (SPEC §9,
  §7.6). It followed the system, with no recourse: a francophone whose phone is
  in English read the application in English, while the French translation sat
  complete in `values-fr/` and out of reach. §9 has the other languages exist to
  be read, and reaching them only by changing the language of the whole phone
  emptied half of that. The setting sits under the units in the display section,
  applies on the press with no "apply" button, and follows the system by
  default — which is the behaviour of every version before it, and what an
  absent or unknown value reads as.
  **Only the languages actually translated are offered**, which is the whole
  difficulty of the change: the repository carries thirty `values-<language>/`
  folders and thirty entries in `localeFilters`, and twenty-eight of them still
  hold the English text. The chooser is **derived from `TRANSLATED_LANGUAGES`**
  in `ui/Locales.kt` — the list the dates and distances already followed — so
  finishing a translation is the whole of what it takes to have it offered, and
  offering "Deutsch" to hand back English never becomes possible. Each language
  is named in its own language, and a test fails if that list is ever copied out
  a second time.
  **AppCompat holds the choice and holds it alone**: it applies it, stores it —
  in the framework from Android 13 on, in a file of its own below that, opted
  into by the `autoStoreLocales` service now declared in the manifest — and
  rebuilds the screens on it, as a change of theme does. No second copy in
  `AppPreferences`, which would diverge the first time the language was changed
  from Android's own per-application settings. Those settings offer the same
  languages, through `res/xml/locales_config.xml`: the one place the list is
  written twice, since Android reads it from the resources, and a unit test
  reads that file and fails if the two disagree. `CONTRIBUTING.md` now lists the
  three places a finished translation has to be declared in.
  **The units do not follow the language**, and holding that took a change:
  `regionUnitSystem` read `ULocale.getDefault(ULocale.Category.FORMAT)`, which
  the chosen language now sits at the head of, and a language carries no
  country — the tag is `fr`, never `fr-FR`. Somebody in Boston putting the
  interface into French would have lost their miles to a setting about words.
  It reads the device's own configuration instead, which no per-application
  language overrides; with no language chosen the two answer the same thing.

### Changed

- **The settings read in a new order: city, display, journey, offline data**
  (SPEC §7.6). Display comes before journey because what one settles on
  arriving is what one is looking at — the language, the theme, the units —
  whereas the journey section qualifies a request that has not been made yet,
  and reaching the theme meant scrolling past it. **The language now opens the
  display section**, above the theme, since it decides how everything under it
  is read, the labels of the other settings included. **One's own bike now
  heads the journey section**, above the walking pace, since it says who the
  cyclist is before the pace says how they walk. Blocks moved and nothing else:
  the views keep their identifiers, their strings and the code that wires them,
  and the spacing rule of the screen is unchanged — the first setting under a
  title carries `space_m` and the ones after it `space_l`.

- **The French of the store texts now says *tu*, like the interface.**
  `res/values-fr/` has always addressed the reader as one person; the F-Droid
  description and all three release notes said *vous* — "Vous choisissez votre
  ville au premier lancement" — so the store page and the first screen spoke to
  two different people. All five files were rewritten rather than conjugated
  where the person was carried by a possessive pronoun or a subjunctive
  ("l'application propose la vôtre" → "la tienne", "sans que vous le disiez" →
  "sans que tu le dises"); the tone is untouched, only the person moves. Nothing
  in the application changes — the what's-new screen reads those very files
  (§7.10), so what it shows follows. The rule is now written where it will be
  read before the next translation is started: `SPEC.md` §9 and the translation
  rules of `CONTRIBUTING.md`. It is about the French; documents addressed to
  contributors are outside it.

- **The settings screen is laid out in sections.** Nothing is added and nothing
  behaves differently: the four settings it holds are the same four, in the same
  order, doing the same thing. What changes is that each now sits under a
  section title — city, display, offline data — instead of carrying a title of
  its own, and that "about" closes the screen outside them all. The reason is
  what comes next: six settings are due to arrive here over the coming weeks,
  each from its own piece of work, and left to invent their own place they would
  produce six layouts and as many conflicts. `SPEC.md` §7.6 now names the
  sections, fixes their order and says where a new setting goes, which is the
  paragraph those pieces of work will read. **An empty section is not shown and
  is not written into the layout**: "Journey" is absent until the first setting
  is placed there, a title followed by nothing being a promise the screen cannot
  keep. A section title is a heading for a screen reader, which means one can
  jump between sections rather than hear the screen read end to end — set in
  code rather than in the layout, the attribute that declares it having arrived
  in API 28, above this application's floor.

- **A distance under a kilometre now follows the interface's language, not the
  device's.** It was written through `%1$d`, which Android formats with the
  configuration's locale, while the kilometre beside it went through
  `NumberFormat` and the language actually displayed. On a device set to a
  language the application does not speak — the case `SPEC.md` §9 settles — the
  metres came out in that language's digits and the kilometres in English ones,
  in the same sentence. Both are now written by the same rule. Nothing changes
  in English or in French, the two languages the interface exists in.

- **What a city lends is now counted, no longer merely declared.**
  `tools/read_fleet.py` used to read the GBFS `vehicle_types` feed and believe
  it; it now counts the bikes standing at the stations. A survey of the 333
  networks served showed why: a third of those declaring a mixed fleet have not
  one bike of one of the two kinds out. Madrid declared a mechanical type and
  puts out 5872 electric bikes and no mechanical one, Berlin declared an
  electric type and puts out 1989 mechanical ones and no electric one — both
  wore the wrong glyph. Vélib' Métropole, which declares nothing at all, wore a
  plain bike over 7836 electric ones. The `fleet` block now also carries whether
  both kinds are really lent, and the table translating the producer's vehicle
  type identifiers, without which a station's count cannot be split. A kind
  under two percent of the bikes counted does not make a mixed fleet: Barcelona
  puts out 1922 electric bikes and 2 mechanical ones, which is a residue and not
  an offer. Counted today: 102 mixed fleets, 95 electric, 136 mechanical, and 27
  networks letting nothing be counted, which keep their declaration.

- **The step list left the result screen for the detail screen**, and the
  "details" button that unfolded it went with it. It was a row spent saying in
  words what the block above it can say by answering to a press, on a screen
  where every row is taken from the map. The `ic_plus` and `ic_minus`
  drawables, which nothing else used, are deleted.

- **The journey's summary moved up beside the total time**, level with its top,
  instead of sitting on a line of its own under it. It says the same thing and
  gives the map back the row it was taking. A weighted `LinearLayout` row was
  the obvious way and the wrong one: it settles its own height before handing
  the summary its share of the width, so the second line of a summary that
  wraps fell outside the row and was cut off by the drawing under it. The row
  is a `ConstraintLayout`, checked at font scale 1.5 where the summary runs to
  three lines.

- **The journey's drawing holds its figures against its strokes.** The distance
  above and the time below stood a full `space_s` off the discs, and read as two
  rows of figures floating over and under a drawing rather than as its labels.
  The gap is its own dimension now, at 2 dp: measured from the disc, but read
  against the stroke half a disc further in, which leaves the figures room to
  breathe while belonging to the line they measure. The whole block is 12 dp
  shorter, which the map keeps.

- **The data is laid out as one release per country**, plus a last one holding
  the catalogue and the 306 manifests. GitHub allows a thousand assets per
  release and the sets come to some 1,350 files, so a single release could not
  hold them. The index release is re-created after every other one, because
  `releases/latest/download/manifest-<network>.json` is what the application
  asks for and *latest* means the newest release of the repository. Neither the
  application nor the 306 configurations changed for it: only where the files
  sit, which each manifest names for itself.

- **The interface takes the icon's colour.** The signal hue is now the
  launcher icon's green to the digit, and the neutrals lean green rather than
  blue: an application whose home-screen icon and first screen do not share a
  colour reads as two objects. The availability scale, the map's palette and
  the dark theme follow, contrasts re-verified — the lowest in the set is
  5.04:1.
- The five `surfaceContainer*` roles of Material 3 are now mapped onto the
  project's two tones. Left unset they were not neutral: the station sheet,
  which reads one of them, was drawn in the library's mauve-tinted grey.
- The main action button takes the interface's radius instead of Material's
  pill. It was the only perfectly round element besides the availability
  indicator, which is the one thing meant to be round.

- **The application has its own icon**: a bicycle whose front wheel is a map
  pin, white on a deep green, in place of the green robot Android Studio
  leaves behind. It is adaptive, and themed on Android 13 and above — the
  monochrome layer repeats the drawing exactly, so the icon keeps its form
  when the launcher recolours it. The SVG sources are kept in `art/`, with
  the note on how they map onto the adaptive canvas.

### Removed

- **The larger availability figures**, switch and dimensions both (SPEC §7,
  §7.6). It drew the indicator's figure and its disc a third larger on the
  station list, the favourites and a station's sheet. Android's own text size
  already does that: the indicator follows it, both its tokens being in `sp`,
  and this switch added nothing to it — for a preference, a helper collecting
  it, a flag threaded through three adapters, two dimension tokens and two
  paragraphs of specification, five files to keep in step for a size the system
  offers anyway. The figures go back everywhere to the size they had before it
  existed. **The stored preference is not migrated**: the key
  `large_availability_numbers` leaves the code and a value an existing
  installation may have written stays on disk, unread — nothing is written to
  remove it, as with the pick-up and drop-off time below.

### Changed

- **How near a network is, is measured on its stations rather than on its
  rectangle.** The application offers the network of the conurbation one
  happens to be in; it read that off the reference box, which describes a
  conurbation well and a region not at all. With regional feeds now served, a
  position in the middle of the Morvan was offered 1.4 GB of map for Vélo Fluo,
  whose box passes 46 km away and whose nearest bike is 130. Each city
  configuration therefore carries eight station positions, spread through the
  network — `tools/sample_stations.py` reads them from the feed, and the
  catalogue carries them on — and both the ranking and the fifty-kilometre
  offer measure the distance to the nearest of them. Seclin still gets V'lille
  at 8.3 km; the Morvan gets nothing at 72.7. A catalogue produced before the
  field exists falls back on the box, which is the previous behaviour rather
  than none. The catalogue grows from 182 to 389 kB.

- **The base map's 300 MB is a figure to design for, not a gate.** It was a
  200 MB ceiling; raising it to 300 was not enough to make it true, because a
  network serving a whole region legitimately exceeds any such figure and
  refusing its map would be refusing the network. Twenty-four cities generated
  in one run measured what a rectangle never told: Dubai's box is the second
  widest of them all, 162,065 km², and its map weighs 160 MB because it is
  desert from edge to edge, while Brussels weighs 932 MB over a box seven times
  smaller because Belgium is built upon everywhere. Between Hilo at 2.4 kB/km²
  and Cologne at 44 there is a factor of eighteen at comparable areas. Every
  conurbation stays well under the figure — the heaviest of the three hundred
  is 172 MB — and six regional networks stand above it, their weight announced
  before the download like everyone else's. The building layer, which §4.2
  names as the lever, was measured rather than assumed: moved to zoom 16 it
  returned 15%, which brings a 323 MB map under the figure and does nothing for
  a 932 MB one. It stays unpulled — it would change the map of every city for
  the sake of six.

- **A network is no longer refused for the ground it covers.** The survey used
  to reject any feed whose stations enclosed more than 2,500 km², on the
  grounds that such a rectangle is a region rather than a conurbation. Kiel
  failed it by seven per cent — 2,672 km² for a network of 203 stations that
  is plainly one city and its region — and a line that turns that away while
  admitting the same shape at 2,499 says more about the line than about the
  network. The rule is gone rather than moved. What the data costs is settled
  where it can be measured, by the tile ceiling of `SPEC.md` §4.2, on the files
  produced: Kiel's whole dataset weighs 54.4 MB, less than Lille's over four
  times the area. The area is still surveyed and printed for every network. It
  lets 27 feeds into the list of what can be served, from Capital Bikeshare and
  BIXI Montréal to national ones — being eligible is not being served, and each
  still has to have its data produced.

### Added

- **Twenty-six networks join the ones served**, all of them feeds the area rule
  used to refuse: Capital Bikeshare in Washington, BIXI Montréal, Antwerp's and
  Brussels' Donkey and Blue-bike, METROROWER in Katowice, MEVO in Gdańsk, MyRadl
  in Munich, Lyft Bike in San Jose, Careem in Dubai, nextbike in Lucerne,
  Nicosia, Sarajevo and Zagreb, Beryl in Cornwall, Bora in Viseu, HIBike in
  Hilo, Lovesharing in the Canaries, and others. Twenty-four have their data
  produced — 6.8 GB in all — of which six weigh more than the 300 MB ceiling
  and are therefore listed without data until the question of the building
  layer is settled. Vienna and Zurich wait on Geofabrik: two of the extracts
  their boxes need still carry the previous day's snapshot, and merging two
  days leaves the same node twice.

- **Kiel is served** (`config/cities/kiel.json`): Donkey Republic, 203 stations
  and 4,118 docks over the city and its region, from Rendsburg to Plön, with
  pedal-assist bikes. Its data weighs 54.4 MB — 50.1 of base map, 1.9 of
  routing graph, 2.4 of address index over 5,918 streets and 102,214 house
  numbers. The GBFS registry publishes this network twice, under the address it
  opened with and the one it carries today; the configuration takes the second.

- **The screen the application opens on is a choice**: the map, as always, or
  the station list (`SPEC.md` §7.0, §7.6). The map is the application's content
  and stays the default — somebody who never opens the settings sees exactly
  what they saw before — but it is not what everybody opens the application for:
  somebody who always sets off from the same station reads one line of a list,
  not a plan. **Opening on the list asks for no position**: the location
  permission belongs to the map (§10), so the map is not built at all rather
  than built and replaced, and coming to it afterwards asks then, once. The
  choice settles where one lands and nothing else — the welcome sequence, the
  what's-new screen and a place received from another application all still come
  over it, an explicit intention beating a preference. A list opened on carries
  a way to the map, which it does not carry when it was reached from one: there
  the back gesture is that way, whereas opened on it has nothing behind it, and
  the map is where the journey search and the settings are reached from.

- **The availability figures can be asked for larger** (`SPEC.md` §7, §7.6). A
  switch in the display section, off by default. It is not a stand-in for
  Android's own text size — the whole interface follows that one, the indicator
  included — but it does what that setting cannot: it enlarges the single figure
  the application is opened a hundred times a week to read, and leaves
  everything around it where it was. It reaches the figure where it is read at
  leisure, in the station list, the favourites and a station's details, **and
  not the map's markers**: a marker's size decides how many stations stay
  legible side by side at a given zoom, which is a question of map drawing
  rather than of accessibility, and enlarged the discs would overlap and the map
  would say less. The line under the switch says so, so nobody looks for a
  change that was decided against. The disc grows by the same third as the
  figure inside it, that ratio and not the absolute size being what keeps three
  digits inside the ring.

- **One can say what one's own bike is**, mechanical or electric, in the journey
  section of the settings (`SPEC.md` §7.3, §7.4, §7.6). **Not specified is the
  default**, at installation, after a reset and for a word that cannot be read,
  and it reproduces exactly the drawings and the sentences of the version before
  this choice existed. Declared electric, a journey on one's own bike draws the
  bolt at its two ends — on the search screen's illustration, on the result
  screen's drawing and on the two points of its map — the summary says "on your
  own electric bike", and the one step row of the detail carries the same bike
  beside it, since three readings of one journey on one screen have to agree.
  A bike declared mechanical takes the plain drawing, as
  an undeclared one does: the plain bike promises the least and the bolt is what
  has to be earned.
  **It changes the drawing and the sentence, and nothing else.** No speed, no
  coefficient, no dedicated profile — a decision and not an omission. A
  pedal-assist bike is quicker in the real world, but §6 announces only what the
  routing engine traced, and the ride runs over the same graph with the same
  profile whatever was declared: the same pair of points comes back with the
  same track and the same minutes under all three states, which is what the test
  holds. What holds it structurally is that `OwnBikeKind` lives in the
  application's settings and the `core` module the algorithm lives in cannot see
  it.
  **It is not the kind of bike asked of the network**, and the two are
  deliberately named apart, documented apart and stored under different keys.
  That one is a question about the network's bikes — which of them one wants to
  be sent to — so it exists only where the network lends both kinds and it
  narrows the stations the algorithm may choose. This one is a question about
  the rider, whose bike belongs to no fleet and is the same in Lille as in Lyon:
  it is offered **everywhere**, `FleetDescription.isMixed` being none of its
  business, and the line under the three buttons says what it does not do, so
  nobody expects a faster journey out of it.

### Fixed

- **"1 free docks".** The word beside a station's count did not agree with the
  count: a station with one free dock read "1 PLACES LIBRES" on its sheet and on
  its list row, and "1 free docks" in English. `counterpart_bikes` and
  `counterpart_docks` were fixed `<string>`s, the only two labels in the
  application to escape the rule SPEC §9 and §14 set — a label posed beside a
  figure is a sentence, even when it is two words long, and a sentence agrees.
  They are `<plurals>` now, in all thirty language files, with the categories
  each language draws and no more: English `one`/`other`, French adding the
  `many` that takes "de", `other` alone where a language agrees nothing.
  **What makes these two unlike every other plural here is that the number is
  not in them.** The disc beside the label already holds the figure, so an item
  written `%1$d free docks` would say it twice; they are resolved with the count
  as a quantity and formatted with no argument at all. That is also what Android
  lint's `ImpliedQuantity` is unable to imagine — it reads a plural without a
  placeholder as an oversight — so the two carry a `tools:ignore` naming it, and
  nothing else does.
  **Since the label now depends on a count, it comes from the code**, not from
  `android:text` in `sheet_station_detail.xml`; a disc with no figure — unknown,
  or out of service — keeps the plural form.
  `CounterpartAgreementTest` reads the thirty files off the disk, the way
  `IndicatorScaleTest` reads `dimens.xml`, and fails on two counts: a language
  whose categories do not match its grammar, and an item that has grown a
  placeholder back. The second is the one a contributor breaks first.
  One consequence worth writing down, because it looks like a defect: French
  puts 0 in `one`, so an empty station reads "0 place libre" while English reads
  "0 free docks". Both are right; the plural rules differ.

- **A station's sheet cut off its labels and its three buttons.** Measured on a
  Fairphone 3 in French: at ×1.3 "Ouvrir dans une appli de navig…", at ×1.8 the
  "LIBRES" of "PLACES LIBRES" gone over the edge of the screen, at ×2.0 "PLAC",
  "Partir d'i…" and "Ouvrir dans une appli…". The screen the text size exists
  to serve, again made less readable by it, which SPEC §7 rules out.
  **The two counts are now a `StackingRow`**, a third view for the answer
  `ToggleRow` and `StationRow` already give: each count is a block of its own —
  a disc and the word naming it, which are read as one thing — and the two
  blocks step one under the other as soon as they are wider than the sheet. The
  question is measured at the size in force, carries no threshold and no
  coefficient, and the gap between the blocks becomes the gap above the second
  one when they stack, as the station row's does.
  **The buttons were a theme leaking in.** Material's bottom sheet theme
  descends from its dialog overlay, which points `materialButtonStyle` at an
  alert dialog's button — `android:lines="1"`, `android:singleLine`,
  `android:ellipsize="end"`. A style quoted in a layout only overrides the
  attributes it names, so the three buttons took their colours and their shape
  from `Widget.RoueLibre.Toggle` and their line limit from Material.
  `ThemeOverlay.RoueLibre.BottomSheetDialog` puts the application's own button
  style back where every other screen has it, and the labels take the lines they
  need. **Nothing else on any sheet changes**: they are the only buttons in the
  only two sheets, and both quote the style that decides what one sees.
  **The name, the address, the breakdown and the capacity line are no longer
  justified or hyphenated.** They are lines that name one thing, and
  `Widget.RoueLibre.Name` already refuses both for exactly them elsewhere; until
  now the sheet let the theme hyphenate a station's name in the middle of a
  word — measured at ×1.15 on a 320 dp screen — and spread "18 points d'attache
  · Mis à jour…" bank to bank.
  **At the normal text size nothing has moved**, pinned by a test rather than
  asserted. `SheetTextSizeLayoutTest` lays the sheet out at the seven steps of
  the slider, in English and in French, on 320, 360 and 411 dp, and fails on the
  previous layout on five counts. It resolves the **bottom sheet's own theme**
  the way the dialog resolves it, which is not a detail of the harness: under
  `Theme.RoueLibre` alone the buttons wrap happily and the test reports a screen
  that is broken on the device. It also asks a question the other two files do
  not — whether a view is drawn **inside** its parent — because a label given
  more width than the sheet has carries no ellipsis and reaches its last
  character while half of it is off the screen, which is exactly how "PLACES
  LIBRES" lost its second word.

- **The availability disc stayed the size it was while everything around it
  grew.** Android applies a **non-linear** magnification to sizes declared in
  `sp` — the larger the number, the less of the reader's factor it receives —
  and the indicator's three tokens sit far apart on that curve: the disc at
  `52sp`, its figure at `24sp`, the body text beside it at `16sp`. Measured on a
  Fairphone 3 running Android 15, at ×2.0 the body text grew by ×1.75, the
  figure by ×1.50 and the disc by **×1.11**. So the disc stood still while the
  line around it grew — the exact inverse of what SPEC §7 promises, which takes
  this very disc as its example.
  **The consequence is the real finding, and it is one about tests.** The room
  the ring keeps for a three-digit count is a ratio, disc over figure, and on
  the device it fell to **1.60**, under the **2.1** `IndicatorScaleTest`
  requires to hold three digits inside the ring. **That test was green
  throughout.** It reads the numbers as written in `dimens.xml`, where the ratio
  is right, and no JVM applies the device's curve. Three tokens read at three
  points of one curve stop describing the drawing they were chosen for, and a
  test that never leaves the JVM cannot see it happen: it guarantees that the
  tokens were *written* consistently, never that they *resolve* consistently.
  **The three tokens are now read as the proportions they set out** — 52 : 24 :
  16, taken raw from the resource table before any scale touches them. The
  figure is painted as a multiple of the body text at the size the system is
  actually giving body text, and the disc as a multiple of that figure. The
  curve is neither re-implemented nor fought: it is read once, at the point
  where the text the disc lives beside is read. **No new coefficient appears** —
  §14 asks that each be justified, and there is none to justify, the two ratios
  coming from the two design tokens that stay the one place these sizes are
  decided. At the normal text size the disc is 137 px as it has always been.
  `IndicatorScaleOnDeviceTest` measures the growth at the seven steps of the
  slider and fails on the previous view from ×1.15 upwards; it asserts
  **proportions rather than pixel counts**, so it holds on a phone whose curve
  differs. SPEC §7 now carries the defect, the lesson about what an off-device
  test can and cannot guarantee, and the party retained.

- **The station list wrote station names away at the text sizes the settings
  screen was just repaired for.** At ×2.0 on a Fairphone 3 the list read
  "Alfre…", "Anato…" and "59260…": a name told from its neighbour's at the fifth
  letter, on the screen one uses to pick a station, which is the grievance SPEC
  §7 makes against a truncated availability figure. It was not the list alone —
  `android:maxLines="1"` held a line to one in nine layouts — so the address
  results, the shortcut naming a favourite station, the place picked on the map,
  the map's attribution and the line saying what a download is doing all wrote
  themselves away the same way. **Those lines now have no line limit and nothing
  to ellipsize with**: they take the lines they need and what holds them grows
  taller, with no maximum, for the reason now written into SPEC §7 — a line of a
  list is the full width of the screen and has no equal share to stay inside, and
  a station's name is written by the network rather than by us. The three
  single-line **search fields are deliberately untouched**: what is typed there
  scrolls under the caret rather than being written away, and the one line is
  what makes the keyboard's key search.
  **The station row also shares its width differently once the text is large.**
  The name and the count of free docks sat side by side first-come-first-served,
  and at ×2.0 the indicator and "PLACES LIBRES" between them left the name some
  fifty dp of a 379 dp row — five letters, whatever was done about line limits.
  The row is now a `StationRow`, which moves the count **below** the row's text
  and gives the name the full width. The switch is measured, on the pattern of
  `ToggleRow`: the name may not be left less of the row than everything else on
  it put together, and the name laid out at the width it would actually get may
  not come out with a word cut in two.
  **One threshold stands in front of the first of those two, and it is the only
  one in this work.** The sharing is asked of a reader who has turned the
  system's text size up and of nobody else; the question about a cut word is
  asked at every size, a cut word being a defect rather than a matter of
  comfort. It is not a threshold of the kind SPEC §7 refuses elsewhere — those
  guess at what a measurement can answer — but the answer to a question no
  measurement can put: **who asked for something to change.** Somebody still at
  the normal text size asked for nothing, and a row that rearranges itself under
  their eyes is a regression for them whatever arithmetic produced it. It is
  read from the text size and never from the screen width, and without it the
  sharing rule was measured to move the count below at the normal size on a
  360 dp screen in French and on a 320 dp one in both languages.
  **A line that names one thing is no longer justified or hyphenated.** The
  theme gives both to every text view, on the stated premise that a station name
  fits on one line — a premise these changes end. Justified, "Metropole
  Europeenne de" came out bank to bank and read as three names; hyphenated,
  "Metro-/pole" is not the station's name. The new `Widget.RoueLibre.Name`
  refuses both, which is the argument `Widget.RoueLibre.SettingsLabel` already
  makes for a heading.
  **At the normal text size nothing has moved**, and that is pinned by tests on
  both sides of the threshold rather than asserted: the count keeps its place
  beside the name at ×1.0 on all three screen widths in both languages, and the
  sharing rule is in force at every step above it. `ListTextSizeLayoutTest` lays
  every one of these lines out at the seven steps of the system's slider, in
  English and in French, on 320, 360 and 411 dp, and fails on the previous
  layouts.

- **The settings screen cut its own labels off at the text sizes it exists to
  serve.** Every row of choices in the application laid its buttons out at an
  equal share of the width whatever they had to write, and Android wrote as much
  of a label as fitted the share, followed by an ellipsis. At ×1.3 the screen
  opened on was already "Liste des stat…"; at ×2.0 the theme offered "Syst…"
  against "Som…", the units "Syst…" against "m · k…", and the walking pace "Nor…"
  against "Rapi…". The setting meant to make the screen readable was making it
  less so, which SPEC §7 rules out. **The seven rows are now `ToggleRow`**, which
  wraps a label to two lines inside its button and stacks the row into a column
  only where even two lines cannot hold it — measured on the buttons themselves
  at the width they would get, with an ellipsis, a dropped line and a word cut in
  two all counting as a failure. Their buttons wear a style of their own that
  spends Material's 88 dp minimum width and 24 dp side padding on letters
  instead of air, which is what lets a row of three still write "Système" whole
  at ×2.0. Nothing is written smaller anywhere: **it is the height that gives
  way, never the size of the characters.** Measured on a Fairphone 3, 411 dp
  wide, in French and in English: at ×2.0 only the units row of four stacks, the
  screen opened on takes two lines and stays a row, and every other row is
  unchanged.
- **A heading came out justified once it took two lines.** The theme justifies
  every text view, which shows only on a line that has another after it — so a
  section title or the small capitals naming a setting were untouched until the
  text size doubled and gave them a second line. "WHICH STATIONS THE MAP SHOWS"
  then arrived as four words spread bank to bank, reading as four labels rather
  than one. Headings no longer justify; running text still does, having the lines
  to absorb it.
- **Three yes-or-no settings were still read without their guard.** The two map
  filters go through `readFlag`, which reads a boolean through
  `Preferences.asMap` so the cast can be checked: a settings file holding
  something else under one of those names — written by a version that stored it
  differently, or truncated by a device out of space — gives back the default
  instead of taking the screen down with a `ClassCastException`. The larger
  availability figures, the own-bike switch and the "download on an unmetered
  connection only" setting were still read through their typed key, and each is
  collected by a screen: the indicator on every list, the journey search, the
  storage screen. **The helper now takes the value an unreadable setting means**
  rather than assuming "no": the first two read as false, and downloads still
  **wait for an unmetered connection** when nothing can be read, which is the
  decision of `SPEC.md` §4.4 — read the other way round, an unreadable file
  would send a gigabyte out on a mobile plan. No default changes. Three tests
  cover it, one per setting, and all three threw a `ClassCastException` before
  the change.

- **The availability figure was cut off by the system's own font size.** The
  indicator's figure has always been painted in `sp` and has always followed the
  text size set in Android's settings — but the disc holding it was declared in
  `dp` and did not, and `AvailabilityIndicatorView` paints onto a canvas clipped
  to its own bounds. Raise the font size on the phone and the count grew until
  it ran into its own ring, worst exactly where the figure is worth reading: a
  station of more than a hundred docks. The disc is now declared in `sp` like the
  figure it holds, which is the one box of the token file that follows the text
  scale rather than the screen. At the default scale the two units are the same,
  so nothing moves for anybody who has asked for nothing. The ring keeps its
  `dp`: it is a stroke and not a letter.

- **Seven cities answered to nobody typing their name.** "bialystok" returned
  "No city found" while BIKER — Białystok sat in the catalogue, and so did
  "giessen", "lomza", "wloclawek", "chelm", "jaskolka" and "mlawa"; for Włower
  and ŁoKeR no ASCII typing reached them at all, every word of their label
  beginning with the letter at fault, so they were only ever found by scrolling
  332 rows. `foldForSearch` decomposed and dropped the combining marks, which
  never reaches "ł", "ß" or "ø": those are letters in their own right, not a
  base and an accent. The fold table SPEC.md §4.3 names has existed all along in
  `config/address-normalization/`, read by the indexing script and by the
  address search; **the catalogue and station searches now read it too**, rather
  than a second copy of it. It is gathered across every language shipped, those
  two searches carrying none, and restricted to the letters accent removal
  cannot reach — so Danish "å → aa" stays out and "alborg" goes on finding
  "Ålborg". Both sides of the search fold alike: "gießen" and "giessen" find the
  same city.

- **Bilbao counted 822 docking points where its own figures made 22.** Every one
  of the 65 stations of Bilbao Bizi announced a capacity its own counters
  contradicted by a factor of forty — "ABANDO, 9 bikes, 13 spaces", then "822
  docking points" two lines below. GBFS publishes the figure twice and requires
  the two to agree, and the producer's `capacity` also counts
  `vehicle_types_capacity`, which is not a count of docks at all. **The itemised
  `vehicle_docks_capacity` now wins wherever a document publishes both**, that
  being what the standard defines the field to be: no threshold, no name of a
  city or an operator. Measured against the networks' own live counters, the
  median error of `capacity` is 800 docks at Bilbao, 22 at BiciMAD and 21 at
  Nike, against 0, 2 and 1 for the itemised figure. The 291 networks that
  itemise nothing — Bixi and V'Lille among them — are untouched, and so are the
  37 whose two figures already agree.

- **A station the data does not cover was offered as an ordinary one.** Fifteen
  networks publish at least one station outside the box their datasets are cut
  from: producers' leftovers, like the Hunedoara entry standing in Bucharest 290
  km away, and real stations, like the Namur, Mons, De Panne and Libramont ones
  Blue-bike serves 75 km from the rest. They could be opened, favourited and
  asked for a journey, which could then only answer "no usable route". **They
  are now shown and said to be beyond the data**: the row carries the mention,
  the sheet explains it in full, and the two journey buttons are switched off
  and now look it. Hiding them was refused — six networks would silently lose
  real stations whose waiting bikes the live feed still counts truthfully — and
  widening the box is what `compute_bbox.py` sets strays aside to prevent.
  Handing the station to a navigation application stays offered, that one not
  running on our graph. `SPEC.md` §4 records the choice.

- **The address search announced "nothing matches" while it was still
  searching.** On Vélo Fluo — Strasbourg and its 162,198 streets, typing "Rue
  Pierre de Martimprey" produced "Nothing matches" at 2.3 s and the street
  itself at 5.4 s, with nothing touched in between: the screen spent three
  seconds asserting the opposite of what it was about to answer, and advising
  the user to reword a query that was right. The state raised "searching" only
  when the scan started, and what lies before that is not the 150 ms debounce
  but the wait for the previous scan — `collectLatest` cancels it, yet a scan
  already inside SQLite gives the coroutine back only when it returns.
  **"Searching" is now true from the keystroke onwards**, and the screen shows a
  third state saying so rather than concluding. No fixed delay was added: that
  would have moved the lie rather than removed it. A search that genuinely finds
  nothing still says so at once — 0.2 s on Hunedoara.

- **A station row opened on a separator.** A network publishing neither
  postcode nor position leaves nothing to put before the capacity, and the line
  began mid-sentence — "· 20 docking points" at Rosario, Bilbao and Zürich. The
  capacity now stands on its own there, in the wording the station's own sheet
  uses. Rows that do have somewhere to name are unchanged: "50 m · 36 docking
  points".

- **The address index never said how few numbers it held.** Tokyo's index
  carries 101 house numbers for 7,565 streets and Toyama's 7 for 105, against a
  median of 16.4 per street over the 332 networks; every search there lands on
  the street's representative point, several hundred metres out, which
  `SPEC.md` §4.3 calls unacceptable — and nothing measured or reported it.
  **The indexing script now states the numbers-per-street ratio and warns under
  a floor of one.** It warns and does not refuse: §4.3 already accepts that
  OpenStreetMap's coverage varies, an address in Japan is not built on the
  street, and refusing would leave the catalogue's largest network with no
  address search at all — worse than a street search the application never
  passes off as more, a number it cannot resolve being dropped from the result
  rather than placed wrongly. The floor is a threshold on the data and never on
  a country: nothing names one, and a place whose numbers get mapped rises above
  it without a release.

- **A city announced as unpublished offered its 1.65 GB one tap later.** The
  list said "Data not published yet" of sharedmobility.ch — Zürich, and the
  storage screen reached on touching that very row proposed "Download 1.65 GB";
  nextbike Niederösterreich — Vienna said the same of its 413 MB. The catalogue
  published on 15 August was derived at 11:41 and the two datasets finished at
  11:44 and 12:17: it was older than the data it describes, and
  `publish_data.py` sent it out anyway. **Publishing now refuses a catalogue
  that disagrees with the manifests it would travel with** — a different size, a
  different release tag, or an entry carrying none while the manifest exists —
  names the cities and the command that re-derives it, and stops before a single
  byte goes out. The check runs after the manifests are stamped, that stamping
  being itself one of the ways the catalogue falls behind. All 332 entries now
  agree with their manifest.

  **The application no longer draws a conclusion the catalogue cannot support
  either.** A missing size said "Data not published yet", which is a claim about
  the world; all the catalogue knows is that it carries no size, and it is
  published apart from the manifests and can lag behind them. The row now says
  "Size announced before downloading" — with the station count, which the old
  wording swallowed — and the storage screen, which does fetch the manifest,
  announces it. Both screens say the same thing, and the size still comes before
  the download as `SPEC.md` §4.4 and §11.9 require. The proposal made on the
  application's own account — the nearby city offered on the map — stays limited
  to cities the catalogue vouches for: an interruption is worth making only when
  the weight can be named. `CityEntry.isAvailable` is renamed
  `hasAnnouncedSize`, which is what it tests.

- **A server whose certificate could not be trusted was announced as a feed
  publishing rubbish.** sharedmobility.ch let its own certificate expire on
  15 August 2026 and Zürich lost its stations behind "the data received is
  unreadable. The network's feed is at fault" — an accusation aimed at a
  producer whose data had not reached us by a single byte. A TLS failure is an
  `IOException` like any other, and it fell into the generic case that names the
  content at fault. `DataError.UntrustedServer` now says what happened: the
  server could not prove who it is, nothing was fetched, and the operator has to
  renew its certificate — there being nothing for the user to do, and nothing
  for us to do either short of accepting an expired certificate, which is out of
  the question. Caught in the three places the application calls the network:
  the feeds, the release manifest, and the dataset transfer, the last two of
  which said "unreadable manifest" and "interrupted transfer" on the same
  failure. The technical detail stays in the value, for the log and the bug
  report, as it does for a malformed response.

- **Rosario showed no station, ever, over four characters in its producer's
  feed.** Mi Bici Tu Bici serves its auto-discovery document over `https://` and
  names its four feeds over `http://` — the very same paths, which answer
  perfectly in TLS and which its own server redirects there. The application
  permits no cleartext traffic, so Android refused the call before it left the
  device and the network's 101 stations were unreachable. Every request now goes
  out in TLS whatever scheme its address carried, in one interceptor on the
  shared HTTP client: a cleartext address is a certain failure, so rewriting it
  can only turn that certainty into a chance, and no network that works today
  can be broken by it. The answer sits there rather than in the city
  configuration because nothing specific to a city is hard-coded (`SPEC.md` §15)
  — Rosario was the only one of the three hundred and thirty-three networks
  served in that case, and the next producer to publish the same typo is served
  without a release. Opening cleartext traffic for that one network was refused:
  it would lower the whole application's guarantee to accommodate a producer who
  already serves TLS correctly. Washington's attribution link, the last
  cleartext address left in the shipped configuration, was moved to `https://`
  with it — it is opened in a browser and no interceptor of ours sees it.

- **Most of the cities were given a routing graph with no elevation, and their
  journeys therefore named no climb.** The graph's elevation comes from SRTM
  readings converted into the 5°×5° tiles BRouter names them by, and the
  conversion was skipped whenever a tile of that name was already in the cache —
  though what such a tile holds is only the readings the *first* city of that
  square had downloaded. Paris shares its square with Lyon, three degrees of
  latitude to the south, and the square was converted for Lyon before Paris had
  downloaded a reading of its own: the Paris graph carried no elevation at all,
  and the ride up to Montmartre was announced flat, while
  Lille — the first city of its own square — was served correctly and hid the
  fault. `tools/build_routing.py` now reconverts a tile older than a reading it
  needs, and converts every tile the box reaches rather than the single one its
  north-west corner falls in: thirty-nine of the conurbations served straddle
  one of those lines and were flat on the far side of it. Two hundred and forty
  of the three hundred and thirty-three graphs published are missing some or all
  of their elevation and are to be regenerated; Paris has been.

- **"Locate me" from another city moved the point to a street the user was a
  hundred kilometres from.** The camera is penned inside the served city's
  bounding box, so a position outside it did not stop the move: it was clamped
  to the nearest edge, and the map settled there without a word — the button
  appeared to have worked, on a wrong point. The map now compares the position
  it obtains against the city's box, and says the position is off this map
  rather than framing anything. It comes with the way out: the network of the
  conurbation one is actually in, when the catalogue on the device knows one
  whose data is published — the same offer §15.1 makes on opening, asked for
  this time rather than volunteered, hence not held back by its once-per-session
  restraint — and the city list otherwise. While the map is being used to
  designate a journey's endpoint it only says so, a change of city there
  throwing away what was being composed. The journey screen's own "locate me"
  button (§7.4) was clamping the same way and now says the same thing, without
  the offer: it shows one journey in one conurbation. `SPEC.md` §7.1 and §7.4
  record it.

- **Two cities were named after a neighbour.** The city list offered a "Donkey
  Republic — Rotterdam" that served Katwijk, sixty kilometres away, and a
  "Donkey — Dordrecht" that served Gorinchem while the real Dordrecht sat two
  rows below under another network. Neither name came from the producer: the
  survey reads the main city off the gazetteer over the box widened by 3 km,
  and the largest municipality that box reaches wins — which is right for a
  network named after a region and wrong for one whose outskirts brush a
  metropolis. The GBFS registry names both feeds after the town they serve, and
  the configurations now say the same. Their identifiers keep the name they
  were published under: an identifier names a dataset and its manifest, not a
  place, and renaming one would orphan the data already installed.

- **One letter missing from the fonts blanked a whole town's map.** Hunedoara
  came up empty — no street, no river, no park — over a tile set that held
  1,120 roads at the very spot the camera was on. The cause was two lines in
  the log: `Failed to load glyph range 512-767`. The Romanian Ș and Ț live in
  that range, the APK shipped only Latin, Latin Extended-A and punctuation, and
  MapLibre does not skip the character it cannot draw: the tile whose label
  needed the range never finishes its layout, and nothing of it is drawn. The
  generator now produces every range of the Basic Multilingual Plane, which
  costs 230 kB because a range the font does not cover answers in forty-four
  bytes — what the font cannot draw stays blank, which is what a missing
  character was always supposed to cost. Nine of the networks served wrote
  names in that range, and thirty more used one of the other absent ranges,
  Greek, Cyrillic and Japanese included.

- **A URI escape carries a byte, not a character.** Percent-escaped UTF-8 was
  decoded one escape at a time, so "é" — which travels as `%C3%A9` — arrived as
  two characters: a place shared as "rue de l'Hôpital" reached the address
  search as "rue de l'HÃ´pital". The escapes of a run are now gathered and
  decoded as UTF-8 in one go. The test covering this had been written around
  the bug, replacing the escape with the letter it stood for.

- **The journey arrives already framed.** The track was laid in the bottom of
  the map, a third of it showing open country above, and the framing corrected
  itself as soon as the detail was opened or closed. Two accidents, both in the
  camera limit that keeps the served area's edge off the screen (`SPEC.md`
  §7.1). The box penning the camera's target was measured for the framing being
  left — the map fills the screen while the journey is worked out, and at that
  size the viewport is taller than the whole served area, so the box collapsed
  onto a single latitude and the move that followed was clamped to it. And the
  framing move MapLibre makes for us is reported back from inside itself, at a
  point where the map's projection already answers for the new position while
  `cameraPosition` still returns the old one: the limit recomputed there came
  from no framing at all — one reach negative, the other two and a half times
  its true value — and yanked the camera onto the edge of a box that meant
  nothing. Measured on the marker of the departure station, Lille → Villeneuve
  d'Ascq: 492 px from the top of the map at first display against 334 px
  afterwards, 60 dp too low. The limits now stand down for the length of the
  framing move and are measured again where it lands, as the map screen already
  did for the address it flies to; only the zoom floor is brought up to date
  beforehand, since lifting that is what would uncover the edge. A journey
  north-south, which the limit clamps for real, gained more than the reported
  one: it arrived with the track all but off the screen. Verified on a
  Fairphone FP3 — first display, recomputation, rotation there and back, and
  detail folded and unfolded now give the same framing to the pixel, on a short
  trip, an east-west one, a north-south one, and one crossing the conurbation
  end to end.
- **A long trip no longer answers with four hours of walking.** How far one may
  walk to a station was capped at 1 200 m. Tourcoing → Wattignies, 17 km across
  the Lille conurbation, has a V'lille station 203 m from the departure point,
  but the nearest one to the arrival — Recherche — stands 2 363 m off. It was
  discarded, no pair of stations remained, and the application fell back on the
  direct walk: 19.7 km, three hours fifty-four, "no bike journey is possible
  here" — a breach of `SPEC.md` §11.4. **The cap is gone**: a station is a
  candidate however far it stands. No threshold on the access walk can tell the
  trip where twenty minutes on foot are worth it from the trip where they are
  not, since it never sees how long the journey is.
- **What guards against an absurd access walk is now the comparison with
  walking, and it is made at every distance.** It was only made under 3 km,
  which was harmless while the cap kept far-fetched pairs out and would not be
  any more. Beyond that distance the direct walk is traced after the fact, and
  only when the journey found fails to beat the straight line covered at
  1.8 m/s — a pace no walker holds, so anything quicker beats every real walk
  and needs no leg traced. One route computation added at worst, on journeys
  where walking might still win (`SPEC.md` §6).
- **A superscript in a house number no longer costs a city its index.**
  `tools/build_address_index.py` read the leading digits of an OpenStreetMap
  house number with `str.isdigit()`, which answers true for `²` and `³` —
  characters `int()` then refuses. Karlsruhe writes "23²", so the whole
  generation of that city stopped there. Read with `str.isdecimal()` instead,
  the superscript becomes what it is, a repetition mark beside the number 23,
  and the Arabic-Indic digits an address base outside Europe may hold keep
  working, since everything `isdecimal` accepts `int()` parses.

### Removed

- **The pick-up and drop-off time**, setting and allowance both (SPEC §6,
  §7.6). The same three minutes were added to every pair, so they never
  decided which one won; they only inflated the time announced and asked the
  user to tune a figure they could not measure. The journey now announces
  what the routing engine traced, walk, ride and walk. Two effects follow:
  every journey reads about three minutes shorter than before, and the bike
  wins against walking on slightly shorter trips. The stored values are left
  on disk, unread — nothing is written to remove them.

- The ten bitmap launcher icons of `mipmap-*dpi`, 48 kB no device could
  reach: with `minSdk` 26 the adaptive icon of `mipmap-anydpi` answers at
  every density, and the grid drawn as `ic_launcher_background` went with
  them.

### Added

- **Datasets wait for a connection nobody is billed for** (SPEC §4.4, §7.6).
  A city weighs from a few megabytes to 1.3 GB — Vélo Fluo's base map alone is
  1 343 MB — and until now nothing stopped that from leaving on the mobile plan
  of somebody who had not thought about it. A switch in the offline data section
  of the settings, **on by default**, because a gigabyte spent unasked costs
  more than a download put off for an hour.
  **It reads the billing, not the transport.** Android answers what a network
  charges for (`NET_CAPABILITY_NOT_METERED`), which is the truer notion here: a
  phone's shared connection is Wi-Fi and is a mobile plan, and a capped hotel
  Wi-Fi declares itself billed. No permission was added — `ACCESS_NETWORK_STATE`
  was already declared — and no dependency either.
  **It is never a dead end.** Every time the setting holds a transfer back, the
  screen says why, names what the transfer weighs, and offers to run it anyway:
  somebody in a hotel with no Wi-Fi has to be able to install their city. That
  agreement covers the one transfer and leaves the setting alone, so the next
  download asks again. A connection that starts billing **in the middle** of a
  file stops the transfer where it stands rather than finishing it in silence,
  and the resumption asks the server for the remainder from the offset reached.
  Nothing starts again from the background — §4.1 rules that out — so the return
  of an unbilled connection is announced on the screen the user is looking at.
  The manifest check, a few kilobytes, is never held back: what the setting
  governs is the datasets, not every request that leaves.

### Fixed

- **A transfer given up ran to its end anyway.** `DatasetDownloader` copied its
  buffers without ever looking at the coroutine it was running in, and a read
  blocks, so cancellation could not reach the loop: a download nobody wanted any
  more went on to the last byte of a gigabyte. It now checks between buffers,
  and what has arrived stays in the partial file for the resumption to pick up.
  Nothing depended on this before — nothing cancelled a download — and the
  setting above is what made it matter.

## [0.3.0-alpha]

The version that publishes its data. The three sets of 101 conurbations — the
seventy French ones and thirty-one others — are downloadable from
[RoueLibre-data](https://github.com/mgdx/RoueLibre-data/releases), so the
application no longer asks anyone to run the repository's scripts before it can
show a map. It stays an alpha for one reason: those datasets were generated in
bulk and verified by their digests, not walked over.

### Added

- **Every bike-share network in the world that publishes its stations** — 306
  of them, in 35 countries, against 69 in France alone. The survey that found
  the French ones now reads the whole GBFS registry, calls the sixteen hundred
  feeds it lists, and judges each on what its feed answered. Nothing about the
  application had to change for it: `SPEC.md` §15 asked that serving another
  conurbation be a configuration file, and this is that promise spent a second
  time, on Prague, New York, Barcelona, Tokyo, Buenos Aires and Pristina.
  - `tools/discover_networks.py` is no longer French. It reads MobilityData's
    `systems.csv` — the registry the GBFS standard keeps of itself, and the
    only catalogue covering every country — beside France's national access
    point and the hand-checked addresses of `config/extra-feeds.json`. The
    eligibility rules are unchanged, and they still do most of the work: of
    1,531 distinct systems, 306 pass.
  - Two public datasets replace what was a table of French regions. Geofabrik's
    extract index answers "which extract covers this box" by testing the box
    against the extracts' own geometry, so the answer holds for Auckland as
    well as for Amiens — and an extract that is an ancestor of another already
    chosen is dropped rather than downloaded beside it. The GeoNames gazetteer
    names the municipalities the stations stand in, in any country.
  - The report says which municipalities each network covers besides the town
    it is named after. A bike-share network belongs to an agglomeration, and
    the reference box is derived from its stations precisely so the
    neighbouring towns fall inside it; naming them is how the page shows they
    were not forgotten. It also fixed a real omission: Ecobici was named after
    a borough of Mexico City, whose own point lies three hundred metres outside
    the rectangle its stations enclose.
  - The list, rejections and their reasons included, moved from
    `docs/networks-france.md` to [`docs/networks.md`](docs/networks.md), and is
    grouped by country.

- **A station standing alone is no longer part of the box.** The rectangle is
  derived from the stations, and one station in the wrong place carries it
  away: Valenbisi publishes a "LABMAD" three hundred kilometres from Valencia,
  in Madrid, which made its box 33,645 km² instead of 150 and had the network
  set aside as "one feed for a whole region". A station more than 25 km from
  every other is now dropped from the box — named in the log, never swallowed —
  which is the same treatment the latitude-zero station already had. Recorded
  in `SPEC.md` §4, whose first step said "every station".

- **The address index is no longer French.** `SPEC.md` §15 carried a caveat
  since the first line of this project: the Base Adresse Nationale is French,
  and a foreign city would have to have its index rebuilt from OpenStreetMap.
  It now does. `tools/build_address_index.py` reads the named ways and the
  `addr:housenumber` objects of the extract the map and the routing graph are
  already cut from — one download instead of two — and names the streets that
  carry no municipality after the nearest inhabited place. France keeps the
  BAN, which is finer there; the configuration says which applies, in
  `dataSources.addressSource`, and `tools/generate_all.sh` follows it.

- **Street-name normalisation, one file per language.** A street type is a word
  of a language: "rue" says nothing about a Warsaw address, where the word is
  *ulica* and the abbreviation *ul.*, and no accent removal folds the ß that
  half of Germany types as "ss". `config/address_normalization.json` became
  `config/address-normalization/<language>.json`, thirty-three of them, and
  §15.1's requirement that those rules travel with a city's data is finally
  met: the index records the language it was built with, and the application
  reads it back from there rather than deciding for itself — so an index and a
  rule set can never be paired wrongly. A language with no file falls back on
  English, and `reference-<language>.json` in the test fixtures proves each new
  file the day it lands rather than the day a city speaking it is generated.
  - New in the rules: `letterReplacements`, for the letters accent removal
    cannot reach because they are not accented letters — ß, ø, ł, đ, þ, the
    Greek final sigma, the Turkish dotless ı. Applied identically on both sides
    of the search, in Kotlin and in Python.

- **Twenty-nine started translations**, against eight: one for every language
  spoken where a network is served — Albanian, Basque, Bosnian, Catalan,
  Croatian, Czech, Danish, Finnish, Galician, Hungarian, Japanese, Latvian,
  Lithuanian, Norwegian, Romanian, Serbian, Slovak, Slovene, Swedish, Turkish — each holding the English text
  until somebody translates it. Arriving in Ljubljana with the application must
  mean arriving in a language somebody can finish, not in a folder somebody
  must create.

- **A city configuration carries its country** (ISO 3166-1 alpha-2) and the
  language its streets are named in. The catalogue groups on the first; the
  index is built with the second. A feed declaring English in Nantes has
  declared a default, not a fact about its street names, and twenty-five French
  networks do exactly that: a declared language is followed only where it names
  one of the country's own — which is how Barcelona is served in Catalan and
  Bilbao in Basque.

- **Every French bike-share network that publishes its stations** — 69 of them,
  against three. `SPEC.md` §15 asks that serving another conurbation be a
  configuration file and never a code change; this is that promise spent.
  - `tools/discover_networks.py` reads the two catalogues §4.1 accepts — the
    national access point and MobilityData's `systems.csv` — **calls every
    address they publish**, and judges each network on what its feed answered.
    The eligibility rules are the application's own: stations with real docks,
    a fleet holding bicycles and no car, at least ten stations so §6 has a pair
    to optimise, a box small enough to be a conurbation, and no key to hold.
    Of 269 distinct French systems, 69 pass; the 200 others are free-floating
    scooters, car-sharing, or parking areas published as stations.
  - The reasoned list, **rejections and their reasons included**, is
    [`docs/networks-france.md`](docs/networks-france.md).
  - `tools/add_city.py` writes a configuration from a surveyed network:
    verified feed address, network and authority names, licence, reference box
    recomputed against the live feed, opening framing. It leaves an existing
    configuration alone — the first three were settled by hand.
  - The catalogue now lists a city whose data is not generated yet, saying so.
    The interface already handled that case; nothing had exercised it.

- **A city configuration says where its data is cut from.** A new
  `dataSources` block carries the OpenStreetMap extracts and the Base Adresse
  Nationale departments the reference box reaches, both read from the stations
  rather than from an administrative boundary — Vélib's box spans eight
  departments, Avignon's three. `tools/generate_all.sh` reads them, so
  generating a conurbation is `--city` and nothing else, and merges the
  extracts where a box straddles two of Geofabrik's regions.

- **The journey screen draws its four points.** The track said the shape of a
  journey but not where it changes mode: the two stations now carry the filled
  bike disc of the search screen's illustration, and the two ends the outlined
  walking one. The same drawing on both screens, so the journey is recognised
  from one to the other.

- **The position moves on the journey map.** A disc follows the device for as
  long as the screen is open, above every other marker: where one *is* beats
  what one has planned, and the two coincide at the start of a journey. Read
  from the system only if the permission is already granted — this screen asks
  for nothing (`SPEC.md` §10) — and written nowhere.

- **A search field over the catalogue of cities.** Sixty-nine networks make a
  list one scrolls rather than reads. The search folds accents and the
  apostrophes networks write and nobody types: "velov" finds Vélo'v, "vlille"
  finds V'Lille (`core/config/CitySearch.kt`, tested on the JVM).

- **The network of the conurbation one is in, offered on opening.** Someone who
  travels arrives with another city's data installed, on a blank map that says
  nothing about the network under their feet. The application now offers it —
  once per session, refusable, and only from a position the system already
  holds: no permission is requested, no fix asked for, and no request goes out
  (the catalogue shipped in the APK answers). See `SPEC.md` §15.1.

- **Something to watch while the journey is worked out.** The spinner over a
  half-drawn map is replaced by a screen of its own: the bike of the stations
  crosses from one edge to the other, comes back along a higher line facing the
  way it is going, and goes round again, under "Working out the best journey".
  It is the only moment the application makes anybody wait — three seconds at
  most (`SPEC.md` §6) — and a turning circle said nothing about what was
  happening. Motion in the service of understanding, which is the only kind
  §7 accepts: a device asking for reduced animations gets the drawing still.

### Changed

- **The base map has a ceiling, and it is 200 MB.** `SPEC.md` said two things
  at once about the weight of the downloaded data: §4.2 announced "30 to 60 MB"
  for a medium-sized conurbation, and §11.9 that the data "has no fixed
  ceiling". Neither told anyone what to do about Paris. The figure is now
  written down and it applies to the base map alone, the heaviest of the three
  sets: under 200 MB no city is refused, over it the layer to pull is the
  building footprints — for every city at once, not for the offending one.
  Vélib' passes, at 114.9 MB of tiles inside a box holding 1.24 million
  building footprints against 78,000 for Lille. Measured over the seventy
  French networks generated the same day: median 10.9 MB for the three sets
  together, 3.2 MB for Auray, 143.0 MB for Paris, and Paris alone above 60 MB.

- **The map stops at the edge of what was downloaded, without showing it.** The
  camera is penned inside the reference bounding box of `SPEC.md` §4: it no
  longer zooms out past that box covering the screen, and it no longer pans
  close enough to an edge for the emptiness beyond to appear. Roads cut off in
  mid-air and a straight line of nothing across the screen read as a coastline,
  never as the end of a download. Both limits are measured off the visible
  region on every camera move, so they follow the zoom, the shape of the window
  and the screen's rotation; the arithmetic is in `core`, on the JVM, and the
  map screen and the journey's map share the same `ServedAreaCamera`. On a
  conurbation smaller than a screenful, the widest zoom is now the one framing
  the box, whatever `minZoom` the city configuration allows.

- **The journey's two ends stay on the result screen, and can be corrected
  there.** The fields of `SPEC.md` §7.3 and their swap button now head the
  result: a mistaken address, a departure one would rather take from the other
  end of the street, the way back — a press, and the journey is worked out
  again, instead of a way back to the previous screen and everything filled in
  a second time. The four ways of designating a point moved into a
  `JourneyEndpointPicker` the two screens share, rather than being written
  twice.

- **The detail is laid against the bottom edge of the screen.** It was the top
  of a scrolling area as tall as what was left, so a short journey left a band
  of nothing between the recompute button and the edge of the screen. It now
  hangs from the bottom, the map takes every pixel it leaves, and a detail too
  long to fit scrolls inside itself rather than eating the map.

- **A "locate me" button on the result's map.** Bottom right, where the thumb
  is: it brings the framing down onto the walker at the closest zoom the tiles
  allow, which is what tells the next street corner apart on a map framed on a
  whole journey. It is the only thing on that screen asking for the location
  permission, and only when pressed (`SPEC.md` §10).

- **One journey, the one proved best.** The list of runner-up station pairs is
  gone, and with it the choice it handed back to the user: the risk penalty of
  `SPEC.md` §6 already weighs a well-stocked station against a nearer one, and
  showing four candidates asked them to arbitrate that on figures they cannot
  weigh better than the algorithm. The availability of the two stations chosen
  stays on screen, which is what lets them judge the risk. `JourneyPlan.Found`
  no longer carries alternatives, and the planner stops computing extra pairs
  to fill a list nobody reads — it computes only what could still beat the best.

- **The shape of the journey replaces that list.** The drawing of the search
  screen, carrying the journey actually computed: a disc per station and per
  end, a dotted stroke per walk, an unbroken one for the ride, and under each
  stroke how far it runs, in metres or kilometres.

- **A journey's field opens the address search straight away.** The sheet of
  four ways in between is gone: one nearly always knows the address, and the
  three other ways of `SPEC.md` §7.3 — one's position first, always — now head
  the result list, a press away and no further than they were.

- **Street-name normalisation covers France, not Lille.** The shared rules
  (`config/address_normalization.json`) gained the DGFiP's way-type codes as
  the address base actually writes them — `ALL`, `CHE`, `MTE`, `RLE`, `LD`,
  `TRA`, `PRV`, `VLGE` and their kin — and the vocabulary of the regions now
  served: *traverse* and *vallon* in Marseille, *montée* and *traboule* in
  Lyon, *venelle* and *hent* in Brittany, *cavée* in Normandy, *carriera* and
  *cami* in the Occitan south, *ravine*, *morne* and *habitation* in Guadeloupe
  and Réunion. A region whose vocabulary is missing loses the type/name split,
  and with it the ability to find a street by its proper name alone.
  `tools/refresh_normalization_fixtures.py` recomputes the reference cases the
  Kotlin test replays, without rebuilding an index.

### Added

- **The map asks for the location permission when it opens**, where nothing was
  asked before a press of "locate me". The point that follows the device is
  what the screen is for, and reaching it through a button first is a detour.
  Once per session and never again once refused — the button is what remains to
  change one's mind — and the map stays whole without it. `SPEC.md` §10 said
  "at the moment of use, never at launch"; the map being the launch screen,
  that paragraph is now written as the two moments it really is.
- **The point follows again when the permission is granted from the Android
  settings**, rather than only after a return through the button: the
  subscription is retried when the map comes back to the foreground.
- **The user's point follows the device on the map, in real time.** It only
  moved on a press of "locate me" until now, which on a walk meant a point
  standing a street behind. The framing stays the user's: recentring at every
  fix would take the map back from under someone looking further on, and
  "locate me" is what brings it back to the point. Nothing is asked for at
  opening — the following only starts if the permission is already granted, or
  from the moment the button obtains it — and the subscription stops with the
  screen. `SPEC.md` §7.1 says so now.

### Fixed

- **A source half downloaded was kept, and reused, as if it were whole.**
  `tools/generate_all.sh` wrote each download straight to its final name, so a
  transfer that died in mid-body left a truncated file that the "already
  present" test took for a complete one; every later run reused it and failed
  three steps away, in whatever tried to read it, until someone deleted it by
  hand. It also trusted `curl --retry`, which replays timeouts, refused
  connections, 429 and 5xx — not a connection dropped after a 200, which is
  exactly what `adresse.data.gouv.fr` does about one request in three. Sources
  are now fetched under a temporary name, checked for what they claim to be
  (`gzip -t`, `osmium fileinfo`) and only then renamed, with
  `--retry-all-errors` and, for that host alone, HTTP/1.1. Geofabrik served 164
  extracts over HTTP/2 without a failure and is left as it was.

- **Two extracts of different days are no longer merged into an unusable
  file.** A reference box straddling two Geofabrik regions needs both, and the
  same node cut from two daily snapshots comes with two versions; `osmium
  merge` keeps both, and every step downstream stops at "Node ID twice in
  input". That is what killed Saint-Étienne's generation, whose box reaches
  from Rhône-Alpes into Auvergne. The script now compares the snapshots before
  merging and says what to do about it.

- **A city could be given the map of the city generated before it.**
  `tools/build_tiles.py` reused the cut of the reference box whenever the file
  was there, testing nothing but its existence, and every network wrote that
  file to the same `data/work/tiles/`. The clean-up runs only on success, so a
  run interrupted after the cut left its own behind for the next city, which
  took it for its own. What followed was built from the wrong conurbation's
  data and clipped to a box it does not describe. Measured on Nantes, whose two
  networks overlap by 72.5 %: the second came out at 1,914 tiles and 12.5 MB
  instead of 2,117 and 18.7 MB — a map missing everything north of its
  neighbour's box, with no error raised, a plausible size and a manifest whose
  digest matched the file it described. The cut now carries what it was made
  of, source extract and box, and is reused only against a match; each network
  also gets a working directory of its own. Found by generating the seventy
  French conurbations in one run, where Saint-Étienne died at its cut and left
  the three cities behind it to fail on an empty one — the loud half of the
  same defect.

- **"My position" looked like it did nothing.** Choosing it closed the address
  search and left the field on its old label for as long as the fix took — up
  to the ten seconds `DeviceLocation` waits for one, indoors. Nothing said a
  search was under way, so the press read as lost, and one pressed again. The
  field now carries the wait ("Finding your position…") and goes back to what
  it said, whether a point was found or the search failed — in which case the
  message explaining it was already there, only nobody had waited for it.

- **The point showing the user stayed where it was while the device moved on.**
  Two causes, both in `DeviceLocation`. Every provider is listened to at once,
  as it must be — the satellites stay silent indoors, where the network answers
  in a second — but the answers were taken in the order they arrived: the
  network's always came first, several hundred metres wide and identical from
  one street to the next, and it landed on top of the satellite fix that was
  actually following the walk. Fixes are now arbitrated on accuracy
  (`core.geo.PositionFix`), and a coarse one only replaces a precise one once
  the latter has aged half a minute, which is what unfreezes the point when the
  satellites do go silent. And "locate me" answered out of the cache of
  positions already known, up to two minutes old — where the user *was*, a
  hundred metres back on foot and six hundred by bike. It now asks for a fix,
  leaves a coarse first answer four seconds to be beaten by a precise one, and
  only falls back on the position already known if nothing arrives at all.

- **A station at latitude zero no longer stretches a city's data across the
  Atlantic.** Naolib publishes one; the Nantes bounding box measured 888,100 km²
  instead of 150, and the three datasets §4 cuts from that box would have
  followed. Positions outside the world, or within a hundred metres of Null
  Island, are now ignored and counted out loud.
- Non-breaking spaces are normalised as word breaks. Python treats them as
  whitespace and Kotlin's `\s` does not: a street name holding one was indexed
  as two words and searched as one.

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
- **A page credits the feed producer of every city served**, reached from
  "about". That screen keeps the credit of the city being served; the others,
  including cities not installed, are one labelled tap away, each with the
  address its feed is published at.
- **Eight languages are started** — Arabic, German, Spanish, Italian, Dutch,
  Polish, Portuguese, Chinese. Their files hold the English text until somebody
  translates them, so contributing a translation means editing a file rather
  than creating one. Arabic carries the six plural categories it needs, Chinese
  the single one it uses. Cost: 69 kB of APK, all eight together.
- **The interface speaks English by default.** `values/` holds English —
  what Android serves when no translation matches — and French moved to
  `values-fr/`, a translation like the others, kept complete. An application
  that serves whatever city publishes its data had no business announcing one
  country in its interface.
- **The release notes follow the language displayed.** The F-Droid metadata
  gained an `en-US` folder, the default the store falls back on, and the
  "what's new" screen reads the notes of the language it is speaking rather
  than the French ones whatever happens.
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
