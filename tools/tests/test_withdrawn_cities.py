"""The networks the catalogue lists as withdrawn (SPEC.md §15.1).

What is held here is the one rule that keeps the list safe for every build in
the field: a withdrawn network never stands among the cities, and never while
its configuration is still shipped. The rest is what makes a record readable at
all on the application's side — an identifier in its alphabet, and a date.
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from build_catalogue import DEFAULT_CITIES_DIR, DEFAULT_WITHDRAWN, CatalogueError, read_withdrawn

PAU = {
    "id": "idecycle",
    "displayName": "IDEcycle",
    "mainCity": "Pau",
    "withdrawnOn": "2026-10-06",
}


def written(directory: Path, *records: dict) -> Path:
    """A withdrawal list holding [records], as config/ keeps it."""
    path = directory / "withdrawn-cities.json"
    path.write_text(
        json.dumps({"$comment": ["a note"], "withdrawn": list(records)}),
        encoding="utf-8",
    )
    return path


class ReadWithdrawnTest(unittest.TestCase):

    def test_a_record_is_copied_without_its_comment(self):
        with TemporaryDirectory() as directory:
            entries = read_withdrawn(written(Path(directory), PAU), set())
        self.assertEqual([PAU], entries)

    def test_no_list_is_no_withdrawal(self):
        with TemporaryDirectory() as directory:
            self.assertEqual([], read_withdrawn(Path(directory) / "absent.json", set()))

    def test_a_network_still_configured_is_refused(self):
        with TemporaryDirectory() as directory:
            with self.assertRaises(CatalogueError):
                read_withdrawn(written(Path(directory), PAU), {"idecycle"})

    def test_a_record_without_a_date_is_refused(self):
        undated = {key: value for key, value in PAU.items() if key != "withdrawnOn"}
        with TemporaryDirectory() as directory:
            with self.assertRaises(CatalogueError):
                read_withdrawn(written(Path(directory), undated), set())

    def test_an_identifier_the_application_cannot_hold_is_refused(self):
        with TemporaryDirectory() as directory:
            with self.assertRaises(CatalogueError):
                read_withdrawn(written(Path(directory), {**PAU, "id": "../pau"}), set())

    def test_a_network_listed_twice_is_refused(self):
        with TemporaryDirectory() as directory:
            with self.assertRaises(CatalogueError):
                read_withdrawn(written(Path(directory), PAU, PAU), set())

    def test_the_list_in_the_repository_agrees_with_the_configurations(self):
        configured = {
            json.loads(path.read_text(encoding="utf-8"))["network"]["id"]
            for path in DEFAULT_CITIES_DIR.glob("*.json")
        }
        entries = read_withdrawn(DEFAULT_WITHDRAWN, configured)
        self.assertIn("idecycle", {entry["id"] for entry in entries})


if __name__ == "__main__":
    unittest.main()
