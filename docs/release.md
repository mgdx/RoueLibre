# Publishing a release

Everything a release needs, in the order it is needed: the signing key it
rests on, the checks it must pass, and the steps that put it in front of
somebody.

## The signature, and why it can never change

Android identifies an application by two things: its `applicationId` and the
**certificate its APK is signed with**. An update is accepted only if it
carries the same signature as the version already installed. There is no
authority to appeal to and, this application being published outside Google
Play, no recovery scheme either.

So the key is not a password one rotates. **It is the identity of Roue Libre**,
and it holds for as long as the project does. Lose it and nobody who installed
the application can ever be handed the next version: they would have to remove
it, losing their downloaded cities and their settings, and install again.

The key is a self-signed RSA 4096 pair valid until 2054, created on
23 August 2026, kept in the maintainer's password manager **and** on an
offline copy elsewhere — one copy is not a backup. It is never in this
repository: `.gitignore` refuses `*.jks`, `*.keystore` and
`keystore.properties`, and a key that is versioned is no longer a key.

Its certificate, which is public and which anybody can check an APK against:

```
CN=Roue Libre, O=Roue Libre, C=FR
SHA-256  1D:E5:86:D6:80:F3:29:6F:2D:1A:A0:5D:D5:14:7F:D3:
         DE:18:7A:5D:A1:5A:1F:5D:88:7D:0A:82:A1:E6:ED:89
```

The command that created it is recorded here so the parameters are re-readable
rather than remembered:

```bash
keytool -genkeypair \
  -keystore roue-libre-release.jks \
  -storetype PKCS12 \
  -alias <alias> \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=Roue Libre, O=Roue Libre, C=FR"
```

A PKCS12 store cannot hold a key password different from the store's own, so
the two are the same value.

Releases are signed under the **v2 and v3 schemes**. v2 is all Android needs
from 7.0 on; v3 is what carries the certificate's lineage, and therefore the
only thing that would let the project rotate its key one day without every
installation having to be removed and set up again.

## Wiring the key onto the build

`app/build.gradle.kts` reads `keystore.properties` at the root of the
repository. **Without that file the release is signed by the debug key** — an
APK one can install to try out, and which must never be published, the debug
key being shared by every Android SDK in the world. That fallback exists so
that no key is ever invented on the sly.

```properties
storeFile=/absolute/path/to/roue-libre-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Both files are `chmod 600`, and neither is versioned.

## Releasing, step by step

1. **Set the version** in `app/build.gradle.kts`: `baseVersionCode` goes up by
   one and never back down — it is what Android compares — and `versionName`
   follows semantic versioning.

   Each architecture's APK derives its own code from the base, ten times the
   base plus the architecture's rank: 41 to 44 for base 4, 51 to 54 for base 5.
   A repository cannot hold two APKs of one application under the same code,
   and F-Droid publishes one file per architecture. The universal APK keeps the
   base, and so does `BuildConfig.VERSION_CODE` — which is why the release
   notes are named after the base and not after any of the four.

2. **Write the release notes**: `fastlane/metadata/android/en-US/changelogs/`
   and `fr/changelogs/`, named after the new `versionCode`. English is the
   source, the other languages are translations. They are not optional: the
   what's-new screen reads these very files (`SPEC.md` §7.10), so a version
   published without them shows an empty screen to whoever updates.

3. **Move the changelog on**: `CHANGELOG.md`'s `[Unreleased]` section becomes
   the new version, with a paragraph saying what the version is.

4. **Build and check** — all four, `ktlintCheck` included:

   ```bash
   ./gradlew test lint ktlintCheck
   ./gradlew clean assembleRelease
   ```

   Five APKs come out of `app/build/outputs/apk/release/`: one per
   architecture, plus the universal one. A single architecture must stay under
   12 MB, the universal one under 15 MB compressed (`SPEC.md` §3).

5. **Re-derive the quoted figures**, which are never written by hand:

   ```bash
   python3 tools/update_readme_figures.py
   ```

   It reads the APK sizes off the build itself and the network counts off
   `config/catalogue.json`. `--check` fails instead of rewriting, which is what
   continuous integration runs.

6. **Verify the signature of every one of the five**, not just the one being
   installed. An APK slipped into a release with the wrong signature is a user
   who can never update:

   ```bash
   $ANDROID_HOME/build-tools/<version>/apksigner verify --verbose --print-certs <apk>
   ```

   Expected: `CN=Roue Libre`, the SHA-256 above, `v2 scheme: true` and
   `v3 scheme: true`. Anything reading `CN=Android Debug` means
   `keystore.properties` was not found.

7. **Tag and publish.** The tag is `vX.Y.Z` on the release commit. The APKs are
   renamed `roue-libre-<version>-<abi>.apk` — the build's own names say nothing
   about which application or which version they hold — and the release body
   carries the certificate fingerprint, so that a reader can check what they
   downloaded.

8. **The data is released separately** (`SPEC.md` §4.4): the datasets live in
   [RoueLibre-data](https://github.com/mgdx/RoueLibre-data/releases) under their
   own tags, so updating the base map does not force an application release, and
   the other way round.

## Checking an APK one has downloaded

Anybody can do this, and it needs nothing but the Android SDK build tools:

```bash
apksigner verify --print-certs roue-libre-1.0.0-arm64-v8a.apk
```

The SHA-256 digest it prints must match the one above, character for
character. If it does not, the file was not built by this project.

## Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs and updates an
application straight from where its author publishes it. Here that is this
repository's releases page, and nothing is uploaded anywhere: Obtainium reads
the GitHub API, so the release described above is the whole of the publication.

Two properties of the release — not of Obtainium — are what let it work on its
default settings, and both must survive future versions:

- the APKs are assets of a release tagged `vX.Y.Z`; that tag is where Obtainium
  reads the version from;
- each file names its architecture. Obtainium's `autoApkFilterByArch`, on by
  default, keeps the assets whose name contains one of the ABIs the phone
  reports, so a modern phone is handed `roue-libre-X.Y.Z-arm64-v8a.apk` alone
  instead of a list of five. Renaming those files would put the choice back on
  the user.

The one-tap link, which the README carries as a badge, is the app's minimal
configuration URL-encoded behind the site's redirector:

```
https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/<url-encoded JSON>
```

with the JSON being `{"id","url","author","name"}` — the package identifier,
the repository, `mgdx`, and `Roue Libre`.

### The catalogue

[apps.obtainium.imranr.dev](https://apps.obtainium.imranr.dev) is the
crowdsourced list Obtainium points its users to, a static site fed by one JSON
file per application. Being listed there is what makes the application findable
by somebody who has Obtainium but has never heard of Roue Libre.

The entry is added by pull request to
[ImranR98/apps.obtainium.imranr.dev](https://github.com/ImranR98/apps.obtainium.imranr.dev),
as `public/data/apps/simple/io.github.mgdx.rouelibre.json` — `simple/` because
the application needs no setting changed from the default; `complex/` is for
those that do. Beyond the four required keys it carries an icon URL, a category
(`maps_and_navigation`) and the description in the languages the application
already speaks, taken from `fastlane/metadata/` rather than written again.

Their `APP_CRITERIA.md` asks for two things this respects: the configuration
points at the **official** source, never a re-upload site, and it leaves every
setting at its default value.

## F-Droid

F-Droid **rebuilds from source and signs with its own key**: the key described
above does not sign what F-Droid serves. The consequence to keep in mind is
that an APK downloaded from the releases page and the same version installed
from F-Droid have different signatures, and cannot replace one another without
removing the application first.

### Getting in

The metadata lives in F-Droid's own repository, not in this one. It is a file
named after the application id, `metadata/io.github.mgdx.rouelibre.yml`, added
to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) by merge request — the
route the maintainers prefer over opening a packaging request and waiting for
somebody else to write it.

The recipe, one build entry per architecture, each carrying the version code
that architecture's APK actually declares:

```yaml
Categories:
  - Navigation
  - Public Transport
License: GPL-3.0-only
AuthorName: mgdx
WebSite: https://github.com/mgdx/RoueLibre
SourceCode: https://github.com/mgdx/RoueLibre
IssueTracker: https://github.com/mgdx/RoueLibre/issues
Changelog: https://github.com/mgdx/RoueLibre/blob/HEAD/CHANGELOG.md

AutoName: Roue Libre

RepoType: git
Repo: https://github.com/mgdx/RoueLibre.git

Builds:
  - versionName: 1.0.0
    versionCode: 41
    commit: 433f49c402c50253ac084d7cebf09e8db09a9440
    submodules: true
    gradle:
      - yes
    output: app/build/outputs/apk/release/app-armeabi-v7a-release.apk
    binary: 
      https://github.com/mgdx/RoueLibre/releases/download/v%v/roue-libre-%v-armeabi-v7a.apk
    prebuild: sed -i '/^publishing {/,$d' third_party/brouter/buildSrc/src/main/groovy/brouter.library-conventions.gradle
    scandelete:
      - third_party/brouter/brouter-routing-app
    ndk: r28c

  # … and the same for 42 x86, 43 x86_64, 44 arm64-v8a

AllowedAPKSigningKeys: 1de586d680f3296f2d1aa05dd5147fd3de187a5da15a1f5d887d0a82a1e6ed89

AutoUpdateMode: Version
UpdateCheckMode: Tags
VercodeOperation:
  - '%c * 10 + 1'
  - '%c * 10 + 2'
  - '%c * 10 + 3'
  - '%c * 10 + 4'
CurrentVersion: 1.0.0
CurrentVersionCode: 44
```

**`commit` names a full commit hash, never the tag.** A reviewer asked for it
and they are right: a tag is a name somebody can move, and this one was moved
four times before the recipe was right, while a hash is the thing itself. Read
the hash off the tag rather than off `HEAD` — `git rev-parse v1.0.0^{commit}`,
which resolves the annotated tag to the commit it points at, not to the tag
object.

`submodules: true` is what fetches BRouter, which the root `settings.gradle.kts`
consumes as a composite build and which is pinned to a tag rather than
following `master` — a moving submodule would make the build unreproducible.
`AutoUpdateMode` works despite there being four entries per version:
`VercodeOperation` names one arithmetic expression per architecture, and their
update checker copies the last four recipes and gives each its computed code,
keeping the `output` and `binary` that belong to it. It reads the version off
`app/build.gradle.kts`, which is why the version code is written there as a
number and not as a constant — their expression wants a literal after
`versionCode`, and a name left `fdroid checkupdates` unable to tell one release
from the next.

`AllowedAPKSigningKeys` and the four `binary` URLs are what ask F-Droid to
verify its own rebuild against the APKs published here and serve ours. They are
set from this first submission on purpose: **a version F-Droid signs with its
own key can never be moved to ours afterwards**, so the choice is only offered
once.

The scanner refuses to build over anything it cannot account for, and the
recipe answers each of its three findings by **changing the tree it scans**.
There is no `scanignore` here, and there must not be: a reviewer answered the
first submission with *« Don't use scanignore »*, and they are right.
`scanignore` does not declare one line acceptable, it switches the scanner off
for a whole file — for this version and for every version `AutoUpdateMode`
copies the entry into, including whatever that file gains later. It is meant
for files a contributor cannot touch, and we can touch all three:

- **`brouter-routing-app` is deleted** before the build. It is BRouter's own
  Android application, and it ships two ZIP archives in its assets. We never
  build it — BRouter's `settings.gradle` includes that module only when a
  `local.properties` exists inside the submodule, and none does — so removing
  it costs nothing and leaves nothing unexplained in the tree.
- **BRouter's `publishing` block is cut by `prebuild`.**
  `brouter.library-conventions.gradle` points a Maven repository at
  `maven.pkg.github.com` — where BRouter pushes its own artifacts, never where
  a dependency is fetched from. The file cannot simply be deleted: five
  submodule modules apply the plugin it defines. The block is the whole tail
  of the file, so one `sed` removes it and leaves the `plugins` block that
  matters. `prebuild` runs during source preparation, before the scan, so what
  is scanned is what is compiled — the point `scanignore` was missing.
- **The two JDK downloads left the repository.** `settings.gradle.kts` no
  longer applies `foojay-resolver-convention`, and
  `gradle/gradle-daemon-jvm.properties` no longer carries the ten
  `toolchainUrl.*` entries `updateDaemonJvm` writes into it. Both existed only
  to fetch a JDK when a toolchain is missing, which on their image never
  happens: it carries the JDK 21 both modules ask for and builds with
  `org.gradle.java.installations.auto-download=false`. Re-running
  `updateDaemonJvm` puts the URLs back — strip them again.

  To be exact about the second one: their source preparation deletes
  `gradle-daemon-jvm.properties` itself, next to `gradlew` and the wrapper jar,
  so the scanner would never have reported it. It is stripped here for whoever
  builds this outside F-Droid, and so the repository stops carrying a download
  it never uses.

### What their pipeline insists on

The file above is not merely valid, it is the **only** shape their CI accepts,
and three of its rules cost a round trip to learn:

- `AutoUpdateMode: Version` carries **no pattern**. Their JSON schema allows
  `None` or `Version` with an optional `+suffix` and nothing else; the commit
  to build comes from `UpdateCheckMode: Tags`, so a pattern would have been
  ignored anyway.
- **`AutoName` has to be there.** Their tooling derives it from the manifest,
  and a job ends on `git diff --exit-code`: anything their run adds that the
  file does not already say fails the pipeline.
- The file must be a **fixed point of `fdroid rewritemeta`**, which reorders
  every build entry — `output`, `binary`, `prebuild`, `scandelete` — puts
  `AutoUpdateMode` and `UpdateCheckMode` before `VercodeOperation`, and wraps
  long values on its own terms — the 122-character `prebuild` line above it
  leaves alone, while two of the four shorter `binary` URLs it moves to a line
  of their own. Do not format it by hand: run their tool and commit what it
  writes.

  The wrapping depends on the `ruamel.yaml` version, and not on
  `fdroidserver`'s. Measured against the file their CI accepted, **0.18.12 and
  below reproduce it, 0.18.13 and above fold every long value** — including the
  `prebuild` line and `AllowedAPKSigningKeys`. Debian trixie carries 0.18.6, so
  pin that: `pip install 'ruamel.yaml==0.18.6'` in the same virtualenv as
  `fdroidserver`, whatever the newest release is.

`fdroid rewritemeta` needs no checkout of their repository: a directory holding
`metadata/io.github.mgdx.rouelibre.yml` and a one-line `config.yml` is enough
for it to run, and running it twice is how a fixed point is confirmed.

To rehearse the pipeline here rather than discovering it there, reproduce the
build image: `fdroidserver` from **master** (the released package lags behind
it), the `gradlew-fdroid` script from its own repository symlinked in `PATH` as
`gradle`, a real JDK 21 in `JAVA_HOME`, and a `GRADLE_USER_HOME` holding their
`gradle.properties` — `org.gradle.java.installations.auto-download=false`, plus
`auto-detect=false` on a workstation, where a stray JRE under `/usr/lib/jvm`
will otherwise be offered to Gradle as a compiler and fail the build.

### What made it unreproducible

Their first run rebuilt the application and found exactly one file different:
`lib/*/libdatastore_shared_counter.so`.

The application compiles no native code of its own, so this was never about
building — it was about **stripping**. AGP removes the symbols from the native
libraries its dependencies ship, and it does so with whichever NDK it finds.
Two machines carrying different ones write two different files, and the rebuild
can never match. `ndkVersion` in `app/build.gradle.kts` and `ndk: r28c` in the
recipe now name the same tool on both sides, so the stripping is the same
operation rather than the same intention. Moving that version is a decision
taken in the two places at once, which is the point of writing it down twice.

Keeping the symbols instead — `packaging.jniLibs.keepDebugSymbols` — would also
have made the file identical, by never touching it. It was tried and dropped:
it ships symbols nobody reads and leaves every other library still stripped by
an unnamed tool, which is treating the symptom.

MapLibre's library needs none of this: it arrives already stripped, so
stripping it again changes nothing, which is why it was never the file to
differ.

**`META-INF/version-control-info.textproto` is the other thing to know about.**
AGP writes the git revision the build came from into the APK, so an APK built
from any other commit can never match — the published binaries have to be built
from the very commit the recipe names, not merely from the same source.

### It builds, and it reproduces

Run against the `v1.0.0` tag with F-Droid's own tool, every check passes:

```
$ fdroid lint io.github.mgdx.rouelibre               # no output: nothing to say
$ fdroid build io.github.mgdx.rouelibre:41           # … and 42, 43, 44
1 compilation réussie
```

All four entries build. And what comes out is not merely equivalent to what was
published, it is **the same file**: each of the four compared entry by entry
with its release APK, name and CRC, and **nothing differs** — `classes.dex`
included, identical byte for byte. Only the signature separates them, theirs
being unsigned. The second phase below is therefore not a hope; it has been
measured, on all four architectures.

Their image preinstalls Android platforms only up to `android-33`, while this
project compiles against 37 — which is not the obstacle it looks like. The
image accepts the SDK licences and leaves `platforms/` and `build-tools/`
writable precisely so that Gradle can fetch what a project needs. Built here
against an SDK with API 37 deliberately removed, the build fetched
`platforms/android-37.0` and `build-tools/36.0.0` by itself and succeeded.

What remains untested is the container. `fdroid build --server` runs the build
inside their Debian image, and that needs Docker or Podman. Everything above ran
on a workstation, with their tool and under their constraints — a fresh clone,
one JDK 21, no toolchain provisioning — but not inside their machine.

### The Gradle ceiling is a local artefact, not theirs

Running `fdroid build` from a released fdroidserver stops on:

```
No hash for gradle version 9.5.0! Exiting...
```

**That is an artefact of the local tool, not of F-Droid.** F-Droid never runs
the Gradle wrapper a repository ships — a downloaded wrapper is a binary nobody
reviewed. It runs `gradlew-fdroid`, which used to carry its list of Gradle
versions inline; the released fdroidserver 2.4.5 still bundles that old copy,
and it stops at Gradle 8.14.2. The build server does not use it: it clones
[gradlew-fdroid](https://gitlab.com/fdroid/gradlew-fdroid) from its own
repository at image provisioning time, and that version reads its checksums
from the [gradle transparency
log](https://gitlab.com/fdroid/gradle-transparency-log). The log records
`gradle-9.5.0-bin.zip` under the very SHA-256 our wrapper pins, and the tool's
plugin table maps AGP 9.3 to Gradle 9.5.0 — this project's exact pair. So to
test locally, clone `gradlew-fdroid` yourself rather than trusting the copy
that came with the package.

The **JDK** is what had to be settled, and it is. The build image is Debian
trixie with `default-jdk-headless`, which is **JDK 21**, and it sets
`org.gradle.java.installations.auto-download=false` — a toolchain that is not
installed is refused rather than fetched. This repository used to ask for two
that are not there: a **JDK 25** daemon in `gradle/gradle-daemon-jvm.properties`
and `jvmToolchain(17)` in both modules. Under those conditions the build failed
on each in turn, so both now say 21.

Nothing about the application moved with them. The toolchain says which
compiler runs; `jvmTarget` and `sourceCompatibility` say what is shipped, and
both stay at 11 for API 26. Built before and after the change, the two APKs
differ by exactly one entry — `META-INF/version-control-info.textproto`, which
records the commit — and `classes.dex` is identical byte for byte.

That is the condition to reproduce whenever the toolchain moves again:

```bash
git clone --recurse-submodules <repo> && cd <repo>
JAVA_HOME=<a JDK 21> ./gradlew \
  -Dorg.gradle.java.installations.auto-download=false \
  -Dorg.gradle.java.installations.auto-detect=false \
  assembleRelease
```

A fresh clone with nothing but a JDK 21 and no way to fetch another is what
their server is. If that command builds, so will they.

### Being published under our own signature

Once the recipe builds on their server, F-Droid can be asked to publish **our**
APKs rather than its own: it rebuilds from the recipe, compares the result to
the file we published byte for byte, and if the two match it serves ours
untouched — which, as the comparison above showed, is already the case. The two signatures then stop being rivals, and one can move between
the releases page and F-Droid without removing anything.

It is asked for by adding two fields to the same metadata file — `Binaries`,
where our APKs are downloaded from, and `AllowedAPKSigningKeys`, the SHA-256
above without its colons. It is also why the APK carries no AGP dependency
block (`dependenciesInfo` is off in `app/build.gradle.kts`): that block is
signed by AGP and is not reproducible.
