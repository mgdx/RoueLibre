# Norwegian Bokmål glossary

The terms `res/values-nb/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Norwegian ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Bokmål, and one Norwegian for the whole catalogue

The file is Bokmål. Nothing in it is Nynorsk, and no string carries a form the
two standards do not share by accident.

The catalogue serves one Norwegian network, but the file is read by whoever
sets the interface to Norwegian, in any of the three hundred cities. So nothing
here is written about Norway: the city, the network and the country are always
what the data says they are.

The arbiter for anything Android already names is the phone itself: 5 866
system strings extracted from a device's `framework-res.apk` and
`Settings.apk`. Every key quoted below was found in that extract; where no key
is quoted, the choice is this file's own and says so.

## Register and typography

Norwegian has one address, **du**, and it is used throughout, in the interface
and in the store texts alike (SPEC §9). Imperatives follow: *Trykk*, *Velg*,
*Prøv igjen*, *Dra listen ned*.

**The impersonal turns of the English are kept impersonal.** "No history is
kept" is **"Det lagres ingen historikk"**, "It is read from the feed" is **"Det
leses fra nettverkets egen feed"**. Norwegian's *det*-passive and s-passive do
this without effort, so no privacy sentence in the file has to invent a "vi"
the application has nobody behind it to mean.

Quotation marks are **« »**, with no space inside them, which is what Norwegian
prints. The dash that breaks a sentence is **–** with a space on either side,
not the em dash the English file uses; that is the one piece of punctuation
changed inside a format-only string, `city_label` (`%1$s – %2$s`).

**An ellipsis carries a space before it.** "Søker …", "Beregner sykkelturen …".
That is Android's own Norwegian typography — `android:loading` is *Laster inn
…*, with a plain space and then U+2026 — and 84 lines of the extract print it
that way. It is not a slip.

Nothing in the file is escaped, because no straight apostrophe is written
anywhere in it.

Norwegian compounds are written as one word, and that is what lengthens the
labels. Where a label had to stay short, it takes the wording Android itself
uses, or a shorter turn, rather than the literal translation. The longest
labels the screens carry:

| String | Norwegian | Characters |
|---|---|---|
| `settings_map_filters_hide_empty` | Skjul stasjoner uten noe å tilby | 32 |
| `settings_map_filters_hide_out_of_service` | Skjul stasjoner ute av drift | 28 |
| `download_unmetered_only` | Last bare ned uten datamåling | 29 |
| `settings_opening_title` | Åpne appen som standard på | 26 |
| `about_open_licences` | Lisenser for komponentene | 25 |
| `station_open_in_navigation` | Åpne i en navigasjonsapp | 24 |
| `station_as_origin` / `station_as_destination` | Herfra / Hit | 6 / 3 |

The last pair are the only two buttons of the application placed side by side
on one row. They are read as a pair because they are the same kind of word —
one adverb of place each, one saying *from here* and one saying *to here*. The
longer *Reis herfra* / *Reis hit* was written first and dropped: the verb adds
nothing the row does not already say, and it doubled the width of both.

## The vocabulary

| English | Norwegian | Why |
|---|---|---|
| journey | reise | The whole door-to-door thing: the screen, the settings section, the button, the errors. It is what a Norwegian travel planner calls a door-to-door trip, and it leaves *rute* free for what English also calls a route. Short, which matters on `journey_compute` ("Beregn reisen") and `journey_frame` ("Vis hele reisen"). |
| ride | sykkeltur | The bike leg alone, inside a journey. Plain *tur* was not used: Norwegian uses it for a whole outing as readily as for one leg, so the elevation profile and the "own bike" wait would have read as the whole thing. Where the ride is a manner rather than an object, the file says **på sykkel** ("derav 12 min til fots og 9 min på sykkel"). |
| route | rute | Only in `journey_no_route` — "Ingen farbar rute mellom disse to punktene" — and in `station_beyond_area`: the line on the ground, not the planned journey. Norwegian has the same pair English does, *reise* / *rute*, so no third word had to be invented. |
| journey data (store texts) | reisedata | "Ingen reisedata lagres". It cannot collide with `dataset_routing`, which is **Rutedata** and *is* stored on the device, because the two words are already kept apart above. |
| bike | sykkel / sykler | There is no shorter Norwegian word, and none is wanted. |
| bike-share bikes, as a product | delesykler | Only in the store texts and on the welcome page, where the thing has to be named before it is known. Inside the interface the context is settled and *sykkel* is enough. |
| station | stasjon | A bike-share station. |
| railway station | togstasjon | Norwegian *stasjon* means both, so `address_search_prompt_message` — whose English comment says its "stations" are railway ones — has to say **togstasjoner** in full. |
| dock (free) | ledig plass | What one returns a bike into, counted as available: "6 sykler, 26 ledige plasser". *Ledig* is also what the file says of an available bike ("Vis ledige sykler"), and that is not a collision: the nouns are what carry the difference, as they do in Norwegian generally. The **storage** sense is deliberately kept away from it: free disk space is *ledig lagringsplass* (`settings:insufficient_storage` = *Ikke nok lagringsplass*, `android:low_internal_storage_view_title` = *Lite ledig lagringsplass*), so `dataset_rejected_transfer` cannot be read as talking about docks. |
| dock (capacity) | stativplass | The same object counted as a total. The two figures do meet on one row, but through two different resources rather than one: `counterpart_docks` writes the free count beside a station's disc, and `station_detail_with_capacity` writes the capacity on the supporting line under it — "26 ledige plasser" over "1,2 km · 30 stativplasser". English says "dock" for both; Norwegian does not have to. *Stativplass* is the place in the *sykkelstativ*, the rack a shared bike locks into. **This is this file's own coinage and no Android key stands behind it**; it was chosen because read one under the other, no shorter word tells the two figures apart. **It is confined to the interface.** The store description says *faste sykkelstativer* instead: a coinage works where a figure stands beside it and the screen explains it, and the listing is read before the application is installed, with neither. |
| dock | *never* “stativ” alone, *never* “terminal” | Those are the rack as an object and the payment post, not the point a bike attaches to. |
| mechanical / electric | mekanisk / elektrisk | Kept as adjectives, as in English, because the counts are elliptical: "4 mekaniske · 2 elektriske" stands for "4 mekaniske sykler". Norwegian inflects the adjective after a plural numeral and not after 1, which is why `bikes_mechanical` and `bikes_electric` carry two different forms where English's carry one. |
| electric bike, as an object | elsykkel | **A deliberate departure from the line above, in three strings.** *Elsykkel* is the noun Norwegian has for the thing — the word the traffic rules, the shops and the riders use — and "på din egen elektriske sykkel" reads as a translation where "på din egen elsykkel" reads as Norwegian. So the adjective labels the *choice* (`settings_own_bike_kind_electric` = "Elektrisk", `map_bikes_electric` = "Elektriske") and the noun names the *object*: `journey_own_bike_electric_only`, its `_climb` twin, and `journey_bike_kind_electric_description` = "En elsykkel med tråkkhjelp". That last also settles what "electric" means here: a pedelec, never a *moped*. **Do not "correct" the three back to the adjective.** |
| pedal assist | tråkkhjelp | The Norwegian term for what makes a pedelec a pedelec, and the one word that rules out a throttle. |
| to pedal | tråkke | One root across the whole file, interface and store texts alike: *tråkkhjelp* for the assistance, "En sykkel jeg **tråkker** selv" for the mechanical choice, "mot å **tråkke** litt lenger" in the changelog. The competing verb *trå* (*trår*) is equally good Norwegian on its own and was written here first; it was dropped because it left the file naming one action with two stems, and *tråkkhjelp* had already fixed which one. |
| pace (walking) | gangtempo | A pace is not a speed, which `values/strings.xml` says above the string. *Tempo* is a pace; *hastighet* is the figure nobody has measured about themselves, and is not used. Slow / Normal / Brisk are **Langsomt / Normalt / Raskt**, in the neuter, because they agree with the *tempo* they answer — "et raskt tempo" is the ordinary Norwegian for a brisk walk. Android's own *Treg* (`settings:speed_label_slow`) is the common-gender form, right for the *hastighet* it describes there and wrong under this title; the everyday *sakte* is invariable and would break the run of three neuters. |
| climb | stigning | The metres climbed, over a leg or over the whole journey: "120 m stigning". |
| location / position | posisjon | **Where Danish splits, Norwegian does not, and the extract is why.** Android's Norwegian calls the system feature, the permission and the point on the map by the one word: `android:permgrouplab_location` and `settings:location_settings_title` are both **Posisjon**, and the switch that turns the feature on is `settings:location_settings_primary_switch_title` = *Bruk posisjon*. So `map_location_unavailable` says "Sjekk at posisjon er slått på" — naming the switch — and `journey_source_my_position` says "Posisjonen min", and no reader has to learn two words for one thing. |
| network (bike-share) | nettverk | The operator whose bikes these are. It is never used of a data connection — see the next row — so `error_malformed` can say "nettverkets feed" without a reader wondering which network is meant. |
| network (connectivity) | internett / tilkobling | **A split the English does not make, and the reason for it.** English writes "network" for both the bike-share operator and the data connection, and Norwegian *nettverk* would inherit the ambiguity on the very screens where both appear. So the connection is **internett** where the sentence is about working without one ("Tegner kartet uten internett", "virker uten internett") and **tilkobling** where it is about the connection as an object — Android's own noun, `settings:mobile_data_no_connection` = *Ingen tilkobling*. |
| conurbation | byområde | The city screen serves a metropolitan area rather than a municipality, and *by* is kept for the shorter word the section title and the screen title need. |
| Settings | Innstillinger | Android's own (`settings:settings_label`), including in the system path quoted in `about_links_body`: "Innstillinger → Apper → … → Åpne som standard → Legg til en link", every step of which is Android's own Norwegian (`settings:apps_dashboard_title`, `settings:launch_by_default`, `settings:app_launch_add_link`). |
| Theme | Tema | Android's own: *Mørkt tema* (`settings:dark_ui_mode`), *Enhetstema* (`settings:device_theme`). Light / Dark are **Lyst / Mørkt**, in the neuter, agreeing with the *tema* they sit under. |
| Display (section) | Skjerm | Android's own name for the settings screen that holds the theme and the text size (`settings:display_settings`, `settings:display_category_title`). |
| Storage | Lagring | Android's own for the screen (`settings:storage_settings`, `settings:storage_label`). Everywhere another string points at it, the file says **fra Lagring-skjermen** rather than translating "storage screen" literally. |
| Delete | Slett | Android's own (`android:delete`, `settings:delete`), for what destroys: a city's data, a dataset. |
| Remove (from a list) | Fjern | Android's own, and Norwegian keeps the pair English keeps: *Remove* is **Fjern** (`settings:remove`, `android:kg_reordering_delete_drop_target_text`) against *Delete* = **Slett**. So "Fjern fra favoritter" takes a station out of a list and "Slett" destroys data, with no third word needed. |
| Clear (a search) | Tøm | **A departure from Android, and the reason is one screen.** Android's Norwegian for emptying a search field is *Slett søket* (`android:searchview_description_clear`, `settings:abc_searchview_description_clear`) — the same verb as *Delete*. On the city screen `city_search_clear` sits a few pixels from `city_delete`, where **Slett** erases hundreds of megabytes; one verb may not do both. So the file says **Tøm søket**, from Android's other verb for emptying a field (`settings:proxy_clear_text` = *Tøm*), in all four places a search is cleared. |
| Refresh | Oppdater | **Norwegian has one verb where English has two, and the file lets it.** *Refresh* and *Update* are both **oppdatere**: `action_refresh` is "Oppdater", `freshness_fresh` is "Oppdatert %1$s", and the storage screen says "Se etter oppdateringer" (`android:unsupported_compile_sdk_check_update`) and "Oppdater appen" (`android:autofill_update_yes`). Nothing is lost, because no screen carries both senses: the list refreshes availability, the storage screen updates datasets, and the app updates itself. |
| Out of service | Ute av drift | Android's own (`settings:radioInfo_service_out`), and the ordinary Norwegian for a station or a vehicle that is not running. `settings_map_filters_hide_out_of_service` echoes it word for word. |
| Frozen data | Foreldede data | `freshness_stale`, shown when the feed has stopped moving. *Frosne* reads as a crash rather than as data that has stopped being refreshed. |
| Try again | Prøv igjen | Android's own (`settings:audio_streams_dialog_retry`, `settings:security_settings_fingerprint_enroll_dialog_try_again). The extract also holds *Prøv på nytt* (`android:lockscreen_password_wrong`); the shorter one was taken because it sits on a button. |
| Continue | Fortsett | Android's button word (`android:autofill_continue_yes`, `settings:lockpattern_continue_button_text`), on the welcome carousel and the what's-new screen. |
| Skip | Hopp over | Android's own (`android:skip_button_label`, `settings:skip_label`). |
| Back | Tilbake | Android's own, on the toolbar arrow (`settings:back`, `android:back_button_label`). |
| In use | I bruk | Android's own (`android:media_route_status_in_use`), on the city already selected. |
| just now | nå nettopp | Android's own (`settings:time_unit_just_now`), lower-cased because it is never read alone: `FreshnessText.toStatusLine` puts it inside `freshness_fresh` or inside `freshness_stale` — and **"Oppdatert nå nettopp"** is verbatim what Android itself writes (`settings:no_carrier_update_now_text`). The other freshness lines carry their own **for … siden**, for the same reason and after the same model: `settings:no_carrier_update_text` is *Oppdatert for ^2 siden*. |
| Update available | Oppdatering tilgjengelig | **A shortening, and a departure.** Android's own is the whole sentence *En oppdatering er tilgjengelig* (`settings:android_version_pending_update_summary`), 29 characters, which is a notification summary and not a badge. This one sits on a dataset row beside a size and a date, and takes the noun phrase instead. |
| Replace | Erstatt | Android's own (`settings:vpn_replace`). |
| Cancel | Avbryt | Android's own (`android:cancel`, and 30 further lines of the extract). |
| Show | Vis | Android's own (`settings:condition_expand_show`). |
| Hide | Skjul | Android's own (`settings:condition_expand_hide`). |
| Language | Språk | Android's own (`settings:app_locale_preference_title`). |
| Install / Installed | Installer / Installert | Android's own (`settings:install_text`, `settings:installed`). |
| Wi-Fi | wifi | **Android's Norwegian does not write the hyphen.** `settings:wifi` and `settings:wifi_settings` are both **Wifi**, one word, and inside a sentence the extract lower-cases it — *Aktivér wifi-anrop* (`settings:wifi_calling_settings_activation_instructions`). The file follows: "Du er ikke på wifi", "Koble til wifi". |
| hotspot | wifi-sone | Android has the word and it is not the English loan: `settings:wifi_hotspot_checkbox_text` = **Wifi-sone**, `settings:tether_settings_title_all` = *Wifi-sone og internettdeling*, `settings:hotspot_connection_category` = *Tilkobling til wifi-sone*. It is common gender, which is what settles the article in `download_unmetered_only_description`: *en wifi-sone med datagrense*. |
| unmetered / metered | uten datamåling / måles per megabyte | Android labels a connection **Med datamåling** / **Uten datamåling** (`settings:wifi_metered_label`, `settings:wifi_unmetered_label`). The switch takes the second, as "Last bare ned uten datamåling" so it stays on one line; the sentences explaining it say what is billed — *måles per megabyte* — since that is the point the English makes. |
| download (noun) / to download | nedlasting / laste ned | Norwegian has its own noun and does not need the loan Danish keeps: "Én by, én nedlasting", "Nedlastingen fortsetter der den stoppet", "Last ned %1$s", "Last ned likevel", "%2$s å laste ned". What a download costs is said with **tære på**, not *veie tungt*: in Norwegian something weighs heavily on a decision, never on a data plan. |
| Tap | Trykk (på) | Android's own verb in the imperative — "Trykk for flere alternativer." (`android:usb_notification_message`) — in the second person like the rest. |
| Press and hold | Trykk og hold inne | Android's own phrasing (`android:content_description_sliding_handle` = *Trykk og hold inne.*), for the long press that reorders the favourites. |
| app | app | Android's own; the plural in the settings path is **Apper** (`settings:apps_dashboard_title`). |
| device | enhet | Android's word for a device. Kept apart from **telefon**, which the privacy sentences use because that is the word the English uses there and it is what the reader is holding. |
| map data / tiles | Kartdata | Names the dataset on the storage screen, and every other string that points at it uses the same word — including `map_needs_tiles_title`, **"Kartdataene mangler"**, where the English says "tiles". Norwegian has no settled word for a map tile that a reader would recognise on a first screen, and naming the dataset the reader is about to go and install says more than *fliser* would. The dataset is made the subject there rather than the map, which keeps the one name without writing *Kartet … kartdata* twice in four words. |
| routing data | Rutedata | Pairs with *rute*: the data a route is computed from. |
| address index | Adresseindeks | Also what `incoming_needs_index` calls it, where the English says "the offline index": one name for one dataset. |
| offline data | Offlinedata | One word, as Norwegian writes *offlinemodus*. |
| street name | gatenavn | The word the Norwegian address register uses, and what `address_search_hint` asks for. |
| what's new | Nyheter | What the Norwegian Play Store calls the same thing. |
| tracker | sporing | "ingen sporing". The Norwegian noun for the practice rather than for the piece of code, which is what the sentence is actually about, and what the privacy pages of Norwegian software say. |
| mile | mile / miles | **A false friend, and the one that would have been read as fact.** Norwegian *mil* is the Scandinavian mile of ten kilometres, still in everyday use; the English mile is *mile*, plural *miles*. So `settings_units_us_description` is **"Fot og miles"** and `settings_units_uk_description` **"Yard og miles"** — *yard* takes no plural ending in Norwegian — and the changelog says "miles og fot i USA". The symbols `mi`, `ft` and `yd` are untouched: they are what the road signs of those two countries print. |
| over (a distance) | over | The eight journey summaries keep the English preposition — "på din egen sykkel, over 3,2 km" — because the fragment is read after a duration: "24 min · på din egen sykkel, 3,2 km" would juxtapose the figure instead of saying it was covered. |
| bytes | B, kB, MB, GB | Norwegian writes the same symbols. |
| hour (in a duration) | t | `duration_hours_minutes` is `%1$d t %2$02d`. **t** is how Norwegian abbreviates the hour — "1 t 05" — where `h` reads as foreign. It is the one unit symbol this file changes. |

## Two departures the checker cannot see

**`journey_bike_kind_any` is "Enhver sykkel" and not "Alle sykler".** The
literal reading of "Any bike" would be *Alle sykler*, which is already
`map_bikes_all_kinds` — the map's own label, and a different thing: the map
counts every bike, where the journey screen asks for no particular kind. Two
chips on two screens with one label would be read as one setting. So the
journey screen takes *Enhver*, which is what the English distinction actually
means.

**`journey_no_mechanical_nearby` and `journey_no_electric_nearby` quote that
label word for word** — "Velg «Enhver sykkel», eller prøv igjen senere" — where
the English says "Ask for any bike" and names no control. What it buys: the
reader is standing in front of the journey screen with the Mekanisk or
Elektrisk chip selected, and the way out is one tap on the chip beside it.
What it costs: the day `journey_bike_kind_any` is reworded, these two lines say
something the screen does not. **If that label changes, change these two with
it** — or drop the quotation and go back to describing the choice.

## The switch that had to quote a phrase rather than a word

English writes `settings_map_filters_hide_empty` as "Hide the stations with
nothing to offer" and then, under it, explains what "**Nothing**" is read
against. Norwegian cannot isolate that word: the switch reads **"Skjul
stasjoner uten noe å tilby"**, where the negation lives in *uten* and *noe*
alone means the opposite of what English's "nothing" means.

So the hint quotes the whole phrase instead — **«Uten noe å tilby» følger det
kartet teller** — rather than quoting a single word that would invert the
sense. It is longer than the English and it is the only wording that stays
true to it.

## The three dataset names, and why nothing has to agree with them

`dataset_imported`, `dataset_deleted` and `dataset_absent` all put a past
participle after a name that varies: **Kartdata**, **Rutedata**,
**Adresseindeks**. In Norwegian this asks nothing of the translator. Both verbs
belong to the *-et* class, whose participle is invariable when it is used
verbally — "Kartdata installert", "Adresseindeks slettet", "Ikke installert" —
and the three names are neuter or plural besides, so even an adjectival reading
would land on the same form. The Spanish, Italian and Portuguese files had to
make the three names share a gender to agree once; Norwegian did not have to
choose.

## The one place a pronoun had to pick a gender

`city_here_use` is **"Bruk det"**, and `city_proposal_body` ends **"Skal vi
bruke det?"**. What the pronoun stands for is `%1$s` from the line above: a
network's name — "Oslo Bysykkel", "Donkey Republic", "nextbike" — whose gender
Norwegian cannot know. The neuter *det* is the form Norwegian uses for a
referent of indeterminate gender, and it is what the file takes. Naming the
thing instead ("Bruk dette nettverket") was tried and dropped: the button sits
directly under the sentence that names it, and repeating the noun reads as
though a second network were meant.

## The network's name is not the city's

`city_delete_description`, `city_delete_body` and `city_deleted` take `%1$s` =
the **network's** name, which for 328 of the catalogue's 331 entries is not the
city's. So none of the three says *byen*: "Slett dataene for %1$s", "Alle
offlinedata for %1$s slettes", "Data for %1$s er slettet". The English says
"Data **for** %1$s" and says nothing about a city; Norwegian says the same.

## The address prompt, and the postcode that is not in it

Norwegian writes the street before the number — "Karl Johans gate 12" — and
`address_search_hint` asks for **"Gatenavn, nummer, by"** accordingly.
`AddressQuery.parseQuery` reads a house number standing between street and
town, so the Norwegian order is understood as typed.

**The postcode is deliberately left out of the prompt, and must not be added.**
A Norwegian postcode is four digits, "0150". The parser only strips a postcode
written as a single group of **five** digits, so "0150" would be read as a
second number in the query — and a second number makes the house number be
given up altogether (SPEC §4.3). Inviting the postcode would therefore break
the reading of every query that carried one.

`address_locality` keeps its English order, `%1$s %2$s`, because Norwegian
writes the postcode before the place too: "0150 Oslo".

**How a result is printed is a separate matter, and is not this file's to
decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3): "Karl Johans gate 12" is how an Oslo address is written
for every reader of the application, and "12 rue Nationale" is how a Lyon one
is written for a Norwegian reader. The layouts are a table in
`core/address/AddressLayout.kt`, keyed on the language of the **address base**.

**Norwegian is present in that table** and is right: `numberComesFirst = false`,
a space before the street. Nothing there needed changing for this translation.

## Words that are not translated

Product and network names — Roue Libre, Vélib', V'lille, Vélo'v, Citi Bike,
Donkey Republic, BRouter, MapLibre, OpenStreetMap, GBFS — and the licence
names. Unit symbols: `m`, `km`, `ft`, `yd`, `mi`, `min`. `resources` `name`
attributes, always.

## The three strings the checker flags, and why they stand

`python3 tools/check_translations.py nb` reports three strings identical to the
English. All three are correct Norwegian:

- `settings_theme_system`, `settings_units_system`, `settings_language_system`
  — **System** is Android's own Norwegian for it
  (`settings:header_category_system`, `android:notification_app_name_system`).

`about_version` is not among them: Norwegian writes **Versjon**
(`settings:vpn_version`).
