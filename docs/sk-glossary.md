# Slovak glossary

The terms `res/values-sk/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Slovak ones over three screens, and so that a contributor can correct
one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

Every Android citation below was found in the 5 900 system strings extracted
from this phone's `framework-res.apk` and `Settings.apk`; where a word is a
choice of ours rather than Android's, it says so.

## Register and typography

The Slovak uses **vykanie** — the reader is addressed as *vy* — which is what
Android's own Slovak does: *Klepnite*, *Skontrolujte*, *Zadajte*, *Pripojte
sa*, *Vyberte*, *Potvrďte*. An application that tyká a Slovak reader announces
itself as a translation.

Buttons take the **infinitive**, which is Android's habit for a control rather
than a sentence: *Pokračovať* (`settings:lockpattern_continue_button_text`),
*Preskočiť* (`android:skip_button_label`), *Odstrániť* (`android:delete`),
*Skúsiť znova* (`android:lockscreen_password_wrong`), *Zobraziť*
(`settings:condition_expand_show`). Sentences take the *vy* imperative:
*Skontrolujte pravopis*, *Nainštalujte register*, *Vyberte mesto*.

Quotation marks are **„ … “**. Diacritics are written in full — á ä č ď é í ĺ
ľ ň ó ô ŕ š ť ú ý ž — and never dropped. The dash that breaks a sentence is
**–** with a space on either side, not the em dash the English file uses; that
is the one piece of punctuation changed inside a format-only string,
`city_label` (`%1$s – %2$s`). The apostrophe, where a network's name carries
one (*Vélib’*, *V’lille*, *Vélo’v*), is **’** (U+2019) and never the straight
quote, which is why nothing in the file is escaped.

A **non-breaking space** (U+00A0) stands after every one-letter preposition or
conjunction — **k, s, v, z, o, u, a, i** — which Slovak typesetting never
leaves at the end of a line. There are 111 of them in the strings file and 89
more in the store texts. The vocalised two-letter forms — *ku, so, vo, zo* —
take an ordinary space: the rule is about a single letter left hanging, and
they are not one. One trap worth naming: *Vélo’v* ends in a lone *v* that is
part of a name, not a preposition, and takes an ordinary space after it.

## Plurals: four categories, and what each really covers

Slovak's CLDR categories are `one` (1), `few` (2–4), `many` (any fractional
value) and `other` (0 and 5 upward). `many` is **not** a spare copy of `other`:
it is the decimal one, and it takes the **genitive singular** — *1,5 bicykla*,
*1,5 voľného miesta*, *pred 1,5 minúty*. Every plural in the file is declined
for what its category actually holds.

`city_detail`, `city_stations` and `city_detail_size_unknown` do come back with
`few` and `many` identical — *2 stanice* and *1,5 stanice*. That is the
language, not a line nobody reached: the nominative plural and the genitive
singular of *stanica* fall together, and only the genitive plural *staníc*
differs.

## Cases are suffixes, and a placeholder cannot carry one

Slovak declines, and the ending falls on the word itself. A sentence built
around `%1$s` has to stay right whatever arrives in it, and what arrives is
always a nominative: a station name, a street name, a city, a network label.
So four lines are written around that rather than against it, and each is worth
knowing before it gets "fixed":

| String | What it does | Why |
|---|---|---|
| `station_address_nearby` | `V blízkosti: %1$s` | *V blízkosti* governs the genitive, and the argument is a street **or** a square (`address.streetName` reaches it as it stands). A colon turns the line into a label, which declines nothing. |
| `journey_step_to_station`, `journey_step_ride` | `Pešo k stanici %1$s` | The case falls on *stanici*; the name follows in apposition, in the nominative, exactly as it arrived. |
| `city_delete_description`, `city_delete_body`, `city_deleted` | `Odstrániť dáta: %1$s`, `… dáta „%1$s“ …` | **Not the apposition device, and here is why not.** All three are handed `city.displayName` (`CityAdapter.kt:111`, `CityFragment.kt:279` and `:294`), which is the **network's** name, not the city's: 328 of the 331 catalogue entries carry a `displayName` of their own, and every Slovak network in the catalogue is called *nextbike*. Writing „dáta mesta %1$s“ would have made a Bratislava reader read „dáta mesta nextbike“ — a false statement the English never makes. The colon and the quotation marks hold the name at arm's length instead, exactly as `dataset_deleted` does. |
| `dataset_imported`, `dataset_deleted` | `Nainštalované: %1$s` | A dataset's name is masculine in one case (*register adries*) and a neuter plural in two (*mapové dáta*, *dáta na výpočet trás*), so no participle can be written that agrees with all three. A label with a colon agrees with nothing. |
| `city_installed`, `storage_total` | `Nainštalované v zariadení: %1$s` | Same device, different reason: a **size** hides the gender and number of what it counts. *42,5 MB* is read as a genitive singular and *35 MB* as a genitive plural, so *%1$s nainštalovaných* is wrong for one figure and *%1$s nainštalovaný* for the other. The colon settles it. |

`city_here_body` and `city_here_installed_body` have the same problem in the
other direction: they need a possessive for the network, whose gender the
placeholder hides. They say **dáta tejto siete** and **jej stanice** — *sieť*
is feminine and fixed — rather than a pronoun that would have to agree with
whatever `cityLabel` produced.

The English is scrupulously impersonal — "No history is kept", "It is read from
the feed" — and the Slovak keeps it that way, with reflexive passives:
*Neuchováva sa žiadna história*, *Číta sa to z vlastného dátového kanála siete*,
*Trasy sa počítajú v tomto telefóne*. Nowhere does a "my" appear. In a privacy
text whose whole argument is that nobody is behind the application, a first
person would ask the reader to trust a *we* instead of stating a property of
the software.

## The address prompt, and the number Slovak will lose

`address_search_hint` is **„Ulica, číslo, obec“** — street, number, town, which
is the order Slovak writes an address in. `AddressQuery.parseQuery` has read a
house number standing between the street and the town since the pilot, precisely
so that each language may write this line in its own order.

**The order a result is printed in is a separate matter, and is not this file's
to decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3): *Hlavná 12/3* is how a Košice address is written for
every reader, and *12 rue Nationale* is how a Lyon one is written for a Slovak
reader. The layouts are a table in `core/address/AddressLayout.kt`, keyed on
the language of the **address base**, and the `"sk"` entry was written before
this translation existed. It is right, and for the same reason the Czech one
is: what the `suffix` field holds for a Slovak address is not a repetition mark
but **a second number** — the *súpisné číslo* identifies the building (12) and
the *orientačné číslo* places it in the street (3), and the plate joins them
with a slash. Closed up the address would read *123*, a number that exists
nowhere; spaced, it would read as two addresses.

**The postcode is deliberately not invited.** Slovak writes it in two groups —
*040 01* — and `looksLikePostcode` only strips a single group of five digits.
A postcode typed in therefore reaches the parser as two more numbers,
`holdsSeveralNumbers` sees three, and the house number is given up. Asking for
it would break the reading of every query that obeyed.

**And Slovak, like Czech, loses the number on a street named after a date.**
The second guard — no stop word beside the number — is what protects *rue du
8 Mai 1945* and *Straße des 17. Juni*. Slovak writes its dates **without a
preposition**: *Námestie 1. mája*, *Ulica 29. augusta*, *Nábrežie 4. apríla*.
Nothing stands beside the 1 but the words of the name, so the application reads
it as a house number. That is a known limit, written down in `SPEC.md` §4.3 and
in the KDoc of `parseQuery`, and its cost is bounded: the words left over still
name the street, so the street is still found — only the point inside it is
taken from a number that was never one. **The prompt is written in full
knowledge of it**, because that is what a Slovak reader will type whatever the
prompt says.

## The vocabulary

| English | Slovak | Why |
|---|---|---|
| journey | **trasa** | The whole door-to-door thing: the screen, the settings section, the button, the errors, the waits. It is what Slovak mapping applications call a planned trip (*naplánovať trasu*, *zobraziť celú trasu*), and it is short, which matters on `journey_compute` and `journey_frame`. |
| journey data (privacy) | **cesty** | The one place *trasa* is deliberately not used, and it holds across all three sentences that promise nothing is kept: `welcome_privacy_body` („ani vaše cesty, ani vaše polohy“), `about_privacy_body` („adresy, cesty aj vaša poloha“) and the store's own bullet („nič o vašich cestách“). „Neuchováva sa nič o vašich trasách“ would collide head-on with `dataset_routing`, which **is** called *dáta na výpočet trás* and **is** stored on the device — the sentence would say the opposite of the truth. Do not "correct" any of them back to *trasa*. |
| ride | **jazda** | The bike leg alone, inside a journey: `journey_computing_own_bike` („Počíta sa jazda…“), `journey_detail_profile`, `journey_detail_profile_description`. A different word from *trasa*, so the elevation profile and the own-bike wait cannot be mistaken for the whole thing. |
| journey (changelogs/1.txt) | **cesta** | „skladá **cestu** pešo → na bicykli → pešo“. *Trasa* is the word everywhere else, but the sentence already carries *trasy* („Výpočet trasy skladá…“) and Slovak will not take the repetition. Written down here so the next contributor sees a choice rather than a slip. |
| route | **cesta** | Only in `journey_no_route` — „Medzi týmito dvoma bodmi nevedie žiadna zjazdná cesta“ — and in `station_beyond_area`: the line on the ground, not the planned journey. |
| station | **stanica** | A bike-share station, and what Slovak networks call one. |
| railway station | **železničná stanica** | **The one distinction Slovak cannot make with a separate noun, and the reason one line carries an adjective.** Czech has *nádraží* for a railway station against *stanice* for a bike-share one; Slovak calls both a *stanica*. So `address_search_prompt_message` — the only string that means railway stations, and its English comment says so — writes **železničné stanice** in full. Nowhere else in the file needs the qualifier, and nowhere else should acquire it. |
| bike | **bicykel** | The everyday Slovak word. *Koleso* is a wheel, not a bike, so the Czech *kolo* has no counterpart here. Masculine inanimate, which is what the elliptical counts agree with: *4 mechanické · 2 elektrické*. |
| bike-share bikes, as a product | **zdieľané bicykle** | Only in the store texts and on the welcome page, where the thing has to be named before it is known. Inside the interface the context is settled and *bicykel* is enough. |
| dock (free) | **voľné miesto** | What one returns a bike into, counted as available: *6 bicyklov, 26 voľných miest*. Also the map's second mode, `mode_docks`. |
| dock (capacity) | **stojan** | The same object counted as a total, which is a different figure on the same screen: *12 voľných miest · 30 stojanov*. English says "dock" for both; Slovak does not have to, and *stojan* is the post a bike locks into. |
| dock | *never* **dok**, *never* **terminál** | The first is a harbour, the second the payment post. |
| free space (storage) | **priestor** | **The one collision a straight read of the file caught.** *Voľné miesto* is a free dock on every other screen, so the storage messages cannot use it for disk space or the same two words would name two unrelated things: `dataset_rejected_transfer` says *voľný priestor v úložisku* and `error_local_storage_download` says *Uvoľnite priestor*, which is Android's own (`settings:storage_free_up_space_title`). |
| mechanical / electric | **mechanický / elektrický** | Kept as adjectives, as in English, because the counts are elliptical: *4 mechanické · 2 elektrické* stands for *4 mechanické bicykle*. *Elektrobicykel* was left aside: it is a noun and would not agree with the adjective beside it. `journey_bike_kind_electric_description` says **s asistenciou pri šliapaní** so that "electric" cannot be read as a moped. |
| mechanical / electric, singular vs plural | **Mechanický** *and* **Mechanické** | Both forms are in the file on purpose. The journey screen and the own-bike setting name one bike — *Mechanický*, *Elektrický*, agreeing with *bicykel* — while the map's toggle names the bikes it counts — *Mechanické*, *Elektrické*, agreeing with *bicykle*, which the button beside them says. Same word, two agreements, two screens. |
| pace (walking) | **tempo chôdze** | A pace is not a speed, which `values/strings.xml` says above the string. *Tempo* is a pace; *rýchlosť* is the figure nobody has measured about themselves, and is not used. |
| Slow / Normal / Brisk | **Pomalé / Normálne / Svižné** | **A departure from Android, and a forced one.** The lexicon has *Pomalá* (`settings:speed_label_slow`), feminine because it is said of *rýchlosť*. Here the three words agree with *tempo*, which is neuter, so they are *Pomalé / Normálne / Svižné*. Same word, right ending. |
| climb | **prevýšenie** | The metres climbed, over a leg or over the whole journey. Written before its figure — *prevýšenie 120 m* — which keeps `journey_climb` free of any case on the placeholder. |
| location, position | **poloha** | English has two words here and Slovak has one, so the file uses one: *Moja poloha*, *Vaša poloha leží mimo…*, *Zistiť moju polohu*, *približná poloha*. **Poloha** is also Android's own word for the system feature and the permission (`android:permgrouplab_location`). |
| conurbation | **aglomerácia** | The city screen serves a metropolitan area rather than a municipality, and *mesto* is kept for the shorter word the settings section and the title need. |
| municipality (address) | **obec** | The administrative word, and the one the address index holds. *Mesto* would be wrong for the villages the index also carries. |
| house number | **číslo domu** | In `about_attribution_ban`, where a French source is being named. Deliberately **not** *súpisné číslo* or *orientačné číslo*: those are the two halves of a Slovak plate and would misdescribe the Base Adresse Nationale. In the search prompt the bare **číslo** is enough and shorter. |
| Settings | **Nastavenia** | Android's own (`settings:dashboard_title`), including in the system path quoted in `about_links_body` — *Nastavenia → Aplikácie → … → Predvolené otváranie → Pridať odkaz* — which is Android's own Slovak for that screen, key for key (`settings:launch_by_default`, `settings:app_launch_add_link`, `settings:apps_dashboard_title`). |
| Theme | **Motív** | Android's own: *Tmavý motív* (`settings:dark_ui_mode`), *Motív zariadenia* (`settings:device_theme`). Light / Dark are **Svetlý** / **Tmavý**. |
| Display (section) | **Zobrazenie** | Android's own name for the section that holds the theme (`settings:display_category_title`). *Obrazovka* — which Android also uses, for `settings:display_settings` — is the panel of glass, and the section is not about the glass. |
| Storage | **Úložisko** | **A departure from one Android key, with a reason.** `settings:storage_settings` says *Priestor*, which is Android's name for the phone's free-space screen. Ours is a screen of installed files, and Android itself calls that *úložisko* wherever a store of files is meant: *Spravovať úložisko* (`settings:automatic_storage_manager_settings`), *Úložisko zariadenia* (`android:device_storage_monitor_notification_channel`), *Zmeniť úložisko* (`settings:change_storage`). Everywhere another string points at that screen it says **v „Úložisku“** rather than translating "storage screen" literally. |
| erased (in a delete dialog) | **odstránené** | `city_delete_body` and `dataset_delete_body` say *budú odstránené* / *bude odstránená*, not *vymazané*: the button above them says *Odstrániť*, and a dialog whose button and whose sentence use two different verbs reads as two different actions. *Vymazať* is reserved for emptying a field. |
| Delete / Remove | **Odstrániť / Odobrať** | *Odstrániť* destroys — a city's data, a dataset — and is Android's own (`android:delete`, and *Odstrániť dáta aplikácie?* at `settings:clear_data_dlg_title`). *Odobrať* takes out of a list, and is used for favourites only; Android writes it for taking a user off the device (`settings:user_remove_user`, `settings:user_delete_user_description`, both *Odobrať používateľa*). **Android's own Slovak does blur these** — it renders bare "Remove" as *Odstrániť* too (`android:kg_reordering_delete_drop_target_text`) — so the two-word distinction the English file demands is ours, taken from the pair Android uses when it has to tell them apart. |
| Clear (a search) | **Vymazať hľadanie** | *Vymazať* is Android's verb for emptying a field rather than destroying an object (`settings:clear`, *Vymazať dopyt* at `settings:abc_searchview_description_clear`), which keeps a cleared search well away from a deleted dataset. „Dopyt“ itself is not used: the same English string is also a button in an empty state, where it reads as a database term. |
| Refresh / Updated | **Aktualizovať / Aktualizované** | One family, so that the button and what it produces read as one thing: *Aktualizovať* on the button, *Aktualizované práve teraz* under the data, *Nikdy neaktualizované* when there is none. Android writes the same pair (`android:autofill_update_yes`, *Aktualizované pred ^2* at `settings:no_carrier_update_text`). |
| Check for updates | **Skontrolovať aktualizácie** | Android's own is *Skontrolovať dostupnosť aktualizácie* (`android:deprecated_target_sdk_app_store`), which is a sentence rather than a button. Ours is a button, and this is the short form of the same wording. |
| Update available | **K dispozícii je aktualizácia** | Android's own, whole (`settings:android_version_pending_update_summary`). |
| Try again | **Skúsiť znova** | Android's own (`android:lockscreen_password_wrong`). |
| Continue | **Pokračovať** | Android's button word (`settings:lockpattern_continue_button_text`). *Ďalej* also appears in Android, on wizards; *Pokračovať* is the commoner of the two and is what both `welcome_continue` and `whats_new_done` say. |
| Skip | **Preskočiť** | Android's own (`android:skip_button_label`). |
| Back | **Späť** | Android's own, on the toolbar arrow (`android:back_button_label`). |
| Cancel | **Zrušiť** | Android's own (`android:cancel`). |
| In use | **Používa sa** | Android's own (`android:media_route_status_in_use`), on the city already selected. |
| Out of service | **Mimo prevádzky** | Android's own (`settings:radioInfo_service_out`). |
| just now | **práve teraz** | Lower-cased because it is always read inside `freshness_fresh`: *Aktualizované práve teraz*. Android's `settings:time_unit_just_now` is the bare *Teraz*, which stands alone in a list; read inside our sentence it would come out as *Aktualizované teraz*, which is not how the moment is said. Android's own full sentence for it is *Práve aktualizované* (`settings:no_carrier_update_now_text`), and this keeps that adverb. |
| Replace | **Nahradiť** | Android's own (`settings:vpn_replace`). |
| Yes | **Áno** | Android's own (`settings:yes`). |
| Language | **Jazyk** | Android's own (`settings:app_locale_preference_title`). |
| Wi-Fi | **Wi-Fi** | Untranslated in Android's Slovak too (`settings:wifi`). |
| unmetered / metered | **nemerané / účtované po megabajtoch** | Android labels a connection *Nemerané* / *Merané* (`settings:wifitrackerlib_wifi_unmetered_label`, `settings:wifi_metered_label`). The sentences explaining the setting say what is billed — *účtuje sa po megabajtoch* — since that is the point the English makes. |
| Tap | **Klepnutím** | Android's verb is *Klepnite na* (`settings:accessibility_shortcut_edit_dialog_summary_floating_button`); the instrumental *Klepnutím* is what fits the "do X and Y happens" shape these lines have, and is Android's own too (*Klepnutím zobrazíte ďalšie možnosti*, `android:usb_notification_message`). |
| Press and hold | **stlačením a pridržaním** | The gesture, in the same instrumental shape. *Pridržanie* is Android's noun for it (`settings:power_menu_setting_name`, `android:content_description_sliding_handle`). |
| app | **aplikácia** | Android's own (`settings:apps_dashboard_title`). |
| data | **dáta** | Android's Slovak uses *dáta* for what an application stores and *údaje* for particulars about a person; ours is the former throughout — *mapové dáta*, *offline dáta*, *dátová sada* — following `settings:clear_data_dlg_title` (*Odstrániť dáta aplikácie?*). |
| map data / tiles | **mapové dáta** | Names the dataset on the storage screen, and every other string that points at it uses the same word. |
| routing data | **dáta na výpočet trás** | Pairs with *trasa*: the data a route is computed from. It is the reason the store texts and the privacy lines say *cesty* for journey data. |
| address index | **register adries** | An index one looks a name up in — which is exactly what Slovak calls the index at the back of a book. *Databáza* says how it is stored, which is not the reader's business, and *zoznam* is a list rather than something searched. |
| offline data | **offline dáta** | *Offline* is the word Slovak uses as it stands; *dáta* is Slovak. |
| what's new | **novinky** | What the screen shows is the release notes. |
| tracker | **sledovací nástroj** | Used in the interface and in the store's short description alike, so the promise reads the same before and after installing. |

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Vélo’v, Citi Bike,
BRouter, MapLibre, OpenStreetMap, GBFS — and the licence names. Unit symbols:
`m`, `km`, `ft`, `yd`, `mi`, `min`, `h`, which Slovak writes as they stand (`h`
rather than `hod.`, since the same string also serves a stopwatch-style
"1 h 05"). File-size symbols `B`, `kB`, `MB`, `GB`. `resources` `name`
attributes, always.

## Strings that come back identical to the English

None. `tools/check_translations.py sk` reports no line to confirm: the two that
tripped Czech both differ here — *Offline dáta* against "Offline data", and
*Licencia* against "Licence".
