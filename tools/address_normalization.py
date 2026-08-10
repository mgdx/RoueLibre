"""Normalisation of street names, shared with the Android application.

The rules themselves live in ``config/address_normalization.json`` so that the
index and the search box cannot drift apart; this module only applies them.
See the header of that file for why.

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
DEFAULT_RULES_FILE = REPO_ROOT / "config" / "address_normalization.json"


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
    def load(cls, path: Path = DEFAULT_RULES_FILE) -> "AddressNormalizer":
        with path.open(encoding="utf-8") as stream:
            return cls(json.load(stream))

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

        Lowercase, unaccented, punctuation turned into word breaks, known
        abbreviations expanded, whitespace collapsed.
        """
        folded = self.strip_accents(text).lower()
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


@lru_cache(maxsize=1)
def default_normalizer() -> AddressNormalizer:
    return AddressNormalizer.load()


# Names written out on purpose, replayed by the Kotlin test against every
# generated network (see tools/build_address_index.py). They exist because the
# real street names sampled from a city's own address base only ever cover that
# city's vocabulary: the first block is what any French address base holds, the
# second is what a single region holds and the others do not. A rule added for
# Marseille or for Saint-Denis de La Réunion is worth nothing if no test ever
# reads a name written the way people write it there.
REFERENCE_STREET_NAMES = [
    # Everywhere, and the abbreviations the Base Adresse Nationale carries.
    "Rue Gambetta", "Boulevard de la Liberté", "Av. des Flandres",
    "Bd Victor Hugo", "R. Nationale", "St-André", "Rue de l'Hôpital Militaire",
    "Place du Général de Gaulle", "Chemin des Écoliers", "Grand Place",
    "Rond-Point de l'Europe", "Impasse Sainte-Cécile", "Drève du Château",
    "rue jean-baptiste lebas", "FAUBOURG DE ROUBAIX", "Allée Père Damien",
    "ALL DES TILLEULS", "CHE DE LA FONTAINE", "MTE DU CALVAIRE",
    "RLE DES QUATRE VENTS", "LD LES GRANDES TERRES", "TRA DE LA GARE",
    "PRV Notre-Dame", "ESP Charles de Gaulle", "VLGE DE HAUT",
    "Rte Départementale 6", "Chemin Rural n°4", "Terre-Plein Central",
    # One region each, in the words that region uses.
    "Traverse de la Bonne Mère", "Vallon des Auffes", "Calanque de Sormiou",
    "Montée de la Grande-Côte", "Traboule des Voraces", "Quai Saint-Antoine",
    "Venelle du Puits", "Hent ar Mor", "Ru du Moulin",
    "Cavée Saint-Gilles", "Côte des Deux Amants",
    "Carriera Nòstra Dama", "Cami de la Ribera", "Androne des Frères",
    "Ravine des Cabris", "Morne à l'Eau", "Habitation Beauséjour",
    "Îlet à Cochons", "Section Malecon",
    "Corniche du Pharo", "Digue des Alliés", "Front de Mer Sud",
]
