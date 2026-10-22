"""The operators an aggregator's city keeps (SPEC.md §15).

sharedmobility.ch publishes every operator of Switzerland under one discovery
document. Zürich keeps its bike-share operators alone, and the tools reading its
stations — the box, the samples, the fleet — must keep the same ones as the
application does, or the data is cut around other operators' zones.
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path

from city_config import CityConfig, kept_by_providers

CITIES = Path(__file__).resolve().parents[2] / "config" / "cities"

STATIONS = [
    {"station_id": "velospot:168", "provider_id": "velospot"},
    {"station_id": "voiscooters.com:312", "provider_id": "voiscooters.com"},
    {"station_id": "mystery"},
]


class KeptByProviders(unittest.TestCase):

    def test_a_city_naming_no_operator_keeps_the_whole_feed(self) -> None:
        self.assertEqual(STATIONS, kept_by_providers(STATIONS, frozenset()))

    def test_a_city_naming_operators_keeps_theirs_alone(self) -> None:
        kept = kept_by_providers(STATIONS, frozenset({"velospot"}))
        self.assertEqual(["velospot:168"], [station["station_id"] for station in kept])


class ConfiguredProviders(unittest.TestCase):

    def test_zurich_keeps_its_bike_share_operators(self) -> None:
        zurich = CityConfig.load(CITIES / "zurich.json").provider_ids
        self.assertIn("velospot", zurich)
        self.assertIn("nextbike_ch", zurich)
        for other in ("voiscooters.com", "mybuxi_ch", "mobility", "dott_zurich"):
            self.assertNotIn(other, zurich)

    def test_no_other_city_names_any(self) -> None:
        for path in sorted(CITIES.glob("*.json")):
            if path.name == "zurich.json":
                continue
            document = json.loads(path.read_text(encoding="utf-8"))
            self.assertNotIn("providerIds", document["gbfs"], path.name)

    def test_blank_identifiers_are_no_operator(self) -> None:
        config = CityConfig(Path("x.json"), {"gbfs": {"providerIds": [" velospot ", " "]}})
        self.assertEqual(frozenset({"velospot"}), config.provider_ids)


if __name__ == "__main__":
    unittest.main()
