#!/usr/bin/env python3
"""Compose the images Google Play asks for, from the screenshots already taken.

Play refuses a screenshot whose long side is more than twice its short one, and
a telephone's screen is taller than that — the Fairphone 5 these were taken on
is 1224x2700, or 2.21:1. Cropping would settle it by cutting the attribution
line off the map, so the screenshot is laid whole on a coloured card instead,
which is what a Play listing shows anyway: a caption saying what one is looking
at, above the screen it describes.

F-Droid is not served from here. It shows `fastlane/metadata/.../images/`
untouched, as a repository of free software should — the raw screen, no
marketing around it — and this script never writes there.

Everything it draws comes from the application itself: the colour tokens of
`res/values/colors.xml` and Atkinson Hyperlegible, which is the typeface the
interface reads in. Bricolage, which the application uses for its figures, is
shipped subsetted to the nineteen characters those figures need and has no
letters to write a caption with.

    python3 tools/make_play_assets.py [--out DIR]
"""

from __future__ import annotations

import argparse
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

# One caption per screenshot, in the order the files are numbered. They say
# what the screen does, not what it is called: somebody reading the listing has
# never seen the application's vocabulary.
CAPTIONS = {
    "en-US": [
        "Every station, and how many bikes are standing there",
        "What a station holds, and how old the figure is",
        "Door to door: walk, ride, walk",
    ],
    "fr": [
        "Toutes les stations, et les vélos qui y sont",
        "Ce que tient une station, et l’âge du chiffre",
        "Porte à porte : marche, vélo, marche",
    ],
}

TAGLINE = {
    "en-US": "Bike sharing, offline, with no tracker",
    "fr": "Vélos en libre-service, hors ligne, sans mouchard",
}


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT_DIR / name), size)


def wrap(draw: ImageDraw.ImageDraw, text: str, typeface, width: int) -> list[str]:
    """Break a caption on spaces so that no line passes `width` pixels."""
    lines: list[str] = []
    current = ""
    for word in text.split():
        candidate = f"{current} {word}".strip()
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


def compose_screenshot(source: Path, caption: str) -> Image.Image:
    """Lay one screenshot, whole, on a captioned card of 1080x1920."""
    card_w, card_h = 1080, 1920
    card = Image.new("RGB", (card_w, card_h), BACKDROP)
    draw = ImageDraw.Draw(card)

    title = font("atkinson_bold.ttf", 50)
    margin = 72
    lines = wrap(draw, caption, title, card_w - 2 * margin)
    y = 84
    for line in lines:
        draw.text((margin, y), line, font=title, fill=PAPER)
        y += 64

    # The screenshot keeps its own proportions and is sized by the height that
    # is left: nothing of the screen is cut, which is the whole point of the
    # card.
    shot = Image.open(source).convert("RGBA")
    top = y + 56
    available = card_h - top - 72
    scale = available / shot.height
    shot = shot.resize((round(shot.width * scale), available), Image.LANCZOS)
    shot = rounded(shot, 28)

    card.paste(shot, ((card_w - shot.width) // 2, top), shot)
    return card


def compose_feature_graphic(language: str) -> Image.Image:
    """The 1024x500 banner, which Play requires and crops on some surfaces.

    Nothing is written near an edge for that reason, and it carries no
    screenshot: at this size a screen of a map reads as a smear.
    """
    width, height = 1024, 500
    # One ground, and it is the dark one: the icon carries the signal green on
    # its own tile, and that green only reads as the application's colour if
    # nothing else on the image is wearing it.
    banner = Image.new("RGB", (width, height), BACKDROP)
    draw = ImageDraw.Draw(banner)

    icon = Image.open(METADATA / "en-US/images/icon.png").convert("RGBA")
    icon = icon.resize((248, 248), Image.LANCZOS)
    banner.paste(icon, (96, (height - icon.height) // 2), icon)

    name = font("atkinson_bold.ttf", 76)
    tagline = font("atkinson_regular.ttf", 32)
    draw.text((408, 178), "Roue Libre", font=name, fill=PAPER)
    draw.text((412, 282), TAGLINE[language], font=tagline, fill="#9CC4B6")
    return banner


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out",
        type=Path,
        default=ROOT / "do-not-commit/play-store",
        help="where the composed images are written",
    )
    args = parser.parse_args()

    for language, captions in CAPTIONS.items():
        out = args.out / language
        out.mkdir(parents=True, exist_ok=True)

        source_dir = METADATA / language / "images/phoneScreenshots"
        for index, caption in enumerate(captions, start=1):
            source = source_dir / f"{index}.png"
            if not source.exists():
                print(f"missing {source}")
                continue
            card = compose_screenshot(source, caption)
            target = out / f"{index}.png"
            card.save(target)
            print(f"{target}  {card.width}x{card.height}")

        banner = compose_feature_graphic(language)
        target = out / "featureGraphic.png"
        banner.save(target)
        print(f"{target}  {banner.width}x{banner.height}")


if __name__ == "__main__":
    main()
