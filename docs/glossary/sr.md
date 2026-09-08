# Serbian glossary

The terms `res/values-sr/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Serbian ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

The lexicon quoted throughout is the 8 694 Serbian strings extracted from a
real phone's `framework-res.apk` and `Settings.apk`
(`do-not-commit/lexicon/sr.tsv`). Every key named below was grepped from it;
where the file departs from Android, or where the lexicon holds no word at all,
that is said in as many words.

## The alphabet is Cyrillic, and that decision governs the whole file

The folder is `values-sr`, with no script qualifier, and that is the code
Android serves its **Cyrillic** Serbian under. It is not a judgement about
which script Serbia prefers — the country writes both — but a fact about the
resource system: every one of the 8 694 Serbian strings on the phone is
Cyrillic, from *Откажи* (`android:cancel`) through *Подешавања*
(`android:global_action_settings`) to *Меморијски простор*
(`settings:storage_category`). A Latin Serbian belongs in
`values-b+sr+Latn/`, which is a different folder and a different file.

Writing Latin under `values-sr` would therefore put this application's
interface in one script while every system menu, permission dialog and file
picker around it stayed in the other — the one outcome no reader of either
script wants. So the whole file is Cyrillic, with no mixing anywhere, and
whoever adds a Latin Serbian later adds a folder rather than editing this one.

**What stays in Latin is what is a name and not a word**: Roue Libre, GBFS,
ODbL, MIT, GNU GPL, BRouter, MapLibre Native, Base Adresse Nationale,
GeoNames, `geo:`, Wi-Fi, `transport.data.gouv.fr`, and the unit symbols.
Where such a name has to take a case ending, Serbian orthography hangs the
ending off a hyphen, which is what the phone itself does
(`settings:lineagelicense_title` = *Политика приватности LineageOS-а*). Hence
**OpenStreetMap-а** in `map_attribution`, `about_attribution_osm` and
`about_attribution_brouter`.

**Unit symbols stay international.** `distance_metres` is *%1$s m*, not
*%1$s м*; `size_megabytes` is *%1$s MB*; `duration_minutes` is *%1$d min* and
`duration_hours_minutes` *%1$d h %2$02d*. That is what a Serbian road sign
writes, what the phone's own storage screen writes, and what the SI itself
prescribes — the symbol is not a word and does not transliterate. The space
between figure and symbol is a **non-breaking** one (U+00A0) in all eleven of
those strings, as in every language: a summary that wrapped once ended a line
on "5" and opened the next on "m успона". Numbers keep the decimal comma and
the thousands stop, which the formatting APIs give for free once `sr` is in
`TRANSLATED_LANGUAGES`.

## It is Serbian, not Croatian and not Bosnian

The three are translated separately, in `values-sr/`, `values-hr/` and
`values-bs/`, and this file is the Serbian one throughout rather than a text
that tries to serve all three. Where the standards part it follows Serbian —
and, wherever the lexicon has an entry, Serbian **as the phone writes it**:

*Подешавања* and not *Postavke* (`android:global_action_settings`), *Екран*
(`settings:display_settings`), *Откажи* and not *Odustani* (`android:cancel`),
*Назад* (`android:accessibility_system_action_back_label`), *Листа* rather
than *popis* (`android:chooser_all_apps_button_label`), *Изаберите* rather
than *Odaberite* (`android:choose_account_label`), *Датотека* rather than
*fajl* (`android:mime_type_generic`), *Прегледач*
(`android:keyboard_shortcut_group_applications_browser`), *Сертификат*
(`android:ssl_certificate` = *Безбедносни сертификат*), *Мапе*
(`android:app_category_maps`, `android:keyboard_shortcut_group_applications_maps`).

Where the lexicon is silent the file follows written Serbian all the same:
**ekavian** throughout — *месец*, *где*, *време*, *пре*, *светла* — which is
what Serbia writes and what separates this file from `values-hr/`'s *mjesec*
and *prije*; *шта* rather than *što*; *историја* rather than *povijest*;
*индекс* rather than *kazalo*; *интерфејс* rather than *sučelje*; *сарадник*
rather than *suradnik*; *тачка* rather than *točka*; *рута* rather than
*put* for the line on the ground.

**The future is written as one word** — *биће избрисани* (`city_delete_body`,
`dataset_delete_body`), *мораћете*, *наставиће се* (`download_can_resume`) —
which is Serbian orthography, and exactly where `values-bs/` writes *bit će*
and *morat ćete*. It is the cheapest single tell that this file is Serbian and
not one of its neighbours; do not "correct" it.

## Register and typography

The reader is addressed with **персирање**, the polite plural. That is not a
house style but Android's own: the lexicon says *Изаберите налог*
(`android:choose_account_label`), *Проверите подешавања приступа*
(`android:view_and_control_notification_title`), *Додирните да бисте га
искључили* (`android:adb_active_notification_message`), *Пробајте да промените
жељену мрежу* (`android:NetworkPreferenceSwitchSummary`), and never the
singular in a sentence.

**Buttons take the second person singular imperative**, which is Android's
habit for a control rather than a sentence: *Настави*
(`android:autofill_continue_yes`), *Откажи* (`android:cancel`), *Избриши*
(`android:delete`), *Прескочи* (`android:skip_button_label`), *Прикажи*
(`settings:condition_expand_show`), *Замени* (`settings:vpn_replace`), *Додај*
(`settings:add`), *Обриши* (`settings:clear`), *Ажурирај*
(`android:autofill_update_yes`), *Уклони*
(`android:kg_reordering_delete_drop_target_text`).

**`action_retry` is *Пробај поново*, in the singular**, and here Serbian and
Croatian part company. `values-hr/` writes the polite plural on that one
button because Android's Croatian does; Android's **Serbian** does not —
`settings:private_space_tryagain_label` is *Пробај поново*, singular, so the
button rule and the system agree and there is nothing to arbitrate. The
sentences that end on the same idea keep the polite plural, again following
the system (`settings:wifi_check_password_try_again` = *Проверите лозинку и
пробајте поново*): `map_location_unavailable`, `journey_no_stations`,
`dataset_rejected_transfer`, `error_timeout` and the two `error_*_check` lines
all end *пробајте поново* or *пробајте касније*.

Quotation marks are **„ … “**. The dash that breaks a sentence is **–** with a
space on either side, not the em dash the English file uses; that is the one
piece of punctuation changed inside a format-only string, `city_label`
(`%1$s – %2$s`). The only em dash left in the file is `counterpart_none`,
which is a glyph standing in for an absent figure rather than punctuation.
There is no apostrophe anywhere in the Serbian text, so nothing in the file is
escaped; should a network's name ever bring one in (*Vélib’*), it is ’ (U+2019)
and never the straight quote.

## Plurals: what each category covers, not its smallest number

Serbian has three, and each has to read correctly for **every** number that
falls into it:

| Category | Numbers | Form |
|---|---|---|
| `one` | 1, 21, 31, 101… (anything ending in 1 except 11) | *1 бицикл*, *21 бицикл* |
| `few` | 2, 3, 4, 22, 23, 24… | *2 бицикла*, *23 бицикла* |
| `other` | 0, 5–10, **11–14**, 15–20, 25… | *5 бицикала*, *12 бицикала*, *0 бицикала* |

The 11–14 band is the one that gets forgotten: *11 бицикала*, not *11 бицикл*.

The eighteen `<plurals>` in the started file already carried Serbian's three
categories, so nothing had to be added or removed — only filled in.

### The five `freshness_*` plurals, and one departure from Android

**`пре` governs the genitive**, and that is what decides every ending in the
five. Three of them therefore read the same in `one` and `few`, which is
correct rather than a copy-paste: the genitive singular and the paucal after
2–4 coincide for these nouns.

| String | `one` | `few` | `other` |
|---|---|---|---|
| `freshness_seconds` | пре 1 секунде | пре 3 секунде | пре 5 секунди |
| `freshness_minutes` | пре 1 минута | пре 3 минута | пре 5 минута |
| `freshness_hours` | пре 1 сата | пре 3 сата | пре 5 сати |
| `freshness_days` | пре 1 дана | пре 3 дана | пре 5 дана |
| `freshness_months` | пре 1 месеца | пре 3 месеца | пре 5 месеци |

**Android's own Serbian writes the nominative in `one` and this file does
not.** `android:duration_minutes_relative` is *Пре # минут*,
`android:duration_hours_relative` *Пре # сат*, `android:duration_days_relative`
*Пре # дан*. The rule of this project is to follow the system, and this is the
one place the file departs from it, for a reason that only shows up at the
numbers nobody thinks of: `one` covers 21, 31, 101 as well as 1, and *Пре 21
минут* is wrong in a way *пре 21 минута* is not. A form that has to be right
for four numbers in ten cannot be borrowed from a string that was only ever
read at one. `values-hr/` and `values-bs/` write the genitive for the same
reason, so all three sister files agree.

## The words

| English | Serbian | Why |
|---|---|---|
| journey | **путовање** | The whole door-to-door thing. One word on the journey screen, in the settings section and on every button, so the three are visibly about one object. |
| ride | **вожња** | The bike leg alone. A different word from *путовање*, as the English asks. |
| route | **рута** | Only the line on the ground: `journey_no_route`, `journey_graph_missing`, `dataset_routing`. Never the planned journey. |
| walk (leg) | **пешице** | Adverbial, so it needs no agreement anywhere: *Пешице до станице*, *Пешице до одредишта*, *%1$s пешице*. |
| map | **мапа** | Not *карта*, which is what `values-hr/` and `values-bs/` write. Serbian's phone says *Мапе* (`android:app_category_maps` = *Мапе и навигација*, `android:keyboard_shortcut_group_applications_maps` = *Мапе*), and *карта* in Serbian is first a ticket and a playing card. Held to across `map_*`, `dataset_tiles` (*Подаци мапе*), `settings_opening_map` and `station_no_navigation_app`. |
| station | **станица** | The bike-share station, throughout. |
| railway station | **железничка станица** | `address_search_prompt_message` means railway and coach stations by "stations", and Serbian — unlike Croatian, which has *kolodvor* — has no separate word. So the line spells it out: *и железничке станице, универзитети и главни тргови*. Without the adjective the sentence would be read as offering to search the bike stations, which that screen does not do. |
| free dock | **слободно место** | What a bike is returned into, counted as available: `docks_available`, `counterpart_docks`, `mode_docks`. |
| dock (capacity) | **сталак** | The same object counted as a total: `docks_total`, `station_detail_with_capacity`. The screen shows both figures side by side — *12 слободних места · 30 сталака* — so they cannot share a word. Never the payment terminal. |
| bike | **бицикл** | |
| mechanical | **класични** | The contrast Serbian actually draws with *електрични бицикл*. *Механички* is the literal rendering and reads as a machine part rather than as a bike you pedal. |
| electric | **електрични** | Pedal-assist, which `journey_bike_kind_electric_description` spells out as *бицикл са помоћним електричним погоном* so nobody reads "moped". |
| any bike | **Било који** | `journey_bike_kind_any`, the first of three toggle branches. Not *Било који бицикл*: the two branches beside it (*Класични*, *Електрични*) elide the noun, and repeating it here made this row the widest in the file. The spoken `journey_bike_kind_any_description` carries the full phrase. |
| pace (walking) | **темпо** | Never *брзина*. `settings_walking_pace_title` is *Темпо ходања*: a pace is something one knows about oneself, where a speed is a figure nobody has measured about themselves. The lexicon's *брзина* belongs to rates of machines, which is the connotation to avoid. |
| brisk | **Живахно** | The third pace. *Брзо* would say speed, which is the word the row is built to avoid. |
| Delete | **Избриши** | Destroys: a city's data, a dataset. `android:delete`, `settings:dlg_delete`. |
| Clear | **Обриши** | Empties a field without destroying anything: `search_stations_clear`, `address_search_clear`, `city_search_clear`, `action_clear_search`, `place_clear`. `settings:clear` = *Обриши*, `android:searchview_description_clear` = *Обриши упит*. Two words where Android has two, and the pair *Избриши* / *Обриши* is exactly the pair the phone draws. |
| Remove | **Уклони** | Takes out of a list: `station_favourite_remove`, *Уклони из фаворита*. `android:kg_reordering_delete_drop_target_text`. |
| favourites | **Фаворити** | Not in the lexicon — this is a choice, and it is the word Serbian apps put beside a star. *Омиљене* was weighed and dropped: it is an adjective needing a noun to agree with, and `favourites_title` stands alone as a screen title. |
| offline | **за рад ван мреже** | Not in the lexicon either. Serbian has no adjective of the *izvanmrežni* kind, so the file says what offline means rather than coining one: `settings_section_data` is *Подаци за рад ван мреже*, `map_needs_tiles_title` *плочице за рад ван мреже*, `incoming_needs_index` *индекс за рад ван мреже*. Adverbially it is plain *без мреже* (`dataset_tiles_purpose`, `storage_intro`) or *ван мреже* (`dataset_delete_body`). |
| storage (the screen) | **Меморијски простор** | `settings:storage_category`. Three lines send the reader to that screen and name it as the phone names it, capitalised: *са екрана Меморијски простор* (`map_needs_tiles_message`, `address_needs_index_message`, `journey_graph_missing`). |
| Wi-Fi | **Wi-Fi** | Untranslated and, in Cyrillic, never declined. Serbian would need *Wi-Fi-ju* with a hyphen to inflect it, which is ugly beside Cyrillic; the file adds the noun instead and lets that carry the case: *на Wi-Fi мрежи* (`download_unmetered_only`, `storage_wifi_warning`), *Повежите се на Wi-Fi* (accusative, no ending needed), *чека Wi-Fi*. The switch names Wi-Fi rather than the billing, as the English does, because it is the word every reader already has. |
| may be billed | **може да се наплаћује** | Never the flat *наплаћује се*. The phone reads only that the connection declares itself metered; what the plan behind it charges, no device can know. The lexicon says the same thing the same way: `android:network_switch_metered_detail` = *Можда ће се наплаћивати трошкови*, `android:perm_costs_money` = *ово ће вам можда бити наплаћено*. `download_held_back_title` names the situation for the same reason — *Преузимање на мобилној мрежи*, not a verdict on the connection — and `download_can_resume` drops the modal because there the device does state what it knows: *Ова веза више није мобилна мрежа*. |
| feed (GBFS) | **канал података** | The stream a network publishes: `error_feed_unavailable`, `error_malformed`, `welcome_fleet_body`. Kept apart from the line below, exactly as English keeps *feed* and *data sources* apart. |
| data sources | **извори података** | The attributions screen and the city-by-city list: `about_attributions_title`, `sources_title`, `sources_open`. Who produced the data, never the pipe it comes down. |
| address index | **индекс адреса** | *Индекс*, not the *kazalo* `values-hr/` uses: Serbian reads *казало* as a Croatianism and *индекс* is the ordinary word. |
| tiles (map) | **плочице** | `map_needs_tiles_title`. |
| file | **датотека** | `android:mime_type_generic`. The whole `dataset_rejected_*` block holds to it; *фајл* is what people say and what `android:permgrouplab_storage` writes for the storage permission, but the written word on this phone's own dialogs is *датотека*. |
| server | **сервер** | `settings:server_name_title`. |
| security certificate | **безбедносни сертификат** | `android:ssl_certificate`, word for word. |
| position / location | **локација** | One word for both, which is what Android has: `android:permgrouplab_location` and `settings:location_settings_title` are both *Локација*. So `map_locating` is *Тражење ваше локације…*, `map_location_unavailable` *Локација није доступна*, `journey_source_my_position` *Моја локација*, `address_detail_approximate` *приближна локација*. *Положај* exists but the lexicon reserves it for the attitude of a thing (`settings:auto_rotate_screen_summary`), which is the wrong sense here. |
| Out of service | **Не ради** | `settings:radioInfo_service_out`. |
| just now | **управо сада** | `settings:time_unit_just_now` is *Управо*, which stands alone in a list; embedded in *Ажурирано %1$s* it needs its adverb, so the file writes *управо сада*, lowercased. This is a small, deliberate departure and the only change made to that word. |
| In use | **У употреби** | `android:media_route_status_in_use`. |
| Settings / Display / Storage / Privacy / System / Language / Back / List | **Подешавања / Екран / Меморијски простор / Приватност / Систем / Језик / Назад / Листа** | All from the lexicon, keys `android:global_action_settings`, `settings:display_settings`, `settings:storage_category`, `settings:privacy_dashboard_title`, `android:default_audio_route_category_name`, `settings:app_locale_preference_title`, `android:back_button_label`, `android:chooser_all_apps_button_label`. |
| Licence | **лиценца** | `settings:license_title` = *Лиценца*. |
| account / advert / tracker | **налог / реклама / алат за праћење** | `welcome_hello_body` and `about_privacy_body`. *Налог* is `android:choose_account_label`. |
| Home (the place) | **Кућа** | Where the reader lives, named by them (`settings_place_home`, `journey_source_home`, the same word in both). `android:postalTypeHome` = *Кућа*, which is also what the Serbian of every contacts and maps app puts beside that pin. The dwelling, never an application's opening screen. |
| Work (the place) | **Посао** | Where they work (`settings_place_work`, `journey_source_work`). *Посао* is the place one goes to; *Рад* would be the work itself, and it is not a point on a map. |
| My places | **Моја места** | The settings section that holds the two (`settings_section_places`). *Место* is the noun `place_sheet_title` uses as well. |
| outside the city served | **Изван покривеног града** | Under a place the conurbation in use does not cover (`settings_place_outside_city`). *Изван* and *покривен* are what `station_beyond_area` already says. This one **may** say *град*: no placeholder follows it, so the rule that keeps a network's name out of a genitive does not reach it. The application serves several conurbations side by side: a *Кућа* named in Novi Sad is out of reach while Belgrade is in use. The place is kept and stays erasable — out of reach, not wrong. |
| Face north and lay the map flat | **Окрени према северу и поравнај мапу** | `map_face_north`, on the compass, which shows while the map is turned or tilted, and one press gives back both at once — the north and the flat; `map_bearing_description` counts the turn in *степени*, over the three categories (*1 степен*, *3 степена*, *5 степени*). |
| tilt / lay flat | **нагнути / нагнута**, поравнати | *Нагнути* is the tilt and *окренути* the turn, kept apart so that the four gestures of `map_description` stay four. `map_tilted_description` is „Мапа је нагнута.“, and `map_face_north` says *поравнај* for laying it flat again. |
| this place | **Ово место** | `place_sheet_title`, the sheet a point found on the map opens — the same noun as *Моја места*. `place_clear` is *Обриши ову тачку*. |
| Go there / Leave from here | **Иди онамо / Крени одавде** | The place sheet's two actions take the station sheet's own words: `station_as_origin` is already *Крени одавде*, word for word, and `station_as_destination` *Иди овамо*, of which *Иди онамо* is the same verb with the far deictic Serbian has and English has not. The place sheet is the station sheet's sister and the two must read as one, so neither pair is reworded without the other. |

## Android's own words for the path through its settings

`about_links_body` walks the reader through Android's own screens, and every
step is written with the words the phone writes rather than with a translation
of the English: **Подешавања → Апликације → Roue Libre → Подразумевано отварај
→ Додај** (`android:global_action_settings`,
`settings:keywords_applications_settings`, `settings:launch_by_default`,
`settings:app_launch_supported_links_add`). A path is only useful if the
reader can match each word to what is on the screen, so this line is not
reworded for style; it is corrected against the phone when Android renames a
screen.

The same sentence says *изаберите Roue Libre када Android понуди избор
апликације* rather than naming a "chooser": Android's Serbian calls that
dialog nothing consistent — `android:whichApplication` is *Доврши радњу
преко*, `android:chooseActivity` *Изаберите радњу* — so the line describes
what happens instead of inventing a noun.

## The English is impersonal, and so is the Serbian

"No history is kept", "It is read from the feed", "Nothing is sent" — the
source never says "we". In an application whose whole argument is that nobody
is behind it, a first person plural would ask the reader to trust a *ми*
instead of stating a property of the software.

Serbian keeps it at no cost, because the reflexive passive does the same work:
*не чува се никаква историја*, *не шаљу се никоме*, *чита се из сопственог
канала података мреже*, *скупови података се преузимају само када то
затражите*.

**One line does say *ми*, and deliberately.** `city_proposal_body` ends
*Идемо са тим?* — because the English ends "Shall we go with that?". That
sentence is not a claim about what the software does with your data; it is the
application asking a question and waiting for *Да*. The impersonal rule covers
the privacy and behaviour texts, and this is neither.

## Cases are suffixes, and a placeholder cannot carry one

Serbian declines, and the ending falls on the word itself. A sentence built
around `%1$s` has to stay right whatever arrives in it, and what arrives is
always a nominative: a station name, a street name, a city, a network label.
So these lines are written around that rather than against it:

| String | What it does | Why |
|---|---|---|
| `journey_step_to_station`, `journey_step_ride` | *Пешице до станице %1$s* | *До* governs the genitive; the case falls on *станице* and the name follows in apposition, in the nominative it arrived in. |
| `station_address_nearby` | *Близу: %1$s* | *Близу* also governs the genitive, and the argument is a street **or** a square, reaching the line as it stands. A colon turns it into a label, which declines nothing. |
| `dataset_imported`, `dataset_deleted`, `dataset_delete_description` | *Инсталирано: %1$s* | A dataset's name is a masculine plural in two cases (*Подаци мапе*, *Подаци за руте*) and a masculine singular in the third (*Индекс адреса*), so no participle agrees with all three. |
| `settings_place_set`, `settings_place_change`, `settings_place_clear`, `settings_place_cleared` | *Подеси: %1$s* | The argument is *Кућа* or *Посао*, one feminine and one masculine, and the four lines would need the accusative. A colon spares both. |
| `city_here_body`, `city_here_installed_body` | *%1$s покрива подручје у ком се налазите. Инсталирајте податке те мреже…* | The placeholder opens the sentence as the **subject**, in the nominative it arrived in, and *те мреже* carries the case the rest needs — rather than a pronoun that would have to agree with whatever the label produced. Subject-first also settles which of the two nouns covers which. |
| `city_proposal_body`, `map_outside_city_message`, `map_outside_city_brief` | *…подручја које покрива %1$s* | Same placeholder, inside a relative clause this time: *које* is the accusative object and `%1$s` the nominative subject, so again nothing is asked of the name. |
| `journey_bikes_at_departure` | *%1$s на полазној станици* | The counts arrive already agreed, and the locative falls on *станици*, which this file owns. |

### The three lines that must not say "град"

`city_delete_description`, `city_delete_body` and `city_deleted` are handed
`city.displayName`, which is the **network's** name and not the city's: 328 of
the 331 entries in `config/catalogue.json` carry a `displayName` of their own,
and whole countries' worth of them are called *nextbike*. Writing *подаци
града %1$s* — the obvious way to dodge the case ending — would make the reader
read *подаци града nextbike*, a false statement the English never makes. So
the three say

- *Избриши податке: %1$s*
- *Сви подаци за рад ван мреже за „%1$s“ биће избрисани…*
- *Подаци „%1$s“ су избрисани*

The colon and the quotation marks hold the name at arm's length, exactly as
`dataset_deleted` does, and spare it an ending it could not take.

## The one place a sort order is not named after an alphabet

`stations_location_denied` says *станице остају поређане по називу* — ordered
by name — where the English says "alphabetical order". Serbia writes both
scripts, station names reach the application from the network's own feed in
whichever script that feed publishes them, and the collator that sorts them
is the one Android gives for `sr`. Naming an alphabet — *азбучним редом* for
Cyrillic, *абецедним редом* for Latin — would promise the reader an order the
data may not be in. Naming the field the sort runs on promises only what is
true.

## The address prompt, and the layout that is not ours

`address_search_hint` is **„Улица, број, место“** — street, then number, then
town, which is the order Serbia writes an address in (*Кнез Михаилова 12,
Београд*). `AddressQuery.parseQuery` has read a house number standing between
the street and the town since the pilot, precisely so that each language may
write this line in its own order rather than in English's.

No postcode is invited. Serbia's is five digits and would qualify under the
rule in `SPEC.md` §4.3, but the parser gives a number up as soon as the query
holds a second one — and a Serbian address that already carries a house number
would then lose it. Street, number, town is what the parser reads best and
what the prompt therefore asks for.

**The order a result is printed in is a separate matter and is not this file's
to decide.** It belongs to the country the address is in, not to the reader's
language (SPEC §4.3): *Кнез Михаилова 12* is how a Belgrade address reads for
a reader in Japanese, and *12 rue Nationale* is how a Lyon one reads for a
Serbian reader. The layouts live in `core/address/AddressLayout.kt`, keyed on
the language of the **address base**.

**There is no `"sr"` entry in that table yet.** A Serbian base therefore falls
on `DEFAULT_LAYOUT` and would print *12 Кнез Михаилова*, which is neither
Serbia's order nor anybody's. Serbia closes with the number — *Кнез Михаилова
12*, *Булевар краља Александра 73а* — and runs a letter suffix hard against
it, exactly as the `"hr"` and `"bs"` entries beside it already do; there is no
second number of the Czech kind. Adding the line is one entry and it belongs
to whoever owns that file, not to this translation.

## The longest strings, measured

Serbian runs a little longer than English and about as long as Croatian, so
nothing here asks for a layout change. The five longest bodies are
`welcome_data_body` (382 characters), `about_privacy_body` (338),
`welcome_privacy_body` (280), `about_links_body` (239) and
`welcome_hello_body` (219) — all of them scrolling text in a sheet, where
length costs nothing.

The lines worth watching are the short ones, which sit on controls:

| String | English | Serbian | Where it sits |
|---|---|---|---|
| `storage_open` | 19 | 34 | a settings row title, which wraps |
| `settings_opening_title` | 26 | 37 | a settings row title over a two-button row |
| `settings_section_data` | 12 | 23 | a section heading |
| `storage_title` | 7 | 18 | a screen title |
| `journey_navigate`, `station_open_in_navigation` | 24 | 33 | full-width buttons |
| `settings_units_title` | 5 | 14 | a settings row title |

None of the six is a fixed-width control, and every one of them is within a
character or two of what `values-hr/` already ships and Croatian screens have
been walked through with. The one place the length was cut rather than
accepted is `journey_bike_kind_any`, in the table above: three labels share
one toggle there, and that row has no room to wrap.
