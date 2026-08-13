#!/usr/bin/env python3
"""Publish the generated datasets to the RoueLibre-data releases (SPEC.md §4.4).

GitHub allows **1,000 assets per release**, and 306 conurbations come to some
1,350 files. The sets are therefore spread over one release per country —
``data-<tag>-fr``, ``data-<tag>-de`` — and a last one, ``data-<tag>``, holding
only the catalogue and the 306 manifests.

That last one exists for a precise reason: the application asks for
``releases/latest/download/manifest-<network>.json``, and *latest* names the
newest release of the repository, whichever it is. Every manifest must
therefore sit in one release, and that release must be the newest — so it is
published after the others, and re-published whenever anything else is. The
heavy files it points to live wherever their manifest says, at a fixed tag that
no later release can steal.

A country is where the network is, as its configuration declares it. Nothing
else has to be recorded: the tag of a city's files is derived, never looked up,
which is what keeps a partial upload from turning into a registry to maintain.

Usage:
    python3 tools/publish_data.py [--dry-run] [--tag data-2026-08]
                                  [--repo mgdx/RoueLibre-data]

Re-runnable: an asset already online is left alone, so an interrupted upload is
finished by running the command again.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
CITIES_DIR = REPO_ROOT / "config" / "cities"
DATA_DIR = REPO_ROOT / "data" / "out"
CATALOGUE = REPO_ROOT / "config" / "catalogue.json"
BATCH = 20


class PublishError(RuntimeError):
    """Something the operator must fix; the message says what."""


def run(command: list[str], dry_run: bool = False) -> str:
    if dry_run:
        print(f"    would run: {' '.join(command[:6])}…")
        return ""
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        raise PublishError(f"{' '.join(command[:4])}… → {result.stderr.strip()[:300]}")
    return result.stdout


def existing_assets(tag: str, repo: str) -> set[str] | None:
    """The names already online, or None when the release does not exist."""
    result = subprocess.run(
        ["gh", "release", "view", tag, "--repo", repo, "--json", "assets"],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        return None
    return {asset["name"] for asset in json.loads(result.stdout)["assets"]}


def read_cities() -> list[dict]:
    """Every configured city whose data has been generated, with its manifest."""
    cities = []
    for path in sorted(CITIES_DIR.glob("*.json")):
        config = json.loads(path.read_text(encoding="utf-8"))
        network = config["network"]["id"]
        manifest_path = DATA_DIR / network / "manifest.json"
        if not manifest_path.is_file():
            continue
        cities.append({
            "id": network,
            "country": (config.get("country") or "zz").lower(),
            "config": path,
            "directory": DATA_DIR / network,
            "manifest_path": manifest_path,
            "manifest": json.loads(manifest_path.read_text(encoding="utf-8")),
        })
    return cities


def heavy_files(city: dict) -> list[tuple[Path, str]]:
    """The files a manifest points at, beside the name they take once online."""
    files = []
    for dataset in city["manifest"]["datasets"]:
        for entry in dataset["files"]:
            source = (city["directory"] / "routing" / entry["name"]
                      if dataset["id"] == "routing"
                      else city["directory"] / entry["name"])
            files.append((source, entry["url"].rsplit("/", 1)[-1]))
    return files


def stamp_manifests(cities: list[dict], tag: str, dry_run: bool) -> None:
    """Rewrite every manifest so its URLs name the release its files are in."""
    for city in cities:
        country_tag = f"{tag}-{city['country']}"
        if city["manifest"].get("releaseTag") == country_tag:
            continue
        print(f"  {city['id']} → {country_tag}")
        if dry_run:
            continue
        run(["/usr/bin/python3", str(TOOLS_DIR / "build_manifest.py"),
             "--config", str(city["config"]),
             "--data-dir", str(city["directory"]),
             "--output", str(city["manifest_path"]),
             "--release-tag", country_tag])
        city["manifest"] = json.loads(city["manifest_path"].read_text())


def upload(tag: str, repo: str, title: str, notes: str,
           files: list[tuple[Path, str]], staging: Path, dry_run: bool) -> int:
    """Create the release if needed and upload what is not already there."""
    online = existing_assets(tag, repo)
    if online is None:
        print(f"  creating release {tag}")
        run(["gh", "release", "create", tag, "--repo", repo,
             "--title", title, "--notes", notes], dry_run)
        online = set()

    # gh names an asset after the file it is given, so the staging directory
    # holds hard links under the published names — 5.6 GB copied to be renamed
    # would be 5.6 GB of nothing.
    staging.mkdir(parents=True, exist_ok=True)
    for stale in staging.iterdir():
        stale.unlink()
    pending = []
    for source, name in files:
        if name in online:
            continue
        link = staging / name
        link.hardlink_to(source)
        pending.append(link)
    if not pending:
        print(f"  {tag}: nothing to add")
        return 0
    print(f"  {tag}: {len(pending)} assets to upload")
    for start in range(0, len(pending), BATCH):
        batch = pending[start:start + BATCH]
        run(["gh", "release", "upload", tag, "--repo", repo, "--clobber"]
            + [str(path) for path in batch], dry_run)
        print(f"    {min(start + BATCH, len(pending))}/{len(pending)}")
    return len(pending)


def verify(cities: list[dict], repo: str) -> bool:
    """Ask for the addresses the application will ask for, and read one whole.

    Reporting on what was sent proves nothing: what matters is that the URLs
    written in the manifests answer, and that a file downloaded from one of
    them still matches its digest.
    """
    import hashlib
    failures = []
    for city in cities:
        url = (f"https://github.com/{repo}/releases/latest/download/"
               f"manifest-{city['id']}.json")
        try:
            with urllib.request.urlopen(url, timeout=60) as answer:
                published = json.loads(answer.read())
        except Exception as error:  # noqa: BLE001 — the reason is reported
            failures.append((city["id"], f"manifest: {error}"))
            continue
        if published.get("network") != city["id"]:
            failures.append((city["id"], "manifest names another network"))
    print(f"  {len(cities) - len(failures)}/{len(cities)} manifests answer")

    sample = min(cities, key=lambda city: sum(
        entry["sizeBytes"] for dataset in city["manifest"]["datasets"]
        for entry in dataset["files"]))
    print(f"  reading {sample['id']} whole, as the application would")
    for dataset in sample["manifest"]["datasets"]:
        for entry in dataset["files"]:
            with urllib.request.urlopen(entry["url"], timeout=300) as answer:
                payload = answer.read()
            if hashlib.sha256(payload).hexdigest() != entry["sha256"]:
                failures.append((sample["id"], f"digest: {entry['name']}"))
    for city, reason in failures[:10]:
        print(f"    {city}: {reason}")
    return not failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="mgdx/RoueLibre-data")
    parser.add_argument("--tag", default="data-2026-08")
    parser.add_argument("--dry-run", action="store_true")
    arguments = parser.parse_args()

    try:
        if subprocess.run(["gh", "auth", "status"], capture_output=True).returncode:
            raise PublishError("gh is not logged in: gh auth login")

        cities = read_cities()
        if not cities:
            raise PublishError(f"No generated city in {DATA_DIR}")
        by_country = defaultdict(list)
        for city in cities:
            by_country[city["country"]].append(city)
        print(f"{len(cities)} cities, {len(by_country)} countries\n")

        print("── Manifests ──")
        stamp_manifests(cities, arguments.tag, arguments.dry_run)

        print("\n── Country releases ──")
        staging = REPO_ROOT / "data" / "release" / "staging"
        for country, group in sorted(by_country.items()):
            files = [pair for city in group for pair in heavy_files(city)]
            upload(f"{arguments.tag}-{country}", arguments.repo,
                   f"Data of {len(group)} conurbations — {country.upper()}",
                   f"Base maps, routing graphs and address indexes of "
                   f"{len(group)} conurbations. The manifests describing them "
                   f"are in the {arguments.tag} release.",
                   files, staging, arguments.dry_run)

        # Last, and re-created rather than updated: a release becomes "latest"
        # by being the newest, and the application's manifest URLs depend on
        # this one holding that place.
        print("\n── Index release, published last ──")
        if existing_assets(arguments.tag, arguments.repo) is not None:
            print(f"  removing the previous {arguments.tag}")
            run(["gh", "release", "delete", arguments.tag, "--repo", arguments.repo,
                 "--yes", "--cleanup-tag"], arguments.dry_run)
        index = [(city["manifest_path"], f"manifest-{city['id']}.json")
                 for city in cities]
        index.append((CATALOGUE, "catalogue.json"))
        upload(arguments.tag, arguments.repo,
               f"Catalogue and manifests of {len(cities)} conurbations",
               "The index the application reads: the catalogue of served "
               "conurbations, and one manifest per city naming the files to "
               "download and their digests. The files themselves are in the "
               "per-country releases beside this one.",
               index, staging, arguments.dry_run)

        if arguments.dry_run:
            print("\nDry run: nothing was sent.")
            return 0
        print("\n── Check ──")
        return 0 if verify(cities, arguments.repo) else 1

    except PublishError as error:
        print(f"\nError: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
