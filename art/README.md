# Brand assets

The drawings the application's icon is made of, kept as SVG because that is
the form they can be edited in — Android only reads the `VectorDrawable`
translation of them.

| File | What it is | Where it goes |
| --- | --- | --- |
| `roue-libre-icone.svg` | The complete icon, background included | `fastlane/metadata/…/images/icon.png`, and any store or web listing |
| `ic_launcher_foreground.svg` | The drawing alone, white | `app/src/main/res/drawable/ic_launcher_foreground.xml` |
| `ic_launcher_monochrome.svg` | The same drawing, single colour, for the themed icon of Android 13+ | `app/src/main/res/drawable/ic_launcher_monochrome.xml` |

The background is a flat `#0F6E56`, declared once in
`app/src/main/res/values/ic_launcher_background.xml`.

Changing the drawing means changing the SVG **and** the two vector drawables:
nothing generates one from the other at build time, so they are kept in step
by hand. The paths are identical on both sides, only the syntax differs.

The 512 unit canvas maps to the adaptive icon's 108 dp; every stroke stays
inside the 33 dp safe radius around the centre, the smallest area a launcher
mask is guaranteed to leave visible.
