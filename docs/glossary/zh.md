# Chinese glossary

The terms `res/values-zh/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Chinese ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## The script, and why it is Simplified

The folder carries **no region**: `values-zh`, not `values-zh-rCN`. Android
serves it to every Chinese reader whose more precise folder is missing, and
what it serves for a bare `zh` is **Simplified**. So Simplified is what is
written here, with mainland vocabulary throughout — 自行车 and not 單車,
存储空间 and not 儲存空間, 应用 and not 應用程式.

The alternative was considered and refused. Traditional in `values-zh` would
reach a Hong Kong or a Taiwan reader in their own script and a Beijing one in
a script they read with effort, where Simplified reaches the largest readership
correctly. It also leaves `values-zh-rTW` and `values-zh-rHK` free for whoever
writes them: those are conversions of vocabulary as much as of glyphs — 单车,
捷運, 資料 — and are a translator's work, not a converter's.

One string arrived here already Simplified, `city_needs_newer_version`, written
by whoever set the started file up. It reads the same today.

## Register and typography

The register is **neutral and plain**, addressing the reader as **您** where a
pronoun cannot be dropped and dropping it wherever Chinese would. SPEC §9 asks
the French for *tu* because the application speaks to one person walking to a
station rather than to a customer; Chinese has no *tu / vous* choice to make,
and 您 is what Android's own Simplified strings write. No 请 is piled onto a
button — 请 heads an instruction, never a label.

The arbiter throughout is the **Simplified system lexicon**, extracted with
`aapt2 dump resources` from `framework-res.apk` and `Settings.apk` on a device
in `zh-rCN`: 8,675 rows of English beside the Chinese Android itself shows. It
is cited below as `android:…` and `settings:…`.

**Punctuation is full-width** — `，` `。` `：` `、` `（）` `“”` `？` — and
nothing is padded around it: the glyph already occupies the space a Latin mark
would need beside it. The Chinese enumeration comma `、` separates the items of
a list, which is what `station_content_description` and
`address_content_description` are: `%1$s、%2$s、%3$s`, as androidx writes
`summary_collapsed_preference_list` (`%1$s、%2$s`).

**A half-width space separates Chinese characters from a Latin run or a
figure.** That is what Simplified Android does — 「%1$d 个应用正在消耗电量」,
「Enter 键」, 「转到 %1$d 年」, 「%1$d mAh」 — and it is the rule the whole file
follows: 「仅通过 WLAN 下载」, 「GBFS 格式」, 「ODbL 许可」, 「版本 %1$s」,
「第 %1$d 步，共 %2$d 步」.

**A placeholder follows the same rule, by what it holds.** A city, a station, an
address, a size, a version or a count begins with Latin or with a figure and
takes the space — 「%1$s 附近」, 「删除 %1$s 的数据」, 「起点站有 %1$s」. A
fragment this file wrote itself is Chinese and takes none — 「设置%1$s」 where
`%1$s` is 家, 「已安装%1$s」 where it is 地图数据, 「%1$s更新」 where it is
5 分钟前. Android draws the line in exactly that place: 「%1$d 个应用」 with a
space against 「移除%1$s」 without one. Where the neighbour is full-width
punctuation or a quote, the punctuation carries the space and none is written:
「界面语言：%1$s」, 「“%1$s”在覆盖范围内没有匹配的地址。」.

**The space between a figure and the unit or classifier naming it is
non-breaking** (U+00A0), here as in every language in the project.
`UnitTypographyTest` holds the file to it, so that a summary never ends a line
on 「5」 and opens the next on 「m 爬升」. It applies to the twenty-four
resources that test names — the distances, the sizes, the durations, the
counts of bikes, of docking places and of stations — and to `1 小时 05 分`,
whose **every** space is glued, a clock reading being one fact and not two.

The separator the English writes `·` is **kept as `·`** (U+00B7), with the
ordinary spaces the source gives it: it is the interpunct a Chinese reader
already knows from foreign names, and it stays breakable, which is what leaves
a long row somewhere to wrap.

**No apostrophe is written anywhere in the file**, so nothing here is escaped.

## Plurals

Chinese has **one category, `other`**, and CLDR gives it no other. The started
file already carried the single item; what the translation had to get right is
the **classifier**, since one wording has to read for nought, one and a
thousand alike:

| what is counted | classifier | resource |
|---|---|---|
| a bike | 辆 | `bikes_available`, `bikes_mechanical`, `bikes_electric` |
| a docking place | 个 | `docks_available`, `docks_total`, `station_detail_with_capacity` |
| a station | 个 | `city_stations`, `city_detail`, `city_detail_size_unknown` |
| a degree of bearing | 度, bare | `map_bearing_description` |
| the age of the data | 秒 · 分钟 · 小时 · 天 · 个月 | `freshness_*` |

`counterpart_bikes` and `counterpart_docks` carry **no figure and no
classifier**: they are the label under or beside a count, set in capitals on a
list row, and the figure is already written next to them. 车辆 and 空位 stand on
their own there, which is what that resource needs.

The months take 个 — `%1$d 个月前` — where the days do not: `%1$d 天前`. Both
are Android's own, from `%dd ago` and `1 month ago` in the lexicon.

## The vocabulary

| English | Chinese | Why |
|---|---|---|
| the network | 该系统 / 共享单车系统 | The bike-share network. **Never 网络**, which is the data network on a screen that also says 无网络连接 — the same trap Japanese has with ネットワーク. 该系统 carries it in running prose, where the context is set by the screen; `settings_city_description` writes 所服务的系统 in full. |
| the operator | 运营方 | `error_untrusted_server`: the party that renews the certificate, named apart from the system itself. |
| shared bikes (the idea) | 共享单车 | The words everybody in the mainland has for this, used where the application introduces itself — `welcome_hello_body`. The formal name of a docked system, 公共自行车, is not what a reader looks for. |
| station | 站点 | A bike-share station, and what mainland public-bike operators and the two dominant map applications both call one. **Not 车站**, which is a railway or bus station — the very thing `address_search_prompt_message` means when it says "stations", and which is written 车站 there. |
| dock (free) | 空位 | What a bike is returned into, counted as available: 「12 个空位」. |
| dock (capacity) | 车位 | The same object counted as a total, which is a different figure on the same row: 「%1$s · 30 个车位」. English says "dock" for both; Chinese does not have to. |
| dock | *never* 桩 alone | 车桩 and 空桩 are what an operator's own signage says, and they are read by somebody who already uses that network. 空位 and 车位 are read by everybody. |
| bike | 自行车, counted 辆 | — |
| mechanical bike | 普通自行车, short 普通车 | The kind one pedals oneself, named against the assisted one. The short form is what the toggles and the two-button choosers carry — `map_bikes_mechanical`, `journey_bike_kind_mechanical`, `settings_own_bike_kind_mechanical` — and 车 is kept on the end of it: 普通 alone under a figure is an adjective with nothing to lean on. |
| electric bike | 电动助力自行车, short 助力车 | Pedal-assist, which is what the network lends. **Not 电动车 and not 电单车**: both name a throttle machine in mainland usage, which is a different vehicle and a different licence. 助力 says the pedals are still turned. |
| bikes / free docks (the map toggle) | 可借车辆 / 可还空位 | 可借 and 可还 are the pair a mainland public-bike rider reads on every screen of the service itself, and the two labels come out the same width, which a two-button toggle wants. |
| availability | 可借情况 | What the feed publishes and the network request fetches. `station_availability_unknown` is 可用情况未知, the wider word, because a station's row may be counting docks rather than bikes. |
| journey | 路线 | The whole door-to-door thing: the screen, the settings section, the button, `journey_title` through to `journey_detail_title`. It is what the dominant Chinese map applications call a planned itinerary, and 规划路线 is what they write on the button that computes one. |
| ride | 骑行 | The bike leg alone, inside a journey. Kept away from 路线 so that `journey_summary` can say 「其中步行 12 分钟，骑行 8 分钟」 and name two different things on one line. |
| route (the ground) | 道路 | Only in `journey_no_route`: 「这两点之间没有可通行的道路。」 says the ground has no path, which is what happened — not that the planner declined. |
| walk | 步行 | `journey_step_to_station` is 步行至 %1$s, `journey_step_ride` 骑行至 %1$s: the noun-plus-至 form Chinese transit applications use for the legs of a trip, and shorter than any verb phrase. |
| climb | 爬升 | Metres gained. The word the Chinese cycling and hiking applications write, and the one `journey_climb` and `journey_detail_profile_description` share. |
| pace (walking) | 步速 | A pace is not a speed: `values/strings.xml` says so above the string, and 速度 would say the opposite. The three settings are 慢 / 正常 / 快 — `settings:speed_label_slow` is 慢 and `speed_label_fast` 快, and "Brisk" lands on 快 rather than on 较快, which would read as a comparison with nothing to compare to. |
| Out of service | 暂停服务 | A **departure from the lexicon**, which gives 不在服务区 (`settings:radioInfo_service_out`). That is Android's phrase for a radio blackspot and says nothing about a rack; a station its operator has taken out is 暂停服务. |
| Settings | 设置 | Android's own word, `android:global_action_settings`. |
| Search | 搜索 | Android's own word for the action and the field, `android:search_go`. |
| Searching… | 正在搜索… | `settings:progress_scanning`. The 正在… form, which Android uses for every wait it narrates — 正在加载, 正在读取 — reads as an account of what is happening rather than as a badge. |
| Clear (a field) | 清除 | `settings:clear`. Emptying a field, which is what clearing a search does. Every one of them is 清除搜索内容. |
| Refresh | 刷新 | Not in the lexicon as a standalone label, but it is what `settings:auto_sync_account_summary` writes for refreshing data (自动刷新数据) and what every Chinese application puts on a pull-to-refresh. |
| Try again | 重试 | Android's own word, `settings:retry`. |
| Continue | 继续 | Android's own word, `settings:lockpattern_continue_button_text`. Used on the welcome pages and on the what's-new screen alike, where Japanese needed two words. |
| Skip | 跳过 | Android's own word, `settings:skip_label`. |
| Back | 返回 | Android's own word, `settings:back`. |
| Cancel | 取消 | Android's own word, `android:cancel`. |
| Tap | 点按 | Android's own verb in Simplified — 「点按即可关闭 USB 调试」 — and not 点击, which is a mouse. |
| Press and hold | 长按 | Android's own wording, from 长按延迟. |
| Delete | 删除 | Destroys: a dataset, a city's data. `android:delete`. |
| Remove (from favourites) | 移除 | Takes out of a list. `settings:contextual_card_dismiss_remove`. 从收藏中移除 stands beside 添加到收藏, which is what every Chinese application writes. |
| Favourites | 收藏 | The word Chinese applications use for a saved list, and the one the lexicon uses for a collection (音乐收藏). |
| Replace | 替换 | `settings:vpn_replace`. |
| Import | 导入 | Not in the lexicon — the Settings application never imports a file — but it is the settled counterpart of 导出 everywhere else. |
| In use | 正在使用 | `android:media_route_status_in_use`. |
| Not set | 未设置 | `settings:apn_not_set`, and androidx's own `not_set`. Exactly what `settings_place_none` means. |
| Storage | 存储空间 | `settings:storage_settings`. Two characters longer than 存储, and it is the row the reader will look for in their own settings. |
| Display (settings section) | 显示 | `settings:display_category_title`. Not 显示屏, which is the hardware. |
| Theme | 主题 / 浅色 / 深色 | 深色主题 is Android's own (`settings:dark_theme_main_switch_title`); 浅色 is its counterpart in every Chinese three-way picker. |
| System (theme, units, language) | 跟随系统 | A **departure from the lexicon**, which gives 系统 and 系统默认设置. 系统 alone on a button beside 浅色 and 深色 reads as a fourth kind of theme; 跟随系统 says what the choice does, is what every Chinese application writes in this exact picker, and at four characters still leaves the widest of the four unit buttons room to spare. The same word heads the language list, as the English writes one word in all three places. |
| Language | 语言 | `settings:app_locale_preference_title`. |
| Version | 版本 | `settings:vpn_version`. |
| Privacy | 隐私 | `settings:privacy_dashboard_title`. |
| Licence | 许可协议 | `settings:license_title` gives 许可, which names the permission; the section heading names the document, and 许可协议 is the document. |
| Wi-Fi | **WLAN** | Simplified Android writes WLAN wherever the English says Wi-Fi: `settings:wifi` is 「WLAN」, `android:wfc_mode_wifi_only_summary` is 「仅限 WLAN」, and the extracted lexicon holds 138 WLAN against 2 Wi-Fi — both of the latter inside search-keyword lists that deliberately spell every variant. The switch has to be the word the reader will look for in their own settings, so it is WLAN here too. |
| metered / unmetered | 按流量计费 / 不按流量计费 | Android's own words, `settings:wifi_metered_label` and `wifi_unmetered_label`, from the Wi-Fi settings. The switch itself names WLAN — 「仅通过 WLAN 下载」, as the English now names Wi-Fi — and 按流量计费 stays in the sentences that explain the billing, where it is the point. **可能 renders "may be billed"**: the phone reads only that the connection declares itself metered, never what the plan behind it charges, and a flat 「按流量计费」 would state as fact what only the operator knows. |
| mobile network | 移动网络 | `settings:network_settings_title`. |
| location | 位置信息 | `android:permgrouplab_location`, and what the two refusal messages say is missing. 定位 is the act — `map_locate_me` is 定位到我的位置, `city_locate_me` 定位我所在的城市. |
| bytes | B, kB, MB, GB | Left as they are: Chinese writes the symbols in Latin. |
| offline data | 离线数据 | — |
| map data / tiles | 地图数据 | The name the storage screen gives the dataset, and the one every other string must use for it. `map_needs_tiles_title` says 离线瓦片 once, which is the file and not the dataset. |
| routing data | 路线数据 | Built on 路线, so that `journey_graph_missing` names the thing the journey screen is about. |
| address index | 地址索引 | — |
| the area a city covers | 覆盖的范围 | One notion, one wording, over the eight strings that carry it — `station_beyond_area`, `map_outside_city_message`, `journey_outside_coverage`, `incoming_outside_coverage` and their neighbours. |
| Home (the place) | 家 | `settings_place_home`, `journey_source_home`, the same word in both. It is what the dominant Chinese map applications label the same row, and what a Chinese reader will read as a dwelling. **Not 主页**, which is an application's opening screen. |
| Work (the place) | 公司 | `settings_place_work`, `journey_source_work`, and `android:orgTypeWork`. The workplace as a point on a map, which is what map applications write beside 家. 工作 would be the activity, which is not a place. |
| My places | 我的地点 | `settings_section_places`, the settings section that holds the two. |
| outside the city served | 在所服务的城市之外 | `settings_place_outside_city`, written under a place the conurbation in use does not cover. The application serves several conurbations side by side: a 家 named in Lille is out of reach while Ljubljana is in use. The place is kept and stays erasable. |
| this place | 这个地点 | `place_sheet_title`, the sheet a point found on the map opens. `place_clear` is 清除这个点, keeping 清除 for emptying against 删除 for destroying. |
| Go there / Leave from here | 去这里 / 从这里出发 | Word for word what the station sheet says: `station_as_destination` is 去这里 and `station_as_origin` 从这里出发. The place sheet is the station sheet's sister and the two must read as one, so neither pair is reworded without the other. |
| Face north and lay the map flat | 正北朝上并放平地图 | `map_face_north`, on the compass, which shows while the map is turned or tilted, and one press gives back both at once — the north and the flat. 正北朝上 is how a Chinese map application says what the button does; 转向北 would name only the turn. |
| turn / tilt | 偏离正北 %1$d 度 / 已倾斜 | The turn is measured, the tilt is only named — a degree of tilt compares to nothing. 倾斜 is kept for the tilt and 转动 for the turn, so that the four gestures of `map_description` stay four: 平移、缩放、转动、倾斜. |
| What's new | 新变化 | What Google Play writes in Simplified for the same heading, so a reader arrives at it having read it before. |

## Two strings that are read against a rule rather than translated

**`settings_map_filters_hide_empty`** is 「隐藏可用数为零的站点」. The English is
deliberately vague — "with nothing to offer" — because this screen knows nothing
of the map's own Bikes / Free docks button and may name neither count. Chinese
has no comparable vagueness that stays grammatical, so a third noun was coined
for the pair: **可用数**, the count in question, whichever it is. The hint under
the switch then says what that count is read against, and quotes the word back:
「“可用数”随地图当前统计的内容而定：统计车辆时是没有车可借，统计空位时是没有位可还。」

**`settings_map_filters_keeps_unknown`** keeps the English's turn of phrase
whole — 「谁都读不到的数量，不等于数量为零」 — because that sentence is the
most important rule of the two filters and the only place it is written.

## The search prompt puts the town last

`address_search_hint` is 「街道、门牌号、城市」, and not the source's
"Number, street, town".

SPEC §4.3 lets every translation write this prompt in the order of its own
language, and names the three orders the query parser actually reads:
"12 rue Nationale", "rue Nationale 12", "Gran Vía 12 Madrid". **None of them
begins with the town.** A full Chinese address runs largest to smallest —
城市, then the road, then the number — so writing the prompt in Chinese order
would invite a query the parser cannot take apart.

What Chinese order does settle is the half the parser agrees with: within a
street, Chinese puts the **number after the road**, 南京东路 123 号, which is
the second and third of the three orders above. So the number moves behind the
street, the town stays at the end, and the prompt describes something that will
be understood. `address_search_prompt_message` says the number is optional,
and `address_no_match_message` sends the reader back to the same two things
the hint named — 街道名称 and 城市.

The number itself is kept, unlike in Japanese: the networks served are cities
where an address is built on the street, and SPEC §4.3 measures a median of
16.4 house numbers per street over the catalogue.

## Length

Chinese is the shortest of the thirty translations, and no control gained
width against the English. Measured in half-width columns:

| resource | Chinese | English |
|---|---|---|
| `settings_map_filters_hide_empty` | 20 | 39 |
| `map_face_north` | 18 | 31 |
| `settings_opening_title` | 18 | 26 |
| `settings_place_outside_city` | 18 | 23 |
| `download_anyway` | 8 | 15 |
| `map_bikes_mechanical` | 6 | 10 |

The one label that is wider is `mode_bikes`, 可借车辆 at 8 against "Bikes" at 5
— and it is paired with `mode_docks`, 可还空位, at the same 8, which is what a
two-button toggle needs. The three theme buttons and the four unit buttons share
their row by weight, so the widest label decides: 跟随系统, at four characters,
leaves the narrowest of them room to spare.

The longest string in the file is `welcome_data_body`, 130 characters, which is
a welcome page and scrolls.

## Words that are not translated

Product and network names — Roue Libre, BRouter, MapLibre, MapLibre Native,
OpenStreetMap, GeoNames, Base Adresse Nationale, GBFS — the licence names
(GNU GPL, ODbL, MIT, BSD, CC BY 4.0), and the unit symbols (m, km, ft, yd, mi,
B, kB, MB, GB), which the units button itself shows in Latin; 米 and 千米 are
written out only in the descriptions beside it. WLAN is Latin because
Simplified Android writes it that way.

The map's own attribution, `© OpenStreetMap contributors`, is the wording
OpenStreetMap asks for and stays in English — it is the one string
`tools/check_translations.py` reports as identical, and it is right to.

The strings that are nothing but a shell around their placeholders keep the
shell: `station_bikes_split`, `station_capacity_and_age`, `address_detail`,
`dataset_installed`, `address_locality`, `city_label`, `incoming_address_choice`,
`counterpart_none`. And `resources` `name` attributes, always.

**The order of an address is not a matter for this file.** The addresses shown
are Lyon's and Riga's, written the way their street signs are, and reversing
them into Chinese order would misquote the sign. That is the rule for every
language (SPEC §4.3) — the layout comes from a table in
`core/address/AddressLayout.kt`, keyed on the language of the **address
base** — and the digits a number is written in follow the reader's locale,
which for a Chinese reader is the Western ones.
