Offline datasets generated on 12 August 2026, for **101 conurbations in 10
countries**: the seventy French networks and thirty-one others.

Each city carries three files and a manifest holding their SHA-256 digests.
The application picks them up on its own — the manifest address is in its
settings, and nothing here needs to be downloaded by hand.

| | |
|---|---|
| Total | 1.46 GB, 442 files |
| Median per city | 10.2 MB, all three sets together |
| Heaviest | Vélib' Métropole, Paris — 143.0 MB |
| Lightest | nextbike, Moravská Třebová — 2.0 MB |

**Sources.** OpenStreetMap (ODbL) for the base map, the routing graph, the
landmarks and, outside France, the addresses; the Base Adresse Nationale
(ODbL) for French house numbers; SRTM for the elevation the graph uses. Station
availability is not here: the application reads it live from each network's
feed.

**Reproducing them.** `tools/generate_all.sh --city config/cities/<city>.json`
in the [main repository](https://github.com/mgdx/RoueLibre) rebuilds any of
these files from the public sources. This host is a convenience, not a
dependency.
