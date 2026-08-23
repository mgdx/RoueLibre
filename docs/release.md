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

1. **Set the version** in `app/build.gradle.kts`. `versionCode` goes up by one
   and never back down — it is what Android compares. `versionName` follows
   semantic versioning.

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

## F-Droid

F-Droid **rebuilds from source and signs with its own key**: the key described
here does not sign what F-Droid serves. The consequence to keep in mind is that
an APK downloaded here and the same version installed from F-Droid have
different signatures, and cannot replace one another without removing the
application first.

Reconciling them is possible and is the goal: F-Droid's *reproducible builds*
mode has it verify that its own rebuild matches ours byte for byte, and then
publish **ours**. That is why the APK carries no AGP dependency block
(`dependenciesInfo` is off in `app/build.gradle.kts`), and the request is made
with the certificate fingerprint above.
