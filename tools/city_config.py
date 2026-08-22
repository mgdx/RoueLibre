"""Reading and updating of the city configuration file (SPEC.md §15).

The city configuration is the single source of every setting specific to one
agglomeration: network name, GBFS discovery URL, geographic bounding box,
default map centre, data release URLs. Both the Android application and these
generation scripts read the very same file, which is what makes porting the
project to another city a matter of configuration rather than of code.

Keys prefixed with ``$comment`` are documentation embedded in the JSON file.
They are preserved on write and ignored on read.
"""

from __future__ import annotations

import json
import math
import statistics
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CITY_CONFIG = REPO_ROOT / "config" / "cities" / "lille.json"

# What the "fleet" block says about itself, written by whichever script fills
# it — the survey when a city is added, tools/read_fleet.py when an existing
# one is refreshed. One text, so the two cannot drift apart.
FLEET_COMMENT = [
    "What the network lends, counted from its own feeds (§4.1).",
    "Never written by hand, and never taken from the vehicle_types",
    "declaration alone: a third of the networks declaring a mixed fleet",
    "have not a single bike of one of the two kinds in circulation.",
    "tools/read_fleet.py therefore counts the bikes actually available",
    "at the stations, and writes what it saw.",
    "This block SEEDS the answer, it does not settle it: the",
    "application counts again from the live feeds on every refresh,",
    "and a reading only ever adds to what is written here. What this",
    "block is for is the launch that reaches no network -- the first",
    "one, and every one made with no connection.",
    "\"electricBikes\" marks every bike glyph drawn for this city with a",
    "bolt (§7); \"mixed\" is what allows the station sheet to split its",
    "count into mechanical and electric; \"vehicleTypes\" translates the",
    "identifiers the status feed counts by into the two kinds.",
    "A network whose feeds let nothing be counted keeps whatever its",
    "declaration says and is never called mixed: the application draws",
    "one plain figure rather than a split nobody verified.",
]

# One degree of latitude is very nearly this many metres everywhere on the
# ellipsoid; the variation is far below the precision this project needs.
METRES_PER_DEGREE_LATITUDE = 111_320.0

# The equator, in metres: what a whole map spans at zoom 0.
EARTH_CIRCUMFERENCE_METRES = 40_075_016.686

# MapLibre draws 512-pixel tiles, which is what puts its zoom about one step
# off the 256-tile convention every other tool counts in.
TILE_SIZE_PIXELS = 512

# The screen the opening framing is judged on: the shape of the phones this is
# tested on, and of most of what runs Android. It is read two ways, because the
# two questions asked of a framing are not the same one (§4).
REFERENCE_VIEWPORT_WIDTH_PIXELS = 1080
REFERENCE_VIEWPORT_HEIGHT_PIXELS = 2280

# Opening zoom. The three conurbations settled by hand open between 11.2 and
# 11.5 over boxes around 21 km wide; the rule below reproduces that and
# tightens the framing for a smaller network, so that a ten-station town does
# not open on a quarter of its department.
REFERENCE_ZOOM = 11.4
REFERENCE_WIDTH_KILOMETRES = 21.0
MINIMUM_ZOOM, MAXIMUM_ZOOM = 10.5, 14.0


def metres_per_pixel(zoom: float, latitude: float) -> float:
    """The ground distance one screen pixel covers at this zoom and latitude."""
    return EARTH_CIRCUMFERENCE_METRES * math.cos(math.radians(latitude)) / (
        TILE_SIZE_PIXELS * 2.0 ** zoom
    )


# How many station positions a configuration carries. Eight describes a network
# stretched over a region along its whole length — one station per town of the
# Grand Est, 261 km by 327 — for a hundred and sixty bytes, and the application
# measures on them how near a network is (§15.1).
STATION_SAMPLE_COUNT = 8

# Five decimals is a metre. A station's position is known to far less than
# that, and the figure decides a distance in kilometres.
SAMPLE_PRECISION = 5


def spread_through(items: Sequence, count: int = STATION_SAMPLE_COUNT) -> list:
    """Take ``count`` items at regular intervals through a list, in its order.

    At regular intervals rather than the first ones: a feed often lists a
    network district by district, and the first eight would describe one
    neighbourhood of a conurbation that spreads over sixty municipalities.
    """
    if not items:
        return []
    step = max(1, len(items) // count)
    return list(items[::step][:count])


def sample_positions(stations: list[dict], count: int = STATION_SAMPLE_COUNT) -> list[list[float]]:
    """Take positions spread through a station list, in feed order."""
    return [
        [round(station["lat"], SAMPLE_PRECISION), round(station["lon"], SAMPLE_PRECISION)]
        for station in spread_through(stations, count)
    ]


@dataclass(frozen=True)
class BoundingBox:
    """A geographic rectangle in WGS 84 decimal degrees."""

    south: float
    west: float
    north: float
    east: float

    def expanded_by_metres(self, margin_metres: float) -> "BoundingBox":
        """Return this box grown by ``margin_metres`` on all four sides.

        The longitude margin is computed at the latitude of the box centre.
        Over a box of this size the resulting east-west margin varies by less
        than a percent between its northern and southern edges, which is well
        inside the tolerance of a 3 km buffer.
        """
        centre_latitude = (self.south + self.north) / 2.0
        latitude_margin = margin_metres / METRES_PER_DEGREE_LATITUDE
        longitude_margin = margin_metres / (
            METRES_PER_DEGREE_LATITUDE * math.cos(math.radians(centre_latitude))
        )
        return BoundingBox(
            south=self.south - latitude_margin,
            west=self.west - longitude_margin,
            north=self.north + latitude_margin,
            east=self.east + longitude_margin,
        )

    @property
    def width_kilometres(self) -> float:
        centre_latitude = (self.south + self.north) / 2.0
        degrees = self.east - self.west
        return degrees * METRES_PER_DEGREE_LATITUDE * math.cos(
            math.radians(centre_latitude)
        ) / 1000.0

    @property
    def height_kilometres(self) -> float:
        return (self.north - self.south) * METRES_PER_DEGREE_LATITUDE / 1000.0

    @property
    def area_square_kilometres(self) -> float:
        return self.width_kilometres * self.height_kilometres

    def as_osmium_extract_argument(self) -> str:
        """Format as ``left,bottom,right,top``, the order osmium expects."""
        return f"{self.west},{self.south},{self.east},{self.north}"

    def contains(self, latitude: float, longitude: float) -> bool:
        return (
            self.south <= latitude <= self.north
            and self.west <= longitude <= self.east
        )

    def __str__(self) -> str:
        return (
            f"S {self.south:.6f}  O {self.west:.6f}  "
            f"N {self.north:.6f}  E {self.east:.6f}"
        )


def default_zoom(box: BoundingBox) -> float:
    """The opening zoom that frames a network of this width."""
    width = max(box.width_kilometres, 0.5)
    zoom = REFERENCE_ZOOM - math.log2(width / REFERENCE_WIDTH_KILOMETRES)
    return round(min(max(zoom, MINIMUM_ZOOM), MAXIMUM_ZOOM), 1)


def median_position(
    positions: Sequence[tuple[float, float]]
) -> tuple[float, float]:
    """The median latitude and the median longitude of a set of positions.

    The median and not the mean: a handful of stations at one end of a network
    pulls a mean out of the built-up area it should be framing, while the
    median stays where the stations are. Each coordinate is taken separately,
    which is enough for a group of stations that hangs together — the caller
    passes one cluster, not a network spread over a country, and the result
    frames it rather than naming a point of interest.

    Raises:
        ValueError: if no position is given.
    """
    if not positions:
        raise ValueError("No position to take a median of.")
    return (
        statistics.median(latitude for latitude, _ in positions),
        statistics.median(longitude for _, longitude in positions),
    )


@dataclass(frozen=True)
class OpeningView:
    """Where the map opens on a network, and how wide (SPEC.md §4).

    Not the middle of the reference box: that rectangle is drawn around
    everything the feed publishes, and its middle can be a place holding no
    station at all — Careem BIKE's box spanned Dubai and Medina, and its middle
    fell 769 km from the nearest bike, in the Saudi desert.
    """

    latitude: float
    longitude: float
    zoom: float

    @classmethod
    def from_stations(
        cls,
        positions: Sequence[tuple[float, float]],
        main_cluster_box: BoundingBox,
        margin_metres: float,
    ) -> "OpeningView":
        """Frame a network on its stations rather than on its rectangle.

        Both the centre and the zoom are read off the most populous cluster:
        that is where the stations the user came to see are, and a network
        holding one station per town of a region would otherwise open on the
        region and show nothing anywhere. The centre is the median of those
        positions and not their mean — the mean of a cluster stretched along a
        valley lands in the fields beside it, the median stays on the stations.

        Args:
            positions: the positions of the most populous cluster.
            main_cluster_box: the rectangle enclosing them.
            margin_metres: the margin §4 puts around the stations, applied here
                too so that the zoom of a single-cluster city is the very one
                the reference box would have given.

        Raises:
            ValueError: if no position is given.
        """
        latitude, longitude = median_position(positions)
        return cls(
            latitude=round(latitude, 6),
            longitude=round(longitude, 6),
            zoom=default_zoom(main_cluster_box.expanded_by_metres(margin_metres)),
        )

    def _degrees_of(self, metres: float) -> tuple[float, float]:
        """That distance in degrees of latitude, then of longitude, here."""
        return (
            metres / METRES_PER_DEGREE_LATITUDE,
            metres
            / (METRES_PER_DEGREE_LATITUDE * math.cos(math.radians(self.latitude))),
        )

    def shows_a_station(self, positions: Sequence[tuple[float, float]]) -> bool:
        """Whether at least one of these positions is on screen at the opening.

        This is the question the map asks when it opens with no fix available,
        and the one nothing asked until Dubai opened on an empty desert.

        The screen is read as a square of its narrow side, which under-states
        what a phone held upright really shows: a check that decides whether to
        move a map had better err towards calling the framing empty.
        """
        half_side = metres_per_pixel(self.zoom, self.latitude) * (
            REFERENCE_VIEWPORT_WIDTH_PIXELS / 2.0
        )
        half_latitude, half_longitude = self._degrees_of(half_side)
        return any(
            abs(latitude - self.latitude) <= half_latitude
            and abs(longitude - self.longitude) <= half_longitude
            for latitude, longitude in positions
        )

    def distance_to_nearest_metres(
        self, positions: Sequence[tuple[float, float]]
    ) -> float:
        """How far the nearest of these positions is from where the map opens.

        Infinite when there is none, so that two framings can be compared
        without the caller having to check first.
        """
        metres_per_degree_longitude = METRES_PER_DEGREE_LATITUDE * math.cos(
            math.radians(self.latitude)
        )
        return min(
            (
                math.hypot(
                    (latitude - self.latitude) * METRES_PER_DEGREE_LATITUDE,
                    (longitude - self.longitude) * metres_per_degree_longitude,
                )
                for latitude, longitude in positions
            ),
            default=math.inf,
        )

    def reaches_a_station(self, positions: Sequence[tuple[float, float]]) -> bool:
        """Whether one of these positions is within a screenful of the centre.

        The same screen read at its most generous — corner to centre — because
        this answers the question put by a witness too sparse for the strict
        one. A configuration records eight positions spread through its network
        (§15.1), and eight points spread through twelve thousand stations over
        three hundred kilometres of Switzerland can all be forty kilometres
        from a framing that is perfectly good. Anything this catches is a
        framing that has left its network altogether, which is what Dubai's
        had done, by a factor of twelve.
        """
        reach = (
            metres_per_pixel(self.zoom, self.latitude)
            * math.hypot(
                REFERENCE_VIEWPORT_WIDTH_PIXELS, REFERENCE_VIEWPORT_HEIGHT_PIXELS
            )
            / 2.0
        )
        return self.distance_to_nearest_metres(positions) <= reach


class CityConfig:
    """A loaded city configuration file, editable and writable back to disk."""

    def __init__(self, path: Path, document: dict) -> None:
        self.path = path
        self.document = document

    @classmethod
    def load(cls, path: Path = DEFAULT_CITY_CONFIG) -> "CityConfig":
        """Read a city configuration from disk.

        Raises:
            FileNotFoundError: if the configuration file does not exist.
            json.JSONDecodeError: if the file is not valid JSON.
        """
        with path.open(encoding="utf-8") as stream:
            return cls(path, json.load(stream))

    def save(self) -> None:
        """Write the configuration back, keeping the two-space indentation."""
        with self.path.open("w", encoding="utf-8") as stream:
            json.dump(self.document, stream, ensure_ascii=False, indent=2)
            stream.write("\n")

    @property
    def network_id(self) -> str:
        return self.document["network"]["id"]

    @property
    def gbfs_discovery_url(self) -> str:
        return self.document["gbfs"]["discoveryUrl"]

    @property
    def default_language(self) -> str:
        """The language of the conurbation's own address base (§15.1).

        Not the language of the interface, which follows the device: it is the
        language streets are named in here, and therefore the one whose
        normalisation rules the index has to be built with.
        """
        return self.document["network"].get("defaultLanguage", "en")

    @property
    def country(self) -> str:
        """ISO 3166-1 alpha-2 code of the country the network runs in."""
        return self.document.get("country", "")

    @property
    def format_version(self) -> int:
        return self.document["dataRelease"]["formatVersion"]

    def update_station_samples(self, samples: list[list[float]]) -> bool:
        """Record where the stations are, for the proposal of §15.1.

        Written right after the box, which comes from the same stations and the
        same fetch.

        Returns:
            whether the configuration changed, so a sweep over three hundred
            cities names the ones that moved and leaves the rest untouched.
        """
        if self.document.get("stationSamples") == samples:
            return False
        rebuilt = {}
        for key, value in self.document.items():
            if key == "stationSamples":
                continue
            rebuilt[key] = value
            if key == "boundingBox":
                rebuilt["stationSamples"] = samples
        if "stationSamples" not in rebuilt:
            rebuilt["stationSamples"] = samples
        self.document = rebuilt
        return True

    @property
    def has_electric_bikes(self) -> bool:
        """Whether the network lends pedal-assist bikes (§15).

        False as well when the configuration says nothing, which is the case
        of a network whose feed declares no vehicle type: the application then
        draws the plain bike rather than promising a motor nobody verified.
        """
        return bool(self.document.get("fleet", {}).get("electricBikes", False))

    def update_fleet(
        self,
        has_electric_bikes: bool,
        is_mixed: bool,
        vehicle_types: dict[str, str],
        bikes_seen: dict[str, int],
        surveyed_at: str,
    ) -> bool:
        """Record what the network lends, as counted from its own feeds.

        Written by ``tools/read_fleet.py`` and by nothing else: this is a fact
        about the city, and §16 wants facts observed rather than typed in.

        What is recorded seeds the application rather than settling it. The
        application counts again from the live feeds on every refresh (§4.1),
        so a survey run again is no longer what makes a network show the right
        bike — it is what makes its *first* launch show it, before any feed has
        been reached.

        Args:
            has_electric_bikes: whether pedal-assist bikes are in circulation.
            is_mixed: whether both kinds are, in numbers that make an offer.
            vehicle_types: the kind — ``mechanical``, ``electric`` or
                ``other`` — of every vehicle type identifier the status feed
                may count by.
            bikes_seen: how many of each kind were out at survey time, kept so
                that the two flags above can be checked rather than believed.

        Returns:
            whether the configuration changed, so a caller sweeping three
            hundred cities can name the ones that moved and leave the rest
            untouched — including their file's modification date.
        """
        # Placed after the network it describes, where whoever reads the file
        # expects it, rather than appended at the end.
        fleet = {
            "$comment": FLEET_COMMENT,
            "electricBikes": has_electric_bikes,
            "mixed": is_mixed,
            "vehicleTypes": dict(sorted(vehicle_types.items())),
            "bikesSeen": bikes_seen,
            "surveyedAt": surveyed_at,
        }
        stored = self.document.get("fleet")
        # The documentation counts as content: a comment reworded in one place
        # must reach the three hundred files it explains, and a run that
        # changed nothing else must leave their dates alone. The survey date
        # is excluded on purpose — it moves at every run by definition, and
        # rewriting three hundred files to say "counted again, same answer"
        # would drown the one city that did change.
        if stored is not None and all(
            stored.get(key) == value
            for key, value in fleet.items()
            if key != "surveyedAt"
        ):
            return False
        rebuilt = {}
        for key, value in self.document.items():
            if key == "fleet":
                continue
            rebuilt[key] = value
            if key == "network":
                rebuilt["fleet"] = fleet
        if "fleet" not in rebuilt:
            rebuilt["fleet"] = fleet
        self.document = rebuilt
        return True

    @property
    def bounding_box(self) -> BoundingBox:
        """The reference bounding box shared by all three datasets.

        Raises:
            ValueError: if the box has never been computed. Running
                ``tools/compute_bbox.py`` fills it in from the live station
                list, which is the only supported way to set it (§4).
        """
        box = self.document["boundingBox"]
        if box.get("south") is None:
            raise ValueError(
                "The box has not been computed yet in "
                f"{self.path.name}. Run this first: python3 tools/compute_bbox.py"
            )
        return BoundingBox(
            south=box["south"],
            west=box["west"],
            north=box["north"],
            east=box["east"],
        )

    @property
    def bounding_box_margin_metres(self) -> float:
        return float(self.document["boundingBox"]["marginMeters"])

    def update_bounding_box(
        self,
        box: BoundingBox,
        station_count: int,
        generated_at: str,
        opening: OpeningView | None = None,
        station_positions: Sequence[tuple[float, float]] = (),
    ) -> bool:
        """Record a freshly computed bounding box, preserving the comments.

        The opening framing follows the stations when it stops showing any. A
        recomputation does not only widen the rectangle: dropping a stray
        station, or a network that retreats from an outlying town, moves an edge
        inwards, and VélôToulouse showed what that costs — one station shed
        26 km west of the others took the western edge with it, and the centre
        stayed where the old rectangle used to be, outside the new one.

        Falling outside the box was long the only test, and it let the worse
        failure through: a centre that stays well inside a box spanning two
        conurbations, showing neither. So the centre is now also required to
        have a station on screen at the opening zoom, which is what the map is
        opened for in the first place.

        A framing that still shows the network is left alone: the first cities
        served have their centre set on the city centre rather than computed,
        and that is a deliberate choice this must not undo.

        Args:
            opening: the framing read from the stations, used only if the
                stored one no longer shows any. Nothing is moved without it.
            station_positions: a spread of positions through the most populous
                cluster, the witness the stored framing is judged against —
                a handful of points, deliberately, and taken from that cluster
                alone. Judged against every station instead, a framing catching
                one station at the end of an Alpine valley would pass:
                sharedmobility.ch has enough of them for the whole of
                Switzerland to answer "yes, one" wherever it is asked. Judged
                against the whole network, a map opening 130 km west of
                Strasbourg would pass on one station of the Meuse.

        Returns:
            whether the opening framing had to be moved, so the caller can say
            so in its log rather than change the map in silence.
        """
        stored = self.document["boundingBox"]
        stored["generatedAt"] = generated_at
        stored["stationCount"] = station_count
        stored["south"] = round(box.south, 6)
        stored["west"] = round(box.west, 6)
        stored["north"] = round(box.north, 6)
        stored["east"] = round(box.east, 6)

        map_block = self.document["map"]
        current = OpeningView(
            latitude=map_block["defaultCenterLatitude"],
            longitude=map_block["defaultCenterLongitude"],
            zoom=map_block["defaultZoom"],
        )
        if opening is None:
            return False
        if box.contains(current.latitude, current.longitude):
            if not station_positions or current.shows_a_station(station_positions):
                return False
            # Nothing to gain, nothing moves. A network spread over a whole
            # country has no framing that shows one of a handful of points
            # spread through it, and a map that cannot be improved on is not a
            # map to rewrite at every regeneration.
            if opening.distance_to_nearest_metres(
                station_positions
            ) >= current.distance_to_nearest_metres(station_positions):
                return False
        map_block["defaultCenterLatitude"] = opening.latitude
        map_block["defaultCenterLongitude"] = opening.longitude
        map_block["defaultZoom"] = opening.zoom
        return True
