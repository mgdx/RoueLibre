# Japanese glossary

The terms `res/values-ja/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different Japanese ones over three screens, and so that a contributor can
correct one word later without unpicking the whole file.

An entry is not changed without going back over every one of its occurrences.

## Register and typography

The register is the plain **です・ます** Android's own Settings write, with no
commercial keigo — no ご〜ください piled on, no お客様. SPEC §9 asks the French
for *tu* because the application speaks to one person walking to a station
rather than to a customer; Japanese has no *tu* to ask for, and the same
intention lands as a neutral polite register instead. The system lexicon
extracted from `framework-res.apk` and `Settings.apk` is the arbiter.

There are **no spaces between words**, and the punctuation is full-width:
`。`, `、`, `「」`, `（）`. Those marks already carry the whitespace a Latin
mark would need beside them, so nothing is padded around them. Figures, unit
symbols and Latin names stay **half-width** — `12 km`, `Wi-Fi`, `GBFS`,
`OpenStreetMap`, `Roue Libre` — and a **half-width space sits on both sides of
every Latin run**: `Wi-Fi に接続`, `GBFS 形式`, `ODbL ライセンス`,
`Android の選択画面`, `バージョン %1$d です`. Android spaces them in 755 of the
944 rows of the lexicon, and a run set tight against kana loses its edges.

The separator the English writes `·` is **`・`** (U+30FB), set tight:
`空きラック12台・ラック30台`.

No apostrophe is written anywhere in the file, so **nothing is escaped**.

Japanese has **one plural category, `other`**, and CLDR gives it no other. A
noun does not agree in number, so the single item reads correctly for any
count — but only if the **counter** is right, and that is where the work went:
`台` for bikes and for racks, `分` and `時間` for durations, `件` for stations
in a list, `秒` for the age of the data. A count written beside a kanji unit carries no
leading zero: 「1時間5分」, where 「1時間05分」 is a clock reading.

A question keeps its mark, and the mark is the **full-width `？`** —
`どこへ行きますか？`, `このデータを削除しますか？`. That is what Android's own
dialogs write (`settings:storage_wizard_migrate_v2_title`), and it is followed
by no space, the glyph carrying its own.

## The vocabulary

| English | Japanese | Why |
|---|---|---|
| journey | 経路 | The whole door-to-door thing: the screen, the settings section, the button. It is what Japanese transit apps call a planned itinerary (`経路検索`), and it is the one word used from `journey_title` through to `journey_no_route`. |
| ride | 走行 | The bike leg alone, inside a journey. Kept away from 経路 so that `journey_summary` can say `うち徒歩12分、走行8分` and name two different things on one line. |
| route | 道 | Only in `journey_no_route`: the line on the ground, not the planned journey. 「この2地点を結ぶ通行可能な道がありません。」 says the ground has no path, which is what happened. |
| a journey step | 徒歩 / 自転車 | `%1$sまで徒歩`, `%1$sまで自転車` — the noun style Japanese transit apps use for the legs of a trip, and shorter than any verb. |
| station | ステーション | A bike-share station. Japanese operators are split — ドコモ・バイクシェア says ポート, HELLO CYCLING says ステーション — and ステーション is the one that cannot be misread. |
| railway station | 駅 | What `address_search_prompt_message` means by "stations", and the reason the bike-share one may not be 駅. |
| dock (free) | 空きラック | What a bike is returned into, counted as available: `空きラック12台`. |
| dock (capacity) | ラック | The same object counted as a total, which is a different figure on the same row: `空きラック12台・ラック30台`. English says "dock" for both; Japanese does not have to. |
| dock | *never* 端末 or 精算機 | Those are the payment terminal, not the point a bike attaches to. |
| bike | 自転車, counted 台 | — |
| mechanical bike | 一般自転車 | The kind one pedals oneself, named against 電動アシスト. Not ノーマル, which is not what a Japanese operator writes. This is the form the toggles and the two-button choosers carry — `map_bikes_mechanical`, `journey_bike_kind_mechanical`, `settings_own_bike_kind_mechanical`. |
| electric bike | 電動アシスト自転車 (toggles: 電動アシスト) | Pedal-assist, which is what 電動アシスト means in Japanese and what the network lends. 電動自転車 alone would suggest a moped. The toggles carry the short form, where 電動アシスト自転車 would not fit. |
| the two counts side by side | 一般車 / 電動アシスト車 | `bikes_mechanical` and `bikes_electric` are read apart from any label — `journey_bikes_at_departure` hangs them off the end of a summary — so each has to be a noun that stands alone. 一般3台 is not one; 一般車3台 is. |
| network | サービス | The bike-share network. ネットワーク would be read as a data network on a screen that also says 接続がありません, and 事業者 would collide with "operator" — which the application names separately, as 運営者, in `error_untrusted_server`. |
| pace (walking) | ペース | A pace is not a speed: `values/strings.xml` says so above the string, and 速度 would say the opposite. |
| Slow (pace) | ゆっくり | A **departure from the lexicon**, which gives 遅い for Android's "Slow". 遅い judges — it is what a slow charger is — where ゆっくり describes how somebody walks. 速め rather than 速い for the same reason. |
| Out of service | 利用停止中 | A **departure from the lexicon**, which gives 圏外. 圏外 is Android's word for a radio blackspot and says nothing about a rack; a station taken out of service by its operator is 利用停止中. |
| Settings | 設定 | Android's own word. |
| Search | 検索 | Android's own word for the action and the field. |
| Searching… | 検索しています… | A **departure from the lexicon**, which has 検索中 (`settings:progress_scanning`). This string titles an otherwise empty screen, where the sentence form Android uses for its own waits — 検索しています…, デバイスを検索しています… (`android:media_route_chooser_searching`) — reads as an account of what is happening rather than as a status badge. |
| Refresh | 更新 | Android's own word for data. |
| Update (a dataset) | アップデート | Android writes アップデートを利用できます for a pending update, and 更新 is already carrying "refresh". |
| Back | 戻る | Android's own word. |
| Continue | 次へ / 続行 | 次へ on the welcome pages, which are a numbered sequence and are what Android's own wizards call 次へ; 続行 on the "what's new" screen, which advances nothing and is Android's other reading of the same English word. |
| Skip | スキップ | Android's own word. |
| Cancel | キャンセル | Android's own word. |
| Try again | 再試行 | Android's own word on a button. In running prose the sentence ends もう一度お試しください, which is the lexicon's other reading and the one that fits a sentence. |
| Tap | タップ | Android's own verb. |
| Press and hold | 長押し | Android's own wording for a long press. |
| Delete | 削除 | Destroys: a dataset, a city's data. |
| Remove (from favourites) | 外す | Takes out of a list. Android maps both English words onto 削除, and the application distinguishes them — `お気に入りから外す` beside `お気に入りに追加`, which is what every Japanese application writes. |
| Clear (a field) | 消去 | Android's own word, `settings:clear`. Emptying a field, which is what "clear the search" does. |
| Replace | 置き換え | A **departure from the lexicon**, which gives 置換. 置換 is find-and-replace in a text; a dataset is 置き換え. |
| In use | 使用中 | Android's own word. |
| Storage | ストレージ | Android's own word. |
| Display (settings section) | 表示 | Android's own word for the settings category, `settings:display_category_title`. |
| System (theme, units, language) | システム | The English writes one word in all three settings, and so does this. Not システムのデフォルト: the buttons beside it are ライト and ダーク, and one long label among short ones reads as a different kind of choice. |
| Theme | テーマ / ライト / ダーク | Android's own word for the setting; the two states are what Japanese applications write in a three-way theme picker. |
| Language | 言語 | Android's own word. |
| the area a city covers | 対象とする範囲 / 対象とする | One notion, one word, over the nine strings that carry it. カバー appears nowhere: it would have made a third dressing of the same idea beside 対象範囲 and 対象とする範囲. |
| offline data | オフラインデータ | — |
| map data / tiles | 地図データ | The name the storage screen gives the dataset, and the one every other string must use for it. |
| routing data | 経路データ | Built on 経路, so that `journey_graph_missing` names the thing the journey screen is about. More legible than ルーティングデータ. |
| address index | 住所インデックス | — |
| metered / unmetered | 従量制 / 定額制 | Android's own words for the two, from the Wi-Fi settings. The setting names the billing rather than Wi-Fi, as the English does. |
| bytes | B, kB, MB, GB | Left as they are: Japanese writes the symbols in Latin. |

## The search prompt names no house number

`address_search_hint` is 「市区町村、通りの名前」, and not the source's
"Number, street, town". Two things decide it, and only the second is about
Japanese word order.

SPEC §4.3 lets every translation write this prompt in the order of its own
language: the query parser reads a house number in the three orders that are
written. Japanese runs largest to smallest — 市区町村, then the road — so the
English order is reversed rather than transposed.

The number is then dropped altogether, which is the substantive decision. Where
an address is not built on the street, as in Japan, `addr:housenumber` is all
but absent; SPEC §4.3 measures Tokyo, the largest network in the catalogue, at
0.01 numbers per street. And a Japanese 番地 is not a sparse version of a French
one — it belongs to a 街区 inside a 町, not to a road — so naming it in the
field would promise a kind of precision the index does not hold there in any
form. `address_search_prompt_message`, on the empty screen, still says a number
is accepted 「番地の有無にかかわらず」, which permits one where the city has
them without promising one where it does not; `address_no_match_message` sends
the reader back to the same two things the hint named, 通りの名前 and 市区町村.

## Words that are not translated

Product and network names — Roue Libre, Vélib', Citi Bike, BRouter, MapLibre,
OpenStreetMap, GBFS, Wi-Fi — and the licence names. The map's own attribution,
`© OpenStreetMap contributors`, is the wording OpenStreetMap asks for and stays
in English. `resources` `name` attributes, always.

`address_with_number` keeps the source's order, number then street. The
addresses it formats are Lyon's and Riga's, written the way their street signs
are; reversing them into the Japanese order would misquote the sign.
