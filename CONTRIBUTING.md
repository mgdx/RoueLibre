# Contributing to Roue Libre

Thank you for the interest you are taking in the project. Every contribution is
welcome: translation, fixes, review, adding another conurbation.

Everything here is in English: the code, the documentation and the interface.
Other languages arrive as translations.

## Before you start

Read [`SPEC.md`](SPEC.md). It is the complete specification and the project's
source of truth. Several decisions that look arbitrary are justified there — the
absence of Jetpack Compose, the refusal of commercial points of interest on the
map, the house-number granularity. If a proposal contradicts `SPEC.md`, open an
issue first to discuss it: those decisions were taken after thought, not by
default.

## Translating the application

This is the most useful contribution if you do not write code.

The application is written in English. Every string lives in a single file:
[`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml),
which has no language qualifier: it is what Android serves when nothing else
matches, so it is always complete. French, in `values-fr/`, is a translation
like the others.

**Twenty-nine languages are already started** — Albanian, Arabic, Basque,
Bosnian, Catalan, Chinese, Croatian, Czech, Danish, Dutch, Finnish, Galician,
German, Hungarian, Italian, Japanese, Latvian, Lithuanian, Norwegian, Polish,
Portuguese, Romanian, Serbian, Slovak, Slovene, Spanish, Swedish, Turkish. Their files exist
under `values-ar/`, `values-de/` and so on, but every string in them still
holds its English text: they are a starting point, not a translation. Open the
one for your language and translate it in place.

The list follows the catalogue: there is a started file for every language
spoken where a network is served, so that arriving in Ljubljana or Pristina
with the application means arriving in a language somebody can finish rather
than in a folder somebody must create.

For a language that has no file yet:

1. **Create its folder**: `app/src/main/res/values-<code>/`, where `<code>` is
   the ISO 639-1 code. For a regional variant, `values-pt-rBR`.
2. **Copy `values/strings.xml` into it** — the English file, which is always
   complete — and translate the tags' contents, never their `name` attribute.
3. **Declare the language** in `localeFilters`, in
   [`app/build.gradle.kts`](app/build.gradle.kts). Without that line Android
   drops the folder from the APK and nobody ever sees the translation.
4. **Check the layouts.** Switch the system to your language and walk through
   every screen. German and Dutch lengthen labels appreciably; that is where
   layouts break. A right-to-left language mirrors them, which is another
   thing entirely: the screens are built for it, but each addition deserves a
   walk through in Arabic before it is called done.

While a string is left untranslated, that is the English text its readers
get — the same thing they would have got with no file at all. Translating half
a file is already worth doing.

### Rules to respect

**Placeholders are positional and keep their number.** `%1$s` stays `%1$s`, but
its place in the sentence may change:

```xml
<!-- source -->
<string name="freshness_fresh">Updated %1$s</string>
<!-- a language where the order differs -->
<string name="freshness_fresh">%1$s aktualisiert</string>
```

Never concatenate two strings in the code: word order changes from one language
to another.

**Plurals go through `<plurals>`, and the categories vary.** English uses two
(`one`, `other`), Polish four, Arabic six. Provide the ones for your language,
listed in the
[CLDR plural rules](https://cldr.unicode.org/index/cldr-spec/plural-rules). The started
files already carry the categories their language needs — Arabic six, Polish
four, Spanish, Italian and Portuguese three, Chinese one — each holding the
English plural for want of better. Do not remove one, and do not add one
either: Android Lint checks both ways, and it is right to.

**Watch what `one` covers.** In French it takes 0 as well as 1 — "0 vélo", in
the singular — where English writes "0 bikes". Every language draws that line
where it draws it; do not copy the source's.

**Apostrophes are escaped**: `l\'instant`, never `l'instant`.

**The comments above the strings are meant for you.** They say what each
placeholder stands for and in what context the sentence appears. Keep them, and
extend them if an ambiguity made you hesitate.

**Tone.** Short sentences, active voice, no jargon. An error message says what
happened and what to do, without apologising or staying vague. An empty screen
invites action, it does not state a fact. An action keeps the same name from the
button through to the confirmation.

**The vocabulary stays generic**: "station", "bike", "network". A particular
network's name appears only in the city configuration.

## Teaching it your streets

A translation makes the interface readable. This makes the **search** work, and
it is a different file.

The address index folds a street name down to a comparable form before storing
it, and folds what you type the same way. Those rules are written per language,
in [`config/address-normalization/`](config/address-normalization/): which
abbreviations to expand (`ul.` → *ulica*, `bd` → *boulevard*), which letters to
fold that accent removal cannot reach (ß → ss, ł → l), which words are street
types to be split off the head of a name, and which are articles not worth
ranking on.

If your language is there, read it: a missing abbreviation is a street its
speakers cannot find. If it is not, copy `en.json`, fill it in, and add a
dozen real street names to `referenceNames` — a unit test replays them against
both implementations from the moment the file lands. Then:

```bash
python3 tools/refresh_normalization_fixtures.py   # --check to see without writing
./gradlew :core:test
```

A language with no file of its own is served by `en.json`: plain folding, no
street type. It works, and it is worse than what you can write in an hour.

## Contributing code

### Setting up

```bash
git clone https://github.com/mgdx/RoueLibre.git
cd RoueLibre
./gradlew test
```

You need a JDK 17 or later and the Android SDK. No key and no account is
required.

### Before opening a pull request

```bash
./gradlew ktlintFormat            # formats
./gradlew test lint ktlintCheck   # must pass without a single warning
```

The static analysis tolerates no warning. This is not fussiness: the project is
meant to be audited by F-Droid reviewers, and one warning left lying around
hides the next.

### What the review will look at

- **Layer separation.** Business logic goes into `:core`, which is allowed no
  Android import at all. The compiler enforces it; that is what makes the logic
  testable on the JVM without an emulator.
- **Not a single hard-coded string**, neither in the Kotlin nor in a layout. No
  hard-coded colour or size either: everything goes through the resources.
- **Comments explain the *why*.** A comment that paraphrases the code is noise
  that will go stale. Do document systematically, however: non-obvious choices,
  accepted trade-offs, workarounds for library limitations, and every
  coefficient of the journey algorithm.
- **KDoc** on everything public: role, parameters, return value, error cases.
- **Tests.** Mandatory on the journey algorithm, GBFS feed parsing, address
  resolution and house-number interpolation. Every bug fix comes with the test
  that would have caught it.
- **Explicit errors.** Result types, not silent exceptions, and for every
  failure a message saying what to do.
- **No dead code, no anticipated abstraction.** The only generalisations asked
  for are those of serving another city.

### Commits

Atomic, with a message describing the **intent** rather than the manipulation.
"Fix the departure station chosen when two stations tie on time" beats "change
RouteFinder.kt". Commit messages are in English, like the rest of the
repository.

### Adding a dependency

It must be:

1. **justified in [`docs/dependencies.md`](docs/dependencies.md)**, with the
   reason for choosing it over another;
2. **GPLv3-compatible** — check before integrating;
3. **free of any Google service**: no Play Services, no Firebase, no Maps SDK,
   no ML Kit, no Crashlytics. The application must work on any Android OS
   without Google services;
4. **free of telemetry.** The Exodus Privacy analysis must detect no tracker.

If one of those constraints stands in the way of a feature you find useful, say
so in an issue and propose an alternative — do not work around it in silence.

## Reporting a bug

Give the device model and Android version, what you expected, what happened, and
how to reproduce it. If the problem concerns a specific station or address, name
it: the data itself is often the cause, and the distinction is quick to make.

The application sends no log anywhere. If you attach one, that is your decision
— read it through first, a trace can contain an address you searched for.

## Licence

By contributing, you agree that your work be published under
[GPLv3](LICENSE), like the rest of the project.
