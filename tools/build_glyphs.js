#!/usr/bin/env node
/*
 * Produit les glyphes de texte du fond de carte — SPEC.md §4.2.
 *
 * MapLibre ne dessine pas les étiquettes avec une police système : il lui faut
 * des glyphes précalculés en champ de distance signée, découpés en plages de
 * 256 caractères. Sans eux, la carte s'affiche mais reste muette — ni noms de
 * rues, ni noms de communes.
 *
 * Ils sont EMBARQUÉS DANS L'APK, jamais téléchargés : un style qui va chercher
 * ses glyphes sur un serveur de polices ferait sortir une requête à chaque
 * déplacement de la carte, ce que la contrainte C3 du SPEC §2 exclut.
 *
 * Les polices sont celles de l'application. La carte et l'interface parlent
 * ainsi la même langue typographique, et l'on n'embarque pas une famille de
 * plus pour le seul usage de la carte.
 *
 * Usage :
 *   node tools/build_glyphs.js [répertoire de sortie]
 */

const fs = require('fs');
const path = require('path');
const fontnik = require('fontnik');

const REPO_ROOT = path.resolve(__dirname, '..');
const FONT_DIRECTORY = path.join(REPO_ROOT, 'app/src/main/res/font');
const DEFAULT_OUTPUT = path.join(REPO_ROOT, 'app/src/main/assets/glyphs');

/*
 * Les plages retenues, et pourquoi celles-là seulement.
 *
 *   0–255      latin de base et supplément latin-1 : tout le français courant,
 *              accents et guillemets « » compris.
 *   256–511    latin étendu A : le œ de « Cœur », le ł et le ż des noms
 *              d'origine polonaise, nombreux dans le bassin minier du Nord.
 *   8192–8447  ponctuation générale : l'apostrophe typographique ’, que la
 *              Base Adresse Nationale et OpenStreetMap emploient tous deux.
 *
 * Chaque plage supplémentaire pèse quelques dizaines de kilooctets par
 * graisse. Les caractères absents s'affichent en blanc : mieux vaut vérifier
 * qu'ajouter par précaution.
 */
const TEXT_RANGES = [
  [0, 255],
  [256, 511],
  [8192, 8447],
];

/*
 * Les marqueurs de stations n'affichent que des chiffres, tous dans la
 * première plage. Embarquer les accents et la ponctuation d'une police qui ne
 * sert qu'à cela coûterait cinquante kilooctets pour rien.
 */
const DIGIT_RANGES = [[0, 255]];

/*
 * Les noms de pile doivent correspondre exactement au `text-font` du style.
 * MapLibre demande alors `glyphs/<nom>/<début>-<fin>.pbf`.
 *
 * Bricolage porte les chiffres des marqueurs, comme il porte ceux de
 * l'indicateur dans la liste : l'élément signature doit se ressembler d'un
 * écran à l'autre.
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
      console.error(`Police introuvable : ${fontPath}`);
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
        `  ${stack} ${start}-${end} : ${(data.length / 1024).toFixed(1)} ko`,
      );
    }
  }

  const totalBytes = FONT_STACKS.flatMap(({ stack, ranges }) =>
    ranges.map(([start, end]) =>
      fs.statSync(path.join(outputDirectory, stack, `${start}-${end}.pbf`)).size,
    ),
  ).reduce((sum, size) => sum + size, 0);

  console.log(`\nGlyphes écrits dans ${outputDirectory}`);
  console.log(`Total : ${(totalBytes / 1024).toFixed(0)} ko embarqués dans l'APK`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
