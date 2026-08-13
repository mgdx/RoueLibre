# CLAUDE.md

## Before anything else

**Read `SPEC.md` at the root of the repository.** It is the complete
specification and the project's source of truth. This file is only an
operational reminder: where the two disagree, `SPEC.md` wins.

## The project in one sentence

**Roue Libre** — a free Android application showing bike-share stations on a
map and computing a door-to-door walk → bike → walk journey. It serves any
conurbation whose network publishes its stations as open data in GBFS, several
of them side by side, and none by default. Everything works offline except
real-time availability.

## Absolute rules

1. **No Google services.** No Play Services, no Firebase, no FCM, no Maps SDK,
   no Crashlytics. The application must run on any Android OS without Google
   services.
2. **No telemetry, no tracker, no unique identifier.** No journey data is kept.
3. **Offline by default.** Map, address search and route computation all run on
   the device. Only the GBFS feed goes out on the network.
4. **Lightness.** APK under 15 MB. Every dependency added must be justified in
   `docs/dependencies.md`.
5. **Nothing specific to a city hard-coded**: URLs, bounding box, centring and
   network name all live in the city configuration. See `SPEC.md` §15.
6. **Not a single hard-coded string.** Everything in `res/values/strings.xml`,
   English by default, `plurals` for agreement, positional placeholders. Other
   languages are translations, `res/values-fr/` included.
7. **GPLv3.** Check licence compatibility before adding a dependency.

## Coding conventions

See `SPEC.md` §14 for the detail. In short:

- Kotlin, XML views + ViewBinding, **no Compose**. minSdk 26.
- Business logic in pure Kotlin, no Android imports, testable on the JVM.
- Comments explain the **why**, never the **what**. Every coefficient of the
  journey algorithm must be justified.
- KDoc on everything public. Explicit English naming, no abbreviations.
- Short functions, single responsibility. No dead code, no premature
  abstraction.
- Atomic commits, messages describing intent.

## Commands

```bash
./gradlew assembleDebug        # build
./gradlew test                 # JVM unit tests
./gradlew lint                 # static analysis
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Build and run the tests after every significant change. Do not hand me code
that does not compile.

## Ask me before doing

- Adding a dependency that is not listed in `SPEC.md` §3.
- Departing from a decision already settled in `SPEC.md` — those were settled
  after discussion, not by default.
- Changing the data schema or the format of the downloaded files.
- Applying a visual style to the whole interface: submit the design tokens and
  **a single screen** as a screenshot first.

## Never do

- Silently work around a constraint from `SPEC.md` §2. If one blocks a feature,
  say so and propose an alternative.
- Hard-code a URL, a coordinate or a key.
- Invent a data feed URL: verify it with a real request before writing it into
  the code.
- Add a feature listed as out of scope (`SPEC.md` §13).

## Order of work

Follow the progression of `SPEC.md` §16. It starts with the **data generation
scripts** — tiles, routing graph, address index — whose real sizes govern the
whole architecture. Report those sizes to me before building the interface on
top.

## Language

Everything written in this repository is in English: the code, its comments,
the documentation, the commit messages, **and the interface** (`SPEC.md` §9) —
the application serves whatever city publishes its data, not one country.
French is a translation, in `res/values-fr/`, kept complete. Our exchanges are
in French.
