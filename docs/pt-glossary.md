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

## The lexicon, and a correction worth recording

`pt.tsv` is the system lexicon extracted from a phone's `framework-res.apk` and
`Settings.apk`, and it arbitrates any word Android already has.

**It was Brazilian on the first pass, and that was a tooling defect, since
fixed.** AOSP names the Brazilian norm `pt` and the European one `pt-rPT`; the
extraction took the bare code and so produced Brazilian throughout. It was
caught here — the file held 186 rows saying *tela* and none saying *ecrã* — and
this translation was written without it, from the norms themselves. It has since
been regenerated as European, and every term below has now been checked against
the real thing: 187 rows say *ecrã* and none say *tela*, 124 say *definições*
and none say *configurações*.

The re-check confirmed the vocabulary and corrected four words. That is recorded
here because "right" and "right by luck" are not the same thing, and only the
second one rots silently:

| Was | Now | The row that decided it |
|---|---|---|
| Voltar | **Anterior** | `android:back_button_label`, `settings:back`, `settings:wizard_back`. `action_back` is the toolbar's back-arrow content description in eleven fragments and a visible back button in a twelfth — exactly the two things those keys name. One row does say *Voltar* (`android:input_method_nav_back_button_desc`), against five for *Anterior*. |
| Em uso | **Em utilização** | `android:media_route_status_in_use`, `settings:wifi_display_status_in_use`. Longer than *Em uso* on the city row's badge, and taken anyway: length is not a reason to call a thing by a different name than the system does. |
| Definições → **Aplicações** → … | Definições → **Apps** → … | `settings:apps_dashboard_title` and `settings:keywords_applications_settings` are both *Apps*. `about_links_body` quotes a Settings path and has to match the device word for word; *Aplicações* does exist in the lexicon, but only at `android:keyboard_shortcut_group_applications`, which is not this screen. |
| ligação ilimitada | ligação de **acesso ilimitado** | `settings:wifi_unmetered_label` is *Acesso ilimitado* and `settings:wifi_metered_label` *Acesso limitado* — not the bare adjectives assumed. |

Everything else held. Citations in the table below give the key actually
grepped; where this file departs from a row, it says so, and says why.

## Register: the third person, because that is what Android says

The interface addresses the reader in the **third person singular** — *Toque*,
*Escolha*, *Verifique*, *a sua cidade* — and never with *tu*.

This was first settled on the argument that Portuguese has no informal register
the two norms share. That is true, but it is only a tie-breaker. The lexicon
gives the real reason, and it is not a tie: **European Android does not use *tu*
at all.** Counted over its Portuguese:

| | third person / *o seu* | *tu* / *o teu* |
|---|---|---|
| imperatives, over 29 common verbs | **217** | **0** |
| possessives | **60** (*o seu*, *a sua*) | **0** (*o teu*, *a tua*) |
| subject pronouns | *você* 0 — elided throughout | *tu* **0** |
| clitic *te* | — | **0** |

The imperative count takes sentence-initial forms only, so that descriptive
clauses cannot inflate it. Five hits first looked like *tu* forms and are not:
*Consulta de pesquisa* is a noun ("search query"), while *Abre a app Mensagens*,
*Remove da secção de conversas* and *Tem mensagens novas* are third-person
indicatives. The true count is 217 to nil.

So the register here is the system's, not a compromise between two norms: an
application saying *Toca* would be the odd one on the phone. That it also reads
correctly in Brazil, where these same forms are *você*, is a convenience rather
than the reason — and it is why the divergence table below has an empty row for
it.

It remains a real departure from `values-fr/` and `values-es/`, which say *tu*
and *tú*. It is not the same decision reached differently: Android addresses
French and Spanish informally and Portuguese formally, and each translation
followed its own system. The German pilot ran this same count and found 69 *du*
against 0 *Sie*, and went informal on it. Same method, opposite answer, both
right.

Consequences kept consistent throughout: possessives are *o seu / a sua*, never
*o teu*; *o senhor* never appears, the third person being neutral already; and
the polite object pronoun *lhe* is used where a sentence needs one (*A aplicação
propõe-lhe a sua*, in the store text). That last is this file's own usage and
not a citation — the lexicon has no *lhe* at all, having little occasion for one.

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
| pace (walking) | ritmo de caminhada | A pace is not a speed: `values/strings.xml` says so above the string, and *velocidade* would say the opposite. Android's own slow/fast pair is *Lenta / Rápida* (`settings:speed_label_slow`, `settings:speed_label_fast`), feminine because it agrees with *velocidade*; *ritmo* is masculine, so this file writes *Lento / Normal / Rápido*. |
| leg (of a journey) | trecho | *Os trechos a pé de um percurso*. Chosen over the European *troço*, which in Brazil reads as "junk"; *trecho* is correct in both. |
| climb | desnível | The metres climbed over a leg or a journey. |
| Settings | Definições | Android's own word, on thirteen rows including `settings:settings_label`, `settings:dashboard_title` and `android:global_action_settings`. Not one row says *Configurações*. |
| Search | Pesquisar | Android's own — `android:searchview_description_search`, `android:ime_action_search`, `settings:search_menu_title`, on 17 rows against 6 for *procurar*. |
| Clear | Limpar | Android's word for emptying a field, which is what "clear the search" does — `settings:clear`, `settings:proxy_clear_text`. |
| Refresh | Atualizar | Android's verb — `android:autofill_update_yes`, `settings:nfc_payment_btn_text_update`. Spelled the same in both norms since the orthographic agreement. |
| Try again | Tentar novamente | Android's own, on all six of its rows, with no variation — `android:lockscreen_password_wrong`, `settings:network_connection_timeout_dialog_ok`, `settings:private_space_tryagain_label`. |
| Back | Anterior | Android's word on the back arrow and on a wizard's back button — `android:back_button_label`, `settings:back`, `settings:wizard_back`, `settings:searchview_navigation_content_description`. **Not *Voltar***, which is the obvious word and the wrong one: it holds a single row, `android:input_method_nav_back_button_desc`. `action_back` serves both the uses those four keys name. |
| Tap | Toque | Android's own imperative, on 36 sentence-initial rows; *Toca* appears on none. |
| Press and hold | Toque sem soltar | The European wording; Brazil says *toque e segure*, which is listed below. |
| Delete / Remove | Eliminar / Remover | Two gestures, two words, and Android distinguishes them the same way: *Eliminar* at `android:delete`, `android:deleteText`, `settings:delete` and nine more; *Remover* at `settings:remove`, `android:kg_reordering_delete_drop_target_text`, `settings:locale_remove_menu`. *Eliminar* destroys data, *Remover* takes a station out of the favourites. |
| Skip | Ignorar | Android's own word — `android:skip_button_label`, `settings:skip_label`, `settings:gesture_button_skip`. |
| Show / Hide | Mostrar / Ocultar | Android's own pair — `settings:condition_expand_show`, `settings:condition_expand_hide`; *Ocultar* is what the two map filters say. **Ver** is used instead where a button opens a screen or a place: *Ver o mapa*, *Ver os favoritos*, *Ver as novidades*, and `incoming_show_me`. One word for one gesture. |
| About | Acerca de | Android's own construction, at `settings:about_settings` — *Acerca do telemóvel*. The application's screen is *Acerca de*, the same preposition with no complement. Brazil writes *Sobre*. |
| In use | Em utilização | Android's own wording — `android:media_route_status_in_use`, `settings:wifi_display_status_in_use`. It sits on the city already installed. *Em uso* is shorter and was what this file said first; the badge takes the longer word because it is the system's. |
| Out of service | Fora de serviço | Android's own wording — `settings:radioInfo_service_out`. |
| just now | agora mesmo | Android's own wording — `settings:time_unit_just_now` is *Agora mesmo*. It reads as one phrase with *Atualizado %1$s*. |
| Update available | Atualização disponível | Android's own wording — `settings:android_version_pending_update_summary`, which writes it with a full stop (*Atualização disponível.*); here it is a badge rather than a sentence, so the stop is dropped. |
| Replace | Substituir | Android's own word — `settings:vpn_replace`. |
| Language | Idioma | Android's own word — `settings:app_locale_preference_title`, `settings:tts_default_lang_title`. |
| Cancel / Yes | Cancelar / Sim | Android's own — `android:cancel`, `settings:yes`, `settings:sim_action_yes`. |
| Storage | Armazenamento | Android's own — `settings:storage_settings`, `settings:storage_category`. *Libertar espaço* in `error_local_storage_download` and `dataset_delete_body` follows `settings:storage_free_up_space_title`. |
| Location / position | localização | Android's own word on the permission — `android:permgrouplab_location`, `settings:location_settings_title`. *Posição* is used for the reader's own point on the map, where the English says "position" rather than naming the permission. |
| link (hyperlink) | link | Android's own, in the very path this file quotes — `settings:app_launch_add_link` is *Adicionar link*. It also keeps *ligação* free for the network sense, which matters: writing both as *ligação* would have made `about_links_body` unreadable. |
| connection (network) | ligação | Android's own — `settings:mobile_data_no_connection` is *Sem ligação*, and the lexicon holds 55 *ligação* against 0 *conexão*. Brazil says *conexão*, and there *ligação* means a phone call, so `error_offline` is written *Sem ligação à Internet*: Android's word, disambiguated for a Brazilian reader without leaving European Portuguese. |
| offline | offline | As a qualifier — *dados offline*, *índice offline*, *trabalhar offline* — where both norms use the borrowing unchanged. *Sem ligação* is used for the state of having no network. |
| app | aplicação | The European word, understood in Brazil, where the everyday word is *aplicativo*. Android itself shortens it to *app* in running text (`android:install_carrier_app_notification_button`) and to *Apps* on the Settings dashboard; this file writes *aplicação* in its own sentences and *Apps* only inside the system path it quotes. |
| conurbation | área metropolitana | Neutral, and the term the catalogue's own cities are described by. |
| town (in an address) | localidade | Read the same way in both norms, where *município* and *concelho* are administrative. |
| file | ficheiro | Android's own — `android:mime_type_generic` is *Ficheiro*, `android:permgrouplab_storage` and `settings:storage_files` *Ficheiros*. Brazil says *arquivo*. |
| download | transferir / transferência | Android's own verb and noun — `android:install_carrier_app_notification_button` is *Transferir app*, `settings:filter_apps_third_party` is *Transferidas*, `settings:ingress_rate_limit_dialog_title` says *velocidade de transferência*. Not one row says *baixar*. This is the divergence with the most occurrences in the file: the whole storage screen turns on it. |
| feed (GBFS) | fluxo | The network's published stream, in the errors and in the welcome. |
| tracker | rastreador | The store texts and `about_privacy_body`. Shared. |
| map data / tiles | mapa base | The name the storage screen gives the dataset, and the one every other string must use for it — including `map_needs_tiles_title`. |
| routing data | grafo de percursos | The project's own term, used in `journey_graph_missing` and in the store text too. More technical than the English "routing data", and kept deliberately: it is one object with one name, and it reuses *percurso* so the dataset and the thing it computes are visibly the same subject. |
| address index | índice de endereços | — |
| dataset | conjunto de dados | — |
| unmetered connection | ligação de acesso ilimitado | Android's own pair is *Acesso limitado* / *Acesso ilimitado* — `settings:wifi_metered_label`, `settings:wifi_unmetered_label` — and not the bare adjectives this file first assumed. The setting's description then explains it as billing by the megabyte, in plain words, exactly as the English does. |
| by default | por predefinição | Android's own — `settings:launch_by_default` and `settings:auto_launch_label` are both *Abrir por predefinição*, which is the exact step `about_links_body` quotes. Brazil writes *por padrão*. |

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
| Anterior | Voltar | `action_back`. Both are Android's own word in their own norm, which is why this one is invisible without the lexicon: *Voltar* is what a European reader would guess and what a Brazilian device actually says |
| Em utilização | Em uso | `city_active` |
| acesso ilimitado, acesso limitado | ilimitada, limitada | `download_unmetered_only`, `download_waiting_for_unmetered` — Android names the metered state differently in each norm |
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
| Abrir por predefinição | Abrir por padrão | `about_links_body` — it quotes a system path and has to match the device word for word, and this is the one step of it that differs: both norms call the Settings entry **Apps** (`settings:apps_dashboard_title`), and both say *Adicionar link* |
| Nova Iorque, Copenhaga | Nova York, Copenhague | `full_description.txt` |
| Em França, fora de França | Na França, fora da França | `about_attribution_gbfs`, `changelogs/3.txt` — European Portuguese takes no article before most country names, Brazilian takes one |
| *A calcular…*, *A pesquisar…* | *Calculando…*, *Pesquisando…* | `journey_computing`, `journey_computing_own_bike`, `journey_locating`, `address_searching_title`, `address_searching_message`, `storage_checking` — European Portuguese forms the progressive with *estar a* + infinitive, Brazilian with the gerund. This is systematic rather than lexical: it is the shape of the sentence, not a word to swap. |
| *para a mover*, *para os transferir* | *para movê-la*, *para transferi-los* | clitic placement, systematic. European Portuguese puts the pronoun before the infinitive after a preposition; Brazilian attaches it. Both are grammatical written Portuguese, so nothing here is wrong in Brazil — it is only marked. |
| *a minha cidade*, *a sua posição* | *minha cidade*, *sua posição* | the article before a possessive, systematic. European keeps it, Brazilian drops it in ordinary register. |

Two words deliberately **not** taken from Android's European Portuguese.

**telefone**, where Android says *telemóvel* (`android:permgrouplab_phone`,
`settings:about_settings`). *Telemóvel* is European-only and reads as foreign in
Brazil, where the word is *celular*; *telefone* is correct in both and is what
`welcome_privacy_title`, `about_privacy_body` and the store texts use. This is
the one place the shared-word rule is allowed to outrank the system's own word,
and it is allowed because the alternative is a word half the readership does not
use.

**endereço** and not *morada* for a postal address, throughout `address_*`,
`dataset_addresses` and the store texts. *Morada* is the more idiomatic European
word and reads as archaic — a dwelling, an abode — in Brazil, where it would be
the single most jarring word in the file. This one turned out not to be a
departure at all once the lexicon was corrected: Android itself says *endereço*,
at `android:autofill_save_type_address`. It is correct European Portuguese, it is
what Portuguese official texts write for a postal address, and it is shared. A
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
