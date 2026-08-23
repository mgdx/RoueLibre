# Turkish glossary

The terms `res/values-tr/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Turkish ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

The lexicon quoted below is the 5 904 system strings extracted from a phone's
`framework-res.apk` and `Settings.apk`. Every key cited was grepped in it;
where no key is cited, the choice is this file's own and says so.

## The dotted and the dotless i

Turkish has four i's, not two. `i` capitalises to **İ** and `ı` capitalises to
**I**, and writing `I` where `İ` belongs is the single most visible mistake a
Turkish file can make. Every capitalised word in `values-tr/strings.xml` was
checked against that rule, one by one. The ones that carry it are:

**İstasyonlar**, **İstasyon ara**, **İstasyon bulunamadı**, **İstasyon
listesi**, **İstasyonları görmek için…**, **İptal**, **İçe aktar**, **İndir:
%1$s**, **İndirme bekletildi**, **İndirme durdu**, **İndirmeyi yeniden
başlat**, **İşletmeci onu yenileyene kadar…** — and in the store texts **İlk
kurulabilir sürüm**, **İki anahtar da Ayarlar’da**, **İndirmeler…**,
**İngilizce**.

No string in the file begins with `ı`, so no bare `I` opens one either, and
none should be introduced: if a word ever has to start a sentence with `ı`
(*ışık*, *ılık*), its capital is **I** and not **İ**.

**It matters past the file as well.** The station list stacks a figure over
its label and sets the label in capitals through
`TextAppearance.RoueLibre.Label`, so `bisiklet` is upper-cased at render time.
That upper-casing has to happen in the Turkish locale to give **BİSİKLET**; in
the root locale it gives *BISIKLET*, which a Turkish reader sees as a
different word. Nothing in this file can fix that — it is a property of the
locale the view is inflated in — but it is where the mistake would show, and
it is worth a look on the device when the language is recetted.

## Register

Turkish button labels take the bare imperative — *Sil*, *Yenile*, *Göster*,
*Devam et*, *Atla*, *Değiştir* — which is morphologically the singular. This
file therefore addresses the reader in the **singular (sen)** in its sentences
too: *başlangıç noktanı seç*, *sen yazarken bile*, *daha sonra tekrar dene*.
Android's own Turkish mixes the two, putting *Tekrar dene* on a button
(`settings:network_connection_timeout_dialog_ok`) and *Tekrar deneyin* in a
message (`android:lockscreen_password_wrong`); mixing them inside one screen
reads worse than choosing one, and the singular is the one the buttons already
force. It is also what the project's tone is elsewhere.

**The impersonal is kept where the English is impersonal.** Turkish has the
passive for exactly this, and the promises that nothing is kept use it
throughout: *Yolculuklar bu telefonda hesaplanır*, *Hiçbir geçmiş tutulmaz*,
*Ağın kendi akışından okunur*, *Veriler yalnızca istendiğinde indirilir*,
*Her şey cihazda aranır*. There is no *biz* anywhere in the file and none
should be added: an application whose argument is that nobody is behind it
must not ask the reader to trust a "we". The one first-person plural in the
file is `city_proposal_body` (*Bununla devam edelim mi?*), where the English
itself says "Shall we go with that?" and nothing is being promised.

## Typography

Quotation marks are **“ … ”** — `stations_no_match_message`, `city_no_match_message`,
`address_no_match_message`, `incoming_address_not_found`, `dataset_delete_body`,
`dataset_rejected_format`, `error_feed_unavailable`,
`settings_map_filters_hide_empty_hint`, and `about_links_body` around *geo:*.

The apostrophe is **’** (U+2019) and never the straight quote, which is why
nothing in the file is escaped. Turkish writes an apostrophe before a suffix
attached to a proper name, and the file needs it in three places:
**Roue Libre’yi** and **Android’in** in `about_links_body`, **Fransa’da** in
`about_attribution_gbfs`. The store texts add **Paris’te**, **Ayarlar’da**,
**OpenStreetMap’ten**, **Boston’da**.

No space stands before `?`, `!`, `:` or `;`. The em dash of the English file
is kept as it is, in `city_label` and in the journey summaries; Turkish writes
it the same way.

Numbers keep whatever the localisation APIs give them, which for Turkish is a
decimal comma — *1,3 GB* in the changelog, and the same figure on the storage
screen.

## Suffixes are cases, and a placeholder cannot carry one

Turkish is agglutinative with vowel harmony, so an ending glued onto a `%1$s`
is wrong the moment the vowels of what arrives change: *Kadıköy’e* against
*Beşiktaş’a*, *Vélib’in* against *Citi Bike’ın*. Not one line in this file
does it. Four devices carry the weight instead, and each is worth knowing
before it gets "fixed":

| String | What it does | Why |
|---|---|---|
| `journey_step_to_station`, `journey_step_ride` | `Yürüyerek %1$s istasyonuna git` | Turkish's **bare noun-noun compound**: only the head takes the ending, so *istasyonuna* declines and the station's name stands in front of it untouched. |
| `map_outside_city_message`, `map_outside_city_brief`, `city_proposal_body` | `%1$s kapsama alanının dışında` | The same compound. What arrives is `cityLabel(...)`, a whole label — *Vélib’ Métropole — Paris* — and it modifies *kapsama alanı*, which carries every ending the sentence needs. |
| `city_delete_description`, `city_delete_body`, `city_deleted` | `%1$s için veriler…` | The postposition **için** governs the bare nominative, which is exactly the form a network name arrives in. |
| `storage_download_pending`, `dataset_delete_description` | `İndir: %1$s`, `Sil: %1$s` | The one place the compound would not do: an accusative object (*Harita verilerini indir*) would have to guess the vowels of a dataset name the string never sees. A colon turns the line into a label, which declines nothing. |
| `city_here_body`, `city_here_installed_body` | `%1$s bulunduğun bölgeye hizmet veriyor` | Subject position, where the nominative is what Turkish wants. |
| `dataset_imported`, `dataset_deleted` | `%1$s yüklendi`, `%1$s silindi` | Passive subject, nominative again. Nothing needs bending. |
| `station_address_nearby` | `%1$s yakınında` | *yakın* takes the ending, the street name does not. The argument is `address.streetName` — a street **or** a square, arriving as it stands. |
| `journey_climb`, `journey_bikes_at_departure` | `%1$s tırmanış`, `Kalkış istasyonunda %1$s` | A figure and a pair of counts, both already formatted; neither is ever inflected. |

**`city_delete_description`, `city_delete_body` and `city_deleted` are handed
the NETWORK's name, not the city's.** `CityAdapter.kt:111`, `CityFragment.kt:279`
and `:294` pass `city.displayName`, and 328 of the 331 catalogue entries carry
a `displayName` of their own — every Czech network is called *nextbike*.
Writing *%1$s şehrinin verileri* would have made a Brno reader read "the data
of the city nextbike". None of the three says *şehir*, and the English never
does either.

## Plurals

Turkish has two CLDR categories, `one` and `other`, and both are written. They
are word for word identical in every plural in the file, and that is correct
Turkish rather than an oversight: **a noun after a numeral stays singular** —
*3 bisiklet*, never *3 bisikletler*. The two categories are a fact about the
language and Android resolves both, so both are filled in; what changes
between them is nothing.

## The address prompt

`address_search_hint` is **“Sokak, numara, şehir”** — street, then number,
then the town, which is the order Turkey writes an address in (*Bağdat
Caddesi 15, Kadıköy*). `AddressQuery.parseQuery` has read a house number
standing between the street and the town since the pilot, precisely so that
each language may write this line in its own order rather than in English's.

*Sokak* rather than *cadde*: both are Turkish for a street and *cadde* is the
larger of the two, but *sokak* is the generic one — it is what *sokak adı*
means in `address_search_prompt_message` — and a prompt naming the larger sort
would read as excluding the smaller.

*Şehir* stands in for the English "town". It is not perfect: the index carries
villages and suburbs as well as cities, and Turkey's own address hierarchy
would want *ilçe* or *mahalle*. Neither was taken, for the same reason: they
are Turkish administrative units, and this prompt is read by somebody typing a
Paris or a Prague address just as often as a Konya one. *Şehir* is also what
`city_search_hint` and the whole city screen say, so the two screens name one
thing with one word.

**The postcode is deliberately not invited.** Turkey writes it as a single
group of five digits (*34710*), which is exactly what `looksLikePostcode`
filters (`POSTCODE_LENGTH = 5`), and `parseQuery` strips it **before** it
looks for a house number — so *Bağdat Caddesi 15, 34710 Kadıköy* would keep
its door. Turkey is one of the countries the parser handles cleanly. The
reason it stays out is the other end of the same code: a stripped postcode
narrows nothing, because the index does not hold it in full text. Typing it
changes no result, and a three-word prompt should not invite a fourth thing
the search discards.

The second guard — no stop word beside a number read between street and town —
protects streets named after a date. Turkey has a great many of them
(*19 Mayıs Caddesi*, *29 Ekim Bulvarı*, *100. Yıl*), and they put the figure
at the **head** of the name rather than after it, which is the far side of the
street from where this prompt invites a number. The prompt stacks no second
number, so the first guard is not stressed either.

**The layout of an address is not this file's business.** A Konya address
reads the way Turkey writes it whatever language the interface speaks, and a
Lyon address reads *12 rue Nationale* to a reader in Turkish. That lives in
`core/.../address/AddressLayout.kt`, keyed on the language of the address base
(SPEC §4.3).

**There is no Turkish entry in that table, and this file does not write one.**
`LAYOUTS` holds `fr, de, es, pt, it, nl, pl, cs, sk, da, fi, sl, hr, ro, sv,
nb, el` and nothing else, so a Turkish address base falls on `DEFAULT_LAYOUT`
— number first, spaced — and the one Turkish network served (**AAR Bike**,
Konya, `country: "TR"` in `config/catalogue.json`) would print *15 Atatürk
Caddesi*, which is not an address any Turk would recognise. Turkey writes the
street first and the number after it, usually behind *No:* — *Atatürk Caddesi
No: 15*, or simply *Atatürk Caddesi 15*. The entry that would fix it is
`"tr" to AddressLayout(numberComesFirst = false, streetSeparator = " ",
suffixSeparator = "/")` — the slash being how Turkey writes a flat inside a
building, *15/3* — but the brief is explicit that the line is one line and is
not the translator's to write, so it is **reported** here rather than added.

## The vocabulary

| English | Turkish | Why |
|---|---|---|
| journey | **yolculuk** | The whole door-to-door thing: the screen, the settings section, the button, the waits, the errors. It is the ordinary Turkish for a trip one takes, and it is what a journey planner calls the thing it plans. One word throughout, so `journey_title`, `settings_section_journey` and `journey_compute` are visibly about one object. |
| ride | **sürüş** | The bike leg alone, inside a journey: `journey_computing_own_bike` (*Sürüş hesaplanıyor…*), `journey_detail_profile` (*Sürüşün inişi ve çıkışı*), `journey_detail_profile_description`, `journey_hint_own_bike` (*kapıdan kapıya tek sürüş*). A different word from *yolculuk*, so the elevation profile and the own-bike wait cannot be mistaken for the whole thing. |
| route | **rota** | Only the line on the ground: `journey_no_route` (*Bu iki nokta arasında geçilebilir bir rota yok*), `dataset_routing` (*Rota verileri*), `settings_own_bike_kind_hint` (*kendi rotasını … alır*). Never the planned journey — `journey_none_title` one line above says *Yolculuk yok*, and the two words on that card name two different things on purpose. |
| station | **istasyon** | A bike-share station, and what Turkish bike-share systems call one. |
| railway station | **tren istasyonu** | What `address_search_prompt_message` means by "stations", as its comment in `values/` says. Turkish has the compound, so *istasyon* stays the bike-share one throughout with nothing to disambiguate. |
| bike | **bisiklet** | The everyday word, and the only one. |
| dock (free) | **boş yuva** | What a bike is returned into, counted as available: *6 bisiklet · 26 boş yuva*. Also the map's second mode, `mode_docks` (*Boş yuvalar*), and the counterpart label. **Yuva** is Android's own Turkish for a dock in the physical sense — the cradle a device sits in (`settings:media_transfer_dock_speaker_device_name`, *Yuva hoparlörü*; `settings:docking_sounds_title`, *Yuvaya yerleştirme sesleri*) — which is exactly the object here. |
| dock (capacity) | **park yeri** | The same object counted as a total, which is a different figure on the same screen: *26 boş yuva · 30 park yeri*. English says "dock" for both; Turkish does not have to, and *park yeri* is what a Turkish bike-share network calls a stand a bike is parked at. `docks_total` and `station_detail_with_capacity` are the two that carry it. |
| dock | *never* **ödeme noktası** | Not the payment terminal, which is a different object and is not named in this application at all. |
| mechanical | **klasik** | The word Turkish actually uses against *elektrikli bisiklet*. *Mekanik bisiklet* is not said by anybody. Not from the lexicon — Android has no bike vocabulary — and this is the file's own choice. |
| electric | **elektrikli** | Likewise. `journey_bike_kind_electric_description` says **pedal destekli** so that "electric" cannot be read as a moped. |
| the two counts side by side | **%1$d klasik · %1$d elektrikli** | Both elliptical, both singular, matching what a Turk would say aloud. The noun is not repeated because the label beside them already says *bisiklet*. |
| pace (walking) | **tempo** | A pace is not a speed, which `values/strings.xml` says above the string. *Tempo* is a pace one knows about oneself; *hız* is the figure nobody has measured about themselves, and is not used. |
| Slow / Normal / Brisk | **Yavaş / Normal / Tempolu** | *Yavaş* is Android's own (`settings:speed_label_slow`). *Tempolu* is the ordinary Turkish for a brisk walk (*tempolu yürüyüş*) and keeps the pace-not-speed line, where *hızlı* — Android's word for "Fast" (`settings:speed_label_fast`) — would have crossed it. |
| climb | **tırmanış** | The metres climbed, over a leg or over the whole journey, written after its figure: *120 m tırmanış*. |
| availability | **durum** | What the network publishes about a station and the one figure that needs the network: `station_availability_unknown` (*Durum bilinmiyor*), `error_offline` (*Son bilinen durum*), `about_privacy_body`, `sources_intro`, and the store texts (*anlık bisiklet durumu*). Turkish has no comfortable noun for "availability" — *müsaitlik* is what a diary has — and *durum* is what a Turkish transit app puts over the same figure. |
| location, position | **konum** | English has two words here and Turkish has one, so the file uses one: *Konumum*, *Konumun … dışında kalıyor*, *Konumumu bul*, *yaklaşık konum*. **Konum** is also Android's own word for the system feature and the permission (`android:permgrouplab_location`, `settings:location_settings_title`). Forcing a second word to mirror the English would read as a translation, not as Turkish. |
| network (bike-share) | **ağ** | The operator whose bikes these are: *Ağın sunucusu*, *ağın kendi akışı*, *Ağ yalnızca klasik bisiklet sunuyor*, *Buraya yakın bilinen bir ağ yok*. |
| network (data) | **bağlantı** | The connection. Android's own noun for it (`settings:bluetooth_profile_pan_nap`, *İnternet bağlantısı paylaşımı*), and the file never says *ağ* for it: *Bağlantı yok*, *bağlantı olmadan çalışır*, *sayaçsız bağlantı*, *kablosuz bağlantı*. **This is the one distinction most easily lost, and losing it makes *the network's server* read as *the internet's server*.** Where the English says "goes out on the network", the Turkish says *dışarı çıkar* rather than reaching for *ağ*. |
| conurbation | **şehir** | The city screen serves a metropolitan area rather than a municipality, and Turkish's administrative word for that — *büyükşehir* — is a legal unit of Turkey's own that would be wrong for Paris and Copenhagen. *Şehir* everywhere, with *ve çevresi* where the surrounding area has to be said (changelog 2, *Paris ve çevresi*). |
| dataset | **veri** in a name, **veri kümesi** when counted | *Harita verileri*, *Rota verileri*, *Adres dizini* are the three names, and *Çevrimdışı veriler* the settings section. Where the screen counts them — `storage_intro`, *bir kez yüklenen üç veri kümesi* — *veri* alone is uncountable in Turkish and cannot take *üç*, so the compound is used for that one sentence. |
| address index | **adres dizini** | An index one looks a name up in. *Veri tabanı* says how it is stored, which is not the reader's business. |
| landmarks | **önemli noktalar** | The metro stations, libraries and squares the index carries beside the streets. |
| tracker | **izleyici** | Used in the interface and in the store's short description alike, so the promise reads the same before and after installing. |
| Settings | **Ayarlar** | Android's own (`settings:settings_label` and passim), including in the system path quoted in `about_links_body` — *Ayarlar → Uygulamalar → Roue Libre → Varsayılan olarak aç → Bağlantı ekle* — which is Android's own Turkish for that screen, key for key (`settings:apps_dashboard_title`, `settings:launch_by_default`, `settings:app_launch_add_link`). |
| Display (section) | **Ekran** | Android's own name for the section that holds the theme (`settings:display_category_title`, `settings:display_settings`). |
| Theme | **Tema** | Android's own: *Koyu tema* (`settings:dark_ui_mode`), *Cihaz teması* (`settings:device_theme`). Dark is **Koyu**, from the same key. |
| Light (theme) | **Açık** | **Not from the lexicon, and here is why.** Android's Turkish names only the dark theme; there is no "light theme" string to grep in `framework-res` or `Settings`. *Açık* is the ordinary Turkish opposite of *koyu* — of a colour, not of a door — and is what Turkish interfaces use. |
| Storage | **Depolama** | Android's own (`settings:storage_settings`, `settings:storage_label`), and the name the screen carries. Everywhere another string points at that screen it says **depolama ekranından** in the same words — `map_needs_tiles_message`, `journey_graph_missing`, `address_needs_index_message`. |
| Delete / Remove | **Sil** / **Kaldır** | Turkish keeps the two apart and so does this file. *Sil* destroys (`android:delete`, `settings:delete`): `city_delete`, `dataset_delete`, `map_picked_place_description`. *Kaldır* takes out of a list (`settings:remove`, `android:kg_reordering_delete_drop_target_text`): `station_favourite_remove`, *Favorilerden kaldır*. |
| Clear (a search) | **Aramayı temizle** | Android's verb for emptying a field (`settings:proxy_clear_text`, `settings:lockpattern_retry_button_text`). One wording serves the icon inside the field, the button in the empty state and the city screen alike. |
| Check (spelling) | **kontrol et** | Android writes both *kontrol* and *denetle* for "check" and puts them in different places; this file follows it, using *kontrol et* for looking something over (`settings:ambient_display_title`, *Telefonu kontrol etmek için…*) and keeping *denetle* for the update check below, which Android writes whole. |
| Check for updates | **Güncellemeleri denetle** | Android's own, whole (`android:unsupported_compile_sdk_check_update`, `android:deprecated_target_sdk_app_store`). |
| Update available | **Güncelleme var** | **A departure from Android, and a necessary one.** The lexicon's Turkish for "Update available" is *Uygulama güncellendi* (`settings:android_version_pending_update_summary`) — which means "the app **was** updated", the opposite of what this badge says. Android's Turkish is wrong there; following it would put a badge on a stale dataset saying it is fresh. *Güncelleme var* is this file's own and says what is true. |
| Refresh / Updated | **Yenile** / **güncellendi** | Two roots, as in English: *Yenile* on the button (`settings:show_refresh_rate`, *Yenileme hızını göster*), *az önce güncellendi* under the data. |
| Try again | **Tekrar dene** | Android's own (`settings:network_connection_timeout_dialog_ok`, `settings:security_settings_fingerprint_enroll_dialog_try_again`). |
| Continue | **Devam et** | Android's own for the button that carries on through a wizard (`settings:lockpattern_continue_button_text`, *Devam Et*), lower-cased to this file's sentence case. |
| Skip | **Atla** | Android's own (`android:skip_button_label`). |
| Show | **Göster** | Android's own (`settings:condition_expand_show`). |
| Back | **Geri** | Android's own, on the toolbar arrow (`android:back_button_label`). |
| Cancel | **İptal** | Android's own (`android:cancel`), on every dialog. |
| Yes | **Evet** | Android's own (`android:gpsVerifYes`, `settings:yes`). |
| In use | **Kullanımda** | Android's own (`android:media_route_status_in_use`), on the city already selected. |
| Available | **Kullanılabilir** | Android's own (`android:media_route_status_available`), in `mode_bikes_description` and wherever something stays usable. |
| Unknown | **bilinmiyor** | Android's own (`settings:unknown`, `android:unknownName`). |
| Out of service | **Hizmet dışı** | Android's Turkish for the phrase is *Hizmet Dışı*, and the key it comes from is `settings:radioInfo_service_out` — a phone with no radio service rather than a machine that is not working. Unlike some languages, the Turkish phrase carries both senses perfectly well, so it is kept: it is the ordinary Turkish on a machine's out-of-order notice, and `settings_map_filters_hide_out_of_service` echoes it (*Hizmet dışı istasyonları gizle*). Sentence case, not Android's title case. |
| just now | **az önce** | Android's own (`settings:time_unit_just_now`), lower-cased because it is only ever read inside `freshness_fresh`: *az önce güncellendi*. |
| Replace | **Değiştir** | Android's own (`settings:vpn_replace`). |
| Import | **İçe aktar** | **Not from the lexicon**: neither `framework-res` nor `Settings` carries an "Import" string to grep. *İçe aktar* is the standard Turkish for bringing a file in, and the counterpart of *dışa aktar*. |
| Free up space | **Yer aç** | Android's own (`settings:storage_free_up_space_title`, `settings:storage_menu_free`), in `error_local_storage_download`. |
| metered / unmetered | **sayaçlı / sayaçsız** | Android labels a connection *Sayaçlı* (`settings:wifi_metered_label`, `settings:data_usage_metered_yes`); *sayaçsız* is its regular negative. The sentences explaining the setting say what is billed — *megabayt başına ücretlendirilir* — since that is the point the English makes. |
| Wi-Fi | **kablosuz bağlantı** | **Android's Turkish, and not the loanword.** `settings:wifi` is *Kablosuz*, and where a full phrase is needed Android writes *kablosuz bağlantı* (`settings:credential_for_wifi`, `settings:condition_cellular_summary`). Everyday Turkish says *Wi-Fi* and would have been defensible; the brief's rule is that Android is the arbiter where a row exists, and one does. |
| Mobile data | **mobil veri** | Android's own (`settings:mobile_data_settings_title`). |
| Apps | **Uygulamalar** | Android's own (`settings:apps_dashboard_title`), in the system path. |
| Android's chooser | **Android’in uygulama seçicisi** | **A phrase this file forms, not a key to grep.** Android's Turkish names the sheets by their titles — *Şununla aç:* for a VIEW intent (`android:whichViewApplication`), *Şununla paylaş:* for a SEND one (`android:share_action_provider_share_with`) — and an address arriving as plain text raises the second while a `geo:` link raises the first, so naming either would leave half the readers looking for a title they never see. The English says "Android's chooser" generically for exactly this reason. *Seçici* is Android's own noun for a chooser (`android:activitychooserview_choose_application` heads its sheet *Bir uygulama seçin*). |
| Press and hold | **dokunup basılı tut** | Android writes the gesture *Dokunun ve basılı tutun* (`android:content_description_sliding_handle`); `favourites_reorder_hint` folds it into the shape the sentence needs. |
| Tap | **dokun** | Android's verb (`settings:inactive_app_active_summary`, *Geçiş yapmak için dokunun*), in the singular this file uses. |
| Language | **Dil** | Android's own (`settings:app_locale_preference_title`). |
| System | **Sistem** | Android's own (`settings:header_category_system`), for the theme, the units and the language alike. |
| Privacy | **Gizlilik** | Android's own (`settings:privacy_dashboard_title`). |
| Version | **Sürüm** | Android's own (`settings:vpn_version`). Both `about_version` and `dataset_rejected_version` use it, and so does *sürüm notu* in `whats_new_nothing`. |
| Units | **Birimler** | Android's own (`settings:regional_preferences_summary`, *Birimleri ve sayı tercihlerini ayarlayın*). |
| Favourites | **Favoriler** | **Not from the lexicon** — Android's Turkish has no favourites screen to grep — but it is what every Turkish application calls one, and *sık kullanılanlar* is longer and belongs to a browser's bookmarks. |
| what's new | **Yenilikler** | What the screen shows is the release notes, and *Yenilikler* is what an app store writes above them. |
| Delete this data? | **Bu veriler silinsin mi?** | The optative-passive question Turkish puts on a confirmation dialog, rather than a literal *Bu veriler silinecek mi?*, which asks about the future instead of asking permission. Both `city_delete_title` and `dataset_delete_title` use it, word for word: they are the same question about two objects. |

## Units, and the two that are translated

Distance symbols stay as they are — `m`, `km`, `ft`, `yd`, `mi` — because
Turkish writes them the same way; the descriptions beside them spell out the
Turkish names (*Metre ve kilometre*, *Fit ve mil*, *Yarda ve mil*). The byte
units are Turkish's too as they stand: `B`, `kB`, `MB`, `GB`.

**The two time symbols are not.** Turkish abbreviates *dakika* to **dk** and
*saat* to **sa**, and writes neither `min` nor `h`. So `duration_minutes` is
*%1$d dk* and `duration_hours_minutes` is *%1$d sa %2$02d*. This is the only
place the file departs from the brief's list of untranslated symbols, and it
departs on the ground the brief itself allows: the language genuinely writes
them differently.

## Words that are not translated

Product and network names — Roue Libre, Vélib’, V’lille, Vélo’v, Citi Bike,
AAR Bike, BRouter, MapLibre, OpenStreetMap, GBFS — and the licence names. The
`resources` `name` attributes, always. And the format-only strings
(`station_content_description`, `address_locality`, `address_detail`,
`station_capacity_and_age`, `dataset_installed`, `city_label`,
`storage_download_failed`), whose punctuation is already what Turkish uses.
`welcome_step` is the one that changed: English's *%1$d of %2$d* becomes
*%1$d / %2$d*, since Turkish would have to inflect *%2$d* to write it in
words and the slash says the same thing without a suffix.

`storage_downloading` was reordered for the same reason — *%1$s · %2$s / %3$s*
rather than a phrase with *üzerinden* — and keeps all three placeholders.

## What the validator says

`python3 tools/check_translations.py tr` reports one warning, and it is a
legitimate one: `settings_walking_pace_normal` comes back as **Normal**, which
is the Turkish word as well as the English one. There is no "Normal" row in
the lexicon to cite — the nearest is `settings:battery_tip_summary_title`,
*Uygulamalar normal şekilde çalışıyor*, which shows the word is ordinary
Turkish rather than a loan left untranslated. Every other string in the file
differs from its English source.
