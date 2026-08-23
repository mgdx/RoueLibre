# Italian glossary

The terms `res/values-it/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Italian ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Register, region and typography

The Italian says **tu** (SPEC §9), in the interface and in the store texts
alike — never *Lei*: the application speaks to one person walking to a station,
not to a customer at a counter. Buttons are second-person imperatives, which is
what Android's own Italian writes on them — *Annulla*, *Riprova*, *Elimina*,
*Continua*, *Salta*, *Aggiorna*, *Sostituisci* — and so are the sentences that
instruct: *Tocca*, *Scegli*, *Controlla*, *Installa*. Confirmation dialogs put
their question in the infinitive, as the system does: *Eliminare questi dati?*

It serves **Italy and Italian-speaking Switzerland** (Ticino, Grigioni), so
nothing is written for one of the two alone. That mostly costs nothing —
*comune*, *stazione*, *bici*, *impostazioni* are read the same on both sides of
the border — and where a Swiss word differs it is the technical vocabulary of
administration rather than anything this application says.

Where Android's own Italian has a word, that word wins: an application that
calls the settings anything but **Impostazioni** reads as a foreign one. The
arbiter is the system lexicon extracted from `framework-res.apk` and
`Settings.apk`; every row below that cites a key was grepped in it, and the
handful of choices that are **not** Android's say so.

Typography: the apostrophe is **’** (U+2019) and never the straight quote,
which is why nothing in this file is escaped; quotations are **« »**; no space
stands before `:` or `;`. Numbers keep the decimal comma and the thousands
point — "42,5 MB", "1,3 GB" — and the size units stay the international
symbols, B, kB, MB, GB, since Italian says *byte*.

**Euphonic *d*** is written where usage asks for it before the same vowel —
*ed eliminare* — and not otherwise.

## Elision around a placeholder

Italian contracts its articles and elides them, and a placeholder holding a
city, a street or a dataset name cannot be counted on to start with the right
letter. A sentence that is only correct when the placeholder begins with a
consonant is a defect, so several strings are written to keep any article away
from the placeholder altogether:

- `journey_step_to_station` is **Raggiungi %1$s a piedi**, not *Cammina fino a
  %1$s*: the verb takes its object directly, and no preposition meets a station
  name of any language. Its four siblings — the ride, the two "to the
  destination" forms — are built the same way, so the five steps of a journey
  read as one series.
- `storage_total` is **Occupa %1$s su questo dispositivo**, which avoids the
  participle *occupati* agreeing with a size that may be "1 MB" or "340 MB".
- `download_stopped_body` says **per completare il download di %1$s** rather
  than putting an article before the figure, where *i 1,3 GB* would read badly.
- `city_installed` is **%1$s sul dispositivo**, with the placeholder opening the
  string.

Where the placeholder is a quoted name — `dataset_delete_body`,
`dataset_rejected_format`, `error_feed_unavailable` — the « » do the same work
and the sentence keeps its article.

## The vocabulary

| English | Italian | Why |
|---|---|---|
| journey | itinerario | The whole door-to-door thing: the screen, the settings section, the button, the store text. It is what Italian transport applications call a computed trip, and it leaves *percorso* free for the line on the ground. |
| ride | tratto in bici | The bike leg alone, inside a journey. A different word from *itinerario*, so "the ride, uphill and down" and "the journey in detail" stay about two different objects. |
| route | percorso | Only in "no practicable route" — *Nessun percorso praticabile tra questi due punti* — and in `dataset_routing`, `settings_own_bike_kind_hint` and the attributions, which all mean the line on the ground rather than the planned journey. |
| leg (of a journey) | tratto | "The walking parts of a journey" — *i tratti a piedi di un itinerario*. The ride is one such leg, which is why it is *il tratto in bici*. |
| to ride | pedalare / in bici | The bike verb where one is needed, against *a piedi* for the walking legs. The journey steps say *Raggiungi %1$s in bici* so that no preposition meets the placeholder. |
| station | stazione | A bike-share station. Italian uses the same word for a railway station, which is exactly what `address_search_prompt_message` means; there it is written out as **stazioni ferroviarie** so the two cannot be confused. |
| bike | bici | Not *bicicletta*. It is the everyday word, it fits in a list row and on a station's sheet where *bicicletta* does not, and it is feminine, so *meccanica* and *elettrica* agree with it everywhere. It does not inflect — "1 bici", "3 bici" — which is a fact about the word, not a missing plural: only the `many` form differs, and it differs by the *di* a million takes. |
| bike sharing (the service) | bike sharing | The name Italian gives the service itself, and what the networks call themselves — BikeMi is *il bike sharing di Milano*. It appears in the store texts, where somebody looking for the application is looking for those two words. Inside the application, where the English says "shared bikes" — the vehicles rather than the service — it is **bici condivise**. |
| dock (free) | posto libero | What a bike is returned into, counted as available. Plain, read the same in Milan and in Lugano, and free of the collision *piazza* would have with the town squares of the address search. |
| dock (capacity) | stallo | The same object counted as a total, which is a different figure on the screen: "12 posti liberi · 30 stalli". It is the word the Italian networks use for a docking slot, and English's single "dock" does not oblige Italian to have one either. |
| dock | *never* «colonnina», *never* «terminale» | A terminal is what one pays at, not the point a bike attaches to. |
| bike, electric | elettrica | Pedal-assist, never a moped: `journey_bike_kind_electric_description` says *a pedalata assistita* in full, which is the standard Italian term. |
| pace (walking) | andatura | A pace is not a speed: `values/strings.xml` says so above the string, and *velocità di marcia* would say the opposite. *Andatura* is feminine, hence **Lenta / Normale / Sostenuta**. The **a piedi** is not the redundancy it looks like, and is not to be trimmed: *andatura* is said of a horse, a boat, a peloton — it does not imply walking, and in a **bicycle** application an unqualified *Andatura* would be read as a cycling pace, which is the one thing this setting is not. *Andatura di marcia* is worse still, *marcia* being the word for a bicycle gear. "Brisk" is *sostenuta*, the ordinary Italian collocation for a brisk walk, where *veloce* would name a speed again. |
| climb | dislivello | The metres climbed over a leg or a journey, and the word Italian cycling uses for it. |
| Settings | Impostazioni | Android's own word (`settings:settings_label`), everywhere including the system path quoted in `about_links_body`. |
| Display (settings section) | Visualizzazione | Android's own section title (`settings:display_category_title`), over the *Display* it also uses for the screen itself. |
| Search | Cerca | Android's own word for the action and the field (`android:search_go`). |
| Clear | Cancella | Android's word for emptying a field (`settings:clear`), which is what "clear the search" does. |
| Refresh | Aggiorna | Android's word for data (`android:autofill_update_yes`, `settings:auto_sync_account_summary` writes *l'aggiornamento automatico dei dati*). |
| Try again | Riprova | Android's word on a button and in running text alike (`settings:retry`, `settings:wifitrackerlib_wifi_mbo_assoc_disallowed_cannot_connect`). |
| Back | Indietro | Android's word on the toolbar's back arrow (`android:back_button_label`). |
| Tap | Tocca | Android's own verb, in the second person like the rest (`settings:inactive_app_active_summary`). |
| Press and hold | Tieni premuta | Android's own wording for a long press (`settings:assistant_long_press_home_gesture_title`), agreed here with *riga*. |
| Delete / Remove | Elimina / Rimuovi | Android distinguishes them (`settings:delete`, `settings:remove`) and so does this file: *Elimina* destroys data, *Rimuovi* takes a station out of the favourites. |
| Skip | Salta | Android's word (`android:skip_button_label`). |
| Show / See | Mostra / Vedi | **Mostra** where something hidden is revealed — what the map counts, the received place, the journey opened in full. **Vedi** where a button opens a screen or a page: *Vedi la mappa*, *Vedi i preferiti*, *Vedi le novità*, *Vedi il codice sorgente*. Android has only `settings:condition_expand_show` → *Mostra*; the *Vedi* half is this file's own, and it is what Italian interfaces put on a button that goes somewhere. |
| About | Informazioni | Android's own row is *Informazioni sullo smartphone*, which is about the device; an application's about screen is *Informazioni*, and that is what the stores call it. |
| In use | In uso | Android's own word (`settings:wifi_display_status_in_use`), on the city already installed. |
| Out of service | Fuori servizio | Android's own wording (`settings:radioInfo_service_out`). |
| just now | adesso | Android's own wording (`settings:time_unit_just_now`), and it reads as one phrase with *Aggiornato %1$s*. |
| Searching… | Ricerca in corso… | Android's own wording (`settings:wifi_p2p_menu_searching`). The other waits follow its nominal shape: *Calcolo dell'itinerario migliore…*, *Lettura del manifest…*, *Ricerca della posizione…* |
| Update available | Aggiornamento disponibile | Android's own wording (`settings:android_version_pending_update_summary`). |
| Check for update | Cerca aggiornamenti | Android's own wording (`android:unsupported_compile_sdk_check_update`). |
| Replace | Sostituisci | Android's own word (`settings:vpn_replace`). |
| Language | Lingua | Android's own word (`settings:app_locale_preference_title`). |
| Location / position | posizione | Android's own word (`android:permgrouplab_location`), on the button and in the sentences. The service being switched on or off is *la localizzazione*, the noun of Android's *Servizi di localizzazione* (`settings:location_services_preference_title`) — the system has no row for the switch under that name, and this shortening is this file's own. |
| Storage | Archiviazione | Android's own section word (`settings:storage_category`), over the longer *Spazio di archiviazione* it uses for the screen. |
| Cancel / Yes | Annulla / Sì | Android's own (`android:cancel`, `settings:yes`). |
| No connection | Nessuna connessione | Android's own wording (`settings:mobile_data_no_connection`). |
| Can't … | Impossibile … | The system's shape for a failure — *Impossibile salvare il file*, *Impossibile leggere i dati* — of which `settings:wifi_add_app_network_save_failed_summary` and dozens of others are instances. |
| app | app | Android's own word for an application (`settings:apps_dashboard_title`), including in the Settings path quoted in `about_links_body`. *Applicazione* is not wrong, but the system says *app* and so does this file, once and everywhere. |
| offline | offline | **Not Android's**: the lexicon has no row for it. It is the word Italian actually uses, in the stores and out of them, and *senza connessione* would have made *Dati offline* and *Gestisci i dati offline* considerably heavier for nothing. |
| tracker | tracker | **Not Android's** either, and deliberate: it is the word Italian privacy writing uses, including F-Droid's own. *Sistema di tracciamento* explains it; it does not name it. |
| manifest | manifest | **Not Android's**: the lexicon has no row for it, and this is the third departure. Italian technical writing says *il manifest* for the file, where *manifesto* is a poster or a political text — somebody who has just tapped *Cerca aggiornamenti* would read the wrong noun. |
| conurbation | area urbana | Neutral, where *conurbazione* is a planner's word and *area metropolitana* would be wrong about Auray, which is three megabytes and no metropolis. |
| town (in an address) | comune | The municipality, in the address search and its failures. It is what every Italian and every Swiss address form is labelled, it is precise where *città* is not, and it keeps *città* for the conurbation the application serves — two different things one screen apart. |
| file | file | Android's own (`android:mime_type_generic`). |
| feed (GBFS) | flusso | The network's published stream, in the errors and in the welcome. |
| dataset | set di dati | The three of them together, in `storage_intro` and in the store text. |
| map data / tiles | Sfondo cartografico | The name the storage screen gives the dataset, and the one every other string must use for it, `map_needs_tiles_title` included. *Mappa di base* would have read more plainly and is feminine, which is the reason it was not chosen — see below. |
| routing data | Grafo dei percorsi | The project's own term, used in the store text and in `journey_graph_missing` too. More technical than the English "routing data", and kept deliberately: it is one object with one name. |
| address index | Indice degli indirizzi | — |
| metered / unmetered | a consumo / non a consumo | Android's own wording for a metered network (`settings:wifi_metered_label`, `settings:wifi_unmetered_label`). The setting's description then explains it as billing by the megabyte, exactly as the English does. |
| by default | per impostazione predefinita | Android's own (`settings:launch_by_default`), which is what makes the Settings path in `about_links_body` match the system word for word. It is written **once**, there: `settings_opening_title` says *All'avvio apri l'app su* instead, so that the reader does not meet the same five words twice for two different settings — one of this application's, one of Android's. |
| one's own bike | la tua bici **personale** | The English "own" is carried, and not dropped into *la tua bici*: `values/strings.xml` asks above `settings_own_bike_kind_title` that the wording stay clearly about the reader's own equipment, because the journey screen holds a separate, similar-looking choice about the bikes the **network** lends. *Personale* is Italian's natural intensifier here — *la mia bici personale*, like *il computer personale* — where a calque of French *ton propre* would stack two possessives Italian does not stack. It is carried through all six strings, the switch, the setting and the four summaries, so the three screens stay about one object. |
| favourites | Preferiti | The word Italian interfaces use, and the one a reader looks for. |

The three dataset names are all **masculine singular** — *sfondo cartografico*,
*grafo dei percorsi*, *indice degli indirizzi* — and that is why the first one
is not *mappa di base*. `dataset_imported` and `dataset_deleted` are one string
each for all three ("%1$s installato", "%1$s eliminato"), so a feminine name
among two masculine ones would have broken the agreement for one dataset out of
three or forced both strings into a participle-free circumlocution. The plainer
noun lost to the agreement, and the purpose line under it — *Disegna la mappa
senza rete* — says what it is.

## Where this file departs from its own rules

Two strings do, and both on purpose:

- **`welcome_data_body`** translates "the route calculation" as *il calcolo
  **degli itinerari*** and not *dei percorsi*, which is the only place the
  journey/route split above is crossed without the English changing word. It is
  the right way round: what that sentence says is computed on the device is the
  door-to-door journey, not the line on the ground. `storage_intro` and
  `dataset_routing_purpose` say *itinerari* for the same reason; *percorso* is
  kept for `journey_no_route`, `dataset_routing` and the attributions, which
  really do mean the ground.
- **`download_held_back_title`** is *Download **in attesa*** and not the
  *trattenuto* that would mirror the French *retenu* and the Spanish
  *retenida*. Italian holds back a person, not a transfer; and the distinction
  the two dialogs draw is worth keeping legible — this download has **not
  started**, where `download_stopped_title` (*Download interrotto*) had started
  and stopped. *Sospeso* would have said the second thing. *In attesa* says the
  first, and reads as one phrase with `download_waiting_for_unmetered`, which
  already opens *In attesa di una connessione non a consumo*.

## Plurals

Italian has **three** categories: `one`, `many`, `other`. `many` is only
reached at a million, and it is written out all the same: what a language
distinguishes is a fact about the language, not about the counts this
application shows. What it holds is the **di** a million takes —
*1.000.000 di stazioni* — which is the same shape French and Spanish give
their own `many`.

`counterpart_bikes` and `counterpart_docks` carry no placeholder in any item,
on purpose: the figure is already painted in the disc these words stand beside,
and an item holding it would say it twice. `CounterpartAgreementTest` fails the
day one comes back.

## The order of an address

`address_with_number` is written **`%2$s %1$s`** and not `%1$s %2$s`: Italian
puts the street before the number — "Via Roma 12" — where English puts the
number first. This is precisely what positional placeholders exist for, and it
is the only string in the file whose placeholders are reordered. No comma
stands between the two, which is how Poste Italiane writes an address.

`address_search_hint` follows it — **Via, numero, comune**, and not the English
*Number, street, town*. The search engine reads a house number in the three
orders that are written (SPEC §4.3): opening the query, closing it, or
**standing between the street and the town**, which is Italian's ordinary order
and the reason this prompt may name it. A postcode is dropped before the number
is looked for and the Italian CAP is a single group of five digits, so
"Via Roma 12 20121 Milano" resolves as readily as "Via Roma 12 Milano" — it is
left out of the prompt only because three items is as much as a hint can carry.

Two things the prompt must not invite:

- **a second number.** A number that does not open the query is given up as
  soon as another appears — the guard that keeps a street named after a date
  written in full whole. Italian's own dated streets are mostly written in
  Roman numerals — Via XX Settembre, Piazza XXIV Maggio — which hold no digit
  and never reach the guard at all; the ones written in Arabic numerals and
  **without a preposition**, Via 25 Aprile, are the gap `readMedianNumber`'s
  KDoc names openly, and telling them from a street plus a number needs the
  month names of the language. The cost is bounded: the words still name the
  street, and only the doorway inside it is taken from a number that was never
  one. The prompt naming one number and one town is as far as it can honestly
  go.
- **an "a" after the number.** `config/address-normalization/it.json` lists *a*
  among the stop words, and a number between street and town is only read when
  neither neighbour is one. So "via Roma 12 a Milano" gives its number up and
  returns the street entire. That is a decision the code states in
  `readMedianNumber`'s KDoc — a lone *a* is a repetition mark in German and an
  article in Italian and Spanish, and giving up the mark costs a doorway where
  giving up the article would cost the address. The prompt therefore writes the
  town straight after the number, with nothing between them.

## Words that are not translated

Product and network names — Roue Libre, Vélib', V'lille, Vélo'v, BikeMi, Citi
Bike, BRouter, MapLibre, OpenStreetMap, GBFS, Wi-Fi — the licence names, and
Base Adresse Nationale, which is the proper name of a French dataset. Unit
symbols (m, km, ft, yd, mi, min, h, B, kB, MB, GB) stay as they are. City names
take their Italian form where Italian has one — Parigi, Lione, Barcellona,
Praga, Copenaghen — and keep their own where it does not: New York, Buenos
Aires, Riga, Pristina, Auray, Lille. `resources` `name` attributes, always.

One string comes back identical to the English and is right to:
`about_privacy_title`, **Privacy**, which is the word Android's own Italian
puts on that screen (`settings:privacy_dashboard_title`).
