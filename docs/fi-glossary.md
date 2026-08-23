# Finnish glossary

The terms `res/values-fi/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Finnish ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

The lexicon quoted below is the 5 885 system strings extracted from this
phone's `framework-res.apk` and `Settings.apk`. Every key cited was grepped in
it; where no key is cited, the choice is this file's own and says so.

## Register and typography

Finnish has no T/V distinction to settle: the second person singular — *sinä*,
*-si*, *kävele*, *valitse* — is what Android's own Finnish uses throughout, and
it is what this file uses. Buttons take the **imperative**: *Jatka*, *Ohita*,
*Poista*, *Yritä uudelleen*, *Näytä*, *Lataa*.

The English source is **scrupulously impersonal** where it promises that
nothing is kept — "No history is kept", "It is read from the feed" — and
Finnish keeps it, with the passive that the language has for exactly this:
*Historiaa ei säilytetä*, *Reitit lasketaan tässä puhelimessa*, *Tieto luetaan
verkoston omasta syötteestä*, *Aineistot ladataan vain pyydettäessä*. There is
no *me* anywhere in the file, and none should be added: an application whose
argument is that nobody is behind it must not ask the reader to trust a "we".

Quotation marks are **” … ”**, closing on both sides — `stations_no_match_message`,
`city_delete_body`, `error_feed_unavailable`, `settings_map_filters_hide_empty_hint`.
The dash that breaks a sentence is **–** with a space on either side, not the
em dash the English file uses; that is the one piece of punctuation changed
inside a format-only string, `city_label` (`%1$s – %2$s`). No space stands
before `?`, `!`, `:` or `;`. The apostrophe, where a network's name carries one
(*Vélib’*, *V’lille*, *Vélo’v*), is **’** (U+2019) and never the straight
quote, which is why nothing in the file is escaped.

## Cases are suffixes, and a placeholder cannot carry one

Finnish declines over fifteen cases, and the ending falls on the word itself. A
sentence built around `%1$s` has to stay right whatever arrives in it, and what
arrives is always a nominative: a station name, a street name, a network label,
a dataset name, a size. Not one line in this file glues an ending onto a
placeholder. Five devices carry the weight, and each is worth knowing before it
gets "fixed":

| String | What it does | Why |
|---|---|---|
| `journey_step_to_station`, `journey_step_ride` | `Kävele asemalle %1$s` | The allative falls on *asemalle*; the station's name follows in apposition, in the nominative exactly as it arrived. |
| `station_address_nearby` | `Lähellä: %1$s` | *Lähellä* governs the genitive, and the argument is `address.streetName` — a street **or** a square, reaching the string as it stands. A colon turns the line into a label, which declines nothing. |
| `city_delete_body` | `Kaikki verkoston ”%1$s” offline-aineistot poistetaan.` | The genitive falls on *verkoston*; the name sits behind it in quotation marks, untouched. |
| `city_delete_description`, `city_deleted` | `Poista aineistot: %1$s`, `Aineistot poistettu: %1$s` | Same argument, but these two are a content description and a snackbar, where a label with a colon is shorter and reads better than a full sentence. |
| `journey_climb` | `nousua %1$s` | The noun stands **before** the figure — *nousua 120 m* — which is where Finnish puts a partitive amount and keeps every case off the placeholder. |
| `dataset_imported`, `dataset_deleted`, `storage_download_pending`, `dataset_delete_description` | `%1$s asennettu`, `Lataa %1$s` | A dataset's name arrives in the nominative, which is what a subject takes and what the object of an imperative takes. Nothing needs bending. |

**`city_delete_description`, `city_delete_body` and `city_deleted` are handed
the NETWORK's name, not the city's.** `CityAdapter.kt:111`, `CityFragment.kt:279`
and `:294` all pass `city.displayName`, and 328 of the 331 catalogue entries
carry a `displayName` of their own — every Czech network is called *nextbike*.
Writing *kaupungin %1$s aineistot* would have made a Brno reader read "the data
of the city nextbike". None of the three says *kaupunki*; the English never
does either.

`city_here_body`, `city_here_installed_body`, `map_outside_city_message` and
`map_outside_city_brief` are handed `cityLabel(...)`, which is a whole label —
*Vélib’ Métropole – Paris*. Each puts it in **subject position**, where the
nominative is what Finnish wants: *%1$s palvelee aluetta, jolla olet*,
*Sijaintisi on sen alueen ulkopuolella, jota %1$s palvelee*.

## The address prompt

`address_search_hint` is **”Katu, numero, paikkakunta”** — street, then number,
then municipality, which is the order Finnish writes an address in
(*Mannerheimintie 12, Helsinki*). `AddressQuery.parseQuery` has read a house
number standing between the street and the town since the pilot, precisely so
that each language may write this line in its own order rather than in English's.

*Paikkakunta* rather than *kaupunki*: the index carries villages as well as
cities, and *kaupunki* would be wrong for them. *Kunta* alone is the
administrative unit, which is not what somebody typing an address is thinking of.

**The postcode is deliberately not invited**, even though Finland writes it as
a single group of five digits (*00100*) — the one shape `looksLikePostcode`
could strip. `AddressQuery` gives the house number up as soon as the query holds
a second number, and a Finn writing the full address writes *Mannerheimintie 12,
00100 Helsinki*: two numbers, and the door is lost. Inviting the postcode would
cost the house number on every query that obeyed the prompt.

The second guard — no stop word beside a number read between street and town —
protects streets named after a date. Finnish has few of them and writes them
with the ordinal attached to a noun (*Kolmas linja*, *Neljäs linja*), so the
common Finnish case is a spelled-out ordinal rather than a digit and the guard
is not stressed the way Czech's is.

**The layout of an address is not this file's business.** A Helsinki address
reads *Mannerheimintie 12* to a reader in French, and a Lyon address reads
*12 rue Nationale* to a reader in Finnish. That lives in
`core/.../address/AddressLayout.kt`, keyed on the language of the address base
(SPEC §4.3), and this language decides only the words around an address. The
Finnish entry — street then number, closed up, no separator — matches how
Finland writes it; nothing needed changing there.

## The vocabulary

| English | Finnish | Why |
|---|---|---|
| journey | **reitti** | The whole door-to-door thing: the screen, the settings section, the button, the errors, the waits. It is what Finnish journey planners call a planned trip (*suunnittele reitti*, *reittiopas*), and it is short, which `journey_compute` and `journey_frame` need. |
| journey data (privacy) | **matka** | The one place *reitti* is deliberately not used, and it holds across both sentences that promise nothing is kept: `welcome_privacy_body` (*ei matkoja, ei sijainteja, ei määränpäitä*) and `about_privacy_body` (*osoitteet, matkat ja sijaintisi*). *Ei reittejä* would collide head-on with `dataset_routing`, which **is** called *Reittiaineisto* and **is** stored on the device — the sentence would say the opposite of the truth on a screen that sits two taps away. Do not "correct" either back to *reitti*. |
| ride | **pyöräosuus** | The bike leg alone, inside a journey: `journey_computing_own_bike`, `journey_detail_profile`, `journey_detail_profile_description`. A different word from *reitti*, so the elevation profile and the own-bike wait cannot be mistaken for the whole thing. |
| route | **väylä** | Only in `journey_no_route` — *Näiden kahden pisteen välillä ei ole kuljettavaa väylää* — the line on the ground, not the planned journey. *Yhteys* was the obvious alternative and is refused: it is the word `error_offline` uses for a network connection (*Ei yhteyttä*), and the two would have read as one. |
| station | **asema** | A bike-share station, and what Finnish city-bike systems call one. |
| railway station | **rautatieasema** | What `address_search_prompt_message` means by "stations", as its comment in `values/` says. Finnish has a separate compound for it, so *asema* stays the bike-share one throughout with no ambiguity to manage. |
| bike | **pyörä** | The everyday word. *Polkupyörä* is what a form asks for; nobody says it on a phone. |
| bike-share bikes, as a product | **kaupunkipyörä** | Only in the store texts and on the welcome page, where the thing has to be named before it is known. Inside the interface the context is settled and *pyörä* is enough. |
| dock (free) | **vapaa paikka** | What one returns a bike into, counted as available: *6 pyörää · 26 vapaata paikkaa*. Also the map's second mode, `mode_docks` (*Vapaat paikat*). |
| dock (capacity) | **teline** | The same object counted as a total, which is a different figure on the same screen: *26 vapaata paikkaa · 30 telinettä*. English says "dock" for both; Finnish does not have to, and *teline* is the stand a bike locks into. |
| dock | *never* **dokki**, *never* **maksupääte** | The first is a harbour, the second the payment post. |
| mechanical | **tavallinen** | The word Finnish actually uses against *sähköpyörä*. *Mekaaninen pyörä* is not said. Plural on the map toggle (*Tavalliset*), singular where one bike is being chosen (*Tavallinen*). |
| electric | **sähköpyörä** | Likewise the word Finnish uses. `journey_bike_kind_electric_description` says **sähköavusteinen pyörä** so that "electric" cannot be read as a moped. |
| the two counts side by side | **%1$d tavallista · %1$d sähköpyörää** | An asymmetry on purpose, and the one place this glossary bends. The mechanical count is elliptical — *6 tavallista* stands for *6 tavallista pyörää* — while the electric one names the noun, because *sähköinen* on its own reads as "electronic" in Finnish and *sähköpyörää* is what a Finn would say aloud. Both are partitive singular, which is what any numeral but one takes, so the two agree with the figure beside them. |
| pace (walking) | **kävelytahti** | A pace is not a speed, which `values/strings.xml` says above the string. *Tahti* is a pace; *nopeus* is the figure nobody has measured about themselves, and is not used. |
| Slow / Normal / Brisk | **Hidas / Normaali / Reipas** | *Hidas* is Android's own (`settings:speed_label_slow`). *Reipas* is the ordinary Finnish for a brisk walk. *Normaali* rather than *Tavallinen*, which this file has already spent on the mechanical bike two settings sections away. |
| climb | **nousu** | The metres climbed, over a leg or over the whole journey, written before its figure: *nousua 120 m*. |
| location, position | **sijainti** | English has two words here and Finnish has one, so the file uses one: *Oma sijainti*, *Sijaintisi on … ulkopuolella*, *Paikanna minut*, *likimääräinen sijainti*. **Sijainti** is also Android's own word for the system feature and the permission (`android:permgrouplab_location`). Forcing a second word to mirror the English would read as a translation, not as Finnish. |
| network (bike-share) | **verkosto** | The operator whose bikes these are: *verkoston palvelin*, *verkoston syöte*, *Verkosto lainaa vain sähköpyöriä*. |
| network (data) | **verkko** | The connection: *ilman verkkoa*, *ei lähde koskaan verkkoon*, *Wi-Fi-verkko*. Android's own (`settings:network_operator_category`). The two are one word in English and two in Finnish, and keeping them apart is what stops *the network's server* from reading as *the internet's server*. |
| conurbation | **kaupunkiseutu** | The city screen serves a metropolitan area rather than a municipality, and *kaupunki* is kept for the shorter word the settings section and the title need. |
| municipality (address) | **paikkakunta** | See the address prompt above. |
| dataset | **aineisto** | *Kartta-aineisto*, *Reittiaineisto*, *Osoitehakemisto*; *Offline-aineistot* for the settings section and the storage screen. *Data* exists in Finnish but is uncountable, and these are three countable things the screen lists and deletes one at a time. |
| address index | **osoitehakemisto** | An index one looks a name up in. *Rekisteri* is what the state keeps; *tietokanta* says how it is stored, which is not the reader's business. |
| Settings | **Asetukset** | Android's own (`settings:app_locale_preference_title` and passim), including in the system path quoted in `about_links_body` — *Asetukset → Sovellukset → Roue Libre → Avaa oletuksena → Lisää linkki* — which is Android's own Finnish for that screen, key for key (`settings:apps_dashboard_title`, `settings:launch_by_default`, `settings:app_launch_add_link`). |
| Theme | **Teema** | Android's own: *Tumma teema* (`settings:dark_ui_mode`), *Laitteen teema* (`settings:device_theme`). Dark is **Tumma**, from the same key. |
| Light (theme) | **Vaalea** | **Not from the lexicon, and here is why.** Android's Finnish names only the dark theme; there is no "light theme" string to grep in `framework-res` or `Settings`. *Vaalea* is the ordinary Finnish opposite of *tumma* and is what Finnish interfaces use. |
| Display (section) | **Näyttö** | Android's own name for the section that holds the theme (`settings:display_category_title`, `settings:display_settings`). |
| Storage | **Tallennustila** | Android's own (`settings:storage_settings`), and the name the screen carries. Everywhere another string points at that screen it says **Tallennustila-näytöltä** rather than translating "storage screen" loosely — `map_needs_tiles_message`, `journey_graph_missing`, `address_needs_index_message` all say it the same way. |
| Delete / Remove | **Poista**, for both | **A departure from the brief's distinction, and a forced one.** English keeps *delete* (destroy) apart from *remove* (take out of a list), and Finnish does not: Android itself writes **Poista** for both (`android:delete` and `settings:delete` for delete, `android:kg_reordering_delete_drop_target_text` and `settings:remove` for remove). What carries the distinction in Finnish is the case, not the verb: `station_favourite_remove` is **Poista suosikeista** — elative, "out of the favourites" — against the bare *Poista* that destroys a dataset. Do not invent a second verb to mirror the English; there is none a Finn would recognise. |
| Clear (a search) | **Tyhjennä haku** | Android's verb for emptying a field (`settings:clear`, `settings:proxy_clear_text`). One wording serves both the icon inside the field and the button in the empty state. |
| Refresh / Updated | **Päivitä / Päivitetty** | One family, so that the button and what it produces read as one thing: *Päivitä* on the button, *Päivitetty äsken* under the data, *Ei päivitetty koskaan* when there is none. |
| Check for updates | **Tarkista päivitykset** | Android's own (`android:unsupported_compile_sdk_check_update`). |
| Update available | **Päivitys saatavilla** | Android's own, whole (`settings:android_version_pending_update_summary`). |
| Try again | **Yritä uudelleen** | Android's own (`settings:retry`). *Yritä myöhemmin uudelleen* where the English adds "later" (`android:restr_pin_try_later`). |
| Continue | **Jatka** | Android's button word (`settings:lockpattern_continue_button_text`). |
| Skip | **Ohita** | Android's own (`android:skip_button_label`). |
| Back | **Takaisin** | Android's own, on the toolbar arrow (`android:back_button_label`). |
| Cancel | **Peru** | Android's own (`android:cancel`), which is what the framework writes on every dialog; *Peruuta* appears in `Settings` on three keys only. |
| In use | **Käytössä** | Android's own (`android:media_route_status_in_use`), on the city already selected. |
| Out of service | **Poissa käytöstä** | **A departure from Android, and a deliberate one.** The lexicon's translation of "Out of service" is *Katvealueella*, but the key it comes from is `settings:radioInfo_service_out` — a phone in a **radio blackspot**, not a machine that is not working: a bike station is not "in a dead zone". *Poissa käytöstä* is the ordinary Finnish for a machine out of service, and it is what `settings_map_filters_hide_out_of_service` echoes (*poissa käytöstä olevat asemat*). |
| just now | **äsken** | Lower-cased because it is only ever read inside `freshness_fresh`: *Päivitetty äsken*. |
| Replace | **Korvaa** | Android's own (`settings:vpn_replace`). |
| Import | **Tuo** | **Not from the lexicon**: neither `framework-res` nor `Settings` carries an "Import" string to grep. *Tuo* is the ordinary Finnish for bringing a file in, and the counterpart of *vie* for sending one out. |
| Yes | **Kyllä** | Android's own (`android:gpsVerifYes`, `settings:yes`). |
| Language | **Kieli** | Android's own (`settings:app_locale_preference_title`). |
| System | **Järjestelmä** | Android's own (`settings:header_category_system`), for the theme, the units and the language alike. |
| Privacy | **Yksityisyys** | Android's own (`settings:privacy_dashboard_title`). |
| Version | **Versio** | Android's own (`settings:vpn_version`). |
| Wi-Fi | **Wi-Fi** | Untranslated in Android's Finnish too (`settings:wifi`). |
| unmetered / metered | **maksuton / maksullinen** | Android labels a connection *Maksuton* / *Maksullinen* (`settings:wifi_unmetered_label`, `settings:wifi_metered_label`). The sentences explaining the setting say what is billed — *laskutetaan megatavuittain* — since that is the point the English makes. |
| Free up space | **Vapauta tilaa** | Android's own (`settings:storage_free_up_space_title`), in `error_local_storage_download`. |
| Tap | **Napauta, niin …** | Android's verb is *Napauta* (`settings:security_settings_remoteauth_enroll_introduction_animation_tap_notification`), and the *Napauta, niin …* shape is Android's own for "do X and Y happens" (`android:vpn_text`: *Napauta, niin voit hallinnoida verkkoa*). |
| Press and hold | **koskettamalla pitkään** | Android writes the gesture *Kosketa pitkään* (`android:content_description_sliding_handle`); `favourites_reorder_hint` puts it in the instrumental shape the sentence needs. |
| app | **sovellus** | Android's own (`settings:apps_dashboard_title`). |
| what's new | **Uutta** | What the screen shows is the release notes, and *Uutta* is what an app store writes above them. |
| tracker | **seuranta** | Used in the interface and in the store's short description alike, so the promise reads the same before and after installing. |

## Units, and the one set that is translated

Distance and time symbols stay as they are — `m`, `km`, `ft`, `yd`, `mi`,
`min`, `h` — because Finnish writes them the same way.

**The byte units are not.** Finnish writes them with a `t`, for *tavu*: `kt`,
`Mt`, `Gt`, and Android's own Finnish does the same (`settings:enable_16k_pages`,
*”Käynnistä 16 kt:n sivukoolla”*). So `size_bytes`, `size_kilobytes`,
`size_megabytes` and `size_gigabytes` are translated, and the store texts follow
— *1,3 Gt*, *143 Mt* — with the decimal comma Finnish uses. A reader who sees
*143 Mt* in the changelog must see *143 Mt* on the storage screen too.

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Vélo’v, Citi Bike,
BRouter, MapLibre, OpenStreetMap, GBFS — and the licence names. The
`resources` `name` attributes, always. And the format-only strings
(`station_content_description`, `address_detail`, `dataset_installed` and the
rest), whose punctuation is already what Finnish uses; the single exception is
`city_label`, whose em dash becomes the en dash Finnish writes.

## Nothing comes back identical to the English

`tools/check_translations.py fi` reports no warning: every string that could be
translated was, including `settings_section_data` (*Offline-aineistot*) and
`about_licence_title` (*Lisenssi*), which several other languages legitimately
leave standing.
