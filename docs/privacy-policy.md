# Privacy policy

*Roue Libre — `io.github.mgdx.rouelibre`. In force since 14 September 2026.*

Roue Libre collects nothing about you. There is no account, no advertisement,
no analytics, no crash reporting, no unique identifier, and no third-party
software development kit that could carry one. Nothing is sent to the author of
the application, who therefore holds no data about anybody using it, and could
hand none over if asked.

This document says what happens instead, because "we collect nothing" is easy
to write and worth nothing unless one can check it. The application is free
software under the GNU GPL v3: its
[source code](https://github.com/mgdx/RoueLibre) is the reference, and every
statement below is checkable against it.

## What stays on your device

Four things are written to the device's own storage, readable by no other
application, and copied nowhere:

- **Your settings**: the city in use, the language, the units, the journey
  preferences.
- **Your favourite stations**, as station identifiers.
- **The two places you may name for yourself** — a home and a work — which you
  type on the settings screen and erase from that same screen.
- **The datasets of the cities you have installed**: the map, the routing graph
  and the address index. These are public files, the same for everybody.

Removing the application removes all of it. A single city's data can be deleted
from the storage screen without touching the rest.

## What is never written down at all

What the application observes of you, as opposed to what you deliberately type
in, reaches no storage of any kind:

- the journeys you compute, and the places they run between;
- the addresses you search for, including as you type them;
- your position, and every position you pass through;
- the stations you open, and the times of day you do it.

They live in the memory of the running application and are gone when it closes.
There is no history screen because there is no history.

## Your position

The location permissions are asked for by the map alone — when it opens, and
when you press "locate me". Refusing them blocks nothing: you then designate
your points by hand.

Your position is read from the system's own location provider, never from
Google's services, which this application does not use at all. It is drawn on
the screen and given to the journey computation running on the device. It is
not stored, and it is not sent anywhere — no request this application makes
carries it.

## What goes out on the network

Three requests, and no others:

1. **Real-time bike availability**, to the GBFS feed of the network of the city
   you have installed — run by the bike-share operator or its city, not by this
   project. Like any request to any website, it shows that operator's server
   your IP address and the fact that a request was made. It carries no
   identifier, no position and no name of yours.
2. **The catalogue of cities**, from
   [GitHub](https://github.com/mgdx/RoueLibre-data/releases), so that the list
   of networks stays current.
3. **The datasets of a city**, from the same place, and only when you ask for
   them from the storage screen.

The map is drawn from the files on the device: no tile is fetched while you
pan, and no tile server ever learns where you are looking. Address search runs
against the index on the device: nothing is sent while you type, and nothing is
sent when you press enter.

## Backups

Automatic backup to a cloud is turned off, and so is device-to-device transfer:
nothing of this application is copied to a Google account, to a manufacturer's
account, or to a new telephone. If you change phone, you install the
application again and download the cities you want.

## Permissions, and what each is for

| Permission | What it is used for |
| --- | --- |
| `INTERNET` | The three requests above, and nothing else. |
| `ACCESS_NETWORK_STATE` | To tell "no connection" from "the server did not answer". |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | To show your position on the map and to offer to start a journey from it. Optional: the application works without them. |

There is no permission for contacts, photos, storage, the microphone, the
camera, or the advertising identifier. The application runs on a device with no
Google services at all.

## Children

The application is meant for a general audience. It collects nothing from
anybody, whatever their age.

## Changes to this policy

Any change is made in this file, whose whole history is public in the
repository. A change that made the application collect something would be a
change to the project's founding constraints, and would be announced in the
release notes rather than slipped into a document.

## Getting in touch

Questions and reports go to the
[issue tracker](https://github.com/mgdx/RoueLibre/issues), which is public.
