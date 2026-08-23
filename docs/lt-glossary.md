# Lithuanian glossary

The terms `res/values-lt/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Lithuanian ones over three screens.

An entry is not changed without going back over every one of its occurrences.

Where a row cites an Android key — `settings:storage_label`, `android:back_button_label`
— the wording was read out of the Lithuanian `framework-res.apk` and
`Settings.apk` of a real phone. Where no key is cited, the choice is this
project's own and the reason is written out.

## Register and typography

The register is the **polite plural** (jūs): "Palieskite", "Pasirinkite",
"Bandykite dar kartą". That is what Android speaks in Lithuanian
(`android:lockscreen_password_wrong` → *Bandykite dar kartą*), and an
interface that switched to *tu* would read as a different piece of software
from the phone it runs on.

Quotation marks are **„ … “**. The dash that breaks a sentence is **–**
(U+2013) with a space on either side, not the em dash the English file uses.
Diacritics are written in full: ą č ę ė į š ų ū ž. The apostrophe, where a
name carries one (Vélib’), is ’ (U+2019), which is why nothing in the file is
escaped.

Foreign product and brand names are set in „ “ — „OpenStreetMap“, „BRouter“,
„MapLibre Native“, „Roue Libre“, „Android“, „Google“ — which is Lithuanian
orthography and what Android itself does (`android:android_system_label` →
*„Android“ sistema*). **Wi-Fi is quoted with them** whenever it stands inside a sentence, which is
every occurrence in this file: `android:wifi_available_sign_in` →
*Prisijungti prie „Wi-Fi“ tinklo*, `settings:accessibility_wifi_off` →
*„Wi-Fi“ išjungtas*, `android:wfc_mode_wifi_only_summary` → *Tik „Wi-Fi“*.
The bare `settings:wifi` → *Wi-Fi* that an earlier draft of this file leaned
on is a **label standing on its own**, and it does not license a bare Wi-Fi in
running prose. `app_name` and `welcome_hello_title` are the application naming
itself and stay unquoted.

Lithuanian declines over seven cases, all suffixes. No case ending is ever
glued onto a placeholder: the sentence is turned around so the placeholder
sits in the nominative (`city_here_body`: "Teritoriją, kurioje esate,
aptarnauja %1$s."), or the placeholder is set in „ “ in apposition to a noun
that carries the case instead (`journey_step_ride`: "Važiuoti iki stotelės
„%1$s“"), or a colon takes the strain (`dataset_imported`: "Įdiegta: %1$s").

## Plurals

Lithuanian has four CLDR categories and they do not mean what their names
suggest:

| Category | Numbers it covers | Case written |
|---|---|---|
| `one` | 1, 21, 31, 101… — **not 11** | nominative singular |
| `few` | 2–9, 22–29, 32–39… — **not 12–19** | nominative plural |
| `many` | decimals only (1,5) | genitive singular |
| `other` | everything else: 0, 10, 11–19, 20, 30, 100… | genitive plural |

`freshness_seconds` and its two siblings sit after **prieš**, which takes the
accusative — *prieš 1 sekundę*, *prieš 2 sekundes* — except that a numeral of
ten or more takes the genitive plural after it, *prieš 12 sekundžių*, which is
exactly what `other` covers. So those two categories are not the same word
twice.

In five plurals `few` and `many` are genuinely identical, because the noun has
one form for both the nominative plural and the genitive singular: *vieta* in
`docks_available` and `counterpart_docks`, and *stotelė* in `city_detail`,
`city_stations` and `city_detail_size_unknown`. **All five are the language,
not a copied line** — do not "correct" them.

## The vocabulary

| English | Lithuanian | Why |
|---|---|---|
| journey | kelionė | The whole door-to-door thing: the screen, the settings section, the button. One word throughout. |
| ride | važiavimas / važiuoti | The bike leg alone, inside a journey. Distinct from *kelionė*, so the two screens stay about the same object. |
| route | kelias | Only in `journey_no_route`: the line on the ground. *Maršrutas* is kept for the routing data and the computed path, so *kelias* is left free for this. |
| station (bike-share) | stotelė | What the Vilnius network — Cyclocity, the one Lithuanian network served — calls its stands. |
| station (railway) | stotis | `address_search_prompt_message` means railway and coach stations by "stations" and says so in its comment. The two words are never mixed. |
| dock (free) | laisva vieta | What a bike is returned into, counted as available. Also the map's second counting mode, `mode_docks`. |
| dock (capacity) | stovas | The same object counted as a total: "12 laisvų vietų · 30 stovų". English uses one word for both and the screen shows both figures side by side, so Lithuanian uses two. |
| dock | *never* „terminalas“ | The payment terminal is not the point a bike attaches to. |
| bike, mechanical | mechaninis | **Not *įprastas***: Android already spends that word on "Standard" (`settings:external_display_standard_rotation` → *Įprastas*), and a bike labelled *įprastas* beside a settings screen full of *įprastas* would read as "default". |
| bike, electric | elektrinis | Pedal-assist, not a moped, which is what `journey_bike_kind_electric_description` spells out: "Dviratis, kurio variklis padeda minti". |
| bike sharing | dviračių dalijimasis | And *dalijimosi dviračiai* for the bikes themselves — one root, so the store text and the welcome screen say the same thing. |
| lend (a network lends bikes) | siūlyti | English says "lends"; a Lithuanian bike-share **rents**, and *nuomoti* would drag a price into a sentence that is only about which kinds exist. *Siūlyti* states the fact without the transaction. |
| availability | prieinamumas | `station_availability_unknown`, `about_attribution_gbfs`, `error_offline`. |
| pace (walking) | tempas | A pace is not a speed — `values/strings.xml` says so above the string — so never *greitis*. The three values are **Lėtas / Vidutinis / Spartus**: *Vidutinis* rather than *Įprastas* for the same reason as "mechanical" above, and *Spartus* rather than *Greitas* because "brisk" is not "fast". |
| Delete | Ištrinti | `android:delete`. Destroys: a city's data, a dataset. |
| Remove | Pašalinti | `settings:remove`. Takes out of a list: `station_favourite_remove`. Two words, never one. |
| Clear | Išvalyti | `settings:clear`. Emptying a search field. |
| Settings | Nustatymai | `settings:settings_label`. |
| Display (settings section) | Ekranas | `settings:display_settings`. |
| Storage | Saugykla | `settings:storage_label`. |
| Search | Ieškoti / Paieška | `android:searchview_description_search` for the action, `settings:m3c_search_bar_search` for the noun. |
| Refresh / Update | Atnaujinti | `android:autofill_update_yes`. And "Update available" is *Pasiekiamas naujinys*, `settings:android_version_pending_update_summary`. |
| Back | Atgal | `android:back_button_label`. |
| Tap | Palieskite | `android:usb_notification_message` and a dozen others. |
| Press and hold | Palieskite ir laikykite | `android:content_description_sliding_handle`. |
| Continue | Tęsti | `settings:lockpattern_continue_button_text`. |
| Skip | Praleisti | `android:skip_button_label`. |
| Show | Rodyti | `settings:condition_expand_show`. |
| Cancel | Atšaukti | `android:cancel`. |
| Try again | Bandyti dar kartą | `settings:retry`. |
| In use | Naudojama | `android:media_route_status_in_use`. |
| Out of service | Paslaugos neteikiamos | `settings:radioInfo_service_out`. `settings_map_filters_hide_out_of_service` repeats it in full — "Slėpti stoteles, kuriose paslaugos neteikiamos" — rather than inventing a shorter *neveikiančios*, so the filter and the badge name one state. |
| Replace | Pakeisti | `settings:vpn_replace`. |
| Language | Kalba | `settings:app_locale_preference_title`. |
| Open | Atidaryti | `android:whichViewApplicationLabel`, `settings:credman_button_open`, `settings:launch_instant_app`. *Atverti* is correct Lithuanian and lives in other software, but it appears **nowhere** in the phone's 5 862 lines — Android says *atidaryti*, twenty-three times. So do `sources_open`, `journey_navigate`, `station_open_in_navigation`, `settings_opening_title` and the About screen's own `about_links_title`, which sat three lines from an `about_links_body` already saying *Atidaryti*. The store changelogs follow the same rule — *atsidaro*, not *atsiveria* — since changelog 3 narrates `settings_opening_title` word for word. |
| Open by default | Atidaryti pagal numatytuosius nustatymus | `settings:auto_launch_label`. `about_links_body` walks the reader down a real Android path, so every link of it is quoted from the phone and not translated afresh — *Atverti* appears nowhere in Settings, and the reader following the chain would stop at the one label that is not on screen. Note that this is Android's **link** setting, which is why `settings_opening_title` — this application's own opening-screen setting — is worded differently on purpose. |
| Privacy | Privatumas | `settings:privacy_dashboard_title`. |
| Licence | Licencija | `settings:license_title`. |
| just now | ką tik | `settings:time_unit_just_now`, lower-cased because it lands inside "Atnaujinta ką tik". |
| Searching… | Ieškoma… | `settings:wifi_p2p_menu_searching`. |
| System (on a ToggleRow) | Sistemos | Genitive: *the system's* theme, *the system's* units. Android's noun is *Sistema* (`settings:header_category_system`), but a button standing beside *Šviesi* and *Tamsi* is naming a possessor, not a category. **The exception stops at the two `ToggleRow`s**, `settings_theme_system` and `settings_units_system`, where the neighbouring buttons supply the noun. |
| System (heading the language list) | Sistemos kalba | `settings:preference_of_system_locale_title` — Android's own wording for this very row. `settings_language_system` is not a `ToggleRow`: it heads a list of endonyms (*English*, *Français*, *Lietuvių*), where a bare genitive would have nothing to be the genitive of. Android's other rendering, *Numatytoji sistemos vertė* (`android:system_locale_title`), says the same thing more heavily. |
| offline data | duomenys įrenginyje | *Neprisijungus* is Android's word for offline, but it is an adverb: *duomenys neprisijungus* reads "data while offline", and the participle that repairs it — *neprisijungus naudojami duomenys* — is 32 characters against English's 12, on a settings section title. What these datasets are is data held on the device, so that is what they are called. The adverb is kept where it genuinely means "while offline": `dataset_delete_body`, "Norint dirbti neprisijungus". |
| unmetered / metered | neapmokestinamas / apmokestinamas | **A departure from Android**, which says *Nematuojamas* / *Matuojamas* (`settings:wifitrackerlib_wifi_unmetered_label`, `settings:wifi_metered_label`) — literally un/measured. The English string deliberately names **what is billed** rather than Wi-Fi, and `download_unmetered_only_description` spends a whole sentence on the billing; *nematuojamas* would name the measuring instead and lose the point. Android's own second rendering, `settings:wifi_unmetered_label` → *Neišmatuotas*, shows the term is not settled there either. |
| Favourites | Mėgstamiausios stotelės | **The noun is carried, not dropped.** English's bare "Favourites" is a noun; Lithuanian's *mėgstamiausios* is an adjective, and a substantivised Lithuanian adjective takes the pronominal (definite) form — *mėgstamiausiosios* — or keeps its noun. A bare *Mėgstamiausios* heads a screen while naming nothing, and quietly commits the whole screen to the feminine of a *stotelė* it never writes; *Žiūrėti mėgstamiausias* is half a phrase. A draft of this file shortened both and was wrong to. If length ever forces the issue, the correct short form is the generic masculine **Mėgstamiausi**, never the bare feminine. |
| map data / tiles | žemėlapio duomenys | The name the storage screen gives the dataset, and the one every other string uses for it. |
| routing data | maršrutų duomenys | And *maršrutų grafas* in the store text, which is the project's own more technical term, as in French. |
| address index | adresų rodyklė | *Rodyklė* is an index, not a pointer, here. |
| repository (the project's) | projektas | **A departure from the obvious.** A git repository is *saugykla* in Lithuanian — but `storage_title` already spends *saugykla* on the storage screen, and a store text sending the reader to "saugyklos scenarijai" would point at the wrong screen of the very application it is describing. The store texts say *projekto scenarijai* instead. |
| source code | pirminis kodas | Distinct from *atvirasis kodas*, which is the licensing model and is what `welcome_hello_body` says. |
| bytes | B, kB, MB, GB | The Lithuanian unit is the *baitas*; its symbols are the international ones. |
| hour and minute (in a duration) | val. / min. | The only unit symbols this file rewrites. Lithuanian writes *val.* for the hour and *min.* **with its point** (`android:minute` → *min.*, `settings:accessibility_timeout_1min` → *1 min.*), and it spells the minutes out rather than zero-padding them behind the hour: *1 val. 5 min.*, not the *1 h 05* of the French source. Polish took the same view. *m*, *km*, *ft*, *yd* and *mi* are unchanged. |

## The impersonal voice

The English privacy texts are scrupulously impersonal — "No history is kept",
"It is read from the feed" — and Lithuanian keeps them so, with passive
participles and reflexive verbs: *Istorija nesaugoma*, *Kelionės
apskaičiuojamos šiame telefone*, *Tai perskaitoma iš paties tinklo duomenų
srauto*. There is no *mes* anywhere in the file. In an application whose whole
argument is that nobody is behind it, a "we do not keep" would ask the reader
to trust a party instead of stating a property of the software.

## The layout of an address is not this file's business

`AddressLayout.kt` lays an address out the way its own country writes it,
keyed by the language of the address base (SPEC §4.3). Lithuanian decides the
words **around** an address and nothing about the address itself.

**As of this commit that table carries no `"lt"` entry**, so a Lithuanian
address falls back to the default layout rather than to "Gedimino pr. 9". The
entry is one line and it is not this file's to write: it belongs to whoever
merges the language. Until it lands, the sentence above describes what
`AddressLayout.kt` does for other countries, not yet what it does for
Lithuania.

`address_search_hint` is the one place where the order of a Lithuanian address
shows: **"Gatvė, numeris, miestas"**, because Lithuania writes "Gedimino pr. 9,
Vilnius" — the street first and the house number after it, which is the third
position `AddressQuery.parseQuery` reads a number in. The postcode is left out
on purpose: Lithuania writes it "LT-01103", not as a bare group of five
digits, and inviting it would put a second number in the query, which makes
the parser give the house number up.

## Words that are not translated

Product and network names — Roue Libre, Cyclocity, Vélib’, Citi Bike, BRouter,
MapLibre, OpenStreetMap, GBFS, Wi-Fi — and the licence names. `resources`
`name` attributes, always.
