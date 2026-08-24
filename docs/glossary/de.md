# German glossary

The terms `res/values-de/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different German ones over three screens, and so that a contributor can correct
one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Register and typography

The German says **du**, in the interface and in the store texts alike, as the
French says *tu* (SPEC §9). It is also what Android itself does: of the 5 900
system strings extracted from this phone's `framework-res.apk` and
`Settings.apk`, sixty-nine address the reader as *du* and not one as *Sie*.
Imperatives follow — "Tippe", "Wähle", "Versuch es erneut", "Zieh die Liste
nach unten".

Quotation marks are **„ … “**. Nouns take a capital. **ß** is written where the
language writes it (*außerhalb*, *Größe*, *schließen*), never *ss*. The dash
that breaks a sentence is **–** with a space on either side, not the em dash the
English and French files use; that is the one piece of punctuation changed
inside a format-only string, `city_label` (`%1$s – %2$s`). The apostrophe, where
a name needs one (*Vélib’*, *V’lille*), is **’** (U+2019) and never the straight
quote, which is why nothing in the file is escaped.

German compounds are long, and this is the language that breaks the layouts.
Where a label had to stay short, it takes the wording Android itself uses rather
than the literal translation.

## The vocabulary

| English | German | Why |
|---|---|---|
| journey | Route | The whole door-to-door thing: the screen, the settings section, the button, the errors. It is what a German mapping application calls a planned trip, and it is short, which matters on `journey_compute` and `journey_frame`. |
| journey data (store texts) | Fahrtdaten, deine Fahrten | The one place *Route* is deliberately not used. "Keine Routendaten gespeichert" would collide head-on with `dataset_routing`, which **is** called *Routendaten* and **is** stored on the device — the sentence would say the opposite of the truth. *Fahrtdaten* is not a one-off either: `welcome_privacy_body` already says "weder deine Fahrten noch deine Positionen", so the store bullet and the welcome page use the same word for the same thing. Do not "correct" it back to *Route*. |
| ride | Radfahrt / Fahrt | The bike leg alone, inside a journey. A different word from *Route*, so the elevation profile and the "own bike" wait cannot be mistaken for the whole thing. |
| route | Weg | Only in `journey_no_route`, "Kein befahrbarer Weg zwischen diesen beiden Punkten": the line on the ground, not the planned journey. Also in `station_beyond_area`, for the same reason. |
| bike | Rad, Räder | Shorter than *Fahrrad* by four characters on a label that is repeated on every marker, every row and every disc, and it is what German bike-share networks write. *Fahrrad* appears nowhere, so nothing reads as two words for one object. |
| bike-share bikes, as a product | Leihräder | Only in the store texts and on the welcome page, where the thing has to be named before it is known. Inside the interface the context is settled and *Rad* is enough. |
| an address, as written | Straße, Hausnummer, Ort | German writes the street before the number, and `address_search_hint` asks for it that way. `AddressQuery.parseQuery` reads a number standing between street and town since 9510db7c, so the German order is understood as typed. **How a result is printed back is a separate matter and is not this file's to decide**: the layout of an address belongs to the country the address is in, not to the reader's language (SPEC §4.3), so "Bahnhofstraße 12" is what a Karlsruhe address reads as for every reader and "12 rue Nationale" is what a Lyon one reads as for a German reader. `address_with_number` and `address_number_with_suffix` were removed from every translation; the layouts are a table in `core/address/AddressLayout.kt`, keyed on the language of the address base. The **postcode is deliberately left out of the hint**: the parser only strips one written as a single group of five digits, which is Germany but neither Austria nor Switzerland, and a second number in the query makes the house number be given up. Asking three German-speaking countries for a postcode would break the reading for two of them. |
| station | Station | A bike-share station. A railway station is a **Bahnhof** — which is what `address_search_prompt_message` means by "stations", and it says *Bahnhöfe*. |
| dock (free) | freier Platz, Platz | What one returns a bike into, counted as available: "6 Räder, 26 Plätze". |
| dock (capacity) | Stellplatz | The same object counted as a total, which is a different figure on the same screen: "12 freie Plätze · 30 Stellplätze". English says "dock" for both; German does not have to. *Fahrradstellplatz* is the ordinary German for the spot a bike is parked in. |
| dock | *never* „Säule“, *never* „Terminal“ | Those are the payment post, not the point a bike attaches to. |
| mechanical / electric | mechanisch / elektrisch | Kept as adjectives, as in English, because the counts are elliptical: "4 mechanische · 2 elektrische" stands for "4 mechanische Räder". *E-Bike* was left aside: it is a brand-flavoured noun and would not agree with the mechanical half beside it. `journey_bike_kind_electric_description` says **Tretunterstützung** so that "electric" cannot be read as a moped. |
| pace (walking) | Gehtempo | A pace is not a speed, which `values/strings.xml` says above the string. *Tempo* is a pace; *Geschwindigkeit* is the figure nobody has measured about themselves, and is not used. Slow / Normal / Brisk are **Langsam** (Android's own word) / **Normal** / **Zügig**. |
| climb | Anstieg | The metres climbed, over a leg or over the whole journey: "120 m Anstieg". |
| location / position | Standort / Position | Two words, as in English and for the same reason. **Standort** is the system feature and the permission — Android's own word — and is what `map_locate_me` and `map_location_denied` speak of. **Position** is where the reader actually is, the point on the map: "Meine Position", "Deine Position liegt außerhalb …". |
| conurbation | Ballungsraum | The city screen serves a metropolitan area rather than a municipality, and *Stadt* is kept for the shorter word the settings section and the title need. |
| Settings | Einstellungen | Android's own word, including in the system path quoted in `about_links_body` ("Einstellungen → Apps → … → Standardmäßig öffnen"), which is Android's own German for that screen. |
| Theme | Design | Android's own word: *Dunkles Design*, *Gerätedesign*. *Thema* would be the subject of a text. Light / Dark are **Hell** / **Dunkel**. |
| Display (section) | Display | Android's own name for the section that holds the theme and the text size, untranslated in German too. |
| Delete / Remove | Löschen / Entfernen | *Löschen* destroys — a city's data, a dataset, a picked point. *Entfernen* takes out of a list, and is used for favourites only. Android distinguishes them the same way. |
| Clear (a search) | Suche löschen | Android says *Suchanfrage löschen* for the icon inside a field, but the same English string is also a button in an empty state, where nineteen characters is long. One wording serves both, and it keeps Android's verb. |
| Refresh | Aktualisieren | Android's word for data. It is also *aktualisiert* in the freshness lines, so the button and what it produces read as one thing. |
| Try again | Erneut versuchen | Android's own, and the shortest of the three it uses (*Noch einmal versuchen*, *Noch mal versuchen*). |
| Continue | Weiter | Android's button word, and four characters against *Fortfahren*'s ten — the welcome carousel and the what's-new screen both end on it. |
| Back | Zurück | Android's own, on the toolbar arrow. |
| In use | In Verwendung | Android's own, on the city already selected. |
| Out of service | Außer Betrieb | Android's own. |
| just now | gerade eben | Android's own, lower-cased because it is always read inside `freshness_fresh`: "Aktualisiert gerade eben". |
| Update available | Update verfügbar | Android's own. |
| Check for updates | Auf Updates prüfen | Android's own. |
| Storage | Speicher | Android's own, and the name the screen carries. Everywhere another string points at that screen it says **unter „Speicher“** rather than translating "storage screen" literally. |
| Wi-Fi | WLAN | What Android's German writes throughout, and what a German reader says. This is the one product-looking name that *is* translated. |
| unmetered / metered | ohne Datenlimit / pro Megabyte abgerechnet | Android labels a connection *Ohne Datenlimit* and *Kostenpflichtig*. The setting takes the first as it stands; the sentences explaining it say what is billed, since that is the point the English makes. |
| Tap | Tippe (auf) | Android's own verb, in the second person like the rest. |
| Press and hold | Halte … gedrückt | Android's own wording for a long press. |
| app | App | Android's own; *Anwendung* is not what a phone says. |
| map data / tiles | Kartendaten | Names the dataset on the storage screen, and every other string that points at it uses the same word. |
| routing data | Routendaten | Pairs with *Route*: the data a route is computed from. |
| address index | Adressindex | — |
| offline data | Offline-Daten | — |
| bytes | B, kB, MB, GB | German writes the same symbols. |
| what's new | Neuerungen | What the screen shows is the release notes. *Neuigkeiten* would also read as news from elsewhere. |

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Citi Bike, BRouter,
MapLibre, OpenStreetMap, GBFS — and the licence names. Unit symbols: `m`, `km`,
`ft`, `yd`, `mi`, `min`, `h`, which German writes as they stand (`h` rather than
`Std.`, since the same string also serves a stopwatch-style "1 h 05").
`resources` `name` attributes, always.
