#!/usr/bin/env node
/*
 * Produces the base map's text glyphs — SPEC.md §4.2.
 *
 * MapLibre does not draw labels with a system font: it needs glyphs
 * precomputed as signed distance fields, cut into ranges of 256 characters.
 * Without them the map draws but stays mute — no street names, no municipality
 * names.
 *
 * They are EMBEDDED IN THE APK, never downloaded: a style that fetches its
 * glyphs from a font server would send a request out on every pan of the map,
 * which constraint C3 of SPEC §2 rules out.
 *
 * The fonts are the application's own. The map and the interface then speak
 * the same typographic language, and no extra family is embedded for the map
 * alone.
 *
 * Usage:
 *   node tools/build_glyphs.js [output directory]
 */

const fs = require('fs');
const path = require('path');
const fontnik = require('fontnik');

const REPO_ROOT = path.resolve(__dirname, '..');
const FONT_DIRECTORY = path.join(REPO_ROOT, 'app/src/main/res/font');
const DEFAULT_OUTPUT = path.join(REPO_ROOT, 'app/src/main/assets/glyphs');

/*
 * Every range of the Basic Multilingual Plane, and that is not a precaution.
 *
 * A missing range does NOT merely leave a character blank, as this script long
 * assumed: MapLibre answers "Could not read asset", the tile whose label needed
 * it never finishes its layout, and NOTHING of that tile is drawn — not the
 * streets, not the water, not the parks. Hunedoara showed it plainly: a town
 * whose streets are named "Piața Gării" has its Ș and Ț in 512–767, which was
 * not shipped, and its map came up empty over a perfectly good tile set.
 *
 * The cost is small, because fontnik answers for a range the font does not
 * cover with a valid empty file of forty-four bytes. Only the ranges the font
 * really carries weigh anything — Latin, its extensions and the punctuation —
 * and the two hundred and fifty others amount to eleven kilobytes together.
 * That is what buys a base map that cannot be blanked by a place name, in
 * Japanese as in Romanian: what the font cannot draw stays blank, which is
 * what a missing character was always supposed to cost.
 *
 * MapLibre never asks beyond the BMP: a name written in an astral plane —
 * emoji do turn up in OpenStreetMap — is dropped from the label rather than
 * requested.
 */
const BMP_RANGE_COUNT = 256;
const TEXT_RANGES = Array.from({ length: BMP_RANGE_COUNT }, (_, index) => [
  index * 256,
  index * 256 + 255,
]);

/*
 * The station markers show digits only, all in the first range. Embedding the
 * accents and punctuation of a font used for nothing else would cost fifty
 * kilobytes for nothing.
 */
const DIGIT_RANGES = [[0, 255]];

/*
 * The stack names must match the style's `text-font` exactly. MapLibre then
 * asks for `glyphs/<name>/<start>-<end>.pbf`.
 *
 * Bricolage carries the markers' digits, as it carries the indicator's in the
 * list: the signature element must look the same from one screen to the
 * next.
 */
const FONT_STACKS = [
  {
    file: 'atkinson_regular.ttf',
    stack: 'Atkinson Hyperlegible Regular',
    ranges: TEXT_RANGES,
  },
  {
    file: 'atkinson_bold.ttf',
    stack: 'Atkinson Hyperlegible Bold',
    ranges: TEXT_RANGES,
  },
  {
    file: 'bricolage_bold.ttf',
    stack: 'Bricolage Grotesque Bold',
    ranges: DIGIT_RANGES,
  },
];

async function main() {
  const outputDirectory = process.argv[2]
    ? path.resolve(process.argv[2])
    : DEFAULT_OUTPUT;

  for (const { file } of FONT_STACKS) {
    const fontPath = path.join(FONT_DIRECTORY, file);
    if (!fs.existsSync(fontPath)) {
      console.error(`Font not found: ${fontPath}`);
      process.exit(1);
    }
  }

  fs.rmSync(outputDirectory, { recursive: true, force: true });

  for (const { file, stack, ranges } of FONT_STACKS) {
    const buffer = fs.readFileSync(path.join(FONT_DIRECTORY, file));
    const stackDirectory = path.join(outputDirectory, stack);
    fs.mkdirSync(stackDirectory, { recursive: true });

    for (const [start, end] of ranges) {
      const data = await new Promise((resolve, reject) => {
        fontnik.range({ font: buffer, start, end }, (error, result) => {
          if (error) reject(error);
          else resolve(result);
        });
      });
      const target = path.join(stackDirectory, `${start}-${end}.pbf`);
      fs.writeFileSync(target, data);
      console.log(
        `  ${stack} ${start}-${end}: ${(data.length / 1024).toFixed(1)} kB`,
      );
    }
  }

  const totalBytes = FONT_STACKS.flatMap(({ stack, ranges }) =>
    ranges.map(([start, end]) =>
      fs.statSync(path.join(outputDirectory, stack, `${start}-${end}.pbf`)).size,
    ),
  ).reduce((sum, size) => sum + size, 0);

  console.log(`\nGlyphs written to ${outputDirectory}`);
  console.log(`Total: ${(totalBytes / 1024).toFixed(0)} kB embedded in the APK`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
