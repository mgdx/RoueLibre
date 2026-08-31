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

Re-runnable: an asset already online under the digest its manifest announces is
left alone, so an interrupted upload is finished by running the command again.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

from build_manifest import sha256_of

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


def online_digests(tag: str, repo: str) -> dict[str, str] | None:
    """The names already online and their SHA-256, or None when there is no
    such release.

    **The digest, and never the size.** The size answers "is there a file of
    that name weighing about that much", which is not the question the phone
    asks: it recomputes the digest its manifest announces and throws away
    anything else. A regeneration that leaves the weight untouched is ordinary
    — keeping the house-number mark as its own country writes it changed the
    content of 245 address indexes and the size of not one of them — and while
    the comparison was made on sizes, every one of those stayed online beneath
    a manifest rewritten to describe the new file. 253 conurbations then
    downloaded an index their application refused, address search and routing
    graph alike.

    An asset GitHub gives no digest for is reported as unknown, which sends it
    up again: re-sending a file that was already right costs bandwidth, leaving
    a stale one costs a city its address search.
    """
    identifier = subprocess.run(
        ["gh", "api", f"repos/{repo}/releases/tags/{tag}", "--jq", ".id"],
        capture_output=True, text=True,
    )
    if identifier.returncode != 0:
        return None
    # The assets embedded in the release answer are not a paginated list; the
    # endpoint devoted to them is, and a country release may hold a thousand.
    listing = run(["gh", "api", "--paginate",
                   f"repos/{repo}/releases/{identifier.stdout.strip()}"
                   f"/assets?per_page=100",
                   "--jq", ".[] | [.name, .digest] | @tsv"])
    digests = {}
    for line in listing.splitlines():
        name, _, digest = line.partition("\t")
        digests[name] = digest.removeprefix("sha256:")
    return digests


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


def stamp_manifests(cities: list[dict], tag: str, dry_run: bool) -> set[str]:
    """Rewrite every manifest so its URLs name the release its files are in.

    Also whenever a dataset has been generated since the manifest was written:
    a manifest is a list of digests, and one older than the files it describes
    would send the application after a file it then refuses.

    Returns the cities rewritten, which the catalogue then has to be re-derived
    from: this step is itself one of the ways it goes stale.
    """
    stamped = set()
    for city in cities:
        country_tag = f"{tag}-{city['country']}"
        written = city["manifest_path"].stat().st_mtime
        described = max(
            (source.stat().st_mtime for source, _ in heavy_files(city)
             if source.exists()),
            default=0.0,
        )
        if city["manifest"].get("releaseTag") == country_tag and written >= described:
            continue
        print(f"  {city['id']} → {country_tag}")
        stamped.add(city["id"])
        if dry_run:
            continue
        run(["/usr/bin/python3", str(TOOLS_DIR / "build_manifest.py"),
             "--config", str(city["config"]),
             "--data-dir", str(city["directory"]),
             "--output", str(city["manifest_path"]),
             "--release-tag", country_tag])
        city["manifest"] = json.loads(city["manifest_path"].read_text())
    return stamped


def manifest_size(manifest: dict) -> int:
    """What a manifest says the whole of a city's data weighs."""
    return sum(entry["sizeBytes"]
               for dataset in manifest.get("datasets", [])
               for entry in dataset["files"])


def check_catalogue(cities: list[dict], stamped: set[str], dry_run: bool) -> None:
    """Refuse to publish a catalogue that contradicts the manifests beside it.

    The catalogue and the manifests leave in the same release and describe the
    same files, but they are produced by two commands run at two moments. The
    catalogue is the one the list of cities reads, so a catalogue older than the
    manifests makes the application announce a city as unpublished on one screen
    and offer its download on the next.

    Nothing here repairs the catalogue: it is derived from the configurations
    and the manifests, and re-deriving it silently at publishing time would hide
    a change to a file the repository tracks. Refusing before a single byte goes
    out, and naming the command that fixes it, is what stops the drift from
    reaching a phone.
    """
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    entries = {entry["id"]: entry for entry in catalogue["cities"]}
    disagreements = []
    for city in cities:
        entry = entries.get(city["id"])
        if entry is None:
            disagreements.append(f"{city['id']}: absent from the catalogue")
            continue
        # A dry run leaves the manifests as they were, so a city it would have
        # stamped cannot be compared against what will actually be published.
        if dry_run and city["id"] in stamped:
            continue
        expected = manifest_size(city["manifest"])
        if entry["dataSizeBytes"] != expected:
            disagreements.append(
                f"{city['id']}: catalogue says {entry['dataSizeBytes']}, "
                f"manifest says {expected} bytes")
        elif entry["releaseTag"] != city["manifest"].get("releaseTag"):
            disagreements.append(
                f"{city['id']}: catalogue says release {entry['releaseTag']}, "
                f"manifest says {city['manifest'].get('releaseTag')}")

    if dry_run and stamped:
        print(f"  {len(stamped)} manifests would be rewritten; the catalogue "
              f"has to be rebuilt after that, and is not compared here")
    if not disagreements:
        print(f"  {len(cities)} entries agree with their manifest")
        return
    for line in disagreements[:10]:
        print(f"    {line}")
    if len(disagreements) > 10:
        print(f"    … and {len(disagreements) - 10} more")
    raise PublishError(
        f"the catalogue disagrees with {len(disagreements)} of the manifests "
        f"it would be published with: run python3 tools/build_catalogue.py, "
        f"then publish again")


def upload(tag: str, repo: str, title: str, notes: str,
           files: list[tuple[Path, str]], staging: Path, dry_run: bool) -> int:
    """Create the release if needed and upload what is not already there."""
    online = online_digests(tag, repo)
    if online is None:
        print(f"  creating release {tag}")
        run(["gh", "release", "create", tag, "--repo", repo,
             "--title", title, "--notes", notes], dry_run)
        online = {}

    # gh names an asset after the file it is given, so the staging directory
    # holds hard links under the published names — 5.6 GB copied to be renamed
    # would be 5.6 GB of nothing.
    staging.mkdir(parents=True, exist_ok=True)
    for stale in staging.iterdir():
        stale.unlink()
    pending = []
    for source, name in files:
        # Hashed only where there is something to compare it with: a file whose
        # name is not online yet goes up whatever its content.
        if name in online and online[name] == sha256_of(source):
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


def check_published(cities: list[dict], tag: str, repo: str) -> None:
    """Refuse to publish a manifest describing a file that is not online.

    This runs between the country releases and the index, which is the last
    moment anything can be stopped: the index release is deleted and re-created
    rather than updated, and once its manifests are out they are what every
    installation reads.

    A manifest is a list of digests, and the application believes it to the
    byte. So a manifest may only go out once the files it names are online
    *under those very digests* — the case this guard exists for being a run
    that stamps a manifest without sending what it describes, which is what
    ``--network`` does to every city it does not name.
    """
    disagreements = []
    for country in sorted({city["country"] for city in cities}):
        online = online_digests(f"{tag}-{country}", repo) or {}
        for city in (c for c in cities if c["country"] == country):
            for dataset in city["manifest"]["datasets"]:
                for entry in dataset["files"]:
                    name = entry["url"].rsplit("/", 1)[-1]
                    if online.get(name) != entry["sha256"]:
                        disagreements.append(
                            f"{city['id']}: {name} is "
                            f"{'absent' if name not in online else 'another file'} "
                            f"in {tag}-{country}")
    if not disagreements:
        print(f"  the files of {len(cities)} cities are online, digests included")
        return
    for line in disagreements[:10]:
        print(f"    {line}")
    if len(disagreements) > 10:
        print(f"    … and {len(disagreements) - 10} more")
    raise PublishError(
        f"{len(disagreements)} files named by a manifest are not online as that "
        f"manifest describes them: publish those cities' files — without "
        f"--network, or with it naming them — before their manifests go out")


def verify(cities: list[dict], repo: str) -> bool:
    """Ask for the addresses the application will ask for, and read one whole.

    Reporting on what was sent proves nothing: what matters is that the URLs
    written in the manifests answer, and that a file downloaded from one of
    them still matches its digest.
    """
    import hashlib
    failures = []
    # One listing per release, kept: a manifest names the release its files are
    # in through their URLs, and a country is asked about once.
    listings: dict[str, dict[str, str]] = {}

    def digest_online(url: str) -> str | None:
        release_tag, name = url.rsplit("/", 2)[-2:]
        if release_tag not in listings:
            listings[release_tag] = online_digests(release_tag, repo) or {}
        return listings[release_tag].get(name)

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
            continue
        # What the published manifest promises, against what is really at the
        # end of the URLs it gives. Reporting on what was sent proves nothing,
        # and one city read whole proves it of one city: this compares all of
        # them, and costs one call per country.
        for dataset in published.get("datasets", []):
            for entry in dataset["files"]:
                if digest_online(entry["url"]) != entry["sha256"]:
                    name = entry["url"].rsplit("/", 1)[-1]
                    failures.append((city["id"], f"digest announced: {name}"))
    print(f"  {len(cities) - len({city for city, _ in failures})}/{len(cities)} "
          f"manifests answer and describe the files that are online")

    # The whole of one city, downloaded rather than asked about: the digest
    # GitHub reports is not the digest of what it hands over.
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
    parser.add_argument("--network", action="append", default=[],
                        help="publish the heavy files of these networks only, "
                             "by identifier; repeatable. The index release "
                             "still carries every generated city")
    parser.add_argument("--no-index", action="store_true",
                        help="leave the index release alone. The catalogue is "
                             "what makes a city visible to every installation, "
                             "including those too old to serve it, so it can "
                             "have to wait for an application release")
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

        # A run named by "--network" concerns those networks and no others:
        # stamping a manifest whose files are not going out is what leaves a
        # city announcing a digest nothing online carries.
        wanted = set(arguments.network)
        published = [city for city in cities
                     if not wanted or city["id"] in wanted]

        print("── Manifests ──")
        stamped = stamp_manifests(published, arguments.tag, arguments.dry_run)

        # Before anything is sent: a catalogue that lies about a city is worth
        # catching here rather than after 5.6 GB have gone out.
        print("\n── Catalogue ──")
        check_catalogue(cities, stamped, arguments.dry_run)

        print("\n── Country releases ──")
        staging = REPO_ROOT / "data" / "release" / "staging"
        # Named networks only, where they are asked for. The comparison against
        # what is already online would otherwise carry along every file
        # regenerated since the last publication, in countries nobody meant to
        # touch: publishing six cities must send six cities.
        for country, group in sorted(by_country.items()):
            if wanted:
                group = [city for city in group if city["id"] in wanted]
                if not group:
                    continue
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
        if arguments.no_index:
            print("  left alone (--no-index): the catalogue online still "
                  "describes the previous set of cities")
            return 0

        # The last moment anything can be stopped, and only where the manifests
        # are really going out: past this point they are what a phone obeys.
        if arguments.dry_run:
            print("  dry run: nothing was sent, so nothing is compared")
        else:
            check_published(cities, arguments.tag, arguments.repo)
        if online_digests(arguments.tag, arguments.repo) is not None:
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
