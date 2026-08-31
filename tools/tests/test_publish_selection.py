"""What the publisher sends, and what it refuses to publish ahead of the files.

The case is the one that broke 253 conurbations on 23 August 2026. Keeping the
house-number mark as its own country writes it changed the content of 245
address indexes without changing the weight of a single one; the publisher
compared weights, sent none of them, and the index release went out carrying
the manifests describing the new files. Those cities then downloaded an address
index — and, for 72 of them, a routing graph — whose digest the application
recomputed, refused and deleted.

Two rules answer it, and they are what these cases hold: what goes up is
decided on the digest, and no manifest goes out ahead of the files it names.
"""

from __future__ import annotations

import contextlib
import io
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import mock

import publish_data
from build_manifest import sha256_of

# Two payloads of the same length and of different content: the regeneration
# this test exists for, reduced to its essentials.
BEFORE = b"12a rue des Arts"
AFTER = b"12A rue des Arts"

ONLINE_NAME = "pony-addresses.sqlite"


def generated_city(directory: Path, payload: bytes) -> dict:
    """A generated city holding one address index, as read_cities returns it."""
    index = directory / "addresses.sqlite"
    index.write_bytes(payload)
    return {
        "id": "pony",
        "country": "fr",
        "directory": directory,
        "manifest_path": directory / "manifest.json",
        "manifest": {
            "releaseTag": "data-2026-08-fr",
            "datasets": [{
                "id": "addresses",
                "files": [{
                    "name": "addresses.sqlite",
                    "url": "https://example.invalid/releases/download/"
                           f"data-2026-08-fr/{ONLINE_NAME}",
                    "sizeBytes": len(payload),
                    "sha256": sha256_of(index),
                }],
            }],
        },
    }


def what_goes_up(city: dict, online: dict[str, str]) -> list[str]:
    """The names one upload would send, the network never being reached."""
    sent: list[str] = []

    def record(command: list[str], dry_run: bool = False) -> str:
        if command[:3] == ["gh", "release", "upload"]:
            # gh release upload <tag> --repo <repo> --clobber <paths…>
            sent.extend(Path(argument).name for argument in command[7:])
        return ""

    with TemporaryDirectory() as staging:
        with mock.patch.object(publish_data, "online_digests", return_value=online), \
                mock.patch.object(publish_data, "run", record), \
                contextlib.redirect_stdout(io.StringIO()):
            publish_data.upload("data-2026-08-fr", "repo/data", "title", "notes",
                                publish_data.heavy_files(city), Path(staging),
                                dry_run=False)
    return sorted(sent)


class UploadSelection(unittest.TestCase):

    def test_a_regenerated_file_of_the_same_size_is_sent(self) -> None:
        """The bug itself: the same weight, another content, and it must go."""
        self.assertEqual(len(BEFORE), len(AFTER))
        with TemporaryDirectory() as directory:
            previous = Path(directory) / "previous"
            previous.write_bytes(BEFORE)
            city = generated_city(Path(directory), AFTER)
            self.assertEqual([ONLINE_NAME],
                             what_goes_up(city, {ONLINE_NAME: sha256_of(previous)}))

    def test_a_file_already_online_is_left_alone(self) -> None:
        with TemporaryDirectory() as directory:
            city = generated_city(Path(directory), AFTER)
            announced = city["manifest"]["datasets"][0]["files"][0]["sha256"]
            self.assertEqual([], what_goes_up(city, {ONLINE_NAME: announced}))

    def test_a_file_absent_from_the_release_is_sent(self) -> None:
        with TemporaryDirectory() as directory:
            city = generated_city(Path(directory), AFTER)
            self.assertEqual([ONLINE_NAME], what_goes_up(city, {}))


class ManifestsAgainstWhatIsOnline(unittest.TestCase):

    def check(self, city: dict, online: dict[str, str]) -> None:
        with mock.patch.object(publish_data, "online_digests", return_value=online), \
                contextlib.redirect_stdout(io.StringIO()):
            publish_data.check_published([city], "data-2026-08", "repo/data")

    def test_a_manifest_whose_file_is_online_passes(self) -> None:
        with TemporaryDirectory() as directory:
            city = generated_city(Path(directory), AFTER)
            announced = city["manifest"]["datasets"][0]["files"][0]["sha256"]
            self.check(city, {ONLINE_NAME: announced})

    def test_a_manifest_describing_another_file_is_refused(self) -> None:
        """The index release stops rather than send a phone after that file."""
        with TemporaryDirectory() as directory:
            city = generated_city(Path(directory), AFTER)
            previous = Path(directory) / "previous"
            previous.write_bytes(BEFORE)
            with self.assertRaises(publish_data.PublishError):
                self.check(city, {ONLINE_NAME: sha256_of(previous)})

    def test_a_manifest_naming_an_absent_file_is_refused(self) -> None:
        with TemporaryDirectory() as directory:
            city = generated_city(Path(directory), AFTER)
            with self.assertRaises(publish_data.PublishError):
                self.check(city, {})


if __name__ == "__main__":
    unittest.main()
