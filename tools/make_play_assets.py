#!/usr/bin/env python3
"""Compose the images Google Play asks for, from the screenshots already taken.

Play refuses a screenshot whose long side is more than twice its short one, and
a telephone's screen is at that limit or past it — the Fairphone 3 these are
taken on is 1080x2160, exactly 2:1, the Fairphone 5 2.21:1. Cropping would
settle it by cutting the attribution line off the map, so the screenshot is
laid whole, and alone, on a plain card of 1080x1920 instead: no caption, no
text of any kind, only the screen, centred on the application's green.

F-Droid is not served from here. It shows `fastlane/metadata/.../images/`
untouched, as a repository of free software should — the raw screen, no
marketing around it — and this script never writes there.

The screenshots are read from a directory of one folder per language, each
holding `1.png`, `2.png`… By default that is `fastlane/metadata/android/`,
which carries English and French; `--source` names a directory of the same
shape, `<language>/<n>.png`, which is how the full set of languages taken for
Play is composed without committing thirty-one copies of the same screens.

The feature banner is drawn for every language. Its top third is a band of
the map with its stations, `tools/assets/map-band.png`, cut from a landscape
capture of the application over central Paris at the zoom where every station
is a green disc of its own; under it the icon, the name and
the listing's own short description (`fastlane/metadata/.../short_description.txt`,
so the banner says what the listing says); and along the bottom how many
cities and countries the catalogue serves, read from `config/catalogue.json`
so the figure never goes stale, rounded down to the nearest fifty.

The interface's typeface, Atkinson Hyperlegible, writes Latin only. Greek,
Cyrillic, Arabic and the ideographic scripts fall back on fonts the system
provides (Noto Sans, Noto Sans Arabic, Droid Sans Fallback, at the Debian
paths below); the script says which file it misses rather than drawing boxes.

    python3 tools/make_play_assets.py [--source DIR] [--out DIR]
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "app/src/main/res/font"
METADATA = ROOT / "fastlane/metadata/android"

# The colour tokens of `res/values/colors.xml`, by the names they carry there.
SIGNAL = "#0F6E56"
PAPER = "#E7ECE9"
INK = "#0F1714"

# The card is darker than the signal hue so that the screenshot laid on it,
# which is mostly paper-coloured, reads as the lit thing on the image.
BACKDROP = "#0A4A3A"

# Play's own limits for a telephone screenshot: a 9:16 card, well inside the
# 320–3840 px it accepts on each side.
CARD_WIDTH, CARD_HEIGHT = 1080, 1920

# What is left bare above and below the screen on the card.
CARD_MARGIN = 72

# The line along the bottom of the banner, in every language the listing has.
# `{cities}` and `{countries}` are filled from the catalogue; each template was
# written for a count of cities in the hundreds and of countries in the
# thirties to fifties, which is where the grammar of the number-bound forms
# (Slavic genitives, Romanian "de") holds.
AVAILABILITY = {
    "en-US": "Available in more than {cities} cities in {countries} countries",
    "fr": "Disponible dans plus de {cities} villes dans {countries} pays",
    "ar": "متاح في أكثر من {cities} مدينة في {countries} دولة",
    "bs": "Dostupno u više od {cities} gradova u {countries} zemalja",
    "ca": "Disponible a més de {cities} ciutats de {countries} països",
    "cs": "K dispozici ve více než {cities} městech ve {countries} zemích",
    "da": "Tilgængelig i over {cities} byer i {countries} lande",
    "de": "In über {cities} Städten in {countries} Ländern verfügbar",
    "el": "Διαθέσιμη σε πάνω από {cities} πόλεις σε {countries} χώρες",
    "es": "Disponible en más de {cities} ciudades de {countries} países",
    "eu": "{countries} herrialdetako {cities} hiri baino gehiagotan erabilgarri",
    "fi": "Saatavilla yli {cities} kaupungissa {countries} maassa",
    "gl": "Dispoñible en máis de {cities} cidades de {countries} países",
    "hr": "Dostupno u više od {cities} gradova u {countries} zemalja",
    "hu": "Több mint {cities} városban, {countries} országban elérhető",
    "it": "Disponibile in oltre {cities} città in {countries} paesi",
    "ja": "{countries}か国・{cities}以上の都市で利用可能",
    "lt": "Prieinama daugiau nei {cities} miestų {countries} šalyse",
    "lv": "Pieejama vairāk nekā {cities} pilsētās {countries} valstīs",
    "nb": "Tilgjengelig i over {cities} byer i {countries} land",
    "nl": "Beschikbaar in meer dan {cities} steden in {countries} landen",
    "pl": "Dostępna w ponad {cities} miastach w {countries} krajach",
    "pt": "Disponível em mais de {cities} cidades em {countries} países",
    "ro": "Disponibil în peste {cities} de orașe din {countries} de țări",
    "sk": "Dostupné vo viac ako {cities} mestách v {countries} krajinách",
    "sl": "Na voljo v več kot {cities} mestih v {countries} državah",
    "sq": "I disponueshëm në mbi {cities} qytete të {countries} shteteve",
    "sr": "Доступно у више од {cities} градова у {countries} земаља",
    "sv": "Finns i över {cities} städer i {countries} länder",
    "tr": "{countries} ülkede {cities}’den fazla şehirde kullanılabilir",
    "zh": "{countries}\u00a0个国家、{cities}\u00a0多座城市可用",
}

# Languages written right to left: the banner is mirrored for them.
RIGHT_TO_LEFT = {"ar"}

# What the system provides for the scripts Atkinson Hyperlegible does not
# write. Latin stays in the application's own typeface.
SYSTEM_FONTS = Path("/usr/share/fonts/truetype")
FALLBACK_FONTS = {
    "ar": (SYSTEM_FONTS / "noto/NotoSansArabic-Regular.ttf", SYSTEM_FONTS / "noto/NotoSansArabic-Bold.ttf"),
    "el": (SYSTEM_FONTS / "noto/NotoSans-Regular.ttf", SYSTEM_FONTS / "noto/NotoSans-Bold.ttf"),
    "sr": (SYSTEM_FONTS / "noto/NotoSans-Regular.ttf", SYSTEM_FONTS / "noto/NotoSans-Bold.ttf"),
    "ja": (SYSTEM_FONTS / "droid/DroidSansFallbackFull.ttf",) * 2,
    "zh": (SYSTEM_FONTS / "droid/DroidSansFallbackFull.ttf",) * 2,
}

MAP_BAND = ROOT / "tools/assets/map-band.png"

# The reach line is framed by two emoji, a bike where the sentence starts and
# the globe where it ends, so that it reads at a glance in every script. They
# are drawn from the system's colour emoji font, which only renders at 109 px:
# each is drawn there and scaled down to the line's height.
EMOJI_FONT = SYSTEM_FONTS / "noto/NotoColorEmoji.ttf"
REACH_EMOJI = ("🚲", "🌍")
CATALOGUE = ROOT / "config/catalogue.json"


def font(name: str | Path, size: int) -> ImageFont.FreeTypeFont:
    path = name if isinstance(name, Path) else FONT_DIR / name
    if not path.exists():
        raise SystemExit(f"missing font {path}: install it or point FALLBACK_FONTS elsewhere")
    return ImageFont.truetype(str(path), size)


def text_fonts(language: str) -> tuple[Path | str, Path | str]:
    """The regular and bold faces that can write this language's text."""
    if language in FALLBACK_FONTS:
        return FALLBACK_FONTS[language]
    return "atkinson_regular.ttf", "atkinson_bold.ttf"


def runs_of(text: str, typeface) -> list[tuple[str, ImageFont.FreeTypeFont]]:
    """The text cut into runs, each with the face that writes it.

    The ideographic fallback writes no Latin letter and no digit, so in a
    language set in it each run of ASCII goes to Atkinson; every other face
    writes its whole line itself.
    """
    if typeface.path != str(FALLBACK_FONTS["ja"][0]):
        return [(text, typeface)]
    latin = font("atkinson_regular.ttf", typeface.size)
    runs: list[tuple[str, ImageFont.FreeTypeFont]] = []
    for char in text:
        face = latin if char.isascii() else typeface
        if runs and runs[-1][1] is face:
            runs[-1] = (runs[-1][0] + char, face)
        else:
            runs.append((char, face))
    return runs


def measure(draw: ImageDraw.ImageDraw, text: str, typeface, direction=None) -> int:
    """How wide `write` draws `text`."""
    return round(sum(draw.textlength(run, font=face, direction=direction) for run, face in runs_of(text, typeface)))


def write(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str, typeface, **style) -> None:
    """Draws `text`, handing its Latin runs to the interface's typeface.

    The ideographic fallback writes no Latin letter and no digit, and a
    banner that says "350 cities" in boxes says nothing: so in a language set
    in it, each run of ASCII is written in Atkinson instead, run after run
    along the line. Those languages are written left to right, which is what
    lets the runs be laid end to end.
    """
    if typeface.path != str(FALLBACK_FONTS["ja"][0]) or style.get("direction") == "rtl":
        draw.text(xy, text, font=typeface, **style)
        return
    latin = font("atkinson_regular.ttf", typeface.size)
    x, y = xy
    runs: list[tuple[bool, str]] = []
    for char in text:
        if runs and runs[-1][0] == char.isascii():
            runs[-1] = (runs[-1][0], runs[-1][1] + char)
        else:
            runs.append((char.isascii(), char))
    for is_latin, run in runs:
        face = latin if is_latin else typeface
        draw.text((x, y), run, font=face, **style)
        x += draw.textlength(run, font=face)


def wrap(draw: ImageDraw.ImageDraw, text: str, typeface, width: int) -> list[str]:
    """Break a line on spaces so that no line passes `width` pixels."""
    lines: list[str] = []
    current = ""
    # Japanese and Chinese write no spaces: they break between any two
    # characters, so each character is a word of its own there.
    words = text.split() if " " in text.strip() else list(text)
    joint = " " if " " in text.strip() else ""
    for word in words:
        candidate = f"{current}{joint}{word}".strip()
        if draw.textlength(candidate, font=typeface) <= width or not current:
            current = candidate
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def rounded(image: Image.Image, radius: int) -> Image.Image:
    """Round the corners of a screenshot, as the telephone's own screen does."""
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *image.size), radius=radius, fill=255)
    out = Image.new("RGBA", image.size)
    out.paste(image, mask=mask)
    return out


def compose_screenshot(source: Path) -> Image.Image:
    """Lay one screenshot, whole and centred, on a plain card of 1080x1920."""
    card = Image.new("RGB", (CARD_WIDTH, CARD_HEIGHT), BACKDROP)

    # The screenshot keeps its own proportions and is sized by the height that
    # is left between the margins: nothing of the screen is cut, which is the
    # whole point of the card.
    shot = Image.open(source).convert("RGBA")
    available = CARD_HEIGHT - 2 * CARD_MARGIN
    scale = available / shot.height
    shot = shot.resize((round(shot.width * scale), available), Image.LANCZOS)
    shot = rounded(shot, 28)

    card.paste(shot, ((CARD_WIDTH - shot.width) // 2, CARD_MARGIN), shot)
    return card


def emoji(character: str, height: int) -> Image.Image:
    """One colour emoji, scaled to `height` pixels."""
    canvas = Image.new("RGBA", (160, 160), (0, 0, 0, 0))
    ImageDraw.Draw(canvas).text((0, 0), character, font=font(EMOJI_FONT, 109), embedded_color=True)
    glyph = canvas.crop(canvas.getbbox())
    return glyph.resize((round(glyph.width * height / glyph.height), height), Image.LANCZOS)


def catalogue_figures() -> tuple[int, int]:
    """How many cities and countries the catalogue serves, cities rounded
    down to the nearest fifty so the line can say "more than"."""
    document = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    cities = document["cities"]
    return len(cities) // 50 * 50, len({city["country"] for city in cities})


def short_description(language: str) -> str:
    """The listing's short description, which the banner repeats as its
    tagline, shorn of the full stop a sentence carries and a tagline does not."""
    text = (METADATA / language / "short_description.txt").read_text(encoding="utf-8").strip()
    return text.rstrip(".。")


def map_band(width: int, height: int, fade: int) -> Image.Image:
    """The strip of map across the top, melting into the card over `fade` px."""
    band = Image.open(MAP_BAND).convert("RGBA").resize((width, height), Image.LANCZOS)
    mask = Image.new("L", band.size, 255)
    draw = ImageDraw.Draw(mask)
    for row in range(fade):
        draw.line((0, height - fade + row, width, height - fade + row), fill=255 - 255 * row // fade)
    band.putalpha(mask)
    return band


def compose_feature_graphic(language: str) -> Image.Image:
    """The 1024x500 banner, which Play requires and crops on some surfaces.

    Nothing is written near an edge for that reason. The top third is the map,
    the middle the icon, the name and the listing's short description, the
    bottom line the reach of the catalogue.
    """
    width, height = 1024, 500
    margin = 64
    rtl = language in RIGHT_TO_LEFT
    regular_face, bold_face = text_fonts(language)

    banner = Image.new("RGB", (width, height), BACKDROP)
    band_height = height // 3
    band = map_band(width, band_height, fade=28)
    banner.paste(band, (0, 0), band)
    draw = ImageDraw.Draw(banner)

    icon = Image.open(METADATA / "en-US/images/icon.png").convert("RGBA")
    icon = icon.resize((168, 168), Image.LANCZOS)
    icon_top = band_height + 32
    banner.paste(icon, (width - margin - icon.width if rtl else margin, icon_top), icon)

    # The text column starts after the icon; mirrored, it ends before it.
    column_left = margin + icon.width + 40
    column_width = width - column_left - margin
    anchor_x = width - column_left if rtl else column_left
    anchor = "ra" if rtl else "la"
    direction = "rtl" if rtl else None

    # The name is Latin in every language, and set in the interface's own face.
    draw.text((anchor_x, icon_top - 4), "Roue Libre", font=font("atkinson_bold.ttf", 60), fill=PAPER, anchor=anchor)

    # The description is a sentence in whatever language the listing is
    # written in, and one that fits in English overruns in most others, so it
    # is wrapped to the column rather than drawn past the edge.
    tagline = font(regular_face, 27)
    y = icon_top + 76
    for line in wrap(draw, short_description(language), tagline, column_width)[:3]:
        write(draw, (anchor_x, y), line, tagline, fill="#9CC4B6", anchor=anchor, direction=direction)
        y += 35

    cities, countries = catalogue_figures()
    reach = AVAILABILITY[language].format(cities=cities, countries=countries)
    # The bike opens the line and the globe closes it, on whichever side the
    # language starts: mirrored with the rest of the banner for Arabic.
    first, last = (emoji(character, 30) for character in REACH_EMOJI)
    gap = 12
    baseline_top = height - margin + 4
    # The longest wordings (Basque, Greek) overrun the column at 25 px; the
    # line is set a size smaller until it fits, rather than run into the edge
    # Play crops.
    for size in range(25, 18, -1):
        reach_face = font(regular_face, size)
        text_width = measure(draw, reach, reach_face, direction)
        if text_width + first.width + last.width + 2 * gap <= column_width:
            break
    if rtl:
        banner.paste(first, (anchor_x - first.width, baseline_top), first)
        text_x = anchor_x - first.width - gap
        banner.paste(last, (text_x - text_width - gap - last.width, baseline_top), last)
    else:
        banner.paste(first, (anchor_x, baseline_top), first)
        text_x = anchor_x + first.width + gap
        banner.paste(last, (text_x + text_width + gap, baseline_top), last)
    write(draw, (text_x, height - margin + 8), reach, reach_face, fill=PAPER, anchor=anchor, direction=direction)
    return banner


def screenshot_folders(source: Path) -> dict[str, Path]:
    """The folder holding each language's screenshots, by language.

    Fastlane keeps them under `<language>/images/phoneScreenshots/`; a plain
    directory of captures keeps them straight under `<language>/`.
    """
    folders = {}
    for entry in sorted(source.iterdir()):
        if not entry.is_dir():
            continue
        nested = entry / "images/phoneScreenshots"
        folder = nested if nested.is_dir() else entry
        if any(folder.glob("[0-9]*.png")):
            folders[entry.name] = folder
    return folders


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source",
        type=Path,
        default=METADATA,
        help="a directory of <language>/<n>.png screenshots (default: fastlane's)",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=ROOT / "do-not-commit/play-store",
        help="where the composed images are written",
    )
    args = parser.parse_args()

    for language, folder in screenshot_folders(args.source).items():
        out = args.out / language
        out.mkdir(parents=True, exist_ok=True)

        for source in sorted(folder.glob("[0-9]*.png"), key=lambda p: int(p.stem)):
            card = compose_screenshot(source)
            target = out / source.name
            card.save(target)
            print(f"{target}  {card.width}x{card.height}")

        banner = compose_feature_graphic(language)
        target = out / "featureGraphic.png"
        banner.save(target)
        print(f"{target}  {banner.width}x{banner.height}")


if __name__ == "__main__":
    main()
