# Galician glossary

The terms `res/values-gl/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Galician ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Which Galician, and for whom

The file serves **Galicia**, where the catalogue holds two networks: A Coruña
(`config/cities/a-coruna.json`) and biciArteixo, in Arteixo
(`config/cities/arteixo.json`). It is a small territory and a large
difficulty, because of a fact no other translation in this repository has to
deal with: **every reader of this file also reads `values-es/`**, on the same
phone, the same day. Galician is read beside Spanish, not instead of it.

The consequence, and the rule this file was written under: the wrong word here
is almost never an invention. It is the Spanish one arriving unnoticed —
*ubicación*, *búsqueda*, *espacio*, *enlace*, *licencia*, *dirección* for an
address — or, more rarely, the Portuguese one, which looks close enough to pass
and is not Galician either. The section "The two neighbours" below is the list
of those, and it is the part of this file most worth reading before rewording
anything.

The norm is the **official one**, the RAG–ILG orthography (NOMIG): *servizo*,
*espazo*, *licenza*, *ao*, *ñ*, *ll*, *-ble* rather than *-bel*. Not the
reintegrationist spelling. This is not a position on the question; it is what
Galician Android, the Xunta and the schools write, and an interface spelled
otherwise would be the only thing on the phone spelled that way. A reader who
prefers the reintegrationist norm reads this file without effort all the same.

## The lexicon

`gl.tsv`, the working file this translation was arbitrated against, is the
Galician of a real phone: **8 699 rows** extracted from `framework-res.apk` and
`Settings.apk` on the FP3 (LineageOS, Android 15), with
`aapt2 dump resources` and the `(gl)` configuration. Every citation below gives
the key actually grepped, prefixed `android:` or `settings:` by the APK it came
from. Where this file departs from a row, it says so, and says why.

The lexicon is not committed — it is a device dump, not project data. It is
reproducible in two commands, which is why the counts below can be checked
rather than believed.

## Register: *ti*, and it is not a close call

The interface addresses the reader in the **second person singular** — *Toca*,
*Escolle*, *Comproba*, *a túa cidade* — and never with *vostede*.

Android's own Galician settles it, and settles it by a margin no tie-breaker is
needed for:

| | second person singular | polite third person |
|---|---|---|
| imperatives, sentence-initial, over the 20 verbs listed below | **289** | **0** |
| possessives meaning YOUR | **171** (*o teu*, *a túa*) | **0** (*o seu*, *a súa*) |
| subject pronouns | *ti* **15** | *vostede* **0** |

The 20 verbs counted are *tocar, escoller, seleccionar, probar, comprobar,
premer, introducir, engadir, abrir, volver, manter, usar, activar, desactivar,
instalar, configurar, gardar, ir, facer, arrastrar*, each in both its
second-person and its polite form, counted only at the head of a string so that
descriptive clauses cannot inflate the total.

**One hit looked like a polite imperative and is not.**
`settings:trackpad_tap_to_click` is *Toque para facer clic* — the **noun**
*toque*, "a tap", not the verb. Discount it and the count is 289 to nil. The
trap is recorded here rather than left for the next contributor to fall into,
because "right" and "right by luck" are not the same thing.

So the register is the system's, not a preference: an application saying
*Escolla a súa cidade* would be the odd one on the phone. This agrees with
`values-es/` (*tú*) and `values-fr/` (*tu*), and disagrees with `values-pt/`,
whose Android addresses its reader formally. Same method, different answer,
each following its own system.

### *O seu* is free, and that is the opposite of Portuguese

Because YOUR is *o teu / a túa* here, **`o seu / a súa` is free to mean ITS**,
and this file uses it for exactly that: *as súas bicis* (a station's), *os seus
datos* (a city's), *os seus propios datos* (a network's). Android's Galician
uses it for nothing else either — all 25 of its `o seu / a súa` rows are third
persons: *a súa duración*, *a súa posición relativa*, *a súa configuración*.

`docs/glossary/pt.md` records the opposite trap at length: `values-pt/` says
YOUR with *o seu*, so it cannot also say ITS with it, and had to rewrite a
dozen strings around *de* + noun. Galician needs none of that, and the
difference is instructive rather than a mistake on either side — it falls out
of the register, which fell out of what each system says.

## The vocabulary

| English | Galician | Why |
|---|---|---|
| journey | ruta | The whole door-to-door thing: the screen, the settings section, the button. Short enough for a section title, and the word Galician mapping services use for a computed trip. Feminine, so *toda a ruta*, *ningunha ruta*. |
| ride | traxecto en bici | The bike leg alone, inside a journey. A different word from *ruta*, so "the ride, uphill and down" and "the journey in detail" stay about two different objects. |
| route | camiño | Only in `journey_no_route`: the line on the ground, not the planned journey — *Non hai ningún camiño practicable entre estes dous puntos*. Also *ningunha ruta pode chegar* in `station_beyond_area`, where what cannot arrive is the computed thing. |
| to ride | pedalar | The verb of the bike leg — *Pedala ata…* — against *camiñar* for the walking ones. |
| station | estación | A bike-share station. Galician uses the same word for a railway station, which is exactly what `address_search_prompt_message` means; there it is written out as **estacións de tren** so the two cannot be confused. |
| bike | bici, bicis | Not *bicicleta*. It is the everyday clipping, it is in the RAG dictionary, and it fits in a list row and on a station's sheet where *bicicletas* does not — 5 characters against 10 on `mode_bikes`, which is a button on the map. Feminine, so *mecánica* and *eléctrica* agree with it everywhere in the file. |
| dock (free) | espazo libre | What a bike is returned into, counted as available. Spelled **espazo**, with the *z* the official norm writes — *espacio* is the single most visible castilianism this file could carry, and it would carry it eleven times. |
| dock (capacity) | ancoraxe | The same object counted as a total, which is a different figure on the screen: *12 espazos libres · 30 ancoraxes*. English says "dock" for both; Galician does not have to. Note the gender: Galician *-axe* is **feminine** (*a viaxe*, *a paisaxe*), unlike Spanish *-aje*; nothing in the file puts an article before it, so nothing depends on it, but a contributor adding one should write *a ancoraxe*. |
| dock | *never* «borne», *never* «terminal» | A terminal is what one pays at, not the point a bike attaches to. |
| bike, electric | eléctrica | Pedal-assist, never a moped: `journey_bike_kind_electric_description` says *con asistencia á pedalada* in full. |
| pace (walking) | ritmo ao camiñar | A pace is not a speed: `values/strings.xml` says so above the string, and *velocidade* would say the opposite. Android's own slow/fast pair is *Lenta / Rápida* (`settings:speed_label_slow`, `settings:speed_label_fast`), feminine because it agrees with *velocidade*; *ritmo* is masculine, so this file writes *Lento / Normal / Rápido*. |
| leg (of a journey) | treito | *Os treitos a pé dunha ruta*. The Galician cognate of Portuguese *trecho* and the RAG's word for a stretch of a way; *tramo* is the Spanish one. |
| climb | desnivel | The metres climbed over a leg or a journey. Galicia is not flat, so this string is read rather than theoretical. |
| Settings | Configuración | Android's own word — `settings:settings_label`, `settings:dashboard_title`, and 324 rows in all. *Axustes*, which is what Spanish Android says and therefore the word a Galician reader might expect from a translation, holds **5**. This is the clearest case in the file of the system word and the Spanish habit pointing different ways. |
| Search | Buscar | Android's own — `android:search_go`, `settings:search_menu_title`. The noun is **busca**, never *búsqueda*, which is Spanish and is not a Galician word. |
| Clear | Borrar | Android's word for emptying a field — `settings:clear` — which is what "clear the search" does. Kept apart from *Eliminar*, which destroys. |
| Refresh | Actualizar | Android's verb — `android:autofill_update_yes`. |
| Try again | Tentar de novo / Téntao de novo | Both are Android's, in their own place: the infinitive on `action_retry`, the button; the conjugated form in the sentences that ask for the gesture, which is what Android writes in running text on 37 rows (`android:face_acquired_insufficient`, `android:face_error_lockout`, `android:connected_display_unavailable_notification_content`). |
| Back | Volver | `android:back_button_label` and, more to the point, `android:accessibility_system_action_back_label` — `action_back` is the toolbar back-arrow's content description, which is exactly what that key names. Android also says *Atrás* (`settings:back`, `settings:wizard_back`), on a wizard's back button, which is not this. |
| Tap | Toca | Android's own imperative, 50 sentence-initial rows. |
| Press and hold | Mantén premida | Android's own wording (`Mantén premida`, `Mantén premido`, 6 rows each; the feminine agrees with *fila* in `favourites_reorder_hint`). |
| Delete / Remove | Eliminar / Quitar | Two gestures, two words, and Android distinguishes them the same way: *Eliminar* at `android:delete`, `android:deleteText`, `settings:delete`; *Quitar* at `settings:remove`. *Eliminar* destroys data, *Quitar* takes a station out of the favourites. |
| Skip | Omitir | Android's own — `android:skip_button_label`, `settings:skip_label`. `values-es/` chose *Saltar* over the Spanish *Omitir*; Galician Android leaves no such choice. |
| Show / Hide | Mostrar / Ocultar | Android's own pair — `settings:condition_expand_show`, `settings:condition_expand_hide`; *Ocultar* is what the two map filters say. **Ver** is used instead where a button opens a screen or a place: *Ver o mapa*, *Ver os favoritos*, *Ver as novidades*, and `incoming_show_me`. One word for one gesture. |
| About | Acerca de | Android's own construction, at `settings:about_settings` — *Acerca do teléfono*. The application's screen is *Acerca de*, the same preposition with no complement. |
| In use | En uso | Android's own wording — `android:media_route_status_in_use`. It sits on the city already installed, and it is 6 characters, the same as the English. |
| Out of service | Fóra de servizo | Android's own wording — `settings:radioInfo_service_out`. Note both diacritics: *fóra* takes its accent, *servizo* its *z*. |
| just now | agora mesmo | Android's own — `settings:time_unit_just_now`. It reads as one phrase with *Actualizado %1$s*, which is Android's own sentence as well: `settings:no_carrier_update_now_text` is **Actualizado agora mesmo**, the two strings of this file side by side. |
| Update available | Actualización dispoñible | Android writes the sentence — `settings:android_version_pending_update_summary` is *Hai dispoñible unha actualización*. Here it is a badge on a dataset row, not a sentence, so it takes the compact nominal form, with the system's two words in it. |
| Replace | Substituír | Android's own word — `settings:vpn_replace`. |
| Language | Idioma | Android's own — `settings:app_locale_preference_title`, `settings:tts_default_lang_title`. Not *lingua*, which is the language as a subject rather than as a setting, and which the system uses nowhere. |
| Cancel / Yes | Cancelar / Si | Android's own — `android:cancel`, `settings:yes`. Note that `android:ok` is *Aceptar*, not *OK*. |
| Storage | Almacenamento | Android's own — `settings:storage_settings`, `settings:storage_category`. *Liberar espazo* in `error_local_storage_download` and `dataset_delete_body` follows `settings:storage_free_up_space_title` and `settings:storage_menu_free`. |
| Display (settings section) | Pantalla | Android's own — `settings:display_settings`. |
| Location / position | localización / posición | Two words for two things, and Android draws the line in the same place: **localización** is the permission and the system feature (`android:permgrouplab_location`, `settings:location_settings_title`, 82 rows); **posición** is the reader's own point on the ground (`android:permdesc_ranging` and 21 more). Never *ubicación*, which is Spanish and holds **0** rows. |
| link (hyperlink) | ligazón | Android's own, and in the very path this file quotes — `settings:app_launch_add_link` is *Engadir ligazón*; 26 rows against **0** for *enlace*. |
| connection (network) | conexión | Android's own — `settings:mobile_data_no_connection` and `settings:disconnected` are both *Sen conexión*, which is `error_offline`'s opening word. |
| offline | sen conexión | Both as a qualifier — *datos sen conexión*, *índice sen conexión* — and as a state. Galician has no established borrowing here the way Portuguese has *offline*. |
| app | aplicación | 861 rows. Never *app*, and never *aplicativo*, which is Brazilian Portuguese. |
| conurbation | área metropolitana | Neutral, and the term the catalogue's own cities are described by. |
| town (in an address) | localidade | The place an address names, in `address_search_hint` and `address_no_match_message`. **concello** is used where the source says "municipality" — `about_attribution_geonames` — because that is the administrative unit Galicia actually has, and the one GeoNames names. |
| street | rúa | And `address_search_hint` opens on it, which is the order a Galician address is written in. |
| file | ficheiro | Android's own — 50 rows against 3 for *arquivo*. It happens to be the Portuguese word too; it is Galician on its own account. |
| download | descargar / descarga | Android's own verb — `android:install_carrier_app_notification_button` is *Descargar aplicación*, `settings:filter_apps_third_party` is *Descargadas*. **Not *transferir***, which is Portugal's word and would read as a lusism here; Android's Galician uses *transferir* only for moving a thing from one device to another (`settings:transfer_esim_to_another_device_title`). |
| feed (GBFS) | fluxo | The network's published stream, in the errors and in the welcome. |
| tracker | rastrexador | `welcome_hello_body` and `about_privacy_body`. This file's own word: Android's Galician says *seguimento* for tracking, which is the act, and has no noun for the thing. *Rastrexador* is what Galician privacy texts write and what *rastrexar* gives. |
| security | seguranza | Android's own, and unanimously: **92** rows say *seguranza* (`android:notification_channel_security`, `settings:cellular_security_settings_title`) and **0** say *seguridade*. It carries the three `error_untrusted_server*` strings. |
| map data / tiles | mapa base | The name the storage screen gives the dataset, and the one every other string must use for it — including `map_needs_tiles_title`. |
| routing data | grafo de rutas | The project's own term, used in `journey_graph_missing` too. More technical than the English "routing data", and kept deliberately: it is one object with one name, and it reuses *ruta* so the dataset and the thing it computes are visibly the same subject. |
| address | enderezo | **26** rows, and the decisive one is `android:autofill_save_type_address`, which is *enderezo* — a postal address, named as such; `settings:emergency_address_title` is *Enderezo de emerxencia*. *Dirección* holds **3** rows and **not one of them is an address**: `settings:force_rtl_layout_all_locales` (*dirección do deseño RTL*) and `android:httpErrorRedirectLoop` (*redireccións do servidor*). In Galician a *dirección* is a direction one faces, and using it for a postal address is the commonest castilianism in Galician software. |
| address index | índice de enderezos | — |
| dataset | conxunto de datos | — |
| metered connection | rede sen tarifa plana | Android's own pair is `settings:wifi_metered_label` *Rede sen tarifa plana* and `settings:wifi_unmetered_label` *Rede con tarifa plana*. **The switch no longer says it**: it reads *Descargar só coa wifi*, as the English now does, because the wifi is the word every reader already has. The pair stays here as the name of the concept, and the line under the switch and the two dialogs explain it in ordinary words — *cobrarse por megabyte*, *conta como unha rede móbil*. |
| Wi-Fi | wifi | **Lower case, feminine, one word** — *a wifi*, *coa wifi*, *unha wifi* — because that is what Android's Galician writes: 175 lower-case occurrences, `settings:wifi_settings` titled *Wifi*, `settings:wifi_select_network` *Seleccionar wifi*, 23 rows saying *a wifi* and none saying *o wifi*. Exactly one row in 8 699 writes *Wi-Fi*. This is the only entry in this glossary where the file departs from every other language's "Wi-Fi", and it is flagged as such: it is not a slip, it is the system word. |
| mobile network / mobile data | rede móbil / datos móbiles | Android's own — `android:notification_channel_mobile_data_status` is *Estado dos datos móbiles*. |
| may be billed | pode cobrarse | Never *cóbrase*. The phone reads only that the connection declares itself metered; what the plan behind it charges, no device can know. So `download_unmetered_only_description`, `download_held_back_body` and `download_stopped_body` all carry *pode*, and `download_held_back_title` names the situation — *Descarga nunha rede móbil* — rather than passing a verdict on the connection. `download_can_resume` drops the modal because there the device does state what it knows: *xa non é unha rede móbil*. |
| by default | de forma predeterminada | Android's own — `settings:launch_by_default` and `settings:auto_launch_label` are both *Abrir de forma predeterminada*, which is the exact step `about_links_body` quotes, and it is also what `settings_opening_title` says. Never *por defecto*, which holds 1 row against 107 for *predeterminado*. |
| Home (the place) | Casa | Where the reader lives, named by them (`settings_place_home`, `journey_source_home`, the same word in both). **Never *Inicio***, which is an application's first screen and not a place one rides home to. |
| Work (the place) | Traballo | Where they work (`settings_place_work`, `journey_source_work`). The place, not the occupation: it stands beside *Casa* under *Os meus lugares*, which settles the reading. |
| My places | Os meus lugares | The settings section that holds the two (`settings_section_places`). Galician keeps the article before the possessive, as the rest of the file does — *a túa cidade*, *a miña posición*. *Lugar* is the noun `place_sheet_title` uses as well. |
| outside the city served | Fóra da cidade cuberta | Under a place the conurbation in use does not cover (`settings_place_outside_city`). *Fóra de* and *cubrir* are what `station_beyond_area` already says. The application serves several conurbations side by side: a *Casa* named in Lille is out of reach while A Coruña is in use. The place is kept and stays erasable — out of reach, not wrong. |
| Face north and lay the map flat | Orientar ao norte e aplanar o mapa | `map_face_north`, on the compass, which shows while the map is turned or tilted, and one press gives back both at once — the north and the flat; `map_bearing_description` counts the turn in *graos*. |
| tilt / lay flat | inclinar / inclinado, aplanar | *Inclinar* is the tilt and *xirar* the turn, kept apart so that the four gestures of `map_description` stay four. `map_tilted_description` is «O mapa está inclinado.», and `map_face_north` says *aplanar* for laying it flat again. |
| this place | Este lugar | `place_sheet_title`, the sheet a point found on the map opens — the same noun as *Os meus lugares*. `place_clear` is *Borrar este punto*, which keeps *Borrar* for emptying against *Eliminar*, which destroys. |
| Go there / Leave from here | Ir alí / Partir de aquí | The place sheet's two actions take the station sheet's own words: `station_as_origin` is already *Partir de aquí*, word for word, and `station_as_destination` *Ir aquí*, of which *Ir alí* is the same verb with the deictic moved. The place sheet is the station sheet's sister and the two must read as one, so neither pair is reworded without the other. |
| Reserved | Reservada | On a bike's own line of the station sheet (`station_bike_reserved`), where the feed says the bike is booked. Feminine, agreeing with *bici* as every word said of one bike does. |

The three dataset names are all **masculine singular** — *mapa base*, *grafo de
rutas*, *índice de enderezos* — which is what lets `dataset_imported` and
`dataset_deleted` agree once for all three (*%1$s instalado*, *%1$s eliminado*).

## The two neighbours

The characteristic risk of this file, and the reason it is longer than
`docs/glossary/es.md`. Galician sits between Spanish and Portuguese, is read by
people fluent in the first, and looks from a distance like the second. Both
sides offer words that a reviewer may "correct" this file **into**.

### Castilianisms turned down

Every one of these is a real word in Spanish and not the Galician one. The
count beside it is the Android lexicon's, which agrees in every case.

| Spanish | Galician (written here) | Where it would have got in |
|---|---|---|
| ubicación | **localización** / **posición** | 0 rows against 82 and 22. `map_location_denied`, `journey_source_my_position`, `address_detail_approximate` |
| búsqueda | **busca** | the noun in `search_stations_clear`, `address_search_clear`, `city_search_clear`, `action_clear_search` |
| dirección (postal) | **enderezo** | the whole `address_*` block, `dataset_addresses`, `about_attribution_ban` |
| enlace | **ligazón** | 0 rows against 26. `about_links_body`, twice |
| espacio | **espazo** | `docks_available`, `counterpart_docks`, `mode_docks` and eight more — the *-cio/-zo* pair is the loudest of the lot |
| servicio | **servizo** | `station_out_of_service`, `settings_city_description`, `city_here_body`, `settings_map_filters_hide_out_of_service` |
| licencia | **licenza** | `about_licence_title`, `about_licences_title`, the six `about_attribution_*` |
| seguridad(e) | **seguranza** | 0 rows against 92. the three `error_untrusted_server*` |
| ajustes | **Configuración** | `settings_title`, `settings_open`, `about_links_body`. Spanish Android says *Ajustes*; Galician Android says *Configuración*, 324 rows to 5 |
| archivo | **ficheiro** | 3 rows against 50. the seven `dataset_rejected_*`, `error_malformed_download`, `error_local_storage_download` |
| por defecto | **de forma predeterminada** | 1 row against 107. `settings_opening_title`, `about_links_body` |
| ¿…? | **…?** | see Typography: Galician writes no opening question mark, and `map_needs_city_title`, `address_search_prompt_title`, `journey_origin_empty`, `journey_destination_empty`, `journey_navigate_which`, `city_delete_title`, `dataset_delete_title`, `city_proposal_body` all open bare |

### Lusisms turned down

Portuguese is close enough that a well-meaning contributor may reach for it,
and the reintegrationist debate makes some of these look like a norm question
when they are simply the wrong language.

| Portuguese | Galician (written here) | Note |
|---|---|---|
| transferir, transferência | **descargar, descarga** | The largest of these by occurrence: the whole storage screen turns on it. Android's Galician reserves *transferir* for moving a thing between devices |
| ecrã | **pantalla** | `settings:display_settings` |
| aplicação, aplicativo | **aplicación** | throughout |
| morada | **enderezo** | *Morada* in Galician is a dwelling, and archaic at that |
| lugar livre, vaga, doca | **espazo libre**, **ancoraxe** | the free dock and the total, kept apart as in every other language of this project |
| comboio | **tren** | `address_search_prompt_message` |
| ligação (network) | **conexión** | *Ligazón* in Galician is a hyperlink; the network sense is *conexión*, and writing both the same way would have made `about_links_body` unreadable |
| ç, nh, lh, -çom | **z, ñ, ll, -ción** | the orthographic norm, settled at the head of the file |

## What Galician does that neither neighbour does

Three grammatical facts that shaped sentences rather than words, written down
because a reviewer reading only the vocabulary table would put them back.

**Galician has no compound perfect.** There is no *hei feito* answering to
Spanish *he hecho* or Portuguese *tenho feito*: what English says with "has
been received", Galician says with the plain preterite. So
`error_offline_no_availability` is *Aínda non se recibiu ningunha
dispoñibilidade*, `error_offline_check` is *Non se comprobou nada*, and
`sources_intro` is *as cidades que non instalaches*. `values-es/` reached the
same shape by a decision — its glossary argues the perfect out as a peninsular
marker — where here the language leaves no choice at all.

**Clitic pronouns go after the verb, except when something pulls them
forward.** Affirmative main clauses write *calcúlanse*, *cóntanse*,
*descárganse*, *instálanse*, *anúnciase*, *consérvase*, *mantense*,
*esqueceuse*; a negation, an interrogative or a quantifier pulls the pronoun in
front: *non se garda*, *non se atopou*, *todo se busca*, *nada sae*. Both
shapes appear in this file within a few lines of each other, and both are
correct where they stand.

**An infinitive followed by a clitic loses its *-r*.** *Mover* + *o* is
**movelo**, *contar* + *as* is **contalas**, *escoller* + *a* is **escollela**,
*distribuír* + *o* is **distribuílo** (the accent stays). The file is full of
them — `map_description` alone has three — and they are the form a contributor
writing from Spanish gets wrong first, since Spanish keeps the *r* (*moverlo*).

## The order of an address

**The order of an address is not this file's to decide, and never was.** It
belongs to the country the address is in, not to the reader's language
(SPEC §4.3): *Rúa Real, 12* is how an A Coruña address is written for every
reader of the application, and *12 rue Nationale* is how a Lyon one is written
for a Galician reader. The layouts are a table in
`core/address/AddressLayout.kt`, keyed on the language of the **address base**.

`address_search_hint` follows it — **Rúa, número, localidade**, and not the
English *Number, street, town*. The search engine reads a house number in the
three orders that are written (SPEC §4.3): opening the query, closing it, or
standing between the street and the town, which is the ordinary order here and
the reason this prompt may name it. A postcode is dropped before the number is
looked for, so *Rúa Real 12 15003 A Coruña* resolves as readily as *Rúa Real 12
A Coruña*.

What the prompt must not do is invite a second number: a number that does not
open the query is given up as soon as another appears, which is the guard that
keeps street names carrying a date whole. Naming one number and one town is
exactly as far as the prompt can go.

**There is no `config/address-normalization/gl.json` yet**, so Galician street
names are folded by `en.json` — plain folding, no street type, no article list.
That is a separate file from this one and a separate contribution; it is
recorded here because it is what a Galician reader will notice next, after the
interface stops being English.

## The longest strings measured

Galician runs **+10.3 %** longer than the English source over the whole file —
15 174 characters of translated text against the source's 13 759 — which is ordinary for a Romance language
and well inside what the layouts already carry for Spanish and Portuguese. The
places worth knowing are the short ones, where a few characters are a wrapped
line:

| Key | English | Galician | |
|---|---|---|---|
| `storage_open` | Manage offline data (19) | Xestionar os datos sen conexión (31) | the widest row of the settings screen |
| `stations_order_by_distance` | Nearest station first (21) | A estación máis próxima primeiro (32) | spoken label, not drawn — no risk |
| `dataset_update_available` | Update available (16) | Actualización dispoñible (24) | a badge on a dataset row; the longest thing that badge holds in any language of this set |
| `welcome_choose_city`, `city_choose` | Choose my city (14) | Escoller a miña cidade (22) | a full-width button, so it fits |
| `settings_opening_title` | Open the app by default on (26) | Abrir a aplicación de forma predeterminada en (45) | the price of quoting the system's *de forma predeterminada*, and worth it |
| `city_here_use` | Use it (6) | Usar esta cidade (16) | *Usalo* would have been shorter and ambiguous — Galician needs the noun here |
| `settings_title` | Settings (8) | Configuración (13) | a toolbar title; *Axustes* would have been shorter and wrong |

Three go the other way and are worth recording so nobody "fixes" them:
`journey_compute` (20 → 15, *Calcular a ruta*), `journey_navigate` (24 → 19,
*Abrir en navegación*) and `incoming_show_me` (4 → 3, *Ver*).

Nothing here needed shortening at the cost of clarity, and no layout was
touched for this translation.

## Words that are not translated

Product and network names — Roue Libre, biciArteixo, Vélib', Bicing, BRouter,
MapLibre, OpenStreetMap, GBFS — the licence names, and Base Adresse Nationale,
which is the proper name of a French dataset. Unit symbols (m, km, ft, yd, mi,
min, h, B, kB, MB, GB) stay as they are. `resources` `name` attributes, always.

`settings_walking_pace_normal` is **Normal**, which is the English word
unchanged and is also the Galician one. `tools/check_translations.py` reports it
as a string to confirm; it is confirmed here, and it is the only one.

**Wi-Fi is the exception to the exception**: it is a trademark and every other
glossary in this repository lists it among the untranslated, but Galician
Android writes it *wifi*, lower case and feminine, and this file follows the
system. See the vocabulary table for the count.

## Typography

Quotations are **« »**. **A question carries no opening mark** — Galician is
not Spanish, and this is the place the difference is most visible on screen:
*Onde vas?*, *Que cidade?*, *Desde onde?*. **No space stands before `:`, `;`,
`?` or `!`** — Galician is not French either.

The apostrophe is a mark Galician has almost no use for, so nothing in the file
is escaped; a contributor who does need one in a resource value must write it
`\'`, since Android would otherwise drop it.

Contracted articles are everywhere and they have to hold around a placeholder
whose contents vary: `de` + `o` = *do*, `en` + `o` = *no*, `a` + `o` = *ao*,
`con` + `o` = *co*, `por` + `o` = *polo*, `en` + `outro` = *noutro*. Where the
gender behind a placeholder could not be settled in advance — a city, a street,
a dataset — the sentence is written so that no article stands before it at all.
`ao` is written in full rather than *ó*: both are admitted, the first is the
recommended written form.

Numbers keep the **decimal comma** and the thousands point: *42,5 MB*,
*1,3 GB*. Size units are the international symbols — B, kB, MB, GB — since
Galician says *byte*.

**A figure and the unit naming it are joined by a non-breaking space** (U+00A0),
here as in every language: *%1$s m*, *%1$d bicis*, *hai %1$d minutos*,
*%1$d h %2$02d*. `UnitTypographyTest` holds every language to it.

Galician has **two plural categories**, `one` and `other` (CLDR), where
Spanish, Portuguese, Italian and Catalan have three. The `many` those four
reach at a million does not exist here: writing one would be dead text and
`lint` reports it as `UnusedQuantity`. `one` covers 1 alone — *0 bicis*, in the
plural, as in Spanish and unlike French.
