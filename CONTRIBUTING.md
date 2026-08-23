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

**Thirty languages are already started** — Albanian, Arabic, Basque,
Bosnian, Catalan, Chinese, Croatian, Czech, Danish, Dutch, Finnish, Galician,
German, Greek, Hungarian, Italian, Japanese, Latvian, Lithuanian, Norwegian,
Polish,
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

### When a translation is finished

A started file holds the English text, and the application knows it: it goes on
formatting that language's dates and distances in English, and it does not offer
that language in its settings. **"Deutsch" that answers in English would be
worse than no German at all.** So a translation only becomes real when it is
declared, and it has to be declared in **three** places — the code holds one
list, the build holds another, and Android insists on reading a third from the
resources, where nothing can be computed:

1. **`TRANSLATED_LANGUAGES`**, in
   [`app/src/main/java/io/github/mgdx/rouelibre/ui/Locales.kt`](app/src/main/java/io/github/mgdx/rouelibre/ui/Locales.kt).
   This is the one that matters, and everything the application decides for
   itself is derived from it: the dates, the numbers and the distances start
   being formatted in the language, and the language chooser in the settings
   starts offering it. Nothing else in the Kotlin code has to change.
2. **`localeFilters`**, in
   [`app/build.gradle.kts`](app/build.gradle.kts). Already done if the started
   file exists, since a folder absent from that list is dropped from the APK.
   Check it rather than assume it.
3. **`res/xml/locales_config.xml`**, which is what makes the language appear in
   **Android's own per-application language settings**, from Android 13 on. It
   is the one place the list is written a second time, because Android reads it
   from the APK's resources; a unit test reads that file and fails if it and
   `TRANSLATED_LANGUAGES` disagree, so a step forgotten here is a red build
   rather than a defect nobody notices.

Then run `./gradlew test`: the tests pin the three against one another, and their
failures name the file left behind.

Two things are **not** on that list, and deliberately. Nothing has to be added to
the settings screen — the chooser is built from the list, not written out beside
it. And `values-<language>/` folders are never read to decide what is offered:
thirty-one of them exist, and most still hold English.

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

**Use the typographic apostrophe**: `l’instant`, with U+2019, and never the
straight `'`. It is what the English source already writes, it is what a reader
of French expects in print, and it needs no escaping — a body that still holds
`\'` is one that has the wrong character in it.

**Punctuation that takes an inner space takes a non-breaking one.** French sets
`?`, `!`, `;`, `:` and the inside of `« »` off with U+00A0, so that the mark
never begins a line on its own. Every language does this its own way; follow the
one you are writing.

**The comments above the strings are meant for you.** They say what each
placeholder stands for and in what context the sentence appears. Keep them, and
extend them if an ambiguity made you hesitate.

**Tone.** Short sentences, active voice, no jargon. An error message says what
happened and what to do, without apologising or staying vague. An empty screen
invites action, it does not state a fact. An action keeps the same name from the
button through to the confirmation.

**French says *tu*.** In `values-fr/` and in the store texts of
`fastlane/metadata/android/fr/` alike: the application speaks to one person
walking to a station, not to a customer — "Tu choisis ta ville", never "Vous
choisissez votre ville" (`SPEC.md` §9). The rule is about the French, and this
file and `docs/` are outside it, addressing developers rather than users. Every
other language settles its own register: choose the one that sounds like one
person talking to another, and hold it from the first string to the last.

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
