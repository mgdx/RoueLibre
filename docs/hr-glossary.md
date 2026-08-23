# Croatian glossary

The terms `res/values-hr/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Croatian ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

The lexicon quoted throughout is the 5 900 system strings extracted from a real
phone's `framework-res.apk` and `Settings.apk` (`do-not-commit/lexicon/hr.tsv`).
Every key named below was grepped from it; where the file departs from Android,
that is said in as many words.

## Register and typography

The reader is addressed with **persiranje**, the polite plural. That is not a
house style but Android's own: the lexicon's Croatian says *Provjerite postavke
pristupa* (`android:view_and_control_notification_title`), *Odaberite račun*
(`android:choose_account_label`), *Dodirnite da biste to promijenili*
(`settings:inactive_app_active_summary`), and nowhere the singular.

**Buttons take the second person singular imperative**, which is Android's
habit for a control rather than a sentence: *Nastavi* (`android:autofill_
continue_yes`), *Odustani* (`android:cancel`), *Izbriši* (`android:delete`),
*Preskoči* (`android:skip_button_label`), *Prikaži* (`settings:condition_
expand_show`), *Zamijeni* (`settings:vpn_replace`), *Dodaj* (`settings:add`).
Sentences take the polite plural imperative: *Provjerite pravopis*,
*Instalirajte kazalo*, *Odaberite grad*.

**The one departure from that rule is `action_retry`**, written *Pokušajte
ponovo* and not *Pokušaj ponovo*. It is a button and the rule above would give
the singular — but Android's own button for exactly this says the plural:
`settings:private_space_tryagain_label` = *Pokušajte ponovo*, and
`android:lockscreen_password_wrong` and `settings:wifi_check_password_try_again`
say the same. Following the system on the phrase the reader has met a hundred
times matters more here than the internal rule, and `journey_no_stations` and
`map_location_unavailable` end on the same words for the same reason. Do not
"fix" it to the singular.

Quotation marks are **„ … “**. Diacritics are written in full — **č ć ž š đ** —
and never approximated by their bare letters. The dash that breaks a sentence
is **–** with a space on either side, not the em dash the English file uses;
that is the one piece of punctuation changed inside a format-only string,
`city_label` (`%1$s – %2$s`). The apostrophe, where a network's name carries one
(*Vélib’*, *V’lille*, *Vélo’v*), is **’** (U+2019) and never the straight quote,
which is why nothing in the file is escaped.

## Plurals: what each category covers, not its smallest number

Croatian has three, and each has to read correctly for **every** number that
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
sati*).

## The words

| English | Croatian | Why |
|---|---|---|
| journey | **putovanje** | The whole door-to-door thing. One word on the journey screen, in the settings section and on every button, so the three are visibly about one object. |
| ride | **vožnja** | The bike leg alone. A different word from *putovanje*, as the English asks. The lexicon has *vožnja* for riding/driving (`settings:zen_mode_trigger_title_driving`, *Tijekom vožnje*). |
| route | **ruta** | Only the line on the ground: `journey_no_route` (*nema prohodne rute*), `journey_graph_missing`, `dataset_routing`. Never the planned journey. |
| walk (leg) | **pješice** | Adverbial, so it needs no agreement anywhere: *Pješice do stanice*, *Pješice do odredišta*, *%1$s pješice*. |
| station | **stanica** | The bike-share station, throughout. |
| railway station | **kolodvor** | `address_search_prompt_message` means railway and coach stations by "stations", and Croatian has a separate word for them. Using it there is what keeps *stanica* meaning one thing in the whole file. The store texts say *postaje metroa* for the same reason. |
| free dock | **slobodno mjesto** | What a bike is returned into, counted as available: `docks_available`, `counterpart_docks`, `mode_docks`. |
| dock (capacity) | **stalak** | The same object counted as a total: `docks_total`, `station_detail_with_capacity`. The screen shows both figures side by side — *12 slobodnih mjesta · 30 stalaka* — so they cannot share a word. Never the payment terminal. |
| bike | **bicikl** | |
| mechanical | **klasični** | The contrast Croatian actually draws with *električni bicikl*. *Mehanički* is the literal rendering and reads as a machine part rather than as a bike you pedal. |
| electric | **električni** | Pedal-assist, which `journey_bike_kind_electric_description` spells out as *bicikl s pomoćnim električnim pogonom* so nobody reads "moped". |
| pace (walking) | **tempo** | Never *brzina*. `settings_walking_pace_title` is *Tempo hodanja*: a pace is something one knows about oneself, where a speed is a figure nobody has measured about themselves. The lexicon's *brzina* is reserved for rates of machines (`settings:wifi_speed`, `settings:tts_default_rate_title`), which is the connotation to avoid. |
| Delete | **Izbriši** | Destroys: a city's data, a dataset. `android:delete`, `settings:dlg_delete`. |
| Remove | **Ukloni** | Takes out of a list: `station_favourite_remove`, *Ukloni iz favorita*. `android:kg_reordering_delete_drop_target_text`. Two words, as Android itself has two. |
| Clear (a search) | **Izbriši pretraživanje** | `settings:clear` = *Izbriši*. |
| favourites | **Favoriti** | Not in the lexicon — this is a choice, and it is the word Croatian apps put beside a star. |
| offline | **izvanmrežni** | Not in the lexicon either, and again a choice: *offline* is unavoidable in speech but the written Croatian of the platform is *izvanmrežno*. `settings_section_data` is *Izvanmrežni podaci*. |
| address index | **kazalo adresa** | *Kazalo*, an index in a book, rather than *indeks*, which in Croatian reads as a figure or a student's record book. |
| tiles (map) | **pločice** | `map_needs_tiles_title`. |
| Out of service | **Ne radi** | `settings:radioInfo_service_out`. |
| just now | **upravo sad** | `settings:time_unit_just_now`, lowercased because it is embedded in *Ažurirano %1$s*. |
| In use | **U upotrebi** | `android:media_route_status_in_use`. |
| Settings / Display / Storage / Privacy / System / Language | **Postavke / Zaslon / Pohrana / Privatnost / Sustav / Jezik** | All from the lexicon, keys `android:global_action_settings`, `settings:display_settings`, `settings:storage_category`, `settings:privacy_dashboard_title`, `android:default_audio_route_category_name`, `settings:app_locale_preference_title`. |
| Licence | **licenca** | `settings:license_title` = *Licenca*, not *licencija*. |

## The English is impersonal, and so is the Croatian

"No history is kept", "It is read from the feed", "Nothing is sent" — the
source never says "we". In an application whose whole argument is that nobody
is behind it, a first person plural would ask the reader to trust a *mi*
instead of stating a property of the software.

Croatian keeps it at no cost, because the reflexive passive does the same work:
*ne čuva se nikakva povijest*, *ne šalju se nikome*, *čita se iz vlastitog
izvora podataka mreže*, *skupovi podataka preuzimaju se samo kad to zatražite*.
There is not one *mi* in the file or in the store texts.

## Cases are suffixes, and a placeholder cannot carry one

Croatian declines, and the ending falls on the word itself. A sentence built
around `%1$s` has to stay right whatever arrives in it, and what arrives is
always a nominative: a station name, a street name, a city, a network label.
So these lines are written around that rather than against it:

| String | What it does | Why |
|---|---|---|
| `journey_step_to_station`, `journey_step_ride` | *Pješice do stanice %1$s* | *Do* governs the genitive; the case falls on *stanice* and the name follows in apposition, in the nominative it arrived in. |
| `station_address_nearby` | *Blizu: %1$s* | *Blizu* also governs the genitive, and the argument is a street **or** a square, reaching the line as it stands. A colon turns it into a label, which declines nothing. |
| `dataset_imported`, `dataset_deleted` | *Instalirano: %1$s* | A dataset's name is a masculine plural in two cases (*Podaci karte*, *Podaci za rute*) and a neuter singular in the third (*Kazalo adresa*), so no participle agrees with all three. |
| `city_here_body`, `city_here_installed_body`, `city_proposal_body`, `map_outside_city_message` | *…poslužuje %1$s. Instalirajte podatke te mreže…* | The placeholder stands as the **subject**, in the nominative, and *te mreže* carries the case the rest of the sentence needs — rather than a pronoun that would have to agree with whatever the label produced. |

### The three lines that must not say "grad"

`city_delete_description`, `city_delete_body` and `city_deleted` are handed
`city.displayName` (`CityAdapter.kt:111`, `CityFragment.kt:279` and `:294`),
which is the **network's** name and not the city's: 328 of the
331 entries in `config/catalogue.json` carry a `displayName` of their own, and
all 36 Czech networks are called *nextbike*. Writing *podaci grada %1$s* — the
obvious way to dodge the case ending — would make the reader read *podaci grada
nextbike*, a false statement the English never makes. So the three say

- *Izbriši podatke: %1$s*
- *Svi izvanmrežni podaci za „%1$s“ bit će izbrisani…*
- *Podaci „%1$s“ izbrisani su*

The colon and the quotation marks hold the name at arm's length, exactly as
`dataset_deleted` does, and spare it an ending it could not take.

## The address prompt, and the layout that is not ours

`address_search_hint` is **„Ulica, broj, mjesto“** — street, then number, then
town, which is the order Croatia writes an address in (*Ilica 12, Zagreb*).
`AddressQuery.parseQuery` has read a house number standing between the street
and the town since the pilot, precisely so that each language may write this
line in its own order rather than in English's.

No postcode is invited. Croatia's is five digits and would qualify under the
rule in `SPEC.md` §4.3, but the parser gives a number up as soon as the query
holds a second one — and a Croatian address that already carries a house number
would then lose it. Street, number, town is what the parser reads best and what
the prompt therefore asks for.

**The order a result is printed in is a separate matter and is not this file's
to decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3): *Ilica 12* is how a Zagreb address reads for a reader in
Japanese, and *12 rue Nationale* is how a Lyon one reads for a Croatian reader.
The layouts live in `core/address/AddressLayout.kt`, keyed on the language of
the **address base**.

**There is no `"hr"` entry in that table yet.** A Croatian base therefore falls
on `DEFAULT_LAYOUT` and would print *12 Ilica*, which is neither Croatia's order
nor anybody's. Croatia closes with the number — *Ilica 12*, *Vukovarska 269a* —
and runs a letter suffix hard against it, exactly as the Polish and Dutch
entries do; there is no second number of the Czech kind. Adding the line is one
entry and it belongs to whoever owns that file, not to this translation.
