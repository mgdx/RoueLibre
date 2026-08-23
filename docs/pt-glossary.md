# Portuguese glossary

The terms `res/values-pt/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Portuguese ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Which Portuguese, and for whom

`values-pt/` is **European Portuguese**. Brazil would be `values-pt-rBR/`, and
this repository does not carry it — so until somebody writes it, Android serves
this very file to a Brazilian device as well. The catalogue holds **seven
Brazilian networks against one Portuguese**, which means the file is read far
more often in Brazil than in Portugal.

The rule that follows from those two facts, and the rule this file was written
under: **the Portuguese is European and correct, but nothing is reached for
that a Brazilian reader could not follow.** Where the two norms share a word,
that word is used even when a more markedly European one exists. Where they
genuinely part company, the European form is written — that is what the folder
is — and the divergence is listed under "Where the two norms part company"
below. That list is what a `values-pt-rBR/` should be derived from; do not fork
this file without reading it.

## The lexicon supplied with this translation is Brazilian

`pt.tsv`, the system lexicon extracted from a phone's `framework-res.apk` and
`Settings.apk` and supplied to arbitrate Android's own vocabulary, holds
**Brazilian** Portuguese. That is not a judgement call — the extraction contains
186 rows saying *tela*, 122 saying *configurações*, 43 saying *usuário*, 14
saying *arquivo*, and **zero** rows saying *ecrã*, *definições*, *utilizador* or
*ficheiro*.

So it cannot arbitrate this file, and it was not allowed to. It was used in the
one way it remains sound: as evidence of what Android says **where both norms
say the same thing** — those citations are given in the table below with the
resource key actually found. Where the row is Brazilian-only, the table says so
and gives the European word instead, marked as a departure. Nothing here cites a
key that was not grepped.

## Register: the third person, and not "tu"

The interface addresses the reader in the **third person singular** — *Toque*,
*Escolha*, *Verifique*, *a sua cidade* — and never with *tu*.

This departs from the *tu* of `values-fr/` and the *tú* of `values-es/`, and it
is deliberate. **Portuguese has no informal register the two norms share.**
European informality is *tu* with its own imperatives (*Toca*, *Escolhe*), which
reads as strange, almost childish, to a Brazilian. Brazilian informality is
*você*, which takes third-person forms — and those same forms are the polite
register in Portugal. Only one of the two choices is readable on both sides of
the Atlantic, and it is the third person. It is also what Android itself says in
both norms: `android:lockscreen_password_wrong` is *Tente novamente*, a third
person form, not *Tenta*.

The consequence, kept consistent throughout: possessives are *o seu / a sua*,
never *o teu*; the polite object pronoun *lhe* appears where the sentence needs
one (*A aplicação propõe-lhe a sua*); *o senhor* never appears, since the third
person alone is already neutral between the two norms.

**This is the one decision in the file most worth a second opinion**, because it
is the one that visibly breaks step with the other four translations.

## The vocabulary

| English | Portuguese | Why |
|---|---|---|
| journey | percurso | The whole door-to-door thing: the screen, the settings section, the button, the store text. It is the word Portuguese transport services use for a computed trip, and it is short enough for a section title. Masculine, so *todo o percurso*, *nenhum percurso*. |
| ride | trajeto de bicicleta | The bike leg alone, inside a journey. A different word from *percurso*, so "the ride, uphill and down" and "the journey in detail" stay about two different objects. |
| route | caminho | Only in "no practicable route": the line on the ground, not the planned journey — *Não há nenhum caminho praticável entre estes dois pontos*. Also *nenhum caminho lá chega* in `station_beyond_area`. |
| to ride | pedalar | The verb of the bike leg — *Pedale até…* — against *caminhar* for the walking ones. |
| station | estação | A bike-share station. Portuguese uses the same word for a railway station, which is exactly what `address_search_prompt_message` means; there it is written out as **estações ferroviárias**, which is neutral, where *estações de comboio* would be European-only and *estações de trem* Brazilian-only. |
| bike | bicicleta | Not *bici*: Portuguese does not clip the word the way Spanish does — the Brazilian clipping is the anglicism *bike*, and Portugal clips nothing. *Bicicleta* is the only form both norms write. It is long, which is the price; the counts beside a station's disc carry the word alone, never the figure, so the row absorbs it. Feminine, hence *mecânica* and *elétrica* everywhere. |
| dock (free) | lugar livre | What a bike is returned into, counted as available. A car park in Portugal signs *LUGARES LIVRES* and the sense carries exactly; Brazil reads it without effort. *Vaga* would have been the natural Brazilian word and is not the Portuguese one. |
| dock (capacity) | doca | The same object counted as a total, which is a different figure on the screen: *12 lugares livres · 30 docas*. English says "dock" for both; Portuguese does not have to. *Doca* is what Lisbon's own GIRA calls the point a bike attaches to. |
| dock | *never* «terminal», *never* «quiosque» | A terminal is what one pays at, not the point a bike attaches to. |
| bike, electric | elétrica | Pedal-assist, never a moped: `journey_bike_kind_electric_description` says *com assistência elétrica ao pedalar* in full. Spelled *elétrica* in both norms since the 1990 orthographic agreement; the pre-agreement European *eléctrica* is not used. |
| pace (walking) | ritmo de caminhada | A pace is not a speed: `values/strings.xml` says so above the string, and *velocidade* would say the opposite. *Ritmo* is masculine, hence *Lento / Normal / Rápido*. |
| leg (of a journey) | trecho | *Os trechos a pé de um percurso*. Chosen over the European *troço*, which in Brazil reads as "junk"; *trecho* is correct in both. |
| climb | desnível | The metres climbed over a leg or a journey. |
| Settings | Definições | Android's European word. The lexicon supplied gives *Configurações* (`settings:settings_label`), which is Brazilian; this is a deliberate departure, listed below. |
| Search | Pesquisar | Shared by both norms, and Android's own — `android:searchview_description_search`, `settings:search_menu_title`. |
| Clear | Limpar | Android's word for emptying a field, which is what "clear the search" does — `settings:clear`. Shared. |
| Refresh | Atualizar | Android's word for data — `android:autofill_update_yes` for the verb. Shared spelling since the orthographic agreement. |
| Try again | Tentar novamente | Android's own, and already in the third person — `settings:network_connection_timeout_dialog_ok`, `settings:security_settings_fingerprint_enroll_dialog_try_again`. Shared. |
| Back | Voltar | Android's word on the toolbar's back arrow — `android:back_button_label`. Shared. |
| Tap | Toque | The third-person imperative, matching the register above. |
| Press and hold | Toque sem soltar | The European wording; Brazil says *toque e segure*, which is listed below. |
| Delete / Remove | Eliminar / Remover | Two gestures, two words: *Eliminar* destroys data, *Remover* takes a station out of the favourites. Android confirms *Remover* — `settings:remove`, `android:kg_reordering_delete_drop_target_text`. For "delete" the lexicon gives the Brazilian *Excluir* (`android:delete`); *Eliminar* is the European word and is used here. |
| Skip | Ignorar | The European word. The lexicon gives *Pular* (`android:skip_button_label`), which is Brazilian. |
| Show | Mostrar / Ver | *Mostrar* where something hidden is revealed — what the map counts, what the list shows; Android's own at `settings:condition_expand_show`. **Ver** where a button opens a screen or a place: *Ver o mapa*, *Ver os favoritos*, *Ver as novidades*, and `incoming_show_me`. One word for one gesture. |
| About | Acerca de | The European form of an application's about screen; Brazil writes *Sobre*. Android's own row is about the device, not about an application, so it does not arbitrate here. |
| In use | Em uso | Android's own word, on the city already installed — `android:media_route_status_in_use`. Shared. |
| Out of service | Fora de serviço | Android's own wording — `settings:radioInfo_service_out`. Shared. |
| just now | agora mesmo | Android says *Agora* (`settings:time_unit_just_now`). Departed from on purpose: this string is read as one phrase with *Atualizado %1$s*, and *Atualizado agora mesmo* carries the "a moment ago" sense that *Atualizado agora* loses to the plain adverb. Shared between the norms either way. |
| Update available | Atualização disponível | Android's own wording — `settings:android_version_pending_update_summary`. Shared. |
| Replace | Substituir | Android's own word — `settings:vpn_replace`. Shared. |
| Language | Idioma | Android's own word — `settings:app_locale_preference_title`. Shared. |
| Cancel / Yes | Cancelar / Sim | Android's own — `android:cancel`, `settings:yes`. Shared. |
| Storage | Armazenamento | Android's own — `settings:storage_settings`. Shared. |
| Location / position | localização | Android's own word on the permission — `android:permgrouplab_location`. Shared. *Posição* is used for the reader's own point on the map, where the English says "position" rather than naming the permission. |
| link (hyperlink) | link | Android's own, in the very path this file quotes — `settings:app_launch_supported_links_add` is *Adicionar link*. It also keeps *ligação* free for the network sense, which matters: writing both as *ligação* would have made `about_links_body` unreadable. |
| connection (network) | ligação | The European word. Brazil says *conexão*, and in Brazil *ligação* means a phone call — so `error_offline` is written *Sem ligação à Internet*, which disambiguates for a Brazilian reader without leaving European Portuguese. |
| offline | offline | As a qualifier — *dados offline*, *índice offline*, *trabalhar offline* — where both norms use the borrowing unchanged. *Sem ligação* is used for the state of having no network. |
| app | aplicação | The European word, understood in Brazil, where the everyday word is *aplicativo*. |
| conurbation | área metropolitana | Neutral, and the term the catalogue's own cities are described by. |
| town (in an address) | localidade | Read the same way in both norms, where *município* and *concelho* are administrative. |
| file | ficheiro | The European word. The lexicon gives *arquivo* throughout, which is Brazilian. |
| download | transferir / transferência | The European verb and noun, and Android's own in Portugal. The lexicon gives *baixar* (`android:install_carrier_app_notification_button`) and *download*, both Brazilian. This is the divergence with the most occurrences in the file — the whole storage screen turns on it. |
| feed (GBFS) | fluxo | The network's published stream, in the errors and in the welcome. |
| tracker | rastreador | The store texts and `about_privacy_body`. Shared. |
| map data / tiles | mapa base | The name the storage screen gives the dataset, and the one every other string must use for it — including `map_needs_tiles_title`. |
| routing data | grafo de percursos | The project's own term, used in `journey_graph_missing` and in the store text too. More technical than the English "routing data", and kept deliberately: it is one object with one name, and it reuses *percurso* so the dataset and the thing it computes are visibly the same subject. |
| address index | índice de endereços | — |
| dataset | conjunto de dados | — |
| unmetered connection | ligação ilimitada | Android's own pair for a metered network is *Limitada* / *Ilimitada* — `settings:wifi_metered_label`, `settings:wifi_unmetered_label`, shared by both norms. The setting's description then explains it as billing by the megabyte, exactly as the English does. |
| by default | por predefinição | The European form, including in the Android Settings path quoted by `about_links_body`. Brazil writes *por padrão* — `settings:launch_by_default`. |

The three dataset names are all **masculine singular** — *mapa base*, *grafo de
percursos*, *índice de endereços* — which is what lets `dataset_imported` and
`dataset_deleted` agree once for all three (*%1$s instalado*).

## The order of an address, and why no postcode is invited

`address_with_number` is written **`%2$s, %1$s`** and not `%1$s %2$s`:
Portuguese puts the street before the number — *Rua Augusta, 12* — where English
puts the number first. This is precisely what positional placeholders exist for,
and it is the only string in the file whose placeholders are reordered.

`address_search_hint` follows it — **Rua, número, localidade**, not the English
*Number, street, town*. `AddressQuery.parseQuery` reads a house number in the
three orders that are written (SPEC §4.3): opening the query, closing it, or
**standing between the street and the town**, which is Portuguese's ordinary
order and the reason this prompt may name it. *Rua Augusta 12 Lisboa* resolves.

**The prompt does not name the postcode, and it must not be added.** A
Portuguese postcode is written **1000-001**, in two groups. The parser drops a
postcode before it looks for the house number, but it only recognises the shapes
it was taught; *1000-001* is not among them, so *1000* stands in the query as a
**second number** — and a number that does not open the query is given up as
soon as a second one appears. Inviting *Rua Augusta 12 1000-001 Lisboa* would
therefore cost the reader the house number 12 they had just typed, silently,
and leave them with the street. Naming one number and one town is exactly as far
as this prompt can go.

The same two guards are why the prompt invites no second number of any kind:
they are what keeps street names carrying a date whole — *Avenida 25 de Abril*,
*Rua 31 de Janeiro*, *Avenida 9 de Julho* are streets of cities this application
serves.

## Where the two norms part company

The list a `values-pt-rBR/` would be derived from. Everything here is written in
the European form in `values-pt/`; the Brazilian column is what would have to
change, and nothing else in the file should.

| European (written here) | Brazilian | Where |
|---|---|---|
| Third person, no *tu* | the same third person | **No change.** The register was chosen precisely so this row would be empty. |
| Definições | Configurações | `settings_title`, `settings_open`, `about_links_body`, and the store texts |
| ecrã | tela | `map_needs_tiles_message`, `journey_graph_missing`, `address_needs_index_message`, `settings_opening_description`, `error_offline`, and the store texts |
| ficheiro | arquivo | the five `dataset_rejected_*`, `error_malformed_download`, `error_local_storage_download` |
| transferir, transferência | baixar, download | the whole storage screen, `city_detail*`, `download_*`, `welcome_data_*`, and the store texts |
| Eliminar | Excluir | `city_delete*`, `dataset_delete*` |
| Ignorar | Pular | `welcome_skip` |
| Acerca de | Sobre | `about_title`, `about_open` |
| aplicação | aplicativo | throughout |
| ligação (network) | conexão | `error_offline*`, `storage_intro`, `download_*`, `dataset_tiles_purpose` |
| partilhado, partilhar | compartilhado, compartilhar | `welcome_hello_body`, `about_links_body`, `download_unmetered_only_description`, the store texts |
| quilómetros | quilômetros | `settings_units_metric_description` |
| planear | planejar | `journey_open` |
| até ao destino | até o destino | `journey_step_to_destination`, `journey_step_walk_all`, `journey_step_ride_all` |
| libertar espaço | liberar espaço | `error_local_storage_download`, `dataset_delete_body` |
| Toque sem soltar | Toque e segure | `favourites_reorder_hint` |
| Abrir por predefinição, Aplicações | Abrir por padrão, Apps | `about_links_body` — it quotes a system path and has to match the device word for word; the European path is quoted here, and the comment above the string says so |
| Nova Iorque, Copenhaga | Nova York, Copenhague | `full_description.txt` |
| Em França, fora de França | Na França, fora da França | `about_attribution_gbfs`, `changelogs/3.txt` — European Portuguese takes no article before most country names, Brazilian takes one |
| *A calcular…*, *A pesquisar…* | *Calculando…*, *Pesquisando…* | `journey_computing`, `journey_computing_own_bike`, `journey_locating`, `address_searching_title`, `address_searching_message`, `storage_checking` — European Portuguese forms the progressive with *estar a* + infinitive, Brazilian with the gerund. This is systematic rather than lexical: it is the shape of the sentence, not a word to swap. |
| *para a mover*, *para os transferir* | *para movê-la*, *para transferi-los* | clitic placement, systematic. European Portuguese puts the pronoun before the infinitive after a preposition; Brazilian attaches it. Both are grammatical written Portuguese, so nothing here is wrong in Brazil — it is only marked. |
| *a minha cidade*, *a sua posição* | *minha cidade*, *sua posição* | the article before a possessive, systematic. European keeps it, Brazilian drops it in ordinary register. |

One word deliberately **not** taken from European Portuguese: **endereço** and
not *morada* for a postal address, throughout `address_*`, `dataset_addresses`
and the store texts. *Morada* is the more idiomatic European word and reads as
archaic — a dwelling, an abode — in Brazil, where it would be the single most
jarring word in the file. *Endereço* is correct European Portuguese, is what
Portuguese official texts write for a postal address, and is shared. A
`values-pt-rBR/` needs no change here; a reviewer who "corrects" it to *morada*
is undoing a decision rather than fixing an oversight.

## Words that are not translated

Product and network names — Roue Libre, Vélib', V'lille, Vélo'v, GIRA, Citi
Bike, BRouter, MapLibre, OpenStreetMap, GBFS, Wi-Fi — the licence names, and
Base Adresse Nationale, which is the proper name of a French dataset. Unit
symbols (m, km, ft, yd, mi, min, h, B, kB, MB, GB) stay as they are.
`resources` `name` attributes, always.

`settings_walking_pace_normal` is **Normal**, which is the English word
unchanged and is also the Portuguese one. The translation checker reports it as
a string to confirm; it is confirmed here.

## Typography

Quotations are **« »**. A question carries no opening mark — Portuguese is not
Spanish. **No space stands before `:`, `;`, `?` or `!`** — Portuguese is not
French. The apostrophe, where the language uses one, is **’** (U+2019) and not
the straight quote; European Portuguese uses one almost nowhere, so nothing in
the file needed escaping, and a contributor adding a straight `'` to a resource
value must write it `\'`.

Numbers keep the **decimal comma** and a thousands separator that is a space or
a point: *42,5 MB*, *1,3 GB*. Size units are the international symbols — B, kB,
MB, GB — since Portuguese says *byte*, not *octeto*.

`many` is a real plural category of Portuguese, reached at a million and beyond
(CLDR). It is written out in every `<plurals>` all the same: what a language
distinguishes is a fact about the language, not about the counts this
application happens to show. Portuguese writes those large numbers with *de* —
*1000000 de bicicletas* — which is what the `many` items carry.
