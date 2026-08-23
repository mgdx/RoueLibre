# Bosnian glossary

The terms `res/values-bs/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Bosnian ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

The lexicon quoted throughout is the 5 900 system strings extracted from a real
phone's `framework-res.apk` and `Settings.apk`
(`do-not-commit/lexicon/bs.tsv`). Every key named below was grepped from it;
where the file departs from Android, or where the lexicon holds no word at all,
that is said in as many words.

## It is Bosnian, not Croatian and not Serbian

The three are translated separately, in `values-bs/`, `values-hr/` and
`values-sr/`, and this file is the Bosnian one throughout rather than a text
that tries to serve all three. Where the standards part, it follows Bosnian —
and, wherever the lexicon has an entry, Bosnian **as the phone writes it**:

*sistem* and not *sustav* (`android:default_audio_route_category_name`),
*ekran* (`settings:power_screen`), *Otkaži* and not *Odustani*
(`android:cancel`), *Nazad* and not *Natrag*
(`android:accessibility_system_action_back_label`), *Obriši*
(`settings:clear`), *fajl* and *fajlovi* rather than *datoteka*
(`android:mime_type_generic`, `android:permgrouplab_storage`), *meni* rather
than *izbornik* (`settings:dropdown_menu` = *Padajući meni*,
`settings:power_menu_summary_long_press_for_power_menu` = *Pristupite meniju
napajanja*), *Lista* rather than *popis*
(`android:chooser_all_apps_button_label`), *pristupna tačka* and so *tačka*
rather than *točka* (`settings:wifi_tether_configure_ssid_default`), *lični*
rather than *osobni* (`android:miniresolver_use_personal_browser`).

Where the lexicon is silent the file follows written Bosnian all the same:
*šta* rather than *što*, *ko* rather than *tko*, *da li* rather than *je li*,
*indeks* rather than *kazalo*, *interfejs* rather than *sučelje*, *historija*
rather than *povijest*, *saradnik* rather than *suradnik*, *dobija* rather than
*dobiva*, *brojali* rather than *brojili*, *zavisi od* rather than *ovisi o*.
The future is written apart — *bit će izbrisani*, *morat ćete* — as Bosnian
orthography has it, and not *biće* / *moraćete*.

## Register and typography

The reader is addressed with **persiranje**, the polite plural. That is not a
house style but Android's own: the lexicon's Bosnian says *Odaberite račun*
(`android:choose_account_label`), *Odaberite aplikaciju*
(`android:activitychooserview_choose_application`), *Potvrdite uzorak*
(`settings:lockpassword_confirm_your_pattern_header`), *Postavite preference za
jedinice i brojeve* (`settings:regional_preferences_summary`), and never the
singular in a sentence.

**Buttons take the second person singular imperative**, which is Android's
habit for a control rather than a sentence: *Nastavi*
(`android:autofill_continue_yes`), *Otkaži* (`android:cancel`), *Izbriši*
(`android:deleteText`), *Preskoči* (`android:skip_button_label`), *Prikaži*
(`settings:condition_expand_show`), *Zamijeni* (`settings:vpn_replace`),
*Instaliraj* (`settings:install_text`), *Ažuriraj*
(`android:autofill_update_yes`), *Ukloni*
(`android:kg_reordering_delete_drop_target_text`).

**`action_retry` is *Pokušaj ponovo*, in the singular**, and this is where
Bosnian parts company with the Croatian file, which had to write the plural.
Android's Bosnian button for exactly this is singular — `settings:retry` =
*Pokušaj ponovo* — so the button rule and the system agree here and there is
nothing to arbitrate. The sentences that end on the same idea keep the polite
plural, because they are sentences: *pa pokušajte ponovo*
(`map_location_unavailable`, `journey_no_stations`,
`dataset_rejected_transfer`), which is also what
`android:lockscreen_password_wrong` writes.

**The preposition is *s*, not *sa*.** This is not a Bosnian marker but an
orthographic rule the three standards share: *sa* stands only before a word
beginning with *s*, *š*, *z* or *ž*, before *mnom*, and before an abbreviation
read out letter by letter. The lexicon bears it out — 60 bare *s* against 15
*sa*, and every one of those fifteen is before a sibilant or a spelled-out
abbreviation (*sa slušnim*, *sa serverom*, *sa SIM*, *sa SD*). So this file
writes *s ekrana za pohranu*, *s biciklom*, *s brojem*, *s liste*, *s telefona*,
*Idemo s tim?* — and keeps *sa* in the two places the rule asks for it,
`journey_no_dock_nearby` (*sa slobodnim mjestom*) and the store text's *sa
svakom GBFS mrežom*. The first draft of this file wrote *sa* in all thirteen
places and contradicted itself between a string and a changelog; if a `sa`
turns up in front of anything but a sibilant, it is a mistake.

**Wi-Fi is spelled *WiFi* and declines with a hyphen**, which is what the
lexicon does throughout: *Povežite se s WiFi-jem prije potpunog brisanja*
(`settings:wifi_warning_dialog_title`), *putem WiFi-ja*
(`android:wfc_mode_wifi_preferred_summary`), *upravlja WiFi-jem*
(`settings:change_wifi_state_app_detail_switch`). The two `download_*` bodies
therefore say *Povežite se s WiFi-jem*, not *na Wi-Fi*: the system governs this
phrase with *s* and the instrumental, and the reader has met it there.

Quotation marks are **„ … “**. Diacritics are written in full — **č ć ž š đ** —
and never approximated by their bare letters. The dash that breaks a sentence
is **–** with a space on either side, not the em dash the English file uses;
that is the one piece of punctuation changed inside a format-only string,
`city_label` (`%1$s – %2$s`). The only em dash left is `counterpart_none`,
which is a glyph standing in for an absent figure rather than punctuation. The
apostrophe, where a network's name carries one (*Vélib’*, *V’lille*, *Vélo’v*),
is **’** (U+2019) and never the straight quote, which is why nothing in the
file is escaped.

## Plurals: what each category covers, not its smallest number

Bosnian has three, and each has to read correctly for **every** number that
falls into it:

| Category | Numbers | Form |
|---|---|---|
| `one` | 1, 21, 31, 101… (anything ending in 1 except 11) | *1 bicikl*, *21 bicikl* |
| `few` | 2, 3, 4, 22, 23, 24… | *2 bicikla*, *23 bicikla* |
| `other` | 0, 5–10, **11–14**, 15–20, 25… | *5 bicikala*, *12 bicikala*, *0 bicikala* |

The 11–14 band is the one that gets forgotten: *11 bicikala*, not *11 bicikl*.

`freshness_seconds`, `freshness_minutes` and `freshness_hours` have identical
`one` and `few` items, and that is correct rather than a copy-paste: **`prije`
governs the genitive**, and the genitive singular of *minuta* and the paucal
after 2–4 are both *minute* (*prije 1 minute*, *prije 3 minute*, *prije 5
minuta*); *sat* behaves the same (*prije 1 sata*, *prije 2 sata*, *prije 5
sati*), and so does *sekunda*.

## The words

| English | Bosnian | Why |
|---|---|---|
| journey | **putovanje** | The whole door-to-door thing. One word on the journey screen, in the settings section and on every button, so the three are visibly about one object. |
| ride | **vožnja** | The bike leg alone. A different word from *putovanje*, as the English asks. |
| route | **ruta** | Only the line on the ground: `journey_no_route` (*nema prohodne rute*), `journey_graph_missing`, `dataset_routing`. Never the planned journey. |
| walk (leg) | **pješice** | Adverbial, so it needs no agreement anywhere: *Pješice do stanice*, *Pješice do odredišta*, *%1$s pješice*. |
| station | **stanica** | The bike-share station, throughout. |
| railway station | **željeznička stanica** | Bosnian has no separate word of the Croatian *kolodvor* kind, so `address_search_prompt_message` — which means railway stations by "stations", and says so in its comment — names them in full: *željezničke stanice*. That is what keeps bare *stanica* meaning one thing in the whole file. |
| free dock | **slobodno mjesto** | What a bike is returned into, counted as available: `docks_available`, `counterpart_docks`, `mode_docks`. |
| dock (capacity) | **stalak** | The same object counted as a total: `docks_total`, `station_detail_with_capacity`. The screen shows both figures side by side — *12 slobodnih mjesta · 30 stalaka* — so they cannot share a word. Never the payment terminal. |
| bike | **bicikl** | |
| map | **karta** | **A departure from the lexicon, and a deliberate one.** Android says *mape*: `settings:default_map_app_title` = *Aplikacija za mape*, `android:keyboard_shortcut_group_applications_maps` = *Mape*, `android:app_category_maps` = *Mape i navigacija*. This file writes *karta* in all 22 places, because *karta* is written Bosnian's word for a geographic map where *mapa* is the loan Android took for its **application category** — and this application draws a map, it is not a maps app. |
| map app | **aplikacija za mape** | The one place the lexicon wins outright, and the distinction is worth holding: `station_no_navigation_app` is about an *application*, not about a map, and `settings:default_map_app_title` is that exact phrase. So the thing on screen is a *karta* and the thing you might not have installed is an *aplikacija za mape*. |
| any bike | **Bilo koji** | `journey_bike_kind_any`, the first of three toggle branches. Not *Bilo koji bicikl*: the two branches beside it (*Klasični*, *Električni*) elide the noun, and repeating it here made this the longest toggle label in any language. *Svejedno* was tried and dropped — idiomatic and shorter still, but an adverb wedged between two adjectives, where *Bilo koji* agrees with the elided *bicikl* as its neighbours do. The spoken `journey_bike_kind_any_description` carries the full phrase. |
| mechanical | **klasični** | The contrast Bosnian actually draws with *električni bicikl*, and the one the bike-share networks in the country use. *Mehanički* is the literal rendering and reads as a machine part rather than as a bike you pedal. |
| electric | **električni** | Pedal-assist, which `journey_bike_kind_electric_description` spells out as *bicikl s pomoćnim električnim pogonom* so nobody reads "moped". |
| pace (walking) | **tempo** | Never *brzina*. `settings_walking_pace_title` is *Tempo hodanja*: a pace is something one knows about oneself, where a speed is a figure nobody has measured about themselves. The lexicon's *brzina* is reserved for the rates of machines (`settings:wifi_speed` *Brzina veze*, `settings:pointer_speed`), which is the connotation to avoid. `settings_walking_pace_brisk` is *Žustro* for the same reason — *Brzo* would drag the speed reading back in. |
| Delete | **Izbriši** | Destroys: a city's data, a dataset. `android:deleteText`, and `android:delete` in the polite *Izbrišite*. |
| Remove | **Ukloni** | Takes out of a list: `station_favourite_remove`, *Ukloni iz favorita*. `android:kg_reordering_delete_drop_target_text`. Two words, as Android itself has two. |
| Clear (a search) | **Obriši pretragu** | `settings:clear` = *Obriši*, `android:locale_search_menu` = *Pretraga*. Deliberately not *Izbriši*: clearing a search field destroys nothing, and the file keeps *Izbriši* for what it does destroy. |
| favourites | **Favoriti** | Not in the lexicon — this is a choice, and it is the word Bosnian apps put beside a star. `journey_source_favourite` is *Stanica iz favorita* rather than *omiljena stanica*, so the star, the list and the journey picker all say one word. |
| offline | **van mreže** | Not in the lexicon either, and again a choice. Bosnian does not take the Croatian *izvanmrežni*, and leaving the English *offline* in a store text is what the whole file avoids elsewhere. So the idea is written as a phrase: `settings_section_data` is *Podaci van mreže*, `storage_open` *Upravljaj podacima van mreže*, `dataset_delete_body` ends *za rad van mreže*. |
| feed (GBFS) | **kanal podataka** | The stream a network publishes: `error_feed_unavailable`, `error_malformed`, `welcome_fleet_body`. Kept apart from the line below on purpose — English distinguishes *feed* from *data sources*, and naming both *izvor podataka* would have the error messages and the attributions screen using one phrase for two things. |
| data sources | **izvori podataka** | The attributions screen and the city-by-city list: `about_attributions_title`, `sources_title`, `sources_open`. Who produced the data, never the pipe it comes down. |
| address index | **indeks adresa** | *Indeks* and not the Croatian *kazalo*, which is not Bosnian. |
| tiles (map) | **pločice** | `map_needs_tiles_title`. `settings:quick_settings_developer_tiles` = *Pločice programera za brze postavke*. |
| metered / unmetered | **s naplatom / bez naplate** | The five `download_*` strings name what is billed rather than Wi-Fi, and use the system's own pair: `settings:wifi_metered_label` = *S naplatom*, `settings:wifitrackerlib_wifi_unmetered_label` = *Bez naplate*. |
| Out of service | **Ne radi** | `settings:radioInfo_service_out`. |
| No connection | **Niste povezani s mrežom** | `settings:mobile_data_no_connection`, used verbatim to open `error_offline` and `error_offline_download`. Emphatically **not** *Nema veze*, which is first of all the idiom for "never mind, it doesn't matter" — at the head of an error message it tells the reader the opposite of what the line means. |
| just now | **upravo sad** | `settings:time_unit_just_now` is *Upravo*, which stands alone there; here the words are swallowed by *Ažurirano %1$s*, and *Ažurirano upravo* does not close. *Sad* is added for that, and nothing else changes. |
| In use | **U upotrebi** | `android:media_route_status_in_use`. |
| Update available | **Dostupno je ažuriranje** | `settings:android_version_pending_update_summary`. |
| browser | **preglednik** | `android:keyboard_shortcut_group_applications_browser`, `settings:default_browser_title`. |
| Open by default | **Zadano otvaranje** | `settings:auto_launch_label`, quoted verbatim inside `about_links_body` because the reader has to find that row in Settings. *Dodajte link* on the same line is `settings:app_launch_add_link`, plural because that is how the system writes it. |
| Settings / Display / Storage / Privacy / System / Language / Licence / Version | **Postavke / Prikaz / Pohrana / Privatnost / Sistem / Jezik / Licenca / Verzija** | All from the lexicon: `android:global_action_settings`, `settings:display_category_title`, `settings:storage_category`, `settings:lock_screen_notifications_title`, `android:default_audio_route_category_name`, `settings:app_locale_preference_title`, `settings:license_title`, `settings:vpn_version`. *Prikaz* rather than *Ekran* for `settings_section_display` because *Prikaz* is the lexicon's own settings **category** title (`settings:display_category_title`), which is exactly what that string is. *Ekran* is not reserved for hardware and is not avoided: the file uses it six times for a surface of the application itself — *s ekrana za pohranu*, *Zadani ekran pri otvaranju*, *ostaje na ekranu* — and the two live side by side without colliding. |

## Two rules that are not about single words

**`Prikaži` shows something the application already has; `Pogledaj` sends the
reader off to read something.** English uses "See", "Show" and "View" loosely
across these, so the split is ours and it needs writing down or the next
contributor will flatten it. *Prikaži* is the lexicon's word for revealing a
surface (`settings:condition_expand_show`) and takes `map_open_list`,
`favourites_open`, `stations_open_map`, `journey_frame`, `incoming_show_me` —
each of which swaps what is on screen. *Pogledaj* takes the two that hand the
reader a text to read rather than a view to look at: `whats_new_open` and
`about_open_repository`, the second of which leaves the application entirely.

**`bikes_mechanical` and `bikes_electric` take the indefinite adjective in
`one`, and this is a deliberate departure from the map filter's labels.** The
filter says *Klasični* / *Električni*, definite, because there the word is a
nominalised category label — "the mechanical ones". The counting plurals sit
somewhere else grammatically: the noun is elided and a bare figure stands in
front, which is the indefinite's position. So *1 klasičan · 3 električna*, and
not *1 klasični*, which would read as "the classic one" pointing at a bike the
reader is supposed to already know. Only `one` distinguishes the two — *2
klasična* and *5 klasičnih* are shared — so this is a two-item exception and
not a general disagreement with the filter.

## The English is impersonal, and so is the Bosnian

"No history is kept", "It is read from the feed", "Nothing is sent" — the
source never says "we". In an application whose whole argument is that nobody
is behind it, a first person plural would ask the reader to trust a *mi*
instead of stating a property of the software.

Bosnian keeps it at no cost, because the reflexive passive does the same work:
*ne čuva se nikakva historija*, *ne šalju se nikome*, *čita se iz vlastitog
kanala podataka mreže*, *skupovi podataka preuzimaju se samo kada to
zatražite*.

**One line does say *mi*, and deliberately.** `city_proposal_body` ends *Idemo
s tim?* — because the English ends "Shall we go with that?". That sentence is
not a claim about what the software does with your data; it is the application
asking a question and waiting for *Da*. The impersonal rule covers the privacy
and behaviour texts, and this is neither.

## Cases are suffixes, and a placeholder cannot carry one

Bosnian declines, and the ending falls on the word itself. A sentence built
around `%1$s` has to stay right whatever arrives in it, and what arrives is
always a nominative: a station name, a street name, a city, a network label.
So these lines are written around that rather than against it:

| String | What it does | Why |
|---|---|---|
| `journey_step_to_station`, `journey_step_ride` | *Pješice do stanice %1$s* | *Do* governs the genitive; the case falls on *stanice* and the name follows in apposition, in the nominative it arrived in. |
| `station_address_nearby` | *Blizu: %1$s* | *Blizu* also governs the genitive, and the argument is a street **or** a square, reaching the line as it stands. A colon turns it into a label, which declines nothing. |
| `dataset_imported`, `dataset_deleted` | *Instalirano: %1$s* | A dataset's name is a masculine plural in two cases (*Podaci karte*, *Podaci za rute*) and a masculine singular in the third (*Indeks adresa*), so no participle agrees with all three. |
| `city_here_body`, `city_here_installed_body` | *%1$s pokriva područje u kojem se nalazite. Instalirajte podatke te mreže…* | The placeholder opens the sentence as the **subject**, in the nominative it arrived in, and *te mreže* carries the case the rest needs. Subject-first also settles which of the two nouns covers which, where *Područje … pokriva %1$s* could be read either way round. |
| `dataset_delete_description` | *Izbriši: %1$s* | Spoken by TalkBack on the delete button (`DatasetAdapter.kt:79`), handed the same dataset name. A bare *Izbriši %1$s* asks the name for an accusative it cannot take — *Izbriši Podaci karte* — so the colon does the work here exactly as it does in `city_delete_description`. |
| `city_here_use`, `city_here_install` | *Prebaci se* / *Instaliraj podatke* | These are **one** button, not two: `MainActivity.kt:553-555` and `MapFragment.kt:899-901` pick one label or the other for the same positive button, which calls `switchTo` either way. So they take the same shape — bare imperative, no pronoun. The first draft had *Koristi je*, leaning a feminine pronoun on *mreže* eight words back while the sentence's **subject** is `%1$s`, whose gender nobody can predict (*Vélib’*, *nextbike*, *Bicikelj*); the second draft fixed that by naming the network but left the other branch on *njene podatke*, so the same button argued two different ways. *Prebaci se* is genderless and says what the button does.
| `city_proposal_body`, `map_outside_city_message` | *…područja koje pokriva %1$s* | Same placeholder, inside a relative clause this time: *koje* is the accusative object and `%1$s` the nominative subject, so again nothing is asked of the name. |

### The three lines that must not say "grad"

`city_delete_description`, `city_delete_body` and `city_deleted` are handed
`city.displayName`, which is the **network's** name and not the city's: 328 of
the 331 entries in `config/catalogue.json` carry a `displayName` of their own,
and all 36 Czech networks are called *nextbike*. Writing *podaci grada %1$s* —
the obvious way to dodge the case ending — would make the reader read *podaci
grada nextbike*, a false statement the English never makes. So the three say

- *Izbriši podatke: %1$s*
- *Svi podaci za rad van mreže za „%1$s“ bit će izbrisani…*
- *Podaci „%1$s“ su izbrisani*

The colon and the quotation marks hold the name at arm's length, exactly as
`dataset_deleted` does, and spare it an ending it could not take.

## The address prompt, and the layout that is not ours

`address_search_hint` is **„Ulica, broj, mjesto“** — street, then number, then
town, which is the order Bosnia and Herzegovina writes an address in (*Zmaja od
Bosne 4, Sarajevo*). `AddressQuery.parseQuery` has read a house number standing
between the street and the town since the pilot, precisely so that each
language may write this line in its own order rather than in English's.

No postcode is invited, and the reason is not the one it is easy to assume.
The parser does **not** choke on a second number here: `parseQuery` strips a
five-digit group out of the words *before* it goes looking for a house number
(`AddressQuery.kt:87-88`, `looksLikePostcode`), and a Bosnian postcode — 71000,
78000 — is exactly a five-digit group. So *Zmaja od Bosne 4 71000 Sarajevo*
parses fine. The postcode is left out of the prompt because the prompt is a
hint sitting in a narrow field, and inviting something the parser throws away
buys the reader nothing. Street, number, town is what the field has room for
and what the index actually matches on.

**The order a result is printed in is a separate matter and is not this file's
to decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3): *Zmaja od Bosne 4* is how a Sarajevo address reads for a
reader in Japanese, and *12 rue Nationale* is how a Lyon one reads for a
Bosnian reader. The layouts live in `core/address/AddressLayout.kt`, keyed on
the language of the **address base**.

**There is no `"bs"` entry in that table yet.** A Bosnian base therefore falls
on `DEFAULT_LAYOUT` and would print *4 Zmaja od Bosne*, which is neither the
country's order nor anybody's. Bosnia and Herzegovina closes with the number —
*Zmaja od Bosne 4*, *Titova 21a* — and runs a letter suffix hard against it,
exactly as the Polish and Dutch entries do; there is no second number of the
Czech kind. Adding the line is one entry and it belongs to whoever owns that
file, not to this translation.
