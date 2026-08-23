# Arabic glossary

The terms `res/values-ar/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Arabic ones over three screens, and so that a contributor can correct
one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Register and typography

The register is **Modern Standard Arabic**, the register Android's own Settings
are written in — neither dialect nor the flowery commercial register a service
would use on a customer. SPEC §9 asks the French for *tu* because the
application speaks to one person walking to a station; Arabic has no such
choice to make, and the same intention lands as the plain second-person
singular (**أنت**), which is what the file uses throughout: `اختر`, `ثبّت`,
`أعد المحاولة`. The system lexicon extracted from `framework-res.apk` and
`Settings.apk` is the arbiter for anything Android already names.

The English is **scrupulously impersonal** — "No history is kept", "It is read
from the feed" — and Arabic keeps it, in the passive voice it has for exactly
this: `لا يُحفظ أي سجلّ`, `تُقرأ من تدفّق بيانات شبكة الدراجات`, `تُحسب الرحلات
على هذا الهاتف`. Nowhere does the file say *we*. In a privacy text whose whole
argument is that nobody is behind the application, a "we" would ask the reader
to trust a party instead of stating a property of the software.

The punctuation is Arabic: the comma is **،** (U+060C), the question mark
**؟** (U+061F), and a quotation is set in **«…»**. The semicolon, where the
English uses one, is **؛** (U+061B). Arabic writes no apostrophe, so **nothing
in the file is escaped**.

### The right-to-left mark, and why it is written out

A value that opens on a placeholder or on a Latin word carries **U+200F** in
front of it, written as the numeric reference `&#x200F;` so that it can be seen
while reading the file rather than being an invisible byte a later edit drops.
That is what Android's own Arabic resources do — the lexicon is full of rows
like `‏شهادة CA واحدة` and `‏خرائط Google`. Without it, a network name or a
figure standing at the head of the line sets the paragraph direction from its
own characters, and `%1$s يخدم` comes out with the name on the wrong side of
the sentence.

It is written only where the value **begins** with a placeholder, a Latin word
or `©`. Where Arabic reordering could put a word in front instead, that was
done and the mark left out: `يقع موقعك خارج المنطقة التي يغطّيها %1$s` needs
no mark, and reads better than a mark plus the English order would.

### Digits

**Not one digit is written in this file.** The application formats every figure
it shows, and Android serves Arabic-Indic digits on an Arabic device (SPEC §9);
a digit typed by hand into a resource would put two numbering systems on one
line. Where the English spells a number out in words — "forty megabytes" — so
does the Arabic: `أربعين ميغابايت`.

The store texts under `fastlane/metadata/android/ar/` are the exception, and
deliberately: the application's "what's new" screen prints those files
verbatim, beside an interface whose own figures are Arabic-Indic, so their
figures are **written in Arabic-Indic digits** (`٣٠٦ شبكة`, `١٤٣ ميغابايت`)
with the Arabic decimal separator **٫** (U+066B) in `١٫٣ غيغابايت`. Latin
digits there would have been the very mixture the rule above forbids. The one
network Arabic serves is Careem BIKE in Dubai, and the Gulf writes
Arabic-Indic.

## Plurals: six categories, three shapes

CLDR gives Arabic six cardinal categories, and the file writes all six:
`zero`, `one`, `two`, `few` (3–10), `many` (11–99), `other` (100 and beyond).

What they carry is decided by the **tamyiz**, the rule that governs a noun
standing after a numeral — and the numeral is always written here, because
every one of these strings holds its `%1$d`:

| category | shape | example |
|---|---|---|
| `zero`, `one`, `other` | plain singular | `٠ دراجة`, `١ دراجة`, `١٠٠ دراجة` |
| `two` | dual | `٢ دراجتان` |
| `few` (3–10) | plural | `٥ دراجات` |
| `many` (11–99) | accusative singular, with tanwin | `١٥ دراجةً` |

So **three of the six read alike, and that is Arabic rather than a line nobody
reached.** `zero`, `one` and `other` all take the singular after a written
numeral; there is no fourth shape to give them. Writing `لا توجد دراجات` in
`zero` would have read better but would have dropped the placeholder the
validator and the call site both require. `two` carries the dual even though
the numeral has already said "two": that redundancy is what filling the `two`
slot means once the figure is on screen, and leaving the singular there would
have wasted the one category Arabic has that most languages do not.

`counterpart_bikes` and `counterpart_docks` are the exception to the table
above, and they hold **no** placeholder — the figure is painted in the disc
beside them. Two things about the screen, rather than about the grammar,
decide what goes in each item.

`zero` is what an **unknown** count resolves to: `StationDetailSheet` and
`StationAdapter` both call `getQuantityString(…, count ?: 0)`, and both say in
so many words that an unknown count reads as the plural. So `zero` carries
`دراجات` and `مواقف شاغرة`, not the singular a written ٠ would have taken.

And `many` is written **bare**, `دراجة` and not `دراجةً`. On a list row the
label is stacked under the figure, in `TextAppearance.RoueLibre.Label`, and is
read as a word standing on its own line — which is the trap the English file
records Romanian falling into. An accusative tanwin is the tamyiz of a numeral
standing beside the noun; with the numeral on the line above, it is a
diacritic on a word nobody is counting. `bikes_available`, where the figure
really is in the string, keeps it.

## The vocabulary

| English | Arabic | Why |
|---|---|---|
| journey | رحلة | The whole door-to-door thing: the screen, the settings section, the button, from `journey_title` through to `journey_no_route`. What an Arabic transit application calls a planned trip. |
| ride | ركوب | The bike leg alone, inside a journey. Kept away from رحلة so that `journey_summary` can read `منها ١٥ دقيقة مشيًا و٢٠ دقيقة ركوبًا` and name two different things on one line. |
| walk (a leg) | مشي / سيرًا على الأقدام | The noun in the summaries and the steps, the adverbial phrase where a whole journey is on foot (`journey_walk_only`). |
| route | مسار | Only the line on the ground: `journey_no_route`, `journey_graph_missing`, `dataset_routing`. Never the planned journey, which is رحلة. |
| station | محطة | A bike-share station, everywhere in the interface. |
| railway station | محطة قطار / محطة مترو | What `address_search_prompt_message` means by "stations", and it says so: `محطات القطار والمترو`. Arabic has one word for both, so the message names the kind rather than leaving محطة to be misread as a bike station. |
| dock (free) | موقف شاغر | What a bike is returned into, counted as available. |
| dock (capacity) | موقف | The same object counted as a total, which is a different figure on the same row: `٢٠ موقفًا شاغرًا · ٣٠ موقفًا`. English says "dock" for both; Arabic does not have to. |
| dock | *never* مرسى | مرسى is the exact technical word — a docking station is محطة إرساء — and it was the first choice. It was dropped because **المرسى is a district of Dubai**, and Dubai is the one city Arabic serves: Careem BIKE is the only Arabic-speaking network in `config/catalogue.json`. A bike dock named after Dubai Marina is a collision no glossary entry repairs. موقف is regular, short, and `موقف دراجات` is what bike parking is called. |
| bike | دراجة | Never دراجة نارية, which is a motorcycle. In the store texts, where the reader has no context yet, the phrase is الدراجات المشتركة. |
| mechanical bike | تقليدية | Named against كهربائية. Not هوائية: an electric bike is a دراجة هوائية too, so the contrast would not hold. This is the form the toggles and the two-button choosers carry — `map_bikes_mechanical`, `journey_bike_kind_mechanical`, `settings_own_bike_kind_mechanical`. |
| electric bike | كهربائية | Pedal-assist, which `journey_bike_kind_electric_description` spells out as `دراجة تساعدني كهربائيًا حين أدوس` so that nobody reads it as a moped. |
| the two counts side by side | دراجة تقليدية / دراجة كهربائية | `bikes_mechanical` and `bikes_electric` are read apart from any label — `journey_bikes_at_departure` hangs them off the end of a summary — so each carries its noun. `٣ تقليدية` is not a phrase that stands alone; `٣ دراجات تقليدية` is. |
| network | شبكة الدراجات | **A departure worth stating.** Arabic شبكة alone is what a data network is called, and the same screens say `لا يتوفّر اتصال`; `خادم الشبكة` would have read as the data network's server. Naming it in full every time costs two words and removes the ambiguity everywhere. |
| operator | المشغّل | The party who renews the certificate, in `error_untrusted_server`. Named apart from the network so the sentence has somebody to point at. |
| feed (GBFS) | تدفّق البيانات | — |
| city | مدينة | — |
| conurbation | تجمّع حضري | Where the English deliberately says "conurbation" rather than "city": `city_intro`, `map_needs_city_message`, `welcome_data_body`. |
| the area a city covers | المنطقة التي يغطّيها … | One notion, one verb (غطّى), over the nine strings that carry it. |
| pace (walking) | وتيرة | A pace is not a speed: `values/strings.xml` says so above the string, and سرعة would say the opposite. |
| Slow / Normal / Brisk | بطيئة / عادية / نشيطة | بطيئة is the lexicon's own (`settings:speed_label_slow`) and agrees with وتيرة. نشيطة for "brisk" because المشي النشيط is what brisk walking is called in Arabic; سريعة would have made a speed of it again. |
| Out of service | خارج الخدمة | **A departure from the lexicon**, which gives خارج نطاق الخدمة (`settings:radioInfo_service_out`). That is Android's phrase for a radio blackspot — literally "outside the service *range*" — and says nothing about a station an operator has taken out of use. خارج الخدمة is what a lift or a machine says. |
| Settings | الإعدادات | Android's own word. |
| Display (settings section) | العرض | Android's own word for the settings category (`settings:display_category_title`). |
| System (theme, units, language) | النظام | The English writes one word in all three settings, and so does this. |
| Theme | المظهر | From the lexicon's المظهر الداكن (`settings:dark_ui_mode`). The two states are داكن and فاتح; only the first is in the lexicon, and the second is its obvious partner. |
| Language | اللغة | Android's own word (`settings:app_locale_preference_title`). |
| Storage | التخزين | Android's own word (`settings:storage_label`). |
| Privacy | الخصوصية | Android's own word. |
| Version | الإصدار | Android's own word (`settings:vpn_version`), and the same word carries the data-format version in `dataset_rejected_version`. |
| Licence | الترخيص / التراخيص | Android's own word (`settings:license_title`); `about_open_licences` reads تراخيص المكوّنات, after the lexicon's تراخيص الأطراف الثالثة. |
| Search | البحث | Android's own word. |
| Searching… | جارٍ البحث… | The lexicon's own (`settings:wifi_p2p_menu_searching`). The message under it uses a different verb — `يجري تفحّص العناوين` — because the English does too. |
| Clear (a field) | محو | Android's own word (`settings:clear`). Emptying a field, which is what "clear the search" does. |
| Refresh / Update | تحديث | Arabic uses the one word for both, as it does throughout Settings, and no ambiguity arises: `action_refresh` sits on a station list and `storage_check_updates` on the storage screen. |
| Try again | إعادة المحاولة / أعد المحاولة | The noun form on a button (`settings:retry`), the imperative in running prose (`android:lockscreen_password_wrong`). Both are the lexicon's. |
| Continue | متابعة | Android's own word, on the welcome pages and on the "what's new" screen alike — the English writes one word in both places. التالي ("Next") was left aside for that reason. |
| Skip | التخطي | Android's own word. |
| Back | رجوع | Android's own word. |
| Cancel | إلغاء | Android's own word. |
| Yes | نعم | Android's own word. |
| Show | إظهار | Android's own word (`settings:condition_expand_show`). |
| Add | إضافة | Android's own word. |
| In use | قيد الاستخدام | Android's own word. |
| Delete | حذف | Destroys: a dataset, a city's data. Android's own word. |
| Remove (from favourites) | إزالة | Takes out of a list. Android distinguishes the two exactly as the application does — حذف (`settings:delete`) against إزالة (`settings:remove`) — so `إزالة من المفضّلة` stands beside `إضافة إلى المفضّلة`. |
| Replace | استبدال | Android's own word (`settings:vpn_replace`). |
| Import | استيراد | — |
| Install / Installed | تثبيت / مثبّت | Android's own words. |
| Download | تنزيل | Android's own word, kept apart from تحميل, which the lexicon uses for loading. |
| offline | دون اتصال | `settings_section_data` is البيانات دون اتصال, and every string about working without a network is built on it. Not بلا إنترنت: the application works without any network at all, not merely without the internet. |
| map data / tiles | بيانات الخريطة | The name the storage screen gives the dataset, and the one every other string must use for it. |
| routing data | بيانات المسارات | Built on مسار, so that `journey_graph_missing` names the thing the journey screen is about. |
| address index | فهرس العناوين | — |
| metered / unmetered | يفرض تكلفة استخدام / لا يفرض تكلفة استخدام | Android's own phrasing (`settings:wifi_metered_label`, `settings:wifitrackerlib_wifi_unmetered_label`). Like the English, the setting names what is billed rather than Wi-Fi; the sentences under it say `يُحتسب بالميغابايت`, which is the English's own image. |
| climb | صعود | A journey's metres of ascent, in `journey_climb` and the four `…_climb` summaries. |
| favourites | المفضّلة | — |
| Location | الموقع الجغرافي | Android's own word for the permission and the setting (`android:permgrouplab_location`); the reader's own position is simply موقعك. |
| bytes | بايت / كيلوبايت / ميغابايت / غيغابايت | Arabic writes these out; the lexicon has بايت (`android:byteShort`) and كيلوبايت. |
| distance symbols | م · كم · قدم · ياردة · ميل | The one place a unit symbol was **not** left in Latin. Arabic road signs count in كم, and a Latin `km` beside an Arabic-Indic figure would be the two-systems-on-one-line defect again. |

## Five strings where a noun follows a numeral and cannot agree

`duration_minutes`, `duration_hours_minutes`, `distance_feet`,
`distance_yards` and `distance_miles` are plain strings in the source, not
`<plurals>`: English writes "min", "h", "ft", "yd" and "mi", abbreviations that
need no agreement. Arabic writes the words, and the words agree. With no
category to resolve against, one shape has to serve every count.

**`duration_hours_minutes` was repaired by going back to the source's own
shape.** Writing `%1$d ساعة و%2$d دقيقة` invented two agreements the English
never asked for — `١ ساعة و٥ دقيقة` is wrong, `٢ ساعة` wants `ساعتان`,
`٣ ساعة` wants `ساعات` — where the source writes no noun behind its minutes at
all. The file now writes **`%1$d س %2$02d`**: `س` is CLDR's Arabic abbreviation
for the hour, it is not a currency, and the padding comes back with it.

**`duration_minutes` still carries the compromise**, as **`%1$d دقيقة`**: the
singular, which is the tamyiz for 11–99 and beyond. A leg of three to ten
minutes therefore reads `٥ دقيقة` where Arabic wants `٥ دقائق` — and that is
the commoner case for a walk, so this is the worst of the five. The
abbreviation `%1$d د` would sidestep it the way "min" does, and was dropped
because a bare **د** beside a figure is read as a currency in the Gulf, and
Dubai is the city this translation serves. **The real repair is a `<plurals>`
in `values/strings.xml`, with `Durations.kt` moving to `getQuantityString`** —
a change that touches every language, and one a translation may not make on
its own. It is flagged rather than worked around.

**`distance_feet`, `distance_yards` and `distance_miles` carry the same
compromise**, as `قدم`, `ياردة` and `ميل`: `٥ قدم` wants `٥ أقدام` and
`٣ ميل` wants `٣ أميال`. They are left as they are, and they cost less: the
imperial systems are served to readers in the United States and the United
Kingdom, and an invariable unit noun is tolerated in a figure-plus-unit
reading. `distance_metres` and `distance_kilometres` have no such problem —
`م` and `كم` are abbreviations, and abbreviations do not decline.

## The search prompt is written in the Arabic order

`address_search_hint` is **`الشارع، الرقم، المدينة`**, and not the source's
"Number, street, town". SPEC §4.3 lets every translation write this prompt in
the order of its own language, because `AddressQuery.parseQuery` reads a house
number in three positions — opening the query, closing it, and **between the
street and the town**. Arabic writes an address street-first, with the number
after the street name and the town last (`شارع النصر ١٢ دبي`), which is exactly
that third position.

The prompt invites **one** number and no postcode, which is what the two guards
in SPEC §4.3 require: a number that does not open the query is given up as soon
as a second number appears, and a number between street and town is read only
when no stop word stands beside it.

## The address layout is not this file's business

`address_with_number` and `address_number_with_suffix` no longer exist, and no
replacement belongs here. **An address is laid out the way its own country
writes it** — a Lyon address reads `12 rue Nationale` to an Arabic reader, a
Warsaw one `Marszałkowska 12` — from the table in
`core/address/AddressLayout.kt`, keyed on the language of the address base
(SPEC §4.3). This file decides the words **around** an address and nothing
about the address itself. The figures are the exception the specification
already settles: digits and separators follow the reader (SPEC §9).

## Words that are not translated

Product and network names — Roue Libre, Vélib', Citi Bike, Careem BIKE,
BRouter, MapLibre, OpenStreetMap, GBFS, Wi-Fi, Base Adresse Nationale — and the
licence names (ODbL, MIT, BSD, GNU GPL). `resources` `name` attributes, always.

The map's attribution is translated — `© مساهمو OpenStreetMap` — which is the
wording OpenStreetMap's own Arabic pages use.

Format-only values keep their `·` separator: it is neutral in the bidirectional
algorithm and needs no Arabic equivalent.

The path in `about_links_body` is written with **←**, not the source's →: the
line reads right to left, and an arrow pointing the reader's way has to point
left. U+2192 is not mirrored by the bidirectional algorithm, so it had to be
changed by hand rather than left to the renderer.
