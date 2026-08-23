# Albanian glossary

The terms `res/values-sq/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Albanian ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

The lexicon quoted throughout is the 5 900 system strings extracted from a real
phone's `framework-res.apk` and `Settings.apk` (`do-not-commit/lexicon/sq.tsv`).
Every key named below was grepped from it; where the file departs from Android,
that is said in as many words.

The network served in Albanian is **Prishtina bike**, in Kosovo. The file is
written in standard Albanian, which reads the same on both sides of the border:
nothing in it is Gheg or Kosovo-specific, and nothing in it is Tosk-only.

## Register and typography

The reader is addressed **in the second person singular**, and that is not a
house style but Android's own. Of the system strings extracted from the phone,
over two hundred use the singular imperative — *Kontrollo lidhjen dhe provo
përsëri* (`settings:wifi_dpp_check_connection_try_again`), *Zgjidh një llogari*
(`android:choose_account_label`), *Aktiv. Trokit për ta ndryshuar*
(`settings:inactive_app_active_summary`) — against exactly one line in the
polite plural (*Zgjidhni siguruesin e rezervës*). Sentences and buttons alike
therefore take the singular: *Vazhdo* (`android:autofill_continue_yes`),
*Anulo* (`android:cancel`), *Fshi* (`android:delete`), *Kapërce*
(`android:skip_button_label`), *Shfaq* (`settings:condition_expand_show`),
*Zëvendëso* (`settings:vpn_replace`).

Quotation marks are **„ … “**. The diacritics **ë** and **ç** are written in
full and never approximated by a bare *e* or *c*: they are letters of their own
and dropping one makes another word (*të* / *te*, *çelët* / *celet*).

The dash that breaks a sentence is **–** with a space on either side, not the
em dash the English file uses. It replaces that dash inside sentences
(`welcome_data_body`, the four `journey_walk_*` lines) and inside `city_label`,
the one format-only string that carries one. The only em dash left in the file
is `counterpart_none`, which is a glyph standing in for an absent figure rather
than punctuation.

The apostrophe is **’** (U+2019), never the straight quote — in Albanian's own
elisions (*t’i shkarkosh*, *ta hapësh*) as in a network's name (*Vélib’*,
*V’lille*, *Vélo’v*). That is why nothing in the file is escaped.

## Plurals: two categories, and `one` means one

CLDR gives Albanian **`one`** and **`other`** for cardinals. `one` is the
number 1 and nothing else; `other` takes everything else, **0 included** — so
`other` has to read correctly for zero as well: *0 biçikleta*, *0 vende të
lira*. The two `counterpart_*` plurals hold no placeholder and must never grow
one: the figure is already painted beside them.

## The words

| English | Albanian | Why |
|---|---|---|
| journey | **udhëtim** | The whole door-to-door thing. One word on the journey screen (`journey_title`), in the settings section (`settings_section_journey`) and on every button, so the three are visibly about one object. |
| ride | **ngasje** | The bike leg alone, as a noun: `journey_computing_own_bike` (*Po llogaritet ngasja…*), `journey_detail_profile`, `journey_detail_profile_description`. A different word from *udhëtim*, as the English asks. *Ngas biçikletën* is the ordinary Albanian for riding one. |
| ride (as a step) | **me biçikletë** | `journey_step_ride`, `journey_step_ride_all` and `journey_summary` say *me biçikletë* rather than the noun: a step label is adverbial in Albanian — *Me biçikletë deri te %1$s* — and an adverb needs no agreement with whatever follows it. Same reason as *në këmbë* below. |
| route | **rrugë** | Only the line on the ground, in `journey_no_route`: *Nuk ka rrugë të kalueshme mes këtyre dy pikave*. Never the planned journey. |
| routing | **itinerar** | The computed line, where *rrugë* would be read as a street: `dataset_routing` (*Grafi i itinerarëve*), `journey_graph_missing`, `about_attribution_brouter` (*Llogaritja e itinerarëve*). |
| walk (leg) | **në këmbë** | Adverbial, so it agrees with nothing anywhere: *Në këmbë deri te %1$s*, *%1$s në këmbë*, *pjesët në këmbë*. |
| station | **stacion** | The bike-share station, throughout. |
| railway station | **stacion transporti** | `address_search_prompt_message` means railway and bus stations by "stations" — its comment in `values/strings.xml` says so. Albanian has no separate word for them, so the line names what they belong to: *stacionet e transportit*. That is what keeps a bare *stacion* meaning one thing in the whole file. The store texts say *stacionet e metrosë* for the same reason. |
| free dock | **vend i lirë** | What a bike is returned into, counted as available: `docks_available`, `counterpart_docks`, `mode_docks`, `journey_no_dock_nearby`. |
| dock (capacity) | **vend parkimi** | The same object counted as a total: `docks_total`, `station_detail_with_capacity`. The two figures sit side by side on a station's sheet — *12 vende të lira · 30 vende parkimi* — so they cannot share a word. **This is the one place the file departs from the neat solution**: Albanian has no single noun for a bike rack that a reader would recognise, so the distinction is carried by the qualifier rather than by two unrelated nouns. *Vend parkimi* was preferred to a coined *mbajtëse* precisely because a reader has met it. Never the payment terminal. |
| bike | **biçikletë** | |
| mechanical | **mekanike** | The contrast Albanian draws with *elektrike*, and the word the reader will have seen on a bike-share sign. |
| electric | **elektrike** | Pedal-assist, which `journey_bike_kind_electric_description` spells out as *një biçikletë me ndihmë në pedale* so that nobody reads "moped". |
| pace (walking) | **ritëm** | Never *shpejtësi*. `settings_walking_pace_title` is *Ritmi i ecjes*: a pace is something one knows about oneself, where a speed is a figure nobody has measured about themselves. The three values agree with *ritmi* in the masculine — *I ngadaltë*, *Normal*, *I shpejtë* — where the lexicon's own *Slow* is *E ngadaltë* (`settings:speed_label_slow`), feminine because it agrees with a speed there. |
| Delete | **Fshi** | Destroys: a city's data, a dataset. `android:delete`, `settings:dlg_delete`, `settings:user_delete_button`. |
| Remove | **Hiq** | Takes out of a list: `station_favourite_remove`, *Hiq nga të preferuarat*. `android:kg_reordering_delete_drop_target_text`, `settings:remove`. Two words, because Android itself has two. |
| Clear (a search) | **Pastro** | `settings:clear` = *Pastro*, and `settings:searchview_clear_text_content_description` = *Pastro tekstin*. Hence *Pastro kërkimin* throughout. |
| favourites | **Të preferuarat** | Not in the lexicon — this is a choice, and it is the word Albanian applications put beside a star. |
| offline | **jashtë linje** | Not in the lexicon either, and again a choice: the phone's Albanian never has to say it. *Jashtë linje* is what Albanian software writing uses, and it is used consistently — `settings_section_data`, `storage_open`, `city_delete_body`, `dataset_delete_body`. |
| feed (GBFS) | **burim** | The stream a network publishes: `error_feed_unavailable`, `error_malformed`, `welcome_fleet_body`, `sources_open`. |
| data sources | **burimet e të dhënave** | The attributions screen and the city-by-city list: `about_attributions_title`, `sources_title`. Same family as the line above, and deliberately so: `sources_intro` says who *produces* the data and `error_malformed` says the *burim* is at fault, which in Albanian are two readings of one word that the reader resolves from the sentence, not two different objects. |
| address index | **indeksi i adresave** | `dataset_addresses`, `address_needs_index_*`, `incoming_needs_index`. |
| map | **harta** | The map the reader looks at: `settings_opening_map`, `stations_open_map`. The tiles dataset is *harta bazë*, a different label on purpose; see below. |
| conurbation | **zonë urbane** | `city_intro`, `map_needs_city_message`, `welcome_data_body`. *Qytet* stays for a city and *zonë urbane* for the wider thing a network covers, exactly as the English keeps "city" and "conurbation" apart. |
| network | **rrjet** | The bike-share network. Which is why the routing dataset is *Grafi i itinerarëve* and never *rrjeti i rrugëve*: that would put the word *rrjet* on two unrelated objects on the same screen. |
| Out of service | **Jashtë shërbimit** | The wording the phone's own Albanian uses for a service that is down. |
| just now | **pikërisht tani** | `settings:time_unit_just_now` gives *Pikërisht tani*; lower-cased here because it is read inside *Përditësuar %1$s*. |
| In use | **Në përdorim** | `android:media_route_status_in_use`, `settings:wifi_display_status_in_use`. |
| Try again | **Provo përsëri** | Android says both, and almost evenly: *Provo sërish* twelve times (`android:lockscreen_password_wrong`, `settings:network_connection_timeout_dialog_ok`) against *Provo përsëri* ten (`settings:audio_streams_dialog_retry`, and the sentence `settings:wifi_dpp_check_connection_try_again` = *Kontrollo lidhjen dhe provo përsëri*). Either is the system's; **one** of them has to be this file's, and it is *Provo përsëri* — `action_retry`, `journey_no_stations`, `map_location_unavailable`, `dataset_rejected_transfer` all end on it. |
| Refresh | **Rifresko** | `action_refresh`, the pull-to-refresh control and `journey_no_stations`. The lexicon has the verb only once and inflected — *Lejo që aplikacionet t’i rifreskojnë automatikisht të dhënat* (`settings:auto_sync_account_summary`) — and no imperative, so *Rifresko* is formed here from a verb Android does use. It is kept apart from **Përditëso**, which is reserved for a version replacing another: `storage_check_updates`, `dataset_update_available`, `dataset_rejected_version`. Asking the network again for its counts is not an update. |
| in detail | **në hollësi** | `journey_detail_open`, `journey_detail_title`. **A departure from the lexicon**, which says *Detajet* (`settings:memory_details`, `settings:wifi_details_title`): those are labels on a screen of technical fields, where these two lines are a sentence about looking at a journey more closely, and *shfaq udhëtimin në hollësi* is what Albanian says for that. Everything else on the screen follows Android. |
| Update available | **Ofrohet përditësim** | `settings:android_version_pending_update_summary`. |
| metered / unmetered | **me matje / pa matje** | `settings:data_usage_metered_yes` = *Me matje*, `settings:wifi_unmetered_label` = *Pa matje*. `download_unmetered_only` follows the pair rather than naming Wi-Fi, exactly as the English does. |
| anyway | **gjithsesi** | `settings:certificate_warning_install_anyway` = *Instalo gjithsesi*, `settings:accessibility_magnification_triple_tap_warning_positive_button` = *Vazhdo gjithsesi*. Hence *Shkarko gjithsesi*. |
| Tap | **Trokit** | `settings:inactive_app_active_summary` = *Aktiv. Trokit për ta ndryshuar*. Not *Prek*, which the lexicon keeps for touching a sensor (`settings:security_settings_fingerprint_enroll_find_sensor_title` = *Prek sensorin*). |
| Settings / Display / Storage / Privacy / System / Language | **Cilësimet / Ekrani / Hapësira ruajtëse / Privatësia / Sistemi / Gjuha** | All from the lexicon: `android:global_action_settings`, `settings:display_settings`, `settings:storage_settings`, `settings:privacy_dashboard_title`, `android:default_audio_route_category_name`, `settings:app_locale_preference_title`. |
| Licence | **Licenca** | `settings:license_title` = *Licenca*. |
| Version | **Versioni** | `settings:vpn_version`. `settings_walking_pace_normal` is the one line the checker reports as identical to the English, and *Normal* is genuinely the Albanian word for it. |

### One word deliberately said two ways

*Provo përsëri* is the file's word for trying again, and it is the only one on
the buttons and at the end of the error messages. But **five lines say
*sërish*** — `welcome_open` (*Lexo sërish hyrjen*), `city_delete_body`,
`dataset_delete_body`, `dataset_rejected_checksum` and `download_can_resume`
(*Nise sërish shkarkimin*) — and that is not an oversight. None of them is
"try again": they say *do it a second time*, which *sërish* carries and which
*përsëri* would make heavy next to the *provo përsëri* the reader has already
met. Android itself uses both words for both senses. **Do not level the five
onto *përsëri*** without reading each sentence: the split is between two
meanings, not between two synonyms.

## The English is impersonal, and so is the Albanian

"No history is kept", "It is read from the feed", "Nothing is sent" — the
source never says "we". In an application whose whole argument is that nobody
is behind it, a first person plural would ask the reader to trust a *ne*
instead of stating a property of the software.

Albanian keeps it at no cost, because the non-active voice does the same work:
*nuk ruhet asnjë historik*, *nuk i dërgohen askujt*, *lexohet nga burimi i vetë
rrjetit*, *grupet e të dhënave shkarkohen vetëm kur ti e kërkon*.

**`city_proposal_body` is where that rule is easiest to break, and it does not
break here.** The English ends "Shall we go with that?", and the obvious
Albanian is *Ta zgjedhim këtë?* — a *ne* the application has no business using.
The line ends **Të përdoret ky rrjet?** instead, the non-active question form
Android itself uses for exactly this kind of dialogue (*Të fshihet ky
përdorues?*, `settings:user_confirm_remove_title`).

## The definite article is a suffix, and a placeholder cannot carry one

Albanian marks the definite article on the end of the noun — *stacion* /
*stacioni*, *biçikletë* / *biçikleta*. A sentence built around `%1$s` cannot
decide how the placeholder ends, and what arrives is always a bare name: a
station, a street, a city, a network label, a formatted size. So these lines
are written around that rather than against it:

| String | What it does | Why |
|---|---|---|
| `journey_step_to_station`, `journey_step_ride` | *Në këmbë deri te %1$s* | *Te* takes the nominative in Albanian, definite or not, so a station name arrives fit to stand there whatever its shape. |
| `station_address_nearby` | *Afër: %1$s* | *Afër* governs the ablative, and `%1$s` is a raw `address.streetName` in the nominative (`StationDetailSheet.kt:219`, `JourneyDetailFragment.kt:592`): *Afër Rruga B* would be wrong. The colon makes it a label, which governs nothing. |
| `dataset_delete_description` | *Fshi: %1$s* | Spoken on a dataset row's delete button (`DatasetAdapter.kt:79`). *Fshi* takes the accusative definite — *Fshi hartën bazë* — while the placeholder always arrives in the nominative, so the colon carries it instead. This is the line of the file where the article trap is easiest to miss; the Croatian file solved it the same way. |
| `city_installed`, `storage_total` | *Instaluar në pajisje: %1$s* | The placeholder is a formatted size — "45 MB", "1,2 GB" — and no Albanian participle agrees with a figure this file never sees. A colon turns the line into a label, which agrees with nothing. |
| `city_here_body`, `city_here_installed_body` | *%1$s mbulon zonën ku ndodhesh. Instalo të dhënat e këtij rrjeti…* | The placeholder opens the sentence as the **subject**, in the shape it arrived in, and *këtij rrjeti* carries the case the rest of the sentence needs — rather than a pronoun (*e tij*) that would have to agree with whatever the label produced. |
| `city_proposal_body`, `map_outside_city_message` | *…zonës që mbulon %1$s* | The same placeholder inside a relative clause: *zonës* carries the ending and `%1$s` is the subject of *mbulon*, so nothing is asked of the name. |
| `dataset_imported`, `dataset_deleted` | *%1$s u instalua*, *%1$s u fshi* | See the next section. |

### The three dataset names are all singular, and that is on purpose

`dataset_imported` (*%1$s u instalua*), `dataset_deleted` (*%1$s u fshi*) and
`dataset_delete_body` (*„%1$s“ do të fshihet…*) conjugate a verb with the
dataset's name. Albanian's non-active aorist ignores gender but not number:
*u instalua* against *u instaluan*. A file naming one dataset *Të dhënat e
hartës* — the literal rendering of "Map data", and a plural — and another
*Indeksi i adresave* — a singular — would need two different verbs for one
string.

So the three names are **Harta bazë**, **Grafi i itinerarëve** and **Indeksi i
adresave**, all singular, and the three lines above read correctly for each of
them.

*Harta bazë* — the base map, the drawn ground — and not a bare *Harta*, which
is already `settings_opening_map`, the map screen itself, and which would leave
`map_needs_tiles_message` sending the reader after something the storage screen
calls by another name. The other translations draw the same distinction:
French *Fond de carte*, Spanish *Mapa base*, Romanian *Fond cartografic*, none
of them "the map". `map_needs_tiles_title` and `map_needs_tiles_message` name
it exactly as the storage screen does.

**Do not "restore" `dataset_tiles` to *Të dhënat e hartës*** without rewriting
those three strings at the same time.

### The three lines that must not say "qytet"

`city_delete_description`, `city_delete_body` and `city_deleted` are handed
`city.displayName` (`CityAdapter.kt:111`, and the confirmation dialogue that
follows it), which is the **network's** name and not the city's: 328 of the 331
entries in `config/catalogue.json` carry a `displayName` of their own, and all
36 Czech networks are called *nextbike*. Writing *të dhënat e qytetit %1$s* —
the obvious way to dodge the ending — would make the reader read *të dhënat e
qytetit nextbike*, a false statement the English never makes. So the three say

- *Fshi të dhënat për %1$s*
- *Të gjitha të dhënat jashtë linje për %1$s do të fshihen…*
- *Të dhënat për %1$s u fshinë*

*Për* takes the nominative and says nothing about what the name denotes, which
is exactly what the English "Data for %1$s" does.

## The address prompt, and the layout that is not ours

`address_search_hint` is **„Rruga, numri, qyteti“** — street, then number, then
town, which is the order Kosovo and Albania write an address in (*Rruga Agim
Ramadani 15, Prishtinë*). `AddressQuery.parseQuery` has read a house number
standing between the street and the town since the pilot, precisely so that
each language may write this line in its own order rather than in English's,
and no stop word stands beside the number in that form.

No postcode is invited. Kosovo's is five digits and would qualify under the
rule in `SPEC.md` §4.3, but the parser gives a number up as soon as the query
holds a second one — and an address that already carries a house number would
then lose it.

**The order a result is printed in is a separate matter and is not this file's
to decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3): *Rruga B 12* is how a Pristina address reads for a reader
in Japanese, and *12 rue Nationale* is how a Lyon one reads for an Albanian
reader. The layouts live in `core/address/AddressLayout.kt`, keyed on the
language of the **address base**.

**There is no `"sq"` entry in that table yet.** An Albanian base therefore falls
on `DEFAULT_LAYOUT` and would print *12 Rruga B*, which is neither Kosovo's
order nor anybody's. Kosovo and Albania close with the number — *Rruga Agim
Ramadani 15*, *Bulevardi Nënë Tereza 5* — and run a letter suffix hard against
it, exactly as the Croatian and Polish entries do; there is no second number of
the Czech kind. Adding the line is one entry and it belongs to whoever owns that
file, not to this translation.

## The store texts

`fastlane/metadata/android/sq/` follows the same words as the interface —
*stacion*, *biçikleta të përbashkëta*, *vende të lira*, *jashtë linje*, *grafi i
itinerarëve* — so that somebody who reads the listing and then opens the
application meets one vocabulary and not two.

**The list of cities is the English one, untouched.** `full_description` runs
from Vélib’ in Paris to Citi Bike in New York, by way of Prague, Barcelona,
Copenhagen, Tokyo and Buenos Aires, and the Albanian says the same five in the
same order. Opening that run with Prishtina — tempting, since it is the one
network this language serves — would be this file privileging a city, which is
the one thing the application does not do; every other translation of the wave
leaves the five alone, including those whose own city is not among them.
`changelogs/3.txt` needed no such decision: the English already names Pristina
there, and the Albanian keeps it.

The three walking paces are quoted in `changelogs/3.txt` exactly as the screen
shows them — *i ngadaltë, normal ose i shpejtë* — because the English changelog
quotes its own interface word for word.
