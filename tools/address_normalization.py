"""Normalisation of street names, shared with the Android application.

The rules themselves live in ``config/address-normalization/<language>.json``
so that the index and the search box cannot drift apart; this module only
applies them. See the header of any of those files for why.

There is one file per language because a street type is a word of a language:
``rue`` and ``boulevard`` say nothing about a Warsaw address, where the word is
``ulica`` and the abbreviation ``ul.``. SPEC.md §15.1 asks for exactly that —
rules that travel with a city's data rather than being frozen into the
application.

The language meant is the one the ADDRESS BASE is written in, not the one the
interface speaks: an index built over Antwerp is searched in Dutch whatever the
phone is set to.

The pipeline, applied identically to indexed names and to typed queries:

    "Bd. de l'Hôpital Militaire"  →  "boulevard de l hopital militaire"
                                  →  type "boulevard", name "de l hopital militaire"
"""

from __future__ import annotations

import json
import unicodedata
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RULES_DIR = REPO_ROOT / "config" / "address-normalization"

# The language a set of data falls back on when it names none, and the one
# whose file must exist: English, like the interface's own default (§9).
DEFAULT_LANGUAGE = "en"


@dataclass(frozen=True)
class SplitName:
    """A street name split into its type and its proper name."""

    street_type: str | None
    proper_name: str

    @property
    def full(self) -> str:
        if self.street_type:
            return f"{self.street_type} {self.proper_name}".strip()
        return self.proper_name


class AddressNormalizer:
    """Applies the shared normalisation rules to street names and queries."""

    def __init__(self, rules: dict) -> None:
        self.language = rules.get("language", DEFAULT_LANGUAGE)
        self.rules_version = rules.get("rulesVersion", 1)
        self.reference_names = list(rules.get("referenceNames", []))
        # Letters accent removal cannot reach, because they are not accented
        # letters: the German ß, the Nordic ø and æ, the Polish ł, the Greek
        # final sigma. Folded on both sides of the search, so that whoever
        # types "strasse" finds a "Straße".
        self._letters = {
            key: value for key, value in (rules.get("letterReplacements") or {}).items()
            if not key.startswith("$")
        }
        abbreviations = rules["abbreviations"]
        self._anywhere = {
            key: value for key, value in abbreviations["anywhere"].items()
            if not key.startswith("$")
        }
        self._leading_only = {
            key: value for key, value in abbreviations["leadingOnly"].items()
            if not key.startswith("$")
        }
        self._punctuation = set(rules["punctuationReplacedBySpace"])
        self._stop_words = frozenset(rules["stopWords"]["words"])
        # Longest first so that "rond point" wins over "rond", and "grand rue"
        # over "rue".
        self._street_types = sorted(
            rules["streetTypes"]["types"], key=lambda t: -len(t.split())
        )

    @classmethod
    def load(cls, path: Path) -> "AddressNormalizer":
        with path.open(encoding="utf-8") as stream:
            return cls(json.load(stream))

    @classmethod
    def for_language(cls, language: str | None) -> "AddressNormalizer":
        """The rules of a language, or English where none are written yet.

        Falling back rather than failing is deliberate: a network appears in a
        country before anyone has written that country's street vocabulary, and
        an index built with the plainest rules is still searchable — a street
        found by its whole name, without the type/name split. What is never
        acceptable is building an index with one set of rules and searching it
        with another, and that cannot happen here: the language retained is
        written into the index, and the application reads it back from there.
        """
        for candidate in (language, DEFAULT_LANGUAGE):
            if not candidate:
                continue
            path = RULES_DIR / f"{candidate}.json"
            if path.is_file():
                return cls.load(path)
        raise FileNotFoundError(f"No normalisation rules in {RULES_DIR}")

    @property
    def stop_words(self) -> frozenset[str]:
        return self._stop_words

    @staticmethod
    def strip_accents(text: str) -> str:
        """Remove diacritics, keeping the base letters.

        Decomposing then dropping the combining marks handles the whole Latin
        range at once, which matters for Flemish-rooted names common around
        Lille.
        """
        decomposed = unicodedata.normalize("NFD", text)
        return "".join(
            character for character in decomposed
            if unicodedata.category(character) != "Mn"
        )

    def normalize(self, text: str) -> str:
        """Fold a raw name or query down to its comparable form.

        Lowercase, letters folded, unaccented, punctuation turned into word
        breaks, known abbreviations expanded, whitespace collapsed.

        The order matters and is the same in Kotlin: lowercasing first, since
        the letters folded are written in lower case; then the folding, whose
        output — "ss" for "ß" — must itself go through accent removal.
        """
        lowered = text.lower()
        if self._letters:
            lowered = "".join(self._letters.get(character, character)
                              for character in lowered)
        folded = self.strip_accents(lowered)
        folded = "".join(
            " " if character in self._punctuation else character
            for character in folded
        )
        words = folded.split()

        expanded: list[str] = []
        for position, word in enumerate(words):
            if word in self._anywhere:
                expanded.extend(self._anywhere[word].split())
            elif position == 0 and word in self._leading_only:
                expanded.extend(self._leading_only[word].split())
            else:
                expanded.append(word)
        return " ".join(expanded)

    def split_street_type(self, normalized: str) -> SplitName:
        """Separate a leading street type from the proper name.

        Only a leading type is recognised: in "rue de la Place", "place" is
        part of the name, not the type of the way.
        """
        words = normalized.split()
        for candidate in self._street_types:
            candidate_words = candidate.split()
            if words[: len(candidate_words)] == candidate_words:
                remainder = " ".join(words[len(candidate_words):])
                # A name reduced to nothing but its type — "la Grand Place" —
                # keeps the type as its name, otherwise it becomes unsearchable.
                if not remainder:
                    return SplitName(None, candidate)
                return SplitName(candidate, remainder)
        return SplitName(None, normalized)

    def analyse(self, raw_name: str) -> SplitName:
        """Normalise a raw street name and split its type off in one step."""
        return self.split_street_type(self.normalize(raw_name))


@lru_cache(maxsize=None)
def normalizer_for(language: str | None) -> AddressNormalizer:
    """The rules of a language, loaded once per run."""
    return AddressNormalizer.for_language(language)


def available_languages() -> list[str]:
    """The languages whose street vocabulary is written down."""
    return sorted(path.stem for path in RULES_DIR.glob("*.json"))
