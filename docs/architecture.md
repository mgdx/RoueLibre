# Architecture

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
│                FleetRepository — what the city lends        │
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
│              of the data, counting what a network lends     │
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

**What the city lends** follows that same flow rather than sitting beside it.
`StationRepository` has both halves in hand on every refresh — the bikes standing
at the stations, and the table saying what each vehicle type identifier is — so
it counts them there and reports the reading; `FleetRepository` decides what to
keep and emits it as a stream the screens collect. The counting itself is a pure
function in `:core`, which is why the rules it encodes — the two-percent floor,
the fall back on the declaration when nothing is out — are held by JVM tests
rather than by a device. The city configuration seeds the answer and no longer
settles it: see [`offline-data.md`](offline-data.md) and `SPEC.md` §4.1.

## Where the availability comes from

There is no Roue Libre server between the phone and the stations. The live
availability is read from the GBFS feed the network publishes itself, and the
chain that leads to it holds no address of ours.

The city configuration carries a single address, the auto-discovery document
`gbfs.json`. Everything else — `station_information`, `station_status`,
`vehicle_types` — is read from that document rather than from a constant, which
is the principle of GBFS and what keeps a feed moved on the producer's side from
breaking a release. The whole chain is three files: `CityCatalogueSource`, which
gives the city its configuration, `GbfsRemoteSource`, which fetches, and
`StationRepository`, which decides when.

Those addresses are the operators' own: `gbfs.nextbike.net` for the 128 nextbike
networks, `stables.donkey.bike` for Donkey Republic's 40, the 40
`*.publicbikesystem.net` of PBSC, `api.gbfs.v3.0.ecovelo.mobi`,
`api.cyclocity.fr` for JCDecaux, `*.fifteen.eu`, Beryl's two domains,
`gbfs.urbansharing.com`, `velib-metropole-opendata.smovengo.cloud`, and one
domain per network for the rest.

Four networks are read at an address that is not the operator's own, because
that is where the network publishes and there is no more direct source to aim
at: Limoges through the proxy of `transport.data.gouv.fr`, Rennes on the
authority's Opendatasoft portal, Tokyo through ODPT, and Blue-bike on De Lijn's
API.

Two other things do come from an address of ours, and neither carries a station:
the city catalogue and the map, routing and address datasets, published as
releases of `RoueLibre-data`. The catalogue is only downloaded when the city list
is opened, the datasets once per city and only on a press. **Those two addresses
are not settings**: they are shipped in the APK, and what stands in for them is a
file imported by hand rather than a host typed in — see
[`offline-data.md`](offline-data.md#hosting-the-data-yourself) and `SPEC.md` §9.

Everything goes out over TLS. An address in cleartext — a producer's typo in an
auto-discovery document — is rewritten to `https://` by `HttpsOnlyInterceptor`
rather than failing, since the application declares no cleartext exception. The
`User-Agent` names the application and its version, and nothing else: no
identifier of the device or of the user.

Nothing is fetched in the background. Every request comes from a screen being
shown or from a gesture: at most one state refresh a minute, one static refresh a
day, and pull-to-refresh forces the first.

## What the operator sees

Reading the feed at the operator's own address has a cost, and it is the honest
counterpart of having no server of ours in between: **the operator sees the
phone's IP address and the time of each request**, as any web server does of any
visitor. Roue Libre sends nothing else — no identifier, no account, no position,
no destination, and a `User-Agent` naming the application and its version and
nothing more — so what is on offer is an address and a clock, not a person and
not a journey. The same holds for the host of the catalogue and of the datasets,
which is read at most a handful of times in the life of an installation.

Put a server of ours in the middle and the operator would see us instead of the
user — and we would then see every user, which is worse: an intermediary that
knows which city each phone is watching, and when. That is the trade, and this
side of it is the one we would rather take. Somebody who does not want the
operator to see their address can put the application behind a VPN or Tor like
any other, and the map, the routing and the address search go on working with no
network at all.

**Error handling.** No exception crosses a layer boundary. Failures are values —
`Outcome.Failure(DataError.Offline)` — and the only layer that puts them into
words is the interface. The business module is not allowed to hold a
displayable string.

The reasons behind these choices — the absence of Jetpack Compose, the manual
dependency container, the refusal of commercial points of interest on the map —
are argued in [`SPEC.md`](../SPEC.md) §14.
