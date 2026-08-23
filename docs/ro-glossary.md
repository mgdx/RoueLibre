# Romanian glossary

The terms `res/values-ro/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Romanian ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Register, region and typography

The Romanian says **tu** (SPEC §9), in the interface and in the store texts
alike — never the plural of politeness: the application speaks to one person
walking to a station, not to a customer at a counter. That is also what
Android's own Romanian does on its buttons — *Anulează*, *Încearcă din nou*,
*Șterge*, *Continuă*, *Omite*, *Actualizează*, *Înlocuiește*, *Adaugă* — and in
its sentences: *Atinge*, *Alege*, *Verifică*, *Instalează*. Confirmation
dialogs put their question in the second person, as the system does
(*Activezi Explorează prin atingere?*): **Ștergi aceste date?**

It serves the three Romanian networks of the catalogue — **Alba Iulia
Velocity**, **Hunedoara**, **Dej BikeCity** — and any other conurbation a
reader installs, so nothing here is written for one city alone.

Where Android's own Romanian has a word, that word wins: an application that
calls the settings anything but **Setări** reads as a foreign one. The arbiter
is the system lexicon extracted from `framework-res.apk` and `Settings.apk`;
every row below that cites a key was grepped in it, and the handful of choices
that are **not** Android's say so.

### The letters

The two Romanian letters with a **comma** below are **ș** (U+0219) and **ț**
(U+021B). The Turkish cedilla letters at U+015F and U+0163 look nearly
identical at interface size and are wrong; nothing in `values-ro/strings.xml`
or in `fastlane/metadata/android/ro/` holds one, header comments included, and
that is checkable in one command:

```bash
grep -rc -P "\x{015F}|\x{0163}|\x{015E}|\x{0162}" \
    app/src/main/res/values-ro/strings.xml fastlane/metadata/android/ro/
```

It must answer 0 everywhere. **ă**, **â** and **î** are written in full.

Quotations are **„ ”** — opening below, closing above. The apostrophe, where a
network name carries one (*Vélib’*, *V’lille*, *Vélo’v*), is **’** (U+2019) and
never the straight quote, so nothing in the XML needs escaping. Numbers keep
the decimal comma and the thousands point — "42,5 MB", "1,3 GB" — and the size
units stay the international symbols B, kB, MB, GB.

## Plurals

Romanian has **three** categories and the boundaries are its own:

| Category | The numbers it takes | Example |
|---|---|---|
| `one` | 1 | 1 bicicletă |
| `few` | **0**, 2–19, 101–119, … | 0 biciclete, 7 biciclete |
| `other` | 20 and above, 100, 1000, … | 20 **de** biciclete |

Two consequences that a translator coming from another language gets wrong:

- **zero falls into `few`**, so it reads *0 biciclete* and never *0 de
  biciclete*;
- **`other` carries the preposition *de***, which is not decoration but
  grammar: *20 de stații*, *143 de secunde*. Every `other` item in the file
  holds it.

`counterpart_bikes` and `counterpart_docks` carry no placeholder in any item,
on purpose: the figure is already painted in the disc these words stand beside,
and an item holding it would say it twice. `CounterpartAgreementTest` fails the
day one comes back. Their `other` items are therefore the bare **de biciclete**
and **de locuri libere**, which read with the figure in the disc — "20" then
"de biciclete".

`journey_climb` is **urcare de %1$s** and not *%1$s de urcare*: that "de"
belongs to a count, and the placeholder there is an already formatted distance,
not a number.

## The definite article, and what it costs around a placeholder

Romanian suffixes its definite article — *bicicletă* / *bicicleta*, *stație* /
*stația* — and inflects the adjectives and participles that follow. A sentence
built around a placeholder holding a city, a network, a street or a dataset
name cannot count on the gender or the definiteness of what lands in it, so
several strings are written to keep any agreeing word away from the placeholder
altogether:

- **`city_installed`** is *%1$s pe dispozitiv*, not *%1$s instalați*: the
  placeholder is a size, "143 MB", and no participle can agree with it.
- **`storage_total`** is *Ocupă %1$s pe acest dispozitiv*, for the same reason:
  the verb keeps the sentence impersonal where *ocupați* would have had to
  agree.
- **`city_here_body`** and **`city_here_install`** carry "its" as the dative
  clitic **-i** — *Instalează-i datele* — which names no gender and therefore
  meets a network called anything.
- **`city_here_installed_body`** drops the possessive entirely: *…, iar datele
  sunt deja pe dispozitiv*.
- **`download_stopped_body`** says *ca să termini de descărcat %1$s* rather
  than putting an article before the figure, where *cei 1,3 GB* would have had
  to guess a gender.
- **`city_here_use`** is *Folosește acest oraș* and not *Folosește-o*: the
  button carries no placeholder, but the thing it points at is named by one.

Where the placeholder is a quoted name — `dataset_delete_body`,
`dataset_rejected_format`, `error_feed_unavailable` — the „ ” do the same work
and the sentence keeps its article.

## The vocabulary

| English | Romanian | Why |
|---|---|---|
| journey | traseu | The whole door-to-door thing: the screen, the settings section, the button, the store texts. It is what Romanian transport writing calls a computed trip, and it leaves *rută* free for the line on the ground. |
| ride | etapa cu bicicleta | The bike leg alone, inside a journey — *Etapa cu bicicleta, la deal și la vale*. A different word from *traseu*, so the profile drawing and the journey detail stay about two different objects. Where the whole journey is one ride, on one's own bike, `journey_computing_own_bike` says *drumul cu bicicleta*: there is no leg there, because there is nothing else. |
| to ride | a pedala | The bike verb where one is needed, against *a merge pe jos* for the walking legs. The five journey steps say *Pedalează până la %1$s* and *Mergi pe jos până la %1$s*, so they read as one series and no article meets the placeholder. |
| route | rută | Only in `journey_no_route` — *Nicio rută practicabilă între aceste două puncte* — and in `settings_own_bike_kind_hint`, which mean the line on the ground rather than the planned journey. |
| leg (of a journey) | etapă / porțiune | *Etapă* for the ride, *porțiunile pe jos* for the walking parts in `settings_walking_pace_description`. |
| station | stație | A bike-share station. Romanian keeps **gară** for a railway station, so the collision Italian and Spanish have to write around does not exist here: `address_search_prompt_message` says *gări* and cannot be misread. |
| bike | bicicletă | There is no short everyday noun in Romanian — *bicla* is slang and *velo* is not Romanian — so the full word is used everywhere. It is feminine, which is what makes *mecanică* and *electrică* agree with it in every string. |
| bike sharing (the service) | bike-sharing | The name Romanian gives the service itself, in the press and in the store, and what somebody looking for this application types. It appears in `short_description.txt` only. Inside the application, where the English says "shared bikes" — the vehicles rather than the service — it is **biciclete partajate**. |
| dock (free) | loc liber | What a bike is returned into, counted as available. Plain, immediately read, and it is what the mode toggle is called: *Locuri libere*. |
| dock (capacity) | doc | The same object counted as a total, which is a different figure on the screen: "12 locuri libere · 30 de docuri". English uses one word for both and Romanian needs two, since the two figures stand side by side on a list row. **This is the entry I am least sure of** — Romanian operators are small and their wording is not settled; *punct de andocare* is the descriptive alternative and is far too long for a list row. A native reviewer should confirm it. |
| dock | *never* «terminal», *never* «automat» | A terminal is what one pays at, not the point a bike attaches to. |
| bike, electric | electrică | Pedal-assist, never a moped: `journey_bike_kind_electric_description` says *cu asistență la pedalare* in full, which is the standard Romanian term. |
| out of service | nefuncțională | **Not Android's**: the system's *În afara ariei de acoperire* (`settings:radioInfo_service_out`) is about radio coverage, and a station out of service is one that does not work. Feminine, agreeing with *stație*. |
| pace (walking) | ritm de mers | A pace is not a speed: `values/strings.xml` says so above the string, and *viteză de mers* would say the opposite. The **de mers** is not redundancy and is not to be trimmed — in a bicycle application a bare *Ritm* would be read as a pedalling pace, which is the one thing this setting is not. *Ritm* is masculine, hence **Lent / Normal / Alert**. "Brisk" is *alert*, the ordinary Romanian collocation for a brisk walk (*pas alert*), where *rapid* would name a speed again. |
| climb | urcare | The metres climbed over a leg or a journey. *Denivelare* names a difference in level without saying which way it goes; the English means the metres gained, and so does *urcare*. |
| conurbation | zonă urbană | Neutral, where *conurbație* is a planner's word and *zonă metropolitană* would be wrong about Auray, which is three megabytes and no metropolis. |
| town (in an address) | localitate | The administrative unit an address sits in, in the address search and its failures. It is what every Romanian address form is labelled, and it keeps *oraș* for the conurbation the application serves — two different things one screen apart, and *localitate* is also true of a commune, which *oraș* is not. |
| Settings | Setări | Android's own word (`settings:settings_label`), everywhere including the system path quoted in `about_links_body`. |
| Display (settings section) | Afișaj | Android's own section title (`settings:display_category_title`). |
| Search | Caută | Android's own word for the action and the field (`android:search_go`). |
| Clear | Șterge | Android's word for emptying a field (`settings:clear`), which is what "clear the search" does. |
| Refresh | Actualizează | Android's word for data (`android:autofill_update_yes`, `settings:nfc_payment_btn_text_update`). |
| Try again | Încearcă din nou | Android's own (`settings:retry`, `android:lockscreen_password_wrong`). *Reîncearcă* also occurs in the system but in one place only; the longer form is the usual one. |
| Back | Înapoi | Android's own (`android:back_button_label`). |
| Tap | Atinge | Android's own verb, in the second person like the rest (`settings:inactive_app_active_summary`). |
| Press and hold | Ține apăsat | Android's own wording for a long press (`settings:assistant_long_press_home_gesture_title`). |
| Delete / Remove | Șterge / Elimină | Android distinguishes them (`settings:delete`, `settings:remove`) and so does this file: *Șterge* destroys data, *Elimină* takes a station out of the favourites. |
| Skip | Omite | Android's own (`android:skip_button_label`). |
| Continue | Continuă | Android's own (`android:autofill_continue_yes`), in the second person; the system's shouted *CONTINUAȚI* belongs to one carrier disclaimer and is not the register of this file. |
| Show / See | Afișează / Vezi | **Afișează** where something hidden is revealed — the counts on the map, the received place. Android's own (`settings:condition_expand_show`). **Vezi** where a button opens a screen or a page: *Vezi harta*, *Vezi favoritele*, *Vezi noutățile*, *Vezi codul sursă*. That half is this file's own, and it is what Romanian interfaces put on a button that goes somewhere. |
| About | Despre | Android's own row is *Despre telefon* (`settings:about_settings`), which is about the device; an application's about screen is *Despre*. |
| In use | În uz | Android's own (`android:media_route_status_in_use`), on the city already installed. The system also writes *Se utilizează* (`settings:wifi_display_status_in_use`); the shorter one fits a list row. |
| just now | chiar acum | Android's own (`settings:time_unit_just_now`), lowercased because it is read after *Actualizat*. |
| Searching… | Se caută… | Android's own (`settings:wifi_p2p_menu_searching`). The other waits follow its impersonal-reflexive shape: *Se calculează cel mai bun traseu…*, *Se citește manifestul…*, *Se caută poziția ta…* |
| Update available | Actualizare disponibilă | Android's own (`settings:android_version_pending_update_summary`). |
| Check for update | Caută actualizări | Android's own (`android:unsupported_compile_sdk_check_update`). |
| Replace | Înlocuiește | Android's own (`settings:vpn_replace`). |
| Language | Limbă | Android's own (`settings:app_locale_preference_title`). |
| Location / position | locație / poziție | **Locație** for the phone's service, Android's own word (`android:permgrouplab_location`), in `map_location_denied` and `map_location_unavailable`. **Poziție** for where the reader actually is, which is what the journey screen and the city proposal talk about. The system uses both and so does this file, for the two different things. |
| Storage | Stocare | Android's own section word (`settings:storage_category`). |
| Cancel / Yes | Anulează / Da | Android's own (`android:cancel`, `settings:yes`). |
| Can't … | Nu se poate … | The system's shape for a failure — *Nu se poate conecta*, *Nu se poate șterge* — of which `settings:wifi_cant_connect` and dozens of others are instances. `error_local_storage` follows it. |
| Open by default | Deschide în mod prestabilit | Android's own (`settings:launch_by_default`), which is what makes the Settings path in `about_links_body` match the system word for word, alongside *Aplicații* (`settings:apps_dashboard_title`) and *Adaugă un link* (`settings:app_launch_add_link`). |
| app | aplicație | Android's own (`settings:apps_dashboard_title`). Romanian has no short colloquial form the system uses, so the full word stands everywhere. |
| offline | offline | **Not Android's**: the lexicon has no row for it. It is the word Romanian actually uses, in the stores and out of them, and *fără conexiune* would have made *Date offline* and *Gestionează datele offline* considerably heavier for nothing. |
| tracker | tracker / trackere | **Not Android's** either, and deliberate: it is the word Romanian privacy writing uses. *Sistem de urmărire* explains it; it does not name it. |
| manifest | manifest | **Not Android's**: the lexicon has no row for it. Romanian technical writing says *manifest*, definite *manifestul*, which is what `storage_checking` reads. |
| metered / unmetered | contorizată / necontorizată | Android's own wording for a metered network (`settings:wifi_metered_label`, `settings:wifi_unmetered_label`). The setting's description then explains it as billing by the megabyte, exactly as the English does. |
| megabyte (as a unit of billing) | megaoctet | **Not Android's**: the lexicon carries only *B* (`android:byteShort`). *Megaoctet* is the standard Romanian localisation term and the one DEX recognises, where *megabyte* is an unadapted borrowing. The symbols themselves stay MB and GB. |
| feed (GBFS) | flux | The network's published stream, in the errors and in the welcome. |
| dataset | set de date | The three of them together, in `storage_intro` and in the store text. |
| file | fișier | Android's own (`android:mime_type_generic`). |
| favourites | Favorite | **Not Android's**: the lexicon has *preferat* only as an adjective (*Limba preferată*), never as the name of a saved list. *Favorite* is what Romanian interfaces put on that screen and what a reader looks for. |
| one's own bike | **propria** ta bicicletă | The English "own" is carried and not dropped into *bicicleta ta*: `values/strings.xml` asks above `settings_own_bike_kind_title` that the wording stay clearly about the reader's own equipment, because the journey screen holds a separate, similar-looking choice about the bikes the **network** lends. *Propriu* is exactly that intensifier in Romanian. It is carried through the switch, the setting and the four summaries, so the three screens stay about one object. |
| house number | număr poștal | What Romanian address forms call the doorway number, in `about_attribution_ban` and in changelog 3. |

## The three dataset names

They are the names the storage screen gives the three downloads, and every
other string must use them rather than describe them again:

| Dataset | Romanian | Where else it is read |
|---|---|---|
| map data / tiles | **Fond cartografic** | `map_needs_tiles_title`, `map_needs_tiles_message` |
| routing data | **Graf de trasee** | `journey_graph_missing` |
| address index | **Index de adrese** | `address_needs_index_title`, `address_needs_index_message`, `address_unreadable_title`, `incoming_needs_index` |

All three are **masculine singular**, and that is not an accident:
`dataset_imported` and `dataset_deleted` are one string each for all three
("%1$s instalat", "%1$s șters"), so a feminine or plural name among them would
have broken the agreement for one dataset out of three. That is why the first
is not *Date de hartă* — *date* is feminine plural — and why the second is not
*Datele de rutare*. *Fond cartografic* is the Romanian cartographic term for a
base map, and it is preferred over the plainer *Fond de hartă* because
`map_needs_tiles_title` would otherwise have read *Harta are nevoie de fondul
ei de hartă*; the purpose line under it, *Desenează harta fără rețea*, says
what it is.

*Graf de trasee* is more technical than the English "routing data" and is kept
deliberately: it is one object with one name, and *rutare* in Romanian is first
of all a networking word.

## Where this file departs from its own rules

One string does, on purpose:

- **`journey_computing_own_bike`** says *drumul cu bicicleta* and not *etapa cu
  bicicleta*, which is the word the glossary gives to "ride" everywhere else.
  It is the right way round: a journey on one's own bike has no leg, because
  there is nothing else in it — the English comment above the string says the
  same thing about its own wording, and calling it an *etapă* would have
  implied a walk on either side of it that is not there.

## The order of an address

**The order of an address is not this file's to decide, and never was.** It
belongs to the country the address is in, not to the reader's language
(SPEC §4.3): a Cluj address reads "Strada Memorandumului 21" for every reader
of the application, and a Lyon one reads "12 rue Nationale" for a Romanian
reader. The two formats that used to live here, `address_with_number` and
`address_number_with_suffix`, were removed for that reason; the layouts are a
table in `core/address/AddressLayout.kt`, keyed on the language of the
**address base**.

`address_search_hint` follows it — **Stradă, număr, localitate**, and not the
English *Number, street, town*. Romania writes the doorway after the street, so
the prompt does too. `AddressQuery.parseQuery` reads a house number in the
three positions that are written (SPEC §4.3): opening the query, closing it, or
**standing between the street and the town**, which is Romanian's ordinary
order and the reason this prompt may name it.

Two things the prompt must not invite:

- **a second number.** A number that does not open the query is given up as
  soon as another appears — the guard that keeps a street named after a date
  written in full whole, and Romania has many of them: Bulevardul 1 Decembrie
  1918, Strada 9 Mai, Piața 21 Decembrie 1989. That guard is why the **postcode
  is left out of the prompt**: the Romanian code is six digits, a second number
  in the query, and inviting it would cost the doorway. Only a country writing
  its postcode as a single group of five digits could name it here, and
  Romania is not one.
- **a stop word beside the number.** A number between street and town is only
  read when neither neighbour is one, so the prompt writes the town straight
  after the number with nothing between them. `config/address-normalization/`
  is where those words are listed.

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Vélo’v, Citi Bike,
Alba Iulia Velocity, Dej BikeCity, BRouter, MapLibre, OpenStreetMap, GBFS,
Wi-Fi — the licence names, and Base Adresse Nationale, which is the proper name
of a French dataset. Unit symbols (m, km, ft, yd, mi, min, h, B, kB, MB, GB)
stay as they are. City names take their Romanian form where Romanian has one —
Praga, Copenhaga, Barcelona, Viena — and keep their own where it does not: New
York, Buenos Aires, Riga, Pristina, Auray, Lille, Paris.
`resources` `name` attributes, always.

One string comes back identical to the English and is right to:
`settings_walking_pace_normal`, **Normal**, which is the Romanian word too,
spelled and read the same. The lexicon has no row for it — this is my own
choice, and there is no other word for it in Romanian.
