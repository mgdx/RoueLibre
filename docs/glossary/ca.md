# Catalan glossary

The terms `res/values-ca/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Catalan ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Register, territory and typography

The Catalan says **tu** (SPEC §9), and that is Android's own answer rather than
a house style. The system's Catalan addresses one person in the singular
throughout — *Toca el sensor* (`settings:tap_to_wake`, *Toca per activar*),
*Tria una funció* (`android:accessibility_button_prompt_text`),
*Torna-ho a provar* (`android:face_error_lockout`), *la teva ubicació*
(`android:permdesc_accessFineLocation*`) — and the polite *vós* that older
Catalan localisation guides ask for survives in about a dozen strings of the
whole system, all of them written before the current style: 663 strings in
`framework-res.apk` and `Settings.apk` carry a singular imperative against
eleven carrying the plural one. *Vostè* appears nowhere. An application that
answered in *vós* would read as a translation from a form nobody's phone uses.

**A control and an instruction therefore take the same form**, the singular
imperative — *Cancel·la*, *Suprimeix*, *Actualitza*, *Cerca*, *Instal·la* on
the buttons, and *Instal·la l'índex des de la pantalla d'emmagatzematge* in the
sentence beneath. There is one register in this file, not two. What belongs to
the reader stays in the first person, as the English has it: *La meva ubicació*,
*Els meus llocs*, *Tria la meva ciutat*, *Amb la meva bici*.

**One Catalan for the whole catalogue.** It serves Catalonia, the Valencian
Country and the Balearic Islands alike, so nothing here is written for one of
the three alone: the words kept are the ones the language shares. *Cercar* over
*buscar*, *fitxer* over *arxiu*, *baixar* over *descarregar* — each is the
general form and the one Android's Catalan uses, not a Barcelona preference.
Where Android already has a word, that word wins: an application calling the
settings anything but **Configuració** (`settings:settings_label`) reads as a
foreign one. The arbiter throughout is the system lexicon extracted from
`framework-res.apk` and `Settings.apk`.

Typography:

- The apostrophe is **’** (U+2019), never the straight quote, so **nothing in
  the file is escaped**. Catalan elides at every turn — *l'aplicació*,
  *d'adreces*, *s'ha aturat* — and a body still holding `\'` is one that has
  the wrong character in it.
- The geminate l is written with the **middle dot U+00B7**: *cancel·la*,
  *instal·lat*, *il·legible*, *col·laboradors*. Never a full stop, never a
  hyphen, never a bare `ll`.
- Quotations are **« »**, with **no space inside them** — that is French's
  rule, not Catalan's. No space stands before `:`, `;`, `?` or `!` either.
- Numbers keep the decimal comma and the thousands point: "42,5 MB", "1,3 GB".
  The size units are the international symbols — B, kB, MB, GB — since Catalan
  says *byte*.
- A figure and the unit naming it are joined by a **non-breaking space**, in
  the distances, the sizes, the durations and every plural that counts
  something. `UnitTypographyTest` holds every language to it.

## The vocabulary

| English | Catalan | Why |
|---|---|---|
| journey | ruta | The whole door-to-door thing: the screen, the settings section, the button. It is the word Catalan mapping applications use for a computed trip, and it is short enough for a section title. |
| ride | trajecte en bici | The bike leg alone, inside a journey. A different word from *ruta*, so "the ride, uphill and down" and "the journey in detail" stay about two different objects. |
| route | camí | Only in `journey_no_route`: the line on the ground, not the planned journey — "No hi ha cap camí practicable entre aquests dos punts". |
| to ride | pedalar | The verb of the bike leg — *Pedala fins a…* — against *caminar* for the walking ones. The noun in `journey_bike_kind_electric_description` is *pedaleig*: "amb assistència al pedaleig". |
| station | estació | A bike-share station. Catalan uses the same word for a railway station, which is what `address_search_prompt_message` means; there it is written out as **estació de tren** so the two cannot be confused. |
| bike | bici, bicis | Not *bicicleta*. It is what the Catalan networks call their own vehicles, it fits in a list row and on a station's sheet where *bicicletes* does not, and it is **feminine**, so *mecànica* and *elèctrica* agree with it in every string that names a kind. |
| dock (free) | espai lliure | What a bike is returned into, counted as available: the map's toggle, the counterpart label, the station sheet. Shorter than *ancoratge lliure* on a toggle that already carries an icon, and *plaça* would collide with the town squares of the address search. |
| dock (capacity) | ancoratge | The same object counted as a total, which is a different figure on the screen: "12 espais lliures · 30 ancoratges". English says "dock" for both; Catalan does not have to, and *ancoratge* is the networks' own word for the physical point. |
| dock | *never* «borne», *never* «terminal» | A terminal is what one pays at, not the point a bike attaches to. |
| bike, electric | elèctrica | Pedal-assist, never a moped: `journey_bike_kind_electric_description` says *amb assistència al pedaleig* in full. |
| pace (walking) | ritme a peu | A pace is not a speed: `values/strings.xml` says so above the string, and *velocitat* would say the opposite. *Ritme* is masculine, hence *Lent / Normal / Ràpid*. The wording also chains with *els trams a peu d'una ruta* just below it. |
| climb | desnivell | The metres climbed over a leg or a journey. |
| leg (of a journey) | tram | "The walking parts of a journey" — *els trams a peu d'una ruta*. |
| Settings | Configuració | Android's own word (`settings:settings_label`, `android:global_action_settings`), including in the system path quoted in `about_links_body`. Never *Ajustos*, which is Spanish's *Ajustes* wearing a Catalan ending. |
| Search | Cerca | Android's own verb, on the control and in the field hints (`android:search_go`, `android:find`). *Cerca actualitzacions* is Android's own line too (`android:deprecated_target_sdk_app_store`), word for word what `storage_check_updates` says. Never *buscar*, which is the spoken word and not the interface one. |
| Clear | Esborra | Android's word for emptying a field (`settings:clear`), which is what "clear the search" does — kept apart from *Suprimeix*, which destroys data. `place_clear` is *Esborra aquest punt* for the same reason. |
| Refresh | Actualitza | Android's word for data (`android:autofill_update_yes`, `settings:nfc_payment_btn_text_update`). |
| Try again | Torna-ho a provar | Android's own wording, on the button and inside the sentences alike (`android:face_error_lockout`: *Massa intents. Torna-ho a provar més tard.*). |
| Back | Enrere | Android's word on the toolbar's back arrow (`android:back_button_label`, `settings:back`). |
| Tap | Toca | Android's own verb (`settings:tap_to_wake`, `settings:accessibility_shortcut_edit_dialog_summary_floating_button`), in the singular like the rest. |
| Press and hold | Mantén premuda | Android's own wording for a long press (`settings:assistant_long_press_home_gesture_title`, `android:content_description_sliding_handle`); feminine here because it is a *fila* that is held. |
| Delete / Remove | Suprimeix / Treu | *Suprimeix* is Android's (`android:delete`) and destroys data — a city, a dataset. *Treu* takes a station out of the favourites and destroys nothing; the lexicon has no word for that gesture, and *Elimina* would be a third synonym for deleting. |
| Skip | Omet | Android's word (`android:skip_button_label`). |
| Show / See | Mostra | Android's own (`settings:condition_expand_show`), and one verb for one gesture over the *Veure* an infinitive style would give: *Mostra el mapa*, *Mostra els preferits*, *Mostra les novetats*, *Mostra la ruta en detall*, and `incoming_show_me`. Its opposite on the map filters is Android's *Amaga* (`settings:condition_expand_hide`). |
| Add | Afegeix | Android's own (`settings:add`), on the favourites and in the quoted system path (`settings:app_launch_add_link`, *Afegeix un enllaç*). |
| Install | Instal·la | Same form on the button and in the sentence, the register being one. |
| Download | Baixa, baixada | Android's own pair (`settings:filter_apps_third_party` = *Baixades*, `android:install_carrier_app_notification_button` = *Baixa l'aplicació*), over *descarregar*, which is longer and not the system's. |
| About | Quant a | The lexicon has only *Informació del telèfon* (`settings:about_settings`), which is about the device; an application's about screen is *Quant a*, which is what F-Droid's Catalan and the stores call it. |
| Favourites | Preferits | The lexicon has neither *Preferits* nor *Favorits* for starred items; *Preferits* is the term Catalan applications and Softcatalà use, and it is what a reader will have met in a browser. |
| In use | En ús | Android's own (`settings:wifi_display_status_in_use`), on the city already installed. |
| Out of service | Fora de servei | Android's own (`settings:radioInfo_service_out`). |
| just now | ara mateix | Reads as one phrase with *Actualitzat %1$s*. |
| Update available | Actualització disponible | Android's own, whole (`settings:android_version_pending_update_summary`). |
| Replace | Substitueix | Android's own (`settings:vpn_replace`). |
| Yes | Sí | Android's own (`settings:yes`). |
| Language | Idioma | Android's own on the per-application row (`settings:app_locale_preference_title`), and *Idioma del sistema* (`settings:preference_of_system_locale_title`) is what `settings_language_system` shortens to *Sistema*. |
| Display (section) | Visualització | Android's own name for the section that holds the theme (`settings:display_category_title`). *Pantalla* is the panel of glass, which is not what the section is about. |
| Theme | Tema | Android's own (`settings:dark_ui_mode` = *Tema fosc*, `settings:device_theme` = *Tema del dispositiu*). Light / Dark are **Clar** / **Fosc**. |
| Storage | Emmagatzematge | Android's own (`settings:storage_settings`), and the name the screen carries. |
| Privacy | Privadesa | Android's own (`settings:privacy_dashboard_title`). |
| Location / position | ubicació | Android's own on the permission and on the button alike (`android:permgrouplab_location`, `settings:location_settings_title`). Never *posició*. |
| app | aplicació | Android's own (`settings:apps_dashboard_title` = *Aplicacions*). Never *app*. |
| offline | sense connexió | Both as a qualifier — *dades sense connexió* — and as a state. |
| conurbation | àrea metropolitana | Neutral where *aglomeració* is a calque and *conurbació* is a planner's word. |
| town (in an address) | població | The word Catalan address forms use, in all three territories, where *localitat* is administrative and *municipi* is the unit and not the place one writes down. |
| file | fitxer | Android's own (`android:upload_file` = *Tria un fitxer*). *Arxiu* is a place documents are kept, not a file on a disc. |
| feed (GBFS) | flux | The network's published stream, in the errors and in the welcome. |
| tracker | rastrejador | `about_privacy_body` and the welcome. |
| map data / tiles | mapa base | The name the storage screen gives the dataset, and the one every other string must use for it — including `map_needs_tiles_title`. |
| routing data | graf de rutes | The project's own term, used in `journey_graph_missing` too. More technical than the English "routing data", and kept deliberately: it is one object with one name. |
| address index | índex d'adreces | — |
| dataset | conjunt de dades | — |
| metered connection | connexió d'ús mesurat | Android's own wording for what the code actually reads (`settings:wifi_metered_label` = *D'ús mesurat*). **The switch no longer says it**: it reads *Baixa només amb Wi-Fi*, as the English now does, because Wi-Fi is the word every reader already has. The pair stays here as the name of the concept, and the line under the switch and the two dialogs explain it in ordinary words — *facturar-se per megabyte*, *compta com una xarxa mòbil*. |
| may be billed | es pot facturar | Never *es factura*. The phone reads only that the connection declares itself metered; what the plan behind it charges, no device can know. So `download_unmetered_only_description`, `download_held_back_body` and `download_stopped_body` all carry *es pot facturar*, and `download_held_back_title` names the situation — *Baixada en una xarxa mòbil* — rather than passing a verdict on the connection. `download_can_resume` drops the modal because there the device does state what it knows: *ja no és una xarxa mòbil*. |
| by default | per defecte | For the application's own setting (`settings_opening_title`). The one place Android's own **de manera predeterminada** appears is `about_links_body`, which quotes the system path *Obre de manera predeterminada* (`settings:launch_by_default`) and has to match the reader's screen word for word. |
| Home (the place) | Casa | Where the reader lives, named by them (`settings_place_home`, `journey_source_home`, the same word in both). **Never *Inici***, which is an application's first screen and not a place one rides home to. |
| Work (the place) | Feina | Where they work (`settings_place_work`, `journey_source_work`) — the word Android uses for the same thing (`android:managed_profile_label_badge` = *%1$s de la feina*). The place, not the occupation, and it stands beside *Casa* under *Els meus llocs*, which settles the reading. *Lloc de treball* is three times the width on a row that already carries an address. |
| My places | Els meus llocs | The settings section that holds the two (`settings_section_places`). *Lloc* is the noun `place_sheet_title` uses as well. |
| outside the city served | Fora de la ciutat coberta | Under a place the conurbation in use does not cover (`settings_place_outside_city`). *Fora de* and *cobrir* are what `station_beyond_area` already says. The application serves several conurbations side by side: a *Casa* named in Palma is out of reach while Barcelona is in use. The place is kept and stays erasable — out of reach, not wrong. |
| Face north and lay the map flat | Orienta al nord i aplana el mapa | `map_face_north`, on the compass, which shows while the map is turned or tilted, and one press gives back both at once. |
| tilt / turn / lay flat | inclinar / girar / aplanar | Three verbs kept apart so that the four gestures of `map_description` stay four: *moure*, *acostar o allunyar*, *fer girar*, *inclinar*. `map_tilted_description` is «El mapa està inclinat.», `map_bearing_description` counts the turn in *graus*, and `map_face_north` says *aplana* for laying it flat again. |
| this place | Aquest lloc | `place_sheet_title`, the sheet a point found on the map opens — the same noun as *Els meus llocs*. |
| Go there / Leave from here | Vés-hi / Surt d'aquí | The place sheet's two actions take the station sheet's own words, word for word: `station_as_destination` is *Vés-hi* and `station_as_origin` *Surt d'aquí*. English writes four labels where Catalan has two verbs; the sheets are sisters and must read as one, so neither pair is reworded without the other. |

The three dataset names are all **masculine singular** — *mapa base*, *graf de
rutes*, *índex d'adreces* — which is what lets `dataset_imported`,
`dataset_deleted` and `dataset_absent` agree once for all three
("%1$s instal·lat", "%1$s suprimit", "No instal·lat").

The two named places are both **feminine** — *Casa*, *Feina* — which is what
lets `settings_place_cleared` say "S'ha oblidat %1$s" without knowing which of
the two it was handed.

## Elision around a placeholder

Catalan elides *de* to *d'* before a vowel, and no string can know in advance
whether it will be handed Barcelona or Amsterdam. Wherever a **city name**
would have followed a bare *de*, the sentence was rewritten so that it does
not:

| Key | English | Catalan |
|---|---|---|
| `city_delete_description` | Delete the data for %1$s | Suprimeix les dades: %1$s |
| `city_delete_body` | All offline data for %1$s will be erased… | %1$s: s'esborraran totes les dades sense connexió… |
| `city_deleted` | Data for %1$s deleted | Dades suprimides: %1$s |

The same reflex governs `city_here_body` and `city_proposal_body`, where the
placeholder is the subject or follows a verb — *%1$s dona servei a la zona on
ets*, *la zona que cobreix %1$s* — and `dataset_delete_body`, where the dataset
name is quoted and carries no preposition at all: *Les dades «%1$s»
s'esborraran*.

**One string is knowingly left with the problem in it.**
`station_address_nearby` reads *A prop de %1$s*, and %1$s is a street name that
may well begin with a vowel — *Avinguda Diagonal*. Every Romance translation in
this repository carries the same latent fault, and the alternatives are worse:
*Vora %1$s* is literary beside a street name, and a colon turns a supporting
line into a label. If somebody finds a wording that is right in both cases, it
should be changed here and in `values-fr/`, `values-es/`, `values-it/` and
`values-pt/` at once.

Two other agreements are settled by the words themselves rather than by the
sentence, and must not be undone: the three dataset names are masculine and the
two named places feminine, as noted above.

## Plurals

Catalan distinguishes **three** categories — `one`, `many`, `other` — and
`many` is not the middle of the range: it is the **de** a large number takes.
"Un milió **de** bicis", "un milió **d'**estacions", elided before a vowel like
everything else. It is only ever resolved from a million upwards, which no
count this application shows will ever reach, and it is written out all the
same: what a language distinguishes is a fact about the language, not about the
counts on the screen. `CounterpartAgreementTest` and
`tools/check_translations.py` both hold the file to the three.

Two consequences worth knowing before touching a plural here:

- The elision is decided by the **noun that follows**, so the `many` items
  differ from one plural to the next: *de bicis*, *de segons*, *de mesos*,
  *de graus* against *d'espais lliures*, *d'ancoratges*, *d'estacions*,
  *d'hores*, *d'elèctriques*.
- `counterpart_bikes` and `counterpart_docks` **hold no figure**: the disc
  beside them already does. On a list row they are set in capitals and stacked
  under that figure, so *DE BICIS* would stand alone on its line at a million —
  the price of writing the category the language actually has, and never
  reached in practice.

## The order of an address

**The order of an address is not this file's to decide, and never was.** It
belongs to the country the address is in, not to the reader's language
(SPEC §4.3): "Carrer de Mallorca, 302" is how a Barcelona address is written
for every reader of the application, and "12 rue Nationale" is how a Lyon one
is written for a Catalan reader. The layouts are a table in
`core/address/AddressLayout.kt`, keyed on the language of the **address base**.

`address_search_hint` follows the order Catalan writes: *Carrer, número,
població* — the street first, then the number, then the town — and not the
English *Number, street, town*. `address_locality` stays "%1$s %2$s", the
postcode then the town, which is the order both languages share.

## The longest strings, measured

The file runs about 17 % longer than the English overall. The labels that come
closest to their box, with the English beside them:

| Key | Catalan | ca / en |
|---|---|---|
| `settings_map_filters_hide_empty` | Amaga les estacions que no tenen res a oferir | 45 / 39 |
| `journey_navigate`, `station_open_in_navigation` | Obre en una aplicació de navegació | 34 / 24 |
| `settings_map_filters_hide_out_of_service` | Amaga les estacions fora de servei | 34 / 32 |
| `storage_open` | Gestiona les dades sense connexió | 33 / 19 |
| `settings_opening_title` | Obre l'aplicació per defecte a | 30 / 26 |
| `stations_order_by_distance` | L'estació més propera primer | 28 / 21 |
| `action_retry` | Torna-ho a provar | 17 / 9 |
| `mode_docks` | Espais lliures | 14 / 10 |

`action_retry` is the one to watch: it nearly doubles the English on a button
that sits beside `action_refresh`. It is Android's own wording all the same and
is not to be shortened to *Reintenta*, which the system does not use.
`city_here_use` was written *Fes-la servir* rather than *Fes servir aquesta
ciutat* for the same reason — a dialog's action button, four times the English
"Use it" — and it reads back to *La fem servir?* in `city_proposal_body`.

## Words that are not translated

Product and network names — Roue Libre, Bicing, Valenbisi, BiciPalma, BRouter,
MapLibre, OpenStreetMap, GBFS, Wi-Fi — the licence names, and Base Adresse
Nationale, which is the proper name of a French dataset. Unit symbols (m, km,
ft, yd, mi, min, h, B, kB, MB, GB) stay as they are. `resources` `name`
attributes, always.

## Strings that come back identical to the English, and should

`tools/check_translations.py` flags one, and it is right to ask:

- **`settings_walking_pace_normal` — "Normal".** The middle of *Lent / Normal /
  Ràpid*, and Catalan writes it exactly as English does.

The others that come back byte for byte are format templates placing nothing
but a separator (`%1$s · %2$s`, `%1$s: %2$s`, `%1$s — %2$s`, `%1$s, %2$s`), the
unit and size symbols, the three unit-system buttons (`m · km`, `ft · mi`,
`yd · mi`), and the application's own name.
