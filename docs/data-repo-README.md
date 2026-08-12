# Roue Libre — offline datasets

This repository holds nothing but data: the files the
[Roue Libre](https://github.com/mgdx/RoueLibre) application downloads on first
launch, one set per conurbation.

Three files per city, described by a manifest:

| File | What it is |
|---|---|
| `<network>-tiles.mbtiles` | the vector base map, zooms 10 to 16 |
| `<network>-<tile>.rd5` | the BRouter routing graph |
| `<network>-addresses.sqlite` | the address index: streets, house numbers, landmarks |
| `manifest-<network>.json` | their sizes, their SHA-256 digests and their addresses |
| `catalogue.json` | the list of served conurbations, which the application reads to offer them |

**Why a repository of its own.** The application asks for
`releases/latest/download/manifest-<network>.json`, and `latest` names the
newest release of a whole repository. Published beside the application's own
releases, one of those would take that name and every city would stop finding
its manifest — silently, since the URL stays well-formed. Here, `latest` can
only ever mean a release of data.

## Installing without this repository

Nothing obliges you to go through it. The application accepts another manifest
address in its settings, and imports a local file from its storage screen. The
datasets are reproduced from the sources by the scripts of the main
repository — `tools/generate_all.sh --city config/cities/<city>.json` — which
is the point: no host is a single point of failure.

## Sources and licences

The data here is derived, not original. It carries the licences of what it was
made from, and using it means carrying them further.

| Source | Used for | Licence |
|---|---|---|
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | base map, routing graph, landmarks, and the addresses of every country outside France | ODbL 1.0 — © OpenStreetMap contributors |
| [Base Adresse Nationale](https://adresse.data.gouv.fr/) | house numbers in France | ODbL 1.0 |
| [SRTM 1″](https://registry.opendata.aws/terrain-tiles/) | the routing graph's elevation | public domain |
| The networks' GBFS feeds | the reference box each set is cut to | see each city's configuration |

The station availability is **not** here: the application reads it live from
each network's feed.

The generation scripts are GPLv3, in the main repository.
