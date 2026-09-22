# Publishing on Google Play

A third channel beside the releases page and F-Droid, and the only one with
rules of its own to satisfy. What follows is what Play asks of this
application, what was answered, and what has to be done again at each release.

`docs/release.md` stays the reference for the release itself — version, notes,
checks, signature, tag. This file covers only what Play adds on top.

## Why a Google account at all, with constraint C2

C2 forbids **Google services inside the application**: Play Services, Firebase,
Maps SDK, Play Integrity. None of them is added here — the artefact uploaded to
Play is the same code as the one F-Droid rebuilds, and it still runs on a
device with no Google services at all. The store is a way in, for the people
who only ever install from it; it is not a dependency. Nothing in this file
adds a line of Google code to the application.

## Two things that are not the same key

Play refuses an APK from a new application: it takes an **app bundle**, and it
signs what it serves with a key it holds itself. That key is not the
publishing key of `docs/release.md`, and the choice was taken deliberately on
14 September 2026: Google generates its own rather than being handed this
project's.

The consequence is written down here rather than discovered later:

- The releases page and F-Droid are **one installation** — F-Droid's recipe
  names the certificate in `AllowedAPKSigningKeys` and verifies its rebuild
  against the published file instead of signing one of its own, so an update
  passes from either to either.
- Play is a **second, separate one**. Somebody who installed from GitHub or
  F-Droid cannot update to the Play version, nor the other way round, without
  removing the application first — which loses the cities they downloaded and
  their settings. Nothing can be done about that afterwards; it is the price of
  Google holding the key, and it is why the choice was a choice.

The **upload key** is a third key, and it is not an identity: it only proves
who is uploading. It lives at `~/.android-keys/roue-libre-play-upload.jks`,
alias `roue-libre-upload`, RSA 4096, and its four lines sit in
`keystore.properties` beside the publishing key's, under the `upload` prefix.

```
CN=Roue Libre Upload, O=Roue Libre, C=FR
SHA-256  3D:92:01:71:FF:EB:EC:05:48:0C:B9:30:27:D5:C9:08:
         DA:4A:A9:0A:FC:78:61:91:2E:23:24:DA:C2:CC:CB:A6
```

Losing it is recoverable — Google replaces an upload key on request — which is
exactly why it is separate from the one whose loss is not.

## Building the bundle

```bash
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab
```

Two things happen in `app/build.gradle.kts` for that task and no other: the ABI
splits are turned off, because AGP refuses to build a bundle while they are on,
and the upload key signs instead of the publishing key. `assembleRelease` is
untouched — the same five APKs, under the same certificate, which is what
F-Droid's verification rests on.

**The two tasks do not go in one Gradle invocation.** Whether the splits are on
is decided once, at configuration time, from the task graph the invocation
carries: ask for `assembleRelease bundleRelease` together and the splits are off
for both, so `assembleRelease` writes a single `app-release.apk` instead of the
five the release needs. Nothing fails and nothing warns — the directory simply
holds one file. Run them one after the other, as two commands.

The bundle carries the four architectures at once; Play cuts it per device, so
what a telephone downloads is of the order of the per-architecture APK, not of
the bundle's own twenty megabytes.

Check what was signed before uploading:

```bash
unzip -p app/build/outputs/bundle/release/app-release.aab 'META-INF/*.RSA' \
  | openssl pkcs7 -inform DER -print_certs -noout | head -2
```

Expected: `CN=Roue Libre Upload`. Reading `CN=Roue Libre` means the upload lines
are missing from `keystore.properties`, and Play would then hold the publishing
certificate as the one it expects uploads from.

## The store listing

| Field | What it holds |
| --- | --- |
| Title | Roue Libre |
| Short description | `fastlane/metadata/android/<lang>/short_description.txt`, already under Play's eighty characters |
| Full description | `fastlane/metadata/android/<lang>/full_description.txt`, already under four thousand |
| Icon | `fastlane/metadata/android/en-US/images/icon.png`, 512×512 |
| Feature graphic | 1024×500, composed by `tools/make_play_assets.py` |
| Screenshots | composed by the same script, 1080×1920 |
| Category | Maps & Navigation |
| Privacy policy | `docs/privacy-policy.md`, served by GitHub |
| Contact address | `leo@magadoux.fr` |
| Website | `https://github.com/mgdx/RoueLibre` |

The contact address is **shown publicly on the listing**, which Play requires
and does not let one hide. It is the address the commits are authored under, so
it is already public in the repository's history and publishes nothing new.

The descriptions link to no other store and to no downloadable APK, which Play
forbids in a listing. Keep it that way when they are next edited.

**The screenshots cannot be uploaded as taken.** Play refuses an image whose
long side is more than twice its short one, and a telephone screen is taller
than that — the Fairphone 5's is 1224×2700. `tools/make_play_assets.py` lays
each screenshot whole on a captioned card instead of cropping the attribution
line off the map, and writes the feature graphic in the same colours. It reads
the screenshots of `fastlane/metadata/`, which stay exactly as they are:
F-Droid shows the raw screen, and should.

**The release notes are Play's, not F-Droid's.** Play caps them at five hundred
characters where `changelogs/<versionCode>.txt` runs to several thousand. The
rule is to paste the **headline line** of that file — the sentence before the
bullets, which says what the version is and which is short by construction —
rather than to keep a second changelog in step with the first.

## What was declared, and why

Answer these the same way each time; an answer that drifts from the others is
what gets a listing pulled.

- **Data safety — nothing collected, nothing shared.** Play counts data as
  collected when it leaves the device. Nothing does: addresses are searched
  against the index on the device, journeys are computed there, and the
  position is read and drawn without being stored or sent. The GBFS feed and
  the dataset downloads are requests for public files, carrying nothing of the
  user beyond the IP address any request shows any server.
- **No advertising identifier.** The application declares no `AD_ID`
  permission, and the answer to the advertising question is no ads.
- **App access**: every screen is reachable with no account and no code.
- **Account deletion**: not applicable, there is no account.
- **Content rating**: the questionnaire under "Utility, productivity" —
  no violence, no sexuality, no profanity, no gambling, no purchases, no
  user-generated content, no sharing of the user's position with anybody.
- **Target audience**: 16 and over. Including under-13s would bring the
  Families policy in, which asks for things this application has no use for.
- **Not a news, health, financial or government application**, and not one
  covered by the child-safety standards policy, which applies to social and
  dating applications.

## The first publication, once

A personal developer account opened after 13 November 2023 — which this one is
— cannot publish to production until it has run a **closed test with at least
twelve testers, opted in continuously for fourteen days**, and Google now
checks that those testers actually opened the application. Twelve distinct
Google accounts, on real devices; emulators and second accounts of the same
person do not count.

So the order is: create the closed track, gather the twelve, wait out the
fortnight, then apply for production access. It is the fortnight that sets the
publication date, not the code.

## At each release afterwards

1. Everything in `docs/release.md`, which does not change.
2. `./gradlew bundleRelease`, and check the certificate as above.
3. Upload the bundle, paste the headline line of the release's changelog as the
   notes, in English and in French.
4. Re-run `tools/make_play_assets.py` only if the screenshots changed.
5. Roll out. Play staggers the release itself; the releases page and F-Droid do
   not wait for it.
