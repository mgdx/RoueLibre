# Spanish glossary

The terms `res/values-es/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Spanish ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Register, region and typography

The Spanish says **tú** (SPEC §9), in the interface and in the store texts
alike — never *usted*, and never *vos*: the catalogue serves networks in Spain,
Mexico, Argentina and Chile, and the interface has to be read the same way in
all four. Buttons are infinitives, as Android's own are — *Actualizar*,
*Reintentar*, *Cancelar* — while the sentences that instruct are second-person
imperatives: *Toca*, *Elige*, *Comprueba*, *Vuelve a intentarlo*.

Regionalisms are left out on both sides. *Fichero*, *coger*, *móvil* as the
only word for a phone and *ir a por* belong to Spain; *celular*, *computadora*
and *manejar* belong to America. Where Android's own Spanish has a word, that
word wins: an application that calls the settings anything but **Ajustes** reads
as a foreign one. The arbiter is the system lexicon extracted from
`framework-res.apk` and `Settings.apk`.

The **compound perfect is kept out of the status messages**, and that is a
decision rather than an oversight: *no se ha encontrado*, *ha devuelto*, *se ha
detenido* are understood everywhere but are heard as peninsular, and they are
the loudest regional marker a file like this can carry. So an error or an empty
state says what is true now — *No se encuentra ninguna estación*, *No se puede
copiar el archivo*, *Error del servidor de la red*, *la descarga se detiene* —
which is better Spanish for a status message in any case. The perfect is left
where it belongs to the reader's own doing: *las ciudades que no has
instalado*.

Typography: a question opens with **¿** and an exclamation with **¡**;
quotations are **« »**; no space stands before `:`, `;`, `?` or `!`. Spanish
writes no apostrophe, so nothing in the file is escaped — the trap French has
does not exist here.

Numbers keep the decimal comma and the thousands point: "42,5 MB", "1,3 GB".
The size units are the international symbols — B, kB, MB, GB — since Spanish
says *byte*, not *octeto*.

## The vocabulary

| English | Spanish | Why |
|---|---|---|
| journey | ruta | The whole door-to-door thing: the screen, the settings section, the button, the store text. It is the word every mapping application in Spanish uses for a computed trip, and it is short enough for a section title. |
| ride | trayecto en bici | The bike leg alone, inside a journey. A different word from *ruta*, so "the ride, uphill and down" and "the journey in detail" stay about two different objects. |
| route | camino | Only in "no practicable route": the line on the ground, not the planned journey — "No hay ningún camino practicable entre estos dos puntos". |
| to ride | pedalear | The verb of the bike leg — *Pedalea hasta…* — against *caminar* for the walking ones. |
| station | estación | A bike-share station. Spanish uses the same word for a railway station, which is exactly what `address_search_prompt_message` means; there it is written out as **estación de tren** so the two cannot be confused. |
| bike | bici, bicis | Not *bicicleta*. It is what the Spanish-speaking networks call their own vehicles — Bicing, BiciMAD, Ecobici — it is neutral across regions, and it fits in a list row and on a station's sheet where *bicicletas* does not. Feminine, so *mecánica* and *eléctrica* agree with it everywhere. |
| dock (free) | espacio libre | What a bike is returned into, counted as available. Ecobici's own wording, understood in Spain as readily as in Mexico, where *anclaje libre* is Spain's alone and *plaza* would collide with the town squares of the address search. |
| dock (capacity) | anclaje | The same object counted as a total, which is a different figure on the screen: "12 espacios libres · 30 anclajes". English says "dock" for both; Spanish does not have to. |
| dock | *never* «borne», *never* «terminal» | A terminal is what one pays at, not the point a bike attaches to. |
| bike, electric | eléctrica | Pedal-assist, never a moped: `journey_bike_kind_electric_description` says *con asistencia al pedaleo* in full. |
| pace (walking) | ritmo al caminar | A pace is not a speed: `values/strings.xml` says so above the string, and *velocidad de marcha* would say the opposite. *Ritmo* is masculine, hence *Lento / Normal / Rápido* and not the feminine forms Android uses beside *velocidad*. |
| climb | desnivel | The metres climbed over a leg or a journey. |
| leg (of a journey) | tramo | "The walking parts of a journey" — *los tramos a pie de una ruta*. |
| Settings | Ajustes | Android's own word, everywhere including the system path quoted in `about_links_body`. |
| Search | Buscar | Android's own word for the action and the field. |
| Clear | Borrar | Android's word for emptying a field, which is what "clear the search" does. |
| Refresh | Actualizar | Android's word for data. |
| Try again | Reintentar | Android's word on a button. The sentences that ask for the gesture say *Vuelve a intentarlo*, which is Android's wording in running text. |
| Back | Atrás | Android's word on the toolbar's back arrow. |
| Tap | Toca | Android's own verb, in the second person like the rest. |
| Press and hold | Mantén pulsada | Android's own wording for a long press. |
| Delete / Remove | Eliminar / Quitar | Android distinguishes them and so does this file: *Eliminar* destroys data, *Quitar* takes a station out of the favourites. |
| Skip | Saltar | Android's word, over the *Omitir* some stores use. |
| Show | Mostrar / Ver | *Mostrar* where something hidden is revealed — what the map counts, what the list shows. **Ver** where a button opens a screen or a place: *Ver el mapa*, *Ver los favoritos*, *Ver las novedades*, and `incoming_show_me`. The application's buttons are already built on *Ver*, and one word for one gesture matters more here than the system's `condition_expand_show`. |
| About | Acerca de | Android's own row is *Información del teléfono*, which is about the device; an application's about screen is *Acerca de*, which is what F-Droid and the Play Store call it. |
| In use | En uso | Android's own word, on the city already installed. |
| Out of service | Fuera de servicio | Android's own wording. |
| just now | justo ahora | Android's own wording, and it reads as one phrase with *Actualizado %1$s*. |
| Update available | Actualización disponible | Android's own wording. |
| Replace | Reemplazar | Android's own word. |
| Language | Idioma | Android's own word. |
| Location / position | ubicación | Android's own word, on the permission and on the button alike. Never *posición*. |
| app | aplicación | Never *app*, and never *apli*. |
| offline | sin conexión | Both as a qualifier — *datos sin conexión* — and as a state. |
| conurbation | área metropolitana | Neutral where *aglomeración* is a calque and *conurbación* is a planner's word. |
| town (in an address) | localidad | Read the same way in Spain and in America, where *municipio* leans peninsular. |
| file | archivo | *Fichero* is Spain's alone. |
| feed (GBFS) | flujo | The network's published stream, in the errors and in the welcome. |
| tracker | rastreador | The store texts and `about_privacy_body`. |
| map data / tiles | mapa base | The name the storage screen gives the dataset, and the one every other string must use for it — including `map_needs_tiles_title`. |
| routing data | grafo de rutas | The project's own term, used in the store text and in `journey_graph_missing` too. More technical than the English "routing data", and kept deliberately: it is one object with one name. |
| address index | índice de direcciones | — |
| dataset | conjunto de datos | — |
| unmetered connection | conexión de uso no medido | Android's own *uso medido* for a metered network. The setting's description then explains it as billing by the megabyte, exactly as the English does. |
| by default | por defecto | For the application's own setting. The one place *de forma predeterminada* appears is `about_links_body`, which quotes an Android Settings path and has to match the system word for word. |

The three dataset names are all **masculine singular** — *mapa base*, *grafo de
rutas*, *índice de direcciones* — which is what lets `dataset_imported` and
`dataset_deleted` agree once for all three ("%1$s instalado").

## The order of an address

`address_with_number` is written **`%2$s, %1$s`** and not `%1$s %2$s`: Spanish
puts the street before the number — "Gran Vía, 12" — where English puts the
number first. This is precisely what positional placeholders exist for, and it
is the only string in the file whose placeholders are reordered.

`address_search_hint` follows it — *Calle, número, localidad*, and not the
English *Number, street, town*. The search engine reads a house number in the
three orders that are written (SPEC §4.3): opening the query, closing it, or
**standing between the street and the town**, which is Spanish's ordinary
order and the reason this prompt may name it. A postcode is dropped before the
number is looked for, so "Gran Vía 12 28013 Madrid" resolves as readily as
"Gran Vía 12 Madrid".

What the prompt must not do is invite a second number. A number that does not
open the query is given up as soon as another appears, and a number between
street and town is only read when neither neighbour is a stop word — the two
guards that keep "Avenida 9 de Julio" and "Calle 20 de Noviembre" whole, which
are streets of cities this application serves. Naming one number and one town
is exactly as far as the prompt can go.

## Words that are not translated

Product and network names — Roue Libre, Vélib', Bicing, Citi Bike, BRouter,
MapLibre, OpenStreetMap, GBFS, Wi-Fi — the licence names, and Base Adresse
Nationale, which is the proper name of a French dataset. Unit symbols (m, km,
ft, yd, mi, min, h, B, kB, MB, GB) stay as they are. `resources` `name`
attributes, always.
