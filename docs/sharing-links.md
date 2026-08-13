# Opening a place from another application

`geo:` and `google.navigation:` links, along with addresses shared as plain
text, arrive straight in Roue Libre: it is enough to choose it in Android's
chooser.

Links from mapping websites — `openstreetmap.org`, `google.com/maps` —
**cannot** be verified automatically, those domains not belonging to the
project. Since Android 12 they therefore only reach the application if you allow
it:

**Settings → Apps → Roue Libre → Open by default → Add link**, then tick the
domains you want.

A shortened link is not recognised: the place only appears after a redirect, and
following it would send a request out to a third party, teaching them where you
are going.
