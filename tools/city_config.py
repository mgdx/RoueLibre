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
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CITY_CONFIG = REPO_ROOT / "config" / "cities" / "lille.json"

# One degree of latitude is very nearly this many metres everywhere on the
# ellipsoid; the variation is far below the precision this project needs.
METRES_PER_DEGREE_LATITUDE = 111_320.0


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
    def format_version(self) -> int:
        return self.document["dataRelease"]["formatVersion"]

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
                "L'emprise n'est pas encore calculée dans "
                f"{self.path.name}. Lance d'abord : python3 tools/compute_bbox.py"
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
        self, box: BoundingBox, station_count: int, generated_at: str
    ) -> None:
        """Record a freshly computed bounding box, preserving the comments."""
        stored = self.document["boundingBox"]
        stored["generatedAt"] = generated_at
        stored["stationCount"] = station_count
        stored["south"] = round(box.south, 6)
        stored["west"] = round(box.west, 6)
        stored["north"] = round(box.north, 6)
        stored["east"] = round(box.east, 6)
