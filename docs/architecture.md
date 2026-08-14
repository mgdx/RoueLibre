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

**Error handling.** No exception crosses a layer boundary. Failures are values —
`Outcome.Failure(DataError.Offline)` — and the only layer that puts them into
words is the interface. The business module is not allowed to hold a
displayable string.

The reasons behind these choices — the absence of Jetpack Compose, the manual
dependency container, the refusal of commercial points of interest on the map —
are argued in [`SPEC.md`](../SPEC.md) §14.
