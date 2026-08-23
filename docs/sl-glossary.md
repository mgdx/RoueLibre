# Slovene glossary

The terms `res/values-sl/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Slovene ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

Where a word is quoted from Android, the key it was found under is given.
Everything extracted from this phone's `framework-res.apk` and `Settings.apk`
sits in the campaign's `sl.tsv`, 5 900 entries; where no key is given, the
choice is this file's own and says so.

## Register and typography

The reader is addressed with **vikanje**, and buttons take the **bare
imperative**. That split is Android's own rather than a house style: of the
5 900 system strings, 174 carry a *vy* form — *Dotaknite se*, *Preverite*,
*Poskusite*, *Izberite*, *Vnesite* — and 95 carry a bare imperative, every one
of which is a button or a menu label (*Izberi datoteko*, *Namesti*, *Izklopi*,
*Nastavi datum*) and not one of which is a sentence. So this file writes
*Prekliči*, *Izbriši*, *Nadaljuj*, *Preskoči*, *Poskusi znova*, *Osveži* on
controls, and *Preverite črkovanje*, *Namestite kazalo*, *Izberite mesto* in
prose.

A confirmation dialog asks **„Želite … ?“** — *Želite izbrisati te podatke?* —
which is how Settings phrases the same question
(`settings:dev_logpersist_clear_warning_title`, "Clear logger persistent
storage?" → „Želite izbrisati trajno shranjevanje dnevniškega orodja?“).

Quotation marks are **„ … “**. An **ellipsis stands off its word by a space**:
90 of the 93 system strings that hold one write it that way (`android:loading`,
"Loading…" → „Nalaganje …“), three do not. Here that space is a **non-breaking**
one (U+00A0), which renders identically and cannot leave three dots alone at
the head of a line.

The dash that breaks a sentence is **–** with a space on either side, not the
em dash the English file uses; that is the one piece of punctuation changed
inside a format-only string, `city_label` (`%1$s – %2$s`). The apostrophe,
where a network's name carries one (*Vélib’*, *Vélo’v*, *V’lille*), is **’**
(U+2019) and never the straight quote, which is why nothing in the file is
escaped.

The English is **scrupulously impersonal** — "No history is kept", "It is read
from the feed" — and Slovene keeps it, with the reflexive passive: *Zgodovina
se ne shranjuje*, *Poti se izračunajo na tem telefonu*, *Podatek prihaja iz
vira samega omrežja*. Not one line says *ne shranjujemo*: an application whose
whole argument is that nobody is behind it must not ask the reader to trust a
"we".

## The dual, and what it cost

Slovene is the only one of the thirty languages in this campaign with a **real
dual**, and CLDR gives it four cardinal categories: `one` (1, 101, 201 …),
`two` (2, 102, 202 …), `few` (3, 4 and their compounds), `other` (0, 5–10 and
the rest). Two bikes are neither one bike nor three, and the file writes four
genuinely different words wherever the noun allows it:

| | one | two | few | other |
|---|---|---|---|---|
| `bikes_available`, `counterpart_bikes` | kolo | kolesi | kolesa | koles |
| `docks_available`, `counterpart_docks` | prosto mesto | prosti mesti | prosta mesta | prostih mest |
| `docks_total`, `station_detail_with_capacity` | stojalo | stojali | stojala | stojal |
| `city_detail`, `city_stations`, `city_detail_size_unknown` | postaja | postaji | postaje | postaj |
| `bikes_mechanical` | navadno | navadni | navadna | navadnih |
| `bikes_electric` | električno | električni | električna | električnih |

**The three `freshness_*` plurals are the exception, and they are the
exception on purpose.** Android writes an elapsed time as *pred 2 dnevoma*
(`settings:color_contrast_preview_email_send_date`), and *pred* governs the
instrumental. The instrumental **dual** is distinct — *pred 2 minutama* — but
the instrumental **plural** is one form for three as for five: *pred 3
minutami* and *pred 5 minutami*. So `few` and `other` read alike in
`freshness_seconds`, `freshness_minutes` and `freshness_hours`, and nowhere
else in the file. That is the language, not three lines left unfinished, and
it should not be "fixed" by inventing a difference Slovene does not make.

Building *pred* into the plural rather than into `freshness_fresh` is what
makes *„Posodobljeno pred 5 minutami“* and *„Posodobljeno pravkar“* both come
out right — `freshness_just_now` is „pravkar“ (`settings:time_unit_just_now`,
"Just now" → „Pravkar“), which no preposition could precede. Android inverts
that one, „Pravkar posodobljeno“ (`settings:no_carrier_update_now_text`), but
one template has to serve both cases and „Posodobljeno pred 5 minutami“ is the
one it is read in most often — which is also Android's order there
(`settings:no_carrier_update_text`, "Updated ^2 ago" → „Posodobljeno pred ^2“).

## Cases are suffixes, and a placeholder cannot carry one

Slovene declines, and the ending falls on the word itself. A sentence built
around `%1$s` has to stay right whatever arrives in it, and what arrives is
always a nominative: a station name, a street name, a network label, a size.
Six lines are written around that rather than against it:

| String | What it does | Why |
|---|---|---|
| `station_address_nearby` | `V bližini: %1$s` | *V bližini* governs the genitive, and the argument is a street **or** a square, as the index holds it. A colon turns the line into a label, which declines nothing. |
| `journey_step_to_station`, `journey_step_ride` | `Peš do postaje %1$s`, `S kolesom do postaje %1$s` | The case falls on *postaje*; the name follows in apposition, in the nominative, exactly as it arrived. |
| `city_delete_description`, `city_delete_body`, `city_deleted` | `Izbriši podatke: %1$s`, `… „%1$s“ …`, `Podatki izbrisani: %1$s` | **Not the apposition device, and here is why not.** All three are handed the **network's** `displayName`, not the city's: 328 of the 331 catalogue entries carry one of their own, and *Nomago Bikes* alone serves Ljubljana, Celje and Nova Gorica. Writing „podatki mesta %1$s“ would have made a Celje reader read „podatki mesta Nomago Bikes“ — a claim the English never makes, since it says only "Data **for** %1$s". The colon and the quotation marks hold the name at arm's length instead. |
| `dataset_imported`, `dataset_deleted` | `Nameščeno: %1$s`, `Izbrisano: %1$s` | A dataset's name is a masculine plural in two cases (*Podatki zemljevida*, *Podatki za izračun poti*) and a neuter singular in the third (*Kazalo naslovov*), so no participle agrees with all three. A label with a colon agrees with nothing. |
| `city_here_body`, `city_here_installed_body` | *podatke tega omrežja*, *njegove postaje* | The same problem in the other direction: they need a possessive for the network, whose gender the placeholder hides. *Omrežje* is neuter and fixed, so the sentence leans on that noun instead of on a pronoun that would have to agree with whatever `cityLabel` produced. |
| `map_outside_city_message`, `city_proposal_body` | *območja, ki ga pokriva %1$s* | The placeholder is the subject of *pokriva* and stays in the nominative, which is the case it arrives in. |

## The address prompt, and the number Slovenia will lose

`address_search_hint` is **„Ulica, številka, kraj“** — street, number, town,
which is the order Slovenia writes an address in: *Trubarjeva cesta 12,
Ljubljana*. `AddressQuery.parseQuery` has read a house number standing between
the street and the town since the pilot, precisely so that each language may
write this line in its own order.

**No postcode is invited.** Slovenia's is four digits, not the single group of
five the rule allows (SPEC §4.3), and a query holding a second number makes
the parser give the first one up. A prompt that asked for one would teach a
form the parser then refuses.

**The order a result is printed in is a separate matter, and is not this
file's to decide.** It belongs to the country the address is in, not to the
reader's language (SPEC §4.3), and it lives in
`core/.../address/AddressLayout.kt`.

> **Reported, and not fixed here:** that table holds no `sl` entry, so a
> Slovene address base falls on the English fallback and prints
> *„12 Trubarjeva cesta“* — which is neither the country's order nor anything
> a Slovene reader has seen on a wall. Slovenia closes with the number,
> *Trubarjeva cesta 12*, exactly as Poland and Germany do. The entry is one
> line and it is the supervisor's to write, not a translator's; the assertion
> in `AddressLayoutTest` that pins the current behaviour would move with it.

## The words

| English | Slovene | Why |
|---|---|---|
| journey | **pot** | The whole door-to-door thing — walk, ride, walk — and one word on all three screens that name it: `journey_title`, `settings_section_journey`, `journey_open`. |
| ride | **vožnja** | The bike leg alone, inside a journey. A different word from *pot*, or the two screens stop being about the same object: *Izračun vožnje …*, *Vožnja, navzgor in navzdol*. |
| route | **pot**, in `journey_no_route` alone | **A departure from the rule above, deliberate.** *Prevozna pot* is the line on the ground, not the journey that was planned over it, and the alternatives are worse: *trasa* is the alignment of a road or a railway and *povezava* reads as a public-transport connection. The sense is carried by *prevozen* — negotiable, passable — and by nothing else in the sentence. |
| station | **postaja** | A bike-share station. In `address_search_prompt_message` the English "stations" means **railway** stations, as its comment says, so that one line writes *železniške postaje* in full. |
| dock (free) | **prosto mesto** | What a bike is returned into, counted as available: `docks_available`, `counterpart_docks`, `mode_docks`. |
| dock (capacity) | **stojalo** | The same object counted as a total: `docks_total`, `station_detail_with_capacity`. English uses one word for both and the screen shows both figures side by side — *12 prostih mest · 30 stojal* — so Slovene needs two. Neither word is ever the payment terminal. |
| bike, mechanical | **navadno kolo** | *Navadno* — ordinary — is what Slovene bike-share says, against the network's electric ones. *Mehansko* is a calque of the English and says nothing a rider recognises. |
| bike, electric | **električno kolo** | Pedal-assist, and `journey_bike_kind_electric_description` says so: *Kolo s pomožnim električnim pogonom*. Never a moped. |
| bike sharing | **souporaba koles** | The established Slovene calque, as in *souporaba avtomobilov*. *Mestna kolesa* was rejected: it is what Ljubljana says of BicikeLJ, but the application serves 331 networks and several of them are not a city's. |
| pace (walking) | **tempo hoje** | A pace, never a speed, and never a figure in km/h — `SPEC` §7.6 and the comment above the string. The three values agree with the masculine *tempo*: **Počasen**, **Običajen**, **Hiter**. Android's own "Slow" is *Počasna* (`settings:speed_label_slow`), feminine, because it agrees with *hitrost* — the very word this screen refuses. The gender changes; the word does not. |
| Delete | **Izbriši** | `settings:delete`, `android:delete`. Destroys: a city's data, a dataset. |
| Remove | **Odstrani** | `settings:remove`, `android:kg_reordering_delete_drop_target_text`. Takes out of a list: *Odstrani iz priljubljenih*. Android keeps the two apart and so does this file. |
| Clear | **Počisti** | `settings:clear`, `settings:proxy_clear_text`. Empties a search field without destroying anything — a third verb, and the reason *Izbriši* is not stretched over it. |
| Out of service | **Ne deluje** | `settings:radioInfo_service_out`. |
| In use | **V uporabi** | `android:media_route_status_in_use`. |
| just now | **pravkar** | `settings:time_unit_just_now`. Lowercase here, since it lands inside *Posodobljeno %1$s*. |
| Try again | **Poskusi znova** | `settings:audio_streams_dialog_retry` — the button form. The sentences say *Poskusite znova* (`android:fingerprint_error_unable_to_process`). |
| Continue | **Nadaljuj** | `android:fp_power_button_bp_negative_button`. Settings also has *Naprej* for a wizard's next step; this is not one. |
| Skip | **Preskoči** | `android:skip_button_label`. |
| Settings | **Nastavitve** | `settings:settings_label`. |
| Display (settings section) | **Zaslon** | `settings:display_settings`. |
| Storage | **Shramba** | `settings:storage_label`. |
| Language | **Jezik** | `settings:app_locale_preference_title`. |
| Privacy | **Zasebnost** | `settings:privacy_dashboard_title`. |
| System | **Sistem** | `settings:header_category_system`. Theme, units and language all lean on it. |
| Cancel | **Prekliči** | `settings:cancel`. |
| Back | **Nazaj** | `android:back_button_label`. |
| Yes | **Da** | `settings:yes`. |
| Replace | **Zamenjaj** | `settings:vpn_replace`. |
| Tap | **Dotaknite se** | `android:usb_notification_message` and eleven more. *Tapnite* exists in Settings (`android:vpn_text`) but is the minority form. |
| unmetered / metered | **z neomejenim / omejenim prenosom podatkov** | `settings:wifi_unmetered_label`, `settings:wifi_metered_label`. Long, and it is Android's own wording for exactly this distinction; the setting names what is billed rather than Wi-Fi, as the English does. |
| offline (data, work) | **brez povezave** | No Android key: Settings never says it. *Podatki brez povezave*, *delo brez povezave*. The English loanword *offline* was rejected as looser than the thing meant, which is "with no network at all". |
| index (of addresses) | **kazalo** | No Android key. *Kazalo* is a book's index and is the ordinary Slovene word; *indeks* reads as a database term. |
| favourites | **priljubljene** | No Android key — Settings has no favourites. Feminine plural, agreeing with the *postaje* it always holds. |
| conurbation | **mestno območje** | Against *mesto* for "city": the catalogue's entries are metropolitan areas, and `city_intro` and `welcome_data_body` say so. |
| Update available | **Na voljo je posodobitev** | A **departure** from Android, which says *Na voljo je posodobljena različica* (a newer *version*) for a system update. Here what is available is an updated **dataset**, not a version of anything, so the noun changes. |
| Show | **Prikaži** | Android's `settings:display_category_title` gives the noun *Prikaz*, which this file uses for the two map descriptions (*Prikaz razpoložljivih koles*) and not for the buttons, which take the verb. |

## What is deliberately left in English

`app_name` and `welcome_hello_title` — *Roue Libre* is a name. The unit symbols
`m`, `km`, `ft`, `yd`, `mi`, `min`, `h`, and the byte multiples: Slovene writes
them as the SI does. The format-only strings — `%1$s · %2$s`,
`%1$d h %2$02d` — except `city_label`, whose em dash became an en dash.
