"""Every city served must name the extracts its whole box is cut from (§4).

This is the guard rail Washington went through. Its configuration named
Maryland and Virginia, and its box holds a third extract, the District of
Columbia — where four of its eight sampled stations stand. The tiles, the
routing graph and the addresses are all cut from the same merge of extracts,
so the district came out of the three of them at once: an empty map, no route
and no address over the centre of the city, on a build where nothing had
failed.

What lets that happen is that the two are computed apart. A box is recomputed
from the live feed on every regeneration; the extracts were derived once, from
the box a survey held days earlier. The test asks of the file what nothing else
asks: are these extracts the ones THIS box reaches?

It needs Geofabrik's index of extracts, which is a download, and the tests go
out on no network. So it runs when the index is already in `data/cache` — on
the machine that generates the data, which is the one where a configuration is
written — and is skipped elsewhere. Running it is a matter of:

    python3 -c "import sys; sys.path.insert(0, 'tools'); \
                from discover_networks import Extracts; Extracts.download()"
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path

from discover_networks import CACHE_DIR, Extracts

CITIES_DIRECTORY = Path(__file__).resolve().parent.parent.parent / "config" / "cities"
INDEX_CACHE = CACHE_DIR / "geofabrik-index.json"


@unittest.skipUnless(
    INDEX_CACHE.is_file(), f"Geofabrik's index of extracts is not in {CACHE_DIR}"
)
class CityExtractsTest(unittest.TestCase):
    """The OpenStreetMap extracts the shipped configurations are cut from."""

    @classmethod
    def setUpClass(cls) -> None:
        index = json.loads(INDEX_CACHE.read_text(encoding="utf-8"))
        cls.extracts = Extracts(index["features"])

    def test_every_extract_the_box_reaches_is_named(self) -> None:
        """An extract left out is a hole of its own shape in all three datasets."""
        for path in sorted(CITIES_DIRECTORY.glob("*.json")):
            document = json.loads(path.read_text(encoding="utf-8"))
            box = document.get("boundingBox") or {}
            if box.get("south") is None:
                continue
            with self.subTest(city=path.stem):
                named = set(document.get("dataSources", {}).get("osmRegions", []))
                reached = set(self.extracts.for_box(box))
                # Named and not reached is waste, not a hole: a box that has
                # shrunk leaves its old extracts behind, and they cost a
                # download rather than a blank map. Only the missing ones fail.
                self.assertFalse(
                    reached - named,
                    f"the box reaches {sorted(reached - named)}, which "
                    f"{path.name} does not name — re-run "
                    f"python3 tools/add_city.py --refresh-sources",
                )


if __name__ == "__main__":
    unittest.main()
