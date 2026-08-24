# Danish glossary

The terms `res/values-da/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Danish ones over three screens, and so that a contributor can correct
one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## One Danish for five networks, and for the rest of the catalogue

The catalogue serves five Danish networks — Copenhagen, Aarhus, Odense,
Aalborg and Ballerup — but the file is read by whoever sets the interface to
Danish, in any of the three hundred cities. So nothing here is written about
Denmark: the city, the network and the country are always what the data says
they are.

The arbiter for anything Android already names is the phone itself: 5 888
system strings extracted from a device's `framework-res.apk` and
`Settings.apk`. Every key quoted below was found in that extract; where no key
is quoted, the choice is this file's own and says so.

## Register and typography

Danish has one address, **du**, and it is used throughout, in the interface and
in the store texts alike (SPEC §9). Imperatives follow: *Tryk*, *Vælg*, *Prøv
igen*, *Træk listen ned*.

**The impersonal turns of the English are kept impersonal.** "No history is
kept" is **"Der gemmes ingen historik"**, "It is read from the feed" is **"Det
læses fra netværkets eget feed"**. Danish's *der*-passive does this without
effort, so no privacy sentence in the file has to invent a "vi" the application
has no one behind it to mean.

Quotation marks are **» «**, closing inwards, which is what Danish prints.
The dash that breaks a sentence is **–** with a space on either side, not the
em dash the English file uses; that is the one piece of punctuation changed
inside a format-only string, `city_label` (`%1$s – %2$s`). Nothing in the file
is escaped, because no straight apostrophe is written anywhere in it.

Danish compounds are written as one word, and that is what lengthens the
labels. Where a label had to stay short, it takes the wording Android itself
uses, or a shorter turn, rather than the literal translation. The longest
labels the screens carry:

| String | Danish | Characters |
|---|---|---|
| `settings_map_filters_hide_empty` | Skjul stationer, der intet byder på | 35 |
| `download_unmetered_only` | Hent kun uden forbrugsafregning | 31 |
| `settings_map_filters_hide_out_of_service` | Skjul stationer ude af drift | 28 |
| `settings_opening_title` | Åbn appen som standard på | 25 |
| `about_open_licences` | Komponenternes licenser | 23 |
| `station_open_in_navigation` | Åbn i en navigationsapp | 23 |
| `station_as_origin` / `station_as_destination` | Herfra / Hertil | 6 / 6 |

The last pair are the only two buttons of the application placed side by side
on one row. Danish can put them in six characters each, which is shorter than
any language before it managed, and they read as a pair because they are one
word apart.

## The vocabulary

| English | Danish | Why |
|---|---|---|
| journey | rejse | The whole door-to-door thing: the screen, the settings section, the button, the errors. It is what a Danish travel planner calls a door-to-door trip — Rejseplanen names itself after it — and it leaves *rute* free for what English also calls a route. Short, which matters on `journey_compute` ("Beregn rejsen") and `journey_frame` ("Vis hele rejsen"). |
| ride | cykeltur | The bike leg alone, inside a journey. Plain *tur* was written here first and was dropped: Danish uses *tur* for a whole outing as readily as for one leg, so the elevation profile and the "own bike" wait would have read as the whole thing. *Cykeltur* cannot. Where the ride is a manner rather than an object, the file says **på cykel** ("heraf 12 min til fods og 9 min på cykel"). |
| route | rute | Only in `journey_no_route` — "Ingen farbar rute mellem disse to punkter" — and in `station_beyond_area`: the line on the ground, not the planned journey. Danish has the same pair English does, *rejse* / *rute*, so no third word had to be invented. |
| journey data (store texts) | rejsedata | "Ingen rejsedata gemmes". It cannot collide with `dataset_routing`, which is **Rutedata** and *is* stored on the device, because the two words are already kept apart above. |
| bike | cykel / cykler | There is no shorter Danish word, and none is wanted. |
| bike-share bikes, as a product | delecykler | Only in the store texts and on the welcome page, where the thing has to be named before it is known. Inside the interface the context is settled and *cykel* is enough. |
| station | station | A bike-share station. |
| railway station | togstation | Danish *station* means both, so `address_search_prompt_message` — whose English comment says its "stations" are railway ones — has to say **togstationer** in full. |
| dock (free) | ledig plads | What one returns a bike into, counted as available: "6 cykler, 26 ledige pladser". *Ledig* is also what the file says of an available bike ("Vis ledige cykler"), and that is not a collision: the nouns are what carry the difference, as they do in Danish generally. |
| dock (capacity) | stativplads | The same object counted as a total. The two figures do meet on one row, but through two different resources rather than one: `counterpart_docks` writes the free count beside a station's disc, and `station_detail_with_capacity` writes the capacity on the supporting line under it — "26 ledige pladser" over "1,2 km · 30 stativpladser". English says "dock" for both; Danish does not have to. *Stativplads* is the place in the *cykelstativ*, the rack a shared bike locks into, and it is the longest noun in the file on purpose: read one under the other, no shorter word tells the two figures apart. |
| dock | *never* “stander”, *never* “terminal” | Those are the payment post, not the point a bike attaches to. |
| mechanical / electric | mekanisk / elektrisk | Kept as adjectives, as in English, because the counts are elliptical: "4 mekaniske · 2 elektriske" stands for "4 mekaniske cykler". Danish inflects the adjective after a plural numeral and not after 1, which is why `bikes_mechanical` and `bikes_electric` carry two different forms where English's carry one. |
| electric bike, as an object | elcykel | **A deliberate departure from the line above, in three strings.** *Elcykel* is the noun Danish has for the thing — the word the law, the shops and the riders use — and "på din egen elektriske cykel" reads as a translation where "på din egen elcykel" reads as Danish. So the adjective labels the *choice* (`settings_own_bike_kind_electric` = "Elektrisk", `map_bikes_electric` = "Elektriske") and the noun names the *object*: `journey_own_bike_electric_only`, its `_climb` twin, and `journey_bike_kind_electric_description` = "En elcykel med pedalassistance". That last also settles what "electric" means here: a pedelec, never a *knallert*. Do not "correct" the three back to the adjective. |
| pace (walking) | gangtempo | A pace is not a speed, which `values/strings.xml` says above the string. *Tempo* is a pace; *hastighed* is the figure nobody has measured about themselves, and is not used. Slow / Normal / Brisk are **Langsomt / Normalt / Raskt**, in the neuter, because they agree with the *tempo* they answer — "et raskt tempo" is the ordinary Danish for a brisk walk. Android's own *Langsom* (`settings:speed_label_slow`) is the common-gender form, right for the *hastighed* it describes there and wrong under this title. |
| climb | stigning | The metres climbed, over a leg or over the whole journey: "120 m stigning". |
| location / position | lokation / position | Two words, as in English and for the same reason. **Lokation** is the system feature and the permission — Android's own word, `android:permgrouplab_location` — and is what `map_location_denied` and `map_location_unavailable` speak of, because those are about the feature being off or silent. **Position** is where the reader actually is, the point on the map, and is what every string naming that point says: `journey_source_my_position` ("Min position"), `map_outside_city_brief` ("Din position ligger uden for …") and `map_locate_me`, the button that centres the map on it — **"Find min position"**, a verb as `city_locate_me` is a verb ("Find min by"). |
| network (bike-share) | netværk | The operator whose bikes these are. It is never used of a data connection — see the next row — so `error_malformed` can say "netværkets feed" without a reader wondering which network is meant. |
| network (connectivity) | internet / forbindelse | **A split the English does not make, and the reason for it.** English writes "network" for both the bike-share operator and the data connection, and Danish *netværk* would inherit the ambiguity on the very screens where both appear. So the connection is **internet** where the sentence is about working without one ("Tegner kortet uden internet", "virker uden internet") and **forbindelse** where it is about the connection as an object ("Ingen forbindelse", "en forbindelse uden forbrugsafregning"). |
| conurbation | byområde | The city screen serves a metropolitan area rather than a municipality, and *by* is kept for the shorter word the section title and the screen title need. |
| Settings | Indstillinger | Android's own (`settings:settings_label`), including in the system path quoted in `about_links_body`: "Indstillinger → Apps → … → Åbn som standard → Tilføj link", every step of which is Android's own Danish (`settings:launch_by_default`, `settings:app_launch_add_link`). |
| Theme | Tema | Android's own: *Mørkt tema* (`settings:dark_ui_mode`), *Enhedstema* (`settings:device_theme`). Light / Dark are **Lyst / Mørkt**, in the neuter, agreeing with the *tema* they sit under. |
| Display (section) | Skærm | Android's own name for the settings screen that holds the theme and the text size (`settings:display_settings`). |
| Storage | Lagerplads | Android's own for the screen (`settings:storage_settings`, `settings:storage_label`). Everywhere another string points at it, the file says **fra skærmen Lagerplads** rather than translating "storage screen" literally. |
| Delete | Slet | Android's own (`android:delete`, `settings:delete`), for what destroys: a city's data, a dataset. |
| Remove (from a list) | Fjern | Android's own, and Danish keeps the pair English keeps: *Remove* is **Fjern** (`settings:remove`, `android:kg_reordering_delete_drop_target_text`) against *Delete* = **Slet**. So "Fjern fra favoritter" takes a station out of a list and "Slet" destroys data, with no third word needed. |
| Clear (a search) | Ryd | Android's verb for emptying a field (`settings:clear`, `settings:clear_text_end_icon_content_description`). The file says **Ryd søgningen** rather than Android's own *Ryd forespørgslen* (`android:searchview_description_clear`), which is the machine's word for the thing; the same wording serves the icon inside the field and the button in the empty state, so the two read as one thing. |
| Refresh | Opdater | **Danish has one verb where English has two, and the file lets it.** *Refresh* and *Update* are both **opdatere**: `action_refresh` is "Opdater", `freshness_fresh` is "Opdateret %1$s", and the storage screen says "Søg efter opdateringer" (`android:unsupported_compile_sdk_check_update`) and "Opdater appen". Nothing is lost, because no screen carries both senses: the list refreshes availability, the storage screen updates datasets, and the app updates itself. |
| Out of service | Ude af drift | Android's own (`settings:radioInfo_service_out`), and the ordinary Danish for a station or a vehicle that is not running. `settings_map_filters_hide_out_of_service` echoes it word for word. |
| Frozen data | Forældede data | `freshness_stale`, shown when the feed has stopped moving. *Frosne* reads as a crash rather than as data that has stopped being refreshed. |
| Try again | Prøv igen | Android's own (`settings:retry`, `android:lockscreen_password_wrong`). |
| Continue | Fortsæt | Android's button word (`android:autofill_continue_yes`), on the welcome carousel and the what's-new screen. |
| Skip | Spring over | Android's own (`android:skip_button_label`). |
| Back | Tilbage | Android's own, on the toolbar arrow (`settings:back`). |
| In use | I brug | Android's own (`android:media_route_status_in_use`), on the city already selected. |
| just now | lige nu | Android's own (`settings:time_unit_just_now`), lower-cased because it is never read alone: `FreshnessText.toStatusLine` puts it inside `freshness_fresh` or inside `freshness_stale` — "Opdateret lige nu", "Forældede data · lige nu". The other freshness lines carry their own **for … siden**, for the same reason: "Opdateret for 5 minutter siden". |
| Update available | Opdatering tilgængelig | **A shortening, and a departure.** Android's own is the whole sentence *Der er en tilgængelig opdatering* (`settings:android_version_pending_update_summary`), 31 characters, which is a notification summary and not a badge. This one sits on a dataset row beside a size and a date, and takes the noun phrase instead. |
| Replace | Erstat | Android's own (`settings:vpn_replace`). |
| Cancel | Annuller | Android's own (`android:cancel`), which the extract gives 31 times over against three uses of *Luk* in places that are closures rather than cancellations. |
| Show | Vis | Android's own (`settings:condition_expand_show`). |
| Hide | Skjul | Android's own (`settings:condition_expand_hide`). |
| Language | Sprog | Android's own (`settings:app_locale_preference_title`). |
| Wi-Fi | Wi-Fi | Android's Danish writes it exactly so, hyphen included (`settings:wifi`). |
| unmetered / metered | uden forbrugsafregning / afregnes pr. megabyte | Android labels a connection **Forbrugsafregnet** / **Ikke forbrugsafregnet** (`settings:wifi_metered_label`, `settings:wifi_unmetered_label`). The switch takes the second, shortened to "Hent kun uden forbrugsafregning" so it stays on one line; the sentences explaining it say what is billed — *afregnes pr. megabyte* — since that is the point the English makes. |
| download (noun) / to download | download / hente | Danish keeps the loan as a noun — "Én by, én download", "Downloaden fortsætter, hvor den slap" — and uses the native verb for the act: "Hent %1$s", "Hent alligevel", "%2$s at hente". That is how Danish actually writes it, and it keeps the buttons short. What a download costs is said with **tære på**, not *veje tungt*: in Danish something weighs heavily on a decision, never on a data plan. |
| Tap | Tryk (på) | Android's own verb in the imperative — "Tryk for at se flere muligheder" (`android:usb_notification_message`) — in the second person like the rest. |
| Press and hold | Hold … nede | For the long press that reorders the favourites. |
| app | app | Android's own (`settings:apps_dashboard_title`). |
| device | enhed | Android's own word for a device (`settings:wifi_dpp_could_not_add_device` and 199 lines of the extract besides). Kept apart from **telefon**, which the privacy sentences use because that is the word the English uses there and it is what the reader is holding. |
| map data / tiles | Kortdata | Names the dataset on the storage screen, and every other string that points at it uses the same word — including `map_needs_tiles_title`, **"Kortdataene mangler"**, where the English says "tiles". Danish has no settled word for a map tile that a reader would recognise on a first screen, and naming the dataset the reader is about to go and install says more than *fliser* would. The dataset is made the subject there rather than the map, which keeps the one name without writing *Kortet … kortdata* twice in four words. |
| routing data | Rutedata | Pairs with *rute*: the data a route is computed from. |
| address index | Adresseindeks | Also what `incoming_needs_index` calls it, where the English says "the offline index": one name for one dataset. |
| offline data | Offlinedata | One word, as Danish writes *offlinetilstand*. |
| street name | vejnavn | The word the Danish address register itself uses, and what `address_search_hint` asks for. |
| what's new | Nyheder | What the Danish Play Store calls the same thing. |
| mile | mile / miles | **A false friend, and the one that would have been read as fact.** Danish *mil* is the Scandinavian mile of ten kilometres, still in everyday use; the English mile is *mile*, plural *miles*. So `settings_units_us_description` is **"Fod og miles"** and `settings_units_uk_description` **"Yard og miles"** — *yard* takes no plural ending in Danish — and the changelog says "miles og fod i USA". The symbols `mi`, `ft` and `yd` are untouched: they are what the road signs of those two countries print. |
| over (a distance) | over | The eight journey summaries keep the English preposition — "på din egen cykel, over 3,2 km" — because the fragment is read after a duration: "24 min · på din egen cykel, 3,2 km" would juxtapose the figure instead of saying it was covered. |
| bytes | B, kB, MB, GB | Danish writes the same symbols. |
| hour (in a duration) | t | `duration_hours_minutes` is `%1$d t %2$02d`. **t** is how Danish abbreviates the hour — "1 t 05" — where `h` reads as foreign. It is the one unit symbol this file changes. |

## One departure from the English, in two strings

`journey_no_mechanical_nearby` and `journey_no_electric_nearby` say **"Vælg
»Enhver cykel«, eller prøv igen senere"** where the English says "Ask for any
bike" and names no control. The wording is kept, and the cost is written here
so that it is known rather than discovered.

What it buys: the reader is standing in front of the journey screen with the
Mekanisk or Elektrisk chip selected, and the way out is one tap on the chip
beside it. Naming that chip points at the tap; "bed om enhver cykel" describes
an intention and leaves the reader to find where it is expressed.

What it costs: the two messages now quote `journey_bike_kind_any` verbatim, so
the day that label changes these two lines say something the screen does not.
**If `journey_bike_kind_any` is ever reworded, reword these two with it** — or
drop the quotation and go back to describing the choice.

## The three dataset names, and why nothing has to agree with them

`dataset_imported`, `dataset_deleted` and `dataset_absent` all put a past
participle after a name that varies: **Kortdata**, **Rutedata**,
**Adresseindeks**. In Danish this asks nothing of the translator. Both verbs
belong to the *-et* class, whose participle is invariable when it is used
verbally — "Kortdata installeret", "Adresseindeks slettet", "Ikke installeret"
— and the three names are neuter or plural besides, so even an adjectival
reading would land on the same form. The Spanish, Italian and Portuguese files
had to make the three names share a gender to agree once; Danish did not have
to choose.

## The address prompt, and the postcode that is not in it

Danish writes the street before the number — "Nørrebrogade 12" — and
`address_search_hint` asks for **"Vejnavn, nummer, by"** accordingly.
`AddressQuery.parseQuery` reads a house number standing between street and
town, so the Danish order is understood as typed.

**The postcode is deliberately left out of the prompt, and must not be added.**
A Danish postcode is four digits, "2200". The parser only strips a postcode
written as a single group of **five** digits, so "2200" would be read as a
second number in the query — and a second number makes the house number be
given up altogether (SPEC §4.3). Inviting the postcode would therefore break
the reading of every query that carried one.

`address_locality` keeps its English order, `%1$s %2$s`, because Danish writes
the postcode before the municipality too: "2200 København N".

**How a result is printed is a separate matter, and is not this file's to
decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3): "Nørrebrogade 12" is how a Copenhagen address is written
for every reader of the application, and "12 rue Nationale" is how a Lyon one
is written for a Danish reader. The layouts are a table in
`core/address/AddressLayout.kt`, keyed on the language of the **address base**.

**Danish is missing from that table**, and so falls back on English's layout,
which opens with the number — "12 Nørrebrogade", which Denmark does not write.
The entry it wants is `numberComesFirst = false`, a space before the street and
the letter closed up against the number, as in "Nørrebrogade 12A". That is one
line of Kotlin and it is not this file's to add; it is flagged here so that it
is not forgotten.

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Vélo’v, Citi Bike,
Donkey Republic, BRouter, MapLibre, OpenStreetMap, GBFS — and the licence
names. Unit symbols: `m`, `km`, `ft`, `yd`, `mi`, `min`. `resources` `name`
attributes, always.

## The four strings the checker flags, and why they stand

`python3 tools/check_translations.py da` reports four strings identical to the
English. All four are correct Danish:

- `settings_theme_system`, `settings_units_system`, `settings_language_system`
  — **System** is Android's own Danish for it (`settings:header_category_system`).
- `about_version` — **Version %1$s**; Danish writes *version*
  (`settings:vpn_version`).
