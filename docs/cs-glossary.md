# Czech glossary

The terms `res/values-cs/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Czech ones over three screens, and so that a contributor can correct
one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Register and typography

The Czech uses **vykání** — the reader is addressed as *vy* — where the French
file tuts and the German says *du*. That is not a house style but Android's
own: of the 5 900 system strings extracted from this phone's `framework-res.apk`
and `Settings.apk`, a hundred and forty-eight carry a *vy* form (*Klepněte*,
*Zkontrolujte*, *Zadejte*, *Podržte*, *Můžete*, *Vaše*) and **not one** tyká.
An application that tuts a Czech reader announces itself as a translation.

Buttons take the **infinitive**, which is Android's habit for a control rather
than a sentence: *Pokračovat*, *Přeskočit*, *Smazat*, *Zkusit znovu*,
*Zobrazit*. Sentences take the *vy* imperative: *Zkontrolujte pravopis*,
*Nainstalujte rejstřík*, *Vyberte město*.

Quotation marks are **„ … “**. Diacritics are written in full — ě š č ř ž ý á
í é ů — and never dropped. The dash that breaks a sentence is **–** with a
space on either side, not the em dash the English file uses; that is the one
piece of punctuation changed inside a format-only string, `city_label`
(`%1$s – %2$s`). The apostrophe, where a network's name carries one (*Vélib’*,
*V’lille*, *Vélo’v*), is **’** (U+2019) and never the straight quote, which is
why nothing in the file is escaped.

A **non-breaking space** (U+00A0) stands after every one-letter preposition or
conjunction — **k, s, v, z, o, u, a, i** — which Czech typesetting never leaves
at the end of a line. There are 113 of them in the strings file and 92 more in
the store texts. The two-letter vocalised forms — *ke, se, ve, ze* — take an
ordinary space: the rule is about a single letter left hanging, and they are
not one.

## Cases are suffixes, and a placeholder cannot carry one

Czech declines, and the ending falls on the word itself. A sentence built
around `%1$s` has to stay right whatever arrives in it, and what arrives is
always a nominative: a station name, a street name, a city, a network label.
So four lines are written around that rather than against it, and each is worth
knowing before it gets "fixed":

| String | What it does | Why |
|---|---|---|
| `station_address_nearby` | `Poblíž: %1$s` | *Poblíž* governs the genitive, and the argument is a street **or** a square (`address.streetName` reaches it as it stands). A colon turns the line into a label, which declines nothing. |
| `journey_step_to_station`, `journey_step_ride` | `Pěšky ke stanici %1$s` | The case falls on *stanici*; the name follows in apposition, in the nominative, exactly as it arrived. |
| `city_delete_description`, `city_delete_body`, `city_deleted` | `Smazat data: %1$s`, `… data „%1$s“ …` | **Not the apposition device, and here is why not.** All three are handed `city.displayName` (`CityAdapter.kt:111`, `CityFragment.kt:279` and `:294`), which is the **network's** name, not the city's: 328 of the 331 catalogue entries carry a `displayName` of their own, and all 36 Czech networks are called *nextbike*. Writing „data města %1$s“ would have made a Brno reader read „data města nextbike“ — a false statement the English never makes, and one no other translation makes either. The colon and the quotation marks hold the name at arm's length instead, exactly as `dataset_deleted` does. |
| `dataset_imported`, `dataset_deleted` | `Nainstalováno: %1$s` | A dataset's name is masculine in one case (*rejstřík adres*) and a neuter plural in two (*mapová data*, *data pro výpočet tras*), so no participle can be written that agrees with all three. A label with a colon agrees with nothing. |

`city_here_body` and `city_here_installed_body` have the same problem in the
other direction: they need a possessive for the network, whose gender the
placeholder hides. They say **data této sítě** and **její stanice** — *síť* is
feminine and fixed — rather than a pronoun that would have to agree with
whatever `cityLabel` produced.

## The address prompt, and the number Czech will lose

`address_search_hint` is **„Ulice, číslo, obec“** — street, number, town, which
is the order Czech writes an address in. `AddressQuery.parseQuery` has read a
house number standing between the street and the town since the pilot, precisely
so that each language may write this line in its own order.

**The order a result is printed in is a separate matter, and is not this file's
to decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3): *Národní 25a* is how a Prague address is written for every
reader, and *12 rue Nationale* is how a Lyon one is written for a Czech reader.
The two formats that used to live here, `address_with_number` and
`address_number_with_suffix`, were removed for that reason; the layouts are a
table in `core/address/AddressLayout.kt`, keyed on the language of the **address
base**, and the Czech entry is the one that closes the letter up against the
number.

**The postcode is deliberately not invited.** Czech writes it in two groups —
*110 00* — and `looksLikePostcode` only strips a single group of five digits.
A postcode typed in therefore reaches the parser as two more numbers,
`holdsSeveralNumbers` sees three, and the house number is given up. Asking for
it would break the reading of every query that obeyed.

**And Czech loses the number on a street named after a date.** The second guard
— no stop word beside the number — is what protects *rue du 8 Mai 1945* and
*Straße des 17. Juni*. Czech writes its dates **without a preposition**:
*náměstí 28. října*, *třída 17. listopadu*, *nábřeží 1. máje*. Nothing stands
beside the 28 but the words of the name, so the application reads it as a house
number and searches *náměstí října* for door 28.

That is a known limit, written down in `SPEC.md` §4.3 and in the KDoc of
`parseQuery`, and the cost of it is bounded: the words left over still name the
street, so the street is still found — only the point inside it is taken from a
number that was never one. Telling the two apart needs the month names of the
language, which belong in `config/address-normalization/cs.json` rather than in
the parser. **The prompt is written in full knowledge of it**: it invites a
street, a number and a town, because that is what a Czech reader will type
whatever the prompt says, and inviting less would cost every ordinary address
to protect a handful of named squares.

## The vocabulary

| English | Czech | Why |
|---|---|---|
| journey | **trasa** | The whole door-to-door thing: the screen, the settings section, the button, the errors, the waits. It is what Czech mapping applications call a planned trip (*naplánovat trasu*, *zobrazit celou trasu*), and it is short, which matters on `journey_compute` and `journey_frame`. |
| journey data (privacy) | **cesty** | The one place *trasa* is deliberately not used, and it holds across **all three** sentences that promise nothing is kept: `welcome_privacy_body` ("ani vaše cesty, ani vaše polohy"), `about_privacy_body` ("adresy, cesty i vaše poloha") and the store's own bullet ("nic o vašich cestách"). "Neuchovává se nic o vašich trasách" would collide head-on with `dataset_routing`, which **is** called *data pro výpočet tras* and **is** stored on the device — the sentence would say the opposite of the truth. `about_privacy_body` is the one that matters most, since it is the only one of the three that cohabits in the application with an Úložiště screen announcing those very data as installed. Do not "correct" any of them back to *trasa*. |
| ride | **jízda** | The bike leg alone, inside a journey: `journey_computing_own_bike` ("Počítá se jízda…"), `journey_detail_profile`, `journey_detail_profile_description`. A different word from *trasa*, so the elevation profile and the own-bike wait cannot be mistaken for the whole thing. |
| journey (changelogs/1.txt) | **cesta** | "skládá **cestu** pěšky → na kole → pěšky". *Trasa* is the word everywhere else, but the sentence already carries *trasy* twice ("Výpočet trasy skládá trasu…"), and Czech will not take the repetition. Written down here so the next contributor sees a choice rather than a slip; rewriting the sentence impersonally would work too. |
| route | **cesta** | Only in `journey_no_route` — "Mezi těmito dvěma body nevede žádná sjízdná cesta" — and in `station_beyond_area`: the line on the ground, not the planned journey. |
| station | **stanice** | A bike-share station, and what Czech networks call one. |
| railway station | **nádraží** | What `address_search_prompt_message` means by "stations", and Czech has a separate word for it, so there is no ambiguity to manage: *stanice* stays the bike-share one throughout. |
| bike | **kolo** | The everyday word. *Jízdní kolo* is the form filled in on a customs declaration; nobody says it on a phone. Neuter, so the elliptical counts agree with it: *4 mechanická · 2 elektrická*. |
| bike-share bikes, as a product | **sdílená kola** | Only in the store texts and on the welcome page, where the thing has to be named before it is known. Inside the interface the context is settled and *kolo* is enough. |
| dock (free) | **volné místo** | What one returns a bike into, counted as available: *6 kol, 26 volných míst*. Also the map's second mode, `mode_docks`. |
| dock (capacity) | **stojan** | The same object counted as a total, which is a different figure on the same screen: *12 volných míst · 30 stojanů*. English says "dock" for both; Czech does not have to, and *stojan* is the post a bike locks into. |
| dock | *never* **dok**, *never* **terminál** | The first is a harbour, the second the payment post. |
| mechanical / electric | **mechanické / elektrické** | Kept as adjectives, as in English, because the counts are elliptical: *4 mechanická · 2 elektrická* stands for *4 mechanická kola*. *Elektrokolo* was left aside: it is a noun and would not agree with the adjective beside it. `journey_bike_kind_electric_description` says **s asistencí při šlapání** so that "electric" cannot be read as a moped. |
| pace (walking) | **tempo chůze** | A pace is not a speed, which `values/strings.xml` says above the string. *Tempo* is a pace; *rychlost* is the figure nobody has measured about themselves, and is not used. |
| Slow / Normal / Brisk | **Pomalé / Normální / Svižné** | **A departure from Android, and a forced one.** The lexicon has *Pomalá* (`settings:speed_label_slow`), feminine because it is said of *rychlost*. Here the three words agree with *tempo*, which is neuter, so they are *Pomalé / Normální / Svižné*. Same word, right ending. |
| climb | **převýšení** | The metres climbed, over a leg or over the whole journey. Written before its figure — *převýšení 120 m* — which keeps `journey_climb` free of any case on the placeholder. |
| location, position | **poloha** | English has two words here and Czech has one, so the file uses one: *Moje poloha*, *Vaše poloha leží mimo…*, *Zjistit moji polohu*, *přibližná poloha*. **Poloha** is also Android's own word for the system feature and the permission (`android:permgrouplab_location`). Forcing a second word — *pozice* — to mirror the English would read as a translation, not as Czech. |
| conurbation | **aglomerace** | The city screen serves a metropolitan area rather than a municipality, and *město* is kept for the shorter word the settings section and the title need. |
| municipality (address) | **obec** | The administrative word, and the one the address index holds. *Město* would be wrong for the villages the index also carries. |
| house number | **číslo popisné** | In `about_attribution_ban`, where the source is being named precisely. In the search prompt the bare **číslo** is enough and shorter. |
| Settings | **Nastavení** | Android's own word, including in the system path quoted in `about_links_body` — *Nastavení → Aplikace → … → Otevírání ve výchozím nastavení → Přidat odkaz* — which is Android's own Czech for that screen, key for key (`settings:launch_by_default`, `settings:app_launch_add_link`). |
| Theme | **Motiv** | Android's own: *Tmavý motiv* (`settings:dark_ui_mode`), *Motiv zařízení* (`settings:device_theme`). Light / Dark are **Světlý** / **Tmavý**. |
| Display (section) | **Zobrazení** | Android's own name for the section that holds the theme (`settings:display_category_title`). *Displej* is the panel of glass, which is not what the section is about. |
| Storage | **Úložiště** | Android's own (`settings:storage_settings`), and the name the screen carries. Everywhere another string points at that screen it says **v „Úložišti“** rather than translating "storage screen" literally. |
| Delete / Remove | **Smazat / Odebrat** | *Smazat* destroys — a city's data, a dataset, a search, a picked point — and is Android's own (`android:delete`). *Odebrat* takes out of a list, and is used for favourites only (`android:kg_reordering_delete_drop_target_text`). Android distinguishes them the same way. |
| Clear (a search) | **Smazat hledání** | Android says *Smazat dotaz* for the icon inside a field (`android:searchview_description_clear`), but the same English string is also a button in an empty state, where "dotaz" reads as a database term. One wording serves both, and it keeps Android's verb. |
| Refresh / Updated | **Aktualizovat / Aktualizováno** | One family, so that the button and what it produces read as one thing: *Aktualizovat* on the button, *Aktualizováno právě teď* under the data, *Nikdy neaktualizováno* when there is none. |
| Check for updates | **Zkontrolovat aktualizace** | Android's own (`android:deprecated_target_sdk_app_store`). |
| Update available | **K dispozici je aktualizace** | Android's own, whole (`settings:android_version_pending_update_summary`). |
| Try again | **Zkusit znovu** | Android's own (`android:lockscreen_password_wrong`). |
| Continue | **Pokračovat** | Android's button word (`settings:lockpattern_continue_button_text`). |
| Skip | **Přeskočit** | Android's own (`android:skip_button_label`). |
| Back | **Zpět** | Android's own, on the toolbar arrow (`android:back_button_label`). |
| Cancel | **Zrušit** | Android's own (`android:cancel`). *Storno* exists in the lexicon but only on the print dialog. |
| In use | **Používá se** | Android's own (`android:media_route_status_in_use`), on the city already selected. |
| Out of service | **Mimo provoz** | The ordinary Czech for a machine that is not working. |
| just now | **právě teď** | Lower-cased because it is always read inside `freshness_fresh`: *Aktualizováno právě teď*. |
| Replace | **Nahradit** | Android's own (`settings:vpn_replace`). |
| Yes | **Ano** | Android's own (`settings:yes`). |
| Language | **Jazyk** | Android's own (`settings:app_locale_preference_title`). |
| Wi-Fi | **Wi-Fi** | Untranslated in Android's Czech too (`settings:wifi`), and untranslatable in practice. |
| unmetered / metered | **neměřené / účtované po megabajtech** | Android labels a connection *Neměřená* / *Měřená* (`settings:wifi_unmetered_label`), feminine because it is said of *síť*; here the noun is *připojení*, neuter, so it is *neměřené připojení*. The sentences explaining the setting say what is billed — *účtuje se po megabajtech* — since that is the point the English makes. |
| Tap | **Klepnutím** | Android's verb is *Klepněte na* (`settings:accessibility_shortcut_edit_dialog_summary_floating_button`); the instrumental *Klepnutím* is what fits the "do X and Y happens" shape these lines have: *Klepnutím spočítáte jen mechanická*. |
| Press and hold | **stisknutím a podržením** | The gesture, in the same instrumental shape: *Řádek přesunete stisknutím a podržením*. |
| app | **aplikace** | Android's own (`settings:apps_dashboard_title`). |
| map data / tiles | **mapová data** | Names the dataset on the storage screen, and every other string that points at it uses the same word. |
| routing data | **data pro výpočet tras** | Pairs with *trasa*: the data a route is computed from. Longer than the German *Routendaten* because Czech does not compound, and it is the reason the store texts say *cesty* for journey data. |
| address index | **rejstřík adres** | An index one looks a name up in. *Registr* is what the state keeps; *databáze* says how it is stored, which is not the reader's business. |
| offline data | **offline data** | Both words are Czech as they stand, which is why `settings_section_data` comes back identical to the English and is right. |
| what's new | **novinky** | What the screen shows is the release notes. |
| tracker | **sledovací nástroj** | Used in the interface and in the store's short description alike, so the promise reads the same before and after installing. |

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Vélo’v, Citi Bike,
BRouter, MapLibre, OpenStreetMap, GBFS — and the licence names. Unit symbols:
`m`, `km`, `ft`, `yd`, `mi`, `min`, `h`, which Czech writes as they stand (`h`
rather than `hod.`, since the same string also serves a stopwatch-style
"1 h 05"). File-size symbols `B`, `kB`, `MB`, `GB`. `resources` `name`
attributes, always.

## Two strings that come back identical to the English, and should

`tools/check_translations.py` reports both, and both are right:

- **`settings_section_data`, "Offline data"** — *offline* is the word Czech
  uses, and *data* is Czech. Written any other way it would be a paraphrase.
- **`about_licence_title`, "Licence"** — the Czech for a licence is *licence*.
