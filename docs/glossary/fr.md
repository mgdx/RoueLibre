# French glossary

The terms `res/values-fr/strings.xml` holds to, and the reason each one was
picked. It exists so that the same English word does not come out as three
different French ones over three screens — which is what had happened to
"journey", "settings" and "tap" before this list was written.

An entry is not changed without going back over every one of its occurrences.

## Register and typography

The French says **tu** (SPEC §9), in the interface and in the store texts alike.
Prefer the turn of phrase that does not have to pick a gender — "Bienvenue"
rather than "Tu es connecté·e"; the interpunct is read out loud by a screen
reader and is not a default.

The apostrophe is **’** (U+2019), never the straight quote, and therefore never
escaped. `?`, `!`, `;`, `:` and the inside of `« »` carry a **non-breaking
space** (U+00A0), so the mark never starts a line on its own.

## The vocabulary

| English | French | Why |
|---|---|---|
| journey | itinéraire | The whole door-to-door thing: the screen, the settings section, the button. It was "trajet" on half the screens and "itinéraire" on the other half. |
| ride | trajet à vélo | The bike leg alone, inside a journey. Distinct from the journey, which is why "trajet" is left free for it. |
| route | chemin | Only in "no practicable route": the line on the ground, not the planned journey. |
| station | station | A bike-share station. A railway station is a *gare* — which is what `address_search_prompt_message` means by "stations". |
| dock (free) | place libre, place | What one returns a bike into, counted as available. |
| dock (capacity) | point d’attache | The same object counted as a total, which is a different figure on the screen: "12 places libres · 30 points d’attache". English says "dock" for both; French does not have to. |
| dock | *never* « borne » | A *borne* is the terminal one pays at, not the point a bike attaches to. |
| Settings | Paramètres | Android's own word. The app already wrote "Paramètres" when it quoted the system path in `about_links_body`, and "Réglages" for its own screen. |
| Search | Rechercher | Android's own word for the action and the field. "Chercher" stays in running prose ("Tout se cherche sur l’appareil"). |
| Refresh | Actualiser | Android's own word for data. "Rafraîchir" is not used in interfaces. |
| Back | Retour | Android's own word, on the toolbar's back arrow and on the button that leaves a picker. |
| Tap | Appuie (sur) | Android's own verb, in the second person like the rest. |
| Press and hold | Appuie longuement | Android's own wording for a long press. |
| app | application | Never « appli ». |
| pace (walking) | allure | A pace is not a speed: `values/strings.xml` says so above the string, and "Vitesse de marche" said the opposite. |
| Delete / Remove | Supprimer / Retirer | Supprimer destroys, Retirer takes out of a list (favourites). |
| Clear | Effacer | Emptying a field, which is what "clear the search" does. |
| Add to favourites | Ajouter aux favoris | Pairs with "Retirer des favoris". |
| bytes | o, ko, Mo, Go | The French unit is the *octet*. |
| offline data | données hors ligne | — |
| map data / tiles | fond de carte | The name the storage screen gives the dataset, and the one every other string must use for it. |
| routing data | graphe de routage | The project's own term, used in the store text and the documentation too. More technical than the English "routing data", and kept deliberately. |
| address index | index d’adresses | — |

## Words that are not translated

Product and network names — Roue Libre, Vélib’, Citi Bike, BRouter, MapLibre,
OpenStreetMap, GBFS, Wi-Fi — and the licence names. `resources` `name`
attributes, always.
