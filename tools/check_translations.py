#!/usr/bin/env python3
"""Check a translation against the English source it was copied from (SPEC.md §9).

`res/values/strings.xml` carries no language qualifier and is what Android
serves when nothing else matches, so it is the one file that is always
complete. Every `values-<language>/strings.xml` is measured against it here.

The checks are the ones a reader cannot make for themselves and a reviewer
cannot make reliably by eye. Three of them fail in ways that are invisible
until the string is shown on a phone:

  · **a placeholder that changed number or kind.** `%1$s` written `%1$d` in a
    translation throws at format time, and only on the screen that shows it;
    a `%2$s` dropped silently loses whatever it stood for.
  · **plural categories that are not the language's.** English needs `one` and
    `other`; Arabic needs six, Polish four, Japanese one. Copying the English
    pair into a Polish file makes Android fall back on `other` for two, three
    and four, which reads as broken Polish rather than as a missing string.
    The table below is CLDR's, and `./gradlew lint` checks the same thing from
    its own copy — the two agreeing is what makes either trustworthy.
  · **a string still holding its English text.** The twenty-nine started files
    are the English text to begin with (CONTRIBUTING.md), so "translated" and
    "not started" are told apart by nothing but this comparison.

That last one is the only check that cannot decide on its own. "Stations" is
the French for "Stations" and "Version" is the German for "Version": a string
coming back identical is sometimes the right answer and sometimes a line
nobody reached, and no amount of code tells the two apart. So it is reported
as a **warning**, for a reader to confirm — unless it is most of the file, in
which case the file is still the English starting point and that is an error.

Run it over every language, or over the ones named:

    python3 tools/check_translations.py            # all of them
    python3 tools/check_translations.py de es ja   # these three
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

RESOURCES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"

# The plural categories each language actually distinguishes, from CLDR's
# cardinal rules. A category CLDR does not define for a language is not
# "extra": Android never resolves it, so the text written in it is simply
# never read, and `lint` reports it as UnusedQuantity.
#
# `many` reaches French, Spanish, Italian, Portuguese and Catalan only at a
# million and beyond, which no count in this application ever reaches. It is
# still required: what a language distinguishes is a fact about the language,
# and a file that leaves it out is one line away from being wrong the day a
# figure grows. `values-fr/` writes all three.
PLURAL_CATEGORIES = {
    "en": {"one", "other"},
    "ar": {"zero", "one", "two", "few", "many", "other"},
    "bs": {"one", "few", "other"},
    "ca": {"one", "many", "other"},
    "cs": {"one", "few", "many", "other"},
    "da": {"one", "other"},
    "de": {"one", "other"},
    "el": {"one", "other"},
    "es": {"one", "many", "other"},
    "eu": {"one", "other"},
    "fi": {"one", "other"},
    "fr": {"one", "many", "other"},
    "gl": {"one", "other"},
    "hr": {"one", "few", "other"},
    "hu": {"one", "other"},
    "it": {"one", "many", "other"},
    "ja": {"other"},
    "lt": {"one", "few", "many", "other"},
    "lv": {"zero", "one", "other"},
    "nb": {"one", "other"},
    "nl": {"one", "other"},
    "pl": {"one", "few", "many", "other"},
    "pt": {"one", "many", "other"},
    "ro": {"one", "few", "other"},
    "sk": {"one", "few", "many", "other"},
    "sl": {"one", "two", "few", "other"},
    "sq": {"one", "other"},
    "sr": {"one", "few", "other"},
    "sv": {"one", "other"},
    "tr": {"one", "other"},
    "zh": {"other"},
}

# Strings that are the same in every language by construction, and are not
# worth a warning anywhere: the application's own name, and the two places it
# is written out.
KEPT_AS_IS = {"app_name", "welcome_hello_title"}

# Below this many letters, once the placeholders are taken out, a string is a
# format or a unit symbol rather than a sentence — "%1$s km", "ft · mi",
# "%1$d h %2$02d" — and is expected to survive translation unchanged.
LETTERS_WORTH_TRANSLATING = 5

# The share of strings that may legitimately come back identical before the
# file is read as untranslated rather than as coincidentally alike. A real
# translation lands far below it; a started file sits at 100%.
UNTRANSLATED_SHARE = 0.15

# A format specifier, whole: an optional argument index, then the flags, width
# and precision Java allows between the index and the conversion letter.
# `%2$02d` has to match — it is written that way in `duration_hours_minutes`,
# and a pattern stopping at the index reads straight past it, leaving that
# placeholder unchecked in every language.
PLACEHOLDER = re.compile(r"%%|%(\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])")


def read(path: Path) -> tuple[dict[str, str], dict[str, dict[str, str]]]:
    """The strings and the plurals a resource file holds, by name."""
    root = ElementTree.parse(path).getroot()
    strings = {
        element.get("name"): "".join(element.itertext())
        for element in root.findall("string")
    }
    plurals = {
        element.get("name"): {
            item.get("quantity"): "".join(item.itertext())
            for item in element.findall("item")
        }
        for element in root.findall("plurals")
    }
    return strings, plurals


def worth_translating(english: str) -> bool:
    """Whether a string identical to its English source deserves a second look.

    What is left once the placeholders are removed decides it: a unit symbol
    and a format string carry almost no letters and come out the same in every
    language, where a sentence does not.
    """
    letters = [character for character in PLACEHOLDER.sub("", english) if character.isalpha()]
    return len(letters) >= LETTERS_WORTH_TRANSLATING


def placeholders(text: str) -> list[str]:
    """The format specifiers of a string, as index and conversion alone.

    Compared as a sorted list rather than as a set: a translation may reorder
    them — that is what positional placeholders are for — but it may not drop
    one, add one, or turn a `%1$s` into a `%1$d`.

    **The padding is deliberately not compared.** English writes "1 h 05" and
    pads its minutes `%2$02d`; a language that writes its unit in full writes
    "1時間5分" and must not. Which figure is padded is a fact about how the
    language writes a duration, where the argument it reads and the type it
    reads it as are facts about the code calling it — and only those two can
    be got wrong without anyone noticing until the screen throws.
    """
    return sorted(
        "%%" if match.group(0) == "%%" else f"{match.group(1) or ''}{match.group(2)}"
        for match in PLACEHOLDER.finditer(text)
    )


def check(language: str) -> tuple[list[str], list[str]]:
    """What is wrong with one language's file, and what wants confirming."""
    source_strings, source_plurals = read(RESOURCES / "values" / "strings.xml")
    path = RESOURCES / f"values-{language}" / "strings.xml"
    if not path.exists():
        return [f"{language}: {path} does not exist"], []
    if language not in PLURAL_CATEGORIES:
        return [f"{language}: no plural categories recorded — add them from CLDR"], []

    try:
        strings, plurals = read(path)
    except ElementTree.ParseError as malformed:
        return [f"{language}: the file does not parse — {malformed}"], []

    errors: list[str] = []
    identical: list[str] = []
    expected = PLURAL_CATEGORIES[language]

    missing = sorted(set(source_strings) - set(strings))
    extra = sorted(set(strings) - set(source_strings))
    if missing:
        errors.append(f"strings absent from the file: {', '.join(missing)}")
    if extra:
        errors.append(f"strings the English file does not have: {', '.join(extra)}")

    for name, english in source_strings.items():
        translated = strings.get(name)
        if translated is None:
            continue
        if placeholders(english) != placeholders(translated):
            errors.append(
                f"{name}: placeholders changed — English has "
                f"{placeholders(english)}, this file has {placeholders(translated)}",
            )
        if translated == english and name not in KEPT_AS_IS and worth_translating(english):
            identical.append(f"{name}: identical to the English — {english!r}")

    missing_plurals = sorted(set(source_plurals) - set(plurals))
    extra_plurals = sorted(set(plurals) - set(source_plurals))
    if missing_plurals:
        errors.append(f"plurals absent from the file: {', '.join(missing_plurals)}")
    if extra_plurals:
        errors.append(f"plurals the English file does not have: {', '.join(extra_plurals)}")

    for name, english_items in source_plurals.items():
        translated_items = plurals.get(name)
        if translated_items is None:
            continue
        written = set(translated_items)
        if written != expected:
            errors.append(
                f"{name}: plural categories are {sorted(written)}, "
                f"{language} needs {sorted(expected)}",
            )
        # Every category has to carry the placeholders of the English `other`,
        # which is the form English always writes.
        reference = placeholders(english_items.get("other", ""))
        for quantity, text in sorted(translated_items.items()):
            if placeholders(text) != reference:
                errors.append(
                    f"{name}[{quantity}]: placeholders changed — "
                    f"expected {reference}, found {placeholders(text)}",
                )
        if translated_items.get("other") == english_items.get("other"):
            identical.append(f"{name}: identical to the English")

    translatable = sum(
        1 for name, english in source_strings.items()
        if name not in KEPT_AS_IS and worth_translating(english)
    ) + len(source_plurals)
    if translatable and len(identical) > UNTRANSLATED_SHARE * translatable:
        share = round(100 * len(identical) / translatable)
        errors.append(
            f"{len(identical)} of {translatable} strings still hold their English "
            f"text ({share}%) — this file is the starting point, not a translation",
        )
        identical = []

    return (
        [f"{language}: {error}" for error in errors],
        [f"{language}: {warning}" for warning in identical],
    )


def languages_present() -> list[str]:
    """Every language a `values-<language>/strings.xml` exists for."""
    return sorted(
        folder.name[len("values-"):]
        for folder in RESOURCES.glob("values-*")
        if (folder / "strings.xml").exists()
    )


def main() -> int:
    asked = sys.argv[1:] or languages_present()
    failed = []
    for language in asked:
        errors, warnings = check(language)
        if errors:
            failed.append(language)
            print("\n".join(errors))
        elif warnings:
            print(f"{language}: consistent, {len(warnings)} string(s) to confirm")
            print("\n".join(f"  {warning}" for warning in warnings))
        else:
            print(f"{language}: complete and consistent")
    if failed:
        print(
            f"\n{len(failed)} language(s) out of {len(asked)}: {', '.join(failed)}",
            file=sys.stderr,
        )
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
