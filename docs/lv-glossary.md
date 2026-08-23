# Latvian glossary

The terms `res/values-lv/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Latvian ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

Where a word is quoted from Android, the key it was found under is given.
Everything extracted from this phone's `framework-res.apk` and `Settings.apk`
sits in the campaign's `lv.tsv`, 5 858 entries; where no key is given, the
choice is this file's own and says so.

Latvian covers one network served: **nextbike LV**, in Riga. That matters more
than the count suggests — see *The name in the placeholder is the network's*
below.

## Register and typography

The register is **Android's own**, and it is a split by context rather than a
house style. A button and a menu entry take the **infinitive**: *Atcelt*
(`android:cancel`), *Dzēst* (`android:delete`), *Turpināt*
(`android:autofill_continue_yes`), *Izlaist* (`android:skip_button_label`),
*Rādīt* (`settings:condition_expand_show`), *Mēģināt vēlreiz*
(`android:lockscreen_password_wrong`), *Aizstāt* (`settings:vpn_replace`),
*Atpakaļ* (`android:back_button_label`). The body of a dialog and an error
message take the **polite plural imperative**: *Pieskarieties*
(`android:usb_notification_message`, "Tap for more options." →
„Pieskarieties, lai skatītu citas opcijas.“), *Izvēlieties*
(`settings:choose_sim_title`), *Instalējiet*, *Pārbaudiet*, *Atbrīvojiet*
(`settings:storage_free_up_space_title`, "Free up space" → „Atbrīvojiet
vietu“). The file follows that split line by line.

A confirmation dialog asks **„Vai …?“** — *Vai dzēst šos datus?* — which is
how Settings phrases the same question (`settings:erase_sim_dialog_title`,
"Erase this eSIM?" → „Vai dzēst šo eSIM karti?“).

Quotation marks are **„ … “**. The apostrophe a network's name carries —
*Vélib’*, *Vélo’v*, *V’lille* — is **’** (U+2019) and never the straight
quote, which is why nothing in the file is escaped. The dash that breaks a
sentence is **—** with a space on either side, as the English file writes it.

The English is **scrupulously impersonal** — "No history is kept", "It is read
from the feed" — and Latvian keeps it, with the passive: *Vēsture netiek
saglabāta*, *Maršruti tiek aprēķināti šajā tālrunī*, *Tas tiek nolasīts no
paša tīkla datiem*. Not one line says *mēs nesaglabājam*: an application whose
whole argument is that nobody is behind it must not ask the reader to trust a
"we".

## The `zero` category, and what it cost

Latvian's three CLDR cardinal categories are three genuinely different forms,
and the trap is that `zero` is not the plural written twice:

| | `one` | `zero` | `other` |
|---|---|---|---|
| covers | 1, 21, 31, 41 … | 0, **10, 11–19**, 20, 30 … | 2–9, 22–29 … |
| case | nominative singular | **genitive plural** | nominative plural |

`zero` reaching **11 to 19** is the half of the rule that is easy to miss: it
is not only "everything ending in 0". A station showing 14 bikes reads
*14 velosipēdu*, not *14 velosipēdi*.

| | `one` | `zero` | `other` |
|---|---|---|---|
| `bikes_available`, `counterpart_bikes` | velosipēds | velosipēdu | velosipēdi |
| `docks_available`, `counterpart_docks` | brīva vieta | brīvu vietu | brīvas vietas |
| `docks_total`, `station_detail_with_capacity` | statīvs | statīvu | statīvi |
| `city_detail`, `city_stations`, `city_detail_size_unknown` | stacija | staciju | stacijas |
| `bikes_mechanical` | parastais | parasto | parastie |
| `bikes_electric` | elektriskais | elektrisko | elektriskie |

`bikes_mechanical` and `bikes_electric` elide their noun — the English writes
"%1$d mechanical" — so the adjective stands substantively, which in Latvian
takes the **definite** ending: *3 parastie · 2 elektriskie*. With `one` that
is *1 parastais*, and the genitive plural of a definite adjective is *parasto*.

**The three `freshness_*` plurals are the exception, and they are the
exception on purpose.** The preposition *pirms* is built into the plural
rather than into `freshness_fresh`, so that one template yields both
„Atjaunināts pirms 5 minūtēm“ and „Atjaunināts tikko“ — `freshness_just_now`
is *tikko* (`settings:time_unit_just_now`, "Just now" → „Tikko“), which no
preposition could precede. *Pirms* governs the **genitive singular** after
`one` (*pirms 1 minūtes*, *pirms 21 minūtes*) and the **dative plural**
everywhere else — and the dative plural is one form for 5 as for 20, so
`zero` and `other` read alike here and **nowhere else in the file**. That is
the language, not three lines left unfinished, and it should not be "fixed" by
inventing a difference Latvian does not make.

## Cases are suffixes, and a placeholder cannot carry one

Latvian declines, and the ending falls on the word itself. What arrives in a
placeholder is always a nominative — a station name, a network label, a size,
a dataset name — so several lines are written around that rather than against
it:

| String | What it does | Why |
|---|---|---|
| `journey_step_to_station`, `journey_step_ride` | `Ar kājām līdz stacijai %1$s`, `Ar velosipēdu līdz stacijai %1$s` | *Līdz* governs the dative, and the dative falls on *stacijai*; the name follows in apposition, in the nominative it arrived in. |
| `station_address_nearby` | `Netālu: %1$s` | *Netālu no* governs the genitive, and what arrives is a street **or** a square, as the index holds it. The colon turns the line into a label, which declines nothing. |
| `city_installed` | `Ierīcē instalēts: %1$s` | The argument is a size, and a quantified phrase carries its own agreement: no participle written in front of *%1$s* is right for *42 MB*, *1,3 GB* and *900 B* alike. The bare masculine participle before a colon is the attested label form (`settings:credential_for_vpn_and_apps`). |
| `storage_total` | `Šajā ierīcē izmantotā krātuve: %1$s` | Same shape, different verb. *Aizņemt* appears nowhere in `lv.tsv`: Latvian Android says *izmantot* — `settings:storage_used`, "Storage used" → „Izmantotā krātuve“ — and `settings:data_used_formatted`, "Data used: ^1 ^2" → „Patērētie dati: ^1 ^2“, which is this very colon form with a noun phrase in front of it. So the line names the thing measured rather than leaning on a participle alone. |
| `dataset_delete_description`, `storage_download_pending` | `Dzēst: %1$s`, `Lejupielādēt: %1$s` | A dataset's name is a masculine **plural** in two cases (*Kartes dati*, *Maršrutēšanas dati*) and a masculine **singular** in the third (*Adrešu rādītājs*), so no verb agreeing with it is right all three times. The infinitive behind a colon agrees with nothing. |
| `dataset_absent` | `Nav ierīcē` | The same three names, and the state line sits **directly under** the one it describes, where a reader feels the agreement whether or not the grammar demands it. *Nav instalēts* would be right for *Adrešu rādītājs* and wrong for the two plurals; *Nav ierīcē* declines nothing and says the same thing. |
| `dataset_imported`, `dataset_deleted` | `Datu kopa instalēta: %1$s`, `Datu kopa izdzēsta: %1$s` | The colon alone was not enough here: a participle standing at the head of the line still reads as agreeing with the name that follows it. So the line supplies its own subject — *datu kopa*, feminine, which is already this file's word for a dataset in `storage_intro` — and the participle agrees with that, whatever arrives after the colon. |
| `map_outside_city_message`, `map_outside_city_brief`, `city_proposal_body` | *apgabala, ko aptver %1$s* | The placeholder is the subject of *aptver* and stays in the nominative, which is the case it arrives in. |
| `city_here_body`, `city_here_installed_body` | *Instalējiet šī tīkla datus … tā stacijas* | The same problem in the other direction: they need a possessive for the network, whose gender the placeholder hides. *Tīkls* is masculine and fixed, so the sentence leans on that noun rather than on a pronoun that would have to agree with whatever `cityLabel` produced. |
| `settings_opening_title` | `Lietotnes sākuma ekrāns` | **The one place the English sentence had to be recast.** "Open the app by default on" governs its two buttons, and Latvian would have had to decline them — *Karti*, *Staciju sarakstu* — which reads as nonsense on a toggle. Naming the screen instead lets the buttons stay in the nominative: *Karte*, *Staciju saraksts*. |

## Where Latvian departs from the English note

`counterpart_bikes` and `counterpart_docks` carry an instruction in the source
file that this translation does not follow, and the departure is deliberate.
The English asks that these be treated as **labels** rather than as the tail of
the phrase the count makes — Romanian found that a grammar reaching across the
gap between the figure and the word breaks on a list row, where the two are
stacked and the label is set in capitals.

Latvian marks the case on the noun itself and adds nothing at the head of the
line, so the **agreed form is the label**: „14“ over VELOSIPĒDU reads right,
and a nominative plural over the same figure would be plain wrong. The
capitals and the line break cost nothing here. Keeping the forms agreed is
therefore the same decision the English note was protecting, arrived at from
the other side.

**One case this leaves crooked, and it is written down rather than hidden.**
`StationAdapter.kt:167` resolves the category with `counterpart ?: 0`, so a
station whose availability the network does not publish shows „—“ over BRĪVU
VIETU: a genitive plural with no figure to govern. Of the three forms it is
the least bad — `one` would assert a single dock and `other` a handful — but
it is a real seam, and a contributor who finds it should know it was seen.

## The name in the placeholder is the network's

`city_delete_description`, `city_delete_body` and `city_deleted` are handed the
network's `displayName`, **not** the city's: 328 of the 331 entries in
`config/catalogue.json` carry one of their own, and the entry Latvian is
translated for is called **nextbike LV**, not *Rīga*. Writing „pilsētas %1$s
dati“ would have made a Riga reader read „pilsētas nextbike LV dati“ — a claim
the English never makes, since it says only "Data **for** %1$s". So the three
lines use the colon and the quotation marks instead: *Dzēst datus: %1$s*,
*Visi bezsaistes dati „%1$s“ …*, *Dati izdzēsti: %1$s*.

## The address prompt, and the number Latvia keeps

`address_search_hint` is **„Iela, numurs, pilsēta“** — street, number, town,
which is the order Latvia writes an address in: *Brīvības iela 12, Rīga*.
`AddressQuery.parseQuery` reads a house number standing between the street and
the town, which is what lets this line be written in that order.

The guard on that reading gives the number up when a **stop word** stands
beside it. Latvian's list is `config/address-normalization/lv.json`, and it
holds one word, *un*; *iela* is not on it, so *Brīvības iela 12 Rīga* parses.

**No postcode is invited.** Latvia writes hers *LV-1050*, not the single bare
group of five digits the rule allows (SPEC §4.3), and a query holding a second
number makes the parser give the first one up.

## The words, and why each one

| English | Latvian | Why |
|---|---|---|
| journey | **maršruts** | The whole door-to-door thing, walk and ride together. *Brauciens* could not serve: it means travel **by vehicle**, and two thirds of a journey here is on foot. *Maršruts* is neutral about the mode, which is exactly what the word has to be. One word throughout: the screen, the settings section, the button, the summaries. |
| ride | **brauciens** | The bike leg alone — `journey_computing_own_bike`, `journey_detail_profile`, `journey_detail_profile_description`. A different word from *maršruts*, as the English keeps *ride* different from *journey*. |
| route | **ceļš** | Only in `journey_no_route`: *Starp šiem diviem punktiem nav izbraucama ceļa* — the line on the ground, not the planned journey. Kept apart from *maršruts* on purpose. |
| routing (data) | **maršrutēšana** | `dataset_routing`, `journey_graph_missing`, `about_attribution_brouter`. The technical noun, and no reader confuses *maršrutēšanas dati* with a *maršruts*. |
| station | **stacija** | The bike-share station, everywhere. In `address_search_prompt_message` alone the English *stations* means **railway** ones (its comment says so), and there the line writes *dzelzceļa stacijas* in full so the two cannot be confused on the one screen that names both. |
| dock, free | **brīva vieta** | What a bike is returned into, counted as available: `docks_available`, `counterpart_docks`, `mode_docks`, `journey_no_dock_nearby`. |
| dock, capacity | **statīvs** | The same object counted as a total: `docks_total`, `station_detail_with_capacity`. English uses one word for both and the station sheet shows the two figures side by side — *12 brīvas vietas · 30 statīvi* — so they cannot share a word. *Statīvs* is the ordinary Latvian for a bike stand; it is never the word for the payment terminal. My own choice: Android has neither sense. |
| bike | **velosipēds** | Never *ritenis*, which is colloquial and also means "wheel". |
| mechanical / electric | **parasts / elektrisks** | The two kinds a network lends. *Parasts* — "ordinary" — rather than *mehānisks*, which in Latvian describes a mechanism rather than a bike without a motor. *Elektrisks* here is pedal-assist, which `journey_bike_kind_electric_description` says outright: *Velosipēds ar minēšanas atbalstu*. |
| walking pace | **gaitas temps** | Not a speed, and not in km/h: `settings_walking_pace_description` says *temps*, never *ātrums*. |
| slow / normal / brisk | **Lēns / Normāls / Raits** | *Lēns* is Android's (`settings:speed_label_slow`, "Slow" → „Lēns“). *Raits* is **my own choice, and a departure from the obvious**: Android's word for "Fast" is *Ātrs* (`settings:speed_label_fast`), but a pace that is *ātrs* is a speed, which is the one thing this setting refuses to be. *Raits solis* is the ordinary Latvian for a brisk walk and every speaker has it. |
| pace of a journey | — | `settings_walking_pace_title` is *Gaitas temps* and the changelog says *lēni, normāli vai raiti*: the three adverbs match the three buttons word for word, so a reader who set one recognises the other. |
| favourites | **izlase** | `favourites_title`, and *Pievienot izlasei* / *Noņemt no izlases*. My own choice: Android has no "Favourites" row. *Izlase* is what Latvian software has settled on; *grāmatzīmes* is a bookmark, which a station is not. |
| Delete / Remove | **dzēst / noņemt** | Two words, as Android has two: *Dzēst* destroys (`android:delete`, `settings:dlg_delete`) and is what `city_delete*`, `dataset_delete*` say; *Noņemt* takes out of a list (`settings:remove`, `android:kg_reordering_delete_drop_target_text`) and is what `station_favourite_remove` says. |
| address index | **adrešu rādītājs** | *Rādītājs* is the index of a book, which is what this is. *Indekss* was refused: in Latvian *pasta indekss* is the postcode, and an "address index" called *adrešu indekss* would be read as a list of postcodes on the very screen that searches street names. |
| conurbation | **aglomerācija** | `city_intro`, `map_needs_city_message`, `welcome_data_body`. Distinct from *pilsēta*, which the English also uses and which the file keeps for "city". |
| network | **tīkls** | The bike-share network, in `welcome_fleet_*`, the `error_*` family and `city_here_*`. The same word means a data network, and the error strings say *tīkla serveris* where the English says "the network's server" — the context is a bike feed on every one of them. |
| metered / unmetered | **maksas / bezmaksas** | Android's own (`settings:wifi_metered_label`, `settings:wifi_unmetered_label`). The setting names what is billed rather than Wi-Fi, as the English does, and `download_unmetered_only_description` spells out that a shared phone connection counts as mobile data. |
| theme | **motīvs** | `settings:dark_ui_mode`, "Dark theme" → „Tumšais motīvs“. |
| System | **Sistēmas** | Genitive, in all three places it heads a list — theme, units, language — where it is elliptical for *sistēmas motīvs / mērvienības / valoda*. Android's bare *Sistēma* (`settings:header_category_system`) is a section heading and reads as a noun, not as a choice. |
| Storage | **Krātuve** | `settings:storage_label`. |
| Display | **Attēlojums** | `settings:display_category_title`. Not *Displejs*, which is the physical screen. |
| Privacy | **Konfidencialitāte** | `settings:privacy_dashboard_title`. |
| Refresh | **Atsvaidzināt** | `settings:auto_sync_account_summary`, "Let apps refresh data automatically" → „Ļaut lietotnēm automātiski atsvaidzināt datus“. Android has no bare "Refresh" row; the verb is taken from the sentence that holds it. |
| Out of service | **Nedarbojas** | `settings:radioInfo_service_out`. |
| In use | **Tiek lietota** | `settings:wifi_display_status_in_use`. Feminine: `city_active` badges a **pilsēta**. |
| Licence | **Licence** | `settings:license_title`. Identical to the English, and right — one of the two lines the validator asks a reader to confirm, with `about_licences_title` („Licences“). |

## What is not translated

`app_name` and `welcome_hello_title` — *Roue Libre* is a name. The unit symbols
*m*, *km*, *ft*, *yd*, *mi*, *min*, *h*, and the size symbols *B*, *kB*, *MB*,
*GB*: Latvian writes them as everyone writes them. The format-only strings —
`%1$s · %2$s`, `%1$d h %2$02d`, `city_label` — where only the punctuation could
have changed and none of it needed to.
