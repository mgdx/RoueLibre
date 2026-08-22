# Dutch glossary

The terms `res/values-nl/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Dutch ones over three screens, and so that a contributor can correct
one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## One Dutch for twelve networks

The catalogue serves the Netherlands and Belgium alike — Amsterdam and Antwerp,
twelve networks between them — so the file is written in a reference Dutch that
belongs to neither. Where a word is felt as Belgian (*fiche* for a detail
screen, *opgeslorpt* for a merged municipality) or as Netherlandic only, the
neutral word is taken instead: *detailscherm*, *gefuseerde gemeenten*.

The arbiter for anything Android already names is the phone itself: 5 900
system strings extracted from this device's `framework-res.apk` and
`Settings.apk`. Every key quoted below was found in that extract.

## Register and typography

The Dutch says **je / jij**, in the interface and in the store texts alike, as
the French says *tu* (SPEC §9). It is also what Android's Dutch does —
"Tik om de instellingen voor de simkaart te updaten"
(`settings:post_dsds_reboot_notification_text`), "Houd je aandacht onder
controle" (`settings:zen_mode_inspiration_generic`). Imperatives follow: *Tik*,
*Kies*, *Probeer het opnieuw*, *Trek de lijst omlaag*.

Quotation marks are **“ ”**. The apostrophe, where a name needs one (*Vélib’*,
*V’lille*, *Vélo’v*), is **’** (U+2019) and never the straight quote, which is
why nothing in the file is escaped. The dash that breaks a sentence is **–**
with a space on either side, not the em dash the English file uses; that is the
one piece of punctuation changed inside a format-only string, `city_label`
(`%1$s – %2$s`).

Dutch compounds are written as one word, and that is what lengthens the labels.
Where a label had to stay short, it takes the wording Android itself uses
rather than the literal translation. The three places measured on screen:

| String | Dutch | Characters |
|---|---|---|
| `download_unmetered_only` | Alleen downloaden zonder datalimiet | 35 |
| `settings_map_filters_hide_empty` | Stations verbergen die niets bieden | 35 |
| `settings_map_filters_hide_out_of_service` | Stations buiten dienst verbergen | 32 |
| `station_as_origin` / `station_as_destination` | Vanaf hier / Naar hier | 10 / 9 |

The last pair are the only two buttons of the application placed side by side
on one row, which is why they are the shortest phrasing that still reads as a
pair.

## The vocabulary

| English | Dutch | Why |
|---|---|---|
| journey | reis | The whole door-to-door thing: the screen, the settings section, the button, the errors. It is what a Dutch travel planner calls a door-to-door trip — "Plan je reis" — and it leaves *route* free for what English also calls a route. Short, which matters on `journey_compute` ("Reis berekenen") and `journey_frame` ("Toon de hele reis"). |
| ride | rit | The bike leg alone, inside a journey. A different word from *reis*, so the elevation profile and the "own bike" wait cannot be mistaken for the whole thing. |
| route | route | Only in `journey_no_route` — "Geen begaanbare route tussen deze twee punten" — and in `station_beyond_area`: the line on the ground, not the planned journey. Dutch has the same pair English does, *reis* / *route*, so no third word had to be invented. |
| journey data (store texts) | reisgegevens | "Geen reisgegevens bewaard". It cannot collide with `dataset_routing`, which is **routegegevens** and *is* stored on the device, because the two words are already kept apart above. |
| bike | fiets | There is no shorter Dutch word, and none is wanted: *fiets* is what every network in both countries writes. |
| bike-share bikes, as a product | deelfietsen | Only in the store texts and on the welcome page, where the thing has to be named before it is known. Inside the interface the context is settled and *fiets* is enough. |
| station | station | A bike-share station, which is what Villo!, Velo and Blue-bike call one. |
| railway station | treinstation | Dutch *station* means both, so `address_search_prompt_message` — whose English comment says its "stations" are railway ones — has to say **treinstations** in full. |
| dock (free) | vrije plaats | What one returns a bike into, counted as available: "6 fietsen, 26 vrije plaatsen". It is what the Dutch-language networks write. |
| dock (capacity) | stallingsplaats | The same object counted as a total, which is a different figure shown right beside the first one (`JourneyDetailFragment.availabilityOf` prints "26 vrije plaatsen · 30 stallingsplaatsen"). English says "dock" for both; Dutch does not have to. *Stallingsplaats* is the ordinary Dutch for the spot a bike is parked in, from *fietsenstalling*. It is the longest noun in the file, and it is deliberate: on the one line where both figures meet, no shorter word tells them apart. |
| dock | *never* “zuil”, *never* “terminal” | Those are the payment post, not the point a bike attaches to. |
| mechanical / electric | mechanisch / elektrisch | Kept as adjectives, as in English, because the counts are elliptical: "4 mechanische · 2 elektrische" stands for "4 mechanische fietsen", and the adjective is inflected for that. Both plural categories carry the same inflected form, which is correct Dutch after a numeral. *E-bike* was left aside: it is a product noun and would not agree with the mechanical half beside it. `journey_bike_kind_electric_description` says **trapondersteuning** so that "electric" cannot be read as a moped. |
| pace (walking) | looptempo | A pace is not a speed, which `values/strings.xml` says above the string. *Tempo* is a pace; *snelheid* is the figure nobody has measured about themselves, and is not used. Slow / Normal / Brisk are **Langzaam** (Android's own, `settings:speed_label_slow`) / **Normaal** / **Stevig**, after *stevig doorstappen*. |
| climb | stijging | The metres climbed, over a leg or over the whole journey: "120 m stijging". It is what Dutch cycling and hiking applications write; *klim* reads as the act rather than the figure. |
| location / position | locatie / positie | Two words, as in English and for the same reason. **Locatie** is the system feature and the permission — Android's own word, `android:permgrouplab_location` — and is what `map_locate_me` and `map_location_denied` speak of. **Positie** is where the reader actually is, the point on the map: "Mijn positie", "Je positie valt buiten …". |
| conurbation | agglomeratie | The city screen serves a metropolitan area rather than a municipality, and *stad* is kept for the shorter word the settings section and the title need. |
| Settings | Instellingen | Android's own (`settings:settings_label`), including in the system path quoted in `about_links_body`: "Instellingen → Apps → … → Standaard openen", which is Android's own Dutch for that screen (`settings:launch_by_default`). |
| Theme | Thema | Android's own: *Donker thema* (`settings:dark_ui_mode`), *Apparaatthema* (`settings:device_theme`). Light / Dark are **Licht** / **Donker**. |
| Display (section) | Scherm | Android's own name for the section that holds the theme and the text size (`settings:display_category_title`). |
| Storage | Opslag | Android's own (`settings:storage_category`). Everywhere another string points at that screen it says **via het scherm Opslag** rather than translating "storage screen" literally. |
| Delete | Verwijderen | Android's own (`android:delete`, `settings:delete`), for what destroys: a city's data, a dataset. |
| Remove (from a list) | uit … halen | **This is a departure, and it is deliberate.** Android's Dutch collapses the English pair into one verb: *Remove* is also **Verwijderen** (`settings:remove`, `android:kg_reordering_delete_drop_target_text`). Taking a station out of the favourites is not destroying it, and the file keeps the two acts apart the way English does, with **"Uit favorieten halen"** against **"Verwijderen"**. Do not "correct" it back. |
| Clear (a search) | wissen | Android says *Zoekopdracht wissen* for the icon inside a field (`android:searchview_description_clear`), and the same wording serves the button in the empty state, so the two read as one thing. |
| Refresh | Vernieuwen | Android's verb for data — "Sta toe dat apps gegevens automatisch vernieuwen" (`settings:auto_sync_account_summary`). The freshness lines say *Vernieuwd zojuist*, so the button and what it produces read as one thing. |
| Out of service | Buiten dienst | **A departure from Android, with a reason.** Android's Dutch for out-of-service is *Niet in gebruik* (`settings:radioInfo_service_out`) — but this application already says **In gebruik** for the city currently selected, which is also Android's own (`android:media_route_status_in_use`). Using both would make "In gebruik" mean *selected* on one screen and *working* on another. *Buiten dienst* is the ordinary Dutch for a station or a vehicle that is not running, and it is what `settings_map_filters_hide_out_of_service` echoes. |
| Frozen data | Verouderde gegevens | `freshness_stale`, shown when the feed has stopped moving. *Bevroren* reads as a crash rather than as data that has stopped being refreshed. |
| Try again | Opnieuw proberen | Android's own, and the more frequent of the two it uses (`settings:network_connection_timeout_dialog_ok`; *Nogmaals proberen* appears once, at `android:lockscreen_password_wrong`). |
| Continue | Doorgaan | Android's button word (`android:autofill_continue_yes`), on the welcome carousel and the what's-new screen. |
| Skip | Overslaan | Android's own (`android:skip_button_label`). |
| Back | Terug | Android's own, on the toolbar arrow (`settings:back`). |
| In use | In gebruik | Android's own (`android:media_route_status_in_use`), on the city already selected. |
| just now | zojuist | Android's own (`settings:time_unit_just_now`), lower-cased because it is always read inside `freshness_fresh`: "Vernieuwd zojuist". |
| Update available | Update beschikbaar | Android's own (`settings:android_version_pending_update_summary`). |
| Check for updates | Controleren op updates | Android's own (`android:unsupported_compile_sdk_check_update`). |
| Replace | Vervangen | Android's own (`settings:vpn_replace`). |
| Cancel | Annuleren | Android's own (`android:cancel`). |
| Show | Tonen | Android's own (`settings:condition_expand_show`). |
| Hide | verbergen | Android's own (`settings:condition_expand_hide`). |
| Language | Taal | Android's own (`settings:app_locale_preference_title`). |
| Wi-Fi | wifi | What Android's Dutch writes throughout (`settings:wifi`), one word and lower-cased inside a sentence. |
| unmetered / metered | zonder datalimiet / per megabyte afgerekend | Android labels a connection **Met datalimiet** (`settings:wifi_metered_label`); the switch takes its opposite as it stands, and the sentences explaining it say what is billed, since that is the point the English makes. Android's own *Unmetered* label is **Gratis** (`settings:wifi_unmetered_label`), which is right for a Wi-Fi list and wrong here: nothing about the download is free. |
| Tap | Tik (op) | Android's own verb (`settings:touch_sounds_title`, `android:usb_notification_message`), in the second person like the rest. |
| Press and hold | Houd … ingedrukt | Android's own wording for a long press (`settings:accessibility_shortcut_type_hardware`). |
| app | app | Android's own (`settings:apps_dashboard_title`); *toepassing* is not what a phone says. |
| device | toestel | Kept apart from *telefoon*, which the privacy sentences use because that is what the reader is holding. |
| map data / tiles | Kaartgegevens | Names the dataset on the storage screen, and every other string that points at it uses the same word. *Tegels* appears once, in `map_needs_tiles_title`, where the English says tiles. |
| routing data | Routegegevens | Pairs with *route*: the data a route is computed from. |
| address index | Adresindex | — |
| offline data | Offlinegegevens | One word, as the Taalunie writes *offlinemodus*. |
| what's new | Wat is er nieuw | What the Dutch Play Store calls the same screen. |
| bytes | B, kB, MB, GB | Dutch writes the same symbols. |
| hour (in a duration) | u | `duration_hours_minutes` is `%1$d u %2$02d`. **u** is how Dutch abbreviates the hour in both countries — "1 u 05" — where `h` reads as foreign. It is the one unit symbol this file changes. |

## The address prompt, and the postcode that is not in it

Dutch writes the street before the number — "Damrak 12" — and that order now
holds on both sides of the screen: `address_search_hint` asks for
**"Straat, huisnummer, plaats"**, and `address_with_number` prints
**`%2$s %1$s`**, the placeholders swapped, which is what they are positional
for. `AddressQuery.parseQuery` reads a house number standing between street and
town, so the Dutch order is understood as typed.

**The postcode is deliberately left out of the prompt, and must not be added.**
A Dutch postcode is written as two groups, "1012 AB". The parser only strips a
postcode written as a single group of five digits, so "1012" would be read as a
second number in the query — and a second number makes the house number be
given up altogether (SPEC §4.3). Inviting the postcode would therefore break
the reading of every query that carries one.

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Vélo’v, Citi Bike,
BRouter, MapLibre, OpenStreetMap, GBFS — and the licence names. Unit symbols:
`m`, `km`, `ft`, `yd`, `mi`, `min`. `resources` `name` attributes, always.

## The four strings the checker flags, and why they stand

`python3 tools/check_translations.py nl` reports four strings identical to the
English. All four are correct Dutch:

- `stations_title` — **Stations** is the Dutch plural too.
- `city_stations` — **%1$d station** / **%1$d stations**, likewise.
- `welcome_later` — **Later** is the Dutch word.
- `about_privacy_title` — **Privacy** is what Android's Dutch calls the section
  (`settings:privacy_dashboard_title`).
