# Polish glossary

The terms `res/values-pl/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Polish ones over three screens, and so that a contributor can correct
one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

Where a row says "Android's own", the word was found by grepping the 5 900
system strings extracted from this phone's `framework-res.apk` and
`Settings.apk`, and the key quoted is the key that was actually found. Where no
row existed, the entry says so rather than inventing an authority.

## Register and typography

The Polish addresses the reader informally, as the French says *tu* (SPEC §9),
and it does it the way Polish interfaces do: with the **bare imperative** and
the second person singular — *Kliknij*, *Wybierz*, *Spróbuj ponownie*,
*Pociągnij listę w dół*. No *Pan/Pani*, and no pronoun where the verb ending
already carries the person. Where the reader is addressed as a possessor, the
courtesy capital is kept: *Twoja pozycja*, *Twoje miasto*.

**The application never says *we*.** English is impersonal throughout — "No
history is kept", "It is read from the feed", "the size is announced" — and
Polish keeps it that way, with the passive or with an agentless form: *Żadna
historia nie jest zapisywana*, *Trasa jest wyznaczana na urządzeniu*, *Rozmiar
podawany przed pobraniem*, *Brak znanych stacji*. This matters most in
`welcome_privacy_body` and `about_privacy_body`: an application whose whole
argument is that nobody is behind it must state a property of the software, not
ask the reader to trust a *my*.

The **first person singular** is a different thing and is used, for the machine
narrating what it is doing right now: *Wyznaczam najlepszą trasę…*,
*Przeglądam adresy…*, *Odczytuję manifest…*. That is Android's own habit —
`pl.tsv` has *Wczytuję…* (`android:loading`), *Ładuję…*
(`settings:loading_injected_setting_summary`), *Kończę aktualizację systemu…*
(`android:android_upgrading_notification_title`).

Quotation marks are **„ … ”**. A **non-breaking space** (U+00A0) follows every
one-letter word — *w, i, z, a, o, u* — which Polish typography does not leave
at the end of a line; the file holds 113 of them and they are invisible in a
diff, so a new sentence added later has to carry its own. Diacritics are
written in full: **ą ć ę ł ń ó ś ź ż**. The apostrophe, where a network name
carries one (*Vélib’*, *V’lille*, *Vélo’v*), is **’** (U+2019) and never the
straight quote, which is why nothing in the file is escaped. The dash that
breaks a sentence is **—** with a space on either side.

**Polish declines, and a placeholder cannot be declined.** Every sentence built
around a `%1$s` is turned around so that the placeholder falls where the
nominative falls, or is introduced by a colon or by an apposition:
`city_proposal_body` says "na obszarze, który obejmuje %1$s" rather than
"obszarze obsługiwanym przez %1$s"; `station_address_nearby` says "W pobliżu:
%1$s" rather than gluing a genitive onto a street name; `journey_step_ride`
says "Jedź do stacji %1$s", where *stacji* carries the case and the name stays
as the feed wrote it.

**But the apposition must be true.** Putting a noun in front of the placeholder
is only allowed when the placeholder really holds a thing of that kind. *Jedź do
stacji %1$s* is sound because the argument is a station's name. The three
`city_delete_*` strings once read *dane miasta %1$s*, and that was wrong: the
argument is `city.displayName`, which is the **network's** name and not the
municipality's — 328 of the 331 entries in `config/catalogue.json` differ, and
every Polish one does (*VETURILO 3.0* for Warsaw, *JasKółka* for Jastrzębie
Zdrój). The sentence asserted something the English never says, which is "Data
for %1$s deleted". They now use the quotation marks instead — "Usunięto dane
„%1$s”" — which carry the apposition without claiming what the name names, and
which is already how `dataset_delete_body` and `dataset_rejected_format` handle
a name. The rule: before writing a noun in front of a `%1$s`, read the call
site.

## Plurals

Polish has four categories, and `few` is the one that catches people out: it
covers 2-4 **and** 22-24, 32-34, 42-44…, while 12-14 fall into `many`. Every
form below therefore reads correctly for the whole of its range, not only for
its smallest number.

| | one | few | many | other |
|---|---|---|---|---|
| bikes | 1 rower | 2 rowery | 5 rowerów | 1,5 roweru |
| free docks | 1 wolne miejsce | 2 wolne miejsca | 5 wolnych miejsc | 1,5 wolnego miejsca |
| docks (capacity) | 1 stojak | 2 stojaki | 5 stojaków | 1,5 stojaka |
| stations | 1 stacja | 2 stacje | 5 stacji | 1,5 stacji |
| minutes ago | 1 minutę temu | 2 minuty temu | 5 minut temu | 1,5 minuty temu |

`other` is only ever resolved for a fractional number, which none of these
counts is today; it is written in the genitive singular all the same, because
what a language distinguishes is a fact about the language and not about the
current call sites.

## The vocabulary

| English | Polish | Why |
|---|---|---|
| journey | trasa | The whole door-to-door thing: the screen, the settings section, the button, the errors. It is what a Polish mapping application calls a planned trip — *wyznacz trasę* — and it is short, which matters on `journey_compute` and `journey_frame`. |
| a rider's trips (privacy texts) | przejazdy | The one place *trasa* is deliberately not used. "Nie zapisujemy tras" would collide head-on with `dataset_routing`, which **is** called *Dane tras* and **is** kept on the device: the sentence would say the opposite of the truth. **All three** privacy texts say it — `welcome_privacy_body`, `about_privacy_body` and the store description — so they read as one claim. The rule is: wherever the sentence is about what is *not* kept, the word is *przejazd*. Do not "correct" it back to *trasa*. |
| ride | przejazd | The bike leg alone, inside a journey: `journey_computing_own_bike`, `journey_detail_profile`. A different word from *trasa*, so the elevation profile and the own-bike wait cannot be mistaken for the whole thing. |
| route | droga | Only in `journey_no_route` — "Brak przejezdnej drogi między tymi punktami" — and in `station_beyond_area`: the line on the ground, not the planned journey. |
| bike | rower | The ordinary word, and the one every Polish bike-share network prints. |
| bike-share bikes, as a product | rowery miejskie | Only in the store texts and on the welcome page, where the thing has to be named before it is known; *rower miejski* is what Poland calls the whole product. Inside the interface the context is settled and *rower* is enough. |
| station | stacja | A bike-share station. A railway station is a **dworzec** — which is what `address_search_prompt_message` means by "stations", and it says *dworców*. |
| dock (free) | wolne miejsce | What one returns a bike into, counted as available: "6 rowerów, 26 wolnych miejsc". |
| dock (capacity) | stojak | The same object counted as a total, which is a different figure on the same screen: "12 wolnych miejsc · 30 stojaków". English says "dock" for both; Polish does not have to, and *stojak* is exactly the post a Polish shared bike locks into. |
| dock | *never* „terminal”, *never* „słupek” | Those are the payment post, not the point a bike attaches to. |
| mechanical / electric | zwykły / elektryczny | **A departure from the literal.** *Rower mechaniczny* is not Polish; what a Pole says is *zwykły rower* against *rower elektryczny*, and that is also what the operators write. Kept as adjectives, as in English, because the counts are elliptical: "4 zwykłe · 2 elektryczne" stands for "4 zwykłe rowery". `journey_bike_kind_electric_description` says **wspomaganie pedałowania** so that "electric" cannot be read as a moped, and `journey_bike_kind_mechanical_description` says **napędzany wyłącznie siłą nóg**. |
| pace (walking) | tempo marszu | A pace is not a speed, which `values/strings.xml` says above the string. *Tempo* is a pace; *prędkość* is the figure nobody has measured about themselves, and appears nowhere. Slow / Normal / Brisk are **Wolne** / **Normalne** / **Żwawe** — neuter, agreeing with *tempo*, and *żwawo* is how Polish says brisk on foot. Android's own *Wolna* (`settings:speed_label_slow`) is feminine because it qualifies a speed; here the noun is different, hence the ending. |
| climb | przewyższenie | The metres climbed, over a leg or over the whole journey: "120 m przewyższenia". The standard Polish word for elevation gain, in cycling and in hiking alike. |
| location / position | lokalizacja / pozycja | Two words, as in English and for the same reason. **Lokalizacja** is the system feature and the permission — Android's own word, `settings:location_settings_title` — and is what `map_location_denied` speaks of. **Pozycja** is where the reader actually is, the point on the map: "Moja pozycja", "Twoja pozycja leży poza obszarem…". |
| conurbation | aglomeracja | The city screen serves a metropolitan area rather than a municipality; *miasto* is kept for the shorter word the settings section and the titles need. |
| Settings | Ustawienia | Android's own (`settings:dashboard_title`), including in the system path quoted in `about_links_body`: "Ustawienia → Aplikacje → Roue Libre → **Otwieraj domyślnie** → **Dodaj link**", each step taken from `settings:launch_by_default` and `settings:app_launch_add_link`. |
| Theme | Motyw | Android's own: *Ciemny motyw* (`settings:dark_ui_mode`), *Motyw urządzenia* (`settings:device_theme`). Dark is **Ciemny**, from the same rows; **Jasny** for light has no row in the lexicon and is the ordinary Polish antonym. |
| System (as a choice) | Systemowy | Agrees with what it qualifies: *Systemowy* for `settings_theme_system` and `settings_language_system` (motyw, język — both masculine), *Systemowe* for `settings_units_system` (jednostki). Android writes both forms, `settings:header_category_system` and `settings:trusted_credentials_system_tab`. |
| Display (section) | Wyświetlanie | **A departure from Android**, which calls the hardware screen *Wyświetlacz* (`settings:display_settings`). This section holds the theme, the units, the opening screen and the map filters — how things are shown, not the panel they are shown on — so the gerund is used instead. |
| Delete / Remove | Usuń / Usuń z ulubionych | **Polish does not have two verbs here**, and saying so is more use than inventing one: the lexicon gives *Usuń* for Delete (`android:delete`) and *Usuń* for Remove (`settings:remove`) alike. The distinction is carried by the complement instead — `station_favourite_remove` is "Usuń z ulubionych", which no Polish reader takes for destroying a station, while `city_delete` and `dataset_delete` are the bare "Usuń". That construction is Android's own answer to the same problem: `settings:demote_conversation_summary` is "Remove from the conversation section" → **"Usuń z sekcji rozmów"**. Do not reach for *skasuj* or *wyrzuć* to force a contrast Polish does not draw. |
| Clear (a search) | Wyczyść wyszukiwanie | Android's verb (`settings:clear`). One wording serves both the icon inside the field and the button in an empty state. |
| Refresh | Odśwież | The lexicon has no bare "Refresh", only *odświeżać dane* inside `settings:auto_sync_account_summary`; the imperative is formed from it. *Zaktualizowano* is what the freshness line then says, so the button and its result stay one thing. |
| Try again | Spróbuj ponownie | Android's own, `settings:network_connection_timeout_dialog_ok`. |
| Continue | Dalej | Android's button word, `settings:lockpattern_continue_button_text`, and five characters — the welcome carousel and the what's-new screen both end on it. |
| Skip | Pomiń | Android's own, `android:skip_button_label`. |
| Back | Wstecz | Android's own on the toolbar arrow, `settings:back`. |
| In use | W użyciu | Android's own, `android:media_route_status_in_use`, on the city already selected. |
| Out of service | Nie działa | Android's own, `settings:radioInfo_service_out`. |
| just now | przed chwilą | Android's own, `settings:time_unit_just_now`, lower-cased because it is always read inside `freshness_fresh`: "Zaktualizowano przed chwilą". |
| Update available | Dostępna aktualizacja | Android's own, `settings:android_version_pending_update_summary`. |
| Check for updates | Sprawdź aktualizacje | Shortened from Android's *Sprawdź dostępność aktualizacji* (`android:unsupported_compile_sdk_check_update`), which is twenty-eight characters on a button that has to sit beside a progress line. |
| Replace | Zastąp | Android's own, `settings:vpn_replace`. |
| Cancel / Yes | Anuluj / Tak | Android's own, `android:cancel` and `settings:yes`. |
| Storage | Pamięć | Shortened from Android's *Pamięć wewnętrzna* (`settings:internal_storage`, `settings:storage_label`), which is too long for a toolbar title. Every other string that points at that screen quotes it the same way: **na ekranie „Pamięć”**. |
| Wi-Fi | Wi-Fi | Untranslated in Polish too, `settings:wifi`. |
| unmetered / metered | bez naliczania opłat / rozliczane za megabajty | Android's Polish for a metered connection is a whole sentence — *Użycie danych jest mierzone* (`settings:wifi_metered_label`) — which does not fit a switch label. The setting and the messages say what is billed, which is the point the English makes. |
| Tap | Kliknij (…, aby …) | Android's dominant Polish verb for a touch, e.g. `android:usb_notification_message`, "Kliknij, by wyświetlić więcej opcji". |
| Press and hold | Naciśnij i przytrzymaj | Android's own wording for a long press, `settings:power_menu_setting_name` and `android:content_description_sliding_handle`. |
| app | aplikacja | Android's own, `settings:apps_dashboard_title`. *Apka* is spoken, not written. |
| opening screen | Ekran startowy | **A departure.** The literal "Otwieraj domyślnie" is Android's name for link handling and is quoted as such three strings away in `about_links_body`; naming the start-up screen the same way would make one phrase mean two things on one screen. |
| map data / tiles | Dane mapy | Names the dataset on the storage screen, and every other string that points at it uses the same word. Its own title says *kafelki*, the ordinary Polish for map tiles, only where the map itself is speaking (`map_needs_tiles_title`). |
| routing data | Dane tras | Pairs with *trasa*: the data a route is computed from. |
| address index | Indeks adresów | — |
| offline data | Dane offline | *Offline* is the word Polish uses; *bez sieci* is the phrase used when the sentence explains rather than labels. |
| bytes | B, kB, MB, GB | Polish writes the same symbols. |
| duration | min, godz. | "1 h 05" — minutes padded to two digits with no unit after them — is a **French** convention, not a universal one, and Polish writes a duration out: **"1 godz. 5 min"**. `duration_hours_minutes` is therefore `%1$d godz. %2$d min` and drops the English padding, which the validator deliberately does not compare (see `tools/check_translations.py`, `placeholders`); Japanese does the same with `%1$d時間%2$d分`. *godz.* is Android's own abbreviation, `android:time_picker_hour_label`. "1:05" was rejected: in an application full of transit information it reads as a clock time. |
| what's new | Nowości | What the screen shows is the release notes. *Wiadomości* would read as news from elsewhere. |
| About | O aplikacji | Android says *Informacje o telefonie* (`settings:about_settings`) for the phone's own screen; *O aplikacji* is what Polish applications put on this one, and it fits a toolbar. |

## The address prompt, and what it costs in Polish

`address_search_hint` is **„Ulica, numer, miasto”** — the street first and the
number after it, which is the only order Polish writes.

**What a result is printed as is a separate matter, and is not this file's to
decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3): "Marszałkowska 12A" is how a Warsaw address is written for
every reader of the application, and "12 bis rue Nationale" is how a Lyon one is
written for a Polish reader. The two formats that used to live here,
`address_with_number` and `address_number_with_suffix`, were removed for that
reason; the layouts are a table in `core/address/AddressLayout.kt`, keyed on the
language of the **address base**.

Poland's entry is the one that carries **no space** before the letter, "12A",
where France's carries one, "12 bis" — which is why the table holds the suffix
separator as a field of its own rather than reusing the one before the street
name. Lettered numbers are not an edge case in Poland; they are ordinary, so
that entry is read far more often than the French repetition marks the format
was first written for.

`AddressQuery.parseQuery` reads a house number standing **between the street
and the town**, which is exactly the shape the prompt asks for, so a query typed
as invited is understood as typed. The prompt invites **one number and no
postcode**, and both restrictions are deliberate:

- a second number in the query makes the house number be given up altogether,
  so nothing may be invited that stacks two;
- a Polish postcode is written **in two groups**, "00-001", which the parser
  does not recognise as a postcode at all — it reaches the words as "00" and
  "001" — so inviting one would poison the query rather than help it.

**The known limit, written here because it will otherwise be rediscovered as a
bug.** The second guard protecting street names that carry a number is that
neither neighbour of the number may be a stop word — *rue **du** 8 Mai*,
*Avenida 9 **de** Julio*. **Polish writes its dates without a preposition**:
*Aleja 3 Maja*, *ulica 1 Sierpnia*. Neither *aleja* nor *maja* is a stop word,
so the parser will read the 3 as a house number and look for number 3 in a
street called "Aleja Maja". The cost is bounded and it is written into
`SPEC.md` §4.3 and into the KDoc of `readMedianNumber`: the remaining words
still name the street, so the street is still found and shown — only the
doorway inside it is wrong. Telling those apart needs the month names of the
language, which belong in `config/address-normalization/pl.json` rather than in
the parser, and are not there today.

The prompt was written knowing this. It was **not** narrowed to "Ulica, miasto"
to dodge the problem: house numbers are what makes the journey accurate to
within a doorway rather than a street (SPEC §4.3), Polish riders type them, and
a prompt that hid them would cost every correct address to protect a handful of
commemorative street names.

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Vélo’v, Citi Bike,
BRouter, MapLibre, OpenStreetMap, GBFS — and the licence names. Unit symbols:
`m`, `km`, `ft`, `yd`, `mi`, `min`, `h`. City names take their Polish form where
Polish has one — Praga, Ryga, Paryż, Nowy Jork, Kopenhaga — and stay as written
where it has none. `resources` `name` attributes, always.
