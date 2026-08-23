# Hungarian glossary

The terms `res/values-hu/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Hungarian ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

The lexicon quoted below is the 5 904 system strings extracted from a phone's
`framework-res.apk` and `Settings.apk`. **Every key cited here was grepped in
it**; where no key is cited, the choice is this file's own and says so.

## Register: a deliberate departure from Android's Hungarian

Android's own Hungarian addresses the reader **formally** — *Próbálja újra*
(`android:lockscreen_pattern_wrong`), *Koppintson az alkalmazás letöltéséhez*
(`settings:interact_across_profiles_install_app_summary`), *Törli a privát
területet?* (`settings:private_space_delete_header`). This file does not.

Two rules, in this order:

1. **Controls are nominal**, which is what Android itself does most of the
   time and what sidesteps the T/V question altogether: *Letöltés*, *Törlés*,
   *Frissítés*, *Megjelenítés* (`settings:condition_expand_show`), *Kihagyás*
   (`android:skip_button_label`), *Tovább* (`settings:next`), *Mégse*
   (`android:cancel`), *Csere* (`settings:vpn_replace`), *Használatban*
   (`android:media_route_status_in_use`), *Rendelkezésre áll frissítés*
   (`settings:android_version_pending_update_summary`).
2. **Running prose uses *tegezés*** — *Próbáld újra később*, *Válaszd ki*,
   *Koppints ide* — where Android would write *Próbálja*, *Válassza*,
   *Koppintson*.

The reason for the departure: this application has no operator behind it, and
its whole argument is that nobody is. *Magázás* is the register of an
institution speaking to a customer, and Roue Libre has neither. The French,
which set the project's tone, ships *tutoiement* for the same reason
(`docs/french-glossary.md`). A contributor who wants magázás back must change
every verb in the file, not one screen of them.

**The English is scrupulously impersonal** wherever it promises that nothing is
kept — "No history is kept", "It is read from the feed" — and Hungarian keeps
that, with the impersonal turns the language has for it: *Semmilyen előzmény
nem marad meg*, *Az útvonaltervek ezen a telefonon készülnek*, *A keresett
címek senkihez nem jutnak el*, *Az adatcsomagok csak kérésre töltődnek le*.
There is no *mi* anywhere in the file and none should be added: an application
whose argument is that nobody is behind it must not ask the reader to trust a
"we".

## Typography

Quotation marks are **„ … ”** — `stations_no_match_message`,
`error_feed_unavailable`, `dataset_rejected_format`, `dataset_delete_body`,
`settings_map_filters_hide_empty_hint`. The dash that breaks a sentence is **–** with a space on either side, not the em dash
the English file uses; that is the one piece of punctuation changed inside a
format-only string, `city_label` (`%1$s – %2$s`). No space stands before `:` or
`;`. The apostrophe, where a network's name carries one (*Vélib’*, *Vélo’v*,
*V’lille*), is **’** (U+2019) and never the straight quote, which is why
nothing in the file is escaped.

## Plurals: two categories, one reading

Hungarian's CLDR categories are `one` and `other`, and **the noun behind a
numeral stays singular**: *1 kerékpár*, *3 kerékpár*, *20 kerékpár* — never
*3 kerékpárok*. So both items of every `<plurals>` here carry the same text.
Both are still written out, because `one` is what a count of 1 resolves to and
a wrong `one` would be read every time a station holds a single bike. Neither
may be removed: the scaffolding is CLDR's, not this file's.

## The article, and why no placeholder ever has one in front of it

The definite article is **a** before a consonant and **az** before a vowel, and
what follows a placeholder is unknowable at build time: *a Batthyány tér*, but
*az Erzsébet tér*; *a MOL Bubi*, but *az Anywheel*. Hungarian software has a
convention for this — writing `a(z)` — and this file **deliberately does not
use it**: it reads as a form to be filled in, not as an application speaking,
and there are twenty-odd such lines. Every one is rephrased instead.

Hungarian is also **agglutinative**: a case is a suffix, and it agrees in vowel
harmony with the word it lands on. An ending glued onto a `%1$s` would be wrong
half the time. Not one line does that either.

Six devices carry the whole weight, and each is worth knowing before it gets
"fixed":

| String | What it does | Why |
|---|---|---|
| `journey_step_to_station`, `journey_step_ride` | `Gyaloglás az állomásig: %1$s` | The case falls on *állomás*, a noun this file owns, so *az* is fixed. The station's name follows behind a colon, exactly as the feed published it. |
| `station_address_nearby` | `A közelben: %1$s` | The argument is `address.streetName` — a street **or** a square, arriving as it stands. A colon turns the line into a label, which declines nothing. |
| `city_delete_description`, `city_deleted`, `dataset_delete_description`, `dataset_imported`, `dataset_deleted`, `storage_download_pending` | `Adatok törlése: %1$s`, `Letöltés: %1$s`, `Telepítve: %1$s` | Content descriptions, snackbars and buttons, where a label with a colon is shorter and reads better than a sentence — and takes neither article nor ending. Dataset names decide their own article too: *a térképadatok*, but *az útvonaladatok*. |
| `map_outside_city_message`, `map_outside_city_brief`, `city_here_body`, `city_here_installed_body`, `city_proposal_body` | `… A kiszolgált hálózat: %1$s.`, `Ezt a területet másik hálózat szolgálja ki: %1$s.` | The ending falls on *hálózat* / *terület*; the whole label — *MOL Bubi – Budapest* — sits behind the colon in apposition. The English puts the label in subject position, which Hungarian cannot do without an article. |
| `journey_climb`, `journey_detail_profile_description`, `download_held_back_body`, `download_stopped_body`, `download_waiting_for_unmetered` | `%1$s szintemelkedés`, `A letöltés mérete: %1$s.` | A distance or a size is a **unit symbol**, and each symbol takes a different ending: *m-t*, *ft-ot*, *mi-t*, *km-t*. None of them is ever declined here — the figure stands before a bare noun, or behind a colon. |
| `dataset_rejected_version` | `A fájl formátumverziója %1$d, … pedig %2$d formátumverziót olvas.` | No article stands in front of either figure: it would be *az 1*, *a 2*, *az 5*, *a 6* — decided by a number this file cannot see. |

`city_delete_body` deserves its own line. Quoting the name — *A „%1$s” hálózat
minden adata…* — would have put the article right back in front of the
placeholder, because the article agrees with what is inside the quotation
marks. It reads **`Ennek a hálózatnak minden offline adata törlődik: %1$s.`**
instead: a demonstrative, which is fixed, and the name behind a colon.

**`city_delete_description`, `city_delete_body` and `city_deleted` are handed
the NETWORK's name, not the city's** — `CityAdapter.kt:111`,
`CityFragment.kt:279` and `:294` all pass `city.displayName`, and 328 of the
331 catalogue entries carry a `displayName` of their own; every Czech network
is called *nextbike*. Writing *a %1$s város adatai* would have made a Brno
reader read "the data of the city nextbike". None of the three says *város*,
and the English never does either.

## The address prompt

`address_search_hint` is **„Utca, házszám, település”** — street, then house
number, then settlement, which is the order Hungary writes an address in:
*Széchenyi István tér 7, Budapest*. `AddressQuery.parseQuery` has read a house
number standing **between the street and the town** since the pilot, precisely
so that each language may write this line in its own order rather than in
English's, so *Széchenyi István tér 7 Budapest* keeps its door number.

*Település* rather than *város*: the index carries villages as well as cities,
and *város* would be wrong for them. It is also the word Hungarian
administration itself uses for the settlement line of an address.

**The postcode is deliberately not invited, and the reason is arithmetic.**
`looksLikePostcode` (`AddressQuery.kt:215`) strips a word only when it is
`POSTCODE_LENGTH == 5` digits. **Hungary writes its postcodes as four digits**
— 1051, 4025, 9700. A Hungarian postcode is therefore *not* stripped: it stays
in the query as a stray token, and worse, it is a number, so it trips the first
guard — a house number is given up as soon as the query holds a second number.
Inviting *1051 Budapest, Széchenyi István tér 7* in the prompt would cost the
reader the door number. Germany left the postcode out because five digits and a
house number stack badly; Hungary leaves it out because four digits are not
recognised as a postcode at all.

The second guard — no house number read between street and town when a stop
word stands beside it — protects streets named after a date. Hungary has plenty
(*Március 15. tér*, *Október 6. utca*), and it writes them with the ordinal
**in front**, followed by a full stop. That is the shape the guard was built
for, and the prompt does not invite a second number that would stress it.

**The layout of an address is not this file's business.** A Budapest address
reads *Széchenyi István tér 7* to a reader in French, and a Lyon address reads
*12 rue Nationale* to a reader in Hungarian. That lives in
`core/.../address/AddressLayout.kt`, keyed on the language of the address base
(SPEC §4.3); this language decides only the words around an address. The
figures are the exception the specification already settles: digits and
separators follow the reader (SPEC §9).

## The vocabulary

| English | Hungarian | Why |
|---|---|---|
| journey | **útvonalterv** | The whole door-to-door thing: the screen title, the settings section, the button, every failure message. It is the word Hungarian route planners use for a planned trip. |
| ride | **kerékpározás** (the act), **kerékpáros szakasz** (the leg as an object) | Two forms of one word, and the split is deliberate: `journey_step_ride` and `journey_summary` name an activity, where `journey_detail_profile`, `journey_detail_profile_description` and `journey_computing_own_bike` name a stretch of the journey that has a length and a gradient. Never *útvonalterv*, or the two screens stop being about the same object. |
| route | **útvonal** | Only in `journey_no_route`: the line on the ground, not the planned journey. This is why *útvonal* alone is left free and the journey is *útvonalterv*. |
| routing (the computation) | **útvonaltervezés** | `about_attribution_brouter`, `journey_open`. |
| station | **állomás** | A bike-share station. |
| station (railway) | **vasútállomás** | `address_search_prompt_message` means railway stations by "stations", and says so in its comment. Writing *állomás* there would have offered to search for bike stations, which is the one thing that screen does not do. |
| dock (free) | **szabad hely** | What a bike is returned into, counted as available: `docks_available`, `counterpart_docks`, `mode_docks`. |
| dock (capacity) | **dokkoló** | The same object counted as a total, which is a different figure standing beside the first on the same sheet: *12 szabad hely · 30 dokkoló*. English says "dock" for both; Hungarian does not have to. |
| dock | *never* **terminál** | A *terminál* is the payment machine, not the point a bike attaches to. |
| free space (of the device) | **tárhely** | `dataset_rejected_transfer`, `error_local_storage_download`. **This is the one place the file departs from the entry above it**: writing *szabad hely* there would have said "free dock" on a screen about disk space. Android's own word is *Tárhely* (`settings:storage_label`), and it is used for the storage screen's title too. |
| bike, mechanical | **hagyományos** | *Hagyományos* against *elektromos* is what Hungarian bike-share operators write. *Mechanikus* is an engineering word and reads as machinery, not as a bicycle without a motor. |
| bike, electric | **elektromos** | Pedal-assist, not a moped, which `journey_bike_kind_electric_description` says: *Pedálrásegítéses kerékpár*. |
| pace (walking) | **tempó** | A pace is not a speed: `values/strings.xml` says so above the string, and *gyaloglási sebesség* would have said the opposite. |
| Delete / Remove | **Törlés** / **Eltávolítás** | Two words, and Android itself keeps them apart: *Törlés* (`android:delete`) destroys, *Eltávolítás* (`settings:remove`) takes out of a list — which is what `station_favourite_remove` does to a favourite. |
| Clear | **törlés** (of a search field) | *Keresés törlése*, emptying a field. Android uses *Törlés* for this too (`settings:clear`). |
| Try again | **Újrapróbálkozás** (button), **próbáld újra** (prose) | Both from the lexicon: `settings:security_settings_fingerprint_enroll_dialog_try_again` and `android:lockscreen_pattern_wrong`, the latter turned to *tegezés*. |
| Out of service | **Nem működik** | `settings:radioInfo_service_out`. |
| just now | **az imént** | `settings:time_unit_just_now`, lower-cased because it joins *Frissítve %1$s*. |
| In use | **Használatban** | `android:media_route_status_in_use`. |
| Settings | **Beállítások** | `settings:settings_label`. Also the word `about_links_body` quotes when it spells out the system path. |
| Display | **Megjelenítés** | `settings:display_category_title` for the settings section; *Kijelző* (`android:notification_channel_display`) is the physical screen and is not what this section is about. |
| System | **Rendszer** | `settings:header_category_system`, for the theme, the units and the language. |
| Privacy | **Adatvédelem** | `settings:privacy_dashboard_title`. |
| Storage | **Tárhely** | `settings:storage_label`. |
| Language | **Nyelv** | `settings:app_locale_preference_title`. |
| Unmetered | **nem forgalomkorlátos** | `settings:wifi_unmetered_label`. The setting names what is billed rather than Wi-Fi, exactly as the English does. |
| Update available | **Rendelkezésre áll frissítés** | `settings:android_version_pending_update_summary`. |
| Replace | **Csere** | `settings:vpn_replace`. |
| Open by default → Add link | **Megnyitás alapértelmezés szerint → Link hozzáadása** | `settings:launch_by_default` and `settings:app_launch_add_link`; `about_links_body` quotes a real path, so it must quote it in the words the phone shows. |
| About | **Névjegy** | **No lexicon row applies**: Android's *A telefonról* (`settings:about_settings`) is the phone's about screen, not an application's. *Névjegy* is what Hungarian desktop and mobile applications call their own. |
| Light / Dark (theme) | **Világos** / **Sötét** | *Sötét* is Android's (`settings:dark_ui_mode`, *Sötét téma*). **There is no lexicon row for a light theme**; *Világos* is this file's choice, by symmetry. |
| conurbation | **agglomeráció** | The word Hungarian uses for a city and its ring, which is what the catalogue's entries are. |
| offline data | **offline adatok** | *Offline* is naturalised in Hungarian and needs no translation. |
| map data / tiles | **térképadatok** | The name the storage screen gives the dataset, and the name every other string uses for it — including `map_needs_tiles_title`, which drops the English "tiles" rather than introduce *csempe* on one screen. |
| routing data | **útvonaladatok** | — |
| address index | **címindex** | — |
| dataset | **adatcsomag** | The unit the storage screen installs and deletes. |
| climb | **szintemelkedés** | Metres gained, on a leg or over a whole journey. |
| minute / hour (duration) | **perc** / **ó** | `duration_minutes` is *%1$d perc* and not *%1$d min*: Hungarian writes the word out, and it is what every Hungarian transport application shows. *ó* is the abbreviation, used only in `duration_hours_minutes` where the hours and the minutes share one narrow line. |

## Words that are not translated

Product and network names — Roue Libre, Vélib’, Vélo’v, V’lille, Citi Bike,
MOL Bubi, BRouter, MapLibre, OpenStreetMap, GBFS, Wi-Fi — and the licence
names. Byte units stay **B, kB, MB, GB**, which is what Hungarian writes.
Distance symbols stay **m, km, ft, yd, mi**. `resources` `name` attributes,
always.

## Store texts

`fastlane/metadata/android/hu/` holds the same vocabulary as the interface:
*útvonalterv*, *állomás*, *szabad hely*, *hagyományos* / *elektromos*,
*agglomeráció*, *adatcsomag*. The short description is 72 characters, under the
80 the store allows.
