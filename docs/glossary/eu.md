# Basque glossary

The terms `res/values-eu/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Basque ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

The lexicon quoted below is the 8 675 Basque system strings extracted from a
phone's `framework-res.apk` and `Settings.apk`. **Every key cited here was
grepped in it**; where no key is cited, the choice is this file's own and says
so.

## Who it serves, and which Basque

The file serves the whole Basque-speaking country — the Basque Autonomous
Community, Navarre and the Northern Basque Country — so nothing in it is
written for one of the three alone. What is written is **batua**, the standard,
and not a dialect: the application follows whichever city publishes its data,
and Bilbao, Pamplona and Bayonne read the same strings.

The catalogue's Basque city today is Bilbao, whose configuration declares
`"defaultLanguage": "eu"` and whose street names are indexed through
`config/address-normalization/eu.json`.

## Register: `zuka`, which is Android's own

Basque has no T/V distinction of the European kind. The ordinary address is
**`zu`** — neither formal nor familiar, simply the second person singular —
and it is what Android's own Basque uses throughout: *Sakatu hau USB bidezko
arazketa desaktibatzeko* (`android:adb_active_notification_message`), *Ziurtatu
gutxienez 250 MB erabilgarri dituzula*
(`android:low_internal_storage_view_text_no_boot`). This file uses it and
nothing else.

**`Hika` is refused, and the reason is not politeness.** Hika is the intimate
address between two people who know one another, and it is **gendered**: the
same sentence is said one way to a man and another to a woman. An interface
does not know who is holding the phone, and could only pick one — so hika would
make the application wrong for half its readers on every screen. The French
ships *tutoiement* and the German *du* for a reason this file shares (nobody is
behind this application, so no institutional voice is owed), and `zu` already
carries exactly that much: it is what one person says to another in Basque.

**Controls take the bare imperative**, which is what Android writes on every
button: *Utzi* (`android:cancel`), *Ezabatu* (`android:delete`), *Kendu*
(`settings:remove`), *Garbitu* (`settings:clear`), *Saltatu*
(`android:skip_button_label`), *Bilatu* (`android:search_go`), *Ordeztu*
(`settings:vpn_replace`), *Saiatu berriro* (`settings:retry`), *Atzera*
(`android:accessibility_system_action_back_label`).

**The English is scrupulously impersonal** wherever it promises that nothing is
kept — "No history is kept", "It is read from the feed" — and Basque keeps that
with the impersonal forms the language has for it: *Ez da historiarik
gordetzen*, *Ibilbideak telefono honetan bertan kalkulatzen dira*, *Sarearen
jariotik bertatik irakurtzen da*, *Datu-multzoak eskatzen dituzunean bakarrik
deskargatzen dira*. There is no *gu* anywhere in the file and none should be
added: an application whose argument is that nobody is behind it must not ask
the reader to trust a "we".

## Typography

Quotation marks are **« … »**, with no space inside them, which is what
Euskaltzaindia sets as the first level for Basque —
`stations_no_match_message`, `city_no_match_message`, `address_no_match_message`,
`incoming_address_not_found`, `error_feed_unavailable`, `dataset_delete_body`,
`dataset_rejected_format`, `dataset_rejected_not_data`,
`dataset_rejected_other_dataset`, `settings_map_filters_hide_empty_hint`.

**This is a departure from the lexicon and a deliberate one.** Android's Basque
writes the straight `"` 139 times and no « once; but the straight quote is a
software artefact rather than a typographic choice, and Basque publishing sets
« ». The corner case that settles it: `settings_map_filters_hide_empty_hint`
quotes a word of the interface — « Ezer ez » — and a straight quote there reads
as source code.

The dash that breaks a sentence is **–** with a space on either side, not the em
dash the English file uses; that is the one piece of punctuation changed inside
a format-only string, `city_label` (`%1$s – %2$s`). The only em dash left in the
file is `counterpart_none`, which is a typographic mark and not punctuation. No
space stands before `?`, `!`, `:` or `;`.

**Nothing in the file is escaped**, because nothing in it needs an apostrophe:
Basque uses none, and the network names that carry one (*Vélib’*, *Vélo’v*)
arrive through a placeholder rather than being written here. Should one ever be
needed, it is **’** (U+2019) and never the straight quote.

Numbers keep the decimal comma and the thousands point — *42,5 MB*.

## Cases are suffixes, and the suffix depends on the word's last letter

Basque declines by agglutination, and the ending is not one shape: it is `-ra`
after a vowel and `-era` after a consonant, `-ren` against `-en`, `-k` against
`-ek`, `-tik` against `-etik`. What arrives in a placeholder is a station name,
a street, a network label, a dataset name or a size, and **none of them is known
when this file is written**. An ending glued onto a `%1$s` would be wrong about
half the time.

**Android's own Basque never does it either.** There is not one `%1$s`
carrying a suffix in the 8 675 system strings. What Android does instead is
exactly what this file does: a noun of its own carries the case and the
placeholder follows behind a colon. Eighty strings are built that way —
*Hornitzailea: %1$s.* (`android:perms_description_app`), *Erabiltzailea: %1$s.*
(`android:user_switched`), *Kudeatzailea: %1$s*
(`android:zen_mode_implicit_trigger_description`), *Azken eguneratzea: %1$s*
(`settings:abc_slice_updated`), *Aplikazio pertsonalak egun eta ordu honetan
blokeatuko dira: %1$s, %2$s* (`android:personal_apps_suspension_soon_text`,
which also shows the demonstrative device below).

Nine groups carry the whole weight here, and each is worth knowing before it
gets "fixed":

| String | What it does | Why |
|---|---|---|
| `journey_step_to_station`, `journey_step_ride` | `Oinez geltokira: %1$s` | The allative falls on *geltokira*, a noun this file owns, so the ending is fixed. The station's name follows behind the colon exactly as the feed published it — *Areatza* would take `-ra` and *Bilboko Udaletxea* `-ra` too, but *Zabalburu* and *San Mames* would not agree, and the feed decides. |
| `station_address_nearby` | `Gertu: %1$s` | The argument is `address.streetName` — a street **or** a square, arriving as it stands. *…tik gertu* wants an ablative on it; a colon turns the line into a label, which declines nothing. |
| `city_delete_description`, `city_deleted`, `city_installed`, `dataset_imported`, `dataset_deleted`, `settings_place_cleared`, `settings_city_description`, `journey_bikes_at_departure` | `Ezabatu datuak: %1$s`, `Instalatuta: %1$s`, `Gailuan instalatuta: %1$s`, `Irteerako geltokian: %1$s` | Content descriptions, snackbars and one-line rows, where a label with a colon is shorter than a sentence and takes no ending. `dataset_imported` and `dataset_deleted` have a second reason: the dataset names differ in **number** — *maparen datuak* is plural, *helbideen aurkibidea* singular — so a verb behind them would have to agree with a word this file cannot see (*instalatu dira* against *instalatu da*). |
| `map_outside_city_message`, `map_outside_city_brief`, `city_here_body`, `city_here_installed_body`, `city_proposal_body`, `city_delete_body` | `Zure kokapena sare honek estaltzen duen eremutik kanpo dago: %1$s.`, `Sare honek ematen dio zerbitzua zauden eremuari: %1$s.` | A **demonstrative** opens the sentence — *sare honek*, which is fixed — the ergative or the genitive falls on *sare*, and the label sits behind the colon. English puts the label in subject position, which Basque cannot do without deciding between `-k` and `-ek` on a word it has never seen. |
| `journey_climb`, `journey_detail_profile_description`, `journey_own_bike_only` and the seven other summaries, `download_held_back_body`, `download_stopped_body`, `download_waiting_for_unmetered`, `storage_download_pending`, `city_detail_size_only` | `%1$s igoera`, `guztira %1$s`, `Konektatu wifi batera %1$s deskargatzeko`, `Deskargatu %1$s` | A distance or a size is a **unit symbol**, and each symbol would take a different ending — *km-ko*, *m-ko*, *MB-tan*, *GB-tan*. None is ever declined here: the figure stands before a bare noun, or it is the object of an imperative, which takes the bare absolutive and needs nothing. *Guztira %1$s* rather than *%1$s-ko ibilbidean* is what carries the English "over". |
| `storage_downloading` | `%1$s · %2$s / %3$s` | "of" between two sizes would be an ablative on a unit symbol. A slash says the same thing and declines nothing. |
| `welcome_step` | `%1$d / %2$d` | Not *%2$detik %1$d*: a suffix glued to a numeral changes shape with **how that numeral is read aloud** — *2tik*, *5etik*, *1etik* — and the figure is not known here. `dataset_rejected_version` avoids the same trap by writing *%2$d bertsioa*, a numeral before a bare noun. |
| `settings_place_set`, `settings_place_change`, `settings_place_clear`, `dataset_delete_description` | `Ezarri %1$s`, `Ahaztu %1$s`, `Ezabatu %1$s` | **The one family where the placeholder needs no help.** The argument is the object of an imperative, and the object takes the bare absolutive in Basque: *Ezarri Etxea*, *Ezarri Lantokia*, *Ezabatu maparen datuak* are all right as they stand. |
| `stations_no_match_message`, `city_no_match_message`, `address_no_match_message`, `incoming_address_not_found`, `error_feed_unavailable`, `dataset_rejected_format`, `dataset_rejected_not_data`, `dataset_delete_body` | `Ez dago «%1$s» testuarekin bat datorrenik.`, `Sareak ez du «%1$s» jarioa argitaratzen.` | What was typed, or a feed's or a file's name, is held at arm's length inside « » and the case falls on a noun of this file's own that follows it — *testuarekin*, *jarioa*, *fitxategia*, *datuak*. |

**`city_delete_description`, `city_delete_body`, `city_deleted` and
`city_proposal_body` are handed the NETWORK's name, not the city's** —
`CityAdapter.kt:137`, `CityFragment.kt:280` and `:342` all pass
`city.displayName`, and 328 of the 331 catalogue entries carry a `displayName`
of their own; every Czech network is called *nextbike*. Writing *%1$s hiriaren
datuak* would have made a Brno reader read "the data of the city nextbike".
None of the four says *hiri*, and the English never does either.

Do not confuse them with `city_here_body`, `city_here_installed_body`,
`map_outside_city_message` and `map_outside_city_brief`, which are handed
`cityLabel(...)` — network *and* city, *Bilbaobizi – Bilbo* —
from `MainActivity.kt:657`, `MapFragment.kt:1218` and the map's own callers.
Neither group may borrow the other's wording.

## Plurals: two categories, one reading

Basque's CLDR categories are `one` and `other`, and **a noun counted by a
numeral stays in the indefinite singular**: *1 bizikleta*, *3 bizikleta*,
*20 bizikleta* — never *3 bizikletak*. So both items of every `<plurals>` here
carry the same text.

Android's own Basque writes its plurals the same way, which is the check on
this rule rather than a matter of taste:
`settings:accessibilty_autoclick_delay_unit_second` is
`{count,plural, =1{{time} segundo}other{{time} segundo}}`, and
`android:duration_hours_relative` is `=1{Duela # ordu}other{Duela # ordu}`.

Both items are still written out, because `one` is what a count of 1 resolves
to and a wrong `one` would be read every time a station holds a single bike.
Neither may be removed: the scaffolding is CLDR's, not this file's.

`counterpart_bikes` and `counterpart_docks` are the same rule seen from the
other side. They are labels posed beside — or under — a figure they must not
write themselves, and in Basque neither position is at risk: *bizikleta* and
*leku libre* read the same way with a figure above them as beside them, because
a numeral leaves the noun alone. Romanian's problem (a "de" that has nowhere to
stand under a figure) has no Basque equivalent.

## The address prompt

`address_search_hint` is **« Kalea, zenbakia, herria »** — street, then house
number, then town, which is the order the Basque Country writes an address in:
*Ercilla kalea 25, Bilbo*. `AddressQuery.parseQuery` has read a house number
standing **between the street and the town** since the pilot, precisely so that
each language may write this line in its own order rather than in English's.

*Herria* rather than *hiria*: the index carries villages as well as cities, and
*hiria* would be wrong for them. *Herri* is also the word an address line
actually uses in Basque.

**The postcode is deliberately not invited, and the reason is prompt economy,
not parsing.** Spain writes five-digit postcodes — 48001, 31001 — which is
exactly what `looksLikePostcode` filters (`POSTCODE_LENGTH == 5`), and the
postcode is stripped **before** the parser looks for a house number, so
*Ercilla kalea 25, 48001 Bilbo* keeps its door number. Basque addresses are one
of the cases the parser handles cleanly. The reason it stays out of the prompt
is the other end of the same code: a stripped postcode narrows nothing, because
the index does not hold it in full text. A three-word prompt should not invite a
fourth thing the search discards. Whoever revisits this line should weigh it as
Finnish's question and not as German's.

**The layout of an address is not this file's business** — that lives in
`core/.../address/AddressLayout.kt`, keyed on the language of the address base
(SPEC §4.3), and this language decides only the words around an address.

**There is a gap there, and it is reported rather than fixed.** `LAYOUTS` holds
`fr, de, es, pt, it, nl, pl, cs, sk, da, fi, sl, hr, ro, sv, nb, el, hu, bs, lt, lv, sq, tr`
and **no `eu`**, while `config/cities/bilbao.json` declares
`"defaultLanguage": "eu"`. A Basque address base therefore falls on
`DEFAULT_LAYOUT` — number first, spaced — and Bilbao prints *25 Ercilla kalea*,
which is neither Basque order nor anything a reader in Bilbao would take for an
address. What the entry should say, when whoever owns that file writes it:
`numberComesFirst = false, streetSeparator = " ", suffixSeparator = " "` —
Basque puts the type at the end of the name and the number after the whole of
it (*Sabino Arana etorbidea 12*), with no comma, and a repetition mark is spaced
(*12 bis*), as Spain writes it.

`config/address-normalization/eu.json` already exists and is correct: Basque
glues the street type to the end of the name (*Ercilla kalea*, *Sabino Arana
etorbidea*), so there is no head to detach and `streetTypes` is empty on
purpose.

## The vocabulary

| English | Basque | Why |
|---|---|---|
| journey | **ibilbide** | The whole door-to-door thing: the screen title, the settings section, the button, every failure message. It is what Basque journey planners call a planned trip, and it is short, which `journey_compute` and `journey_frame` need. |
| journey data (privacy) | **bidaia** | The one place *ibilbide* is deliberately not used, and it holds across both sentences that promise nothing is kept: `welcome_privacy_body` (*ez zure bidaiak*) and `about_privacy_body` (*helbideak, bidaiak eta zure kokapena*). *Ez zure ibilbideak* would collide head-on with `dataset_routing`, which **is** called *Ibilbideen datuak* and **is** stored on the device — the sentence would say the opposite of the truth on a screen two taps away. Do not "correct" either back to *ibilbide*. |
| route (the line on the ground) | **bide** | Only in `journey_no_route`: *Ez dago bi puntu hauen artean egin daitekeen biderik*. This is why *ibilbide* is left free for the planned journey and *bide* is spent here. |
| ride (the act) | **ibilaldi** | `journey_computing_own_bike`, and the adverb *bizikletaz* in the steps and the summary. |
| ride (the leg as an object) | **bizikletazko zatia** | `journey_detail_profile` and `journey_detail_profile_description`, where the ride is a stretch with a length and a gradient rather than an activity. Never *ibilbide*, or the elevation drawing stops being about a part of the journey and starts being about the whole. |
| walking / on foot | **oinez** | `journey_step_*`, `journey_summary`, `journey_walk_only`, and *Oinezko erritmoa* for the pace. |
| station | **geltoki** | A bike-share station. |
| station (railway) | **tren-geltoki** | `address_search_prompt_message` means railway stations by "stations", and says so in its comment. Writing *geltokiak* there would have offered to search for bike stations, the one thing that screen does not do. Basque has a separate compound, so *geltoki* stays the bike-share one everywhere else with nothing to manage. |
| bike | **bizikleta** | — |
| dock (free) | **leku libre** | What a bike is returned into, counted as available: `docks_available`, `counterpart_docks`, `mode_docks`, `journey_no_dock_nearby`, `settings_map_filters_hide_empty_hint`. |
| dock (capacity) | **ainguraleku** | The same object counted as a total, which is a different figure standing beside the first on the same sheet: *12 leku libre · 30 ainguraleku*. English says "dock" for both; Basque does not have to, and *ainguraleku* is the anchor point a bike locks into. Used by `station_detail_with_capacity` and `docks_total` only. |
| dock | *never* **aparkaleku** | A car park's word, and eighteen characters wide in *aparkaleku libreak*, on a label that shares a row with a station's name. |
| storage, free space (of the device) | **memoria** | `storage_title`, `error_local_storage*`, `dataset_rejected_transfer`. **This is where the entry above had to give way**: *leku libre* on a screen about disk space would have said "free dock". Android's own word is *Memoria* (`settings:storage_label`), and Android writes *Ez dago behar adina memoria* (`settings:insufficient_storage`) for exactly this. `settings:storage_settings` offers *Biltegiratzeko tokia* as well; it is three words for a screen title and it spends *toki*, which this file owes to `settings_section_places`. |
| the storage screen | **Memoria pantailatik** | Three strings send the reader there — `map_needs_tiles_message`, `journey_graph_missing`, `address_needs_index_message` — and all three name it by its own title (`storage_title`), the way Android writes *Ezarpenak → Aplikazioak*. |
| free up space | **libratu memoria** | `error_local_storage_download`. Android's *Egin tokia* (`settings:storage_free_up_space_title`, `android:low_memory`) is the citation and is **not** used: it spends *toki*, which is *My places* two screens away. *Libratu memoria* names the same act on the noun this file already owns. |
| place (home, work, a point on the map) | **toki** | `settings_section_places` (*Nire tokiak*), `place_sheet_title` (*Toki hau*), `incoming_place_default_label` (*Jasotako tokia*), `map_outside_city_title` (*Beste toki batean zaude*). *Toki* and *leku* are near-synonyms in Basque; splitting them is what keeps *Nire tokiak* from reading as a list of docking points. |
| bike, mechanical | **arrunta** | *Arrunta* against *elektrikoa* is what Basque says of a bicycle without a motor. *Mekanikoa* is an engineering word and reads as machinery. Plural on the map toggle (*Arruntak*), singular where one bike is being chosen (*Arrunta*). |
| bike, electric | **elektrikoa** | Likewise. `journey_bike_kind_electric_description` says *Laguntza elektrikoa duen bizikleta* so that "electric" cannot be read as a moped. |
| the two counts side by side | **%1$d arrunt · %1$d elektriko** | Elliptical on purpose: *6 arrunt* stands for *6 bizikleta arrunt*, and the noun is written once already on the line above. Indefinite singular after the numeral, as everything counted in this file is. |
| pace (walking) | **erritmo**; brisk = **bizkorra** | A pace is not a speed, which `values/strings.xml` says above the string: *abiadura* is the figure nobody has measured about themselves and is not used. *Motela* is Android's own (`settings:speed_label_slow`); *Bizkorra* is Android's word for "Fast" (`settings:speed_label_fast`) and is also the ordinary Basque for a brisk walk. *Normala* rather than *Arrunta*, which this file has already spent on the mechanical bike. |
| Delete / Remove | **Ezabatu** / **Kendu** | Two words, and Android itself keeps them apart: *Ezabatu* (`android:delete`, `settings:delete`) destroys, *Kendu* (`settings:remove`) takes out of a list — which is what `station_favourite_remove` does to a favourite (*Kendu gogokoetatik*) and what `place_clear` does to a point on the map. |
| Clear (a search) | **Garbitu** | Emptying a field, Android's own verb (`settings:clear`). One wording serves the icon inside the field and the button in the empty state alike. |
| Refresh / Updated | **Eguneratu / Eguneratua** | One family, so that the button and what it produces read as one thing: *Eguneratu* on the button, *Eguneratua oraintxe* under the data, *Ez da inoiz eguneratu* when there is none. `freshness_fresh` keeps the participle where Android's own *Azken eguneratzea: %1$s* (`settings:abc_slice_updated`) makes a colon label of it: the argument here is an adverbial phrase (*duela 5 minutu*) that takes no case, and the pill it is written in is narrow. |
| Check for updates | **Bilatu eguneratzeak** | Android's own (`android:unsupported_compile_sdk_check_update`, `android:deprecated_target_sdk_app_store`). |
| Update available | **Eguneratu egin daiteke** | Android's own, whole (`settings:android_version_pending_update_summary`). It is a clause where the English is a noun phrase, and it is what the phone shows for the same fact. |
| Try again | **Saiatu berriro** | Android's own (`settings:retry`, `android:lockscreen_password_wrong`). *Saiatu geroago* where the English adds "later" (`android:httpErrorIO`). |
| Continue | **Aurrera** | Android's button word (`settings:wfc_disclaimer_agree_button_text`, *AURRERA*). Shorter than *Egin aurrera* (`android:autofill_continue_yes`), which is the same word with a verb in front of it, and these two are buttons on a page with a step counter beside them. |
| Skip | **Saltatu** | Android's own (`android:skip_button_label`). |
| Back | **Atzera** | Android's own, on the toolbar arrow (`android:accessibility_system_action_back_label`). |
| Cancel | **Utzi** | Android's own (`android:cancel`, `settings:cancel`). |
| Yes | **Bai** | Android's own (`settings:yes`, `android:gpsVerifYes`). |
| Choose | **Aukeratu** | Android's own (`android:activitychooserview_choose_application`, *Aukeratu aplikazio bat*). *Hautatu* is Android's verb for selecting from a set already on screen (`android:selectAll`) and is not used for a choice the reader makes freely. |
| Replace | **Ordeztu** | Android's own (`settings:vpn_replace`). |
| Import | **Inportatu** | **Not from the lexicon**: neither `framework-res` nor `Settings` carries an "Import" string to grep. *Inportatu* is the ordinary Basque for bringing a file in. |
| Forget | **Ahaztu** | Android's own (`settings:adb_device_forget`, `settings:bluetooth_unpair_dialog_forget_confirm_button`), for the button that drops a saved place. |
| Tap | **Sakatu … -tzeko** | Android's verb is *Sakatu*, and *Sakatu hau … -tzeko* is its own shape for "do X and Y happens" (`android:adb_active_notification_message`, `android:app_running_notification_text`). `map_bikes_*_description` and `map_picked_place_description` use it. |
| Press and hold | **eduki sakatuta** | Android writes the gesture *eduki sakatuta* (`android:accessibility_shortcut_spoken_feedback`, `android:number_picker_increment_scroll_mode`); `favourites_reorder_hint` puts it at the head of the sentence, where an imperative belongs. |
| In use | **Erabiltzen** | On the city already selected (`city_active`). **A departure from Android, and a deliberate one.** The lexicon's "In use" is *Abian* (`android:media_route_status_in_use`) — a media route that is **running** — and on a row naming a conurbation it reads as "under way", which is a statement about the bike network rather than about which city the application is using. |
| Out of service | **Zerbitzuz kanpo** | A station that is not working (`station_out_of_service`, and `settings_map_filters_hide_out_of_service` echoes it). **A departure from Android for the same kind of reason as Finnish's**: the lexicon's translation is *Ez erabilgarri*, but its key is `settings:radioInfo_service_out` — a phone in a radio blackspot, and *ez erabilgarri* is what Android also writes for an app that will not open (`android:app_blocked_title`). *Zerbitzuz kanpo* is the phrase Basque public transport itself puts on a vehicle out of service. |
| just now | **oraintxe** | `settings:time_unit_just_now`, lower-cased because it is only ever read inside `freshness_fresh`: *Eguneratua oraintxe*. |
| Settings | **Ezarpenak** | `settings:settings_label`. Also the word `about_links_body` quotes when it spells out the system path. |
| Apps → Open by default → Add link | **Aplikazioak → Ireki modu lehenetsian → Gehitu esteka bat** | `settings:apps_dashboard_title`, `settings:launch_by_default`, `settings:app_launch_add_link`. `about_links_body` quotes a real path, so it must quote it in the words the phone shows. |
| link | **esteka** | Android's own (`android:granularity_label_link`, `android:whichOpenLinksWith` — *Ireki estekak honekin:* — and `settings:app_launch_add_link`, which `about_links_body` quotes in the same sentence, so it may not call the same thing two names). |
| Android's chooser | **Androiden hautatzailea** | *Hautatzaile* is Android's noun for a chooser (`android:accessibility_system_action_on_screen_a11y_shortcut_chooser_label`). The English says "Android's chooser" generically because an address shared as plain text and a `geo:` link raise two different sheets; the Basque keeps the same generality rather than naming one of them. |
| Display (section) | **Bistaratzea** | `settings:display_category_title`, the section that holds the theme. |
| System | **Sistema** | `settings:header_category_system`, for the theme, the units and the language alike. |
| Privacy | **Pribatutasuna** | `settings:privacy_dashboard_title`. |
| Language | **Hizkuntza** | `settings:app_locale_preference_title`. |
| Version | **Bertsioa** | `settings:vpn_version`. `about_version` is *%1$s bertsioa*, the numeral before a bare noun. |
| Licence | **Lizentzia** | `settings:license_title`. |
| Theme | **Gaia** | Android's own: *Gai iluna* (`settings:dark_ui_mode`), *Gailuaren gaia* (`settings:device_theme`). Dark is **Iluna**, from the same key. |
| Light (theme) | **Argia** | **Not from the lexicon**: Android's Basque names only the dark theme. *Argia* is the ordinary Basque opposite of *iluna* and is what `settings:color_inversion_feature_summary` uses of a screen (*pantaila argiak*). |
| About | **Aplikazioari buruz** | **No lexicon row applies**: Android's *Telefonoari buruz* (`settings:about_settings`) is the **phone's** about screen, and `settings:category_name_about_satellite_messaging` shows the same *…(r)i buruz* frame used of something else. This is that frame made for an application. |
| location, position | **kokapena** | English has two words here and Basque has one, so the file uses one: *Nire kokapena*, *Zure kokapena bilatzen…*, *Aurkitu nire kokapena*, *gutxi gorabeherako kokapena*. **Kokapena** is also Android's own word for the system feature and the permission (`android:permgrouplab_location`, `settings:location_settings_title`). |
| network (bike-share, and the data connection alike) | **sarea** | Basque has one word where Finnish has two, and Android uses it for the connection (`settings:network_operator_category`, *Sare mugikorra*). The two are told apart by what stands beside them: *sarearen zerbitzaria*, *sarearen jarioa* is the operator; *sare mugikorra*, *wifi-sarea* is the connection. Where only the connection is meant and no qualifier fits, the file says **konexioa** (`error_offline`, `download_can_resume`, `download_held_back_body`). |
| Wi-Fi | **wifi** | Android's Basque naturalises it — *Wifia* (`settings:wifi`, `settings:wifi_settings`) — so it is written lower case and declined like a Basque noun: *wifi bidez*, *wifi batera*, *Wifiaren zain*. |
| unmetered / metered | **neurtu gabeko sarea / sare neurtua** | What Android labels a connection (`settings:wifi_unmetered_label`, `settings:wifi_metered_label`). **The switch itself no longer says it**: it reads *Deskargatu wifi bidez soilik*, as the English now does, Wi-Fi being the word every reader has. The pair stays here as the name of what the code reads. |
| may be billed | **ordaindu behar izan daiteke** | Never the flat *ordaintzen da*. The phone reads only that the connection declares itself metered; what the plan behind it charges, no device can know. *Baliteke … ordaindu behar izatea* is Android's own shape for "Charges may apply" (`android:network_switch_metered_detail`), and it is what `download_held_back_body` and `download_stopped_body` use. `download_held_back_title` names the situation for the same reason — *Deskarga sare mugikorrean*, not a verdict on the connection — and `download_can_resume` drops the modal because there the device does state what it knows: *Konexio hau ez da jada sare mugikorra*. |
| conurbation | **hiri-eremua** | The city screen serves a metropolitan area rather than a municipality, and *hiria* is kept for the shorter word the settings section and the title need. |
| town (address) | **herria** | See the address prompt above. |
| dataset | **datu-multzoa** | The unit the storage screen installs and deletes. |
| map data / tiles | **maparen datuak** | The name the storage screen gives the dataset, and the name every other string uses for it — including `map_needs_tiles_title`, which **drops the English "tiles"** rather than introduce *lauza* on one screen for a thing called *maparen datuak* everywhere else. |
| routing data | **ibilbideen datuak** | — |
| address index | **helbideen aurkibidea** | An index one looks a name up in. *Erregistroa* is what the state keeps; *datu-basea* says how it is stored, which is not the reader's business. |
| offline data | **lineaz kanpoko datuak** | `settings_section_data`, `storage_open`, `city_delete_body`, `incoming_needs_index`. |
| feed | **jarioa** | `welcome_fleet_body`, `error_malformed`, `error_feed_unavailable`. |
| climb | **igoera** | Metres gained, on a leg or over a whole journey, written after its figure and before nothing: *120 m igoera*, which keeps every ending off the unit symbol. |
| minute / hour (duration) | **min** / **h** | `duration_minutes` is *%1$d min*, the abbreviation Android itself writes (`android:minutes`, `android:duration_minutes_medium`), and `duration_hours_minutes` is *%1$d h %2$02d* — every space in it non-breaking, since it is one reading of a clock. |
| Home (the place) | **Etxea** | Where the reader lives, named by them (`settings_place_home`, `journey_source_home`, the same word in both). **Never *Hasiera* or *Pantaila nagusia***: an application's opening screen is what Android calls *Pantaila nagusia* (`android:accessibility_system_action_home_label`), and it is not a place one cycles home to. |
| Work (the place) | **Lantokia** | Where they work (`settings_place_work`, `journey_source_work`). Android's own for the workplace as an address (`android:emailTypeWork`), against *Lanekoa* (`android:profile_label_work`), which qualifies a profile rather than naming a point on a map. |
| My places | **Nire tokiak** | The settings section that holds the two (`settings_section_places`); *toki* is the noun `place_sheet_title` uses as well. |
| outside the city served | **Zerbitzatzen den hiritik kanpo** | Under a place the conurbation in use does not cover (`settings_place_outside_city`), in the same frame as `station_beyond_area` and `journey_outside_coverage`: *…tik kanpo*. The application serves several conurbations side by side, so an *Etxea* named in Bayonne is out of reach while Bilbao is in use. The place is kept and stays erasable — out of reach, not wrong. |
| this place | **Toki hau** | `place_sheet_title`, the sheet a point found on the map opens — the same noun as *Nire tokiak*. `place_clear` is *Kendu puntu hau*, on *kendu*, as the Delete/Remove row above says. |
| Go there / Leave from here | **Joan hara / Abiatu hemendik** | The place sheet's two actions take the station sheet's own words: `station_as_origin` is already *Abiatu hemendik*, word for word, and `station_as_destination` *Joan hona*, of which *Joan hara* is the same verb with the deictic moved. The place sheet is the station sheet's sister and the two must read as one, so neither pair is reworded without the other. |
| Face north and lay the map flat | **Jarri iparraldera begira eta lautu mapa** | `map_face_north`, on the compass, which shows while the map is turned or tilted, and one press gives back both at once — the north and the flat. Imperative, as this file's controls are. |
| tilt / turn | **okertu / biratu** | Two verbs kept apart, so that the four gestures of `map_description` stay four: *biratu* is the turn around north, *okertu* the tilt away from flat. `map_tilted_description` is *Mapa okertuta dago.*, and `map_face_north` says *lautu* for laying it flat again. `map_bearing_description` counts the turn in *gradu*, singular after the numeral, which is why its two forms are the same sentence. |
| what's new | **Berritasunak** | What the screen shows is the release notes. *Nobedadeak* is the loan a Basque speaker would also recognise; *Berritasunak* is the batua word and is what this file writes. |
| tracker | **jarraipen-tresna** | Used in the interface and in the store texts alike, so the promise reads the same before and after installing. |
| free software / open source | **software librea / kode irekikoa** | `welcome_hello_body` and `about_licence_body`. |

## Words that are not translated

Product and network names — Roue Libre, Bilbaobizi, Vélib’, Vélo’v, Citi Bike,
BRouter, MapLibre, OpenStreetMap, GBFS, Base Adresse Nationale, GeoNames — and
the licence names. The `resources` `name` attributes, always.

**The unit symbols stay international.** Distance: `m`, `km`, `ft`, `yd`, `mi`.
Duration: `min`, `h`. Bytes: `B`, `kB`, `MB`, `GB` — Basque writes them as the
rest of Europe does, and `android:byteShort` confirms the `B`. So `size_bytes`,
`size_kilobytes`, `size_megabytes`, `size_gigabytes` and the five distances come
back identical to the English, deliberately.

The format-only strings are untouched too — `station_content_description`,
`station_bikes_split`, `station_capacity_and_age`, `address_locality`,
`address_detail`, `address_content_description`, `dataset_installed`,
`storage_download_failed`, `incoming_address_choice`, `settings_place_description`,
`counterpart_none`, `settings_units_metric` / `_us` / `_uk` — whose punctuation
is already what Basque uses. The single exception is `city_label`, whose em dash
becomes the en dash this file writes.

## Nothing comes back identical to the English by accident

`python3 tools/check_translations.py eu` reports **complete and consistent**:
no placeholder changed number or kind, the plural categories are Basque's two,
and nothing it can judge came back in English.

Twenty-seven resources are byte-identical to the source all the same, and every
one of them is meant to be: `app_name` and `welcome_hello_title` (the
application's name), the fourteen format-only strings and unit-symbol pairs
listed above, and the eleven distance, size and duration frames. Not one of them
holds a word of English.

## Store texts

`fastlane/metadata/android/eu/` holds the same vocabulary as the interface —
*ibilbide*, *geltoki*, *leku libre*, *ainguraleku*, *arrunta* / *elektrikoa*,
*hiri-eremua*, *datu-multzoa*, *lineaz kanpoko datuak*, *Memoria pantailatik* —
and the same *ibilbide* / *bidaia* split in the privacy bullet: *Ez da bidaien
daturik gordetzen*, never *ibilbideen*, for the reason the vocabulary table
gives.

The short description is **exactly 80 characters**, the store's limit:
*Bizikleta partekatuak: mapa eta ibilbideak lineaz kanpo, jarraipen-tresnarik
ez.* Nothing shorter keeps both the product's own wording
(*partekatutako bizikletak*, from `welcome_hello_body`) and the interface's
noun for a tracker; the alternative measured at 74 spends *jarraipena*, the
act, where the interface names the thing.

**The three changelogs are history and are translated as such.** `3.txt`
describes the download setting in the categorical form that version shipped —
*megabyteka fakturatzen ez den konexio baten zain*, *Konexioa bidean
fakturatzen hasten bada* — and the *ordaindu behar izan daiteke* of today's
`strings.xml` is deliberately **not** applied to it: the hedge is a later
change, and a release note that rewrote its own past would be wrong about
what the application did.

City names take their Basque form where one exists — *Bartzelona*,
*Kopenhage*, *Praga*, *Tokio*, *Ameriketako Estatu Batuak*, *Erresuma Batua* —
and keep their own where none does: *Paris*, *New York*, *Riga*, *Pristina*,
*Lille*, *Lyon*, *Auray*, *Boston*. Network names are never translated. Where a
name would have had to take a case ending that its spelling does not settle,
the sentence avoids it: the last line of `3.txt` reads *Auray 3 MB, Paris
143 MB* rather than *Aurayrentzat*.
