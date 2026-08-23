# Swedish glossary

The terms `res/values-sv/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Swedish ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## One Swedish for two networks, and for the rest of the catalogue

The catalogue serves two Swedish networks — Donkey Republic in Malmö and
Lundahoj in Lund — but the file is read by whoever sets the interface to
Swedish, in any of the three hundred cities. So nothing here is written about
Sweden: the city, the network and the country are always what the data says
they are.

The arbiter for anything Android already names is the phone itself: 5 882
system strings extracted from a device's `framework-res.apk` and
`Settings.apk`. Every key quoted below was found in that extract; where no key
is quoted, the choice is this file's own and says so.

## Register and typography

Swedish has one address, **du**, and it is used throughout, in the interface
and in the store texts alike (SPEC §9). Imperatives follow: *Tryck*, *Välj*,
*Försök igen*, *Dra listan nedåt*.

**The impersonal turns of the English are kept impersonal.** "No history is
kept" is **"Ingen historik sparas"**, "It is read from the feed" is **"Det
läses från nätverkets eget flöde"**. Swedish's *s*-passive does this without
effort, so no privacy sentence in the file has to invent a "vi" the
application has no one behind it to mean.

Quotation marks are **” ”**, closing on both sides, which is what Swedish
prints. The dash that breaks a sentence is **–** with a space on either side,
not the em dash the English file uses; that is the one piece of punctuation
changed inside a format-only string, `city_label` (`%1$s – %2$s`). Nothing in
the file is escaped, because no straight apostrophe is written anywhere in it.

**A trailing ellipsis is written with a space before it** — "Söker …",
"Beräknar den bästa resan …". That is what Android's own Swedish does on
progress messages, near-uniformly: `android:loading` = "Läser in …",
`settings:wifi_connecting` = "Ansluter …", `settings:zen_mode_apps_calculating`
= "Beräknar …", `android:ext_media_status_checking` = "Kontrollerar …". The
five strings that carry one — `journey_computing`, `journey_computing_own_bike`,
`journey_locating`, `address_searching_title`, `storage_checking` — all follow
it, and nothing else in the file uses the character.

Swedish compounds are written as one word, and that is what lengthens the
labels. Where a label had to stay short, it takes the wording Android itself
uses, or a shorter turn, rather than the literal translation. The longest
labels the screens carry:

| String | Swedish | Characters |
|---|---|---|
| `settings_map_filters_hide_empty` | Dölj stationer utan något att erbjuda | 37 |
| `download_unmetered_only` | Ladda ned endast utan datapriser | 32 |
| `city_size_unknown` | Storleken anges före nedladdning | 32 |
| `address_unreadable_title` | Adressindexet går inte att läsa | 31 |
| `address_needs_index_title` | Sökningen kräver adressindexet | 30 |
| `download_held_back_title` | Nedladdningen hålls tillbaka | 28 |
| `settings_map_filters_title` | Vilka stationer kartan visar | 28 |
| `settings_opening_title` | Öppna appen som standard på | 27 |
| `settings_map_filters_hide_out_of_service` | Dölj stationer ur funktion | 26 |
| `journey_navigate` / `station_open_in_navigation` | Öppna i en navigationsapp | 25 |
| `about_open_licences` | Komponenternas licenser | 23 |
| `station_as_origin` / `station_as_destination` | Härifrån / Hit | 8 / 3 |

The last pair are the only two buttons of the application placed side by side
on one row. They are the two ends of one journey — *from here*, *to here* — and
Swedish has a single word for each, which is why they are shorter than any
other language's. They do not match in length and are not meant to: *hit* is
the ordinary Swedish for "to here" and nothing shorter or longer says it
better.

## The vocabulary

| English | Swedish | Why |
|---|---|---|
| journey | resa | The whole door-to-door thing: the screen, the settings section, the button, the errors. It is what a Swedish travel planner calls a door-to-door trip — Resrobot and every regional operator's *Sök resa* — and it leaves *rutt* free for what English also calls a route. Short, which matters on `journey_compute` ("Beräkna resan") and `journey_frame` ("Visa hela resan"). |
| ride | cykeltur | The bike leg alone, inside a journey: `journey_computing_own_bike`, `journey_detail_profile`, `journey_hint_own_bike`. Plain *tur* was not used, because Swedish gives *tur* a whole outing as readily as one leg — and a departure on a timetable besides. Where the ride is a manner rather than an object, the file says **på cykel** ("varav 12 min till fots och 9 min på cykel"). |
| route | rutt | Only in `journey_no_route` — "Ingen framkomlig rutt mellan de här två punkterna" — and in `station_beyond_area`: the line on the ground, not the planned journey. Swedish has the same pair English does, *resa* / *rutt*, so no third word had to be invented. `dataset_routing` is **Ruttdata** and pairs with it. |
| journey data (store texts) | resedata | "Inga resedata sparas". It cannot collide with `dataset_routing`, which is **Ruttdata** and *is* stored on the device, because the two words are already kept apart above. |
| bike | cykel / cyklar | There is no shorter Swedish word, and none is wanted. |
| bike-share bikes, as a product | lånecyklar | Only in the store texts and on the welcome page, where the thing has to be named before it is known. **Lånecykel** is the Swedish word for it — what Göteborg's and Malmö's schemes have always been called — and it is preferred to the calques *delade cyklar* and *hyrcyklar*. Inside the interface the context is settled and *cykel* is enough. |
| station | station | A bike-share station. |
| railway station | tågstation | Swedish *station* means both, so `address_search_prompt_message` — whose English comment says its "stations" are railway ones — has to say **tågstationer** in full. |
| dock (free) | ledig plats | What one returns a bike into, counted as available: "6 cyklar, 26 lediga platser". *Ledig* is also what the file says of an available bike ("Visa lediga cyklar"), and that is not a collision: the nouns are what carry the difference, as they do in Swedish generally. |
| dock (capacity) | cykelplats | **This file's own word, and the hardest choice in it.** The same object counted as a total. The two figures do meet on one row, through two different resources: `counterpart_docks` writes the free count beside a station's disc, and `station_detail_with_capacity` writes the capacity on the supporting line under it — "26 lediga platser" over "1,2 km · 30 cykelplatser". English says "dock" for both; Swedish must not, or the row reads as one figure contradicting itself. Two candidates were dropped: **ställplats**, the exact analogue of Danish *stativplads*, because in Swedish it is the settled word for a motorhome pitch and Malmö is one of the two cities served; and **dockningsplats**, which is right but sixteen characters on a line that already carries a distance. |
| dock | *never* “stolpe”, *never* “terminal” | Those are the payment post, not the point a bike attaches to. |
| mechanical / electric | mekanisk / elektrisk | Kept as adjectives, as in English, because the counts are elliptical: "4 mekaniska · 2 elektriska" stands for "4 mekaniska cyklar". Swedish inflects the adjective after a plural numeral and not after 1, which is why `bikes_mechanical` and `bikes_electric` carry two different forms where English's carry one. |
| electric bike, as an object | elcykel | **A deliberate departure from the line above, in four strings.** *Elcykel* is the noun Swedish has for the thing — the word the law, the shops and the riders use — and "på din egen elektriska cykel" reads as a translation where "på din egen elcykel" reads as Swedish. So the adjective labels the *choice* (`settings_own_bike_kind_electric` = "Elektrisk", `map_bikes_electric` = "Elektriska") and the noun names the *object*: `journey_own_bike_electric_only`, its `_climb` twin, `journey_bike_kind_electric_description` = "En elcykel med trampassistans", and `welcome_fleet_electric_only`. That description also settles what "electric" means here: *trampassistans* is Transportstyrelsen's own term for a pedelec, and rules out a moped. Do not "correct" the four back to the adjective. |
| pace (walking) | gångtempo | A pace is not a speed, which `values/strings.xml` says above the string. *Tempo* is a pace; *hastighet* is the figure nobody has measured about themselves, and is not used. Slow / Normal / Brisk are **Långsamt / Normalt / Raskt**, in the neuter, because they agree with the *tempo* they answer — "ett raskt tempo" is the ordinary Swedish for a brisk walk. Android's own *Långsam* (`settings:speed_label_slow`) is the common-gender form, right for the *hastighet* it describes there and wrong under this title. |
| climb | stigning | The metres climbed, over a leg or over the whole journey: "120 m stigning". |
| location / position | plats / position | Two words, as in English and for the same reason. **Plats** is the system feature and the permission — Android's own word, `android:permgrouplab_location`, `settings:location_settings_title` — and is what `map_location_denied` and `map_location_unavailable` speak of, because those are about the feature being off or silent; the second names it with a capital, "Kontrollera att Plats är aktiverat", because it is pointing at a switch, and the first compounds it into *platsbehörighet* because what is missing there is the permission and not the feature — *behörighet* being Android's own word for a permission, the one `about_links_body` uses too. **Position** is where the reader actually is, the point on the map: `journey_source_my_position` ("Min position"), `map_outside_city_brief`, and `map_locate_me` — **"Hitta min position"**, a verb as `city_locate_me` is a verb ("Hitta min stad"). |
| destination | mål / destination | **Two words, and the split is deliberate.** In the journey interface the end of a trip is **målet** — `journey_swap` ("Byt plats på start och mål"), `journey_step_to_destination`, `journey_no_dock_nearby` — which is short, is what a Swedish route planner says, and pairs with *start*. In `welcome_privacy_body` and the store texts, where the sentence lists what is not kept, it is **destinationer**: "dina mål" there would read as the reader's ambitions rather than the places they went. |
| network (bike-share) | nätverk | The operator whose bikes these are. It is never used of a data connection — see the next row — so `error_malformed` can say "nätverkets flöde" without a reader wondering which network is meant. |
| network (connectivity) | internet / anslutning | **A split the English does not make, and the reason for it.** English writes "network" for both the bike-share operator and the data connection, and Swedish *nätverk* would inherit the ambiguity on the very screens where both appear. So the connection is **internet** where the sentence is about working without one ("Ritar kartan utan internet", "fungerar utan internet") and **anslutning** where it is about the connection as an object ("Ingen anslutning", `settings:mobile_data_no_connection`; "en anslutning utan datapriser"). |
| feed | flöde | The GBFS stream a network publishes. *Flöde* is the ordinary Swedish for a data feed and needs no loan word. |
| conurbation | tätort | The city screen serves a built-up area rather than a municipality. **Stadsområde is not used, and must not be**: in Malmö — one of the two Swedish cities served — *stadsområde* was the name of the city's own administrative districts, and the word would be read as a part of the city rather than as the whole of it. *Tätort* is SCB's own term, it covers Auray and Stockholm alike, and *stad* is kept for the shorter word the section title and the screen title need. |
| Settings | Inställningar | Android's own (`settings:settings_label`), including in the system path quoted in `about_links_body`: "Inställningar → Appar → … → Öppna som standard → Lägg till länk", every step of which is Android's own Swedish (`settings:apps_dashboard_title`, `settings:launch_by_default`, `settings:app_launch_add_link`). |
| Theme | Tema | Android's own: *Mörkt tema* (`settings:dark_ui_mode`). Light / Dark are **Ljust / Mörkt**, in the neuter, agreeing with the *tema* they sit under. |
| Display (section) | Skärm | Android's own name for the settings screen that holds the theme and the text size (`settings:display_settings`, `settings:display_category_title`). |
| Storage | Lagring | Android's own for the screen (`settings:storage_settings`, `settings:storage_label`). Everywhere another string points at it, the file says **från skärmen Lagring** rather than translating "storage screen" literally. |
| Delete | Radera | For what destroys: a city's data, a dataset. Android's Swedish gives both *Radera* and *Ta bort* for "Delete" — `settings:dlg_delete` and `settings:guest_exit_clear_data_button` (the button that deletes a profile's data, which is exactly this case) say **Radera**, while `settings:delete` says *Ta bort*. The pair below is what settles it. |
| Remove (from a list) | Ta bort | Android's own, and its **only** word for "Remove" — `settings:remove`, `settings:locale_remove_menu`, `android:kg_reordering_delete_drop_target_text`. English keeps *Delete* and *Remove* apart and so must the file, so *Ta bort* is reserved for taking a station out of the favourites and *Radera* for destroying data. Reading the two rows the other way round would leave Swedish with one word for both. |
| Clear (a search) | Rensa | Android's verb for emptying a field (`settings:clear`, `settings:proxy_clear_text`). The file says **Rensa sökningen** rather than Android's own *Ta bort frågan* (`android:searchview_description_clear`), which is the machine's word for the thing and would besides collide with *Ta bort* above; the same wording serves the icon inside the field and the button in the empty state, so the two read as one thing. |
| Refresh | Uppdatera | **Swedish has one verb where English has two, and the file lets it.** *Refresh* and *Update* are both **uppdatera** (`android:autofill_update_yes`): `action_refresh` is "Uppdatera", `freshness_fresh` is "Uppdaterad %1$s", and the storage screen says "Sök efter uppdateringar" and "Uppdatera appen". Nothing is lost, because no screen carries both senses: the list refreshes availability, the storage screen updates datasets, and the app updates itself. |
| Out of service | Ur funktion | Android's own (`settings:radioInfo_service_out`), and the ordinary Swedish for a station or a vehicle that is not running. `settings_map_filters_hide_out_of_service` echoes it word for word. |
| Frozen data | Föråldrade data | `freshness_stale`, shown when the feed has stopped moving. *Frusna* reads as a crash rather than as data that has stopped being refreshed. |
| Try again | Försök igen | Android's own (`settings:retry`, `android:lockscreen_password_wrong`). |
| Continue | Fortsätt | Android's button word (`android:autofill_continue_yes`), on the welcome carousel and the what's-new screen. |
| Skip | Hoppa över | Android's own (`android:skip_button_label`). |
| Back | Tillbaka | Android's own, on the toolbar arrow (`settings:back`, `android:back_button_label`). Not *Föregående*, which the extract gives only for a wizard's previous page (`settings:wizard_back`). |
| In use | Används | Android's own (`android:media_route_status_in_use`), on the city already selected. |
| just now | nyss | Android's own (`settings:time_unit_just_now`), lower-cased because it is never read alone: `FreshnessText.toStatusLine` puts it inside `freshness_fresh` or inside `freshness_stale` — "Uppdaterad nyss", "Föråldrade data · nyss". The other freshness lines carry their own **för … sedan**, for the same reason: "Uppdaterad för 5 minuter sedan". |
| Update available | Uppdatering finns | **A shortening, and a departure.** Android's own is the whole sentence *Det finns en uppdatering*, which is a notification summary and not a badge. This one sits on a dataset row beside a size and a date, and takes the shortest phrase that still says it. |
| Replace | Ersätt | Android's own (`settings:vpn_replace`). |
| Cancel | Avbryt | Android's own (`android:cancel`), which the extract gives some forty times over. |
| Show | Visa | Android's own (`settings:condition_expand_show`). |
| Hide | Dölj | Android's own (`settings:condition_expand_hide`). |
| Language | Språk | Android's own (`settings:app_locale_preference_title`). |
| Wi-Fi | Wifi | **Android's Swedish writes it as one word with no hyphen** — `settings:wifi` = "Wifi" — where the English file and most other translations write "Wi-Fi". The file follows the phone. |
| unmetered / metered | utan datapriser / debiteras per megabyte | Android labels a connection **Med datapriser** / **Utan datapriser** (`settings:wifi_metered_label`, `settings:wifi_unmetered_label`, `settings:wifitrackerlib_wifi_metered_label`). The switch takes the second — "Ladda ned endast utan datapriser" — and the sentences explaining it say what is billed, *debiteras per megabyte*, since that is the point the English makes. |
| download (noun) / to download | nedladdning / ladda ned | Swedish has native words for both and uses them: "En stad, en nedladdning", "Nedladdningen fortsätter där den stannade" against "Ladda ned %1$s", "Ladda ned ändå", "%2$s att ladda ned". *Ladda ned* is Android's own verb (`android:install_carrier_app_notification_button`, "Ladda ned appen"). What a download costs is said with **tära på**, not *väga tungt*: in Swedish something weighs heavily on a decision, never on a data plan. |
| dataset | datamängd | Three of them, named on the storage screen. |
| Tap | Tryck (på) | The imperative, in the second person like the rest of the file. |
| Press and hold | Håll … nedtryckt | For the long press that reorders the favourites. |
| app | app | Android's own (`settings:apps_dashboard_title`). |
| device | enhet | Android's own word for a device. Kept apart from **telefon**, which the privacy sentences use because that is the word the English uses there and it is what the reader is holding. |
| map data / tiles | Kartdata | Names the dataset on the storage screen, and every other string that points at it uses the same word — including `map_needs_tiles_title`, **"Kartdata saknas"**, where the English says "tiles". Swedish has no settled word for a map tile that a reader would recognise on a first screen, and naming the dataset the reader is about to go and install says more than *rutor* would. |
| routing data | Ruttdata | Pairs with *rutt*: the data a route is computed from. |
| address index | Adressindex | Also what `incoming_needs_index` calls it, where the English says "the offline index": one name for one dataset. |
| offline data | Offlinedata | One word, as Swedish writes *offlineläge*. |
| street name | gatunamn | The word a Swedish address register uses, and what `address_search_hint` asks for. |
| what's new | Nyheter | What the Swedish Play Store calls the same thing. |
| tracker | spårning | The store texts and the privacy pages say "ingen spårning" rather than borrowing *tracker*: the noun Swedish would need, *spårare*, reads as a person following someone. The property being claimed is that nothing tracks, and that is what *spårning* states. |
| mile | miles | **A false friend, and the one that would have been read as fact.** Swedish *mil* is ten kilometres and is in daily use — a Swede reading "3 mil" understands thirty kilometres. The English mile is *mile*, plural *miles*. So `settings_units_us_description` is **"Fot och miles"** and `settings_units_uk_description` **"Yard och miles"** — *yard* takes no plural ending in Swedish — and changelog 3 says "miles och fot i USA". The symbols `mi`, `ft` and `yd` are untouched: they are what the road signs of those two countries print. |
| over (a distance) | över | The eight journey summaries keep the English preposition — "på din egen cykel, över 3,2 km" — because the fragment is read after a duration: "24 min · på din egen cykel, 3,2 km" would juxtapose the figure instead of saying it was covered. |
| bytes | B, kB, MB, GB | Swedish writes the same symbols. |
| hour (in a duration) | tim | `duration_hours_minutes` is `%1$d tim %2$02d`. **tim** is how Swedish abbreviates the hour — "1 tim 05" — where `h` reads as foreign outside *km/h*. It is the one unit symbol this file changes. |

## One departure from the English, in two strings

`journey_no_mechanical_nearby` and `journey_no_electric_nearby` say **"Välj
”Valfri cykel” eller försök igen senare"** where the English says "Ask for any
bike" and names no control. The wording is kept, and the cost is written here
so that it is known rather than discovered.

What it buys: the reader is standing in front of the journey screen with the
Mekanisk or Elektrisk chip selected, and the way out is one tap on the chip
beside it. Naming that chip points at the tap; "be om vilken cykel som helst"
describes an intention and leaves the reader to find where it is expressed.

The chip itself is **"Valfri cykel"** and not the literal *Vilken cykel som
helst*: it sits in a row beside "Mekanisk" and "Elektrisk", and twenty-two
characters against eight would not have held the row.

What it costs: the two messages now quote `journey_bike_kind_any` verbatim, so
the day that label changes these two lines say something the screen does not.
**If `journey_bike_kind_any` is ever reworded, reword these two with it** — or
drop the quotation and go back to describing the choice.

## The three dataset names, and the agreement Swedish cannot make

`dataset_imported`, `dataset_deleted` and `dataset_absent` all stand next to a
name that varies: **Kartdata**, **Ruttdata**, **Adressindex**. Unlike Danish,
Swedish cannot put one participle after all three and be right.

*Adressindex* is an unambiguous neuter singular — *ett index* — and wants
*installerat*. *Kartdata* and *Ruttdata* do not have a settled gender at all:
Språkrådet allows *data* to be read as a neuter plural, wanting *installerade*,
or as an uncountable, and everyday Swedish adds a common-gender *datan* on top.
Whichever form is written, it is wrong for at least one of the three names.

So the three strings ask for **no agreement whatsoever**:

- `dataset_imported` = **"%1$s har installerats"** and `dataset_deleted` =
  **"%1$s har raderats"**. The *s*-passive has no participle to inflect, and
  reads correctly after all three: "Kartdata har installerats", "Adressindex
  har raderats".
- `dataset_absent` = **"Saknas"**, which is invariable, is what a Swedish row
  says of something that is not there, and echoes `map_needs_tiles_title`
  ("Kartdata saknas") so the storage screen and the map say one word. It is
  shorter than the English "Not installed" and says the same thing on that row.

The same reasoning governs the running text: where *data* is the head of a
sentence the file reads it as a plural — "Föråldrade data", "Inga data
installerade", "Ruttdata är inte installerade" — which is the traditional
Swedish and is consistent across the file. **Do not "fix" one of these
without fixing all of them.**

## The address prompt, and the postcode that is not in it

Swedish writes the street before the number — "Storgatan 12" — and
`address_search_hint` asks for **"Gatunamn, nummer, ort"** accordingly.
`AddressQuery.parseQuery` reads a house number standing between street and
town, so the Swedish order is understood as typed.

**The postcode is deliberately left out of the prompt, and must not be added.**
A Swedish postcode is five digits but is printed as two groups, "211 34". The
parser only strips a postcode written as a single group of five digits, so
"211 34" would be read as **two** further numbers in the query — and a second
number makes the house number be given up altogether (SPEC §4.3). Inviting the
postcode would therefore break the reading of every query that carried one.
The unspaced form "21134" would be stripped, but no Swede types it.

`address_locality` keeps its English order, `%1$s %2$s`, because Swedish writes
the postcode before the locality too: "211 34 Malmö".

**How a result is printed is a separate matter, and is not this file's to
decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3). Swedish is already in the table in
`core/address/AddressLayout.kt` — `numberComesFirst = false`, a space before
the number, the letter closed up, "Storgatan 12b" — and it is correct as it
stands; nothing here asks for it to be changed.

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Vélo’v, Citi Bike,
Donkey Republic, Lundahoj, BRouter, MapLibre, OpenStreetMap, GBFS — and the
licence names. Unit symbols: `m`, `km`, `ft`, `yd`, `mi`, `min`. `resources`
`name` attributes, always.

## The four strings the checker flags, and why they stand

`python3 tools/check_translations.py sv` reports four strings identical to the
English. All four are correct Swedish:

- `settings_theme_system`, `settings_units_system`, `settings_language_system`
  — **System** is Android's own Swedish for it (`settings:header_category_system`,
  `android:notification_app_name_system`).
- `about_version` — **Version %1$s**; Swedish writes *version*
  (`settings:vpn_version`).
